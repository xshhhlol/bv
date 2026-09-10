package dev.aaa1115910.biliapi.http.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class WebWriteCookieTest {
    /**
     * 写操作接口是 double submit cookie 校验，正文里的 csrf 必须和 Cookie 里的 bili_jct 一致，
     * 只带 SESSDATA 会在网关鉴权层被判成 -401 非法访问
     */
    @Test
    fun `csrf in form body is mirrored into bili_jct cookie`() = runBlocking {
        val sent = capture {
            it.post("https://api.bilibili.com/x/web-interface/coin/add") {
                setBody(FormDataContent(Parameters.build {
                    append("aid", "170001")
                    append("multiply", "1")
                    append("select_like", "0")
                    append("csrf", "0123456789abcdef")
                }))
                header("Cookie", "SESSDATA=session-value")
            }
        }
        assertContains(sent.cookie, "bili_jct=0123456789abcdef")
        assertContains(sent.cookie, "SESSDATA=session-value")
        assertEquals("https://www.bilibili.com", sent.origin)
    }

    @Test
    fun `caller supplied bili_jct is not duplicated`() = runBlocking {
        val sent = capture {
            it.post("https://api.bilibili.com/x/web-interface/archive/like") {
                setBody(FormDataContent(Parameters.build { append("csrf", "abc") }))
                header("Cookie", "SESSDATA=session-value; bili_jct=stale")
            }
        }
        assertEquals(1, sent.cookie.split(';').count { part -> part.trim().startsWith("bili_jct=") })
        assertContains(sent.cookie, "bili_jct=abc")
    }

    @Test
    fun `read only request gets neither bili_jct nor origin`() = runBlocking {
        val sent = capture {
            it.get("https://api.bilibili.com/x/web-interface/archive/coins") {
                header("Cookie", "SESSDATA=session-value")
            }
        }
        assertFalse(sent.cookie.contains("bili_jct"))
        assertEquals(null, sent.origin)
    }

    private class Sent(val cookie: String, val origin: String?)

    private suspend fun capture(request: suspend (HttpClient) -> Unit): Sent {
        var cookie = ""
        var origin: String? = null
        val client = HttpClient(MockEngine {
            cookie = it.headers[HttpHeaders.Cookie].orEmpty()
            origin = it.headers[HttpHeaders.Origin]
            respond(
                """{"code":0,"message":"0"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }).apply { injectBuvid3Cookie() }
        client.use { request(it) }
        return Sent(cookie, origin)
    }
}
