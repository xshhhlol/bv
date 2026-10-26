package dev.aaa1115910.bv.screen.main.pgc

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.shimmer
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.focusHighlight

/** 片单里卡片封面的高度，和猜你喜欢里的卡片保持一致 */
val PgcShelfCoverHeight = 180.dp

/** 电影页各区块统一的左右留白，和顶部轮播图的边缘对齐 */
val PgcShelfHorizontalPadding = 32.dp

/** 区块标题前的小竖条，配色和左侧导航的选中指示条一致 */
private val ShelfAccentBrush = Brush.verticalGradient(
    colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan)
)

@Composable
fun PgcShelfHeader(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PgcShelfHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .background(brush = ShelfAccentBrush, shape = RoundedCornerShape(2.dp))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Text(
                modifier = Modifier.padding(start = 2.dp),
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 横向的一栏片单：标题 + 卡片 + 末尾的「查看全部」
 *
 * 加载中铺一排骨架占位，加载完仍然没有数据（比如请求失败）时整栏不显示
 */
@Composable
fun <T> PgcShelf(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    items: List<T>,
    loading: Boolean,
    moreText: String,
    onMore: () -> Unit,
    itemContent: @Composable (item: T) -> Unit
) {
    if (!loading && items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PgcShelfHeader(
            title = title,
            subtitle = subtitle
        )
        if (loading) {
            PgcShelfSkeletonRow()
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = PgcShelfHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(items = items) { item ->
                    itemContent(item)
                }
                item {
                    PgcShelfMoreCard(
                        // 已经是最后一个，不再往右移动焦点
                        modifier = Modifier.onPreviewKeyEvent { it.key == Key.DirectionRight },
                        text = moreText,
                        onClick = onMore
                    )
                }
            }
        }
    }
}

@Composable
private fun PgcShelfSkeletonRow(
    coverHeight: Dp = PgcShelfCoverHeight
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 超出屏幕的部分直接裁掉，和真实卡片排满一行时的样子一致
            .wrapContentWidth(align = Alignment.Start, unbounded = true)
            .padding(horizontal = PgcShelfHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        repeat(7) {
            Column(
                modifier = Modifier.width(coverHeight * 0.75f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(coverHeight)
                        .shimmer(MaterialTheme.shapes.large)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(0.8f)
                        .height(14.dp)
                        .shimmer(RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth(0.5f)
                        .height(11.dp)
                        .shimmer(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

/**
 * 片单末尾的「查看全部」，尺寸和焦点效果都和 [dev.aaa1115910.bv.component.videocard.SeasonCard] 的封面对齐
 */
@Composable
fun PgcShelfMoreCard(
    modifier: Modifier = Modifier,
    coverHeight: Dp = PgcShelfCoverHeight,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .focusHighlight(shape = MaterialTheme.shapes.large, focusedScale = 1.05f)
            .height(coverHeight)
            .aspectRatio(0.75f),
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            pressedContentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.large),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border.None)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

/**
 * 排行榜卡片左上角的名次，前三名用金、银、铜配色
 */
@Composable
fun PgcRankBadge(
    modifier: Modifier = Modifier,
    rank: Int
) {
    val (badgeBrush, textColor) = when (rank) {
        1 -> Brush.linearGradient(listOf(Color(0xFFFFD66B), Color(0xFFFF9500))) to Color(0xFF2B1700)
        2 -> Brush.linearGradient(listOf(Color(0xFFEEF2F7), Color(0xFFA3AEC2))) to Color(0xFF1B2230)
        3 -> Brush.linearGradient(listOf(Color(0xFFF6C08F), Color(0xFFC0773F))) to Color(0xFF2A1405)
        else -> SolidColor(Color.Black.copy(alpha = 0.6f)) to Color.White
    }
    Box(
        modifier = modifier
            .widthIn(min = 36.dp)
            .background(brush = badgeBrush, shape = RoundedCornerShape(bottomEnd = 12.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.toString(),
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic
        )
    }
}

/**
 * 一行可点击的分类，例如电影的类型、地区
 */
@Composable
fun <T> PgcCategoryRow(
    modifier: Modifier = Modifier,
    title: String,
    categories: List<T>,
    label: (T) -> String,
    onClick: (T) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.padding(start = PgcShelfHorizontalPadding),
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(items = categories) { index, category ->
                PgcCategoryChip(
                    modifier = if (index == categories.lastIndex) {
                        Modifier.onPreviewKeyEvent { it.key == Key.DirectionRight }
                    } else {
                        Modifier
                    },
                    text = label(category),
                    onClick = { onClick(category) }
                )
            }
        }
    }
}

@Composable
private fun PgcCategoryChip(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(36.dp),
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = CircleShape
            ),
            focusedBorder = Border.None
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PgcShelfPreview() {
    BVTheme {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PgcCategoryRow(
                title = "类型",
                categories = listOf("剧情", "喜剧", "爱情", "动作", "恐怖", "科幻"),
                label = { it },
                onClick = {}
            )
            PgcShelfHeader(
                title = "热播榜",
                subtitle = "近三日综合热度，每小时更新"
            )
            Row(
                modifier = Modifier.padding(horizontal = PgcShelfHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                listOf(1, 2, 3, 12).forEach { rank ->
                    Box(
                        modifier = Modifier
                            .height(PgcShelfCoverHeight)
                            .aspectRatio(0.75f)
                            .background(Color.DarkGray, MaterialTheme.shapes.large)
                    ) {
                        PgcRankBadge(rank = rank)
                    }
                }
                PgcShelfMoreCard(text = "完整榜单", onClick = {})
            }
        }
    }
}
