package dev.aaa1115910.bv.screen.main.pgc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.pgc.index.Area
import dev.aaa1115910.biliapi.entity.pgc.index.Style
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonType
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.pgc.PgcIndexActivity
import dev.aaa1115910.bv.activities.pgc.PgcRankActivity
import dev.aaa1115910.bv.activities.user.FollowingSeasonActivity
import dev.aaa1115910.bv.activities.video.SeasonInfoActivity
import dev.aaa1115910.bv.component.videocard.SeasonCard
import dev.aaa1115910.bv.entity.carddata.SeasonCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.tv.screens.main.pgc.PgcScaffold
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.focusHighlight
import dev.aaa1115910.bv.util.getDisplayName
import dev.aaa1115910.bv.util.resizedImageUrl
import dev.aaa1115910.bv.viewmodel.pgc.MovieShelf
import dev.aaa1115910.bv.viewmodel.pgc.PgcMovieViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MovieContent(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    pgcViewModel: PgcMovieViewModel = koinViewModel()
) {
    val context = LocalContext.current

    val onOpenIndex: () -> Unit = {
        PgcIndexActivity.actionStart(context = context, pgcType = PgcType.Movie)
    }
    val onOpenRank: () -> Unit = {
        PgcRankActivity.actionStart(context = context, pgcType = PgcType.Movie)
    }
    val onOpenFollowing: () -> Unit = {
        FollowingSeasonActivity.actionStart(context = context, type = FollowingSeasonType.Cinema)
    }
    val onOpenShelf: (MovieShelf) -> Unit = { shelf ->
        PgcIndexActivity.actionStart(
            context = context,
            pgcType = PgcType.Movie,
            indexOrder = shelf.indexOrder,
            seasonStatus = shelf.seasonStatus
        )
    }
    val onOpenSeason: (seasonId: Int, title: String) -> Unit = { seasonId, title ->
        SeasonInfoActivity.actionStart(
            context = context,
            seasonId = seasonId,
            proxyArea = ProxyArea.checkProxyArea(title)
        )
    }

    LaunchedEffect(Unit) {
        pgcViewModel.loadShelvesIfNeeded()
    }

    PgcScaffold(
        lazyListState = lazyListState,
        pgcViewModel = pgcViewModel,
        pgcType = PgcType.Movie,
        featureButtons = {
            MovieQuickEntries(
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                onOpenRank = onOpenRank,
                onOpenFollowing = onOpenFollowing,
                onOpenIndex = onOpenIndex,
                onOpenFree = { onOpenShelf(MovieShelf.Free) }
            )
        },
        extraContent = {
            item {
                MovieCategories(
                    modifier = Modifier.padding(bottom = 8.dp),
                    onOpenStyle = { style ->
                        PgcIndexActivity.actionStart(
                            context = context,
                            pgcType = PgcType.Movie,
                            style = style
                        )
                    },
                    onOpenArea = { area ->
                        PgcIndexActivity.actionStart(
                            context = context,
                            pgcType = PgcType.Movie,
                            area = area
                        )
                    }
                )
            }
            item {
                PgcShelf(
                    title = stringResource(R.string.movie_home_shelf_rank),
                    subtitle = stringResource(R.string.movie_home_shelf_rank_subtitle),
                    items = pgcViewModel.rankItems,
                    loading = pgcViewModel.rankLoading,
                    moreText = stringResource(R.string.movie_home_shelf_rank_more),
                    onMore = onOpenRank
                ) { rankItem ->
                    SeasonCard(
                        coverHeight = PgcShelfCoverHeight,
                        data = SeasonCardData(
                            seasonId = rankItem.seasonId,
                            title = rankItem.title,
                            subTitle = rankItem.playCount
                                ?.let { stringResource(R.string.pgc_rank_play_count, it) }
                                ?: rankItem.releaseInfo,
                            cover = rankItem.cover.resizedImageUrl(ImageSize.SeasonCoverThumbnail),
                            rating = rankItem.rating
                        ),
                        onClick = { onOpenSeason(rankItem.seasonId, rankItem.title) },
                        coverOverlay = {
                            PgcRankBadge(
                                modifier = Modifier.align(Alignment.TopStart),
                                rank = rankItem.rank
                            )
                        }
                    )
                }
            }
            MovieShelf.entries.forEach { shelf ->
                item {
                    PgcShelf(
                        title = stringResource(shelf.titleRes),
                        subtitle = stringResource(shelf.subtitleRes),
                        items = pgcViewModel.shelfItems.getValue(shelf),
                        loading = pgcViewModel.shelfLoading[shelf] ?: false,
                        moreText = stringResource(R.string.movie_home_shelf_more),
                        onMore = { onOpenShelf(shelf) }
                    ) { pgcItem ->
                        SeasonCard(
                            coverHeight = PgcShelfCoverHeight,
                            data = SeasonCardData.fromPgcItem(pgcItem).let { data ->
                                // 索引接口里没有评分的影片 score 为空，不显示评分遮罩
                                data.copy(rating = data.rating?.takeIf { it.isNotBlank() && it != "0" })
                            },
                            onClick = { onOpenSeason(pgcItem.seasonId, pgcItem.title) }
                        )
                    }
                }
            }
        },
        feedTitle = {
            PgcShelfHeader(
                modifier = Modifier.padding(top = 12.dp),
                title = stringResource(R.string.movie_home_shelf_feed)
            )
        },
        feedHorizontalPadding = PgcShelfHorizontalPadding
    )
}

