package dev.aaa1115910.bv.component.controllers.comment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.reply.Comment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import dev.aaa1115910.biliapi.entity.reply.CommentPicture
import dev.aaa1115910.biliapi.entity.reply.CommentSort
import dev.aaa1115910.bv.component.GradientLoadingIndicator
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.viewmodel.player.CommentLoadState
import dev.aaa1115910.bv.viewmodel.player.CommentPager
import dev.aaa1115910.bv.viewmodel.player.VideoCommentViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val PanelWidth = 420.dp

private val PanelBrush = Brush.verticalGradient(
    listOf(Color(0xFF161B28), Color(0xFF0D111A))
)

// 抽屉左侧压暗一点，和视频画面拉开层次
private val ScrimBrush = Brush.horizontalGradient(
    0f to Color.Transparent,
    0.3f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.55f)
)

// 离列表底部还剩几条时开始加载下一页
private const val PRELOAD_DISTANCE = 4

/**
 * 播放器右侧的评论抽屉
 *
 * 返回键由 VideoPlayerController 统一处理：在楼中楼里先回到评论列表，再按一次才关掉抽屉
 */
@Composable
fun VideoCommentController(
    modifier: Modifier = Modifier,
    show: Boolean,
    aid: Long,
    viewModel: VideoCommentViewModel
) {
    // 放在 AnimatedVisibility 外面：抽屉收起时里面的内容会被移出组合，列表位置要留到下次打开
    val commentListState = rememberCommentListState()
    val focusMemory = remember { CommentFocusMemory() }

    LaunchedEffect(show, aid) {
        commentListState.tallItemScroller.stop()
        if (!show) return@LaunchedEffect
        if (viewModel.aid != aid) {
            focusMemory.lastFocusedRpid = null
            commentListState.lazyListState.scrollToItem(0)
        }
        viewModel.bind(aid)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = show,
            enter = fadeIn(tween(BVMotion.DurationMedium)),
            exit = fadeOut(tween(BVMotion.DurationMedium))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ScrimBrush)
            )
        }
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.CenterEnd),
            visible = show,
            enter = slideInHorizontally(
                tween(320, easing = BVMotion.EmphasizedDecelerateEasing)
            ) { it } + fadeIn(tween(BVMotion.DurationMedium)),
            exit = slideOutHorizontally(
                tween(BVMotion.DurationMedium, easing = BVMotion.StandardEasing)
            ) { it } + fadeOut(tween(BVMotion.DurationMedium))
        ) {
            CommentPanel(
                active = show,
                viewModel = viewModel,
                commentListState = commentListState,
                focusMemory = focusMemory
            )
        }
    }
}

/**
 * 一级评论列表里每条评论的 FocusRequester，以及最后获得焦点的是哪条。
 * 从楼中楼返回、重新打开抽屉时，焦点回到离开时的那条评论上
 */
private class CommentFocusMemory {
    var lastFocusedRpid: Long? = null
    val requesters = mutableMapOf<Long, FocusRequester>()
}

private fun FocusRequester.tryRequestFocus(): Boolean =
    runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

