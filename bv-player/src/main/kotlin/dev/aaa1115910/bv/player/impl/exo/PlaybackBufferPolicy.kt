package dev.aaa1115910.bv.player.impl.exo

internal object PlaybackBufferPolicy {
    // 提前补水，避免缓冲耗到 30 秒才开始重新下载。
    const val MIN_BUFFER_MS = 120_000
    const val MAX_BUFFER_MS = 180_000

    // 按进程堆预算分配，不把电视的总物理内存当作播放器可用内存。
    fun targetBytes(maxHeapBytes: Long): Int =
        (maxHeapBytes / 4).coerceIn(16L * 1024 * 1024, 256L * 1024 * 1024).toInt()
}
