package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.settings.SettingsActionListItem
import dev.aaa1115910.bv.component.settings.SettingsDialogSurface
import dev.aaa1115910.bv.component.settings.SettingsDialogTitle
import dev.aaa1115910.bv.component.settings.SettingsMenuSelectItem
import dev.aaa1115910.bv.entity.PlayerCustomShortcut
import dev.aaa1115910.bv.entity.PlayerCustomShortcutAction
import dev.aaa1115910.bv.entity.PlayerCustomShortcutActionGroup
import dev.aaa1115910.bv.entity.PlayerCustomShortcutCatalog
import dev.aaa1115910.bv.entity.PlayerCustomShortcutKeys
import dev.aaa1115910.bv.entity.PlayerCustomShortcutsStore
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.util.requestFocus
import dev.aaa1115910.bv.util.toast

@Composable
fun PlayerCustomShortcutsDialog(
    onDismiss: () -> Unit,
    onShortcutsChanged: (List<PlayerCustomShortcut>) -> Unit
) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf(PlayerCustomShortcutsStore.get()) }
    var stage by remember {
        mutableStateOf<PlayerCustomShortcutsDialogStage>(
            PlayerCustomShortcutsDialogStage.Main
        )
    }

    fun updateShortcuts(next: List<PlayerCustomShortcut>) {
        shortcuts = next
        onShortcutsChanged(next)
    }

    fun bindAction(keyCode: Int, action: PlayerCustomShortcutAction) {
        val next = PlayerCustomShortcutsStore.upsert(keyCode, action)
        updateShortcuts(next)
        stage = PlayerCustomShortcutsDialogStage.Main
        "已绑定 ${PlayerCustomShortcutKeys.getDisplayName(keyCode)}".toast(context)
    }

    when (val currentStage = stage) {
        PlayerCustomShortcutsDialogStage.Main -> {
            PlayerCustomShortcutsMainDialog(
                shortcuts = shortcuts,
                onDismiss = onDismiss,
                onAdd = { stage = PlayerCustomShortcutsDialogStage.CaptureKey },
                onClear = { stage = PlayerCustomShortcutsDialogStage.ConfirmClear },
                onEdit = { shortcut ->
                    stage = PlayerCustomShortcutsDialogStage.PickAction(shortcut.keyCode)
                }
            )
        }

        PlayerCustomShortcutsDialogStage.CaptureKey -> {
            PlayerCustomShortcutKeyCaptureDialog(
                onDismiss = { stage = PlayerCustomShortcutsDialogStage.Main },
                onCaptured = { keyCode ->
                    stage = PlayerCustomShortcutsDialogStage.PickAction(keyCode)
                }
            )
        }

        is PlayerCustomShortcutsDialogStage.PickAction -> {
            val currentShortcut = shortcuts.firstOrNull { it.keyCode == currentStage.keyCode }
            PlayerCustomShortcutActionPickerDialog(
                keyCode = currentStage.keyCode,
                currentShortcut = currentShortcut,
                onDismiss = { stage = PlayerCustomShortcutsDialogStage.Main },
                onSelectAction = { action ->
                    bindAction(currentStage.keyCode, action)
                },
                onPickValues = { group ->
                    stage = PlayerCustomShortcutsDialogStage.PickActionValue(
                        keyCode = currentStage.keyCode,
                        groupId = group.id
                    )
                },
                onRemove = {
                    val next = PlayerCustomShortcutsStore.remove(currentStage.keyCode)
                    updateShortcuts(next)
                    stage = PlayerCustomShortcutsDialogStage.Main
                    "已删除绑定".toast(context)
                }
            )
        }

        is PlayerCustomShortcutsDialogStage.PickActionValue -> {
            val currentShortcut = shortcuts.firstOrNull { it.keyCode == currentStage.keyCode }
            val group = PlayerCustomShortcutCatalog.groups(context)
                .firstOrNull { it.id == currentStage.groupId }
            if (group == null) {
                stage = PlayerCustomShortcutsDialogStage.Main
            } else {
                PlayerCustomShortcutValuePickerDialog(
                    keyCode = currentStage.keyCode,
                    group = group,
                    currentShortcut = currentShortcut,
                    onDismiss = {
                        stage = PlayerCustomShortcutsDialogStage.PickAction(currentStage.keyCode)
                    },
                    onSelect = { action ->
                        bindAction(currentStage.keyCode, action)
                    }
                )
            }
        }

        PlayerCustomShortcutsDialogStage.ConfirmClear -> {
            PlayerCustomShortcutClearConfirmDialog(
                onDismiss = { stage = PlayerCustomShortcutsDialogStage.Main },
                onConfirm = {
                    val next = PlayerCustomShortcutsStore.clear()
                    updateShortcuts(next)
                    stage = PlayerCustomShortcutsDialogStage.Main
                    "已清空自定义播放快捷键".toast(context)
                }
            )
        }
    }
}

