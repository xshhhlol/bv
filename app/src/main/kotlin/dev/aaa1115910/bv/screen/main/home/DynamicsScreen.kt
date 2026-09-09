package dev.aaa1115910.bv.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.activities.video.UpInfoActivity
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.VideoCardSkeleton
import dev.aaa1115910.bv.component.videoGridColumns
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.screen.user.EmptyTip
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun DynamicsScreen(
    modifier: Modifier = Modifier,
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        toViewViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> {
                    event.message.toast(context)
                }
            }
        }
    }

    if (!dynamicViewModel.isLogin) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "请先登录")
        }
        return
    }

    LaunchedEffect(Unit) {
        dynamicViewModel.loadFollowedUps()
    }

    val selectedUp = dynamicViewModel.selectedUp
    val feed = dynamicViewModel.currentUpFeed
    // 选中「全部」时看动态流，选中某个 UP 时看这个 UP 的投稿
    val videos = feed?.videos ?: dynamicViewModel.dynamicList
    val loading = feed?.loading ?: dynamicViewModel.loading
    val hasMore = feed?.hasMore ?: dynamicViewModel.hasMore
    val attempted = dynamicViewModel.currentTabAttempted

    // 每个栏目各自记着滚到哪了。状态放在 ViewModel 里，这样切到推荐/热门再切回来也还在原位
    val gridState = dynamicViewModel.gridStateOf(selectedUp?.mid)
    val gridFocusRequester = remember { FocusRequester() }

    val onClickVideo: (VideoCardData) -> Unit = { video ->
        VideoInfoActivity.actionStart(
            context = context,
            aid = video.avid,
            epid = video.epId,
            proxyArea = ProxyArea.checkProxyArea(video.title)
        )
    }

    // 监听可见区最后一个 item 的 index，距离尾部 20 个就翻页
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && videos.isNotEmpty() && index >= videos.size - 20
            }
            .collect {
                scope.launch(Dispatchers.IO) {
                    dynamicViewModel.loadMore()
                }
            }
    }

    Row(modifier = modifier.fillMaxSize()) {
        DynamicUpRail(
            ups = dynamicViewModel.followedUps,
            loadingUps = dynamicViewModel.loadingUps,
            selectedMid = selectedUp?.mid,
            onSelectAll = { dynamicViewModel.selectAll() },
            onSelectUp = { dynamicViewModel.selectUp(it) },
            onEnterContent = { gridFocusRequester.requestFocus(scope) }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            FeedHeader(
                title = selectedUp?.name ?: "全部动态",
                count = videos.size
            )

            TvLazyVerticalGrid(
                modifier = Modifier
                    .focusRequester(gridFocusRequester)
                    // 从侧栏回到内容区时优先回到上次看的那张卡片，没有记录才落到第一张
                    .focusRestorer(),
                state = gridState,
                // 侧栏常驻占掉了左边一条，这里相应少排一列，卡片不至于被压扁
                columns = GridCells.Fixed((videoGridColumns() - 1).coerceAtLeast(2)),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 首屏还没数据时先铺骨架，比直接空一片再「啪」地填满要稳当
                if (videos.isEmpty() && !attempted) {
                    items(count = 8) { VideoCardSkeleton() }
                }

                itemsIndexed(
                    items = videos,
                    key = { index, _ -> index }
                ) { _, item ->
                    SmallVideoCard(
                        data = item,
                        onClick = { onClickVideo(item) },
                        onAddWatchLater = {
                            toViewViewModel.addToView(item.avid)
                        },
                        onGoToDetailPage = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                fromController = true,
                                aid = item.avid,
                                epid = item.epId,
                            )
                        },
                        onGoToUpPage = item.upMid?.let { mid ->
                            { UpInfoActivity.actionStart(context, mid, item.upName) }
                        }
                    )
                }

                if (videos.isEmpty() && attempted && !loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyTip(
                            text = if (selectedUp != null) "这位 UP 主还没有投稿" else "还没有动态"
                        )
                    }
                }

                if (loading && videos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingTip()
                        }
                    }
                }

                if (!hasMore && videos.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            text = "没有更多了捏",
                            color = BVColor.TextTertiary
                        )
                    }
                }
            }
        }
    }
}

/** 内容区顶上的一行小标题，收起侧栏时用来提示右边是谁的视频 */
@Composable
private fun FeedHeader(
    modifier: Modifier = Modifier,
    title: String,
    count: Int
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = BVColor.TextPrimary
        )
        if (count > 0) {
            Text(
                text = "已加载 $count 个",
                style = MaterialTheme.typography.bodySmall,
                color = BVColor.TextTertiary
            )
        }
    }
}
