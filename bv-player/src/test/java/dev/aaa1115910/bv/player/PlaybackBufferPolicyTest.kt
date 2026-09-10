package dev.aaa1115910.bv.player

import dev.aaa1115910.bv.player.impl.exo.PlaybackBufferPolicy
import dev.aaa1115910.bv.player.impl.exo.SlowReadMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackBufferPolicyTest {
    @Test
    fun `memory buffer never eats the heap`() {
        for (heapMiB in listOf(64L, 128L, 256L, 512L, 1024L, 4096L)) {
            val heap = heapMiB * 1024 * 1024
            val budget = PlaybackBufferPolicy.targetBytes(heap)
            assertTrue(budget <= heap / 4)
            assertTrue(budget <= 128 * 1024 * 1024)
        }
        // 让缓冲变短的是时长目标，不是字节上限
        assertTrue(PlaybackBufferPolicy.MAX_BUFFER_MS <= 30_000)
    }

    @Test
    fun `byte ceiling still covers a seek increment at high bitrate`() {
        // 快进增量是 10 秒（ExoMediaPlayer 里 setSeekForwardIncrementMs）。
        // 字节上限要是比这还短，每次快进都会跳出缓冲、重置采样队列，表现就是一快进就转圈。
        val seekIncrementMs = 10_000L
        for (heapMiB in listOf(256L, 512L, 1024L)) {
            val budget = PlaybackBufferPolicy.targetBytes(heapMiB * 1024 * 1024).toLong()
            for (bitrate in listOf(3_000_000L, 8_000_000L, 25_000_000L, 50_000_000L)) {
                val bufferedMs = budget * 8 * 1000 / bitrate
                assertTrue(
                    bufferedMs > seekIncrementMs,
                    "heap ${heapMiB}MiB @ ${bitrate / 1_000_000}Mbps 只能缓冲 ${bufferedMs}ms"
                )
            }
        }
    }

    @Test
    fun `prefetch window cannot evict itself out of the cache`() {
        // 视频+音频两个窗口都得装得下，否则后下的会把先下的挤掉，白下
        assertTrue(PlaybackBufferPolicy.PREFETCH_WINDOW_BYTES * 2 < PlaybackBufferPolicy.DISK_CACHE_BYTES)
    }


    @Test
    fun `sustained slow transfer triggers before the buffer drains`() {
        val monitor = SlowReadMonitor()
        repeat(7) { assertFalse(monitor.record(125_000, 1_000_000_000, 25_000_000)) }
        assertTrue(monitor.record(125_000, 1_000_000_000, 25_000_000))
    }

    @Test
    fun `short dip followed by fast transfer does not switch`() {
        val monitor = SlowReadMonitor()
        repeat(2) { assertFalse(monitor.record(125_000, 1_000_000_000, 25_000_000)) }
        repeat(6) { assertFalse(monitor.record(8_000_000, 1_000_000_000, 25_000_000)) }
    }

    @Test
    fun `small audio stream uses its own bitrate and pauses do not count as slow reads`() {
        val monitor = SlowReadMonitor()
        repeat(16) { assertFalse(monitor.record(32_000, 1_000_000_000, 128_000)) }
        // 无论两次读取之间暂停了多久，都只计实际 read 的耗时。
        repeat(100) { assertFalse(monitor.record(64_000, 100_000, 25_000_000)) }
    }

    @Test
    fun `unknown bitrate and EOF do not trigger speculative switches`() {
        val monitor = SlowReadMonitor()
        assertFalse(monitor.record(1024, 20_000_000_000, 0))
        assertFalse(monitor.record(-1, 20_000_000_000, 25_000_000))
        assertFalse(monitor.record(0, 20_000_000_000, 25_000_000))
    }

    @Test
    fun `seek resets old samples and faster playback requires more throughput`() {
        val monitor = SlowReadMonitor()
        assertFalse(monitor.record(1000, 7_000_000_000, 25_000_000))
        monitor.reset()
        assertFalse(monitor.record(1000, 1_000_000_000, 25_000_000))
        monitor.reset()
        assertFalse(monitor.record(40_000_000, 8_000_000_000, 25_000_000))
        assertTrue(monitor.record(40_000_000, 8_000_000_000, 50_000_000))
    }
}
