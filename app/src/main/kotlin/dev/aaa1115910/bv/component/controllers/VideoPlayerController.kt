package dev.aaa1115910.bv.component.controllers

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.video.Subtitle
import androidx.compose.ui.text.font.FontFamily
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.PlayerCustomShortcutAction
import dev.aaa1115910.bv.entity.PlayerCustomShortcutKeys
import dev.aaa1115910.bv.entity.PlayerCustomShortcutsStore
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.ui.state.PlayerState
import dev.aaa1115910.bv.ui.state.PlayerUiState
import dev.aaa1115910.bv.ui.state.SeekerState
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.player.DanmakuSettingAction
import dev.aaa1115910.bv.viewmodel.player.MediaProfileSettingAction
import dev.aaa1115910.bv.viewmodel.player.SubtitleSettingAction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// 放在文件级：写在 Composable 里每次重组都会新建一个，捕获它的回调也跟着全变，子组件就没法跳过重组
private val logger = KotlinLogging.logger {}

@Composable
fun VideoPlayerController(
    modifier: Modifier = Modifier,
    aid: Long,
    fromSeason: Boolean,
    proxyArea: ProxyArea,

    // play state
    isLooping: Boolean,
    isPlaying: Boolean,
    
    // UI related state
    videoShotCache: VideoShotImageCache,
    uiState: PlayerUiState,
    seekerState: State<SeekerState>,

    // player events
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onExit: () -> Unit,
    onGoTime: (time: Long) -> Unit,
    onBackToStart: () -> Unit,
    onCancelSkipToNextEp: () -> Unit,
    onPlayNewVideo: (VideoListItem) -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLoop: () -> Unit,
    onToggleSubtitle: () -> Unit,
    onTogglePersistentSeek: () -> Unit,
    onTogglePlayerInfo: () -> Unit,
    onGoToUpPage: () -> Unit,

    //menu events
    onMediaProfileSettingChange: (MediaProfileSettingAction) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    onPlaySpeedChange: (Float) -> Unit,
    onDanmakuSettingChange: (DanmakuSettingAction) -> Unit,
    onSubtitleChange: (Subtitle) -> Unit,
    onSubtitleSettingChange: (SubtitleSettingAction) -> Unit,
    onRelatedVideoClicked: (VideoCardData) -> Unit,

    content: @Composable () -> Unit
) {
    val currentUiState by rememberUpdatedState(uiState)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var engagementAid by remember { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(uiState.aid) { engagementAid = null }
    var showListController by remember { mutableStateOf(false) }
    var showMenuController by remember { mutableStateOf(false) }
    var showInfoSeekController by remember { mutableStateOf(false) }
    var showRelatedVideosController by remember { mutableStateOf(false) }
    val showClickableControllers by remember { derivedStateOf { showListController || showMenuController || showInfoSeekController || showRelatedVideosController } }
    val controlButtonTooltipState = remember { ControlButtonTooltipState() }

    var lastPressBack by remember { mutableLongStateOf(0L) }
    var goTime by remember { mutableLongStateOf(0L) }

    var isSeeking by remember { mutableStateOf(false) }
    var seekChangeCount by remember { mutableIntStateOf(0) }
    var lastSeekChangeTime by remember { mutableLongStateOf(0L) }

    var seekCountdown: Job? by remember { mutableStateOf(null) }
    // 不是 Compose 状态：每次按键都要记一下，之前拿状态当 effect 的 key，每按一次键整个控制器都要重组
    val interactionClock = remember { InteractionClock() }

    // 缓冲或调整进度时暂停自动隐藏；恢复后重新给用户完整的操作时间。
    androidx.compose.runtime.LaunchedEffect(showInfoSeekController, isSeeking, uiState.isBuffering) {
        if (showInfoSeekController && !isSeeking && !uiState.isBuffering) {
            interactionClock.touch()
            interactionClock.awaitIdle(5000)
            showInfoSeekController = false
        }
    }
    val customShortcutToggleMemory = remember { PlayerCustomShortcutToggleMemory() }

    fun calCoefficient(): Int {
        return if (System.currentTimeMillis() - lastSeekChangeTime < 200) {
            seekChangeCount++
            seekChangeCount / 5
        } else {
            seekChangeCount = 0
            0
        }
    }

    fun onTimeForward() {
        isSeeking = true
        val targetTime = goTime + (10000 + calCoefficient() * 5000)
        goTime =
            if (targetTime > seekerState.value.totalDuration) seekerState.value.totalDuration else targetTime
        lastSeekChangeTime = System.currentTimeMillis()
        logger.info { "onTimeForward: [goTime=$goTime]" }
    }

    fun onTimeBack() {
        isSeeking = true
        val targetTime = goTime - (10000 + calCoefficient() * 5000)
        goTime = if (targetTime < 0) 0 else targetTime
        lastSeekChangeTime = System.currentTimeMillis()
        logger.info { "onTimeBack: [goTime=$goTime]" }
    }

    fun startSeekCountdown() {
        seekCountdown?.cancel()
        seekCountdown = scope.launch {
            delay(1000)

            onGoTime(goTime)
            if (!isPlaying) onPlay()

            isSeeking = false
            interactionClock.touch()
        }
    }

    fun onDirectionLeft() {
        showInfoSeekController = true
        interactionClock.touch()
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeBack()
        startSeekCountdown()
    }

    fun onDirectionRight() {
        showInfoSeekController = true
        interactionClock.touch()
        if (!isSeeking) goTime = seekerState.value.currentTime
        onTimeForward()
        startSeekCountdown()
    }

    fun onSeekGoTime() {
        onGoTime(goTime)
        isSeeking = false
        if (!isPlaying) onPlay()
        showInfoSeekController = true
        interactionClock.touch()
        seekCountdown?.cancel()
    }

    fun onPlayPause() {
        if (isPlaying) onPause() else onPlay()
    }

    fun executeCustomShortcut(
        keyCode: Int,
        action: PlayerCustomShortcutAction
    ): Boolean {
        when (action) {
            PlayerCustomShortcutAction.ShowInfo -> {
                showInfoSeekController = true
            }

            PlayerCustomShortcutAction.OpenSettings -> {
                showInfoSeekController = false
                showMenuController = true
            }

            PlayerCustomShortcutAction.OpenVideoList -> {
                showListController = true
            }

            PlayerCustomShortcutAction.OpenRelatedVideos -> {
                if (isPlaying) onPause()
                showInfoSeekController = false
                showRelatedVideosController = true
            }

            PlayerCustomShortcutAction.TogglePlayPause -> {
                onPlayPause()
            }

            PlayerCustomShortcutAction.PlayPrevious -> {
                onPlayPrevious()
            }

            PlayerCustomShortcutAction.PlayNext -> {
                onPlayNext()
            }

            PlayerCustomShortcutAction.OpenVideoDetail -> {
                VideoInfoActivity.actionStart(
                    context = context,
                    aid = currentUiState.aid,
                    epid = currentUiState.epid.takeIf { currentUiState.fromSeason },
                    fromSeason = currentUiState.fromSeason,
                    fromController = true,
                    proxyArea = currentUiState.proxyArea
                )
            }

            PlayerCustomShortcutAction.OpenUpPage -> {
                if (!fromSeason && uiState.authorMid != 0L) {
                    onGoToUpPage()
                }
            }

            PlayerCustomShortcutAction.ToggleLoop -> {
                onToggleLoop()
            }

            PlayerCustomShortcutAction.ToggleDanmaku -> {
                if (uiState.danmakuState.enabledTypes.isEmpty()) {
                    onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(DanmakuType.entries))
                } else {
                    onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(emptyList()))
                }
            }

            PlayerCustomShortcutAction.ToggleSubtitle -> {
                onToggleSubtitle()
            }

            PlayerCustomShortcutAction.TogglePersistentBottomProgress -> {
                onTogglePersistentSeek()
            }

            is PlayerCustomShortcutAction.SetPlaybackSpeed -> {
                val target = customShortcutToggleMemory.playSpeed.selectTarget(
                    keyCode = keyCode,
                    current = uiState.playSpeed,
                    target = action.speed
                )
                onPlaySpeedChange(target)
            }

            is PlayerCustomShortcutAction.SetResolution -> {
                val target = customShortcutToggleMemory.quality.selectTarget(
                    keyCode = keyCode,
                    current = uiState.mediaProfileState.qualityId,
                    target = action.qualityId
                )
                if (!uiState.availableQuality.containsKey(target)) {
                    return true
                }
                onMediaProfileSettingChange(MediaProfileSettingAction.SetQuality(target))
            }

            is PlayerCustomShortcutAction.SetAudio -> {
                val target = customShortcutToggleMemory.audio.selectTarget(
                    keyCode = keyCode,
                    current = uiState.mediaProfileState.audio,
                    target = action.audio
                )
                if (!uiState.availableAudio.contains(target)) {
                    return true
                }
                onMediaProfileSettingChange(MediaProfileSettingAction.SetAudio(target))
            }

            is PlayerCustomShortcutAction.SetVideoCodec -> {
                val target = customShortcutToggleMemory.videoCodec.selectTarget(
                    keyCode = keyCode,
                    current = uiState.mediaProfileState.videoCodec,
                    target = action.codec
                )
                if (!uiState.availableVideoCodec.contains(target)) {
                    return true
                }
                onMediaProfileSettingChange(MediaProfileSettingAction.SetVideoCodec(target))
            }

            is PlayerCustomShortcutAction.SetAspectRatio -> {
                val target = customShortcutToggleMemory.aspectRatio.selectTarget(
                    keyCode = keyCode,
                    current = uiState.aspectRatio,
                    target = action.aspectRatio
                )
                onAspectRatioChange(target)
            }

            is PlayerCustomShortcutAction.SetDanmakuScale -> {
                val target = customShortcutToggleMemory.danmakuScale.selectTarget(
                    keyCode = keyCode,
                    current = uiState.danmakuState.scale,
                    target = action.scale
                )
                onDanmakuSettingChange(DanmakuSettingAction.SetScale(target))
            }

            is PlayerCustomShortcutAction.SetDanmakuOpacity -> {
                val target = customShortcutToggleMemory.danmakuOpacity.selectTarget(
                    keyCode = keyCode,
                    current = uiState.danmakuState.opacity,
                    target = action.opacity
                )
                onDanmakuSettingChange(DanmakuSettingAction.SetOpacity(target))
            }

            is PlayerCustomShortcutAction.SetDanmakuSpeedFactor -> {
                val target = customShortcutToggleMemory.danmakuSpeedFactor.selectTarget(
                    keyCode = keyCode,
                    current = uiState.danmakuState.speedFactor,
                    target = action.factor
                )
                onDanmakuSettingChange(DanmakuSettingAction.SetSpeedFactor(target))
            }

            is PlayerCustomShortcutAction.SetDanmakuArea -> {
                val target = customShortcutToggleMemory.danmakuArea.selectTarget(
                    keyCode = keyCode,
                    current = uiState.danmakuState.area,
                    target = action.area
                )
                onDanmakuSettingChange(DanmakuSettingAction.SetArea(target))
            }

            is PlayerCustomShortcutAction.SetDanmakuMaskEnabled -> {
                val target = customShortcutToggleMemory.danmakuMaskEnabled.selectTarget(
                    keyCode = keyCode,
                    current = uiState.danmakuState.maskEnabled,
                    target = action.enabled
                )
                onDanmakuSettingChange(DanmakuSettingAction.SetMaskEnabled(target))
            }

            is PlayerCustomShortcutAction.SetSubtitleFontSize -> {
                val target = customShortcutToggleMemory.subtitleFontSize.selectTarget(
                    keyCode = keyCode,
                    current = uiState.subtitleState.fontSize.value.roundToInt(),
                    target = action.sp
                )
                onSubtitleSettingChange(SubtitleSettingAction.SetFontSize(target.sp))
            }

            is PlayerCustomShortcutAction.SetSubtitleBackgroundOpacity -> {
                val target = customShortcutToggleMemory.subtitleOpacity.selectTarget(
                    keyCode = keyCode,
                    current = uiState.subtitleState.opacity,
                    target = action.opacity
                )
                onSubtitleSettingChange(SubtitleSettingAction.SetOpacity(target))
            }

            is PlayerCustomShortcutAction.SetSubtitleBottomPadding -> {
                val target = customShortcutToggleMemory.subtitleBottomPadding.selectTarget(
                    keyCode = keyCode,
                    current = uiState.subtitleState.bottomPadding.value.roundToInt(),
                    target = action.dp
                )
                onSubtitleSettingChange(SubtitleSettingAction.SetBottomPadding(target.dp))
            }
        }

        return true
    }

    fun handleCustomShortcut(event: KeyEvent): Boolean {
        if (showClickableControllers) return false

        val keyCode = event.nativeKeyEvent.keyCode
        if (!PlayerCustomShortcutKeys.isAllowedKeyCode(keyCode)) return false

        val shortcut = PlayerCustomShortcutsStore.getByKey()[keyCode] ?: return false
        if (event.type == KeyEventType.KeyUp) return true
        if (event.type != KeyEventType.KeyDown) return false
        if (event.nativeKeyEvent.repeatCount != 0) return true

        return executeCustomShortcut(keyCode, shortcut.action)
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        // 中键需要区分短按和长按
        val isConfirmKey =
            event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar

        if (event.type == KeyEventType.KeyUp && !isConfirmKey) {
            return true
        }

        logger.info { "[${event.key} press]" }

        if (handleCustomShortcut(event)) {
            return true
        }

        when (event.key) {
            Key.Back -> {
                if (showClickableControllers) {
                    showMenuController = false
                    showListController = false
                    showInfoSeekController = false
                    showRelatedVideosController = false
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastPressBack < 3000) {
                        onExit()
                    } else {
                        lastPressBack = currentTime
                        R.string.video_player_press_back_again_to_exit.toast(context)
                    }
                }
                return true
            }

            Key.Menu -> {
                showInfoSeekController = false
                showListController = false
                showRelatedVideosController = false
                showMenuController = true
                return true
            }

            Key(763) -> {
                showMenuController = true
                return true
            }

            Key.MediaPlayPause -> {
                onPlayPause()
                return true
            }

            Key.MediaPlay -> {
                if (!isPlaying) onPlay()
                return true
            }

            Key.MediaPause -> {
                if (isPlaying) onPause()
                return true
            }
        }

        if (showClickableControllers) {
            return false
        } else {
            when (event.key) {
                Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.nativeKeyEvent.isLongPress) {
                            showMenuController = true
                        }
                        return true
                    } else {
                        if (uiState.showBackToStart) {
                            onBackToStart()
                        } else {
                            onPlayPause()
                        }
                        return true
                    }
                }

                Key.DirectionUp -> {
                    showListController = true
                    return true
                }

                Key.DirectionDown -> {
                    showInfoSeekController = true
                    return true
                }

                Key.MediaRewind, Key.DirectionLeft -> {
                    if (uiState.showSkipToNextEp) onCancelSkipToNextEp()
                    showInfoSeekController = true
                    onDirectionLeft()
                    return true
                }

                Key.MediaFastForward, Key.DirectionRight -> {
                    showInfoSeekController = true
                    onDirectionRight()
                    return true
                }
            }
        }

        return false
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { event ->
                // 只记录操作；计时由上面的 effect 管理，缓冲期间不会误隐藏控制栏。
                interactionClock.touch()
                // 调用分离出去的处理函数
                handleKeyEvent(event)
            }
    ) {
        content()
        if (uiState.subtitleId != -1L) {
            BottomSubtitle(
                subtitleData = uiState.subtitleData,
                currentTime = { seekerState.value.currentTime },
                fontSize = uiState.subtitleState.fontSize,
                opacity = uiState.subtitleState.opacity,
                padding = uiState.subtitleState.bottomPadding,
            )
        }

        SkipTips(
            showBackToStart = uiState.showBackToStart,
            showSkipToNextEp = uiState.showSkipToNextEp,
            showPreviewTip = uiState.showPreviewTip,
        )

        PlayStateTips(
            isPlaying = uiState.playerState == PlayerState.Playing,
            isBuffering = uiState.isBuffering,
            isError = uiState.playerState is PlayerState.Error,
            errorMessage = (uiState.playerState as? PlayerState.Error)?.message,
        )

        RelatedVideosController(
            show = showRelatedVideosController,
            relatedVideos = uiState.relatedVideos,
            onVideoClicked = {
                onRelatedVideoClicked(it)
                showRelatedVideosController = false
            }
        )

        ControllerVideoInfo(
            modifier = Modifier.focusable(),
            show = showInfoSeekController,
            isSeeking = isSeeking,
            goTime = goTime,
            // 传 State 本身：进度每 100ms 变一次，在这里读 value 会让整个控制器每秒重组十次，隐藏时也一样
            seekerState = seekerState,
            title = uiState.title,
            clock = uiState.clock,
            videoShot = uiState.videoShot,
            videoShotCache = videoShotCache,
            fromSeason = fromSeason,
            danmakuEnabled = uiState.danmakuState.enabledTypes.isNotEmpty(),
            isLooping = isLooping,
            authorName = uiState.authorName,
            publishDate = uiState.publishDate,
            viewCount = uiState.viewCount,
            onlineCount = uiState.onlineCount,
            tooltipState = controlButtonTooltipState,
            onDirectionLeft = { onDirectionLeft() },
            onDirectionRight = { onDirectionRight() },
            onSeekGoTime = { onSeekGoTime() },
            onPlayPause = { onPlayPause() },
            onDanmakuSwitchChange = {
                if (uiState.danmakuState.enabledTypes.isEmpty()) {
                    onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(DanmakuType.entries))
                } else {
                    onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(emptyList()))
                }
            },
            onShowSettings = {
                showInfoSeekController = false
                showMenuController = true
            },
            onShowVideoList = {
                showInfoSeekController = false
                showListController = true
            },
            onShowRelatedVideos = {
                if (isPlaying) onPause()

                showInfoSeekController = false
                showRelatedVideosController = true
            },
            onGoToVideoInfo = {
                VideoInfoActivity.actionStart(
                    context = context,
                    aid = currentUiState.aid,
                    epid = currentUiState.epid.takeIf { currentUiState.fromSeason },
                    fromSeason = currentUiState.fromSeason,
                    fromController = true,
                    proxyArea = currentUiState.proxyArea
                )
            },
            onToggleLoop = onToggleLoop,
            onGoToUpPage = onGoToUpPage,
            isPlaying = isPlaying,
            onTogglePlayerInfo = onTogglePlayerInfo,
            showPlayerInfo = uiState.showPlayerInfo,
            onShowEngagement = {
                if (isPlaying) onPause()
                showInfoSeekController = false
                engagementAid = currentUiState.aid
            }
        )

        // 在普通控制层上方、菜单/弹窗下方绘制，不参与遥控器焦点。
        if (uiState.showPlayerInfo) {
            // 调试文本一秒才变一次，只在它变化时重组
            val debugInfo by remember(seekerState) { derivedStateOf { seekerState.value.debugInfo } }
            dev.aaa1115910.bv.player.PlayerDebugOverlay(
                text = debugInfo,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        VideoListController(
            show = showListController,
            currentCid = uiState.cid,
            videoList = uiState.availableVideoList,
            onPlayNewVideo = onPlayNewVideo
        )

        engagementAid?.takeIf { it == uiState.aid }?.let { targetAid ->
            androidx.compose.runtime.key(targetAid) {
                VideoEngagementDialog(aid = targetAid, onDismiss = { engagementAid = null })
            }
        }

        MenuController(
            show = showMenuController,
            uiState = uiState,
            onResolutionChange = { qualityId ->
                onMediaProfileSettingChange(
                    MediaProfileSettingAction.SetQuality(qualityId)
                )
            },
            onCodecChange = { codec ->
                onMediaProfileSettingChange(
                    MediaProfileSettingAction.SetVideoCodec(codec)
                )
            },
            onAudioChange = { audio ->
                onMediaProfileSettingChange(
                    MediaProfileSettingAction.SetAudio(audio)
                )
            },
            onAspectRatioChange = onAspectRatioChange,
            onPlaySpeedChange = onPlaySpeedChange,
            onDanmakuSwitchChange = { danmakuTypes ->
                onDanmakuSettingChange(DanmakuSettingAction.SetEnabledTypes(danmakuTypes))
            },
            onDanmakuSizeChange = { scale ->
                onDanmakuSettingChange(DanmakuSettingAction.SetScale(scale))
            },
            onDanmakuOpacityChange = { opacity ->
                onDanmakuSettingChange(DanmakuSettingAction.SetOpacity(opacity))
            },
            onDanmakuSpeedFactorChange = { factor ->
                onDanmakuSettingChange(DanmakuSettingAction.SetSpeedFactor(factor))
            },
            onDanmakuAreaChange = { area ->
                onDanmakuSettingChange(DanmakuSettingAction.SetArea(area))
            },
            onDanmakuMaskChange = { enabled ->
                onDanmakuSettingChange(DanmakuSettingAction.SetMaskEnabled(enabled))
            },
            onSubtitleChange = onSubtitleChange,
            onSubtitleSizeChange = { size ->
                onSubtitleSettingChange(SubtitleSettingAction.SetFontSize(size))
            },
            onSubtitleBackgroundOpacityChange = { opacity ->
                onSubtitleSettingChange(SubtitleSettingAction.SetOpacity(opacity))
            },
            onSubtitleBottomPadding = { padding ->
                onSubtitleSettingChange(SubtitleSettingAction.SetBottomPadding(padding))
            }
        )

        // 底栏按钮的提示最后画，和原来的 Popup 窗口一样盖在所有控制层上面
        ControlButtonTooltipHost(controlButtonTooltipState)
    }
}

