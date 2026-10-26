package dev.aaa1115910.bv.screen.main.pgc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.pgc.PgcRankDay
import dev.aaa1115910.biliapi.entity.pgc.PgcRankItem
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.video.SeasonInfoActivity
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.videocard.SeasonCard
import dev.aaa1115910.bv.entity.carddata.SeasonCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.getDisplayName
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.resizedImageUrl
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.viewmodel.pgc.PgcRankViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun PgcRankScreen(
    modifier: Modifier = Modifier,
    pgcType: PgcType,
    rankViewModel: PgcRankViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstItemFocusRequester = remember { FocusRequester() }

    val rankItems = rankViewModel.rankItems
    val rankDay = rankViewModel.rankDay
    // 切换统计周期后从头开始看
    val gridState = remember(rankDay) { LazyGridState() }

    var focusedIndex by remember { mutableIntStateOf(0) }
    // 快速移动焦点时不必每一格都换大图，停下来之后再切换
    var heroIndex by remember { mutableIntStateOf(0) }
    var initialFocusRequested by remember { mutableStateOf(false) }
    val heroItem = rankItems.getOrNull(heroIndex)

    LaunchedEffect(Unit) {
        if (rankItems.isEmpty() && !rankViewModel.loading) {
            rankViewModel.load(pgcType = pgcType)
        }
    }

    LaunchedEffect(focusedIndex) {
        delay(150)
        heroIndex = focusedIndex
    }

    // 首次加载完成后把焦点放到第一名上，之后切换周期时焦点留在周期按钮上
    LaunchedEffect(rankItems.isNotEmpty()) {
        if (rankItems.isNotEmpty() && !initialFocusRequested) {
            initialFocusRequested = true
            firstItemFocusRequester.requestFocus(scope)
        }
    }

    val onRankDayChange: (PgcRankDay) -> Unit = { day ->
        if (day != rankViewModel.rankDay) {
            focusedIndex = 0
            heroIndex = 0
            rankViewModel.load(rankDay = day)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        PgcRankBackdrop(
            modifier = Modifier.align(Alignment.TopEnd),
            imageUrl = heroItem?.horizontalCover ?: heroItem?.cover
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PgcRankTopBar(
                modifier = Modifier.padding(start = 48.dp, top = 24.dp, end = 48.dp),
                title = stringResource(R.string.pgc_rank_title, pgcType.getDisplayName(context)),
                note = rankViewModel.note,
                rankDay = rankDay,
                onRankDayChange = onRankDayChange
            )

            PgcRankHero(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 48.dp),
                item = heroItem
            )

            when {
                rankItems.isNotEmpty() -> TvLazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    columns = GridCells.Fixed(7),
                    contentPadding = PaddingValues(start = 48.dp, top = 12.dp, end = 48.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(items = rankItems) { index, rankItem ->
                        SeasonCard(
                            modifier = if (index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                            data = SeasonCardData(
                                seasonId = rankItem.seasonId,
                                title = rankItem.title,
                                subTitle = rankItem.playCount
                                    ?.let { stringResource(R.string.pgc_rank_play_count, it) }
                                    ?: rankItem.releaseInfo,
                                cover = rankItem.cover.resizedImageUrl(ImageSize.SeasonCoverThumbnail),
                                rating = rankItem.rating
                            ),
                            onFocus = { focusedIndex = index },
                            onClick = {
                                SeasonInfoActivity.actionStart(
                                    context = context,
                                    seasonId = rankItem.seasonId,
                                    proxyArea = ProxyArea.checkProxyArea(rankItem.title)
                                )
                            },
                            coverOverlay = {
                                PgcRankBadge(
                                    modifier = Modifier.align(Alignment.TopStart),
                                    rank = rankItem.rank
                                )
                            }
                        )
                    }
                }

                rankViewModel.loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingTip()
                }

                else -> PgcRankEmptyTip(
                    text = if (rankViewModel.loadFailed) {
                        stringResource(R.string.pgc_rank_load_failed)
                    } else {
                        stringResource(R.string.no_data)
                    },
                    onRetry = { rankViewModel.load() }
                )
            }
        }
    }
}

private val PgcRankDay.nameRes: Int
    get() = when (this) {
        PgcRankDay.ThreeDays -> R.string.pgc_rank_day_three_days
        PgcRankDay.Week -> R.string.pgc_rank_day_week
    }

@Composable
private fun PgcRankTopBar(
    modifier: Modifier = Modifier,
    title: String,
    note: String,
    rankDay: PgcRankDay,
    onRankDayChange: (PgcRankDay) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                fontSize = 24.sp
            )
            PgcRankDay.entries.forEach { day ->
                PgcRankDayChip(
                    selected = day == rankDay,
                    text = stringResource(day.nameRes),
                    onClick = { onRankDayChange(day) }
                )
            }
        }
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PgcRankDayChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.height(36.dp),
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

/**
 * 当前焦点影片的横版封面，左侧和底部渐隐到页面底色里，文字压在上面也看得清
 */
@Composable
private fun PgcRankBackdrop(
    modifier: Modifier = Modifier,
    imageUrl: String?
) {
    Crossfade(
        modifier = modifier
            .fillMaxWidth(0.68f)
            .aspectRatio(16f / 9f),
        targetState = imageUrl,
        animationSpec = tween(durationMillis = 500),
        label = "rank backdrop"
    ) { url ->
        if (url != null) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to Color.Black
                            ),
                            blendMode = BlendMode.DstIn
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.4f to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                model = url.resizedImageUrl(ImageSize.SeasonBackdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.6f
            )
        }
    }
}

@Composable
private fun PgcRankHero(
    modifier: Modifier = Modifier,
    item: PgcRankItem?
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        if (item == null) return@Box
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "rank hero"
        ) { heroItem ->
            Column(
                modifier = Modifier.fillMaxWidth(0.6f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "NO.${heroItem.rank}",
                        color = if (heroItem.rank <= 3) {
                            Color(0xFFFFB400)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic
                    )
                    heroItem.badge?.let { badge ->
                        PgcRankHeroBadge(
                            text = badge,
                            color = heroItem.badgeColor
                                ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                                ?: BVColor.Pink
                        )
                    }
                }
                Text(
                    text = heroItem.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    heroItem.rating?.let { rating ->
                        Text(
                            text = rating,
                            color = Color(0xFFFFB400),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    val playCount = heroItem.playCount
                        ?.let { stringResource(R.string.pgc_rank_play_count, it) }
                    val followCount = heroItem.follow.takeIf { it > 0 }
                        ?.let { stringResource(R.string.pgc_rank_follow_count, it.toInt().toWanString()) }
                    Text(
                        text = listOfNotNull(heroItem.releaseInfo, playCount, heroItem.desc, followCount)
                            .joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PgcRankHeroBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(color = color, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun PgcRankEmptyTip(
    modifier: Modifier = Modifier,
    text: String,
    onRetry: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val retryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        retryFocusRequester.requestFocus(scope)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            OutlinedButton(
                modifier = Modifier.focusRequester(retryFocusRequester),
                onClick = onRetry
            ) {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.pgc_rank_retry))
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PgcRankHeroPreview() {
    BVTheme {
        PgcRankHero(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 48.dp),
            item = PgcRankItem(
                rank = 1,
                seasonId = 211802,
                title = "挽救计划",
                cover = "",
                horizontalCover = null,
                rating = "9.8",
                badge = "大会员",
                badgeColor = "#FB7299",
                releaseInfo = "2026-03-20上映",
                playCount = "1012.3万",
                desc = "2.6万弹幕",
                follow = 127575
            )
        )
    }
}
