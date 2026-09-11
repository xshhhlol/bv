package dev.aaa1115910.bv.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aaa1115910.bv.player.impl.exo.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@UnstableApi
@RunWith(AndroidJUnit4::class)
class DiskPrefetchTest {
    @Test
    fun foregroundOpenDoesNotLockOutPrefetchAndReplayUsesDiskOnly() = Fixture().use { f ->
        val player = f.openPlayer()
        val buffer = ByteArray(128 * 1024)
        player.read(buffer, 0, buffer.size)
        val frontier = f.stream.playerPosition
        f.prefetcher.setPlaybackReady(true)
        await { f.cache.isCached(f.stream.cacheKey, frontier, f.content.size - frontier) }
        player.close()
        assertTrue(f.cache.cacheSpace > 0)
        val received = f.networkBytes.get()
        val replay = f.playerFactory().createDataSource()
        replay.open(DataSpec.Builder().setUri(f.uri).setPosition(frontier).setLength(buffer.size.toLong()).build())
        assertEquals(buffer.size, replay.read(buffer, 0, buffer.size))
        assertArrayEquals(f.content.copyOfRange(frontier.toInt(), frontier.toInt() + buffer.size), buffer)
        assertEquals(frontier + buffer.size, f.stream.playerPosition)
        assertEquals(received, f.networkBytes.get())
        replay.close()
    }

    @Test
    fun foregroundBufferingGetsPriorityAndShortAudioHasNoSkippedGap() = Fixture(size = 200_000).use { f ->
        val player = f.openPlayer()
        val before = f.networkBytes.get()
        Thread.sleep(100)
        assertEquals(before, f.networkBytes.get())
        assertEquals(0L, f.cache.cacheSpace)
        f.prefetcher.setPlaybackReady(true)
        await { f.cache.isCached(f.stream.cacheKey, 0, f.content.size.toLong()) }
        await { f.stream.contiguousBytes == f.content.size.toLong() }
        assertEquals(f.content.size.toLong(), f.stream.windowCachedBytes)
        player.close()
    }

    @Test
    fun seekInvalidatesOldPositionAndFillsFromNewFrontier() = Fixture().use { f ->
        f.openPlayer().close()
        f.prefetcher.onSeek()
        f.prefetcher.setPlaybackReady(true)
        Thread.sleep(100)
        assertEquals(-1L, f.stream.playerPosition)
        assertEquals(0L, f.cache.cacheSpace)
        val target = 3L * 1024 * 1024
        val player = f.playerFactory().createDataSource()
        player.open(DataSpec.Builder().setUri(f.uri).setPosition(target).build())
        await { f.cache.isCached(f.stream.cacheKey, target, f.content.size - target) }
        assertEquals(0L, f.cache.getCachedBytes(f.stream.cacheKey, 0, target))
        player.close()
    }

    @Test
    fun movingWindowEvictsOldBlocksWithoutExceedingDiskBudget() = Fixture(budget = 4L * 1024 * 1024).use { f ->
        f.openPlayer().close()
        f.prefetcher.setPlaybackReady(true)
        val window = PlaybackBufferPolicy.windowBytes(f.budget, 1)
        await { f.cache.isCached(f.stream.cacheKey, 0, window) }
        for (position in listOf(window, window * 2)) {
            f.prefetcher.onSeek()
            f.playerFactory().createDataSource().apply {
                open(DataSpec.Builder().setUri(f.uri).setPosition(position).build())
                close()
            }
            await { f.cache.isCached(f.stream.cacheKey, position, window) }
            assertTrue(f.cache.cacheSpace <= f.budget)
        }
        assertEquals(0L, f.cache.getCachedBytes(f.stream.cacheKey, 0, window))
        val received = f.networkBytes.get()
        Thread.sleep(100)
        assertEquals("a full window must not keep downloading", received, f.networkBytes.get())
    }

