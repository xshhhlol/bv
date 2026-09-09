package dev.aaa1115910.biliapi.http.util

import dev.aaa1115910.biliapi.http.plugins.BiliUserAgent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiUserAgentTest {
    @Test
    fun `web login uses browser UA and API app credentials use app UA`() = runBlocking {
        val agents = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            assertEquals(1, request.headers.getAll(HttpHeaders.UserAgent)!!.size)
            agents += request.headers[HttpHeaders.UserAgent]!!
            respond("OK")
        }) { BiliUserAgent() }.use { client ->
            client.get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
            client.get("https://api.bilibili.com/pgc/app/timeline?access_key=test")
            client.post("https://api.bilibili.com/x/v2/history/report") {
                setBody(FormDataContent(Parameters.build { append("access_key", "test") }))
            }
        }
        assertTrue(agents[0].contains("Chrome/"))
        assertTrue(agents[1].contains("BiliDroid/"))
        assertTrue(agents[2].contains("BiliDroid/"))
    }
}
