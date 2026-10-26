package dev.aaa1115910.bv.player.impl.exo

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** 小段 Range 下载，包含连接和首包耗时；不是只测 ping 或 HEAD。 */
internal class CdnSpeedProbe(
    client: OkHttpClient,
    private val userAgent: String?,
    private val referer: String?
) {
    private val client = client.newBuilder()
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .connectTimeout(1000, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun measure(url: String): Double? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url)
            .header("Range", "bytes=0-262143")
            .header("Accept-Encoding", "identity")
            .apply {
                userAgent?.let { header("User-Agent", it) }
                referer?.let { header("Referer", it) }
            }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        val start = System.nanoTime()
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                var bytes = 0
                fun measuredSpeed(): Double? = if (bytes == 0) null else
                    bytes * 8_000_000_000.0 / (System.nanoTime() - start).coerceAtLeast(1)
                val speed = try {
                    response.use {
                        val body = it.body
                        // 错误页即使返回 200，也不能当作高速视频线路。
                        val contentType = body?.contentType()
                        val type = contentType?.type
                        if (body == null || it.code !in listOf(200, 206) || type == "text" ||
                            contentType?.subtype in listOf("json", "xml") ||
                            (it.code == 206 && !it.header("Content-Range").orEmpty().startsWith("bytes 0-"))
                        ) return@use null
                        val buffer = ByteArray(16 * 1024)
                        val input = body.byteStream()
                        while (bytes < 256 * 1024) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 256 * 1024 - bytes))
                            if (count < 0) break
                            bytes += count
                        }
                        measuredSpeed()
                    }
                } catch (_: IOException) {
                    // 高峰期可能在总时限内读不满样本，仍比较实际收到的字节。
                    if (call.isCanceled() && bytes >= 16 * 1024) measuredSpeed() else null
                }
                continuation.resume(speed)
            }
        })
    }
}
