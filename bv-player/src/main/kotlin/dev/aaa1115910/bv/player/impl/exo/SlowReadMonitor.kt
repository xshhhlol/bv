package dev.aaa1115910.bv.player.impl.exo

/** 只统计阻塞读取耗时，不把暂停、解码、缓冲已满后的停载时间算成网络低速。 */
internal class SlowReadMonitor {
    private var bytes = 0L
    private var readNanos = 0L

    fun reset() {
        bytes = 0
        readNanos = 0
    }

    fun record(count: Int, elapsedNanos: Long, requiredBitsPerSecond: Long): Boolean {
        if (count <= 0 || requiredBitsPerSecond <= 0) return false
        bytes += count
        readNanos += elapsedNanos.coerceAtLeast(0)
        if (readNanos < 8_000_000_000L) return false
        val bitsPerSecond = bytes.toDouble() * 8_000_000_000.0 / readNanos
        reset()
        return bitsPerSecond < requiredBitsPerSecond * 1.25
    }
}
