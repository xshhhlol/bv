package dev.aaa1115910.bv.component

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.bv.ui.theme.AccentBrush
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.ui.theme.FocusRingBrush
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.focusHighlight
import dev.aaa1115910.bv.util.requestFocus

private val lineHeight = 84.dp

/** 面板整体宽度：三个操作按钮 + 间隙，用它保证上下两块严格对齐 */
private val panelWidth = 372.dp
private val actionButtonWidth = 116.dp

@Composable
fun UserPanel(
    modifier: Modifier = Modifier,
    username: String,
    face: String,
    level: Int,
    currentExp: Int,
    nextLevelExp: Int,
    onHide: () -> Unit,
    onGoUserSwitch: () -> Unit,
    onGoFollowingUp: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var inIncognitoMode by remember { mutableStateOf(Prefs.incognitoMode) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus(scope)
    }

    Box(
        modifier = modifier
            .onPreviewKeyEvent {
                when (it.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        if (it.nativeKeyEvent.action == KeyEvent.ACTION_UP) onHide()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        Column(
            modifier = Modifier.onPreviewKeyEvent {
                when (it.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        return@onPreviewKeyEvent true
                    }
                }
                false
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UserPanelMyItem(
                modifier = Modifier.width(panelWidth),
                username = username,
                face = face,
                level = level,
                currentExp = currentExp,
                nextLevelExp = nextLevelExp,
            )

            Row {
                UserPanelSmallItem(
                    modifier = Modifier
                        .width(actionButtonWidth)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent {
                            when (it.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        },
                    title = if (inIncognitoMode) "隐身开启" else "隐身关闭",
                    icon = if (inIncognitoMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    highlighted = inIncognitoMode,
                    onClick = {
                        inIncognitoMode = !inIncognitoMode
                        Prefs.incognitoMode = inIncognitoMode
                    }
                )
                UserPanelSmallItem(
                    modifier = Modifier
                        .width(actionButtonWidth),
                    title = "正在关注",
                    icon = Icons.AutoMirrored.Rounded.ListAlt,
                    onClick = {
                        onGoFollowingUp()
                        onHide()
                    }
                )
                UserPanelSmallItem(
                    modifier = Modifier
                        .width(actionButtonWidth)
                        .onPreviewKeyEvent {
                            when (it.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        },
                    title = "账号管理",
                    icon = Icons.Rounded.AccountBox,
                    onClick = {
                        onGoUserSwitch()
                        onHide()
                    }
                )
            }
        }
    }
}

@Composable
private fun UserPanelMyItem(
    modifier: Modifier = Modifier,
    username: String,
    face: String,
    level: Int,
    currentExp: Int,
    nextLevelExp: Int
) {
    val progress = (currentExp.toFloat() / nextLevelExp.coerceAtLeast(1)).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .focusable(false)
            .height(lineHeight),
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = BVColor.Surface,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(end = 14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = username,
                        style = MaterialTheme.typography.titleMedium,
                        color = BVColor.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 等级做成小徽章，比纯文字更像「身份」
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BVColor.Pink.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Lv.$level",
                            style = MaterialTheme.typography.labelSmall,
                            color = BVColor.PinkBright
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LevelProgressBar(progress = progress)
            }

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(2.dp, FocusRingBrush, CircleShape)
                )
                AsyncImage(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BVColor.SurfaceVariant),
                    model = face,
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/** 经验条，用品牌渐变而不是单色，和面板其他强调元素统一 */
@Composable
private fun LevelProgressBar(
    modifier: Modifier = Modifier,
    progress: Float
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(AccentBrush)
        )
    }
}


@Composable
private fun UserPanelSmallItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val iconColor by animateColorAsState(
        targetValue = when {
            hasFocus -> BVColor.TextPrimary
            highlighted -> BVColor.Pink
            else -> BVColor.TextSecondary
        },
        animationSpec = BVMotion.colorTween(),
        label = "user panel icon color"
    )

    Surface(
        modifier = modifier
            .padding(4.dp)
            .height(lineHeight)
            .focusHighlight(
                shape = MaterialTheme.shapes.medium,
                focusedScale = 1.04f,
                borderWidth = 2.dp,
                glowElevation = 14.dp
            )
            .onFocusChanged { hasFocus = it.hasFocus },
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BVColor.Surface,
            focusedContainerColor = BVColor.SurfaceHighlight,
            pressedContainerColor = BVColor.SurfaceHighlight
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Icon(
                modifier = Modifier.align(Alignment.TopStart),
                imageVector = icon,
                contentDescription = null,
                tint = iconColor
            )
            Text(
                modifier = Modifier.align(Alignment.BottomStart),
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = BVColor.TextPrimary
            )
        }
    }
}


@Preview(device = "id:tv_1080p")
@Composable
private fun UserPanelPreview() {
    BVTheme {
        UserPanel(
            username = "abcde",
            face = "",
            onHide = {},
            onGoUserSwitch = {},
            onGoFollowingUp = {},
            level = 5,
            currentExp = 100,
            nextLevelExp = 200,
        )
    }
}
