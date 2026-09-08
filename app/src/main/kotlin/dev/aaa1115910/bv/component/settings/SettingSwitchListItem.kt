package dev.aaa1115910.bv.component.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun SettingSwitchListItem(
    modifier: Modifier = Modifier,
    title: String,
    supportText: String,
    checked: Boolean,
    defaultHasFocus: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    var hasFocus by remember { mutableStateOf(defaultHasFocus) }
    var switchChecked by remember { mutableStateOf(checked) }
    val focusProgress by animateFloatAsState(
        targetValue = if (hasFocus) 1f else 0f,
        animationSpec = BVMotion.smoothSpring(),
        label = "setting switch focus"
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
        trailingContent = {
            Switch(
                modifier = Modifier.focusable(false),
                checked = switchChecked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BVColor.Pink,
                    checkedBorderColor = BVColor.Pink,
                    uncheckedThumbColor = BVColor.TextTertiary,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.2f)
                )
            )
        },
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors = settingListItemColors(),
        scale = settingListItemScale(),
        onClick = {
            switchChecked = !switchChecked
            onCheckedChange(switchChecked)
        },
        selected = false
    )
}

@Preview
@Composable
fun SettingSwitchListItemFocusedAndEnabledPreview() {
    BVTheme {
        SettingSwitchListItem(
            title = "This is a title",
            supportText = "This is a support text",
            checked = true,
            defaultHasFocus = true,
            onCheckedChange = {}
        )
    }
}

@Preview
@Composable
fun SettingSwitchListItemFocusedAndDisabledPreview() {
    BVTheme {
        SettingSwitchListItem(
            title = "This is a title",
            supportText = "This is a support text",
            checked = false,
            defaultHasFocus = true,
            onCheckedChange = {}
        )
    }
}

@Preview
@Composable
fun SettingSwitchListItemNotFocusedAndEnabledPreview() {
    BVTheme {
        SettingSwitchListItem(
            title = "This is a title",
            supportText = "This is a support text",
            checked = true,
            defaultHasFocus = false,
            onCheckedChange = {}
        )
    }
}

@Preview
@Composable
fun SettingSwitchListItemNotFocusedAndDisabledPreview() {
    BVTheme {
        SettingSwitchListItem(
            title = "This is a title",
            supportText = "This is a support text",
            checked = false,
            defaultHasFocus = false,
            onCheckedChange = {}
        )
    }
}