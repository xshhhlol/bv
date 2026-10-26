package dev.aaa1115910.bv.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.aaa1115910.biliapi.entity.home.DislikeType
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.bv.component.settings.SettingsActionListItem
import dev.aaa1115910.bv.component.settings.SettingsDialogSurface
import dev.aaa1115910.bv.component.settings.SettingsDialogTitle

/**
 * 推荐卡片上按菜单键弹出的操作菜单。
 *
 * 选中一项就关掉菜单，请求交给调用方在后台发，结果用 toast 告诉用户。
 */
@Composable
fun RecommendVideoMenu(
    item: UgcItem,
    onDismiss: () -> Unit,
    onAddWatchLater: () -> Unit,
    onDislike: (DislikeType) -> Unit
) {
    val firstItemRequester = remember { FocusRequester() }
    val pick: (() -> Unit) -> Unit = { action ->
        onDismiss()
        action()
    }

    LaunchedEffect(Unit) {
        // 弹窗的窗口要等一帧才挂上，太早 requestFocus 会落空，焦点还留在底下的卡片上
        withFrameNanos { }
        runCatching { firstItemRequester.requestFocus() }
    }

    SettingsDialogSurface(
        modifier = Modifier.onPreviewKeyEvent {
            // 再按一次菜单键收起。只认抬起：按下就关的话，抬起会漏到底下的卡片上又把菜单打开
            if (it.key != Key.Menu) return@onPreviewKeyEvent false
            if (it.type == KeyEventType.KeyUp) onDismiss()
            true
        },
        onDismiss = onDismiss
    ) { maxHeightModifier ->
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsDialogTitle(
                text = item.title,
                subtitle = item.author.takeIf { it.isNotBlank() },
                maxLines = 2
            )
            Column(
                // 矮屏上四项放不下时要能滚，焦点移到最后一项会自己滚进可见区
                modifier = maxHeightModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SettingsActionListItem(
                    modifier = Modifier.focusRequester(firstItemRequester),
                    title = "添加至稍后再看",
                    onClick = { pick(onAddWatchLater) }
                )
                // 只有推荐流的条目带着反馈接口要的参数
                if (item.dislikeArgs != null) {
                    SettingsActionListItem(
                        title = "不感兴趣：当前视频",
                        onClick = { pick { onDislike(DislikeType.Video) } }
                    )
                    if (item.authorMid != null && item.authorMid != 0L) {
                        SettingsActionListItem(
                            title = "不感兴趣：UP 主",
                            value = item.author.takeIf { it.isNotBlank() },
                            onClick = { pick { onDislike(DislikeType.Up) } }
                        )
                    }
                    SettingsActionListItem(
                        title = "不感兴趣：这类视频",
                        onClick = { pick { onDislike(DislikeType.Similar) } }
                    )
                }
            }
        }
    }
}
