package dev.aaa1115910.biliapi.http.util

import dev.aaa1115910.biliapi.http.BiliHttpApi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.clone
import io.ktor.http.encodedPath
import io.ktor.http.plus
import io.ktor.util.AttributeKey
import java.net.URLEncoder
import java.security.MessageDigest

private val SkipWebFingerprintCookies = AttributeKey<Boolean>("SkipWebFingerprintCookies")

fun HttpRequestBuilder.skipWebFingerprintCookies() {
    attributes.put(SkipWebFingerprintCookies, true)
}

private const val APP_KEY = "dfca71928277209b"
private const val APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"
private val mixinKeyEncTab = listOf(
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
    33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
    26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36,
    20, 34, 44, 52
)

private fun String.md5(): String =
    MessageDigest.getInstance("MD5")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

private fun Map<String, String>.toSortedQueryString(): String =
    toSortedMap()
        .map { (k, v) -> "$k=${URLEncoder.encode(v, "utf-8")}" }
        .joinToString("&")

private fun getMixinKey(orig: String): String =
    mixinKeyEncTab.fold("") { s, i -> s + orig[i] }.substring(0, 32)

internal val HttpRequestBuilder.isAppRequest: Boolean
    get() = url.parameters.contains("access_key") || url.host == "app.bilibili.com" ||
        (body as? FormDataContent)?.formData?.contains("access_key") == true

fun HttpRequestBuilder.encAppPost() {
    var parameters = Parameters.build {
        (body as FormDataContent).formData.entries()
            .filter { it.key != "appkey" && it.key != "sign" }
            .forEach { (key, values) -> appendAll(key, values) }
        append("appkey", APP_KEY)
    }

    val sortedQueryString = parameters.entries()
        .associate { it.key to it.value.first() }
        .toSortedQueryString()

    val sign = (sortedQueryString + APP_SEC).md5()
    parameters += Parameters.build { append("sign", sign) }
    setBody(FormDataContent(parameters))
    println("sign: $sign")
}

fun HttpRequestBuilder.encAppGet() {
    url.parameters.remove("appkey")
    url.parameters.remove("sign")
    parameter("appkey", APP_KEY)

    val sortedQueryString = url.parameters.entries()
        .associate { it.key to it.value.first() }
        .toSortedQueryString()

    val sign = (sortedQueryString + APP_SEC).md5()
    parameter("sign", sign)
    println("sign: $sign")
}

internal fun canonicalWbiQuery(parameters: Map<String, String>): String =
    parameters.toSortedMap().entries.joinToString("&") { (key, value) ->
        val filtered = value.filter { it !in "!'()*" }
        val encoded = URLEncoder.encode(filtered, "UTF-8").replace("+", "%20").replace("%7E", "~")
        "$key=$encoded"
    }

suspend fun HttpRequestBuilder.encWbi() {
    // wbi key 每天都会轮换。这里每次签名前都尝试刷新（updateWbi 内部按 2 小时节流），
    // 否则常驻内存的电视端会一直用启动时那份 key，轮换后所有 wbi 接口都返回 -352 风控校验失败
    BiliHttpApi.ensureWebCookies()
    BiliHttpApi.updateWbi()
    val keys = requireNotNull(BiliHttpApi.currentWbiKeys()) { "WBI keys unavailable" }
    val mixinKey = getMixinKey(keys.first + keys.second)

    // HttpRequestRetry 重试时会复用同一个 request builder，
    // 重新签名前必须先清掉上一次的签名参数，否则会追加出重复的 wts/w_rid 导致签名失效
    url.parameters.remove("wts")
    url.parameters.remove("w_rid")

    val wts = (System.currentTimeMillis() / 1000).toInt()
    parameter("wts", wts)

    val sortedParams = canonicalWbiQuery(url.parameters.entries()
        .associate { it.key to it.value.first() })

    val wRid = (sortedParams + mixinKey).md5()
    parameter("w_rid", wRid)
}

fun HttpClient.encApiSign() = plugin(HttpSend)
    .intercept { request ->
        // skip when using grpc proxy
        if (request.url.encodedPath.startsWith("bilibili.")) {
            return@intercept execute(request)
        }

        val getUrlWithoutAccessToken: (URLBuilder) -> String = { urlBuilder ->
            urlBuilder.clone().apply {
                if (parameters.contains("access_key") && !parameters["access_key"].isNullOrBlank()) {
                    parameters["access_key"] = "HIDDEN_ACCESS_TOKEN"
                }
            }.toString()
        }

        when (request.method) {
            HttpMethod.Get -> {
                val isWbiRequest = request.url.encodedPath.contains("wbi") ||
                        request.url.encodedPath.contains("/pgc/player/web/playurl") ||
                        request.url.encodedPath.contains("/pgc/player/web/v2/playurl") ||
                        request.url.encodedPath == "/xlive/web-room/v1/index/getDanmuInfo"
                if (isWbiRequest) {
                    println("Enc wbi for get request: ${getUrlWithoutAccessToken(request.url)}")
                    request.encWbi()
                } else if (request.isAppRequest) {
                    println("Enc app sign for get request: ${getUrlWithoutAccessToken(request.url)}")
                    request.encAppGet()
                    println(getUrlWithoutAccessToken(request.url))
                }
            }

            HttpMethod.Post -> {
                if (request.body is EmptyContent) return@intercept execute(request)
                val parameters = (request.body as? FormDataContent)?.formData
                    ?: return@intercept execute(request)
                val isParametersContainKeywords = parameters.contains("access_key")
                val isPathContainKeywords = request.url.encodedPath.contains("passport")
                if (isParametersContainKeywords || isPathContainKeywords) {
                    println("Enc app sign for post request: ${getUrlWithoutAccessToken(request.url)}")
                    request.encAppPost()
                }
            }
        }
        execute(request)
    }

fun HttpClient.injectBuvid3Cookie() = plugin(HttpSend).intercept { request ->
    val isPlayUrlRequest =
        request.url.encodedPath.contains("/x/player/playurl") ||
                request.url.encodedPath.contains("/x/player/wbi/playurl")

    val isOfficialWebApi = request.url.host in setOf("api.bilibili.com", "api.live.bilibili.com")
    val isBootstrap = request.url.encodedPath == "/x/frontend/finger/spi" ||
        request.url.encodedPath == "/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket"
    if (isOfficialWebApi && !request.isAppRequest && !isPlayUrlRequest &&
        request.attributes.getOrNull(SkipWebFingerprintCookies) != true
    ) {
        // Bootstrap calls use this same client; do not recursively acquire its cookie mutex.
        if (!isBootstrap) BiliHttpApi.ensureWebCookies()
        val managedCookies = mapOf(
            "buvid3" to BiliHttpApi.buvid3,
            "buvid4" to BiliHttpApi.buvid4,
            "b_nut" to BiliHttpApi.bNut,
            "bili_ticket" to BiliHttpApi.biliTicket
        ).filterValues { it.isNotBlank() }
        val existing = request.headers["Cookie"].orEmpty().split(';')
            .map { it.trim() }.filter { it.isNotBlank() }
            .filter { it.substringBefore('=') !in managedCookies }.joinToString("; ")
        if (request.headers["Referer"] == null) {
            request.headers["Referer"] = "https://www.bilibili.com/"
        }
        val extras = managedCookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }

        if (extras.isNotBlank()) {
            request.headers["Cookie"] =
                if (existing.isNotBlank()) "$extras; $existing" else extras
        }
    }
    execute(request)
}