package dev.aaa1115910.bv.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.FocusRingBrush
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMaskFrame
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMobMaskFrame
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuWebMaskFrame
import kotlinx.coroutines.launch

/**
 * 焦点高亮：缩放 + 渐变描边 + 彩色光晕。
 *
 * 缩放和光晕都写在图层里，动画值只在绘制阶段读取，这样每帧不会触发重组，长列表里滑动才不会掉帧。
 *
 * 用 Modifier.Node 实现而不是 composed：每张视频卡片都带这个修饰符，composed 在每次组合、重组时都要重新物化，
 * animateFloatAsState 还会给每张卡片起一个常驻协程；换成 Node 后只有焦点真的变化时才启动动画。
 * 视觉效果和原来一致：图层参数、弹簧参数都没变，描边依旧画在缩放图层里面。
 *
 * @param shape 描边与光晕的形状，需要和被包裹内容的圆角保持一致
 * @param focusedScale 获焦时放大到的倍数
 * @param glowElevation 光晕的投影高度。投影画在内容底下且不会被抠空，**内容半透明时会透出来变成灰块**，
 *   这种情况传 0.dp 关掉光晕
 * @param ringBrush 描边渐变，默认是「粉 → 紫 → 青」
 */
fun Modifier.focusHighlight(
    shape: Shape = ShapeDefaults.Large,
    focusedScale: Float = 1.06f,
    borderWidth: Dp = 3.dp,
    glowColor: Color = BVColor.FocusGlow,
    glowElevation: Dp = 20.dp,
    ringBrush: Brush? = null
): Modifier = this
    .then(FocusHighlightLayerElement(shape, focusedScale, glowColor, glowElevation))
    // 描边要排在图层后面，才会画进缩放图层里、跟着内容一起放大
    .then(FocusHighlightRingElement(shape, borderWidth, ringBrush ?: FocusRingBrush))

/** animateFloatAsState 会把弹簧的可见阈值换成 0.01，这里保持一致，动画轨迹才和原来一样 */
private const val FocusProgressVisibilityThreshold = 0.01f

private val FocusHighlightSpec: SpringSpec<Float> = BVMotion.focusSpring<Float>().let {
    spring(it.dampingRatio, it.stiffness, FocusProgressVisibilityThreshold)
}

private object FocusHighlightTraverseKey

private data class FocusHighlightLayerElement(
    val shape: Shape,
    val focusedScale: Float,
    val glowColor: Color,
    val glowElevation: Dp
) : ModifierNodeElement<FocusHighlightLayerNode>() {
    override fun create() = FocusHighlightLayerNode(shape, focusedScale, glowColor, glowElevation)

    override fun update(node: FocusHighlightLayerNode) {
        node.update(shape, focusedScale, glowColor, glowElevation)
    }
}

/** 监听焦点、驱动焦点进度动画，并用图层做缩放和光晕 */
private class FocusHighlightLayerNode(
    private var shape: Shape,
    private var focusedScale: Float,
    private var glowColor: Color,
    private var glowElevation: Dp
) : Modifier.Node(), FocusEventModifierNode, LayoutModifierNode, TraversableNode {
    override val traverseKey: Any = FocusHighlightTraverseKey

    var progress = Animatable(0f, FocusProgressVisibilityThreshold)
        private set
    private var hasFocus = false
    private var layerBlock = createLayerBlock()

    fun update(shape: Shape, focusedScale: Float, glowColor: Color, glowElevation: Dp) {
        this.shape = shape
        this.focusedScale = focusedScale
        this.glowColor = glowColor
        this.glowElevation = glowElevation
        // 换一个 lambda，重新摆放时图层才会重新读取参数
        layerBlock = createLayerBlock()
    }

    private fun createLayerBlock(): GraphicsLayerScope.() -> Unit = {
        val value = progress.value
        // 弹簧会略微过冲，这里不做 coerce，让放大有一点回弹
        val scaleValue = 1f + (focusedScale - 1f) * value
        scaleX = scaleValue
        scaleY = scaleValue
        shape = this@FocusHighlightLayerNode.shape
        clip = false
        shadowElevation = glowElevation.toPx() * value.coerceIn(0f, 1f)
        ambientShadowColor = glowColor
        spotShadowColor = glowColor
    }

    override fun onFocusEvent(focusState: FocusState) {
        val focused = focusState.hasFocus
        if (focused == hasFocus) return
        hasFocus = focused
        val target = if (focused) 1f else 0f
        coroutineScope.launch { progress.animateTo(target, FocusHighlightSpec) }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }

    override fun onDetach() {
        // 列表项被复用或重新挂载时回到未获焦状态，和原来 composed 里 remember 的状态随组合一起重建一致
        hasFocus = false
        progress = Animatable(0f, FocusProgressVisibilityThreshold)
        layerBlock = createLayerBlock()
    }
}

