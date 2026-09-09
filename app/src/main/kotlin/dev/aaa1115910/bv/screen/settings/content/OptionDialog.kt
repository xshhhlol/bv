package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.component.settings.SettingsDialogSurface
import dev.aaa1115910.bv.component.settings.SettingsDialogTitle
import dev.aaa1115910.bv.component.settings.SettingsMenuSelectItem

@Composable
fun <T : Enum<T>> OptionDialog(
    modifier: Modifier = Modifier,
    title: String? = null,
    options: Array<T>,
    selectedOption: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    getDisplayName: (T) -> String
) = OptionDialog(
    modifier = modifier,
    title = title,
    options = options.toList(),
    selectedOption = selectedOption,
    onDismiss = onDismiss,
    onSelect = onSelect,
    getDisplayName = getDisplayName
)

/** 选项不是枚举时用这个，比如数字档位 */
@Composable
fun <T> OptionDialog(
    modifier: Modifier = Modifier,
    title: String? = null,
    options: List<T>,
    selectedOption: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    getDisplayName: (T) -> String
) {
    SettingsDialogSurface(
        modifier = modifier,
        onDismiss = onDismiss
    ) { maxHeightModifier ->
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 之前这个弹窗只有一列选项，不说明在改什么；从设置项跳进来还好，
            // 但从播放器里唤起时经常一愣，补上标题
            if (title != null) {
                SettingsDialogTitle(text = title)
            }
            LazyColumn(
                modifier = maxHeightModifier,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(options) { option ->
                    SettingsMenuSelectItem(
                        text = getDisplayName(option),
                        selected = selectedOption == option,
                        onClick = { onSelect(option) }
                    )
                }
            }
        }
    }
}
