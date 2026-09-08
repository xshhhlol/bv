package dev.aaa1115910.bv.screen.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import coil.compose.AsyncImage
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.FocusRingBrush
import dev.aaa1115910.bv.util.isDpadRight
import dev.aaa1115910.bv.util.isKeyDown

/** 导航栏底色：从左边缘的深色向右渐隐，让它和内容区自然分层而不是硬切一条边 */
private val NaviRailBrush = Brush.horizontalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.07f),
        Color.White.copy(alpha = 0.02f)
    )
)

@Composable
fun LeftNaviContent(
    modifier: Modifier = Modifier,
    isLogin: Boolean = false,
    avatar: String = "",
    selectedItem: LeftNaviItem,
    onLeftNaviItemChanged: (LeftNaviItem) -> Unit,
    onOpenSettings: () -> Unit,
    onShowUserPanel: () -> Unit,
    onFocusToContent: () -> Unit,
    onLogin: () -> Unit
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .background(NaviRailBrush)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.isDpadRight()) {
                    if (keyEvent.isKeyDown()) {
                        onFocusToContent()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            },
        containerColor = Color.Transparent,
    ) {
        var userIsFocused by remember { mutableStateOf(false) }
        val avatarRingAlpha by animateFloatAsState(
            targetValue = if (userIsFocused) 1f else 0f,
            animationSpec = BVMotion.smoothSpring(),
            label = "avatar ring alpha"
        )
        NavigationRailItem(
            modifier = Modifier.onFocusChanged {
                userIsFocused = it.hasFocus
            },
            onClick = {
                if (isLogin) {
                    onShowUserPanel()
                } else {
                    onLogin()
                }
            },
            selected = userIsFocused,
            colors = naviItemColors(),
            icon = {
                if (isLogin) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 头像外圈套一层渐变环，获焦时亮起来
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { alpha = avatarRingAlpha }
                                .border(2.dp, FocusRingBrush, CircleShape)
                        )
                        AsyncImage(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BVColor.SurfaceVariant),
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null
                    )
                }
            }
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            listOf(
                LeftNaviItem.Search,
                LeftNaviItem.Personal,
                LeftNaviItem.Home,
                LeftNaviItem.UGC,
                LeftNaviItem.PGC,
            ).forEach { item ->
                var isFocused by remember { mutableStateOf(false) }
                val selectedProgress by animateFloatAsState(
                    targetValue = if (item == selectedItem) 1f else 0f,
                    animationSpec = BVMotion.smoothSpring(),
                    label = "selection indicator progress"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isFocused) 1.15f else 1f,
                    animationSpec = BVMotion.focusSpring(),
                    label = "navi icon scale"
                )
                NavigationRailItem(
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.hasFocus }
                        .selectionIndicator { selectedProgress },
                    onClick = { onLeftNaviItemChanged(item) },
                    selected = isFocused,
                    colors = naviItemColors(),
                    icon = {
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                            imageVector = item.displayIcon,
                            contentDescription = null
                        )
                    }
                )
            }
        }
        var settingsIsFocused by remember { mutableStateOf(false) }
        val settingsScale by animateFloatAsState(
            targetValue = if (settingsIsFocused) 1.15f else 1f,
            animationSpec = BVMotion.focusSpring(),
            label = "settings icon scale"
        )
        NavigationRailItem(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .onFocusChanged {
                    settingsIsFocused = it.hasFocus
                },
            onClick = onOpenSettings,
            selected = settingsIsFocused,
            colors = naviItemColors(),
            icon = {
                Icon(
                    modifier = Modifier.graphicsLayer {
                        scaleX = settingsScale
                        scaleY = settingsScale
                    },
                    imageVector = Icons.Default.Settings,
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
private fun naviItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = BVColor.TextPrimary,
    unselectedIconColor = BVColor.TextTertiary,
    indicatorColor = Color.White.copy(alpha = 0.12f)
)

enum class LeftNaviItem(
    val displayIcon: ImageVector,
    val displayName: String
) {
    Search(displayIcon = Icons.Default.Search, displayName = "搜索"),
    Personal(displayIcon = Icons.Default.Person, displayName = "个人"),
    Home(displayIcon = Icons.Default.Home, displayName = "主页"),
    UGC(displayIcon = Icons.Default.OndemandVideo, displayName = "分区"),
    PGC(displayIcon = Icons.Default.Movie, displayName = "影视"),
}

/**
 * 左侧当前页指示条：一根带渐变的圆角短竖条，切换页面时从中间「长」出来。
 *
 * progress 用 lambda 传进来，动画值只在绘制阶段读取，切页时不会重组整个导航栏。
 */
fun Modifier.selectionIndicator(progress: () -> Float): Modifier = drawBehind {
    val value = progress().coerceIn(0f, 1f)
    if (value <= 0.01f) return@drawBehind

    val strokeWidth = 4.dp.toPx()
    // 离屏幕左边缘留一点距离，电视过扫描时不会被切掉
    val left = 6.dp.toPx()
    val barHeight = size.height * 0.58f * value
    val top = (size.height - barHeight) / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan),
            startY = top,
            endY = top + barHeight
        ),
        topLeft = Offset(x = left, y = top),
        size = Size(width = strokeWidth, height = barHeight),
        cornerRadius = CornerRadius(strokeWidth / 2f),
        alpha = value
    )
}

@Preview(device = "id:tv_1080p")
@Composable
private fun LeftNaviContentPreview() {
    BVTheme {
        LeftNaviContent(
            selectedItem = LeftNaviItem.Home,
            onLeftNaviItemChanged = {},
            onOpenSettings = {},
            onShowUserPanel = {},
            onFocusToContent = {},
            onLogin = {},
        )
    }
}
