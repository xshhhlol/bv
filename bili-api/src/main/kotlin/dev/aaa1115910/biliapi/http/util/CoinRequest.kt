package dev.aaa1115910.biliapi.http.util

import dev.aaa1115910.biliapi.http.entity.BiliResponseWithoutData
import dev.aaa1115910.biliapi.util.toAv
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters

/** 按登录凭据选择投币接口；失败后不切换接口重放，避免重复扣币。 */
internal suspend fun HttpClient.submitVideoCoin(
    avid: Long?,
    bvid: String?,
    multiply: Int,
    like: Boolean,
    csrf: String,
    sessData: String,
    buvid3: String?,
    accessKey: String? = null
): Pair<Boolean, String> {
    require(avid != null || !bvid.isNullOrBlank()) { "视频 ID 不能为空" }
    require(multiply in 1..2) { "投币数量只能为 1 或 2" }
    val appToken = accessKey?.takeIf { it.isNotBlank() }
    val useApp = appToken != null
    val aid = avid ?: if (useApp) bvid?.toAv() else null
    if (useApp) {
        require(aid != null && aid > 0) { "App 投币需要有效的 AV 号" }
    } else {
        require(sessData.isNotBlank() && csrf.isNotBlank()) { "登录凭据不完整，请在账号管理重新扫码登录后再投币" }
    }
    val endpoint = if (useApp) "https://app.bilibili.com/x/v2/view/coin/add"
        else "https://api.bilibili.com/x/web-interface/coin/add"
    val response = post(endpoint) {
        setBody(FormDataContent(Parameters.build {
            aid?.let { append("aid", "$it") }
            if (!useApp) bvid?.let { append("bvid", it) }
            append("multiply", "$multiply")
            append("select_like", if (like) "1" else "0")
            if (useApp) {
                append("access_key", appToken!!)
                append("ts", (System.currentTimeMillis() / 1000).toString())
            } else {
                append("csrf", csrf)
            }
        }))
        if (!useApp) {
            header("Cookie", listOfNotNull(
                "SESSDATA=$sessData",
                "bili_jct=$csrf",
                buvid3?.takeIf { it.isNotBlank() }?.let { "buvid3=$it" }
            ).joinToString("; "))
            header("Origin", "https://www.bilibili.com")
            header("Referer", "https://www.bilibili.com/video/${bvid?.takeIf { it.isNotBlank() } ?: "av$avid"}/")
        }
    }.body<BiliResponseWithoutData>()
    // 不解析未使用的 data.like，避免把已成功的投币误报为失败。
    return response.code.let { code ->
        val hint = when (code) {
            -2, -101, -111, -658 -> "；登录凭据失效，请在账号管理重新扫码登录（覆盖安装不会更新登录凭据）"
            -401 -> if (useApp) "；App 请求被拒绝，请在账号管理重新扫码登录后重试"
                else "；Web 请求被拒绝，请在账号管理使用 App 扫码登录后重试"
            else -> ""
        }
        (code == 0) to if (code == 0) response.message
            else "${response.message} (code=$code, ${if (useApp) "App" else "Web"})$hint"
    }
}