private data class FocusHighlightRingElement(
    val shape: Shape,
    val borderWidth: Dp,
    val brush: Brush
) : ModifierNodeElement<FocusHighlightRingNode>() {
    override fun create() = FocusHighlightRingNode(shape, borderWidth, brush)

    override fun update(node: FocusHighlightRingNode) {
        node.shape = shape
        node.borderWidth = borderWidth
        node.brush = brush
    }
}

/** 渐变描边，透明度跟随前面那个图层节点的焦点进度 */
private class FocusHighlightRingNode(
    var shape: Shape,
    var borderWidth: Dp,
    var brush: Brush
) : Modifier.Node(), DrawModifierNode {
    private var layerNode: FocusHighlightLayerNode? = null

    override fun onAttach() {
        layerNode = findNearestAncestor(FocusHighlightTraverseKey) as? FocusHighlightLayerNode
    }

    override fun onDetach() {
        layerNode = null
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val visible = (layerNode?.progress?.value ?: 0f).coerceIn(0f, 1f)
        if (visible > 0.01f) {
            drawOutline(
                outline = shape.createOutline(size, layoutDirection, this),
                brush = brush,
                alpha = visible,
                style = Stroke(width = borderWidth.toPx())
            )
        }
    }
}

/**
 * 获取到焦点时显示描边
 *
 * @param animate 描边呼吸闪烁，用来提示「这里还需要再按一下」
 */
fun Modifier.focusedBorder(
    shape: Shape = ShapeDefaults.Large,
    animate: Boolean = false
): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }

    // 无限动画只要还在组合里就会每帧跑一次，哪怕没有人读它的值。
    // 不要呼吸效果时就不创建，否则带常驻描边的页面（详情页、轮播图）会一直每帧空转
    val breathAlpha: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "infinite border color transition").animateFloat(
            initialValue = 1f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "focused border breath alpha"
        )
    } else {
        null
    }
    val appearAlpha by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = BVMotion.smoothSpring(),
        label = "focused border alpha"
    )

    onFocusChanged { hasFocus = it.hasFocus }
        .drawWithContent {
            drawContent()
            val alpha = appearAlpha.coerceIn(0f, 1f) * (breathAlpha?.value ?: 1f)
            if (alpha > 0.01f) {
                drawOutline(
                    outline = shape.createOutline(size, layoutDirection, this),
                    brush = FocusRingBrush,
                    alpha = alpha,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
}

/**
 * 在没有获取到焦点的时候缩小，以便在获取到焦点的时候“放大”
 */
fun Modifier.focusedScale(
    scale: Float = 0.9f
): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }
    val scaleValue by animateFloatAsState(
        targetValue = if (hasFocus) 1f else scale,
        animationSpec = BVMotion.focusSpring(),
        label = "focused scale"
    )

    onFocusChanged { hasFocus = it.hasFocus }
        .graphicsLayer {
            scaleX = scaleValue
            scaleY = scaleValue
        }
}

