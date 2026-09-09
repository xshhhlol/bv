package dev.aaa1115910.biliapi.http.util

import dev.aaa1115910.biliapi.http.entity.RiskControlException
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.http.ContentType
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.nio.charset.Charset

/** Inspect the envelope before business-data decoding can hide a challenge as an empty list. */
internal fun checkApiRiskResponse(body: String, status: Int = 200) {
    if (status == 412 || status == 429) {
        throw RiskControlException("请求受限，请稍后重试 (HTTP $status)", code = status)
    }
    val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return
    val code = (root["code"] as? JsonPrimitive)?.intOrNull
    val payloads = listOfNotNull(root["data"] as? JsonObject, root["result"] as? JsonObject)
    val challenge = payloads.any { data ->
        !(data["v_voucher"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank() ||
            (data["is_risk"] as? JsonPrimitive)?.contentOrNull in setOf("true", "1") ||
            (data["gaia_res_type"] as? JsonPrimitive)?.intOrNull == 1
    }
    if (code in setOf(-352, -412, -509) || challenge) {
        val message = (root["message"] as? JsonPrimitive)?.contentOrNull
            ?.takeUnless { it.isBlank() || it == "0" || it.equals("OK", ignoreCase = true) }
            ?: if (challenge) "请求需要风控验证，请稍后重试" else "请求受限，请稍后重试"
        throw RiskControlException(message, code = if (code == 0) -352 else code, requiresVerification = challenge)
    }
}

fun HttpClientConfig<*>.validateApiRiskResponses() {
    HttpResponseValidator {
        validateResponse { response ->
            val status = response.status.value
            if (status == 412 || status == 429) checkApiRiskResponse("", status)
        }
    }
}

/** Never replay mutations or retry a server challenge/coroutine cancellation. */
fun HttpClientConfig<*>.retryReadOnlyNetworkFailures() {
    install(HttpRequestRetry) {
        retryOnExceptionIf(maxRetries = 2) { request, cause ->
            request.method == HttpMethod.Get && cause is IOException
        }
        exponentialDelay()
    }
}

/** Inspect decoded JSON bytes exactly once, after ContentEncoding decompression. */
fun ContentNegotiationConfig.riskAwareJson(json: Json = Json) {
    val delegate = KotlinxSerializationConverter(json)
    register(ContentType.Application.Json, object : ContentConverter by delegate {
        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            val bytes = content.readRemaining().readByteArray()
            checkApiRiskResponse(bytes.toString(charset))
            return delegate.deserialize(charset, typeInfo, ByteReadChannel(bytes))
        }
    })
}