/** 最近一次操作控制器的时间，只给自动隐藏计时用 */
private class InteractionClock {
    private var lastInteractionAt = 0L

    fun touch() {
        lastInteractionAt = SystemClock.uptimeMillis()
    }

    /** 挂起到连续 [idleMillis] 毫秒没有操作为止，中途有操作就顺延 */
    suspend fun awaitIdle(idleMillis: Long) {
        while (true) {
            val remaining = lastInteractionAt + idleMillis - SystemClock.uptimeMillis()
            if (remaining <= 0) return
            delay(remaining)
        }
    }
}

private class PlayerCustomShortcutToggleMemory {
    val playSpeed = mutableMapOf<Int, Float>()
    val quality = mutableMapOf<Int, Int>()
    val audio = mutableMapOf<Int, Audio>()
    val videoCodec = mutableMapOf<Int, VideoCodec>()
    val aspectRatio = mutableMapOf<Int, VideoAspectRatio>()
    val danmakuScale = mutableMapOf<Int, Float>()
    val danmakuOpacity = mutableMapOf<Int, Float>()
    val danmakuSpeedFactor = mutableMapOf<Int, Float>()
    val danmakuArea = mutableMapOf<Int, Float>()
    val danmakuMaskEnabled = mutableMapOf<Int, Boolean>()
    val subtitleFontSize = mutableMapOf<Int, Int>()
    val subtitleOpacity = mutableMapOf<Int, Float>()
    val subtitleBottomPadding = mutableMapOf<Int, Int>()
}

private fun <T> MutableMap<Int, T>.selectTarget(
    keyCode: Int,
    current: T,
    target: T
): T {
    return if (current == target) {
        this[keyCode] ?: target
    } else {
        this[keyCode] = current
        target
    }
}