    @Test
    fun lateOldWriteIsStoppedBeforeCleanupAndCannotDeleteNewSession() = Fixture().use { f ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.beforeNetworkRead = { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
        f.openPlayer().close()
        f.prefetcher.setPlaybackReady(true)
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        val oldKey = f.stream.cacheKey
        val stopped = f.prefetcher.stopAndClear()
        try {
            assertFalse(stopped.isDone)
            val newKey = VideoDiskCache.newStreamKey("video")
            assertNotEquals(oldKey, newKey)
            val cacheSource = f.cacheFactory(newKey).createDataSource()
            f.beforeNetworkRead = null
            val writer = androidx.media3.datasource.cache.CacheWriter(cacheSource,
                DataSpec.Builder().setUri(f.uri).setKey(newKey).setLength(1024).build(), null, null)
            writer.cache()
            release.countDown()
            stopped.get(5, TimeUnit.SECONDS)
            assertEquals(0L, f.cache.getCachedBytes(oldKey, 0, f.content.size.toLong()))
            assertEquals(1024L, f.cache.getCachedBytes(newKey, 0, 1024))
        } finally { release.countDown() }
    }

    @Test
    fun keysDoNotCollideForSamePathWithDifferentContentQueriesOrHosts() {
        assertNotEquals(VideoDiskCache.cacheKeyOf(Uri.parse("https://one.example/video?id=1")),
            VideoDiskCache.cacheKeyOf(Uri.parse("https://one.example/video?id=2")))
        assertNotEquals(VideoDiskCache.cacheKeyOf(Uri.parse("https://one.example/video")),
            VideoDiskCache.cacheKeyOf(Uri.parse("https://two.example/video")))
        val explicit = DataSpec.Builder().setUri("https://two.example/video").setKey("same-stream").build()
        assertEquals("same-stream", VideoDiskCache.cacheKeyFactory.buildCacheKey(explicit))
    }

    @Test
    fun debugSnapshotDoesNotAcquireCacheLock() = Fixture(size = 1024).use { f ->
        f.openPlayer().close()
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread { synchronized(f.cache) { locked.countDown(); release.await(3, TimeUnit.SECONDS) } }
        holder.start()
        assertTrue(locked.await(2, TimeUnit.SECONDS))
        try {
            val begin = System.nanoTime()
            f.prefetcher.leadSummary()
            f.prefetcher.stateSummary()
            f.prefetcher.cachedBytes
            assertTrue("debug must use a snapshot", System.nanoTime() - begin < 100_000_000)
        } finally { release.countDown(); holder.join() }
    }

    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < end) { "condition timed out" }
            Thread.sleep(10)
        }
    }

    private class Fixture(size: Int = 6 * 1024 * 1024, val budget: Long = 32L * 1024 * 1024) : AutoCloseable {
        val content = ByteArray(size) { (it % 251).toByte() }
        val uri = Uri.parse("https://one.example/video")
        private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cache-test-${UUID.randomUUID()}")
        @Suppress("DEPRECATION")
        val cache = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(budget)).apply { checkInitialization() }
        val networkBytes = AtomicLong()
        @Volatile var beforeNetworkRead: (() -> Unit)? = null
        private val upstream = DataSource.Factory {
            val source = ByteArrayDataSource(content)
            object : DataSource by source {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    beforeNetworkRead?.invoke()
                    return source.read(buffer, offset, length).also { if (it > 0) networkBytes.addAndGet(it.toLong()) }
                }
            }
        }
        private val streamKey = VideoDiskCache.newStreamKey("video")
        val stream: StreamPrefetcher.Stream = StreamPrefetcher.Stream("video", uri, streamKey,
            { cacheFactory(streamKey).createDataSource() })
        val prefetcher = StreamPrefetcher({ cache }, diskBudget = { budget }, idleWaitMs = 10).apply { setStreams(listOf(stream)) }
        fun cacheFactory(key: String) = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory { key }.setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
        fun playerFactory(): DataSource.Factory = ExoMediaPlayer.ReadPositionTrackingFactory(
            cacheFactory(stream.cacheKey).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).setCacheWriteDataSinkFactory(null),
            NetworkSpeedMeter(), stream)
        fun openPlayer(): DataSource = playerFactory().createDataSource().apply { open(DataSpec.Builder().setUri(this@Fixture.uri).build()) }
        override fun close() {
            prefetcher.stopAndClear().get(5, TimeUnit.SECONDS)
            cache.release()
            dir.deleteRecursively()
        }
    }
}