fun Modifier.bitmapMask(
    bitmap: Bitmap,
    videoAspectRatio: Float, // 视频的宽高比 (例如 1920/1080 ≈ 1.77, 21/9 ≈ 2.33)
): Modifier = composed {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawContent()

            val screenWidth = size.width
            val screenHeight = size.height
            val screenAspectRatio = screenWidth / screenHeight

            val dstWidth: Float
            val dstHeight: Float
            val offsetX: Float
            val offsetY: Float

            if (videoAspectRatio > screenAspectRatio) {
                dstWidth = screenWidth
                dstHeight = dstWidth / videoAspectRatio

                offsetX = 0f
                offsetY = (screenHeight - dstHeight) / 2f
            } else {
                dstHeight = screenHeight
                dstWidth = dstHeight * videoAspectRatio

                offsetY = 0f
                offsetX = (screenWidth - dstWidth) / 2f
            }

            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                dstSize = IntSize(dstWidth.toInt(), dstHeight.toInt()),
                blendMode = BlendMode.DstIn
            )

            canvas.restore()
        }
    }
}

fun Modifier.danmakuWebMask(
    frame: DanmakuWebMaskFrame,
    aspectRatio: Float,
): Modifier = composed {
    // remember(frame) 保证 SVG 解析和 Bitmap 创建只在帧变化时执行一次
    val bitmap = remember(frame) { buildWebMaskBitmap(frame.svg) }
        ?: return@composed this

    bitmapMask(bitmap, aspectRatio)
}

/** 蒙版 SVG 少数情况下会给出很大的尺寸，限一下上限，免得每帧都去申请一张大图 */
private const val MAX_WEB_MASK_SIZE = 1024f

/**
 * 把一帧 webmask SVG 栅格化成蒙版位图
 *
 * B 站下发的 SVG 只写 `viewBox`、不写 `width`/`height`，
 * 这种情况下 androidsvg 的 [SVG.getDocumentWidth] 返回的是 **-1**。
 * 之前直接 `documentWidth.toInt().coerceAtLeast(1)` 拿去建 Bitmap，
 * 结果是一张 1x1 的废图，再被拉伸到整个画面 —— 这就是「防遮挡没有用」的原因。
 * 所以这里在拿不到文档尺寸时回退到 viewBox，并显式指定渲染视口。
 */
private fun buildWebMaskBitmap(svg: String): Bitmap? = runCatching {
    val svgObj = SVG.getFromString(svg)
    val viewBox: RectF? = svgObj.documentViewBox

    val width = svgObj.documentWidth.takeIf { it > 0f }
        ?: viewBox?.width()?.takeIf { it > 0f }
        ?: return@runCatching null
    val height = svgObj.documentHeight.takeIf { it > 0f }
        ?: viewBox?.height()?.takeIf { it > 0f }
        ?: return@runCatching null

    val scale = minOf(1f, MAX_WEB_MASK_SIZE / maxOf(width, height))
    val bitmapWidth = (width * scale).toInt().coerceAtLeast(1)
    val bitmapHeight = (height * scale).toInt().coerceAtLeast(1)

    val bmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // 必须显式给视口：没有 width/height 的 SVG 走无参重载时，
    // 会按 canvas 的裁剪区域自己算缩放，结果不受控
    svgObj.renderToCanvas(
        canvas,
        RectF(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat())
    )
    bmp
}.getOrNull()

fun Modifier.danmakuMobMask(
    frame: DanmakuMobMaskFrame,
    aspectRatio: Float,
): Modifier = composed {
    // remember(frame) 保证像素解码和 Bitmap 创建只在帧变化时执行一次，
    // 避免每次 recompose 都重建 Bitmap（对长视频是显著的内存和 CPU 压力）
    val bitmap = remember(frame) {
        val width = frame.width
        val height = frame.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 1bpp 连续 bit 流，MSB first
        val pixels = IntArray(width * height) { i ->
            val byteIndex = i / 8
            val bitOffset = 7 - (i % 8)
            val bit = (frame.image[byteIndex].toInt() shr bitOffset) and 1
            if (bit == 1) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        bmp
    }

    bitmapMask(bitmap, aspectRatio)
}

fun Modifier.danmakuMask(
    frame: DanmakuMaskFrame?,
    aspectRatio: Float,
): Modifier = composed {
    if (frame == null) return@composed this

    when (frame) {
        is DanmakuWebMaskFrame -> danmakuWebMask(frame, aspectRatio)
        is DanmakuMobMaskFrame -> danmakuMobMask(frame, aspectRatio)
    }
}