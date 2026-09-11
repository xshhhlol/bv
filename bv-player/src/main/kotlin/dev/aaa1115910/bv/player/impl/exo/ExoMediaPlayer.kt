package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.OkHttpUtil
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.formatMinSec

/** 单个分片加载失败后的重试次数，配合 [FallbackUrlDataSource] 一起换 CDN */
private const val LoadRetryCount = 5

@OptIn(UnstableApi::class)
class ExoMediaPlayer(
    private val context: Context,
    private val options: VideoPlayerOptions
) : AbstractVideoPlayer(), Player.Listener {
    var mPlayer: ExoPlayer? = null
    protected var mMediaSource: MediaSource? = null

    private val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
    private val targetBufferBytes = PlaybackBufferPolicy.targetBytes(Runtime.getRuntime().maxMemory())
    @Volatile private var playbackSpeed = 1f
    private val streamFactories = mutableListOf<FallbackUrlDataSource.Factory>()

    private val networkSpeedMeter = NetworkSpeedMeter()

    /** 磁盘层：拿不到缓存就退回直连播放，不影响能不能放 */
    private val diskCache = VideoDiskCache.prepare(context)
    private val prefetcher = StreamPrefetcher({ diskCache.get() }, networkSpeedMeter)
    private val prefetchStreams = mutableListOf<StreamPrefetcher.Stream>()

    // 仍然留给 ExoPlayer 做码率选择用，覆盖层上的网速另算
    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null

    /** 接口给的码率。Format.bitrate 在 progressive mp4 上多半是 NO_VALUE，只能用接口这份 */
    private var videoBitrate = 0
    private var audioBitrate = 0

    @OptIn(UnstableApi::class)
    private val dataSourceFactory =
        OkHttpDataSource.Factory(OkHttpUtil.generateCustomSslOkHttpClient(context)).apply {
            options.userAgent?.let { setUserAgent(it) }
            options.referer?.let { setDefaultRequestProperties(mapOf("referer" to it)) }
        }

    init {
        initPlayer()
    }

    @OptIn(UnstableApi::class)
    override fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                when (options.enableFfmpegAudioRenderer) {
                    true -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    false -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )
            // 首选解码器初始化失败时换一个再试，而不是直接报错停在那儿
            setEnableDecoderFallback(true)
            if (options.enableSoftwareVideoDecoder) {
                // 强制软件解码
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val allDecoders = MediaCodecUtil.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    val softwareDecoders = allDecoders.filter {
                        it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.")
                    }
                    // 兜底回退
                    softwareDecoders.ifEmpty { allDecoders }
                }

            } else {
                // 默认硬件解码
                setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            }
        }
        mPlayer = ExoPlayer
            .Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(buildLoadControl())
            .setBandwidthMeter(bandwidthMeter)
            .setSeekForwardIncrementMs(1000 * 10)
            .setSeekBackIncrementMs(1000 * 5)
            .build()
            .apply {
                // 直接跳到最近的关键帧。精确跳转要从前一个关键帧解码到目标位置，
                // 4K 下这个过程要好几秒，电视上按着方向键找进度会非常难受
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }

        initListener()
    }

    private fun buildLoadControl(): LoadControl = DefaultLoadControl.Builder()
        .setAllocator(allocator)
        .setBufferDurationsMs(
            PlaybackBufferPolicy.MIN_BUFFER_MS,
            PlaybackBufferPolicy.MAX_BUFFER_MS,
            PlaybackBufferPolicy.BUFFER_FOR_PLAYBACK_MS,
            PlaybackBufferPolicy.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
        )
        .setTargetBufferBytes(targetBufferBytes)
        // 字节上限优先，省得高码率视频把内存吃穿
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    private fun initListener() {
        mPlayer?.addListener(this)
        mPlayer?.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                videoDecoderName = decoderName
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                audioDecoderName = decoderName
            }
        })
    }

    @OptIn(UnstableApi::class)
    override fun setHeader(headers: Map<String, String>) {

    }

    @OptIn(UnstableApi::class)
    override fun playUrl(
        videoUrl: String?,
        audioUrl: String?,
        videoBackupUrls: List<String>,
        audioBackupUrls: List<String>,
        videoBitrate: Int,
        audioBitrate: Int
    ) {
        networkSpeedMeter.clear()
        renderedFrameMeter.reset()
        streamFactories.clear()
        prefetchStreams.clear()
        videoDecoderName = null
        audioDecoderName = null
        this.videoBitrate = videoBitrate
        this.audioBitrate = audioBitrate

        val videoMediaSource = videoUrl?.let {
            createMediaSource("video", it, videoBackupUrls, videoBitrate)
        }
        val audioMediaSource = audioUrl?.let {
            createMediaSource("audio", it, audioBackupUrls, audioBitrate)
        }

        val mediaSources = listOfNotNull(videoMediaSource, audioMediaSource)
        mMediaSource = MergingMediaSource(*mediaSources.toTypedArray())

        // 换视频、换清晰度都会走到这儿，旧的预下载任务到此为止
        prefetcher.setStreams(prefetchStreams.toList())
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSource(
        name: String,
        url: String,
        backupUrls: List<String>,
        bitrate: Int
    ): MediaSource {
        val fallbackFactory = FallbackUrlDataSource.Factory(
            dataSourceFactory, listOf(url) + backupUrls,
            requiredBitrate = { (bitrate.toLong() * playbackSpeed).toLong() }
        )
        streamFactories.add(fallbackFactory)

        val cacheKey = VideoDiskCache.newStreamKey(name)
        val prefetchStream = StreamPrefetcher.Stream(
            name, url.toUri(), cacheKey,
            dataSourceFactory = {
                cacheFactory(fallbackFactory, cacheKey, CacheDataSource.FLAG_BLOCK_ON_CACHE)
                    ?.createDataSource()
            }
        ).also { prefetchStreams.add(it) }
        val readFactory = DataSource.Factory {
            // 只读模式不长期占用从播放位置到 EOF 的写锁，后台才能真正写入前方窗口。
            cacheFactory(fallbackFactory, cacheKey, CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                ?.setCacheWriteDataSinkFactory(null)?.createDataSource()
                ?: fallbackFactory.createDataSource()
        }
        val trackedFactory = ReadPositionTrackingFactory(readFactory, networkSpeedMeter, prefetchStream)

        return ProgressiveMediaSource.Factory(trackedFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LoadRetryCount))
            .createMediaSource(MediaItem.fromUri(url))
    }

    /** 在 DataSource 出口计数，磁盘分片与 HTTP Range 都统一成源文件绝对偏移。 */
    @OptIn(UnstableApi::class)
    internal class ReadPositionTrackingFactory(
        private val delegate: DataSource.Factory,
        private val speedMeter: TransferListener,
        private val stream: StreamPrefetcher.Stream
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val source = delegate.createDataSource().apply { addTransferListener(speedMeter) }
            return object : DataSource by source {
                private var position = 0L
                override fun open(dataSpec: DataSpec): Long {
                    val length = source.open(dataSpec)
                    position = dataSpec.position
                    stream.playerPosition = position
                    if (dataSpec.length == C.LENGTH_UNSET.toLong() && length >= 0) {
                        stream.contentLength = position + length
                    }
                    return length
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val count = source.read(buffer, offset, length)
                    if (count > 0) {
                        position += count
                        stream.playerPosition = position
                    }
                    return count
                }
            }
        }
    }

    // 工厂只在加载线程/预下载线程调用；主线程不等待缓存初始化及索引锁。
    private fun cacheFactory(upstream: DataSource.Factory, key: String, flags: Int): CacheDataSource.Factory? {
        val cache = diskCache.get() ?: return null
        return CacheDataSource.Factory().setCache(cache)
            .setUpstreamDataSourceFactory(upstream).setCacheKeyFactory { key }.setFlags(flags)
    }

    @OptIn(UnstableApi::class)
    override fun prepare() {
        mPlayer?.setMediaSource(mMediaSource!!)
        mPlayer?.prepare()
    }

    override fun start() {
        mPlayer?.play()
    }

    override fun pause() {
        mPlayer?.pause()
    }

    override fun stop() {
        prefetcher.setPlaybackReady(false)
        mPlayer?.stop()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override val isPlaying: Boolean
        get() = mPlayer?.isPlaying == true

    override fun seekTo(time: Long) {
        prefetcher.onSeek()
        renderedFrameMeter.reset()
        mPlayer?.seekTo(time)
    }

    override fun release() {
        // 退出播放页就把这次下的数据删掉，不在盘上常驻
        mPlayer?.release()
        prefetcher.stopAndClear()
    }

    override val currentPosition: Long
        get() = mPlayer?.currentPosition ?: 0
    override val duration: Long
        get() = mPlayer?.duration?.coerceAtLeast(0) ?: 0
    override val bufferedPercentage: Int
        get() = mPlayer?.bufferedPercentage ?: 0

    override fun setOptions() {
        mPlayer?.playWhenReady = true
    }

    override var speed: Float
        get() = mPlayer?.playbackParameters?.speed ?: 1f
        set(value) {
            playbackSpeed = value
            mPlayer?.setPlaybackSpeed(value)
        }

    override var collectDebugInfo: Boolean
        get() = networkSpeedMeter.enabled
        set(value) {
            networkSpeedMeter.enabled = value
            networkSpeedMeter.clear()
            renderedFrameMeter.reset()
        }

    /** 最近几秒的实际下载速度，单位 bps */
    override val tcpSpeed: Long
        get() = networkSpeedMeter.bitsPerSecond()

    override fun onIsLoadingChanged(isLoading: Boolean) {
        prefetcher.setPlaybackReady(!isLoading && mPlayer?.playbackState == Player.STATE_READY)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        prefetcher.setPlaybackReady(playbackState == Player.STATE_READY && mPlayer?.isLoading == false)
        when (playbackState) {
            Player.STATE_IDLE -> mPlayerEventListener?.onIdle()
            Player.STATE_BUFFERING -> mPlayerEventListener?.onBuffering()
            Player.STATE_READY -> mPlayerEventListener?.onReady()
            Player.STATE_ENDED -> mPlayerEventListener?.onEnd()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            mPlayerEventListener?.onPlay()
        } else {
            mPlayerEventListener?.onPause()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        // CLOSEST_SYNC 会把落点挪到最近的关键帧，这时候会再补一条 SEEK_ADJUSTMENT，
        // 带的才是真正的播放位置
        if (reason == Player.DISCONTINUITY_REASON_SEEK ||
            reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
        ) {
            renderedFrameMeter.reset()
            mPlayerEventListener?.onSeekProcessed(newPosition.positionMs)
        }
    }

    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
        mPlayerEventListener?.onSeekBack(seekBackIncrementMs)
    }

    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
        mPlayerEventListener?.onSeekForward(seekForwardIncrementMs)
    }

    override val debugInfo: String
        get() {
            val bufferedSeconds =
                ((mPlayer?.bufferedPosition ?: 0) - currentPosition).coerceAtLeast(0) / 1000
            val videoFormat = mPlayer?.videoFormat
            val audioFormat = mPlayer?.audioFormat
            return """
                ${"视频".pad()}${videoFormat?.width?.takeIf { it > 0 } ?: videoWidth} x ${videoFormat?.height?.takeIf { it > 0 } ?: videoHeight}${frameRateText(videoFormat?.frameRate)}  ${formatBitrate(videoBitrate, videoFormat?.bitrate)}
                ${"音频".pad()}${formatBitrate(audioBitrate, audioFormat?.bitrate)}${audioFormat?.sampleRate?.takeIf { it > 0 }?.let { "  ${PlaybackDebugMetrics.sampleRate(it)}" } ?: ""}${audioFormat?.channelCount?.takeIf { it > 0 }?.let { "  ${it}ch" } ?: ""}
                ${"编码".pad()}${videoFormat?.sampleMimeType ?: "-"} (${videoDecoderName ?: "?"})
                ${"".pad()}${audioFormat?.sampleMimeType ?: "-"} (${audioDecoderName ?: getAudioRendererName()})
                ${"渲染".pad()}${renderInfo(videoFormat?.frameRate)}
                ${"网络接收".pad()}${formatSpeed(tcpSpeed)}（近 5 秒，含预下载）
                ${"进度".pad()}${currentPosition.formatMinSec()} / ${duration.formatMinSec()}
                ${"内存缓冲".pad()}${bufferedSeconds}s / 目标 ${PlaybackBufferPolicy.MAX_BUFFER_MS / 1000}s；采样 ${allocator.totalBytesAllocated / 1024 / 1024} / ${targetBufferBytes / 1024 / 1024} MiB
                ${"缓冲终点".pad()}占全片 $bufferedPercentage%（非内存使用率）
                ${"磁盘缓存".pad()}$diskCacheInfo
                ${"读取前方".pad()}${prefetcher.leadSummary()}
                ${"预下载状态".pad()}${prefetcher.stateSummary()}
                ${"最近线路".pad()}${streamFactories.joinToString { it.currentHost }.ifBlank { "-" }}
                ${"播放器".pad()}${androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY}
            """.trimIndent()
        }

    /** 左列对齐，中文按两个字符宽算 */
    private fun String.pad(width: Int = 12): String {
        val cells = fold(0) { acc, char -> acc + if (char.code > 0x2E80) 2 else 1 }
        return this + " ".repeat((width - cells).coerceAtLeast(1))
    }

    private fun frameRateText(frameRate: Float?): String =
        frameRate?.takeIf { it > 0 }?.let { "  ${"%.2f".format(it)} fps" } ?: ""

    /** 优先用接口给的码率，没有再退回容器里解出来的 */
    private fun formatBitrate(fromApi: Int, fromFormat: Int?): String {
        val bps = fromApi.takeIf { it > 0 } ?: fromFormat?.takeIf { it > 0 } ?: return "码率未知"
        return if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1_000_000.0) else "${bps / 1000} kbps"
    }

    private fun formatSpeed(bitsPerSecond: Long): String = when {
        bitsPerSecond <= 0 -> "0 kbps"
        bitsPerSecond >= 1_000_000 -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
        else -> "${bitsPerSecond / 1000} kbps"
    }

    private val renderedFrameMeter = RenderedFrameMeter()

    private fun renderInfo(contentFrameRate: Float?): String {
        val player = mPlayer ?: return "未就绪"
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val fps = renderedFrameMeter.sample(counters?.renderedOutputBufferCount?.toLong() ?: 0,
            android.os.SystemClock.elapsedRealtime(), player.isPlaying)
        val status = when {
            player.playbackState == Player.STATE_BUFFERING -> "缓冲中"
            !player.isPlaying -> "未播放"
            fps == null -> "采样中"
            else -> "%.1f fps".format(fps)
        }
        val expected = contentFrameRate?.takeIf { it > 0 }?.let { " / 期望 %.1f fps".format(it * player.playbackParameters.speed) }.orEmpty()
        // 输出帧率偏低也可能是缓冲、暂停、倍速或采样边界，不能单凭它断言解码器不足。
        return "$status$expected  丢帧 ${counters?.droppedBufferCount ?: 0}"
    }

    private val diskCacheInfo: String
        get() {
            val used = prefetcher.cachedBytes / 1024 / 1024
            val budget = VideoDiskCache.budgetBytes / 1024 / 1024
            return "$used / $budget MiB（目录占用 / 配额）"
        }

    private fun getAudioRendererName(): String {
        val rendererCount = mPlayer?.rendererCount ?: return "UnknownRenderer"
        for (i in 0 until rendererCount) {
            val renderer = mPlayer!!.getRenderer(i)
            if (renderer.trackType == C.TRACK_TYPE_AUDIO && renderer.state != Renderer.STATE_DISABLED) {
                return renderer.name
            }
        }
        return "UnknownRenderer"
    }

    override val videoWidth: Int
        get() = mPlayer?.videoSize?.width ?: 0
    override val videoHeight: Int
        get() = mPlayer?.videoSize?.height ?: 0

    override fun onPlayerError(error: PlaybackException) {
        mPlayerEventListener?.onError(error)
    }
}
