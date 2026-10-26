package dev.aaa1115910.bv.component.controllers.comment

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import dev.aaa1115910.biliapi.entity.reply.CommentPicture
import dev.aaa1115910.bv.component.GradientLoadingIndicator
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 大图最宽 1920，最长边不超过 4096：不少电视盒子的 GPU 纹理上限就是 4096，再大的硬件位图画不出来
private const val MAX_PICTURE_WIDTH = 1920f
private const val MAX_PICTURE_SIDE = 4096f

// 放大后方向键每按一下移动可视区域的比例
private const val PAN_STEP_FRACTION = 0.35f

private const val HINT_VISIBLE_MILLIS = 3000L

/**
 * 评论配图的大图弹窗
 *
 * - ← → 切换图片，确认放大，放大后方向键移动，确认或返回还原
 * - 长图放大时按宽度铺满，从顶部开始往下看
 *
 * 用独立的 Dialog 窗口：返回键先交给弹窗，关掉的只是大图，评论抽屉和播放页都不受影响
 */
@Composable
internal fun CommentPictureViewer(
    pictures: List<CommentPicture>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    var index by remember { mutableIntStateOf(initialIndex.coerceIn(pictures.indices)) }
    var zoomed by remember { mutableStateOf(false) }

    // 退场动画播完再真正关掉弹窗
    LaunchedEffect(visibleState.targetState, visibleState.isIdle) {
        if (!visibleState.targetState && visibleState.isIdle) onDismiss()
    }

    Dialog(
        onDismissRequest = {
            if (zoomed) zoomed = false else visibleState.targetState = false
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // 系统弹窗自带的压暗没法跟着淡入淡出，关掉，遮罩自己画。
        // 播放页是全屏沉浸的，弹窗窗口获焦时系统会把状态栏放出来，关掉后也不会自己收回去，这里跟播放页一样隐藏
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window ?: return@SideEffect
            window.setDimAmount(0f)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        PictureViewerContent(
            pictures = pictures,
            index = index,
            zoomed = zoomed,
            visibleState = visibleState,
            onIndexChange = {
                index = it
                zoomed = false
            },
            onZoomChange = { zoomed = it }
        )
    }
}

@Composable
private fun PictureViewerContent(
    pictures: List<CommentPicture>,
    index: Int,
    zoomed: Boolean,
    visibleState: MutableTransitionState<Boolean>,
    onIndexChange: (Int) -> Unit,
    onZoomChange: (Boolean) -> Unit
) {
    val picture = pictures[index]
    val single = pictures.size == 1
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var loadState by remember(index) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    // 操作提示停 3 秒就淡出，不一直压在图上；按任意键再出来
    var hintsVisible by remember { mutableStateOf(true) }
    var lastKeyAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastKeyAt) {
        hintsVisible = true
        delay(HINT_VISIBLE_MILLIS)
        hintsVisible = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val geometry = remember(picture, containerWidth, containerHeight) {
            PictureGeometry.of(
                picture = picture,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                horizontalPadding = with(density) { 64.dp.toPx() },
                verticalPadding = with(density) { 36.dp.toPx() }
            )
        }

        // 切图或者还原时回到整图，放大时长图从顶部开始
        LaunchedEffect(geometry, zoomed) {
            if (zoomed) {
                launch { scale.animateTo(geometry.zoomScale, tween(BVMotion.DurationMedium)) }
                val startY = if (geometry.isTall) geometry.maxPanY else 0f
                pan.animateTo(Offset(0f, startY), tween(BVMotion.DurationMedium))
            } else {
                launch { scale.animateTo(1f, tween(BVMotion.DurationMedium)) }
                pan.animateTo(Offset.Zero, tween(BVMotion.DurationMedium))
            }
        }

        fun panBy(dx: Float, dy: Float) {
            val target = Offset(
                (pan.targetValue.x + dx).coerceIn(-geometry.maxPanX, geometry.maxPanX),
                (pan.targetValue.y + dy).coerceIn(-geometry.maxPanY, geometry.maxPanY)
            )
            scope.launch { pan.animateTo(target, tween(160)) }
        }

        fun handleKey(event: KeyEvent): Boolean {
            if (event.type != KeyEventType.KeyDown) return false
            lastKeyAt = SystemClock.uptimeMillis()
            val stepX = containerWidth * PAN_STEP_FRACTION
            val stepY = containerHeight * PAN_STEP_FRACTION
            when (event.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.nativeKeyEvent.repeatCount == 0) onZoomChange(!zoomed)
                }

                Key.DirectionLeft -> if (zoomed) panBy(stepX, 0f) else if (index > 0) onIndexChange(index - 1)
                Key.DirectionRight -> if (zoomed) panBy(-stepX, 0f) else if (index < pictures.lastIndex) onIndexChange(index + 1)
                Key.DirectionUp -> if (zoomed) panBy(0f, stepY)
                Key.DirectionDown -> if (zoomed) panBy(0f, -stepY)
                else -> return false
            }
            return true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent(::handleKey)
                .focusRequester(focusRequester)
                .focusable()
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(tween(BVMotion.DurationMedium)),
                exit = fadeOut(tween(BVMotion.DurationFast))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                )
            }

            AnimatedVisibility(
                modifier = Modifier.align(Alignment.Center),
                visibleState = visibleState,
                enter = fadeIn(tween(BVMotion.DurationMedium)) +
                    scaleIn(tween(BVMotion.DurationMedium, easing = BVMotion.EmphasizedDecelerateEasing), initialScale = 0.94f),
                exit = fadeOut(tween(BVMotion.DurationFast)) + scaleOut(tween(BVMotion.DurationFast), targetScale = 0.96f)
            ) {
                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { geometry.fitWidth.toDp() },
                            height = with(density) { geometry.fitHeight.toDp() }
                        )
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            translationX = pan.value.x
                            translationY = pan.value.y
                        }
                ) {
                    // 先用列表里已经加载过的缩略图垫着，大图出来前不至于一片黑
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = picture.thumbnailUrl(single),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds
                    )
                    AsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = rememberViewerRequest(picture),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        onState = { loadState = it }
                    )
                }
            }

            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                visibleState = visibleState,
                enter = fadeIn(tween(BVMotion.DurationMedium)),
                exit = fadeOut(tween(BVMotion.DurationFast))
            ) {
                ViewerOverlay(
                    index = index,
                    count = pictures.size,
                    zoomed = zoomed,
                    hintsVisible = hintsVisible,
                    loadState = loadState
                )
            }
        }
    }
}

