package dev.aaa1115910.bv.player

import dev.aaa1115910.bv.player.impl.exo.*
import kotlin.test.*

class PlaybackDebugMetricsTest {
    @Test fun `audio sample rate preserves fractional kHz`() {
        assertEquals("44.1 kHz", PlaybackDebugMetrics.sampleRate(44100))
        assertEquals("48.0 kHz", PlaybackDebugMetrics.sampleRate(48000))
    }
    @Test fun `frame sampling resets on pause video switch and hidden overlay`() {
        val meter = RenderedFrameMeter()
        assertNull(meter.sample(100, 1000, true))
        assertEquals(60.0, meter.sample(160, 2000, true))
        assertNull(meter.sample(160, 3000, false))
        assertNull(meter.sample(161, 8000, true))
        assertEquals(30.0, meter.sample(191, 9000, true))
        assertNull(meter.sample(5, 10000, true))
        meter.reset()
        assertNull(meter.sample(60, 11000, true))
    }
    @Test fun `network average ages to zero and disabled collection stays empty`() {
        var time = 10000L
        val rate = RollingNetworkRate { time }
        rate.add(1000)
        assertEquals(0, rate.bitsPerSecond())
        rate.enabled = true
        repeat(5) { rate.add(125000); time += 1000 }
        assertEquals(800000, rate.bitsPerSecond())
        rate.enabled = false
        rate.add(1000000)
        rate.enabled = true
        assertEquals(0, rate.bitsPerSecond())
        rate.add(125000)
        time += 5000
        assertEquals(0, rate.bitsPerSecond())
    }
    @Test fun `clock reset does not retain stale network samples`() {
        var time = 10000L
        val rate = RollingNetworkRate { time }.apply { enabled = true }
        rate.add(125000)
        time = 0
        assertEquals(0, rate.bitsPerSecond())
    }
    @Test fun `disk budget leaves free space and windows cannot evict each other`() {
        val mib = 1024L * 1024
        assertEquals(0, PlaybackBufferPolicy.diskTargetBytes(200 * mib))
        for (free in listOf(256L, 512L, 2048L, 8192L)) {
            val budget = PlaybackBufferPolicy.diskTargetBytes(free * mib)
            assertTrue(budget <= free * mib / 4)
            assertTrue(PlaybackBufferPolicy.windowBytes(budget, 2) * 2 <= budget / 2)
        }
    }
}
