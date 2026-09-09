package dev.aaa1115910.biliapi.http.util

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiSignTest {
    @Test
    fun `canonical query matches web encoding for Chinese and spaces`() {
        assertEquals(
            "bar=%E4%BA%94%E4%B8%80%E5%9B%9B&baz=1919810&foo=one%20one%20four",
            canonicalWbiQuery(mapOf("foo" to "one one four", "bar" to "五一四", "baz" to "1919810"))
        )
    }

    @Test
    fun `filters before encoding and preserves literal percent plus and tilde`() {
        assertEquals(
            "keyword=ab%20%2B%2520~",
            canonicalWbiQuery(mapOf("keyword" to "a!'()*b +%20~"))
        )
    }

    @Test
    fun `app GET encodes once and re-signing is idempotent`() {
        val request = HttpRequestBuilder().apply {
            parameter("keyword", "中文 +%")
            parameter("access_key", "")
        }
        request.encAppGet()
        val first = request.url.buildString()
        request.encAppGet()
        assertEquals(first, request.url.buildString())
        val canonical = "access_key=&appkey=dfca71928277209b&keyword=%E4%B8%AD%E6%96%87+%2B%25"
        val expected = MessageDigest.getInstance("MD5")
            .digest((canonical + "b5475a8825547a4fc26c7d518eaaa02e").toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, request.url.parameters["sign"])
    }

    @Test
    fun `app POST re-signing preserves one signature`() {
        val request = HttpRequestBuilder().apply {
            setBody(FormDataContent(Parameters.build {
                append("keyword", "中文 +%")
                append("access_key", "")
            }))
        }
        request.encAppPost()
        val first = (request.body as FormDataContent).formData
        request.encAppPost()
        assertEquals(first, (request.body as FormDataContent).formData)
        assertEquals(1, first.getAll("sign")!!.size)
    }
}
