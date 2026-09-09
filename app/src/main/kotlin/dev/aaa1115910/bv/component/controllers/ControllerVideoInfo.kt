package dev.aaa1115910.bv.component.controllers

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.video.VideoShot
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.ControllerButton
import dev.aaa1115910.bv.entity.ControllerButtonsStore
import dev.aaa1115910.bv.ui.state.SeekerState
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.FocusRingBrush
import dev.aaa1115910.bv.util.VideoShotImageCache
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.formatPubTimeString
import dev.aaa1115910.bv.util.toWanString
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun ControllerVideoInfo(
    modifier: Modifier = Modifier,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    title: String,
    clock: Pair<Int, Int>,
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    fromSeason: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    authorName: String,
    publishDate: Date?,
    viewCount: Int,
    onlineCount: String?,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onShowVideoList: () -> Unit,
    onShowRelatedVideos: () -> Unit,
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopCenter),
            visible = show,
            enter = expandVertically(),
            exit = shrinkVertically(),
            label = "ControllerTopVideoInfo"
        ) {
            ControllerVideoInfoTop(
                modifier = Modifier.align(Alignment.TopCenter),
                title = title,
                clock = clock,
                authorName = authorName,
                publishDate = publishDate,
                viewCount = viewCount,
                onlineCount = onlineCount
            )
        }
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = show,
            enter = expandVertically(),
            exit = shrinkVertically(),
            label = "ControllerBottomVideoInfo"
        ) {
            ControllerVideoInfoBottom(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                show = show,
                isSeeking = isSeeking,
                goTime = goTime,
                seekerState = seekerState,
                videoShot = videoShot,
                videoShotCache = videoShotCache,
                fromSeason = fromSeason,
                danmakuEnabled = danmakuEnabled,
                isLooping = isLooping,
                onDirectionLeft = onDirectionLeft,
                onDirectionRight = onDirectionRight,
                onSeekGoTime = onSeekGoTime,
                onPlayPause = onPlayPause,
                onDanmakuSwitchChange = onDanmakuSwitchChange,
                onShowSettings = onShowSettings,
                onShowVideoList = onShowVideoList,
                onShowRelatedVideos = onShowRelatedVideos,
                onGoToVideoInfo = onGoToVideoInfo,
                onToggleLoop = onToggleLoop,
                onGoToUpPage = onGoToUpPage
            )
        }
    }
}

@Composable
fun ControllerVideoInfoTop(
    modifier: Modifier = Modifier,
    title: String,
    clock: Pair<Int, Int>,
    authorName: String = "",
    publishDate: Date? = null,
    viewCount: Int = -1,
    onlineCount: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                MaterialTheme.shapes.large.copy(
                    topStart = CornerSize(0.dp),
                    topEnd = CornerSize(0.dp)
                )
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f), // 上部颜色较深
                        Color.Black.copy(alpha = 0f)  // 下部颜色较浅
                    )
                )
            )
            .padding(horizontal = 32.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 1f
                        ),
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                VideoMetaLine(
                    authorName = authorName,
                    publishDate = publishDate,
                    viewCount = viewCount,
                    onlineCount = onlineCount
                )
            }
            Clock(
                hour = clock.first,
                minute = clock.second,
            )
        }
    }
}

/**
 * 标题下面那行副信息：UP主 · 发布时间 · 播放量 · 当前在看
 *
 * 拿不到的字段直接不显示，不占位也不留下孤零零的分隔点。
 * 「在看」用品牌粉标出来，它是唯一会实时变的数字。
 */