@Composable
private fun PlayerCustomShortcutsMainDialog(
    shortcuts: List<PlayerCustomShortcut>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onClear: () -> Unit,
    onEdit: (PlayerCustomShortcut) -> Unit
) {
    val context = LocalContext.current

    SettingsDialogSurface(
        onDismiss = onDismiss
    ) { maxHeightModifier ->
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsDialogTitle(
                text = "自定义播放快捷键",
                subtitle = "选中一条可以改绑或删除"
            )
            LazyColumn(
                modifier = maxHeightModifier,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (shortcuts.isEmpty()) {
                    item {
                        Text(
                            modifier = Modifier.padding(vertical = 20.dp),
                            text = "还没有绑定任何按键",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BVColor.TextTertiary
                        )
                    }
                } else {
                    items(shortcuts, key = { it.keyCode }) { shortcut ->
                        // 按键名当标题，绑定的动作放右边对齐成一列，比拼成一句话好扫
                        SettingsActionListItem(
                            title = PlayerCustomShortcutKeys.getDisplayName(shortcut.keyCode),
                            value = PlayerCustomShortcutCatalog.getActionDisplayName(
                                context,
                                shortcut.action
                            ),
                            onClick = { onEdit(shortcut) }
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onAdd) {
                    Text("新增")
                }
                Button(
                    enabled = shortcuts.isNotEmpty(),
                    onClick = onClear
                ) {
                    Text("清空")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun PlayerCustomShortcutKeyCaptureDialog(
    onDismiss: () -> Unit,
    onCaptured: (Int) -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    SettingsDialogSurface(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                if (event.nativeKeyEvent.repeatCount != 0) return@onPreviewKeyEvent true

                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    PlayerCustomShortcutKeys.isCancelKeyCode(keyCode) -> {
                        onDismiss()
                    }

                    PlayerCustomShortcutKeys.isAllowedKeyCode(keyCode) -> {
                        onCaptured(keyCode)
                    }

                    else -> {
                        "该按键不能绑定".toast(context)
                    }
                }
                true
            },
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsDialogTitle(
                text = "按下要绑定的按键",
                subtitle = "返回、ESC、手柄 B、确认和 Enter 不可绑定"
            )
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun PlayerCustomShortcutActionPickerDialog(
    keyCode: Int,
    currentShortcut: PlayerCustomShortcut?,
    onDismiss: () -> Unit,
    onSelectAction: (PlayerCustomShortcutAction) -> Unit,
    onPickValues: (PlayerCustomShortcutActionGroup) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val actionGroups = remember(context) { PlayerCustomShortcutCatalog.groups(context) }
    val firstActionFocusRequester = remember { FocusRequester() }
    val focusScope = rememberCoroutineScope()
    val deleteItemCount = if (currentShortcut != null) 1 else 0
    val totalActionItemCount = actionGroups.size + deleteItemCount
    val returnButtonIndex = totalActionItemCount
    var focusedActionIndex by remember { mutableIntStateOf(deleteItemCount) }

    LaunchedEffect(keyCode) {
        firstActionFocusRequester.requestFocus(focusScope)
    }

    SettingsDialogSurface(
        onDismiss = onDismiss
    ) { maxHeightModifier ->
        Column(
            modifier = Modifier
                .padding(24.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> focusedActionIndex <= 0
                        Key.DirectionDown -> focusedActionIndex >= returnButtonIndex
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsDialogTitle(
                text = "选择动作",
                subtitle = PlayerCustomShortcutKeys.getDisplayName(keyCode)
            )
            LazyColumn(
                modifier = maxHeightModifier
                    .focusRestorer(firstActionFocusRequester),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentShortcut != null) {
                    item {
                        SettingsActionListItem(
                            modifier = Modifier.onFocusChanged {
                                if (it.hasFocus) focusedActionIndex = 0
                            },
                            title = "删除当前绑定",
                            onClick = onRemove
                        )
                    }
                }
                itemsIndexed(actionGroups, key = { _, item -> item.id }) { index, group ->
                    val selected = currentShortcut?.action?.let { currentAction ->
                        // 检查当前动作是否属于这个group
                        group.action == currentAction || group.values.any { it.action == currentAction }
                    } ?: false
                    val itemIndex = index + deleteItemCount
                    SettingsMenuSelectItem(
                        modifier = Modifier
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(firstActionFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .onFocusChanged {
                                if (it.hasFocus) focusedActionIndex = itemIndex
                            },
                        text = group.displayName,
                        selected = selected,
                        onClick = {
                            group.action?.let(onSelectAction) ?: onPickValues(group)
                        }
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) focusedActionIndex = returnButtonIndex
                },
                onClick = onDismiss
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun PlayerCustomShortcutValuePickerDialog(
    keyCode: Int,
    group: PlayerCustomShortcutActionGroup,
    currentShortcut: PlayerCustomShortcut?,
    onDismiss: () -> Unit,
    onSelect: (PlayerCustomShortcutAction) -> Unit
) {
    val firstValueFocusRequester = remember { FocusRequester() }
    val focusScope = rememberCoroutineScope()
    val returnButtonIndex = group.values.size
    var focusedValueIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(keyCode, group.id) {
        firstValueFocusRequester.requestFocus(focusScope)
    }

    SettingsDialogSurface(
        onDismiss = onDismiss
    ) { maxHeightModifier ->
        Column(
            modifier = Modifier
                .padding(24.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> focusedValueIndex <= 0
                        Key.DirectionDown -> focusedValueIndex >= returnButtonIndex
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsDialogTitle(
                text = group.displayName,
                subtitle = PlayerCustomShortcutKeys.getDisplayName(keyCode)
            )
            LazyColumn(
                modifier = maxHeightModifier
                    .focusRestorer(firstValueFocusRequester),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(group.values, key = { _, item -> item.displayName }) { index, entry ->
                    SettingsMenuSelectItem(
                        modifier = Modifier
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(firstValueFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .onFocusChanged {
                                if (it.hasFocus) focusedValueIndex = index
                            },
                        text = entry.valueDisplayName,
                        selected = currentShortcut?.action == entry.action,
                        onClick = { onSelect(entry.action) }
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) focusedValueIndex = returnButtonIndex
                },
                onClick = onDismiss
            ) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun PlayerCustomShortcutClearConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    SettingsDialogSurface(
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsDialogTitle(
                text = "清空全部绑定？",
                subtitle = "清空后所有自定义按键都会失效"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConfirm) {
                    Text("清空")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    }
}

private sealed interface PlayerCustomShortcutsDialogStage {
    data object Main : PlayerCustomShortcutsDialogStage
    data object CaptureKey : PlayerCustomShortcutsDialogStage
    data class PickAction(val keyCode: Int) : PlayerCustomShortcutsDialogStage
    data class PickActionValue(
        val keyCode: Int,
        val groupId: String
    ) : PlayerCustomShortcutsDialogStage

    data object ConfirmClear : PlayerCustomShortcutsDialogStage
}
