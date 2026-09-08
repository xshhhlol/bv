package dev.aaa1115910.bv.component.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.BVTheme

/** 设置项统一配色：默认几乎隐形，获焦时浮起来一层 */
@Composable
fun settingListItemColors() = ListItemDefaults.colors(
    containerColor = Color.White.copy(alpha = 0.04f),
    contentColor = BVColor.TextPrimary,
    focusedContainerColor = BVColor.SurfaceHighlight,
    focusedContentColor = BVColor.TextPrimary,
    pressedContainerColor = BVColor.SurfaceHighlight,
    pressedContentColor = BVColor.TextPrimary,
    selectedContainerColor = Color.White.copy(alpha = 0.08f),
    selectedContentColor = BVColor.TextPrimary,
    focusedSelectedContainerColor = BVColor.SurfaceHighlight,
    focusedSelectedContentColor = BVColor.TextPrimary
)

@Composable
fun settingListItemScale() = ListItemDefaults.scale(focusedScale = 1.02f)

/**
 * 获焦时在左侧长出一条渐变竖条，替代整行反白，视觉上更克制也更容易定位。
 *
 * 必须画在内容之上：ListItem 自己的背景是在这一层之后绘制的，用 drawBehind 会被盖住。
 */
fun Modifier.settingFocusBar(progress: () -> Float): Modifier = drawWithContent {
    drawContent()

    val value = progress().coerceIn(0f, 1f)
    if (value <= 0.01f) return@drawWithContent

    val barWidth = 4.dp.toPx()
    val barHeight = size.height * 0.5f * value
    val top = (size.height - barHeight) / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan),
            startY = top,
            endY = top + barHeight
        ),
        topLeft = Offset(x = 0f, y = top),
        size = Size(width = barWidth, height = barHeight),
        cornerRadius = CornerRadius(barWidth / 2f),
        alpha = value
    )
}

@Composable
fun SettingListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    defaultHasFocus: Boolean = false,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(defaultHasFocus) }
    val focusProgress by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = BVMotion.smoothSpring(),
        label = "setting item focus"
    )

    ListItem(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .settingFocusBar { focusProgress }
            .onFocusChanged { hasFocus = it.hasFocus },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = supportText,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = { },
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors = settingListItemColors(),
        scale = settingListItemScale(),
        onClick = onClick,
        selected = false
    )
}

@Preview(widthDp = 480)
@Composable
private fun SettingListItemPreview() {
    BVTheme {
        SettingListItem(
            title = "默认分辨率",
            supportText = "当前：1080P 高码率",
            onClick = {}
        )
    }
}
