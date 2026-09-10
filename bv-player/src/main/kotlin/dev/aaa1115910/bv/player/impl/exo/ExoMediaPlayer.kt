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

    private val targetBufferBytes = PlaybackBufferPolicy.targetBytes(Runtime.getRuntime().maxMemory())
    @Volatile private var playbackSpeed = 1f
    private val streamFactories = mutableListOf<FallbackUrlDataSource.Factory>()

    private val networkSpeedMeter = NetworkSpeedMeter()

    /** 磁盘层：拿不到缓存就退回直连播放，不影响能不能放 */
    private val diskCache = VideoDiskCache.get(context)
    private val prefetcher = diskCache?.let { StreamPrefetcher(it, networkSpeedMeter) }
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
        .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
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
        prefetcher?.setStreams(prefetchStreams.toList())
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

        // 播放读取也走缓存：命中就不联网，没命中的部分顺手写进磁盘，
        // 换 CDN 的逻辑留在 upstream（和预下载共用，谁探到好节点另一边都跟着受益）
        val readFactory = cacheFactory(
            upstream = fallbackFactory,
            // 缓存读写出问题时绕过缓存继续播，播放本身不能被磁盘拖垮
            flags = CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
        ) ?: fallbackFactory

        val prefetchStream = diskCache?.let {
            StreamPrefetcher.Stream(
                name = name,
                uri = url.toUri(),
                cacheKey = VideoDiskCache.cacheKeyOf(url.toUri()),
                // 预下载反过来：写不进去就得报错，好让它退避而不是闷头重下
                dataSourceFactory = cacheFactory(fallbackFactory, flags = 0)!!
            )
        }?.also { prefetchStreams.add(it) }

        // 预下载要知道播放器真正读到了哪个字节，靠估算会偏几十 MB。
        // 直接写进对应的 Stream：这个回调每读一块就触发一次，不能在里面加锁查表
        val trackedFactory = ReadPositionTrackingFactory(
            delegate = readFactory,
            speedMeter = networkSpeedMeter,
            onPosition = { position -> prefetchStream?.playerPosition = position }
        )

        return ProgressiveMediaSource.Factory(trackedFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LoadRetryCount))
            .createMediaSource(MediaItem.fromUri(url))
    }

    /**
     * 记录播放器读到的字节位置。
     *
     * 只是旁听 TransferListener，不改变数据流；缓存命中和走网络都会走到这里。
     */
    @OptIn(UnstableApi::class)
    private class ReadPositionTrackingFactory(
        private val delegate: DataSource.Factory,
        private val speedMeter: TransferListener,
        private val onPosition: (Long) -> Unit
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            delegate.createDataSource().apply {
                addTransferListener(speedMeter)
                addTransferListener(object : TransferListener {
                    private var position = 0L

                    override fun onTransferInitializing(
                        source: DataSource, dataSpec: DataSpec, isNetwork: Boolean
                    ) = Unit

                    override fun onTransferStart(
                        source: DataSource, dataSpec: DataSpec, isNetwork: Boolean
                    ) {
                        // 必须用 absoluteStreamPosition：从磁盘缓存读时 uri 是缓存分片文件，
                        // dataSpec.position 是分片内偏移（每片从 0 开始），
                        // 用它记录会导致播放器每读一次缓存，位置就被打回一个小数字
                        position = dataSpec.absoluteStreamPosition
                        onPosition(position)
                    }

                    override fun onBytesTransferred(
                        source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int
                    ) {
                        position += bytesTransferred
                        onPosition(position)
                    }

                    override fun onTransferEnd(
                        source: DataSource, dataSpec: DataSpec, isNetwork: Boolean
                    ) = Unit
                })
            }
    }

    @OptIn(UnstableApi::class)
    private fun cacheFactory(upstream: DataSource.Factory, flags: Int): CacheDataSource.Factory? {
        val cache = diskCache ?: return null
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(VideoDiskCache.cacheKeyFactory)
            .setFlags(flags)
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
        mPlayer?.stop()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override val isPlaying: Boolean
        get() = mPlayer?.isPlaying == true

    override fun seekTo(time: Long) {
        mPlayer?.seekTo(time)
        prefetcher?.onSeek()
    }

    override fun release() {
        // 退出播放页就把这次下的数据删掉，不在盘上常驻
        prefetcher?.stopAndClear()
        mPlayer?.release()
    }

    override val currentPosition: Long
        get() = mPlayer?.currentPosition ?: 0
    override val duration: Long
        get() = mPlayer?.duration ?: 0
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
            if (!value) networkSpeedMeter.clear()
            networkSpeedMeter.enabled = value
        }

    /** 最近几秒的实际下载速度，单位 bps */
    override val tcpSpeed: Long
        get() = networkSpeedMeter.bitsPerSecond()

    override fun onPlaybackStateChanged(playbackState: Int) {
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
            prefetcher?.onSeek()
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
                ${"视频".pad()}${videoFormat?.width ?: videoWidth} x ${videoFormat?.height ?: videoHeight}${frameRateText(videoFormat?.frameRate)}  ${formatBitrate(videoBitrate, videoFormat?.bitrate)}
                ${"音频".pad()}${formatBitrate(audioBitrate, audioFormat?.bitrate)}${audioFormat?.sampleRate?.takeIf { it > 0 }?.let { "  ${it / 1000} kHz" } ?: ""}${audioFormat?.channelCount?.takeIf { it > 0 }?.let { "  ${it}ch" } ?: ""}
                ${"编码".pad()}${videoFormat?.sampleMimeType ?: "-"} (${videoDecoderName ?: "?"})
                ${"".pad()}${audioFormat?.sampleMimeType ?: "-"} (${getAudioRendererName()})
                ${"渲染".pad()}${renderInfo(videoFormat?.frameRate)}
                ${"网速".pad()}${formatSpeed(tcpSpeed)}
                ${"进度".pad()}${currentPosition.formatMinSec()} / ${duration.formatMinSec()}
                ${"内存缓冲".pad()}${bufferedSeconds}s / 上限 ${PlaybackBufferPolicy.MAX_BUFFER_MS / 1000}s、${targetBufferBytes / 1024 / 1024} MiB (已缓冲 $bufferedPercentage%)
                ${"磁盘缓存".pad()}$diskCacheInfo
                ${"预下载领先".pad()}${prefetcher?.leadSummary(currentPosition, duration) ?: "未启用"}
                ${"预下载状态".pad()}${prefetcher?.stateSummary() ?: "未启用"}
                ${"CDN".pad()}${streamFactories.joinToString { it.currentHost }.ifBlank { "-" }}
                ${"播放器".pad()}${androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY}
            """.trimIndent()
        }

    /** 左列对齐，中文按两个字符宽算 */
    private fun String.pad(width: Int = 12): String {
        val cells = fold(0) { acc, char -> acc + if (char.code > 0x2E80) 2 else 1 }
        return this + " ".repeat((width - cells).coerceAtLeast(1))
    }

    private fun frameRateText(frameRate: Float?): String =
        frameRate?.takeIf { it > 0 }?.let { "  ${"%.0f".format(it)} fps" } ?: ""

    /** 优先用接口给的码率，没有再退回容器里解出来的 */
    private fun formatBitrate(fromApi: Int, fromFormat: Int?): String {
        val bps = fromApi.takeIf { it > 0 } ?: fromFormat?.takeIf { it > 0 } ?: return "码率未知"
        return if (bps >= 1_000_000) "%.1f Mbps".format(bps / 1_000_000.0) else "${bps / 1000} kbps"
    }

    private fun formatSpeed(bitsPerSecond: Long): String = when {
        bitsPerSecond <= 0 -> "-"
        bitsPerSecond >= 1_000_000 -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
        else -> "${bitsPerSecond / 1000} kbps"
    }

    private var lastRenderedCount = 0L
    private var lastRenderedAt = 0L
    private var renderedFps = 0.0

    /**
     * 实际渲染帧率。
     *
     * 这是判断「卡顿到底是不是解码器扛不住」的直接证据：缓冲是满的、带宽是富余的，
     * 而实际渲染帧率明显低于内容帧率，那就跟网络和缓冲都没关系，是解码器解不过来。
     */
    private fun renderInfo(contentFrameRate: Float?): String {
        val counters = mPlayer?.videoDecoderCounters
        val rendered = counters?.renderedOutputBufferCount?.toLong() ?: 0
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastRenderedAt > 0 && now > lastRenderedAt) {
            val delta = rendered - lastRenderedCount
            // 换视频/跳转后计数器会重置，负增量直接丢掉
            if (delta >= 0) renderedFps = delta * 1000.0 / (now - lastRenderedAt)
        }
        lastRenderedCount = rendered
        lastRenderedAt = now

        val target = contentFrameRate?.takeIf { it > 0 }
        val actual = "%.1f".format(renderedFps)
        val dropped = counters?.droppedBufferCount ?: 0
        return if (target == null) {
            "$actual fps  丢帧 $dropped"
        } else {
            val ratio = renderedFps / target
            val verdict = when {
                renderedFps <= 0 -> ""
                ratio < 0.85 -> "  ← 解码器跟不上"
                else -> ""
            }
            "$actual / ${"%.0f".format(target)} fps  丢帧 $dropped$verdict"
        }
    }

    private val diskCacheInfo: String
        get() {
            val prefetcher = prefetcher ?: return "off"
            val used = prefetcher.cachedBytes / 1024 / 1024
            val budget = PlaybackBufferPolicy.DISK_CACHE_BYTES / 1024 / 1024
            val window = PlaybackBufferPolicy.PREFETCH_WINDOW_BYTES / 1024 / 1024
            return "$used / $budget MiB (prefetch $window MiB ahead)"
        }

    private fun getAudioRendererName(): String {
        val rendererCount = mPlayer?.rendererCount ?: return "UnknownRenderer"
        for (i in 0 until rendererCount) {
            val renderer = mPlayer!!.getRenderer(i)
            if (renderer.trackType == C.TRACK_TYPE_AUDIO && renderer.state == Renderer.STATE_STARTED) {
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
