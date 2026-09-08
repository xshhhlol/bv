package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.bv.entity.ControllerButtonConfig
import dev.aaa1115910.bv.entity.ControllerButtonsStore
import dev.aaa1115910.bv.util.toast

/**
 * 播放器控制条按钮设置
 *
 * 支持调整按钮顺序、隐藏不用的按钮，以及指定控制条弹出时默认聚焦哪个按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControllerButtonsDialog(
    onDismiss: () -> Unit,
    onConfigsChanged: (List<ControllerButtonConfig>) -> Unit
) {
    val context = LocalContext.current
    var configs by remember { mutableStateOf(ControllerButtonsStore.get()) }

    fun update(next: List<ControllerButtonConfig>) {
        configs = ControllerButtonsStore.save(next)
        onConfigsChanged(configs)
    }

    fun move(index: Int, offset: Int) {
        val target = index + offset
        if (target !in configs.indices) return
        val next = configs.toMutableList()
        next.add(target, next.removeAt(index))
        update(next)
    }

    fun toggleHidden(index: Int) {
        val next = configs.toMutableList()
        val current = next[index]
        // 默认焦点按钮被隐藏后就不该再是默认焦点，交给 store 的 normalize 处理
        next[index] = current.copy(hidden = !current.hidden, isDefaultFocus = false)
        update(next)
    }

    fun setDefaultFocus(index: Int) {
        if (configs[index].hidden) {
            "已隐藏的按钮不能作为默认焦点".toast(context)
            return
        }
        update(configs.mapIndexed { i, config -> config.copy(isDefaultFocus = i == index) })
    }

    ControllerButtonsDialogSurface(onDismiss = onDismiss) { maxHeightModifier ->
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "控制条按钮",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "确认键：显示/隐藏　左右键：调整顺序　长按确认键：设为默认焦点",
                style = MaterialTheme.typography.bodySmall
            )
            LazyColumn(
                modifier = maxHeightModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(configs, key = { _, item -> item.button.id }) { index, config ->
                    ListItem(
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> {
                                    move(index, -1); true
                                }

                                Key.DirectionRight -> {
                                    move(index, 1); true
                                }

                                else -> false
                            }
                        },
                        headlineContent = { Text(text = "${index + 1}. ${config.button.title}") },
                        trailingContent = {
                            val status = when {
                                config.hidden -> "已隐藏"
                                config.isDefaultFocus -> "默认焦点"
                                else -> "显示"
                            }
                            Text(text = status)
                        },
                        onClick = { toggleHidden(index) },
                        onLongClick = { setDefaultFocus(index) },
                        selected = false
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        configs = ControllerButtonsStore.reset()
                        onConfigsChanged(configs)
                        "已恢复默认".toast(context)
                    }
                ) {
                    Text("恢复默认")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControllerButtonsDialogSurface(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val maxHeightDp = with(density) { (windowInfo.containerSize.height * 0.6f).toDp() }

    BasicAlertDialog(
        modifier = Modifier.padding(vertical = 24.dp),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            content(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp)
            )
        }
    }
}
