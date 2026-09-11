package dev.aaa1115910.bv.player.impl.exo

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.core.net.toUri
import java.io.IOException

/**
 * 会自动换 CDN 的 DataSource。
 *
 * B 站每条流除了主地址还会给若干个备用地址。默认的做法是只认主地址，节点抽风时就在同一个地址上
 * 重试到超时，这段时间画面是卡住的；这里一旦某个地址打不开就立刻换下一个，并记住换到了哪个，
 * 后面的续传请求直接从这个能用的地址开始，不再每次都去撞那个坏节点。
 */
@OptIn(UnstableApi::class)
class FallbackUrlDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val urls: List<Uri>,
    private val preferredIndexHolder: PreferredIndexHolder,
    private val requiredBitrate: () -> Long = { 0 },
    private val nanoTime: () -> Long = System::nanoTime
) : DataSource {
    companion object {
        private const val TAG = "FallbackUrlDataSource"
    }

    /** 在同一条流的所有 DataSource 之间共享「当前哪个地址是好的」 */
    class PreferredIndexHolder {
        @Volatile
        var index: Int = 0

        private var lastSlowSwitchNanos: Long? = null

        private val monitors = mutableMapOf<Int, SlowReadMonitor>()
        private var slowIndex: Int? = null

        @Synchronized
        fun recordRead(index: Int, count: Int, nanos: Long, bitrate: Long): Boolean {
            val slow = monitors.getOrPut(index) { SlowReadMonitor() }.record(count, nanos, bitrate)
            if (slow) slowIndex = index
            return slow
        }

        @Synchronized
        fun consumeSlowSwitch(index: Int, nowNanos: Long): Boolean {
            if (slowIndex != index || !allowSlowSwitch(nowNanos)) return false
            slowIndex = null
            monitors[index]?.reset()
            return true
        }

        @Synchronized
        fun allowSlowSwitch(nowNanos: Long): Boolean {
            val last = lastSlowSwitchNanos
            if (last != null && nowNanos - last < 30_000_000_000L) return false
            lastSlowSwitchNanos = nowNanos
            return true
        }
    }

    private val transferListeners = mutableListOf<TransferListener>()
    private var currentSource: DataSource? = null
    private var currentUri: Uri? = null
    private var currentIndex = -1

    /** 本次 open 的原始请求，换节点续传时按它算偏移 */
    private var openedSpec: DataSpec? = null

    /** 本次 open 之后已经读出去的字节数 */
    private var bytesRead = 0L
    private var expectedLength = -1L
    private var switchBeforeNextRead = false

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        openedSpec = dataSpec
        bytesRead = 0L
        switchBeforeNextRead = false
        // 磁盘预下载按小块重新 open，低速样本与待切换状态必须跨请求保留。
        if (urls.size > 1 && preferredIndexHolder.consumeSlowSwitch(preferredIndexHolder.index, nanoTime())) {
            preferredIndexHolder.index = (preferredIndexHolder.index + 1) % urls.size
        }
        expectedLength = openFrom(dataSpec, buildCandidates(dataSpec.uri))
        return expectedLength
    }

    /** 按给定顺序挨个试着打开，第一个能开的就留下 */
    private fun openFrom(spec: DataSpec, candidates: List<Pair<Int, Uri>>): Long {
        var lastError: IOException? = null

        candidates.forEach { (index, uri) ->
            val source = upstreamFactory.createDataSource()
            transferListeners.forEach { source.addTransferListener(it) }
            try {
                val bytes = source.open(if (uri == spec.uri) spec else spec.withUri(uri))
                currentSource = source
                currentUri = uri
                currentIndex = index
                preferredIndexHolder.index = index
                return bytes
            } catch (e: IOException) {
                lastError = e
                runCatching { source.close() }
                Log.w(TAG, "Open [${uri.host}] failed: ${e.javaClass.simpleName}, try next cdn")
            }
        }

        throw lastError ?: IOException("No available url")
    }

    /** 从上次成功的地址开始排，剩下的按原顺序补在后面 */
    private fun buildCandidates(specUri: Uri): List<Pair<Int, Uri>> {
        if (urls.isEmpty()) return listOf(0 to specUri)
        val start = preferredIndexHolder.index.coerceIn(urls.indices)
        val ordered = urls.indices.map { (start + it) % urls.size }
        return ordered.map { it to urls[it] }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (expectedLength >= 0 && bytesRead >= expectedLength) return -1
        if (switchBeforeNextRead) {
            switchBeforeNextRead = false
            if (preferredIndexHolder.consumeSlowSwitch(currentIndex, nanoTime())) {
                tryFasterCdn(buffer, offset, length)?.let { return it }
            }
        }
        val source = currentSource ?: throw IOException("DataSource is not opened")
        return try {
            val start = nanoTime()
            val count = readChecked(source, buffer, offset, length)
            if (count > 0) {
                bytesRead += count
                switchBeforeNextRead = urls.size > 1 &&
                    preferredIndexHolder.recordRead(currentIndex, count, nanoTime() - start, requiredBitrate())
            }
            count
        } catch (e: IOException) {
            // 能连上但读一半卡死/断开的节点最难受：抛给上层重试的话，preferredIndex 还指着它，
            // 下次照样从这儿开始。这里直接换个节点，从已经读到的位置续上
            readFromNextCdn(buffer, offset, length, e)
        }
    }

    private fun readChecked(source: DataSource, buffer: ByteArray, offset: Int, length: Int): Int {
        val count = source.read(buffer, offset, length)
        if (count == -1 && expectedLength >= 0 && bytesRead < expectedLength) {
            throw IOException("Unexpected EOF at $bytesRead of $expectedLength bytes")
        }
        return count
    }

    /** 低速但仍可读时只探测一个备选；备选失败继续旧连接，不制造一次新的播放错误。 */
    private fun tryFasterCdn(buffer: ByteArray, offset: Int, length: Int): Int? {
        val spec = openedSpec ?: return null
        val oldSource = currentSource ?: return null
        val oldIndex = currentIndex
        val oldUri = currentUri
        val candidate = buildCandidates(spec.uri).firstOrNull { it.first != oldIndex } ?: return null
        val resumeSpec = if (bytesRead > 0) spec.subrange(bytesRead) else spec
        try {
            openFrom(resumeSpec, listOf(candidate))
            val count = readChecked(currentSource!!, buffer, offset, length)
            if (count <= 0) throw IOException("No data from alternate CDN")
            bytesRead += count
            runCatching { oldSource.close() }
            Log.i(TAG, "Slow CDN ${oldUri?.host} -> ${candidate.second.host}, resumed at ${resumeSpec.position}")
            return count
        } catch (_: IOException) {
            if (currentSource !== oldSource) runCatching { currentSource?.close() }
            currentSource = oldSource
            currentUri = oldUri
            currentIndex = oldIndex
            preferredIndexHolder.index = oldIndex
            return null
        }
    }

    /** 换到下一个能用的节点续读，都不行就把原始错误抛回去 */
    private fun readFromNextCdn(
        buffer: ByteArray,
        offset: Int,
        length: Int,
        cause: IOException
    ): Int {
        val spec = openedSpec ?: throw cause
        if (urls.size < 2) throw cause

        Log.w(TAG, "Read [${currentUri?.host}] failed at $bytesRead bytes: ${cause.javaClass.simpleName}, switch cdn")
        runCatching { currentSource?.close() }
        currentSource = null

        val failedIndex = currentIndex
        val resumeSpec = if (bytesRead > 0) spec.subrange(bytesRead) else spec
        // 坏掉的这个跳过，剩下的按原来的优先级试
        val candidates = buildCandidates(spec.uri).filter { it.first != failedIndex }

        candidates.forEach { candidate ->
            if (runCatching { openFrom(resumeSpec, listOf(candidate)) }.isFailure) return@forEach
            val source = currentSource ?: return@forEach
            try {
                return readChecked(source, buffer, offset, length).also { if (it > 0) bytesRead += it }
            } catch (e: IOException) {
                Log.w(TAG, "Read [${candidate.second.host}] after switching failed: ${e.javaClass.simpleName}")
                runCatching { source.close() }
                currentSource = null
            }
        }

        // 一个都没救回来，至少把偏好挪开，别让上层重试时又从坏节点开始
        if (failedIndex >= 0) preferredIndexHolder.index = (failedIndex + 1) % urls.size
        throw cause
    }

    override fun getUri(): Uri? = currentSource?.uri ?: currentUri

    override fun getResponseHeaders(): Map<String, List<String>> =
        currentSource?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = currentSource ?: return
        currentSource = null
        source.close()
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        urls: List<String>,
        private val requiredBitrate: () -> Long = { 0 }
    ) : DataSource.Factory {
        private val uris = urls.distinct().map { it.toUri() }
        private val preferredIndexHolder = PreferredIndexHolder()
        val currentHost: String
            get() = uris.getOrNull(preferredIndexHolder.index)?.host.orEmpty()

        override fun createDataSource(): DataSource =
            FallbackUrlDataSource(upstreamFactory, uris, preferredIndexHolder, requiredBitrate)
    }
}
