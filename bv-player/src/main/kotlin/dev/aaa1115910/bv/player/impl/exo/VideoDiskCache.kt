package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

/** 一个进程只建一个缓存；初始化、检查索引及清理残留都在后台完成。 */
@OptIn(UnstableApi::class)
internal object VideoDiskCache {
    private var pending: Future<SimpleCache?>? = null
    @Volatile var budgetBytes: Long = 0
        private set

    @Synchronized
    fun prepare(context: Context): Future<SimpleCache?> {
        pending?.let { return it }
        val appContext = context.applicationContext
        return runCacheTask<SimpleCache?>("bv-cache-init") {
            var candidate: SimpleCache? = null
            try {
                val directory = File(appContext.cacheDir, "video_prefetch")
                budgetBytes = PlaybackBufferPolicy.diskTargetBytes(appContext.cacheDir.usableSpace)
                if (budgetBytes == 0L) return@runCacheTask null
                candidate = SimpleCache(directory, LeastRecentlyUsedCacheEvictor(budgetBytes),
                    StandaloneDatabaseProvider(appContext))
                // 构造函数启动异步初始化，返回并不代表缓存可用。
                val ready = candidate
                ready.checkInitialization()
                // 发布实例之前清理，不能异步删除正在被新播放器复用的 key。
                ready.keys.toList().forEach { ready.removeResource(it) }
                ready
            } catch (e: Exception) {
                runCatching { candidate?.release() }
                Log.w("VideoDiskCache", "Cache unavailable: ${e.javaClass.simpleName}")
                null
            }
        }.also { pending = it }
    }

    // 只在同一条流的备用 CDN 间共享缓存；任意 URL 的同名路径并不保证是同一个文件。
    val cacheKeyFactory = CacheKeyFactory { spec -> spec.key ?: cacheKeyOf(spec.uri) }

    fun cacheKeyOf(uri: Uri): String = MessageDigest.getInstance("SHA-256")
        .digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }

    // 每次播放独立命名，旧任务退出时的清理不会误删新播放的同一视频。
    fun newStreamKey(name: String): String = "${UUID.randomUUID()}:$name"
}

// FutureTask 可用于 Android 5/6；不依赖 API 24 才提供的 CompletableFuture。
internal fun <T> runCacheTask(name: String, task: () -> T): Future<T> =
    FutureTask(Callable { task() }).also { future ->
        Thread(future, name).apply { isDaemon = true }.start()
    }
