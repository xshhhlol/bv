package dev.aaa1115910.bv.player

import dev.aaa1115910.bv.player.impl.exo.PlaybackBufferPolicy
import dev.aaa1115910.bv.player.impl.exo.SlowReadMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackBufferPolicyTest {
    @Test
    fun `buffer budget leaves heap for decoder and application and caps large TVs`() {
        for (heapMiB in listOf(64L, 128L, 256L, 512L, 1024L, 4096L)) {
            val heap = heapMiB * 1024 * 1024
            val budget = PlaybackBufferPolicy.targetBytes(heap)
            assertTrue(budget <= heap / 4)
            assertTrue(budget <= 256 * 1024 * 1024)
        }
        assertTrue(PlaybackBufferPolicy.targetBytes(1024L * 1024 * 1024) > 96 * 1024 * 1024)
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
