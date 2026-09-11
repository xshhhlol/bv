package dev.aaa1115910.bv.viewmodel.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ecs.component.filter.TypeFilter
import com.kuaishou.akdanmaku.render.SimpleRenderer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.PlayData
import dev.aaa1115910.biliapi.entity.video.HeartbeatVideoType
import dev.aaa1115910.biliapi.entity.video.VideoPage
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.repositories.VideoPlayRepository
import dev.aaa1115910.bilisubtitle.SubtitleParser
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.controllers.DanmakuType
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.PlayerType
import dev.aaa1115910.bv.entity.Resolution
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.VideoPlayerListener
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.impl.exo.ExoPlayerFactory
import dev.aaa1115910.bv.repository.VideoInfoRepository
import dev.aaa1115910.bv.screen.settings.content.ActionAfterPlayItems
import dev.aaa1115910.bv.ui.effect.PlayerUiEffect
import dev.aaa1115910.bv.ui.state.DanmakuState
import dev.aaa1115910.bv.ui.state.MediaProfileState
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.ui.state.PlayerUiState
import dev.aaa1115910.bv.ui.state.SeekerState
import dev.aaa1115910.bv.ui.state.SubtitleState
import dev.aaa1115910.bv.util.CodecUtil
import android.os.SystemClock
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.android.annotation.KoinViewModel
import java.net.URI
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException

/** 播放出错后最多自动换地址重试几次 */
private const val MAX_PLAY_RETRY = 3
private const val RETRY_DELAY_MS = 800L
private const val RETRY_RESET_DELAY_MS = 10_000L

/**
 * 更新播放状态，但不覆盖已有的错误状态。
 *
 * ExoPlayer 报错时的回调顺序是 onPlayerError -> onPlaybackStateChanged -> onIsPlayingChanged，
 * 最后那一下会把刚设好的 Error 冲成 Paused，错误提示就再也显示不出来了。
 */
private fun PlayerUiState.copyKeepingError(newState: PlayerState): PlayerUiState =
    if (playerState is PlayerState.Error) this else copy(playerState = newState)

/** 调试信息刷新间隔，不跟进度条的 10Hz 走 */
private const val DEBUG_INFO_INTERVAL_MS = 1000L

