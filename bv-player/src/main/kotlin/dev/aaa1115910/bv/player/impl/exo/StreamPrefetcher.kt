package dev.aaa1115910.bv.player.impl.exo

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadataMutations
import java.util.concurrent.Future

/** 播放只读缓存，后台按小块填补连续空洞；起播/再缓冲时优先供给播放器。 */
@OptIn(UnstableApi::class)
internal class StreamPrefetcher(
    private val cacheProvider: () -> Cache?,
    private val speedMeter: TransferListener? = null,
    private val diskBudget: () -> Long = { VideoDiskCache.budgetBytes },
    private val idleWaitMs: Long = 500L
) {
    class Stream(
        val name: String,
        val uri: Uri,
        val cacheKey: String,
        val dataSourceFactory: () -> CacheDataSource?
    ) {
        @Volatile var playerPosition = -1L
        @Volatile var contentLength = -1L
        @Volatile var contiguousBytes = 0L
        @Volatile var windowCachedBytes = 0L
    }

    private var workers = emptyList<Worker>()
    @Volatile private var playbackReady = false
    @Volatile var cachedBytes = 0L
        private set

    @Synchronized
    fun setStreams(streams: List<Stream>) {
        val old = stopWorkers()
        playbackReady = false
        clearAsync(old, old.map { it.stream.cacheKey } - streams.map { it.cacheKey }.toSet())
        val window = PlaybackBufferPolicy.windowBytes(diskBudget(), streams.size)
        workers = streams.map { Worker(it, window).apply { start() } }
    }

    /** 内存缓冲尚未备好时取消预下载，避免两条连接争抢慢线路。暂停但缓冲已满时可继续填盘。 */
    @Synchronized
    fun setPlaybackReady(ready: Boolean) {
        if (playbackReady == ready) return
        playbackReady = ready
        workers.forEach { it.invalidate(resetPosition = false) }
    }

    @Synchronized
    fun onSeek() {
        workers.forEach { it.invalidate(resetPosition = true) }
    }

    @Synchronized
    fun stopAndClear(): Future<Unit> {
        playbackReady = false
        val old = stopWorkers()
        return clearAsync(old, old.map { it.stream.cacheKey })
    }

    private fun stopWorkers(): List<Worker> = workers.also { old ->
        old.forEach { it.shutdown() }
        workers = emptyList()
    }

    // 等写入真正退出后再删；没有主线程 join，也不会“5 秒后删完、旧下载又写回来”。
    private fun clearAsync(old: List<Worker>, keys: List<String>): Future<Unit> =
        runCacheTask("bv-cache-cleanup") {
            old.forEach { it.join() }
            val cache = cacheProvider() ?: return@runCacheTask
            keys.forEach { key -> runCatching { cache.removeResource(key) } }
            cachedBytes = runCatching { cache.cacheSpace }.getOrDefault(0)
        }

    /** 只读后台发布的快照，调试图层不碰磁盘索引锁。不把 VBR 文件的字节占比当作秒数。 */
    fun leadSummary(): String = synchronized(this) {
        workers.joinToString("  ") {
            val s = it.stream
            "${s.name}:连续 ${s.contiguousBytes / 1024 / 1024} MiB / 窗口已存 ${s.windowCachedBytes / 1024 / 1024} MiB"
        }.ifBlank { "未启动" }
    }

    fun stateSummary(): String = synchronized(this) {
        workers.joinToString("  ") { "${it.stream.name}:${it.status}" }.ifBlank { "未启动" }
    }

    private inner class Worker(val stream: Stream, initialWindow: Long) : Thread("bv-prefetch-${stream.name}") {
        private val signal = Object()
        @Volatile private var stopped = false
        private var generation = 0L
        private var writer: CacheWriter? = null
        @Volatile var status = "等待缓冲"
            private set
        private var window = initialWindow
        private var failureCount = 0

        init { isDaemon = true }

        fun invalidate(resetPosition: Boolean) = synchronized(signal) {
            generation++
            if (resetPosition) {
                stream.playerPosition = -1
                stream.contiguousBytes = 0
                stream.windowCachedBytes = 0
            }
            writer?.cancel()
            signal.notifyAll()
        }

        fun shutdown() = synchronized(signal) {
            stopped = true
            generation++
            writer?.cancel()
            signal.notifyAll()
        }

        private fun waitForChange(ms: Long = idleWaitMs) = synchronized(signal) {
            if (!stopped) signal.wait(ms)
        }

        override fun run() {
            val cache = cacheProvider() ?: run { status = "磁盘缓存不可用"; return }
            while (!stopped) {
                val attemptGeneration = synchronized(signal) { generation }
                try {
                    // 缓存初始化异步完成后才能拿到真实磁盘预算。
                    if (window <= 0) window = PlaybackBufferPolicy.windowBytes(diskBudget(), 2)
                    val (frontier, version) = synchronized(signal) { stream.playerPosition to generation }
                    val total = stream.contentLength
                    if (frontier < 0 || total <= 0 || window <= 0) {
                        status = "等待读取位置"
                        waitForChange()
                        continue
                    }
                    val available = (total - frontier).coerceAtLeast(0)
                    val range = minOf(window, available)
                    val contiguous = if (range > 0) cache.getCachedLength(stream.cacheKey, frontier, range).coerceAtLeast(0) else 0
                    stream.contiguousBytes = contiguous
                    stream.windowCachedBytes = if (range > 0) cache.getCachedBytes(stream.cacheKey, frontier, range) else 0
                    cachedBytes = cache.cacheSpace
                    if (!playbackReady) {
                        status = "让播放先缓冲"
                        waitForChange()
                        continue
                    }
                    if (range == 0L || contiguous == range) {
                        status = if (available == 0L) "已到文件末尾" else "窗口已满"
                        waitForChange()
                        continue
                    }
                    // 第一处空洞就是下一块起点，不跳过 16 MiB，也不反复下载锁住的整段窗口。
                    val start = frontier + contiguous
                    val hole = -cache.getCachedLength(stream.cacheKey, start, range - contiguous)
                    val length = minOf(PlaybackBufferPolicy.PREFETCH_CHUNK_BYTES, hole, range - contiguous)
                    if (length <= 0) continue
                    cache.applyContentMetadataMutations(stream.cacheKey,
                        ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, total) })
                    val source = stream.dataSourceFactory() ?: run { status = "磁盘缓存不可用"; return }
                    speedMeter?.let { source.addTransferListener(it) }
                    val spec = DataSpec.Builder().setUri(stream.uri).setKey(stream.cacheKey)
                        .setPosition(start).setLength(length).build()
                    val currentWriter = CacheWriter(source, spec, null, null)
                    val accepted = synchronized(signal) {
                        if (stopped || !playbackReady || version != generation) false
                        else { writer = currentWriter; true }
                    }
                    if (!accepted) { source.close(); continue }
                    try {
                        status = "下载中"
                        currentWriter.cache()
                        if (!stopped && synchronized(signal) { generation == version }) {
                            check(cache.isCached(stream.cacheKey, start, length)) { "Prefetch did not reach disk" }
                        }
                        failureCount = 0
                    } finally {
                        synchronized(signal) { writer = null }
                        source.close()
                    }
                } catch (e: Exception) {
                    if (stopped) break
                    if (synchronized(signal) { generation != attemptGeneration }) continue
                    failureCount = (failureCount + 1).coerceAtMost(4)
                    status = "重试等待 (${e.javaClass.simpleName})"
                    waitForChange(minOf(30_000L, 1_000L shl failureCount))
                }
            }
            status = "已停止"
        }
    }
}
