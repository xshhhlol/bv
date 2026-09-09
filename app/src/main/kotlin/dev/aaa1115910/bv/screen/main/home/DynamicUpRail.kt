package dev.aaa1115910.bv.screen.main.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.user.FollowedUser
import dev.aaa1115910.bv.component.GradientLoadingIndicator
import dev.aaa1115910.bv.component.TvLazyColumn
import dev.aaa1115910.bv.screen.main.selectionIndicator
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.focusHighlight
import dev.aaa1115910.bv.util.isDpadRight
import dev.aaa1115910.bv.util.isKeyDown

/** 侧栏常驻的宽度，右边的视频列表按这个宽度让位 */
val UpRailWidth = 232.dp

private val UpRailItemHeight = 56.dp
private val UpRailAvatarSize = 40.dp

/**
 * 动态页左侧常驻的 UP 主栏目。
 *
 * 第一栏是「全部」，也就是所有关注 UP 的最新投稿；下面每一栏对应一个关注的 UP 主。
 *
 * 上下移动只挪高亮，**按确认键才真的换栏目**——焦点走到哪就切到哪的话，一路按下去会连着发好几次
 * 请求，右边的内容也跟着乱跳。右键则是单纯把焦点交给右边的视频列表，不动当前栏目。
 */
@Composable
fun DynamicUpRail(
    modifier: Modifier = Modifier,
    ups: List<FollowedUser>,
    loadingUps: Boolean,
    /** 当前选中的 UP，null 表示选中「全部」 */
    selectedMid: Long?,
    onSelectAll: () -> Unit,
    onSelectUp: (FollowedUser) -> Unit,
    onEnterContent: () -> Unit
) {
    TvLazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .width(UpRailWidth)
            .drawBehind {
                // 一层很浅的底色 + 右边缘的分隔线，让侧栏和视频列表分层，但不抢视线
                drawRect(
                    Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.015f)
                        )
                    )
                )
                val lineWidth = 1.dp.toPx()
                drawRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(size.width - lineWidth, 0f),
                    size = Size(lineWidth, size.height)
                )
            }
            // 从内容区回到侧栏时，焦点回到上次停的那一栏
            .focusRestorer()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.isDpadRight() && keyEvent.isKeyDown()) {
                    onEnterContent()
                    return@onPreviewKeyEvent true
                }
                false
            },
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "all") {
            UpRailItem(
                name = "全部",
                subtitle = if (ups.isEmpty()) null else "${ups.size} 位 UP 主",
                selected = selectedMid == null,
                onClick = onSelectAll
            )
        }

        itemsIndexed(
            items = ups,
            key = { _, up -> up.mid }
        ) { _, up ->
            UpRailItem(
                avatar = up.avatar,
                name = up.name,
                selected = selectedMid == up.mid,
                onClick = { onSelectUp(up) }
            )
        }

        if (loadingUps) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(UpRailItemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    GradientLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                }
            }
        }
    }
}

/**
 * 侧栏的一栏。
 *
 * 有两个互相独立的状态：**选中**（左边一道渐变竖条 + 一点底色，表示右边正在显示的就是它）
 * 和**获焦**（渐变描边，表示遥控器停在这儿）。焦点挪到没选中的那一栏时两个标记会同时出现，
 * 正好提示「还要按一下确认才会真的切过去」。
 */
@Composable
private fun UpRailItem(
    modifier: Modifier = Modifier,
    avatar: String? = null,
    name: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = BVMotion.smoothSpring(),
        label = "up rail item selection"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(UpRailItemHeight)
            .selectionIndicator { selectedProgress }
            // 这一栏的底色是半透明的，光晕（elevation 投影）会从底下透上来变成两块灰斑，
            // 所以侧栏只留渐变描边，不要光晕
            .focusHighlight(
                shape = MaterialTheme.shapes.large,
                focusedScale = 1f,
                borderWidth = 2.dp,
                glowElevation = 0.dp
            ),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.large),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (selected) 0.09f else 0f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            pressedContainerColor = Color.White.copy(alpha = 0.16f),
            contentColor = if (selected) BVColor.TextPrimary else BVColor.TextSecondary,
            focusedContentColor = BVColor.TextPrimary,
            pressedContentColor = BVColor.TextPrimary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border.None)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (avatar != null) {
                AsyncImage(
                    modifier = Modifier
                        .size(UpRailAvatarSize)
                        .clip(CircleShape)
                        .background(BVColor.SurfaceVariant),
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(UpRailAvatarSize)
                        .clip(CircleShape)
                        .background(BVColor.SurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Rounded.DynamicFeed,
                        contentDescription = null,
                        tint = LocalContentColor.current
                    )
                }
            }
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalContentColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = BVColor.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun DynamicUpRailPreview() {
    val ups = remember {
        listOf("碧诗", "老番茄", "何同学", "影视飓风")
            .mapIndexed { index, name ->
                FollowedUser(mid = index.toLong(), name = name, avatar = "", sign = "")
            }
    }
    BVTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BVColor.Ink)
        ) {
            DynamicUpRail(
                ups = ups,
                loadingUps = false,
                selectedMid = 1L,
                onSelectAll = {},
                onSelectUp = {},
                onEnterContent = {}
            )
        }
    }
}
