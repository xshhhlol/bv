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

/**
 * 进程内唯一的视频磁盘缓存。
 *
 * SimpleCache 同一个目录只允许存在一个实例，而播放器会随着页面进出反复创建销毁，
 * 所以缓存挂在进程上，不跟着播放器释放；退出再进来还能命中上次下好的数据。
 * 放在 cacheDir 下，系统空间紧张时可以自己回收。
 */
@OptIn(UnstableApi::class)
internal object VideoDiskCache {
    private const val TAG = "VideoDiskCache"
    private const val DIR_NAME = "video_prefetch"

    @Volatile
    private var cache: SimpleCache? = null

    /** 建过一次没成功就不再试：失败通常是空间或权限问题，重试只会每次开播都白卡一下 */
    private var initFailed = false

    @Synchronized
    fun get(context: Context): SimpleCache? {
        cache?.let { return it }
        if (initFailed) return null
        initFailed = true
        val appContext = context.applicationContext
        val dir = File(appContext.cacheDir, DIR_NAME)
        // 建缓存失败（磁盘只读、目录被占等）不能连播放一起搞挂，退回直连播放就是了
        return runCatching {
            SimpleCache(
                dir,
                LeastRecentlyUsedCacheEvictor(PlaybackBufferPolicy.DISK_CACHE_BYTES),
                StandaloneDatabaseProvider(appContext)
            )
        }.onFailure {
            Log.w(TAG, "Create disk cache failed: ${it.javaClass.simpleName}, play without disk cache")
        }.getOrNull()?.also {
            cache = it
            initFailed = false
            clearStaleAsync(it)
            Log.i(
                TAG,
                "Disk cache ready at ${dir.path}, " +
                    "budget ${PlaybackBufferPolicy.DISK_CACHE_BYTES / 1024 / 1024} MiB"
            )
        }
    }

    /**
     * 清掉上个进程留下的数据。
     *
     * 正常退出播放页会自己删干净，但进程被系统杀掉时来不及删，残余会一直留到 LRU 淘汰。
     * 这会儿刚建好缓存、还没有人开始读写，盘上的东西必然都是上次剩的，可以整个清掉。
     * key 从内存索引里取，很快；删文件放后台，别卡在播放器创建上。
     */
    private fun clearStaleAsync(cache: SimpleCache) {
        val stale = runCatching { cache.keys.toList() }.getOrDefault(emptyList())
        if (stale.isEmpty()) return
        Thread {
            stale.forEach { key -> runCatching { cache.removeResource(key) } }
            Log.i(TAG, "Cleared ${stale.size} leftover entries from the previous run")
        }.apply {
            name = "bv-cache-clear-stale"
            isDaemon = true
        }.start()
    }

    /**
     * B 站同一条流会给多个 CDN 地址，host 和签名参数不同但路径一致，所以按路径做 key。
     *
     * 用整条 URL 的话，换 CDN、地址过期重新取到的新链接都会各自算一份缓存，
     * 刚下好的数据转头就命不中了。
     */
    val cacheKeyFactory = CacheKeyFactory { dataSpec -> cacheKeyOf(dataSpec.uri) }

    fun cacheKeyOf(uri: Uri): String =
        uri.path?.takeIf { it.isNotBlank() } ?: uri.toString()
}
