package dev.aaa1115910.bv.player

import dev.aaa1115910.bv.player.impl.exo.CdnSpeedProbe
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CdnSpeedProbeTest {
    private val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build()

    @Test
    fun `probe requests bounded range and preserves playback headers`() = runBlocking {
        val headers = CompletableFuture<String>()
        Server { socket ->
            headers.complete(readHeaders(socket))
            val output = socket.getOutputStream()
            output.write(("HTTP/1.1 206 Partial Content\r\nContent-Length: 262144\r\n" +
                "Content-Range: bytes 0-262143/1000000\r\nContent-Type: video/mp4\r\n\r\n").toByteArray())
            output.write(ByteArray(262144))
            output.flush()
        }.use { server ->
            val speed = CdnSpeedProbe(client, "BV-test", "https://www.bilibili.com").measure(server.url)
            assertTrue(speed != null && speed > 0)
            val request = headers.get(2, TimeUnit.SECONDS).lowercase()
            assertTrue("range: bytes=0-262143" in request)
            assertTrue("user-agent: bv-test" in request)
            assertTrue("referer: https://www.bilibili.com" in request)
        }
    }

    @Test
    fun `successful HTTP error page is not a fast CDN`() = runBlocking {
        Server { socket ->
            readHeaders(socket)
            socket.getOutputStream().write(
                "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 5\r\n\r\nerror".toByteArray())
        }.use { server ->
            assertNull(CdnSpeedProbe(client, null, null).measure(server.url))
        }
    }

    @Test
    fun `slow node gets measured even if sample does not finish before deadline`() = runBlocking {
        Server { socket ->
            readHeaders(socket)
            val output = socket.getOutputStream()
            output.write(("HTTP/1.1 206 Partial Content\r\nContent-Length: 262144\r\n" +
                "Content-Range: bytes 0-262143/1000000\r\n\r\n").toByteArray())
            repeat(16) {
                output.write(ByteArray(16384))
                output.flush()
                Thread.sleep(200)
            }
        }.use { server ->
            val speed = CdnSpeedProbe(client, null, null).measure(server.url)
            assertTrue(speed != null && speed > 0)
        }
    }

    private fun readHeaders(socket: Socket): String {
        val reader = socket.getInputStream().bufferedReader()
        return buildString {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                appendLine(line)
            }
        }
    }

    private class Server(respond: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0)
        val url = "http://127.0.0.1:${server.localPort}/video"
        @Volatile private var socket: Socket? = null
        private val thread = Thread {
            runCatching {
                server.accept().use {
                    socket = it
                    respond(it)
                }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            server.close()
            socket?.close()
            thread.interrupt()
            thread.join(1000)
        }
    }
}
