package dev.aaa1115910.bv.screen.settings.content

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.LogsActivity
import dev.aaa1115910.bv.component.settings.CookiesDialog
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.component.settings.SettingsGroupTitle
import dev.aaa1115910.bv.component.settings.SettingsPage
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.util.Prefs

@Composable
fun OtherSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showCookiesDialog by remember { mutableStateOf(false) }
    var showPreferedApiDialog by remember { mutableStateOf(false) }

    var showFps by remember { mutableStateOf(Prefs.showFps) }
    var showPlayerInfo by remember { mutableStateOf(Prefs.showPlayerInfo) }
    var selectedApi by remember { mutableStateOf(Prefs.apiType) }

    SettingsPage(
        modifier = modifier,
        title = SettingsMenuNavItem.Other.getDisplayName(context),
        subtitle = SettingsMenuNavItem.Other.getDescription(context)
    ) {
        item { SettingsGroupTitle(text = "账号与接口") }
        item {
            SettingListItem(
                title = "接口选择",
                supportText = "取数据时优先走哪一套接口",
                value = selectedApi.name,
                icon = Icons.Rounded.Api,
                onClick = { showPreferedApiDialog = true }
            )
        }
        item {
            SettingListItem(
                title = stringResource(R.string.settings_other_cookies_title),
                supportText = stringResource(R.string.settings_other_cookies_text),
                icon = Icons.Rounded.Cookie,
                onClick = { showCookiesDialog = true }
            )
        }

//        item {
//            SettingSwitchListItem(
//                title = stringResource(R.string.settings_other_firebase_title),
//                supportText = stringResource(R.string.settings_other_firebase_text),
//                checked = Prefs.enableFirebaseCollection,
//                onCheckedChange = {
//                    Prefs.enableFirebaseCollection = it
//                    FirebaseUtil.setCrashlyticsCollectionEnabled(it)
//                }
//            )
//        }

        item { SettingsGroupTitle(text = "调试") }
        item {
            SettingSwitchListItem(
                title = stringResource(R.string.settings_other_fps_title),
                supportText = stringResource(R.string.settings_other_fps_text),
                icon = Icons.Rounded.Speed,
                checked = showFps,
                onCheckedChange = {
                    showFps = it
                    Prefs.showFps = it
                }
            )
        }
        item {
            SettingSwitchListItem(
                title = "播放器调试信息",
                supportText = "播放时左上角显示码率、网速、缓冲、磁盘预下载等状态；也可用控制条最右的按钮随时开关",
                icon = Icons.Rounded.Analytics,
                checked = showPlayerInfo,
                onCheckedChange = {
                    showPlayerInfo = it
                    Prefs.showPlayerInfo = it
                }
            )
        }
        item {
            SettingListItem(
                title = stringResource(R.string.settings_create_logs_title),
                supportText = stringResource(R.string.settings_create_logs_text),
                icon = Icons.AutoMirrored.Rounded.ListAlt,
                onClick = {
                    context.startActivity(Intent(context, LogsActivity::class.java))
                }
            )
        }
        if (BuildConfig.DEBUG) {
            item {
                SettingListItem(
                    title = stringResource(R.string.settings_crash_test_title),
                    supportText = stringResource(R.string.settings_crash_test_text),
                    icon = Icons.Rounded.BugReport,
                    onClick = {
                        throw Exception("Boom!")
                    }
                )
            }
        }
    }

    CookiesDialog(
        show = showCookiesDialog,
        onHideDialog = { showCookiesDialog = false }
    )

    if (showPreferedApiDialog) {
        OptionDialog(
            title = "接口选择",
            options = ApiType.entries.toTypedArray(),
            selectedOption = selectedApi,
            onDismiss = { showPreferedApiDialog = false },
            onSelect = {
                Prefs.apiType = it
                selectedApi = it
            },
            getDisplayName = { it.name }
        )
    }
}