/**
 * 图片在屏幕上的摆放
 *
 * @param fitWidth 整图显示时的宽度（px），四周留出边距
 * @param isTall 长图：放大时按屏幕宽度铺满，从顶部开始看；其他图居中放大两倍
 * @param maxPanX 放大后左右最多能移动多少（px），图片不会被移出屏幕
 */
internal data class PictureGeometry(
    val fitWidth: Float,
    val fitHeight: Float,
    val isTall: Boolean,
    val zoomScale: Float,
    val maxPanX: Float,
    val maxPanY: Float
) {
    companion object {
        fun of(
            picture: CommentPicture,
            containerWidth: Float,
            containerHeight: Float,
            horizontalPadding: Float,
            verticalPadding: Float
        ): PictureGeometry {
            val aspect = picture.aspectRatio
            val areaWidth = (containerWidth - horizontalPadding * 2).coerceAtLeast(1f)
            val areaHeight = (containerHeight - verticalPadding * 2).coerceAtLeast(1f)
            val fitWidth = min(areaWidth, areaHeight * aspect)
            val fitHeight = fitWidth / aspect
            val isTall = aspect < 1f && fitWidth < areaWidth * 0.7f
            val zoomScale = if (isTall) max(containerWidth / fitWidth, 1.5f) else 2f
            return PictureGeometry(
                fitWidth = fitWidth,
                fitHeight = fitHeight,
                isTall = isTall,
                zoomScale = zoomScale,
                maxPanX = ((fitWidth * zoomScale - containerWidth) / 2).coerceAtLeast(0f),
                maxPanY = ((fitHeight * zoomScale - containerHeight) / 2).coerceAtLeast(0f)
            )
        }
    }
}

private val CommentPicture.aspectRatio: Float
    get() = (width / height).takeIf { it.isFinite() && it > 0f } ?: 1f

/** 让图床按需要的宽度缩好再下发，按原尺寸解码，放大时依然清晰 */
@Composable
private fun rememberViewerRequest(picture: CommentPicture): ImageRequest {
    val context = LocalContext.current
    return remember(picture) {
        val aspect = picture.aspectRatio
        val width = minOf(
            MAX_PICTURE_WIDTH,
            MAX_PICTURE_SIDE * aspect,
            picture.width.takeIf { it > 0f } ?: MAX_PICTURE_WIDTH
        ).roundToInt().coerceAtLeast(1)
        ImageRequest.Builder(context)
            .data(picture.url.hdslbResized("${width}w"))
            .size(Size.ORIGINAL)
            .build()
    }
}

@Composable
private fun ViewerOverlay(
    index: Int,
    count: Int,
    zoomed: Boolean,
    hintsVisible: Boolean,
    loadState: AsyncImagePainter.State
) {
    Box(Modifier.fillMaxSize()) {
        if (count > 1) {
            Text(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                text = "${index + 1} / $count",
                fontSize = 12.sp,
                color = Color.White
            )
        }

        if (!zoomed && count > 1) {
            if (index > 0) PagerArrow(Modifier.align(Alignment.CenterStart), forward = false)
            if (index < count - 1) PagerArrow(Modifier.align(Alignment.CenterEnd), forward = true)
        }

        when (loadState) {
            is AsyncImagePainter.State.Loading, AsyncImagePainter.State.Empty -> GradientLoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
                size = 28.dp,
                strokeWidth = 2.5.dp
            )

            is AsyncImagePainter.State.Error -> Text(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = "图片加载失败",
                fontSize = 13.sp,
                color = Color.White
            )

            is AsyncImagePainter.State.Success -> Unit
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            visible = hintsVisible,
            enter = fadeIn(tween(BVMotion.DurationFast)),
            exit = fadeOut(tween(BVMotion.DurationMedium))
        ) {
            Text(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                text = when {
                    zoomed -> "方向键 移动　·　确认 / 返回 还原"
                    count > 1 -> "← → 切换　·　确认 放大　·　返回 关闭"
                    else -> "确认 放大　·　返回 关闭"
                },
                fontSize = 11.sp,
                color = BVColor.TextSecondary
            )
        }
    }
}

@Composable
private fun PagerArrow(modifier: Modifier, forward: Boolean) {
    Box(
        modifier = modifier
            .padding(horizontal = 14.dp)
            .size(40.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(26.dp),
            imageVector = if (forward) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
            contentDescription = if (forward) "下一张" else "上一张",
            tint = Color.White.copy(alpha = 0.85f)
        )
    }
}
