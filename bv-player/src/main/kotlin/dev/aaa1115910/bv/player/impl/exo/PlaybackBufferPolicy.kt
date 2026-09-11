package dev.aaa1115910.bv.player.impl.exo

/**
 * 播放缓冲策略：磁盘和内存两层分工。
 *
 * - 磁盘层（[VideoDiskCache] + [StreamPrefetcher]）在播放位置前面预下载一段写到本地，
 *   扛的是网络抖动、CDN 抽风这类「一段时间拿不到数据」的问题；
 * - 内存层（ExoPlayer 的 LoadControl）只要留住马上要送进解码器的那几十秒。
 */
internal object PlaybackBufferPolicy {
    private const val MIB = 1024L * 1024

    // ---------------------------- 内存层 ----------------------------

    /** 内存里保留的播放时长，够喂解码器就行，不承担抗网络抖动的责任 */
    const val MIN_BUFFER_MS = 15_000
    const val MAX_BUFFER_MS = 30_000

    /** 起播前先攒这么多，太大起播慢，太小容易刚播就卡 */
    const val BUFFER_FOR_PLAYBACK_MS = 1_500

    /** 卡顿恢复后多攒一点再接着播，避免「卡一下播一下」 */
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 4_000

    /**
     * 内存缓冲的**字节上限**——是安全阀，不是预算。
     *
     * 让缓冲「短」的是上面的时长目标：1080p 跑满 30 秒也只要十几 MiB，这个上限根本轮不到生效。
     * 它只在高码率时才起作用，而一旦它比时长目标先封顶，30 秒就会被悄悄压成几秒：
     * 4K 高码率下 48 MiB 只够 4~8 秒，比 10 秒的快进增量还短，于是每次快进都跳出缓冲区，
     * 采样队列整个重置、重新发一次 range 请求，表现就是「播着好好的，一快进就转圈」。
     * 所以这里留够余量（对齐 ExoPlayer 自己的 DEFAULT_VIDEO_BUFFER_SIZE ≈ 125 MiB），
     * 真正的存量交给磁盘层，不靠压缩这个数字省内存。
     */
    fun targetBytes(maxHeapBytes: Long): Int =
        (maxHeapBytes / 4).coerceIn(16 * MIB, 128 * MIB).toInt()

    // ---------------------------- 磁盘层 ----------------------------

    /** 磁盘缓存总上限，超了按 LRU 淘汰 */
    const val DISK_CACHE_BYTES = 1024 * MIB

    /**
     * 每条流在播放位置前面预下载多少。
     *
     * 直接按字节算，不折算时长也不看码率：低码率的片子就是能多存几分钟，高码率少存点，
     * 反正都够扛抖动。要比 [DISK_CACHE_BYTES] 小一截，否则视频+音频两个窗口会把自己挤出去。
     */
    const val PREFETCH_WINDOW_BYTES = 256 * MIB

    /** 每次只锁定并提交一小块，取消/跳转不必等整个 256 MiB 窗口。 */
    const val PREFETCH_CHUNK_BYTES = 2 * MIB

    fun diskTargetBytes(freeBytes: Long): Long =
        if (freeBytes < 256 * MIB) 0 else minOf(DISK_CACHE_BYTES, freeBytes / 4)

    fun windowBytes(diskBudget: Long, streamCount: Int): Long =
        minOf(PREFETCH_WINDOW_BYTES, diskBudget / (streamCount.coerceAtLeast(1) * 2))
}
