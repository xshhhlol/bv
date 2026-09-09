package dev.aaa1115910.bv.component.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.tv.material3.Icon
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

/**
 * 设置项左侧的图标底座。
 *
 * 一整列设置项如果只有文字，扫视时全是一样的灰块；给每项一个图标，
 * 找「网络」「存储」这种具体条目会快很多。获焦时底座和图标一起染成品牌粉。
 */
@Composable
internal fun SettingItemIcon(
    icon: ImageVector,
    focusProgress: Float
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                lerp(
                    Color.White.copy(alpha = 0.06f),
                    BVColor.Pink.copy(alpha = 0.20f),
                    focusProgress
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = null,
            tint = lerp(BVColor.TextSecondary, BVColor.PinkBright, focusProgress)
        )
    }
}

/**
 * 右侧的「当前值」胶囊。
 *
 * 以前这些值是拼成「当前：1080P」塞在副标题里的，一列看下来左边参差不齐；
 * 拎到右侧对齐成一列之后，扫一眼就能把所有当前配置读完。
 */
@Composable
internal fun SettingItemValue(
    value: String,
    focusProgress: Float,
    /** 需要用颜色区分状态时传，比如「已隐藏」和「默认焦点」 */
    color: Color? = null
) {
    Text(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                lerp(
                    Color.White.copy(alpha = 0.07f),
                    Color.White.copy(alpha = 0.16f),
                    focusProgress
                )
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        text = value,
        style = MaterialTheme.typography.labelMedium,
        color = color ?: lerp(BVColor.TextSecondary, BVColor.TextPrimary, focusProgress),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SettingListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String? = null,
    /** 当前取值，会显示成右侧的胶囊；为空时右侧退化成一个箭头 */
    value: String? = null,
    icon: ImageVector? = null,
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
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .settingFocusBar { focusProgress }
            .onFocusChanged { hasFocus = it.hasFocus },
        leadingContent = icon?.let { { SettingItemIcon(icon = it, focusProgress = focusProgress) } },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = supportText?.takeIf { it.isNotBlank() }?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = lerp(BVColor.TextTertiary, BVColor.TextSecondary, focusProgress),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            if (value != null) {
                SettingItemValue(value = value, focusProgress = focusProgress)
            } else {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = lerp(BVColor.TextTertiary, BVColor.TextSecondary, focusProgress)
                )
            }
        },
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
            supportText = "找不到所选清晰度时会自动降级",
            value = "1080P 高码率",
            icon = Icons.Rounded.HighQuality,
            onClick = {}
        )
    }
}