private val MovieShelf.titleRes: Int
    get() = when (this) {
        MovieShelf.Free -> R.string.movie_home_shelf_free
        MovieShelf.RecentlyUpdated -> R.string.movie_home_shelf_updated
    }

private val MovieShelf.subtitleRes: Int
    get() = when (this) {
        MovieShelf.Free -> R.string.movie_home_shelf_free_subtitle
        MovieShelf.RecentlyUpdated -> R.string.movie_home_shelf_updated_subtitle
    }

private data class MovieQuickEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val onClick: () -> Unit
)

// 每个快捷入口一种渐变色，电视上隔着几米也能一眼分清
private val RankEntryColors = listOf(Color(0xFFFFC53D), Color(0xFFFF7A1A))
private val FollowingEntryColors = listOf(BVColor.PinkBright, BVColor.PinkDeep)
private val IndexEntryColors = listOf(BVColor.Cyan, BVColor.Violet)
private val FreeEntryColors = listOf(Color(0xFF5BE3A6), Color(0xFF12A386))

/**
 * 电影页顶部的快捷入口
 *
 * 宽度和左右留白与 [PgcScaffold] 里的轮播图一致，两者左右边缘对齐
 */
@Composable
private fun MovieQuickEntries(
    modifier: Modifier = Modifier,
    onOpenRank: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenFree: () -> Unit
) {
    val entries = listOf(
        MovieQuickEntry(
            title = stringResource(R.string.movie_home_button_rank),
            subtitle = stringResource(R.string.movie_home_entry_rank_subtitle),
            icon = Icons.Rounded.Leaderboard,
            colors = RankEntryColors,
            onClick = onOpenRank
        ),
        MovieQuickEntry(
            title = stringResource(R.string.anime_home_button_following),
            subtitle = stringResource(R.string.movie_home_entry_following_subtitle),
            icon = Icons.Rounded.Favorite,
            colors = FollowingEntryColors,
            onClick = onOpenFollowing
        ),
        MovieQuickEntry(
            title = stringResource(R.string.anime_home_button_index),
            subtitle = stringResource(R.string.movie_home_entry_index_subtitle),
            icon = Icons.AutoMirrored.Rounded.List,
            colors = IndexEntryColors,
            onClick = onOpenIndex
        ),
        MovieQuickEntry(
            title = stringResource(R.string.movie_home_button_free),
            subtitle = stringResource(R.string.movie_home_entry_free_subtitle),
            icon = Icons.Rounded.ConfirmationNumber,
            colors = FreeEntryColors,
            onClick = onOpenFree
        )
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .width(880.dp)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            entries.forEach { entry ->
                MovieQuickEntryTile(
                    modifier = Modifier.weight(1f),
                    entry = entry
                )
            }
        }
    }
}

@Composable
private fun MovieQuickEntryTile(
    modifier: Modifier = Modifier,
    entry: MovieQuickEntry
) {
    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier = modifier
            // 焦点效果和卡片一样走 focusHighlight（渐变描边 + 光晕 + 放大），不再整块刷白
            .focusHighlight(shape = shape, focusedScale = 1.05f)
            .height(64.dp),
        onClick = entry.onClick,
        // 底色必须不透明：focusHighlight 的光晕是画在内容底下的投影，
        // 半透明底会把投影透出来，获焦时整块发青、文字后面出现色块
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            pressedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = shape
            ),
            focusedBorder = Border.None
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = Brush.linearGradient(entry.colors),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = entry.icon,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MovieCategories(
    modifier: Modifier = Modifier,
    onOpenStyle: (Style) -> Unit,
    onOpenArea: (Area) -> Unit
) {
    val context = LocalContext.current
    // 短片和其他题材不是一类，放到最后
    val styles = remember {
        Style.getList(PgcType.Movie).filter { it != Style.All }.sortedBy { it == Style.ShortFilm }
    }
    val areas = remember {
        Area.getList(PgcType.Movie).filter { it != Area.All }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PgcCategoryRow(
            title = stringResource(R.string.movie_home_category_style),
            categories = styles,
            label = { it.getDisplayName(context) },
            onClick = onOpenStyle
        )
        PgcCategoryRow(
            title = stringResource(R.string.movie_home_category_area),
            categories = areas,
            label = { it.getDisplayName(context) },
            onClick = onOpenArea
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MovieQuickEntriesPreview() {
    BVTheme {
        Column {
            MovieQuickEntries(
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                onOpenRank = {},
                onOpenFollowing = {},
                onOpenIndex = {},
                onOpenFree = {}
            )
            MovieCategories(
                onOpenStyle = {},
                onOpenArea = {}
            )
        }
    }
}
