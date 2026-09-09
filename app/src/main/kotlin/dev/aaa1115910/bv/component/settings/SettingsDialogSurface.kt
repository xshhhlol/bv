package dev.aaa1115910.bv.component.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion

/**
 * 设置里所有弹窗共用的容器。
 *
 * 之前选项弹窗、控制条按钮、自定义快捷键各自抄了一份几乎一样的 Surface，
 * 圆角、宽度、描边却各写各的；同一个设置页里连着开两个弹窗就能看出不是一套东西。
 *
 * content 收到的 Modifier 已经带好宽度和高度上限，直接给里面的滚动列表用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialogSurface(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val maxHeightDp = with(density) {
        (windowInfo.containerSize.height * 0.6f).toDp()
    }

    BasicAlertDialog(
        modifier = Modifier.padding(vertical = 24.dp),
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = modifier
                // 原来是 fillMaxWidth(0.9f)：选项只有几个短词时会被拉成一条横贯屏幕的空条
                .widthIn(min = 380.dp, max = 560.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(BVColor.Surface)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            content(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp)
            )
        }
    }
}

/**
 * 弹窗里的一行可点条目。
 *
 * 和设置页的条目共用同一套焦点视觉（左侧渐变竖条 + 浮起的底色），
 * 否则弹窗里的行获焦时会走 tv-material3 的默认反白，整行糊成一块白底。
 */
@Composable
fun SettingsActionListItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String? = null,
    valueColor: Color? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = BVMotion.smoothSpring(),
        label = "dialog action item focus"
    )

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .settingFocusBar { focusProgress }
            .onFocusChanged { hasFocus = it.hasFocus },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        trailingContent = value?.let {
            {
                SettingItemValue(
                    value = it,
                    focusProgress = focusProgress,
                    color = valueColor
                )
            }
        },
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors = settingListItemColors(),
        scale = settingListItemScale(),
        onClick = onClick,
        onLongClick = onLongClick,
        selected = false
    )
}

/** 弹窗标题，统一字号和下面那条渐变短线 */
@Composable
fun SettingsDialogTitle(
    modifier: Modifier = Modifier,
    text: String,
    subtitle: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = BVColor.TextPrimary
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = BVColor.TextTertiary
            )
        }
    }
}
