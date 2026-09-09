package dev.aaa1115910.bv.screen.user

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import dev.aaa1115910.bv.util.Prefs
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.user.UserCard
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.videoGridColumns
import dev.aaa1115910.bv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.ui.effect.UiEffect
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.util.toast
import dev.aaa1115910.bv.viewmodel.user.ToViewViewModel
import dev.aaa1115910.bv.viewmodel.user.UpInfoViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel

@Composable
fun UpSpaceScreen(
    modifier: Modifier = Modifier,
    upInfoViewModel: UpInfoViewModel = koinViewModel(),
    toViewViewModel: ToViewViewModel = koinViewModel()
) {
    val gridState = rememberLazyGridState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val intent = (context as Activity).intent
        if (intent.hasExtra("mid")) {
            val mid = intent.getLongExtra("mid", 0)
            val name = intent.getStringExtra("name") ?: ""
            upInfoViewModel.upMid = mid
            upInfoViewModel.upName = name
            upInfoViewModel.update()
        } else {
            context.finish()
        }
    }

    LaunchedEffect(Unit) {
        toViewViewModel.uiEvent.collect { event ->
            when (event) {
                is UiEffect.ShowToast -> {
                    event.message.toast(context)
                }
            }
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= upInfoViewModel.spaceVideos.size - 20
            }
            .collect {
                upInfoViewModel.update()
            }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Box(
                modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 8.dp, end = 48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = upInfoViewModel.upName,
                        fontSize = 24.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.load_data_count,
                                upInfoViewModel.spaceVideos.size
                            ),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        AnimatedVisibility(visible = upInfoViewModel.noMore) {
                            Text(
                                text = stringResource(R.string.load_data_no_more),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        TvLazyVerticalGrid(
            modifier = Modifier.padding(innerPadding),
            columns = GridCells.Fixed(videoGridColumns()),
            state = gridState,
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            upInfoViewModel.userCard?.let { card ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    UpInfoHeader(
                        card = card,
                        changingFollow = upInfoViewModel.changingFollow,
                        onToggleFollow = upInfoViewModel::toggleFollow
                    )
                }
            }

            if (upInfoViewModel.spaceVideos.isNotEmpty()) {
                itemsIndexed(
                    items = upInfoViewModel.spaceVideos,
                    key = { _, video -> video.avid }
                ) { _, video ->
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        SmallVideoCard(
                            data = video,
                            onClick = {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    aid = video.avid,
                                    proxyArea = ProxyArea.checkProxyArea(video.title)
                                )
                            },
                            onAddWatchLater = {
                                toViewViewModel.addToView(video.avid)
                            },
                            onGoToDetailPage = {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    fromController = true,
                                    aid = video.avid
                                )
                            },
                        )
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyTip()
                }
            }
        }
    }
}

/**
 * UP 主名片：头像 + 昵称 + 粉丝/获赞/投稿 + 详情介绍。
 *
 * 放在网格第一行而不是常驻 topBar 里，往下翻的时候能滚走，不占着首屏的位置。
 */
@Composable
private fun UpInfoHeader(
    modifier: Modifier = Modifier,
    card: UserCard,
    changingFollow: Boolean,
    onToggleFollow: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BVColor.SurfaceVariant),
            model = card.face,
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = BVColor.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.level > 0) {
                    LevelBadge(level = card.level)
                }
                if (card.officialTitle.isNotBlank()) {
                    Text(
                        text = card.officialTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = BVColor.Cyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                UpStat(label = "粉丝", value = card.follower)
                UpStat(label = "获赞", value = card.likeNum)
                UpStat(label = "投稿", value = card.archiveCount)
                UpStat(label = "关注", value = card.following)
            }

            Text(
                text = card.sign.takeIf { it.isNotBlank() } ?: "这个 UP 主还没有写简介",
                style = MaterialTheme.typography.bodyMedium,
                color = BVColor.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (card.mid != Prefs.uid) {
            Button(onClick = onToggleFollow, enabled = !changingFollow) {
                Text(
                    text = when {
                        changingFollow -> "处理中…"
                        card.isFollowing -> "取消关注"
                        else -> "关注"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** 单项统计：数字用亮色，标签压暗，一眼能看出主次 */
@Composable
private fun UpStat(
    modifier: Modifier = Modifier,
    label: String,
    value: Int
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = value.toWanString(),
            style = MaterialTheme.typography.titleLarge,
            color = BVColor.TextPrimary
        )
        Text(
            modifier = Modifier.padding(bottom = 2.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = BVColor.TextTertiary
        )
    }
}

@Composable
private fun LevelBadge(
    modifier: Modifier = Modifier,
    level: Int
) {
    Text(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(BVColor.Pink.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        text = "LV$level",
        style = MaterialTheme.typography.labelMedium,
        color = BVColor.PinkBright
    )
}