@Composable
private fun CommentPanel(
    active: Boolean,
    viewModel: VideoCommentViewModel,
    commentListState: CommentListState,
    focusMemory: CommentFocusMemory
) {
    val scope = rememberCoroutineScope()
    val replyRoot = viewModel.replyRoot
    val sortRequester = remember { FocusRequester() }
    val replyRootRequester = remember { FocusRequester() }
    // 打开时评论还没加载出来，焦点先放在排序按钮上，等第一页回来再挪到第一条评论。
    // 期间用户自己按过键就不再挪，免得抢焦点。只看按下：打开抽屉那一下确认键的抬起也会传进来
    val pendingFocus = remember { PendingFocus() }
    val keyRepeatGate = remember { KeyRepeatGate() }
    var pictureViewer by remember { mutableStateOf<PictureViewerRequest?>(null) }
    val openPicture: (List<CommentPicture>, Int) -> Unit = { pictures, index ->
        pictureViewer = PictureViewerRequest(pictures, index)
    }

    fun focusCommentList(): Boolean {
        val target = focusMemory.lastFocusedRpid?.let { focusMemory.requesters[it] }
            ?: commentListState.lazyListState.layoutInfo.visibleItemsInfo
                .firstNotNullOfOrNull { focusMemory.requesters[it.key as? Long] }
        return target?.tryRequestFocus() == true
    }

    // 刚打开，以及在评论列表和楼中楼之间切换时，把焦点放到对应的列表上
    LaunchedEffect(replyRoot?.rpid) {
        withFrameNanos { }
        if (replyRoot != null) {
            replyRootRequester.tryRequestFocus()
        } else if (!focusCommentList()) {
            sortRequester.tryRequestFocus()
            pendingFocus.value = true
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.comments.items.isNotEmpty() }
            .filter { it }
            .collect {
                if (!pendingFocus.value || viewModel.replyRoot != null) return@collect
                withFrameNanos { }
                pendingFocus.value = !focusCommentList()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(PanelWidth)
            .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            .background(PanelBrush)
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) pendingFocus.value = false
                keyRepeatGate.shouldDrop(it)
            }
            // 焦点不能被方向键带出抽屉，否则会落到后面看不见的控制层上
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection != FocusDirection.Enter &&
                        requestedFocusDirection != FocusDirection.Exit
                    ) cancelFocusChange()
                }
            }
            .focusGroup()
    ) {
        if (replyRoot == null) {
            CommentListHeader(
                totalCount = viewModel.comments.totalCount,
                sort = viewModel.sort,
                sortRequester = sortRequester,
                onSortChange = { sort ->
                    focusMemory.lastFocusedRpid = null
                    viewModel.changeSort(sort)
                    scope.launch { commentListState.lazyListState.scrollToItem(0) }
                }
            )
            PanelDivider()
            CommentList(
                active = active && pictureViewer == null,
                pager = viewModel.comments,
                listState = commentListState,
                focusMemory = focusMemory,
                onOpenReplies = viewModel::openReplies,
                onOpenPicture = openPicture
            )
        } else {
            ReplyListHeader(
                totalCount = viewModel.replies.totalCount.takeIf { it > 0 } ?: replyRoot.replyCount,
                onBack = viewModel::closeReplies
            )
            PanelDivider()
            ReplyList(
                active = active && pictureViewer == null,
                root = replyRoot,
                upMid = viewModel.replies.upMid.takeIf { it != 0L } ?: viewModel.comments.upMid,
                pager = viewModel.replies,
                rootRequester = replyRootRequester,
                onOpenPicture = openPicture
            )
        }
    }

    pictureViewer?.let { request ->
        CommentPictureViewer(
            pictures = request.pictures,
            initialIndex = request.index,
            onDismiss = { pictureViewer = null }
        )
    }
}

private data class PictureViewerRequest(val pictures: List<CommentPicture>, val index: Int)

private class PendingFocus {
    var value = false
}

@Composable
private fun CommentListHeader(
    totalCount: Int,
    sort: CommentSort,
    sortRequester: FocusRequester,
    onSortChange: (CommentSort) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 18.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "评论",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BVColor.TextPrimary
        )
        if (totalCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = totalCount.toWanString(),
                fontSize = 13.sp,
                color = BVColor.TextTertiary
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(CommentSort.Hot to "热门", CommentSort.Time to "最新").forEach { (option, label) ->
                SortChip(
                    modifier = if (option == sort) Modifier.focusRequester(sortRequester) else Modifier,
                    label = label,
                    selected = option == sort,
                    onClick = { onSortChange(option) }
                )
            }
        }
    }
}

@Composable
private fun SortChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) BVColor.Pink.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = if (selected) BVColor.PinkBright else BVColor.TextTertiary,
            focusedContainerColor = BVColor.FocusFill,
            focusedContentColor = BVColor.OnFocusFill,
            pressedContainerColor = BVColor.FocusFill.copy(alpha = 0.8f),
            pressedContentColor = BVColor.OnFocusFill
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ReplyListHeader(
    totalCount: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BVColor.TextSecondary,
                focusedContainerColor = BVColor.FocusFill,
                focusedContentColor = BVColor.OnFocusFill
            ),
            border = ClickableSurfaceDefaults.border(border = Border.None),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回评论列表"
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "评论详情",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = BVColor.TextPrimary
            )
            Text(
                text = if (totalCount > 0) "相关回复共 ${totalCount.toWanString()} 条" else "按返回键回到评论列表",
                fontSize = 11.sp,
                color = BVColor.TextTertiary
            )
        }
    }
}

@Composable
private fun PanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

@Composable
private fun CommentList(
    active: Boolean,
    pager: CommentPager,
    listState: CommentListState,
    focusMemory: CommentFocusMemory,
    onOpenReplies: (Comment) -> Unit,
    onOpenPicture: (List<CommentPicture>, Int) -> Unit
) {
    AutoLoadMore(pager = pager, listState = listState.lazyListState)
    CommentLazyColumn(state = listState, spacing = 4.dp, enabled = active) {
        if (pager.items.isEmpty()) {
            item(key = "placeholder") {
                when (pager.state) {
                    CommentLoadState.Error -> ErrorTip(
                        modifier = Modifier.padding(top = 80.dp),
                        onRetry = pager::retry
                    )

                    CommentLoadState.End -> EmptyTip(text = "这里还没有评论")
                    else -> CommentSkeleton()
                }
            }
            return@CommentLazyColumn
        }
        items(items = pager.items, key = { it.rpid }) { comment ->
            val requester = remember { FocusRequester() }
            DisposableEffect(comment.rpid) {
                focusMemory.requesters[comment.rpid] = requester
                onDispose {
                    if (focusMemory.requesters[comment.rpid] === requester) {
                        focusMemory.requesters.remove(comment.rpid)
                    }
                }
            }
            CommentCard(
                modifier = Modifier
                    .focusRequester(requester)
                    .onFocusChanged { if (it.hasFocus) focusMemory.lastFocusedRpid = comment.rpid }
                    .scrollThroughTallItem(listState, comment.rpid),
                comment = comment,
                upMid = pager.upMid,
                onClick = { onOpenReplies(comment) },
                onOpenPicture = onOpenPicture
            )
        }
        item(key = "footer") {
            ListFooter(state = pager.state, onRetry = pager::retry)
        }
    }
}

