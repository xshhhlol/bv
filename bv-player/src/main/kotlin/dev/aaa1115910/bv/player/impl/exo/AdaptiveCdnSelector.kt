package dev.aaa1115910.bv.player.impl.exo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 每条流独立比较实际下载速度；测速不阻塞播放器，也不把签名 URL 的结果永久缓存。 */
internal class AdaptiveCdnSelector(
    private val urls: List<String>,
    private val currentIndex: () -> Int,
    private val select: (Int) -> Unit,
    private val probe: suspend (String) -> Double?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var job: Job? = null
    private var lastProbeNanos: Long? = null
    private var closed = false

    @Synchronized
    fun refresh(): Job? {
        if (closed || urls.size < 2) return null
        if (job?.isActive == true) return job
        val now = nanoTime()
        if (lastProbeNanos?.let { now - it < 30_000_000_000L } == true) return null
        lastProbeNanos = now
        job = scope.launch {
            val initial = currentIndex()
            // 每轮最多四个地址（总流量最多 1 MiB），优先包含当前地址。
            val indices = (listOf(initial) + urls.indices).distinct().filter { it in urls.indices }.take(4)
            val permits = Semaphore(3)
            val results = indices.map { index ->
                async {
                    val speed = permits.withPermit {
                        try {
                            probe(urls[index])?.takeIf { it.isFinite() && it > 0 }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                    index to speed
                }
            }.awaitAll().toMap()
            ensureActive()
            synchronized(this@AdaptiveCdnSelector) {
                if (closed) return@synchronized
                // 测速期间可能已经因网络错误切换过，按最新线路比较。
                val current = currentIndex()
                if (current !in results) return@synchronized
                val best = results.entries.filter { it.value != null }.maxByOrNull { it.value!! }
                    ?: return@synchronized
                val currentSpeed = results[current]
                // 差距不到 25% 不换，避免网络轻微波动导致来回跳。
                if (best.key != current && (currentSpeed == null || best.value!! > currentSpeed * 1.25)) {
                    select(best.key)
                }
            }
        }
        return job
    }

    @Synchronized
    fun close() {
        closed = true
        job?.cancel()
        // scope 是本对象拥有的；测试可传入独立子 scope。
        scope.cancel()
    }
}
