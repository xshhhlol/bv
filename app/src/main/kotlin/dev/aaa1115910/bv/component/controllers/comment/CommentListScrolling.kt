package dev.aaa1115910.bv.component.controllers.comment

import android.view.Choreographer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * 评论列表在遥控器下的滚动行为
 *
 * 焦点往下走到屏幕外的评论时，LazyColumn 要在处理按键的那一刻把这条评论现组合、现排版出来。
 * 一条评论有头像、表情、配图、楼中楼预览，这一下在电视盒子上很重，长按时要控制节奏，别让按键把主线程占满。
 *
 * 试过在列表停下来后用 LazyListPrefetchStrategy 提前组合下一条，但 Compose 1.8 的预取调度器只在有新帧画出来后
 * 利用这一帧的剩余时间，列表静止时算出来的可用时间是 0，标成紧急也不执行；真执行时反而落在滚动动画的帧里，所以没用
 */

/**
 * 焦点移动时，评论还整条在屏幕里就不滚动，快出屏幕了才滚到刚好露全。
 * 电视上 Compose 默认会把获焦项钉在列表 30% 的位置，焦点每挪一下整个列表都跟着滚，这里显式换回最小滚动
 */
private val MinimalScrollBringIntoViewSpec = object : BringIntoViewSpec {}

// 超长评论长按时的滚动速度：每秒滚过多少个可视高度
private const val HOLD_SCROLL_VIEWPORTS_PER_SECOND = 1.6f

// 遗失松键事件时的兜底；正常松键会立即取消连续滚动
private const val HOLD_TIMEOUT_NANOS = 200_000_000L

// 单次按下时超长评论一次滚多少个可视高度
private const val TALL_ITEM_STEP_FRACTION = 0.6f

/** 评论列表的滚动状态：列表本身和超长评论的连续滚动 */
@Stable
internal class CommentListState(val scope: CoroutineScope) {
    val lazyListState = LazyListState()

    internal val tallItemScroller = TallItemScroller(lazyListState, scope)
}

