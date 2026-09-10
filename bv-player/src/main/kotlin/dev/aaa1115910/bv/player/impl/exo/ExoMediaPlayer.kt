package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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

/** 起播前先攒这么多，太大起播慢，太小容易刚播就卡 */
private const val BufferForPlaybackMs = 1_500

/** 卡顿恢复后多攒一点再接着播，避免「卡一下播一下」 */
private const val BufferForPlaybackAfterRebufferMs = 4_000

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

    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null

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
            BufferForPlaybackMs,
            BufferForPlaybackAfterRebufferMs
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
        videoDecoderName = null
        audioDecoderName = null

        val videoMediaSource = videoUrl?.let {
            createMediaSource(it, videoBackupUrls, videoBitrate)
        }
        val audioMediaSource = audioUrl?.let {
            createMediaSource(it, audioBackupUrls, audioBitrate)
        }

        val mediaSources = listOfNotNull(videoMediaSource, audioMediaSource)
        mMediaSource = MergingMediaSource(*mediaSources.toTypedArray())
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSource(url: String, backupUrls: List<String>, bitrate: Int): MediaSource {
        val factory = FallbackUrlDataSource.Factory(
            dataSourceFactory, listOf(url) + backupUrls,
            requiredBitrate = { (bitrate.toLong() * playbackSpeed).toLong() }
        )
        streamFactories.add(factory)
        return ProgressiveMediaSource.Factory(factory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LoadRetryCount))
            .createMediaSource(MediaItem.fromUri(url))
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
    }

    override fun release() {
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

    /** 估算的下载速度，单位 bps */
    override val tcpSpeed: Long
        get() = bandwidthMeter.bitrateEstimate

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
            val droppedFrames = mPlayer?.videoDecoderCounters?.droppedBufferCount ?: 0
            return """
                player: ${androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY}
                time: ${currentPosition.formatMinSec()} / ${duration.formatMinSec()}
                buffered: $bufferedPercentage% (${bufferedSeconds}s)
                buffer budget: ${targetBufferBytes / 1024 / 1024} MiB (target 120–180s)
                CDN: ${streamFactories.joinToString { it.currentHost }}
                network estimate: ${tcpSpeed / 1000} kbps
                resolution: ${mPlayer?.videoSize?.width} x ${mPlayer?.videoSize?.height}
                audio: ${mPlayer?.audioFormat?.bitrate ?: 0} kbps
                video codec: ${mPlayer?.videoFormat?.sampleMimeType ?: "null"} (${videoDecoderName ?: "unknown"})
                audio codec: ${mPlayer?.audioFormat?.sampleMimeType ?: "null"} (${getAudioRendererName()})
                dropped frames: $droppedFrames
            """.trimIndent()
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
