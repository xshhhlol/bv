package dev.aaa1115910.bv.player.impl.exo

import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import java.io.EOFException

/**
 * 磁盘预下载。
 *
 * 在播放位置前面按码率算出一个窗口，用后台线程把这段数据提前写进磁盘缓存，播放器读到时
 * 直接命中本地文件，网络断一下也就不会立刻变成画面卡顿。
 *
 * 几个时机：
 * - **暂停**：不停。暂停恰恰是把窗口填满的好时候，而且窗口有上限，填满就自己歇着；
 * - **快进/拖动**：正在下的那段多半用不上了，立刻中断，按新位置重排；
 * - **切视频/切清晰度**：停掉旧任务换新的，缓存本身留着，切回去还能命中。
 */
@OptIn(UnstableApi::class)
internal class StreamPrefetcher(
    private val cache: Cache,
    /** 预下载的流量也要算进网速里，否则覆盖层上看着像没在下载 */
    private val speedMeter: TransferListener? = null
) {
    companion object {
        private const val TAG = "StreamPrefetcher"

        /** 窗口已经满了，过一会儿再看播放位置有没有推进 */
        private const val IDLE_WAIT_MS = 1_000L

        /** 下载出错后的退避，别对着一个连不上的节点空转 */
        private const val RETRY_WAIT_MS = 3_000L

        /**
         * 起播前先让播放器自己把开头读进来，别跟它抢带宽；
         * 顺带等 seek 落定，免得切清晰度时先从头下一段没用的。
         */
        private const val INITIAL_DELAY_MS = 1_000L

        /** 连续没进展时的退避上限，避免对着一个真写不进去的磁盘空转烧流量 */
        private const val MAX_BACKOFF_MS = 30_000L

        /**
         * 删缓存前等下载线程收手的上限。
         *
         * cancel() 只是置个标志，正卡在 socket 读里的线程不会立刻退出，所以要等一等再删，
         * 免得删完它又把数据写回来。等超时了也照删，剩下的残余交给 LRU。
         */
        private const val CLEAR_JOIN_TIMEOUT_MS = 5_000L
    }

    class Stream(
        val name: String,
        val uri: Uri,
        val cacheKey: String,
        val dataSourceFactory: CacheDataSource.Factory
    ) {
        /**
         * 播放器当前真正读到的字节位置，由 TransferListener 实时更新。
         *
         * 之前是拿 `总长度 × 播放进度/时长` 估的，但 m4s 不是 CBR，误差几十 MB 起步，
         * 预下载全下到了播放位置后面、中间留个洞，等于白下。这个数是精确的。
         */
        @Volatile
        var playerPosition: Long = 0
    }

    private var workers = listOf<Worker>()

    /** 当前这几条流的 cacheKey，退出时按它删自己下过的数据 */
    private var activeKeys = listOf<String>()

    /** 换视频/换清晰度：旧任务全停，位置归零等播放器把真实进度报上来 */
    @Synchronized
    fun setStreams(streams: List<Stream>) {
        val stopping = stopWorkers()
        val previousKeys = activeKeys
        activeKeys = streams.map { it.cacheKey }
        // 换掉的那条流已经没人会读了，别占着盘等 LRU 慢慢淘
        clearAsync(stopping, previousKeys - activeKeys.toSet())
        workers = streams.map { Worker(it).apply { start() } }
    }

    /** 位置跳变，手上这段作废；新位置由 TransferListener 报上来，这里不需要 */
    @Synchronized
    fun onSeek() {
        workers.forEach { it.restartWindow() }
    }

    /**
     * 退出播放页：任务停掉，这次播放落在盘上的数据一并删掉。
     *
     * 磁盘层只是为了扛住这次播放期间的网络抖动，播完就没有留着的理由；
     * 运行期间的总量由 LRU 上限兜底，这里负责让退出之后回到接近零。
     */
    @Synchronized
    fun stopAndClear() {
        val stopping = stopWorkers()
        val keys = activeKeys
        activeKeys = emptyList()
        clearAsync(stopping, keys)
    }

    @Synchronized
    private fun stopWorkers(): List<Worker> {
        val stopping = workers
        stopping.forEach { it.shutdown() }
        workers = emptyList()
        return stopping
    }

    /** join 和删文件都放后台：退出播放页是主线程，不能卡在这儿 */
    private fun clearAsync(stopping: List<Worker>, keys: List<String>) {
        if (keys.isEmpty()) return
        Thread {
            stopping.forEach { runCatching { it.join(CLEAR_JOIN_TIMEOUT_MS) } }
            keys.forEach { key ->
                runCatching { cache.removeResource(key) }
                    .onFailure { Log.w(TAG, "Remove cached $key failed: ${it.javaClass.simpleName}") }
            }
        }.apply {
            name = "bv-prefetch-clear"
            isDaemon = true
        }.start()
    }

    /** 当前磁盘缓存占用，调试信息用 */
    val cachedBytes: Long get() = runCatching { cache.cacheSpace }.getOrDefault(0)

    /**
     * 每条流从当前播放位置往后，还有多少**连续**数据已经落在磁盘上。
     *
     * 这是验证整套两层设计有没有生效的关键数字：
     * 它大于快进增量，快进就是读本地文件、不会转圈；一直是 0 就说明预下载根本没跑起来。
     */
    fun leadSummary(positionMs: Long, durationMs: Long): String {
        val streams = synchronized(this) { workers.map { it.stream } }
        if (streams.isEmpty()) return "idle"
        return streams.joinToString("  ") { stream ->
            val contentLength =
                ContentMetadata.getContentLength(cache.getContentMetadata(stream.cacheKey))
            // 量播放位置往后这一窗口里总共存了多少。
            // 不量「连续长度」是因为播放器正在读的那个字节必然还没入缓存，永远是个空洞，量出来恒为 0
            val lead = runCatching {
                cache.getCachedBytes(
                    stream.cacheKey, stream.playerPosition, PlaybackBufferPolicy.PREFETCH_WINDOW_BYTES
                )
            }.getOrDefault(0L).coerceAtLeast(0)
            val seconds =
                if (contentLength > 0 && durationMs > 0) lead * durationMs / contentLength / 1000 else 0
            "${stream.name}=${lead / 1024 / 1024}MiB/${seconds}s"
        }
    }

    private fun contentLengthLimit(contentLength: Long, start: Long): Long =
        if (contentLength > 0) contentLength - start else Long.MAX_VALUE

    /** 从 [position] 开始**连续**缓存了多少字节；该位置没缓存时 media3 返回空洞长度的负数 */
    private fun cachedLengthAt(cacheKey: String, position: Long, limit: Long): Long =
        runCatching { cache.getCachedLength(cacheKey, position, limit) }
            .getOrDefault(0L).coerceAtLeast(0)

    /** 各 worker 的状态，排查用 */
    fun stateSummary(): String {
        val workers = synchronized(this) { workers }
        if (workers.isEmpty()) return "未启动"
        return workers.joinToString("  ") {
            "${it.stream.name}:${it.state}@${it.stream.playerPosition / 1024 / 1024}MiB"
        }
    }

    private inner class Worker(val stream: Stream) : Thread("bv-prefetch-${stream.name}") {
        @Volatile
        private var stopped = false

        @Volatile
        private var writer: CacheWriter? = null

        /**
         * 这一轮是不是被我们自己叫停的。
         *
         * 不能靠 InterruptedIOException 判断：SocketTimeoutException 也是它的子类，
         * 把读超时当成「我们取消的」会变成不退避地死磕一个连不上的节点。
         */
        @Volatile
        private var windowCancelled = false

        private val idle = Object()

        private var fruitlessRounds = 0

        /** 给调试覆盖层看的状态 */
        @Volatile
        var state: String = "启动中"
            internal set

        init {
            isDaemon = true
        }

        fun restartWindow() {
            windowCancelled = true
            writer?.cancel()
            wake()
        }

        fun shutdown() {
            stopped = true
            windowCancelled = true
            writer?.cancel()
            wake()
        }

        private fun wake() = synchronized(idle) { idle.notifyAll() }

        private fun idleWait(timeoutMs: Long) = synchronized(idle) {
            if (!stopped) runCatching { idle.wait(timeoutMs) }
        }

        override fun run() {
            // I/O 为主的活儿，压一档优先级别跟解码抢 CPU；
            // 但不进后台 cgroup，那里会被限到几乎下不动
            Process.setThreadPriority(
                Process.THREAD_PRIORITY_BACKGROUND + Process.THREAD_PRIORITY_MORE_FAVORABLE
            )
            idleWait(INITIAL_DELAY_MS)

            val window = PlaybackBufferPolicy.PREFETCH_WINDOW_BYTES
            while (!stopped) {
                // 从播放器真正读到的位置往后，跳过已经连续缓存好的部分，接着往下下
                val frontier = stream.playerPosition
                val contentLength =
                    ContentMetadata.getContentLength(cache.getContentMetadata(stream.cacheKey))
                val remaining = if (contentLength > 0) contentLength - frontier else Long.MAX_VALUE
                if (remaining <= 0) {
                    state = "已到文件末尾"
                    idleWait(IDLE_WAIT_MS)
                    continue
                }
                val cachedAhead = cachedLengthAt(stream.cacheKey, frontier, window)
                // 已经连续缓存到哪儿就从哪儿接着下；还没有的话跳开一段，别和播放器抢同一段字节。
                // 跳开段要按剩余长度收窄，否则音频这种只有几 MB 的流会被直接推过文件末尾，一个字节都下不了
                val gap = minOf(PlaybackBufferPolicy.PREFETCH_GAP_BYTES, remaining / 4)
                val offset = maxOf(cachedAhead, gap)
                val start = frontier + offset
                var length = (window - offset).coerceAtMost(contentLengthLimit(contentLength, start))
                if (length <= 0) {
                    state = "窗口已满"
                    idleWait(IDLE_WAIT_MS)
                    continue
                }
                val cachedBefore = cache.getCachedBytes(stream.cacheKey, start, length)
                state = "下载中"
                if (!downloadWindow(start, length)) continue

                // 下完了却一个字节都没进缓存，可能是写盘失败，也可能只是这段正被播放器占着写锁。
                // 分不清就别下结论：退避等一会儿再来，别永久停掉一个其实好好的预下载。
                if (cache.getCachedBytes(stream.cacheKey, start, length) > cachedBefore) {
                    fruitlessRounds = 0
                } else {
                    fruitlessRounds++
                    val backoff = (RETRY_WAIT_MS shl (fruitlessRounds - 1).coerceAtMost(3))
                        .coerceAtMost(MAX_BACKOFF_MS)
                    state = "退避中（${backoff / 1000}s）"
                    Log.w(TAG, "Prefetch ${stream.name} cached nothing, back off ${backoff}ms")
                    idleWait(backoff)
                }
            }
        }

        /**
         * 下一个窗口，返回是否完整跑完。
         *
         * CacheWriter 会跳过已经缓存好的部分，只补空洞；被 cancel 或者报错都返回 false，
         * 交给外层重新算位置，不计入「下了没落盘」的判定。
         */
        private fun downloadWindow(start: Long, length: Long): Boolean {
            val spec = DataSpec.Builder()
                .setUri(stream.uri)
                .setPosition(start)
                .setLength(length)
                .build()
            windowCancelled = false
            val dataSource = stream.dataSourceFactory.createDataSource()
            speedMeter?.let { dataSource.addTransferListener(it) }
            val cacheWriter = CacheWriter(dataSource, spec, null, null)
            writer = cacheWriter
            val error = runCatching { cacheWriter.cache() }.exceptionOrNull()
            writer = null

            if (stopped) return false
            // 位置变了或者要停了，直接进下一轮按新位置重算
            if (windowCancelled) return false
            return when (error) {
                null -> true
                // 窗口越过了文件末尾：能下的都下完了，这轮算数，
                // 下一轮拿到已知的总长度就会把窗口裁到末尾
                is EOFException -> true
                else -> {
                    Log.w(TAG, "Prefetch ${stream.name} at $start failed: ${error.javaClass.simpleName}")
                    idleWait(RETRY_WAIT_MS)
                    false
                }
            }
        }
    }
}
