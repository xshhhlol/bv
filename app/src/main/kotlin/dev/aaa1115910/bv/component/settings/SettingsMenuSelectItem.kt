package dev.aaa1115910.bv.component.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun SettingsMenuSelectItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    defaultHasFocus: Boolean = false,
    onClick: () -> Unit
) {
    var hasFocus by remember { mutableStateOf(defaultHasFocus) }
    val focusProgress by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = BVMotion.smoothSpring(),
        label = "settings select item focus"
    )

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .settingFocusBar { focusProgress }
            .onFocusChanged { hasFocus = it.hasFocus },
        headlineContent = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        },
        trailingContent = {
            RadioButton(
                modifier = Modifier.focusable(false),
                selected = selected,
                onClick = { },
                // 原来选中/未选中给的是同一个颜色，只能靠圆点有没有实心分辨，
                // 在电视上离两三米远基本看不出来；选中直接上品牌粉
                colors = RadioButtonDefaults.colors(
                    selectedColor = BVColor.Pink,
                    unselectedColor = lerp(
                        BVColor.TextTertiary,
                        BVColor.TextSecondary,
                        focusProgress
                    )
                )
            )
        },
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors = settingListItemColors(),
        scale = settingListItemScale(),
        onClick = onClick,
        selected = selected
    )
}

private class SettingsMenuSelectItemPreviewParameterProvider :
    PreviewParameterProvider<SettingsMenuSelectItemData> {
    override val values = sequenceOf(
        SettingsMenuSelectItemData(text = "This is a text", selected = false, onFocused = false),
        SettingsMenuSelectItemData(text = "This is a text", selected = false, onFocused = true),
        SettingsMenuSelectItemData(text = "This is a text", selected = true, onFocused = false),
        SettingsMenuSelectItemData(text = "This is a text", selected = true, onFocused = true),
    )
}

private data class SettingsMenuSelectItemData(
    val text: String,
    val selected: Boolean,
    val onFocused: Boolean
)

@Preview(widthDp = 360)
@Composable
private fun SettingsMenuSelectItemPreview(
    @PreviewParameter(SettingsMenuSelectItemPreviewParameterProvider::class) data: SettingsMenuSelectItemData
) {
    BVTheme {
        SettingsMenuSelectItem(
            text = data.text,
            selected = data.selected,
            defaultHasFocus = data.onFocused,
            onClick = {}
        )
    }
}
