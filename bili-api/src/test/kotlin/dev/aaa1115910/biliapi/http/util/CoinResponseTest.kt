package dev.aaa1115910.biliapi.http.util

import dev.aaa1115910.biliapi.http.entity.BiliResponseWithoutData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CoinResponseTest {
    @Test
    fun `coin status does not depend on optional like payload`() = runBlocking {
        for (data in listOf("null", "{}", "{\"like\":false}", "{\"like\":1}")) {
            val result = decode("""{"code":0,"message":"0","data":$data}""")
            assertEquals(0, result.code)
        }
    }

    @Test
    fun `business rejection keeps server code and message`() = runBlocking {
        val result = decode("""{"code":34005,"message":"超过上限","data":{}}""")
        assertEquals(34005, result.code)
        assertEquals("超过上限", result.message)
    }

    private suspend fun decode(body: String): BiliResponseWithoutData =
        HttpClient(MockEngine {
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) {
            install(ContentNegotiation) { riskAwareJson(Json { ignoreUnknownKeys = true }) }
            validateApiRiskResponses()
        }.use { client -> client.post("https://api.bilibili.com/x/web-interface/coin/add").body() }
}