@KoinViewModel
class VideoPlayerV3ViewModel(
    private val videoInfoRepository: VideoInfoRepository,
    private val videoPlayRepository: VideoPlayRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger { }

    var videoPlayer: AbstractVideoPlayer? by mutableStateOf(null)
        private set
    var danmakuPlayer: DanmakuPlayer? by mutableStateOf(null)
        private set

    private var playData: PlayData? = null

    private val detachedWorkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var danmakuConfig = DanmakuConfig()
    private val danmakuTypeFilter = TypeFilter()

    private val _uiState = MutableStateFlow(
        // 覆盖安装后仍尊重已保存的调试开关。
        PlayerUiState(showPlayerInfo = Prefs.showPlayerInfo)
    )
    val uiState = _uiState.asStateFlow()
    private val _seekerState = MutableStateFlow(SeekerState())
    val seekerState = _seekerState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<PlayerUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    private var seekerUpdateJob: Job? = null

    private var lastDebugInfoAt = 0L
    private var lastDebugInfo = ""

    // 只读播放器/后台缓存快照，每秒刷新一次。关闭时停止调试流量采集。
    private var lastDebugSource: Any? = null
    private fun currentDebugInfo(player: AbstractVideoPlayer): String {
        if (!_uiState.value.showPlayerInfo) {
            lastDebugInfo = ""
            return ""
        }
        val state = _uiState.value
        val source = player to Triple(state.aid, state.cid, state.mediaProfileState)
        if (source != lastDebugSource) {
            lastDebugSource = source
            lastDebugInfo = ""
        }
        val now = SystemClock.elapsedRealtime()
        if (lastDebugInfo.isNotBlank() && now - lastDebugInfoAt < DEBUG_INFO_INTERVAL_MS) return lastDebugInfo
        lastDebugInfoAt = now
        lastDebugInfo = player.debugInfo
        return lastDebugInfo
    }

    /** 调试覆盖层开关，控制条上的按钮可以随时翻转，翻完记回设置里 */
    fun togglePlayerInfo() {
        val show = !_uiState.value.showPlayerInfo
        Prefs.showPlayerInfo = show
        // 关掉之后连采集都停掉，不只是不显示
        videoPlayer?.collectDebugInfo = show
        lastDebugInfo = ""
        _uiState.update { it.copy(showPlayerInfo = show) }
        updateSeekerState()
    }
    private var clockUpdateJob: Job? = null
    private var heartbeatJob: Job? = null
    private var loadVideoJob: Job? = null
    private var loadDetailJob: Job? = null

    /** 播放出错自动重试的次数与待恢复位置 */
    private var playRetryCount = 0
    private var pendingResumePosition = 0L
    private var playRetryJob: Job? = null
    private var playRetryResetJob: Job? = null

    private var onlineCountJob: Job? = null

    private var backToStartCountdownJob: Job? = null
    private var playNextCountdownJob: Job? = null
    private var previewTipCountdownJob: Job? = null

    private val videoPlayerListener = object : VideoPlayerListener {
        override fun onError(error: Exception) {
            logger.warn { "onError: $error" }
            danmakuPlayer?.pause()

            // 播放地址是带时效签名的，跳转时重新发起的分段请求经常会被 CDN 拒掉，
            // 先自动换一份新地址接着放，实在救不回来才把错误抛给界面
            if (playRetryCount < MAX_PLAY_RETRY) {
                retryPlaybackAfterError(error)
                return
            }

            _uiState.update {
                it.copy(
                    playerState = PlayerState.Error(
                        error.message ?: "Unknown error"
                    ),
                    isBuffering = false
                )
            }
        }

        override fun onReady() {
            logger.info { "onReady" }
            // 能 ready 说明已经缓冲上了，之前的错误也算恢复了
            _uiState.update { it.copy(playerState = PlayerState.Ready, isBuffering = false) }

            updatePlaySpeed(forceUpdate = true)
            startSeekerUpdater()
        }

        override fun onPlay() {
            logger.info { "onPlay" }
            danmakuPlayer?.start()
            _uiState.update { it.copy(playerState = PlayerState.Playing, isBuffering = false) }
            schedulePlayRetryReset()

            val resumePosition = pendingResumePosition
            pendingResumePosition = 0
            when {
                // 出错重载后跳回中断的位置，优先于历史进度
                resumePosition > 0 -> {
                    _uiState.update { it.copy(lastPlayed = 0) }
                    seekToTime(resumePosition)
                }

                _uiState.value.lastPlayed > 0 -> {
                    seekToLastPlayed()
                    _uiState.update { it.copy(lastPlayed = 0) }
                }
            }
        }

        override fun onPause() {
            logger.info { "onPause" }
            danmakuPlayer?.pause()
            cancelPlayRetryReset()
            _uiState.update { it.copyKeepingError(PlayerState.Paused) }
        }

        override fun onSeekProcessed(positionMs: Long) {
            // 画面按关键帧对齐后可能和请求的时间差好几秒，用真实落点把弹幕重新对一次
            _seekerState.update { it.copy(currentTime = positionMs) }
            danmakuPlayer?.seekTo(positionMs)
            // akdanmaku 跳转后会自己开始播，视频还没在放就先按住
            if (videoPlayer?.isPlaying != true) danmakuPlayer?.pause()
        }

        override fun onBuffering() {
            logger.info { "onBuffering" }
            danmakuPlayer?.pause()
            cancelPlayRetryReset()
            _uiState.update { it.copy(isBuffering = true) }
        }

        override fun onIdle() {
            logger.info { "onIdle" }
            danmakuPlayer?.pause()
            cancelPlayRetryReset()
            // 正在自动重试就继续转圈，别闪一下
            if (playRetryJob?.isActive == true) return
            // 出错或 stop 之后会停在这个状态，缓冲标记必须清掉，
            // 否则界面会一直转圈（这也是「一直缓冲中」的直接原因）
            _uiState.update { it.copy(isBuffering = false) }
        }

        override fun onEnd() {
            logger.info { "onEnd" }
            danmakuPlayer?.pause()
            stopSeekerUpdater()

            _uiState.update { it.copy(playerState = PlayerState.Ended, isBuffering = false) }
            viewModelScope.launch {
                _uiEffect.emit(PlayerUiEffect.PlayEnded)
            }
        }

        override fun onSeekBack(seekBackIncrementMs: Long) {
        }

        override fun onSeekForward(seekForwardIncrementMs: Long) {
        }
    }

    /**
     * 播放出错后自动换一份播放地址重试
     *
     * B 站的播放地址带时效签名，长时间播放或反复跳转后，分段请求可能被 CDN 拒绝。
     * 重新走一遍取地址流程通常就能恢复，并跳回中断的位置继续放。
     */
    private fun retryPlaybackAfterError(error: Exception) {
        val position = videoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        playRetryCount++
        // 重载之后播放器位置会回到 0，这时候再失败一次别把上回记下的断点冲掉，
        // 否则连着失败几次就变成从头开始放了
        if (position > 0) pendingResumePosition = position
        playRetryResetJob?.cancel()

        logger.warn {
            "Playback error, retry $playRetryCount/$MAX_PLAY_RETRY " +
                    "from ${position.formatHourMinSec()}: ${error.message}"
        }

        // 重试期间界面继续显示缓冲，不要闪一下错误再闪回来
        _uiState.update { it.copy(isBuffering = true) }

        playRetryJob?.cancel()
        playRetryJob = viewModelScope.launch {
            delay(RETRY_DELAY_MS * playRetryCount)
            loadVideoWithResources()
        }
    }

    /**
     * 连续放够一段时间才把重试次数清零
     *
     * 直接在 onPlay 里清零的话，「起播就失败」会变成无限重试，永远走不到报错。
     * 计时必须是「连续播放」，所以一旦转圈或者停下来就要 [cancelPlayRetryReset]，
     * 不然「放一下 → 长时间缓冲」这种循环会在缓冲期间把次数清掉，一样走不到报错。
     */
    private fun schedulePlayRetryReset() {
        if (playRetryCount == 0) return
        playRetryResetJob?.cancel()
        playRetryResetJob = viewModelScope.launch {
            delay(RETRY_RESET_DELAY_MS)
            playRetryCount = 0
        }
    }

    /** 播放中断，重新计「连续播放」的时间 */
    private fun cancelPlayRetryReset() {
        playRetryResetJob?.cancel()
        playRetryResetJob = null
    }

    private fun cancelPlayRetry() {
        playRetryJob?.cancel()
        playRetryJob = null
        playRetryResetJob?.cancel()
        playRetryResetJob = null
        playRetryCount = 0
        pendingResumePosition = 0
    }

    fun init(
        aid: Long,
        cid: Long,
        epid: Int?,
        title: String,
        lastPlayed: Int,
        fromSeason: Boolean,
        subType: Int,
        seasonId: Int,
        proxyArea: ProxyArea = ProxyArea.MainLand,
        authorMid: Long = 0,
        authorName: String
    ) {
        _uiState.update {
            it.copy(
                aid = aid,
                cid = cid,
                epid = epid.takeIf { epid -> epid != 0 },
                seasonId = seasonId,
                title = title,
                lastPlayed = lastPlayed,
                fromSeason = fromSeason,
                subType = subType,
                proxyArea = proxyArea,
                authorMid = authorMid,
                authorName = authorName,
                mediaProfileState = MediaProfileState(
                    qualityId = Prefs.defaultQuality.code,
                    videoCodec = Prefs.defaultVideoCodec,
                    audio = Prefs.defaultAudio
                ),
                playSpeed = Prefs.defaultPlaySpeed.speed,
                danmakuState = DanmakuState(
                    scale = Prefs.defaultDanmakuScale,
                    opacity = Prefs.defaultDanmakuOpacity,
                    area = Prefs.defaultDanmakuArea,
                    speedFactor = Prefs.defaultDanmakuSpeedFactor,
                    maskEnabled = Prefs.defaultDanmakuMask,
                    enabledTypes = Prefs.defaultDanmakuTypes,
                ),
                subtitleState = SubtitleState(
                    fontSize = Prefs.defaultSubtitleFontSize,
                    opacity = Prefs.defaultSubtitleBackgroundOpacity,
                    bottomPadding = Prefs.defaultSubtitleBottomPadding
                )
            )
        }

        startClockUpdater()

        videoInfoRepository.videoList
            .onEach { newList ->
                // 过滤DetailViewModel销毁时repo重置
                if (newList.isEmpty()) return@onEach

                _uiState.update { currentState ->
                    currentState.copy(availableVideoList = newList)
                }
                logger.fInfo { "Sync video list from repo, size: ${newList.size}" }
            }
            .launchIn(viewModelScope)

        videoInfoRepository.videoDetailState
            .filter { it?.aid == _uiState.value.aid }
            .onEach { newDetail ->
                // 过滤DetailViewModel销毁时repo重置
                if (newDetail == null) return@onEach

                _uiState.update { currentState ->
                    if (newDetail.aid != currentState.aid) return@update currentState
                    currentState.copy(
                        relatedVideos = newDetail.relatedVideos,
                        publishDate = newDetail.publishDate,
                        viewCount = newDetail.stat.view,
                        // 当前稿件的详情是作者信息的来源，不能保留启动时的旧作者
                        authorName = newDetail.author.name,
                        authorMid = newDetail.author.mid
                    )
                }
                logger.fInfo { "Sync video detail from repo" }
            }
            .launchIn(viewModelScope)
    }

    fun initVideoPlayer(context: Context) {
        logger.info { "Init video player: ${Prefs.playerType.name}" }

        val options = VideoPlayerOptions(
            userAgent = when (Prefs.apiType) {
                ApiType.Web -> context.getString(R.string.video_player_user_agent_http)
                ApiType.App -> context.getString(R.string.video_player_user_agent_client)
            },
            referer = when (Prefs.apiType) {
                ApiType.Web -> context.getString(R.string.video_player_referer)
                ApiType.App -> null
            },
            enableFfmpegAudioRenderer = Prefs.enableFfmpegAudioRenderer,
            enableSoftwareVideoDecoder = Prefs.enableSoftwareVideoDecoder
        )

        val newVideoPlayer = when (Prefs.playerType) {
            PlayerType.Media3 -> ExoPlayerFactory().create(context.applicationContext, options)
        }

        newVideoPlayer.setPlayerEventListener(videoPlayerListener)
        newVideoPlayer.collectDebugInfo = _uiState.value.showPlayerInfo
        videoPlayer = newVideoPlayer
        lastDebugInfo = ""
        startSeekerUpdater()
    }

    fun detachPlayer() {
        syncProgress(scope = detachedWorkScope, isDetaching = true)

        cancelPlayRetry()
        onlineCountJob?.cancel()
        loadDetailJob?.cancel()
        videoPlayer?.release()
        videoPlayer = null
    }

    fun initDanmakuPlayer() {
        danmakuPlayer = DanmakuPlayer(SimpleRenderer())
        initDanmakuConfig()
    }

    fun releaseDanmakuPlayer() {
        danmakuPlayer?.release()
        danmakuPlayer = null
    }

    fun loadSubtitle(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (id == -1L) {
                _uiState.update {
                    it.copy(
                        subtitleId = -1,
                        subtitleData = emptyList()
                    )
                }
                return@launch
            }
            var subtitleName = ""
            runCatching {
                val subtitle =
                    _uiState.value.subtitleList.find { it.id == id } ?: return@runCatching
                subtitleName = subtitle.langDoc
                logger.info { "Subtitle url: ${subtitle.url}" }
                val client = HttpClient(OkHttp)
                val responseText = client.get(subtitle.url).bodyAsText()
                val subtitleData = SubtitleParser.fromBccString(responseText)
                _uiState.update {
                    it.copy(
                        subtitleId = id,
                        subtitleData = subtitleData
                    )
                }
            }.onFailure {
                logger.fInfo { "Load subtitle failed: ${it.stackTraceToString()}" }
            }.onSuccess {
                logger.fInfo { "Load subtitle $subtitleName success" }
            }
        }
    }

    fun updatePlaySpeed(
        speed: Float? = null,
        forceUpdate: Boolean = false
    ) {
        val currentSpeed = _uiState.value.playSpeed
        val targetSpeed = speed ?: currentSpeed

        if (!forceUpdate && currentSpeed == targetSpeed) return

        _uiState.update { it.copy(playSpeed = targetSpeed) }
        videoPlayer?.speed = targetSpeed
        danmakuPlayer?.updatePlaySpeed(targetSpeed)
    }

    fun updateVideoAspectRatio(aspectRatio: VideoAspectRatio) {
        _uiState.update {
            it.copy(aspectRatio = aspectRatio)
        }
    }

    fun updateMediaProfile(action: MediaProfileSettingAction) {
        val old = _uiState.value.mediaProfileState
        val new = when (action) {
            is MediaProfileSettingAction.SetQuality -> old.copy(qualityId = action.value)
            is MediaProfileSettingAction.SetVideoCodec -> old.copy(videoCodec = action.value)
            is MediaProfileSettingAction.SetAudio -> old.copy(audio = action.value)
        }

        if (old == new) return

        _uiState.update { it.copy(mediaProfileState = new) }

        videoPlayer?.let { player ->
            player.pause()
            val currentPosition = player.currentPosition

            // 解析新配置下的 URL
            val mediaUrls = resolveMediaUrls(new.qualityId, new.videoCodec, new.audio)

            if (mediaUrls != null) {
                // 执行播放逻辑
                player.playUrl(
                    videoUrl = mediaUrls.videoUrl,
                    audioUrl = mediaUrls.audioUrl,
                    videoBackupUrls = mediaUrls.videoBackupUrls,
                    audioBackupUrls = mediaUrls.audioBackupUrls,
                    videoBitrate = mediaUrls.videoBitrate,
                    audioBitrate = mediaUrls.audioBitrate
                )
                player.prepare()
                if (currentPosition > 0) {
                    player.seekTo(currentPosition)
                }
                player.start()
            }
        }
    }

    fun updateDanmakuState(action: DanmakuSettingAction) {
        val old = _uiState.value.danmakuState
        val new = when (action) {
            is DanmakuSettingAction.SetScale -> old.copy(scale = action.value)
            is DanmakuSettingAction.SetOpacity -> old.copy(opacity = action.value)
            is DanmakuSettingAction.SetArea -> old.copy(area = action.value)
            is DanmakuSettingAction.SetSpeedFactor -> old.copy(speedFactor = action.value)
            is DanmakuSettingAction.SetMaskEnabled -> old.copy(maskEnabled = action.enabled)
            is DanmakuSettingAction.SetEnabledTypes -> old.copy(enabledTypes = action.types)
        }

        if (old == new) return

        // 首先更新UI
        _uiState.update { it.copy(danmakuState = new) }

        // ===== 副作用处理 =====
        if (new.enabledTypes != old.enabledTypes) {
            updateDanmakuConfigTypeFilter(new.enabledTypes)
            Prefs.defaultDanmakuTypes = new.enabledTypes
        }
        if (new.scale != old.scale) {
            updateDanmakuScale(new.scale)
            Prefs.defaultDanmakuScale = new.scale
        }
        if (new.speedFactor != old.speedFactor) {
            updateDanmakuSpeedFactor(new.speedFactor)
            Prefs.defaultDanmakuSpeedFactor = new.speedFactor
        }
        if (new.area != old.area) {
            updateDanmakuArea(new.area)
            Prefs.defaultDanmakuArea = new.area
        }
        if (new.opacity != old.opacity) {
            Prefs.defaultDanmakuOpacity = new.opacity
        }
        if (new.maskEnabled != old.maskEnabled) {
            Prefs.defaultDanmakuMask = new.maskEnabled
            // 之前没拉到蒙版数据的话（比如加载时网络抖了一下），
            // 这里再试一次，不然开关打开也是没反应
            if (new.maskEnabled && _uiState.value.danmakuMask == null) {
                viewModelScope.launch(Dispatchers.IO) { updateDanmakuMask() }
            }
        }
    }

    fun updateSubtitleState(action: SubtitleSettingAction) {
        val old = _uiState.value.subtitleState
        val new = when (action) {
            is SubtitleSettingAction.SetFontSize -> old.copy(fontSize = action.value)
            is SubtitleSettingAction.SetOpacity -> old.copy(opacity = action.value)
            is SubtitleSettingAction.SetBottomPadding -> old.copy(bottomPadding = action.value)
        }

        if (old == new) return

        _uiState.update { it.copy(subtitleState = new) }

        // ===== 持久化副作用 =====
        if (new.fontSize != old.fontSize) {
            Prefs.defaultSubtitleFontSize = new.fontSize
        }

        if (new.opacity != old.opacity) {
            Prefs.defaultSubtitleBackgroundOpacity = new.opacity
        }

        if (new.bottomPadding != old.bottomPadding) {
            Prefs.defaultSubtitleBottomPadding = new.bottomPadding
        }
    }

    /**
     * 触发播放结束后的检查逻辑
     */
    fun checkAndPlayNext() {
        when (Prefs.actionAfterPlay) {
            ActionAfterPlayItems.Pause -> return
            ActionAfterPlayItems.Exit -> {
                viewModelScope.launch {
                    _uiEffect.emit(PlayerUiEffect.FinishActivity)
                }

                return
            }

            ActionAfterPlayItems.PlayRelated -> {
                val firstRelatedVideo = _uiState.value.relatedVideos.firstOrNull()
                firstRelatedVideo?.cid?.let {
                    val nextVideo = VideoListItem(
                        aid = firstRelatedVideo.avid,
                        cid = firstRelatedVideo.cid,
                        title = firstRelatedVideo.title
                    )
                    playNewVideo(newVideo = nextVideo)

                    // 因为番剧无相关视频，需要继续播放，所以在这里return
                    return
                }
            }

            ActionAfterPlayItems.PlayNext -> {
                /* 继续执行 */
            }
        }

        val nextTarget = findNextPlayTarget()

        // 3. 根据查找结果执行操作
        if (nextTarget != null) {
            startNextEpisodeCountdown(nextTarget)
        } else {
            // 没有下一集了，发送事件关闭页面
            viewModelScope.launch {
                _uiEffect.emit(PlayerUiEffect.FinishActivity)
            }
        }
    }

    fun playNextNow() {
        playNextCountdownJob?.cancel()
        _uiState.update { it.copy(showSkipToNextEp = false) }

        findNextPlayTarget()?.let { playNextTarget(it) }
    }

    fun playPreviousNow() {
        playNextCountdownJob?.cancel()
        _uiState.update { it.copy(showSkipToNextEp = false) }

        findPreviousPlayTarget()?.let { playNextTarget(it) }
    }

    fun toggleSubtitle() {
        val state = _uiState.value
        if (state.subtitleId != -1L) {
            loadSubtitle(-1L)
            return
        }

        val firstSubtitleId = state.subtitleList
            .firstOrNull { it.id != -1L }
            ?.id
            ?: return
        loadSubtitle(firstSubtitleId)
    }

    fun cancelPlayNext() {
        playNextCountdownJob?.cancel()
        _uiState.update { it.copy(showSkipToNextEp = false) }
    }

    fun backToStart() {
        backToStartCountdownJob?.cancel()
        _uiState.update { it.copy(showBackToStart = false) }

        videoPlayer?.seekTo(0)
        danmakuPlayer?.seekTo(0)
        // akdanmaku 会在跳转后立即播放，如果需要缓冲则会导致弹幕不同步
        danmakuPlayer?.pause()
    }

    /**
     * 周期性刷新「当前在看人数」
     *
     * 只在切分P/切视频时重启。接口很轻，30 秒一次足够让控制条弹出来时数字是新的。
     */
    private fun startOnlineCountUpdater() {
        onlineCountJob?.cancel()
        onlineCountJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val state = _uiState.value
                if (state.aid != 0L && state.cid != 0L) {
                    val count = videoPlayRepository.getOnlineCount(state.aid, state.cid)
                    // 请求期间可能已经切走了，别把上一个视频的数字写进来
                    if (_uiState.value.cid == state.cid) {
                        _uiState.update { it.copy(onlineCount = count) }
                    }
                }
                delay(30_000)
            }
        }
    }

    /**
     * 开始周期性更新播放进度
     */
    fun startSeekerUpdater() {
        // 防止重复启动
        if (seekerUpdateJob?.isActive == true) return

        seekerUpdateJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                updateSeekerState()
                delay(100)
            }
        }
    }

    fun seekToTime(time: Long) {
        videoPlayer?.seekTo(time)
        _seekerState.update { it.copy(currentTime = time) }
        danmakuPlayer?.seekTo(time)
        // akdanmaku 会在跳转后立即播放，如果需要缓冲则会导致弹幕不同步
        danmakuPlayer?.pause()
    }

    fun playNewVideo(newVideo: VideoListItem) {
        videoPlayer?.pause()

        val state = _uiState.value

        val shouldUpdateVideoDetail = state.aid != newVideo.aid
        val shouldUpdateVideoList = !state.availableVideoList.any { it.aid == newVideo.aid }


        // 新视频不在当前视频列表时更新列表
        if (shouldUpdateVideoList) {
            videoInfoRepository.updateVideoList(listOf(newVideo))
        }

        // 更新播放历史并上传
        syncProgress(viewModelScope)

        // 换视频了，上一个视频的重试预算和待恢复位置都作废
        cancelPlayRetry()

        // 重置弹幕
        releaseDanmakuPlayer()
        initDanmakuPlayer()

        // 更新UiState
        _uiState.update {
            it.copy(
                aid = newVideo.aid,
                cid = newVideo.cid,
                epid = newVideo.epid?.takeIf { it > 0 },
                fromSeason = newVideo.epid?.let { it > 0 } == true,
                lastPlayed = 0,
                seasonId = newVideo.seasonId ?: 0,
                title = newVideo.title,
                isBuffering = true,
                // 上一个视频的错误状态是粘的（copyKeepingError），不清掉的话会一直盖在新视频的加载界面上
                playerState = PlayerState.Ready,
                onlineCount = null,
                // 换的是别的稿件才清空详情，同一稿件换分P时这些信息不变
                publishDate = if (shouldUpdateVideoDetail) null else it.publishDate,
                viewCount = if (shouldUpdateVideoDetail) -1 else it.viewCount,
                // 作者信息同理，留着上一个稿件的会让信息栏和「up主页」按钮指向错的 UP
                authorName = if (shouldUpdateVideoDetail) "" else it.authorName,
                authorMid = if (shouldUpdateVideoDetail) 0 else it.authorMid,
                videoShot = null,
                danmakuMask = null,
                subtitleList = emptyList(),
                subtitleData = emptyList(),
                relatedVideos = emptyList(),
            )
        }

        // 先发布新稿件身份，再加载详情，避免快速响应被旧 aid 过滤掉。
        loadDetailJob?.cancel()
        loadDetailJob = viewModelScope.launch {
            try {
                videoInfoRepository.loadVideoDetail(newVideo.aid, Prefs.apiType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load current video detail" }
            }
        }

        // 加载新播放url
        loadVideoWithResources()
    }

    fun trySendHeartbeat() {
        syncProgress(scope = viewModelScope, updateLocal = false)
    }

    fun loadVideoWithResources() {
        val state = _uiState.value
        val avid = state.aid
        val cid = state.cid
        val epid = state.epid

        loadVideoJob?.cancel()
        loadVideoJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                resolveUrlsAndPlay(avid, cid, epid)

                launch {
                    updateSubtitle()
                    val lastPlayEnabledSubtitle = _uiState.value.subtitleId != -1L
                    if (lastPlayEnabledSubtitle) {
                        logger.info { "Subtitle is enabled, auto-enabling first subtitle..." }
                        enableFirstSubtitle()
                    }
                }
                launch { loadDanmaku(cid) }
                withContext(Dispatchers.Main) { startOnlineCountUpdater() }
                launch { updateDanmakuMask() }
                launch { updateVideoShot() }
                launch { updateVideoPages() }
            } catch (e: CancellationException) {
                throw e // 让结构化并发正常取消，不作为播放错误处理
            } catch (e: Exception) {
                logger.error(e) { "Loading video data error: $e" }

                _uiState.update {
                    it.copy(playerState = PlayerState.Error(e.message ?: "未知错误"))
                }
            }
        }
    }

    private suspend fun resolveUrlsAndPlay(avid: Long, cid: Long, epid: Int? = 0) {
        try {
            val mediaUrls = fetchMediaUrls(avid, cid, epid ?: 0)

            withContext(Dispatchers.Main) {
                executePlayback(mediaUrls)
                logger.info { "Video source loaded successfully" }
            }
        } catch (e: CancellationException) {
            throw e // 重新抛出，让结构化并发正常传播取消信号
        } catch (e: Exception) {
            logger.error(e) { "Failed to load media: ${e.message}" }
            // 保留原始异常作为 cause，上层 catch 可通过 e.cause 获取根因
            throw IllegalStateException("${e.message}", e)
        }

    }

    private suspend fun fetchMediaUrls(avid: Long, cid: Long, epid: Int): MediaUrls {
        val config = loadPlaybackConfig(
            avid, cid, epid,
            Prefs.apiType,
            _uiState.value.proxyArea
        )

        return resolveMediaUrls(
            config.qn,
            config.codec,
            config.audio
        ) ?: throw IllegalStateException("视频源解析失败")
    }

    private suspend fun loadPlaybackConfig(
        avid: Long,
        cid: Long,
        epid: Int = 0,
        preferApi: ApiType = Prefs.apiType,
        proxyArea: ProxyArea = ProxyArea.MainLand
    ): PlaybackConfig {
        logger.fInfo { "Load play url: [av=$avid, cid=$cid, preferApi=$preferApi, proxyArea=$proxyArea]" }

        return runCatching {
            // 1. 获取播放数据
            val playData = fetchPlayData(avid, cid, epid, preferApi, proxyArea)
            this@VideoPlayerV3ViewModel.playData = playData

            logger.fInfo { "Load play data response success. Play data: $playData" }

            // 2. 解析并去重可用的清晰度 (使用 associate 替代 forEach + mutableMap)
            val resolutionMap = playData.dashVideos.associate { video ->
                video.quality to Resolution.fromCode(video.quality)
                    .getShortDisplayName(BVApp.context)
            }
            logger.fInfo { "Video available resolution: $resolutionMap" }

            // 3. 解析并去重可用的音质 (使用 buildList 和 distinct 替代 forEach + mutableList)
            val availableAudioList = buildList {
                addAll(playData.dashAudios.map { Audio.fromCode(it.codecId) })
                playData.dolby?.let { add(Audio.fromCode(it.codecId)) }
                playData.flac?.let { add(Audio.fromCode(it.codecId)) }
            }.distinct()

            logger.fInfo { "Video available audio: $availableAudioList" }

            // 4. 计算目标清晰度、音质和编码 (已抽取业务逻辑)
            val targetQualityId =
                calculateTargetQuality(resolutionMap.keys, Prefs.defaultQuality.code)
            val targetAudio = calculateTargetAudio(availableAudioList, Prefs.defaultAudio)
            val targetCodec = getTargetVideoCodec()

            // 5. 统一批量更新 UI State (避免多次触发重组)
            _uiState.update {
                it.copy(
                    availableQuality = resolutionMap,
                    availableAudio = availableAudioList,
                    mediaProfileState = it.mediaProfileState.copy(
                        qualityId = targetQualityId,
                        audio = targetAudio
                    )
                )
            }

            // 6. 付费视频预览状态提示
            if (playData.needPay) {
                startShowPreviewTipCountdown()
            }

            PlaybackConfig(
                qn = targetQualityId,
                codec = targetCodec,
                audio = targetAudio
            )
        }.onFailure { throwable ->
            logger.fException(throwable) { "Load video failed" }
        }.onSuccess {
            logger.fInfo { "Load play url success" }
        }.getOrThrow()
    }

    private suspend fun fetchPlayData(
        avid: Long, cid: Long, epid: Int, preferApi: ApiType, proxyArea: ProxyArea
    ): PlayData {
        return if (_uiState.value.fromSeason) {
            videoPlayRepository.getPgcPlayData(
                aid = avid,
                cid = cid,
                epid = epid,
                preferCodec = Prefs.defaultVideoCodec.toBiliApiCodeType(),
                preferApiType = preferApi,
                enableProxy = Prefs.enableProxy,
                proxyArea = proxyArea.toQueryParam()
            )
        } else {
            videoPlayRepository.getPlayData(
                aid = avid,
                cid = cid,
                preferApiType = preferApi
            )
        }
    }

    private fun calculateTargetQuality(availableQualities: Set<Int>, defaultQualityCode: Int): Int {
        if (availableQualities.contains(defaultQualityCode)) return defaultQualityCode

        val sortedQualities = availableQualities.sorted()
        return sortedQualities.findLast { it <= defaultQualityCode }
            ?: sortedQualities.firstOrNull()
            ?: 0
    }

    private fun calculateTargetAudio(availableAudio: List<Audio>, defaultAudio: Audio): Audio {
        if (availableAudio.contains(defaultAudio)) return defaultAudio

        // Fallback 逻辑
        return when {
            defaultAudio == Audio.ADolbyAtoms && availableAudio.contains(Audio.AHiRes) -> Audio.AHiRes
            defaultAudio == Audio.AHiRes && availableAudio.contains(Audio.ADolbyAtoms) -> Audio.ADolbyAtoms
            availableAudio.contains(Audio.A192K) -> Audio.A192K
            availableAudio.contains(Audio.A132K) -> Audio.A132K
            availableAudio.contains(Audio.A64K) -> Audio.A64K
            else -> availableAudio.firstOrNull() ?: Audio.A132K
        }
    }

    private fun getTargetVideoCodec(): VideoCodec? {
        val state = _uiState.value
        val playData = playData ?: return null

        if (Prefs.apiType == ApiType.App && playData.codec.isEmpty()) {
            val videoItem = playData.dashVideos
                .find { it.quality == state.mediaProfileState.qualityId }
                ?: playData.dashVideos.firstOrNull()
                ?: return null

            val codec = VideoCodec.fromCodecId(videoItem.codecId)
            _uiState.update {
                it.copy(
                    availableVideoCodec = listOf(codec),
                    mediaProfileState = it.mediaProfileState.copy(
                        videoCodec = VideoCodec.fromCodecId(videoItem.codecId)
                    )
                )
            }
            return codec
        }

        val supportedCodec = playData.codec
        val codecList = supportedCodec[state.mediaProfileState.qualityId]
            ?.mapNotNull { VideoCodec.fromCodecString(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val preferredCodec = if (codecList.contains(Prefs.defaultVideoCodec)) {
            Prefs.defaultVideoCodec
        } else {
            codecList.minByOrNull { it.ordinal } ?: return null
        }
        val targetVideoCodec = pickHardwareDecodableCodec(
            codecList = codecList,
            preferred = preferredCodec,
            qualityId = state.mediaProfileState.qualityId
        )

        _uiState.update {
            it.copy(
                availableVideoCodec = codecList,
                mediaProfileState = it.mediaProfileState.copy(videoCodec = targetVideoCodec)
            )
        }
        logger.fInfo { "Select codec: $targetVideoCodec" }
        return targetVideoCodec
    }

    /**
     * 挑一个这台设备真能硬解的编码。
     *
     * 4K 及以上如果用了没有硬件解码器的编码（最常见的是 AV1），就会退到软解，
     * 表现出来就是画面一顿一顿的、而且越到高码率段越明显。这种情况下宁可换成能硬解的编码，
     * 画质差别远小于卡顿带来的影响。用户自己在设置里强制软解时不插手。
     */
    private fun pickHardwareDecodableCodec(
        codecList: List<VideoCodec>,
        preferred: VideoCodec,
        qualityId: Int
    ): VideoCodec {
        if (Prefs.enableSoftwareVideoDecoder) return preferred
        val videoItems = playData?.dashVideos?.filter { it.quality == qualityId }.orEmpty()
        val width = videoItems.maxOfOrNull { it.width } ?: 0
        val height = videoItems.maxOfOrNull { it.height } ?: 0
        // B 站的 frameRate 是字符串，可能是 "25" 也可能是 "59.940060"
        val frameRate = videoItems.mapNotNull { it.frameRate.toDoubleOrNull() }.maxOrNull() ?: 0.0
        // 1080p 及以下基本都能扛住，没必要为了硬解去换编码
        if (width <= 0 || height < 1440) return preferred
        if (CodecUtil.hasHardwareDecoder(preferred.mimeType, width, height, frameRate)) return preferred

        val fallback = codecList.firstOrNull {
            it != preferred && CodecUtil.hasHardwareDecoder(it.mimeType, width, height, frameRate)
        }
        if (fallback == null) {
            logger.fWarn { "No hardware decoder for ${width}x$height@${frameRate}fps, keep codec $preferred" }
            return preferred
        }
        logger.fInfo { "No real-time hardware decoder for $preferred at ${width}x$height@${frameRate}fps, fallback to $fallback" }
        return fallback
    }

    private fun resolveMediaUrls(
        qn: Int? = null,
        codec: VideoCodec? = null,
        audio: Audio? = null
    ): MediaUrls? {
        val currentPlayData = playData ?: return null

        val state = _uiState.value

        val targetQn = qn ?: state.mediaProfileState.qualityId
        val targetCodec = codec ?: state.mediaProfileState.videoCodec
        val targetAudio = audio ?: state.mediaProfileState.audio

        logger.fInfo {
            "Video quality：${state.availableQuality[targetQn]}, video encoding：$targetCodec"
        }

        val foundVideoItem = currentPlayData.dashVideos.find {
            when (Prefs.apiType) {
                ApiType.Web -> it.quality == targetQn && it.codecs?.startsWith(targetCodec.prefix) == true
                ApiType.App -> {
                    if (currentPlayData.codec.isEmpty()) it.quality == targetQn
                    else it.quality == targetQn && it.codecs?.startsWith(targetCodec.prefix) == true
                }
            }
        }

        val actualVideoItem = foundVideoItem ?: currentPlayData.dashVideos.firstOrNull() ?: run {
            logger.fWarn { "No available video stream found" }
            return null
        }

        val videoUrls = mutableListOf<String>()
        videoUrls.add(actualVideoItem.baseUrl)
        videoUrls.addAll(actualVideoItem.backUrl)

        val audioItem = currentPlayData.dashAudios.find { it.codecId == targetAudio.code }
            ?: currentPlayData.dolby.takeIf { it?.codecId == targetAudio.code }
            ?: currentPlayData.flac.takeIf { it?.codecId == targetAudio.code }
            ?: currentPlayData.dashAudios.minByOrNull { it.codecId }

        val audioUrls = mutableListOf<String>()
        audioItem?.baseUrl?.let { audioUrls.add(it) }
        audioUrls.addAll(audioItem?.backUrl ?: emptyList())

        logger.fInfo { "all video hosts: ${videoUrls.map { with(URI(it)) { "$scheme://$authority" } }}" }
        logger.fInfo { "all audio hosts: ${audioUrls.map { with(URI(it)) { "$scheme://$authority" } }}" }

        //replace cdn
        val orderedVideoUrls: List<String>
        val orderedAudioUrls: List<String>
        if (Prefs.enableProxy && state.proxyArea != ProxyArea.MainLand) {
            // 走代理拿到的地址只有换域名后的这一个能用，没有备选
            orderedVideoUrls = listOf(actualVideoItem.baseUrl.replaceUrlDomainWithAliCdn())
            orderedAudioUrls = listOfNotNull(audioItem?.baseUrl?.replaceUrlDomainWithAliCdn())
        } else {
            // 如果未通过网络代理获得播放地址，才判断是否应该优先使用官方 cdn
            orderedVideoUrls = orderCdnUrls(videoUrls)
            orderedAudioUrls = orderCdnUrls(audioUrls)
        }

        val videoUrl = orderedVideoUrls.firstOrNull() ?: run {
            logger.fWarn { "No available video url found" }
            return null
        }
        val audioUrl = orderedAudioUrls.firstOrNull()

        logger.fInfo { "Audio encoding：${(Audio.fromCode(audioItem?.codecId ?: 0))}" }
        logger.info { "Video url: $videoUrl" }
        logger.info { "Audio url: $audioUrl" }

        _uiState.update {
            it.copy(
                videoHeight = actualVideoItem.height,
                videoWidth = actualVideoItem.width
            )
        }

        return MediaUrls(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            videoBackupUrls = orderedVideoUrls.drop(1),
            audioBackupUrls = orderedAudioUrls.drop(1),
            videoBitrate = actualVideoItem.bandwidth,
            audioBitrate = audioItem?.bandwidth ?: 0
        )
    }

    private fun executePlayback(mediaUrls: MediaUrls) {
        val player = videoPlayer ?: run {
            logger.error { "VideoPlayer is not initialized!" }
            return
        }

        logger.info { "Execute playback -> Video: ${mediaUrls.videoUrl}, Audio: ${mediaUrls.audioUrl}" }
        player.playUrl(
            videoUrl = mediaUrls.videoUrl,
            audioUrl = mediaUrls.audioUrl,
            videoBackupUrls = mediaUrls.videoBackupUrls,
            audioBackupUrls = mediaUrls.audioBackupUrls,
            videoBitrate = mediaUrls.videoBitrate,
            audioBitrate = mediaUrls.audioBitrate
        )
        player.prepare()
        player.start()
    }

    // 加载合集内的分P
    private suspend fun updateVideoPages() {
        videoInfoRepository.updateUgcPages(Prefs.apiType)
    }

    private suspend fun loadDanmaku(cid: Long) {
        runCatching {
            val danmakuXmlData = BiliHttpApi.getDanmakuXml(cid = cid, sessData = Prefs.sessData)

            danmakuXmlData.data.map {
                DanmakuItemData(
                    danmakuId = it.dmid,
                    position = (it.time * 1000).toLong(),
                    content = it.text,
                    mode = when (it.type) {
                        4 -> DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM
                        5 -> DanmakuItemData.DANMAKU_MODE_CENTER_TOP
                        else -> DanmakuItemData.DANMAKU_MODE_ROLLING
                    },
                    textSize = it.size,
                    textColor = Color(it.color).toArgb()
                )
            }
        }.onSuccess { list ->
            danmakuPlayer?.updateData(list)
            logger.fInfo { "Load danmaku success, size: ${list.size}" }
        }.onFailure { error ->
            logger.fWarn { "Load danmaku failed: ${error.stackTraceToString()}" }
        }
    }

    private suspend fun updateSubtitle() {
        val state = _uiState.value

        runCatching {
            val subtitleList = videoPlayRepository.getSubtitle(
                aid = state.aid,
                cid = state.cid,
                preferApiType = Prefs.apiType
            )
            _uiState.update { currentState ->
                currentState.copy(
                    subtitleList = subtitleList
                )
            }
            logger.fInfo { "Update subtitle size: ${subtitleList.size}" }
        }.onFailure {
            logger.fWarn { "Update subtitle failed: ${it.stackTraceToString()}" }
        }
    }

    private fun enableFirstSubtitle() {
        runCatching {
            logger.info { "Load first subtitle" }
            logger.info { "availableSubtitle: ${_uiState.value.subtitleList.toList()}" }
            loadSubtitle(
                _uiState.value.subtitleList
                    .firstOrNull { it.id != -1L }?.id
                    ?: throw IllegalStateException("No available subtitle")
            )
        }.onFailure {
            logger.error { "Load first subtitle failed: ${it.stackTraceToString()}" }
        }
    }

    private fun syncProgress(
        scope: CoroutineScope,
        updateLocal: Boolean = true,
        isDetaching: Boolean = false
    ) {
        val player = videoPlayer ?: return
        val state = _uiState.value

        val currentTime = (player.currentPosition.coerceAtLeast(0) / 1000).toInt()
        val totalTime = (player.duration.coerceAtLeast(0) / 1000).toInt()
        // 播放器没准备好时 duration 是 TIME_UNSET，coerce 完是 0，
        // 这时候不能判成「已看完」，否则会把 -1 当进度写进历史，下次进来进度就没了
        val reportTime = if (totalTime > 0 && currentTime >= totalTime) -1 else currentTime

        if (updateLocal) {
            videoInfoRepository.updateHistory(
                progress = reportTime,
                lastPlayedCid = state.cid
            )
        }

        if (!Prefs.incognitoMode) {
            heartbeatJob?.cancel()
            heartbeatJob = scope.launch(Dispatchers.IO) {
                try {
                    if (isDetaching) {
                        withTimeout(3000L) { uploadHistory(state, reportTime) }
                    } else {
                        uploadHistory(state, reportTime)
                    }
                } catch (e: Exception) {
                    logger.warn { "Failed to upload history: $e" }
                }
            }
        }
    }

    private suspend fun uploadHistory(uiState: PlayerUiState, time: Int) {
        try {
            with(uiState) {
                val currentApiType = Prefs.apiType

                if (!fromSeason) {
                    logger.info { "Send heartbeat: [avid=$aid, cid=$cid, time=$time]" }
                    videoPlayRepository.sendHeartbeat(
                        aid = aid,
                        cid = cid,
                        time = time,
                        preferApiType = currentApiType
                    )
                } else {
                    logger.info { "Send heartbeat: [avid=$aid, cid=$cid, epid=$epid, sid=$seasonId, time=$time]" }
                    videoPlayRepository.sendHeartbeat(
                        aid = aid,
                        cid = cid,
                        time = time,
                        type = HeartbeatVideoType.Season,
                        subType = subType,
                        epid = epid,
                        seasonId = seasonId,
                        preferApiType = currentApiType
                    )
                }
            }
            logger.info { "Send heartbeat success" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "Send heartbeat failed: ${e.stackTraceToString()}" }
        }
    }

    private suspend fun updateDanmakuMask() {
        val state = _uiState.value

        runCatching {
            val mask = videoPlayRepository.getDanmakuMask(
                aid = state.aid,
                cid = state.cid,
                preferApiType = Prefs.apiType
            )

            _uiState.update { it.copy(danmakuMask = mask) }

            logger.fInfo { "Load danmaku mask segments: ${mask?.segmentCount ?: 0}" }
        }.onFailure {
            logger.fWarn { "Load danmaku mask failed: ${it.stackTraceToString()}" }
        }
    }

    private suspend fun updateVideoShot() {
        val state = _uiState.value
        runCatching {
            val videoShot = videoPlayRepository.getVideoShot(
                aid = state.aid,
                cid = state.cid,
                preferApiType = Prefs.apiType
            )
            _uiState.update { it.copy(videoShot = videoShot) }
            logger.fInfo { "Load video shot success" }
        }.onFailure { err ->
            logger.fWarn { "Load video shot failed: ${err.stackTraceToString()}" }
        }
    }

    private fun initDanmakuConfig() {
        val danmakuTypes = Prefs.defaultDanmakuTypes
        val area = Prefs.defaultDanmakuArea
        val scale = Prefs.defaultDanmakuScale
        val factor = Prefs.defaultDanmakuSpeedFactor

        danmakuTypeFilter.clear()
        if (!danmakuTypes.contains(DanmakuType.All)) {
            val types = DanmakuType.entries.toMutableList()
            types.remove(DanmakuType.All)
            types.removeAll(danmakuTypes)
            val filterTypes = types.mapNotNull {
                when (it) {
                    DanmakuType.Rolling -> DanmakuItemData.DANMAKU_MODE_ROLLING
                    DanmakuType.Top -> DanmakuItemData.DANMAKU_MODE_CENTER_TOP
                    DanmakuType.Bottom -> DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM
                    else -> null
                }
            }
            filterTypes.forEach { danmakuTypeFilter.addFilterItem(it) }
        }
        danmakuConfig = danmakuConfig.copy(
            density = 120,
            textSizeScale = scale,
            screenPart = area,
            dataFilter = listOf(danmakuTypeFilter),
            rollingSpeedFactor = factor
        )
        danmakuConfig.updateFilter()
        logger.info { "Init danmaku config: $danmakuConfig" }
        danmakuPlayer?.updateConfig(danmakuConfig)
    }

    private fun updateDanmakuConfigTypeFilter(enabledDanmakuTypes: List<DanmakuType>) {
        danmakuTypeFilter.clear()

        if (!enabledDanmakuTypes.contains(DanmakuType.All)) {
            val types = DanmakuType.entries.toMutableList()
            types.remove(DanmakuType.All)
            types.removeAll(enabledDanmakuTypes)
            val filterTypes = types.mapNotNull {
                when (it) {
                    DanmakuType.Rolling -> DanmakuItemData.DANMAKU_MODE_ROLLING
                    DanmakuType.Top -> DanmakuItemData.DANMAKU_MODE_CENTER_TOP
                    DanmakuType.Bottom -> DanmakuItemData.DANMAKU_MODE_CENTER_BOTTOM
                    else -> null
                }
            }
            filterTypes.forEach { danmakuTypeFilter.addFilterItem(it) }
        }
        logger.info { "Update danmaku type filters: ${danmakuTypeFilter.filterSet}" }
        danmakuConfig.updateFilter()
        danmakuPlayer?.updateConfig(danmakuConfig)
    }

    private fun updateDanmakuArea(area: Float) {
        logger.info { "Update danmaku area: $area" }

        danmakuConfig = danmakuConfig.copy(
            screenPart = area
        )
        danmakuPlayer?.updateConfig(danmakuConfig)

        // 更新弹幕库之后updateConfig会导致滚动速度被重置，所以这里需要重新设置
        danmakuPlayer?.setDanmakuRollingSpeed(_uiState.value.danmakuState.speedFactor)
    }

    private fun updateDanmakuScale(scale: Float) {
        logger.info { "Update danmaku config: $danmakuConfig" }

        danmakuConfig = danmakuConfig.copy(
            textSizeScale = scale,
        )
        danmakuPlayer?.updateConfig(danmakuConfig)

        // 更新弹幕库之后updateConfig会导致滚动速度被重置，所以这里需要重新设置
        danmakuPlayer?.setDanmakuRollingSpeed(_uiState.value.danmakuState.speedFactor)
    }

    private fun updateDanmakuSpeedFactor(factor: Float) {
        logger.info { "Update danmaku rolling speed factor: $factor" }
        _uiState.update { it.copy(danmakuState = it.danmakuState.copy(speedFactor = factor)) }

        danmakuPlayer?.setDanmakuRollingSpeed(factor)
    }

    private fun findNextPlayTarget(): NextPlayTarget? {
        val currentState = _uiState.value
        val videoList = currentState.availableVideoList
        val currentCid = currentState.cid

        val videoListIndex = videoList.indexOfFirst { it.aid == currentState.aid }
        if (videoListIndex == -1) return null

        val currentVideoItem = videoList.getOrNull(videoListIndex)
        if (currentVideoItem?.ugcPages?.isNotEmpty() == true) {
            val currentInnerIndex = currentVideoItem.ugcPages.indexOfFirst { it.cid == currentCid }
            if (currentInnerIndex != -1 && currentInnerIndex + 1 < currentVideoItem.ugcPages.size) {
                val nextPage = currentVideoItem.ugcPages[currentInnerIndex + 1]
                return NextPlayTarget.UgcPage(currentVideoItem, nextPage)
            }
        }

        if (videoListIndex + 1 < videoList.size) {
            return NextPlayTarget.VideoItem(videoList[videoListIndex + 1])
        }

        return null
    }

    private fun findPreviousPlayTarget(): NextPlayTarget? {
        val currentState = _uiState.value
        val videoList = currentState.availableVideoList
        val currentCid = currentState.cid

        val videoListIndex = videoList.indexOfFirst { it.aid == currentState.aid }
        if (videoListIndex == -1) return null

        val currentVideoItem = videoList.getOrNull(videoListIndex)
        if (currentVideoItem?.ugcPages?.isNotEmpty() == true) {
            val currentInnerIndex = currentVideoItem.ugcPages.indexOfFirst { it.cid == currentCid }
            if (currentInnerIndex > 0) {
                val previousPage = currentVideoItem.ugcPages[currentInnerIndex - 1]
                return NextPlayTarget.UgcPage(currentVideoItem, previousPage)
            }
        }

        if (videoListIndex > 0) {
            val previousVideo = videoList[videoListIndex - 1]
            val previousLastPage = previousVideo.ugcPages?.lastOrNull()
            return if (previousLastPage != null) {
                NextPlayTarget.UgcPage(previousVideo, previousLastPage)
            } else {
                NextPlayTarget.VideoItem(previousVideo)
            }
        }

        return null
    }

    private fun startNextEpisodeCountdown(target: NextPlayTarget) {
        playNextCountdownJob?.cancel()

        playNextCountdownJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showSkipToNextEp = true,
                )
            }
            delay(5000)

            playNextTarget(target)
            _uiState.update { it.copy(showSkipToNextEp = false) }
        }
    }

    private fun startShowPreviewTipCountdown() {
        previewTipCountdownJob?.cancel()

        previewTipCountdownJob = viewModelScope.launch {
            _uiState.update {
                it.copy(showPreviewTip = true)
            }

            delay(5000)

            _uiState.update {
                it.copy(showPreviewTip = false)
            }
        }
    }

    private fun playNextTarget(target: NextPlayTarget) {
        when (target) {
            is NextPlayTarget.UgcPage -> {
                logger.info { "Play next UGC page: ${target.page.title}" }
                playNewVideo(
                    VideoListItem(
                        aid = target.parentVideo.aid,
                        cid = target.page.cid,
                        title = target.title
                    )
                )
            }

            is NextPlayTarget.VideoItem -> {
                logger.info { "Play next video item: ${target.video.title}" }
                playNewVideo(
                    VideoListItem(
                        aid = target.video.aid,
                        cid = target.video.cid,
                        title = target.title,
                        epid = target.video.epid,
                        seasonId = target.video.seasonId,
                    )
                )
            }
        }
    }

    private fun seekToLastPlayed() {
        val time = _uiState.value.lastPlayed.toLong()
        logger.fInfo { "Back to history: ${time.formatHourMinSec()}" }

        videoPlayer?.seekTo(time)
        danmakuPlayer?.seekTo(time)
        // akdanmaku 会在跳转后立即播放，如果需要缓冲则会导致弹幕不同步
        danmakuPlayer?.pause()

        _uiState.update { it.copy(showBackToStart = true) }

        backToStartCountdownJob?.cancel()
        backToStartCountdownJob = viewModelScope.launch {
            delay(5000)
            _uiState.update { it.copy(showBackToStart = false) }
        }
    }

    private fun stopSeekerUpdater() {
        seekerUpdateJob?.cancel()
        seekerUpdateJob = null
    }

    private fun updateSeekerState() {
        val player = videoPlayer ?: return

        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.coerceAtLeast(0L)
        _seekerState.update {
            it.copy(
                totalDuration = duration,
                currentTime = currentPos,
                bufferedPercentage = player.bufferedPercentage,
                debugInfo = currentDebugInfo(player)
            )
        }
    }

    private fun startClockUpdater() {
        clockUpdateJob?.cancel()
        clockUpdateJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                updateClock()
                delay(1000)
            }
        }
    }

    private fun updateClock() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        _uiState.update { it.copy(clock = Pair(hour, minute)) }
    }

    /**
     * 把一条流的所有地址按可靠程度排好序：官方 CDN 在前，PCDN / 回源 IP 之类的排在后面。
     *
     * 以前是从里面挑一个出来用，挑中的节点抽风就只能干等着缓冲；现在整条顺序都交给播放器，
     * 第一个连不上会自动往后换（见 FallbackUrlDataSource）。
     */
    private fun orderCdnUrls(urls: List<String>): List<String> {
        val distinctUrls = urls.distinct()
        if (!Prefs.preferOfficialCdn || distinctUrls.size <= 1) return distinctUrls
        val (official, others) = distinctUrls.partition { isOfficialCdnUrl(it) }
        logger.fInfo { "official cdn: ${official.size}, other cdn: ${others.size}" }
        return official + others
    }

    private fun isOfficialCdnUrl(url: String): Boolean {
        if (url.contains(".mcdn.bilivideo.")) return false
        if (url.contains(".szbdyd.com")) return false
        return !Regex("^(https?://)?(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d{1,5})?)(/[a-zA-Z0-9_./-]*)?(\\?.*)?$")
            .matches(url)
    }

    private fun String.replaceUrlDomainWithAliCdn(): String {
        val replaceDomainKeywords = listOf(
            "mirroraliov",
            "mirrorakam"
        )
        if (replaceDomainKeywords.none { this.contains(it) }) return this

        return Uri.parse(this)
            .buildUpon()
            .authority("upos-sz-mirrorali.bilivideo.com")
            .build()
            .toString()
    }

    private sealed interface NextPlayTarget {
        val title: String

        data class UgcPage(val parentVideo: VideoListItem, val page: VideoPage) : NextPlayTarget {
            override val title: String = page.title
        }

        data class VideoItem(val video: VideoListItem) : NextPlayTarget {
            override val title: String = video.title
        }
    }

    private data class PlaybackConfig(
        val qn: Int,           // 画质 ID
        val codec: VideoCodec?,     // 编码格式
        val audio: Audio      // 音频配置
    )

    private data class MediaUrls(
        val videoUrl: String,
        val audioUrl: String?,
        /** 同一条流的备用 CDN 地址，主地址连不上时播放器会自动换过去 */
        val videoBackupUrls: List<String> = emptyList(),
        val audioBackupUrls: List<String> = emptyList(),
        val videoBitrate: Int = 0,
        val audioBitrate: Int = 0
    )
}

sealed interface DanmakuSettingAction {
    data class SetScale(val value: Float) : DanmakuSettingAction
    data class SetOpacity(val value: Float) : DanmakuSettingAction
    data class SetArea(val value: Float) : DanmakuSettingAction
    data class SetSpeedFactor(val value: Float) : DanmakuSettingAction
    data class SetMaskEnabled(val enabled: Boolean) : DanmakuSettingAction
    data class SetEnabledTypes(val types: List<DanmakuType>) : DanmakuSettingAction
}

sealed interface SubtitleSettingAction {
    data class SetFontSize(val value: TextUnit) : SubtitleSettingAction
    data class SetOpacity(val value: Float) : SubtitleSettingAction
    data class SetBottomPadding(val value: Dp) : SubtitleSettingAction
}

sealed interface MediaProfileSettingAction {
    data class SetQuality(val value: Int) : MediaProfileSettingAction
    data class SetVideoCodec(val value: VideoCodec) : MediaProfileSettingAction
    data class SetAudio(val value: Audio) : MediaProfileSettingAction
}