@Composable
private fun VideoMetaLine(
    modifier: Modifier = Modifier,
    authorName: String,
    publishDate: Date?,
    viewCount: Int,
    onlineCount: String?
) {
    val metaItems = buildList {
        authorName.takeIf { it.isNotBlank() }?.let { add(it) }
        publishDate?.let { add(it.formatPubTimeString()) }
        viewCount.takeIf { it >= 0 }?.let { add("${it.toWanString()}次播放") }
    }
    if (metaItems.isEmpty() && onlineCount == null) return

    val metaTextStyle = MaterialTheme.typography.labelLarge.copy(
        shadow = Shadow(color = Color.Black, blurRadius = 1f)
    )

    @Composable
    fun Separator() = Text(
        text = "·",
        style = metaTextStyle,
        color = Color.White.copy(alpha = 0.4f)
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        metaItems.forEachIndexed { index, item ->
            if (index > 0) Separator()
            Text(
                text = item,
                style = metaTextStyle,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        onlineCount?.let {
            if (metaItems.isNotEmpty()) Separator()
            Text(
                text = "$it 人在看",
                style = metaTextStyle,
                color = BVColor.PinkBright,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ControllerVideoInfoBottom(
    modifier: Modifier = Modifier,
    show: Boolean,
    isSeeking: Boolean,
    goTime: Long,
    seekerState: SeekerState,
    videoShot: VideoShot?,
    videoShotCache: VideoShotImageCache,
    fromSeason: Boolean,
    danmakuEnabled: Boolean,
    isLooping: Boolean,
    onDirectionLeft: () -> Unit,
    onDirectionRight: () -> Unit,
    onSeekGoTime: () -> Unit,
    onPlayPause: () -> Unit,
    onDanmakuSwitchChange: () -> Unit,
    onShowSettings: () -> Unit,
    onShowVideoList: () -> Unit,
    onShowRelatedVideos: () -> Unit,
    onGoToVideoInfo: () -> Unit,
    onToggleLoop: () -> Unit,
    onGoToUpPage: () -> Unit
) {
    val seekFocusRequester = remember { FocusRequester() }
    val buttonsFocusRequester = remember { FocusRequester() }

    var isSeekFocused by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            delay(50)
            try {
                seekFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                Log.d("ControllerVideoInfo", "requestFocus failed")
            }
        }
    }
    Column(
        modifier = modifier
            .clip(
                MaterialTheme.shapes.large
                    .copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
            ),
        verticalArrangement = Arrangement.Bottom
    ) {
        if (isSeeking && videoShot != null) {
            VideoShot(
                modifier = Modifier
                    .padding(horizontal = 48.dp),
                videoShot = videoShot,
                imageCache = videoShotCache,
                position = goTime,
                duration = seekerState.totalDuration,
                coercedOffset = (-24).dp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.padding(bottom = 2.dp, start = 24.dp),
                text = "${if (isSeeking) goTime.formatHourMinSec() else seekerState.currentTime.formatHourMinSec()} / ${seekerState.totalDuration.formatHourMinSec()}",
                color = Color.White,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black, blurRadius = 1f),
                ),
            )
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .border(
                    width = 2.dp,
                    // 和卡片、按钮统一成同一条渐变焦点环
                    brush = if (isSeekFocused) FocusRingBrush else SolidColor(Color.Transparent),
                    shape = RoundedCornerShape(10.dp)
                )
                .focusable()
                .focusRequester(seekFocusRequester)
                .onKeyEvent {
                    when (it.key) {
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            if (isSeeking) {
                                onSeekGoTime()
                            } else {
                                onPlayPause()
                            }
                            return@onKeyEvent true
                        }

                        Key.DirectionLeft, Key.MediaRewind -> {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            onDirectionLeft()
                            return@onKeyEvent true
                        }

                        Key.DirectionRight, Key.MediaFastForward -> {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            onDirectionRight()
                            return@onKeyEvent true
                        }

                        Key.DirectionDown -> {
                            if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                            buttonsFocusRequester.requestFocus()
                            return@onKeyEvent true
                        }
                    }
                    return@onKeyEvent false
                }
                .onFocusChanged {
                    isSeekFocused = it.isFocused
                },
        ) {
            VideoProgressSeek(
                modifier = Modifier
                    .focusable()
                    .fillMaxWidth(),
                duration = seekerState.totalDuration,
                position = if (isSeeking) goTime else seekerState.currentTime,
                bufferedPercentage = seekerState.bufferedPercentage,
                isPersistentSeek = false
            )
        }

        // 控制条按钮的顺序、显隐与默认焦点由设置决定，这里只负责把配置渲染出来
        val buttonConfigs = remember { ControllerButtonsStore.get() }
        val visibleButtons = buttonConfigs.filter { config ->
            !config.hidden && (config.button.availableInSeason || !fromSeason)
        }
        // 有开关态的按钮图标要跟着当前状态走，不能用枚举里的静态图标
        val iconOf: (ControllerButton) -> Int = { button ->
            when (button) {
                ControllerButton.Danmaku ->
                    if (danmakuEnabled) R.drawable.danmaku_on_24px else R.drawable.danmaku_off_24px

                ControllerButton.PlayMode ->
                    if (isLooping) R.drawable.repeat_one_on_24px else R.drawable.repeat_one_24px

                else -> button.icon
            }
        }
        val actionOf: (ControllerButton) -> () -> Unit = { button ->
            when (button) {
                ControllerButton.PlayPause -> onPlayPause
                ControllerButton.Danmaku -> onDanmakuSwitchChange
                ControllerButton.Settings -> onShowSettings
                ControllerButton.VideoList -> onShowVideoList
                ControllerButton.VideoDetail -> onGoToVideoInfo
                ControllerButton.UpSpace -> onGoToUpPage
                ControllerButton.Related -> onShowRelatedVideos
                ControllerButton.PlayMode -> onToggleLoop
            }
        }

        // 默认焦点按钮被隐藏或在番剧下不可用时，退回第一个可见按钮。
        // 焦点仍由进度条按下键时的 buttonsFocusRequester 触发，这里只决定它落在哪个按钮上
        val defaultFocusIndex = visibleButtons
            .indexOfFirst { it.isDefaultFocus }
            .coerceAtLeast(0)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onKeyEvent {
                    if (it.key == Key.DirectionUp) {
                        if (it.type == KeyEventType.KeyUp) return@onKeyEvent true
                        seekFocusRequester.requestFocus()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)
        ) {
            visibleButtons.forEachIndexed { index, config ->
                val button = config.button
                Surface(
                    modifier = if (index == defaultFocusIndex) {
                        Modifier.focusRequester(buttonsFocusRequester)
                    } else {
                        Modifier
                    },
                    onClick = actionOf(button),
                    shape = ClickableSurfaceDefaults.shape(
                        shape = MaterialTheme.shapes.small,
                    ),
                ) {
                    Icon(
                        painter = painterResource(id = iconOf(button)),
                        contentDescription = button.title,
                        modifier = Modifier.padding(5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Clock(
    modifier: Modifier = Modifier,
    hour: Int,
    minute: Int,
) {
    Text(
        modifier = modifier,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = TextStyle(
            shadow = Shadow(color = Color.Black, blurRadius = 1f),
        ),
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 32.sp)) {
                append("$hour".padStart(2, '0'))
                append(":")
                append("$minute".padStart(2, '0'))
            }
        }
    )
}

@Preview
@Composable
private fun ClockPreview() {
    val clock = Triple(12, 30, 30)
    BVTheme {
        Clock(
            hour = clock.first,
            minute = clock.second,
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ControllerVideoInfoPreview() {
    var show by remember { mutableStateOf(true) }

    BVTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { show = !show }) {
                Text(text = "Switch")
            }
        }
        ControllerVideoInfo(
            modifier = Modifier.fillMaxSize(),
            show = show,
            isSeeking = false,
            goTime = 0,
            seekerState = SeekerState(0, 0, 0, ""),
            title = "【A320】民航史上最佳逆袭！A320的前世今生！民航史上最佳逆袭！A320的前世今生！",
            clock = Pair(12, 30),
            videoShot = null,
            videoShotCache = VideoShotImageCache(),
            fromSeason = false,
            danmakuEnabled = false,
            isLooping = false,
            authorName = "某位UP主",
            publishDate = Date(),
            viewCount = 123456,
            onlineCount = "1000+",
            onDirectionRight = {},
            onDirectionLeft = {},
            onSeekGoTime = {},
            onPlayPause = {},
            onDanmakuSwitchChange = {},
            onShowSettings = {},
            onShowVideoList = {},
            onShowRelatedVideos = {},
            onGoToVideoInfo = {},
            onToggleLoop = {},
            onGoToUpPage = {},
        )
    }
}