@Composable
internal fun rememberCommentListState(): CommentListState {
    val scope = rememberCoroutineScope()
    return remember { CommentListState(scope) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommentLazyColumn(
    state: CommentListState,
    spacing: Dp,
    enabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    DisposableEffect(state) {
        onDispose { state.tallItemScroller.stop() }
    }
    LaunchedEffect(state, enabled) {
        if (!enabled) state.tallItemScroller.stop()
    }
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides MinimalScrollBringIntoViewSpec
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state.lazyListState,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

/**
 * 长按方向键时，上一次移动还没画到屏幕上就丢掉这次重复
 *
 * 按键事件不跟帧对齐，来一个就立刻处理一个。遥控器长按每 50ms 左右重复一次，
 * 慢一点的盒子上每次移动的开销超过这个间隔，按键就会把主线程占满：画面停住不动，松手后还要把积压的按键走完。
 * 等上一次移动画出来再接受下一次，滚动速度就跟着设备实际能画多快走。单次按下不受影响
 */
internal class KeyRepeatGate {
    private var waitingForFrame = false

    /** @return true 表示这次重复按键应该丢掉 */
    fun shouldDrop(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.key != Key.DirectionDown && event.key != Key.DirectionUp) return false
        if (event.nativeKeyEvent.repeatCount > 0 && waitingForFrame) return true
        waitingForFrame = true
        // 下一帧开始时，这次移动的组合和布局已经做完，这一帧就会把它画出来
        Choreographer.getInstance().postFrameCallback { waitingForFrame = false }
        return false
    }
}

/** 卡帧后不追赶积压的位移，避免恢复绘制时突然跳过大段正文。 */
internal fun holdScrollStep(remaining: Float, viewportSize: Int, elapsedNanos: Long): Float =
    minOf(
        remaining.coerceAtLeast(0f),
        viewportSize * HOLD_SCROLL_VIEWPORTS_PER_SECOND *
            elapsedNanos.coerceIn(0L, 32_000_000L) / 1_000_000_000f
    )

/** 单次动画和长按共用一个任务；松开长按、失焦或离开列表时取消。 */
internal class TallItemScroller(
    private val listState: LazyListState,
    private val scope: CoroutineScope
) {
    private var job: Job? = null
    private var holdUntilNanos = 0L
    private var activeKey: Any? = null
    private var holding = false
    private var holdDown = true

    fun step(key: Any, distance: Float) {
        stop()
        activeKey = key
        job = scope.launch { listState.animateScrollBy(distance) }
    }

    fun hold(key: Any, down: Boolean) {
        // 同方向重复只续期，不重启动画。换方向/评论时取消旧任务。
        if (holding && activeKey == key && holdDown == down && job?.isActive == true) {
            holdUntilNanos = System.nanoTime() + HOLD_TIMEOUT_NANOS
            return
        }
        stop()
        activeKey = key
        holding = true
        holdDown = down
        holdUntilNanos = System.nanoTime() + HOLD_TIMEOUT_NANOS
        job = scope.launch {
            // 整段滚动持有同一个 scroll 会话，避免每帧与焦点滚动动画争用。
            listState.scroll {
                var lastFrame = withFrameNanos { it }
                while (true) {
                    val frame = withFrameNanos { it }
                    // 帧时钟只用于算帧间距，超时统一使用单调系统时钟。
                    if (System.nanoTime() >= holdUntilNanos) break
                    val layoutInfo = listState.layoutInfo
                    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: break
                    val remaining = remainingDistance(layoutInfo, item, down)
                    if (remaining <= 1f) break
                    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val step = holdScrollStep(remaining, viewportSize, frame - lastFrame)
                    lastFrame = frame
                    if (step <= 0f) continue
                    val consumed = scrollBy(if (down) step else -step)
                    if (kotlin.math.abs(consumed) < 0.5f) break
                }
            }
        }
    }

    fun release(down: Boolean) {
        // 短按松手仍让本次动画完成；旧方向的抬起不能中断新方向。
        if (holding && holdDown == down) stop()
    }

    fun stop(key: Any? = null) {
        if (key != null && key != activeKey) return
        job?.cancel()
        job = null
        activeKey = null
        holding = false
    }
}

/** 评论在 [down] 方向上还有多少没露出来（px） */
private fun remainingDistance(layoutInfo: LazyListLayoutInfo, item: LazyListItemInfo, down: Boolean): Float =
    if (down) {
        (item.offset + item.size - layoutInfo.viewportEndOffset).toFloat()
    } else {
        (layoutInfo.viewportStartOffset - item.offset).toFloat()
    }

/**
 * 比列表还高的评论（楼主的完整正文、超长回复）按上下键时先滚动自己，露完了才把焦点交给相邻的评论。
 * 不然焦点一跳过去，中间那段就再也看不到了
 */
internal fun Modifier.scrollThroughTallItem(
    state: CommentListState,
    key: Any
): Modifier = onFocusChanged {
    if (!it.hasFocus) state.tallItemScroller.stop(key)
}.onPreviewKeyEvent { event ->
    val down = event.key == Key.DirectionDown
    if (!down && event.key != Key.DirectionUp) {
        if (event.type == KeyEventType.KeyDown) state.tallItemScroller.stop(key)
        return@onPreviewKeyEvent false
    }
    if (event.type == KeyEventType.KeyUp) {
        state.tallItemScroller.release(down)
        return@onPreviewKeyEvent false
    }
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    // 即使新方向已经到边界，也必须先停掉旧方向。
    state.tallItemScroller.release(!down)
    val layoutInfo = state.lazyListState.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@onPreviewKeyEvent false
    // 列表的可见范围包括上下留白，评论滚进留白里也是看得见的
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    // 放得下的评论不拦，焦点照常移动
    if (item.size <= viewportSize) return@onPreviewKeyEvent false
    val remaining = remainingDistance(layoutInfo, item, down)
    if (remaining <= 1f) {
        state.tallItemScroller.stop(key)
        return@onPreviewKeyEvent false
    }
    if (event.nativeKeyEvent.repeatCount > 0) {
        state.tallItemScroller.hold(key, down)
    } else {
        val distance = minOf(remaining, viewportSize * TALL_ITEM_STEP_FRACTION)
        state.tallItemScroller.step(key, if (down) distance else -distance)
    }
    true
}
