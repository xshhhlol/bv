package dev.aaa1115910.biliapi.http.util

import io.ktor.client.HttpClient
import dev.aaa1115910.biliapi.http.plugins.BiliUserAgent
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import java.io.IOException
import java.security.MessageDigest

class CoinRequestTest {
    @Test
    fun `both coin amounts send matching csrf cookie and video origin`() = runBlocking {
        for (amount in 1..2) {
            var requests = 0
            HttpClient(MockEngine { request ->
                requests++
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("https://api.bilibili.com/x/web-interface/coin/add", request.url.toString())
                val body = (request.body as FormDataContent).formData
                assertEquals("170001", body["aid"])
                assertEquals(amount.toString(), body["multiply"])
                assertEquals("0", body["select_like"])
                assertEquals("test-csrf", body["csrf"])
                assertEquals("SESSDATA=test-session; bili_jct=test-csrf", request.headers[HttpHeaders.Cookie])
                assertEquals("https://www.bilibili.com", request.headers[HttpHeaders.Origin])
                assertEquals("https://www.bilibili.com/video/av170001/", request.headers[HttpHeaders.Referrer])
                respond("""{"code":0,"message":"0","data":{"like":1}}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }) {
                install(ContentNegotiation) { riskAwareJson(Json { ignoreUnknownKeys = true }) }
                retryReadOnlyNetworkFailures()
            }.use { client ->
                assertTrue(client.submitVideoCoin(170001, null, amount, false, "test-csrf", "test-session", null).first)
            }
            assertEquals(1, requests)
        }
    }

    @Test
    fun `unauthorized response is preserved without replaying coin mutation`() = runBlocking {
        var requests = 0
        HttpClient(MockEngine {
            requests++
            respond("""{"code":-401,"message":"非法访问","data":{}}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            install(ContentNegotiation) { riskAwareJson(Json { ignoreUnknownKeys = true }) }
            retryReadOnlyNetworkFailures()
        }.use { client ->
            val result = client.submitVideoCoin(170001, null, 1, false, "test-csrf", "test-session", "test-device")
            assertFalse(result.first)
            assertContains(result.second, "code=-401, Web")
            assertContains(result.second, "App 扫码登录")
        }
        assertEquals(1, requests)
    }

    @Test
    fun `app login signs both amounts and ignores persisted web cookies`() = runBlocking {
        for (amount in 1..2) {
            var requests = 0
            appClient { request ->
                requests++
                assertEquals("https://app.bilibili.com/x/v2/view/coin/add", request.url.toString())
                assertNull(request.headers[HttpHeaders.Cookie])
                assertNull(request.headers[HttpHeaders.Origin])
                assertContains(request.headers[HttpHeaders.UserAgent].orEmpty(), "BiliDroid/")
                val body = (request.body as FormDataContent).formData
                assertEquals("170001", body["aid"])
                assertEquals(amount.toString(), body["multiply"])
                assertEquals("0", body["select_like"])
                assertEquals("test-token", body["access_key"])
                assertNull(body["csrf"])
                assertNull(body["bvid"])
                assertEquals("dfca71928277209b", body["appkey"])
                assertTrue(body["ts"]!!.toLong() > 0)
                val canonical = "access_key=test-token&aid=170001&appkey=dfca71928277209b&multiply=$amount&select_like=0&ts=${body["ts"]}"
                val expected = MessageDigest.getInstance("MD5")
                    .digest((canonical + "b5475a8825547a4fc26c7d518eaaa02e").toByteArray())
                    .joinToString("") { "%02x".format(it) }
                assertEquals(expected, body["sign"])
                """{"code":0,"message":"0","data":{"like":false}}"""
            }.use { client ->
                assertTrue(client.submitVideoCoin(
                    null, "BV17x411w7KC", amount, false,
                    "old-csrf", "old-session", "old-device", "test-token"
                ).first)
            }
            assertEquals(1, requests)
        }
    }

    @Test
    fun `expired app credentials and business errors never fall back to web`() = runBlocking {
        for (code in listOf(-2, -101, -658, -401, -104, 34005)) {
            var requests = 0
            appClient {
                requests++
                """{"code":$code,"message":"server message"}"""
            }.use { client ->
                val result = client.submitVideoCoin(170001, null, 1, false, "", "", null, "expired-token")
                assertFalse(result.first)
                assertContains(result.second, "code=$code, App")
                if (code in listOf(-2, -101, -658)) {
                    assertContains(result.second, "覆盖安装不会更新登录凭据")
                }
            }
            assertEquals(1, requests)
        }
    }

    @Test
    fun `lost app response never retries a possibly completed coin operation`() = runBlocking {
        var requests = 0
        appClient {
            requests++
            throw IOException("response lost")
        }.use { client ->
            assertFailsWith<IOException> {
                client.submitVideoCoin(170001, null, 2, false, "", "", null, "test-token")
            }
        }
        assertEquals(1, requests)
    }

    private fun appClient(response: (io.ktor.client.request.HttpRequestData) -> String) =
        HttpClient(MockEngine { request ->
            respond(response(request), headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            BiliUserAgent()
            install(ContentNegotiation) { riskAwareJson(Json { ignoreUnknownKeys = true }) }
            retryReadOnlyNetworkFailures()
        }.apply {
            // Same interceptors as production: App auth must skip all persisted Web cookies.
            encApiSign()
            injectBuvid3Cookie()
        }
}
