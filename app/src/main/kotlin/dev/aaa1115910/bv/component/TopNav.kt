package dev.aaa1115910.bv.component

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.pgc.PgcType
import dev.aaa1115910.biliapi.entity.ugc.UgcTypeV2
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.util.getDisplayName

@Composable
fun TopNav(
    modifier: Modifier = Modifier,
    items: List<TopNavItem>,
    isLargePadding: Boolean,
    onSelectedChanged: (TopNavItem) -> Unit = {},
    onClick: (TopNavItem) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    // 存起来，从别的板块切回来时导航条还停在原来那一栏，不会被重置回第一个
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }

    /**
     * 焦点移到内容区时导航条「收一收」。
     *
     * 只动 graphicsLayer，不动 padding：padding 会改变 topBar 的实际高度，Scaffold 的内容区
     * 跟着重新测量，等于整页视频网格在这 260ms 里每帧重排一次——移动焦点、切页面时那一下的卡顿
     * 主要就是这么来的。位移和缩放放在绘制阶段做，布局全程不动。
     */
    val recede by animateFloatAsState(
        targetValue = if (isLargePadding) 0f else 1f,
        animationSpec = BVMotion.floatTween(),
        label = "top nav recede"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { hasFocus = it.hasFocus }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        TabRow(
            modifier = Modifier
                .graphicsLayer {
                    translationY = -6.dp.toPx() * recede
                    val shrink = 1f - 0.04f * recede
                    scaleX = shrink
                    scaleY = shrink
                    alpha = 1f - 0.12f * recede
                }
                .focusRestorer(focusRequester),
            selectedTabIndex = selectedTabIndex,
            // TabRow 自带的底色是一整条胶囊，会盖住下面的单个 tab 指示器，这里让它透明
            containerColor = Color.Transparent,
            separator = { Spacer(modifier = Modifier.width(8.dp)) },
            indicator = { _, _ -> }
        ) {
            items.forEachIndexed { index, tab ->
                NavItemTab(
                    modifier = Modifier
                        .ifElse(index == 0, Modifier.focusRequester(focusRequester)),
                    topNavItem = tab,
                    selected = index == selectedTabIndex,
                    active = hasFocus,
                    onFocus = {
                        selectedTabIndex = index
                        onSelectedChanged(tab)
                    },
                    onClick = { onClick(tab) }
                )
            }
        }
    }
}

@Composable
private fun TabRowScope.NavItemTab(
    modifier: Modifier = Modifier,
    topNavItem: TopNavItem,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    val context = LocalContext.current

    // 选中态画在 Tab 内部而不是 TabRow 的 indicator 槽位：
    // tv-material3 1.1.0-alpha01 里 indicator 会盖在 tab 内容上面，把后面的 tab 文字挡掉
    val glassAlpha by animateFloatAsState(
        targetValue = when {
            selected && active -> 0.16f
            selected -> 0.09f
            else -> 0f
        },
        animationSpec = BVMotion.floatTween(),
        label = "tab glass alpha"
    )
    val barAlpha by animateFloatAsState(
        targetValue = when {
            selected && active -> 1f
            selected -> 0.6f
            else -> 0f
        },
        animationSpec = BVMotion.floatTween(),
        label = "tab bar alpha"
    )

    Tab(
        modifier = modifier,
        selected = selected,
        onFocus = onFocus,
        onClick = onClick,
        colors = TabDefaults.underlinedIndicatorTabColors(
            contentColor = BVColor.TextSecondary,
            inactiveContentColor = BVColor.TextTertiary,
            selectedContentColor = BVColor.TextPrimary,
            focusedContentColor = BVColor.TextPrimary,
            focusedSelectedContentColor = BVColor.TextPrimary
        )
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = glassAlpha))
                // 底部渐变短线直接按实测宽度画：Tab 是 wrap content，
                // 子元素用 fillMaxWidth 拿不到宽度会被压成 0
                .drawBehind {
                    val alpha = barAlpha
                    if (alpha <= 0.01f) return@drawBehind
                    val barWidth = size.width * 0.5f
                    val barHeight = 3.dp.toPx()
                    val left = (size.width - barWidth) / 2f
                    val top = size.height - barHeight - 3.dp.toPx()
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan),
                            startX = left,
                            endX = left + barWidth
                        ),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2f),
                        alpha = alpha
                    )
                }
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
                text = topNavItem.getDisplayName(context),
                color = LocalContentColor.current,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

interface TopNavItem {
    fun getDisplayName(context: Context = BVApp.context): String
}


enum class HomeTopNavItem(val code: Int, private val displayName: String) : TopNavItem {
    Dynamics(0, "动态"),
    Recommend(1, "推荐"),
    Popular(2, "热门");

    companion object{
        fun fromCode(code: Int): HomeTopNavItem {
            return HomeTopNavItem.entries.find { it.code == code } ?: Dynamics
        }
    }

    override fun getDisplayName(context: Context): String {
        return displayName
    }
}

enum class UgcTopNavItem(val ugcTypeV2: UgcTypeV2) : TopNavItem {
    Douga(UgcTypeV2.Douga),
    Game(UgcTypeV2.Game),
    Kichiku(UgcTypeV2.Kichiku),
    Music(UgcTypeV2.Music),
    Dance(UgcTypeV2.Dance),
    Cinephile(UgcTypeV2.Cinephile),
    Ent(UgcTypeV2.Ent),
    Knowledge(UgcTypeV2.Knowledge),
    Tech(UgcTypeV2.Tech),
    Information(UgcTypeV2.Information),
    Food(UgcTypeV2.Food),
    Life(UgcTypeV2.LifeJoy),
    Car(UgcTypeV2.Car),
    Fashion(UgcTypeV2.Fashion),
    Sports(UgcTypeV2.Sports),
    Animal(UgcTypeV2.Animal);

    override fun getDisplayName(context: Context): String {
        return ugcTypeV2.getDisplayName(context)
    }
}

enum class PgcTopNavItem(private val pgcType: PgcType) : TopNavItem {
    Anime(PgcType.Anime),
    GuoChuang(PgcType.GuoChuang),
    Movie(PgcType.Movie),
    Documentary(PgcType.Documentary),
    Tv(PgcType.Tv),
    Variety(PgcType.Variety);

    override fun getDisplayName(context: Context): String {
        return pgcType.getDisplayName(context)
    }
}

enum class PersonalTopNavItem : TopNavItem {
    ToView,
    History,
    Favorite,
    FollowingSeason;

    override fun getDisplayName(context: Context): String {
        return when (this) {
            ToView -> "稍后再看"
            History -> "历史"
            Favorite -> "收藏"
            FollowingSeason -> "我追的番"
        }
    }
}

enum class SearchTypeTopNavItem: TopNavItem {
    Video,
    MediaBangumi,
    MediaFt,
    BiliUser;
    override fun getDisplayName(context: Context): String {
        return when (this) {
            Video -> "视频"
            MediaBangumi -> "番剧"
            MediaFt -> "影视"
            BiliUser -> "用户"
        }
    }
}