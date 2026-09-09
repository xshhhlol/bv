package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.aaa1115910.bv.component.LibVLCDownloaderDialog
import dev.aaa1115910.bv.component.settings.SettingsMenuSelectItem
import dev.aaa1115910.bv.component.settings.SettingsPage
import dev.aaa1115910.bv.entity.PlayerType
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.util.Prefs

@Composable
fun PlayerTypeSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPlayerType by remember { mutableStateOf(Prefs.playerType) }
    var showLibVLCDownloaderDialog by remember { mutableStateOf(false) }

    SettingsPage(
        modifier = modifier,
        title = SettingsMenuNavItem.PlayerType.getDisplayName(context),
        subtitle = SettingsMenuNavItem.PlayerType.getDescription(context)
    ) {
        items(PlayerType.entries) { playerType ->
            SettingsMenuSelectItem(
                text = playerType.name,
                selected = selectedPlayerType == playerType,
                onClick = {
                    selectedPlayerType = playerType
                    Prefs.playerType = playerType
                }
            )
        }
    }

    LibVLCDownloaderDialog(
        show = showLibVLCDownloaderDialog,
        onHideDialog = {
            showLibVLCDownloaderDialog = false
        }
    )
}