@Composable
private fun ReplyList(
    active: Boolean,
    root: Comment,
    upMid: Long,
    pager: CommentPager,
    rootRequester: FocusRequester,
    onOpenPicture: (List<CommentPicture>, Int) -> Unit
) {
    // 每次打开楼中楼都是新的列表，从顶部开始
    val listState = rememberCommentListState()
    AutoLoadMore(pager = pager, listState = listState.lazyListState)
    CommentLazyColumn(state = listState, spacing = 2.dp, enabled = active) {
        item(key = "root") {
            CommentCard(
                modifier = Modifier
                    .focusRequester(rootRequester)
                    .scrollThroughTallItem(listState, "root"),
                comment = root,
                upMid = upMid,
                expanded = true,
                onClick = {},
                onOpenPicture = onOpenPicture
            )
        }
        item(key = "section") {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 12.dp)
                        .clip(CircleShape)
                        .background(BVColor.Pink)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "全部回复",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = BVColor.TextSecondary
                )
            }
        }
        if (pager.items.isEmpty()) {
            item(key = "placeholder") {
                when (pager.state) {
                    CommentLoadState.Error -> ErrorTip(
                        modifier = Modifier.padding(top = 24.dp),
                        onRetry = pager::retry
                    )

                    CommentLoadState.End -> EmptyTip(text = "还没有人回复", compact = true)
                    else -> LoadingRow()
                }
            }
            return@CommentLazyColumn
        }
        items(items = pager.items, key = { it.rpid }) { reply ->
            ReplyCard(
                modifier = Modifier.scrollThroughTallItem(listState, reply.rpid),
                reply = reply,
                upMid = upMid,
                onOpenPicture = onOpenPicture
            )
        }
        item(key = "footer") {
            ListFooter(state = pager.state, onRetry = pager::retry)
        }
    }
}

/** 快滚到底时加载下一页；加载完如果还在底部附近，会接着加载 */
@Composable
private fun AutoLoadMore(pager: CommentPager, listState: LazyListState) {
    LaunchedEffect(pager, listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            pager.state == CommentLoadState.Idle &&
                pager.items.isNotEmpty() &&
                lastVisible >= layoutInfo.totalItemsCount - PRELOAD_DISTANCE
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { pager.loadMore() }
    }
}

@Composable
private fun ListFooter(state: CommentLoadState, onRetry: () -> Unit) {
    when (state) {
        CommentLoadState.Loading -> LoadingRow()
        CommentLoadState.Error -> ErrorTip(modifier = Modifier.padding(vertical = 8.dp), onRetry = onRetry)
        CommentLoadState.End -> Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            text = "— 没有更多了 —",
            fontSize = 11.sp,
            color = BVColor.TextTertiary,
            textAlign = TextAlign.Center
        )

        CommentLoadState.Idle -> Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientLoadingIndicator(size = 18.dp, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text = "加载中…", fontSize = 12.sp, color = BVColor.TextTertiary)
    }
}

@Composable
private fun ErrorTip(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "评论加载失败", fontSize = 13.sp, color = BVColor.TextSecondary)
        Surface(
            onClick = onRetry,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = BVColor.TextPrimary,
                focusedContainerColor = BVColor.FocusFill,
                focusedContentColor = BVColor.OnFocusFill
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                text = "重试",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EmptyTip(text: String, compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 28.dp else 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!compact) {
            Icon(
                modifier = Modifier.size(40.dp),
                imageVector = Icons.AutoMirrored.Rounded.Comment,
                contentDescription = null,
                tint = BVColor.SurfaceHighlight
            )
        }
        Text(text = text, fontSize = 13.sp, color = BVColor.TextTertiary)
    }
}

/** 第一页回来之前的骨架屏，明暗呼吸只改图层透明度，不触发重组 */
@Composable
private fun CommentSkeleton() {
    val transition = rememberInfiniteTransition(label = "comment skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "comment skeleton alpha"
    )
    val block = Color.White.copy(alpha = 0.08f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(4) { index ->
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(block)
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBar(width = 88.dp, height = 10.dp, color = block)
                    SkeletonBar(width = 280.dp, height = 12.dp, color = block)
                    SkeletonBar(width = if (index % 2 == 0) 220.dp else 160.dp, height = 12.dp, color = block)
                    SkeletonBar(width = 120.dp, height = 8.dp, color = block)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp, color: Color) {
    Box(
        Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}
