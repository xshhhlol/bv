package dev.aaa1115910.biliapi.http.util

import dev.aaa1115910.biliapi.http.entity.BiliResponse
import dev.aaa1115910.biliapi.http.entity.RiskControlException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.aaa1115910.biliapi.http.entity.user.UserInfoData
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiResponseValidationTest {
    @Serializable
    data class RequiredData(val name: String)

    @Test
    fun `public medal summary does not require private intimacy fields`() {
        val medal = Json.decodeFromString<UserInfoData.FansMedal.Medal>(
            """{"level":1,"medal_name":"test","medal_color":1,"medal_color_start":1,"medal_color_end":1,"medal_color_border":1}"""
        )
        assertEquals("test", medal.medalName)
        assertEquals(0, medal.intimacy)
    }

    @Test
    fun `challenge envelopes cannot become missing-field errors or empty results`() {
        for (body in listOf(
            """{"code":0,"message":"OK","data":{"v_voucher":"challenge"}}""",
            """{"code":0,"result":{"is_risk":true}}""",
            """{"code":0,"data":{"gaia_res_type":1}}""",
            """{"code":-352,"message":"blocked"}""",
            """{"code":-412,"message":"banned"}""",
            """{"code":-509,"message":"limited"}"""
        )) assertFailsWith<RiskControlException> { checkApiRiskResponse(body) }
        checkApiRiskResponse("""{"code":0,"data":[{"v_voucher":"not an envelope"}]}""")
        checkApiRiskResponse("""{"code":-101,"message":"not logged in"}""")
        checkApiRiskResponse("""{"code":0,"data":{"is_risk":false,"gaia_res_type":0}}""")
    }

    @Test
    fun `valid response remains available for typed decoding`() = runBlocking {
        HttpClient(MockEngine {
            respond("""{"code":0,"message":"OK","data":{"name":"video"}}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            install(ContentNegotiation) { riskAwareJson() }
            validateApiRiskResponses()
        }.use { client ->
            assertEquals("video", client.get("https://api.bilibili.com/test").body<BiliResponse<RequiredData>>().getResponseData().name)
        }
    }

    @Test
    fun `compressed JSON can be decoded after risk inspection`() = runBlocking {
        val payload = """{"code":0,"message":"OK","data":{"name":"compressed"}}"""
        val bytes = java.io.ByteArrayOutputStream().apply {
            java.util.zip.GZIPOutputStream(this).use { it.write(payload.toByteArray()) }
        }.toByteArray()
        HttpClient(MockEngine {
            respond(bytes, headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                HttpHeaders.ContentEncoding to listOf("gzip")
            ))
        }) {
            install(ContentNegotiation) { riskAwareJson() }
            install(io.ktor.client.plugins.compression.ContentEncoding) { gzip() }
            validateApiRiskResponses()
        }.use { client ->
            assertEquals("compressed", client.get("https://api.bilibili.com/test").body<BiliResponse<RequiredData>>().getResponseData().name)
        }
    }

    @Test
    fun `risk response is not automatically retried`() = runBlocking {
        var attempts = 0
        HttpClient(MockEngine {
            attempts++
            respond("""{"code":0,"message":"OK","data":{"v_voucher":"challenge"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { riskAwareJson() }
            validateApiRiskResponses()
            retryReadOnlyNetworkFailures()
        }.use { client ->
            val error = assertFailsWith<RiskControlException> { client.get("https://api.bilibili.com/test").body<BiliResponse<RequiredData>>() }
            assertTrue(error.requiresVerification)
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `HTTP throttling is recognized even when response is HTML`() = runBlocking {
        for (status in listOf(HttpStatusCode.PreconditionFailed, HttpStatusCode.TooManyRequests)) {
            HttpClient(MockEngine { respond("<html>limited</html>", status) }) {
                validateApiRiskResponses()
            }.use { client ->
                assertEquals(status.value, assertFailsWith<RiskControlException> {
                    client.get("https://api.bilibili.com/test")
                }.code)
            }
        }
    }

    @Test
    fun `POST transport failure is never replayed`() = runBlocking {
        var attempts = 0
        HttpClient(MockEngine { attempts++; throw IOException("connection lost after send") }) {
            retryReadOnlyNetworkFailures()
        }.use { client ->
            assertFailsWith<IOException> { client.post("https://api.bilibili.com/test") }
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `cancellation is never retried`() = runBlocking {
        var attempts = 0
        HttpClient(MockEngine { attempts++; throw CancellationException("cancelled") }) {
            retryReadOnlyNetworkFailures()
        }.use { client ->
            assertFailsWith<CancellationException> { client.get("https://api.bilibili.com/test") }
            assertEquals(1, attempts)
        }
    }

    @Test
    fun `binary content remains unchanged`() = runBlocking {
        HttpClient(MockEngine { respond("binary-payload", headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")) }) {
            validateApiRiskResponses()
        }.use { client -> assertEquals("binary-payload", client.get("https://cdn.example/test").bodyAsText()) }
    }
}
