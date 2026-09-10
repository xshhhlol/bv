package dev.aaa1115910.bv.player.impl.exo

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * 实时网速。
 *
 * 不接着用 DefaultBandwidthMeter 是因为它有两点不合用：
 *
 * 1. 它只在**最后一个传输结束**时才重算 bitrateEstimate（`onTransferEnd` 里 streamCount 归零才更新）。
 *    progressive 播放的连接是长开的，缓冲满了才断一次，覆盖层上就长时间定在一个旧值上；
 * 2. 它只统计注册在播放链路上的传输。加了磁盘层之后，播放器的读取大多命中缓存（不算网络流量），
 *    真正在下载的是预下载线程，而那条链路上没有它，于是它几乎什么都看不到。
 *
 * 这里按秒分桶累计真正走网络的字节，取最近几秒的平均，播放链路和预下载链路都会喂进来。
 */
@OptIn(UnstableApi::class)
internal class NetworkSpeedMeter : TransferListener {
    private companion object {
        /** 平均窗口，太短了数字乱跳，太长了反应迟钝 */
        const val WINDOW_SECONDS = 5
    }

    private val buckets = LongArray(WINDOW_SECONDS)
    private var lastSecond = 0L

    /**
     * 覆盖层关着的时候没人读这个数，就别统计了。
     *
     * onBytesTransferred 是每读一块（约 64 KB）就触发一次的热路径，
     * 关掉之后这里只剩一次 volatile 读，连锁都不用进。
     */
    @Volatile
    var enabled: Boolean = false

    /** 停掉统计并清空，重新打开时不会显示一段陈旧数据 */
    @Synchronized
    fun clear() {
        buckets.fill(0)
        lastSecond = 0
    }

    @Synchronized
    fun bitsPerSecond(): Long {
        rotateTo(System.currentTimeMillis() / 1000)
        return buckets.sum() * 8 / WINDOW_SECONDS
    }

    @Synchronized
    private fun add(bytes: Int) {
        val second = System.currentTimeMillis() / 1000
        rotateTo(second)
        buckets[(second % WINDOW_SECONDS).toInt()] += bytes
    }

    /** 把跨过去的桶清零，否则会把几分钟前的字节算进来 */
    private fun rotateTo(second: Long) {
        if (second == lastSecond) return
        val skipped = (second - lastSecond).coerceIn(0, WINDOW_SECONDS.toLong())
        for (i in 1..skipped) {
            buckets[((lastSecond + i) % WINDOW_SECONDS).toInt()] = 0
        }
        lastSecond = second
    }

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int
    ) {
        if (!enabled) return
        // 命中磁盘缓存的读取不是网速
        if (isNetwork && bytesTransferred > 0) add(bytesTransferred)
    }

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
}
