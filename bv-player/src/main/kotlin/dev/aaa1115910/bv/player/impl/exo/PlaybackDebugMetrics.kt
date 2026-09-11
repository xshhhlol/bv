package dev.aaa1115910.bv.player.impl.exo

import java.util.Locale

internal object PlaybackDebugMetrics {
    fun sampleRate(hz: Int): String = String.format(Locale.ROOT, "%.1f kHz", hz / 1000.0)
}

internal class RenderedFrameMeter {
    private var previousCount: Long? = null
    private var previousTime = 0L

    fun reset() { previousCount = null }

    fun sample(count: Long, nowMs: Long, playing: Boolean): Double? {
        if (!playing) { reset(); return null }
        val oldCount = previousCount
        val elapsed = nowMs - previousTime
        previousCount = count
        previousTime = nowMs
        return if (oldCount == null || count < oldCount || elapsed <= 0 || elapsed > 2_500) null
            else (count - oldCount) * 1000.0 / elapsed
    }
}
