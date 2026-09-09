package dev.aaa1115910.bv.screen.settings.content

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.aaa1115910.bv.entity.ControllerButtonsStore
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.controllers.playermenu.PlaySpeedItem
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.component.settings.SettingsGroupTitle
import dev.aaa1115910.bv.component.settings.SettingsPage
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.PlayerCustomShortcutsStore
import dev.aaa1115910.bv.entity.Resolution
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.util.Prefs

@Composable
fun AudioVideoSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showResolutionDialog by remember { mutableStateOf(false) }
    var showAudioCodecDialog by remember { mutableStateOf(false) }
    var showVideoCodecDialog by remember { mutableStateOf(false) }
    var showPlaySpeedDialog by remember { mutableStateOf(false) }
    var showActionAfterPlayDialog by remember { mutableStateOf(false) }
    var showPlayerCustomShortcutsDialog by remember { mutableStateOf(false) }
    var showPlayerControllerButtonsDialog by remember { mutableStateOf(false) }
    var controllerButtons by remember { mutableStateOf(ControllerButtonsStore.get()) }

    var selectedResolution by remember { mutableStateOf(Prefs.defaultQuality) }
    var selectedVideoCodec by remember { mutableStateOf(Prefs.defaultVideoCodec) }
    var selectedAudioCodec by remember { mutableStateOf(Prefs.defaultAudio) }
    var selectedPlaySpeed by remember { mutableStateOf(Prefs.defaultPlaySpeed) }
    var selectedActionAfterPlay by remember { mutableStateOf(Prefs.actionAfterPlay) }
    var playerCustomShortcuts by remember { mutableStateOf(PlayerCustomShortcutsStore.get()) }

    var enableFfmpegAudioRenderer by remember { mutableStateOf(Prefs.enableFfmpegAudioRenderer) }
    var enableSoftwareVideoRenderer by remember { mutableStateOf(Prefs.enableSoftwareVideoDecoder) }

    SettingsPage(
        modifier = modifier,
        title = SettingsMenuNavItem.AudioVideo.getDisplayName(context),
        subtitle = SettingsMenuNavItem.AudioVideo.getDescription(context)
    ) {
        item { SettingsGroupTitle(text = "默认播放参数") }
        item {
            SettingListItem(
                title = "默认分辨率",
                supportText = "视频没有该清晰度时会自动降级",
                value = selectedResolution.getDisplayName(context),
                icon = Icons.Rounded.HighQuality,
                onClick = { showResolutionDialog = true }
            )
        }
        item {
            SettingListItem(
                title = "默认视频编码",
                supportText = "优先选用的视频编码格式",
                value = selectedVideoCodec.getDisplayName(context),
                icon = Icons.Rounded.VideoSettings,
                onClick = { showVideoCodecDialog = true }
            )
        }
        item {
            SettingListItem(
                title = "默认音频编码",
                supportText = "优先选用的音频编码格式",
                value = selectedAudioCodec.getDisplayName(context),
                icon = Icons.Rounded.GraphicEq,
                onClick = { showAudioCodecDialog = true }
            )
        }
        item {
            SettingListItem(
                title = "默认播放速度",
                supportText = "每次开始播放时的倍速",
                value = selectedPlaySpeed.getDisplayName(context),
                icon = Icons.Rounded.Speed,
                onClick = { showPlaySpeedDialog = true }
            )
        }
        item {
            SettingListItem(
                title = "播放结束动作",
                supportText = "一集放完之后做什么",
                value = selectedActionAfterPlay.getDisplayName(context),
                icon = Icons.Rounded.SkipNext,
                onClick = { showActionAfterPlayDialog = true }
            )
        }

        item { SettingsGroupTitle(text = "操控") }
        item {
            SettingListItem(
                title = "自定义播放快捷键",
                supportText = "把遥控器按键映射到播放器动作",
                value = "${playerCustomShortcuts.size} 个绑定",
                icon = Icons.Rounded.Keyboard,
                onClick = { showPlayerCustomShortcutsDialog = true }
            )
        }
        item {
            SettingListItem(
                title = "控制条按钮",
                supportText = "挑选播放控制条上显示哪些按钮",
                value = "显示 ${controllerButtons.count { !it.hidden }} 个",
                icon = Icons.Rounded.Tune,
                onClick = { showPlayerControllerButtonsDialog = true }
            )
        }

        item { SettingsGroupTitle(text = "解码") }
        item {
            SettingSwitchListItem(
                title = stringResource(R.string.settings_media_software_video_renderer_title),
                supportText = stringResource(R.string.settings_media_software_video_renderer_text),
                icon = Icons.Rounded.Memory,
                checked = enableSoftwareVideoRenderer,
                onCheckedChange = {
                    enableSoftwareVideoRenderer = it
                    Prefs.enableSoftwareVideoDecoder = it
                }
            )
        }
        item {
            SettingSwitchListItem(
                title = stringResource(R.string.settings_media_ffmpeg_audio_renderer_title),
                supportText = stringResource(R.string.settings_media_ffmpeg_audio_renderer_text),
                icon = Icons.Rounded.Headphones,
                checked = enableFfmpegAudioRenderer,
                onCheckedChange = {
                    enableFfmpegAudioRenderer = it
                    Prefs.enableFfmpegAudioRenderer = it
                }
            )
        }
    }
    // 弹窗复用组件
    if (showResolutionDialog) {
        OptionDialog(
            title = "默认分辨率",
            options = Resolution.entries.toTypedArray(),
            selectedOption = selectedResolution,
            onDismiss = { showResolutionDialog = false },
            onSelect = {
                Prefs.defaultQuality = it
                selectedResolution = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showVideoCodecDialog) {
        OptionDialog(
            title = "默认视频编码",
            options = VideoCodec.entries.toTypedArray(),
            selectedOption = selectedVideoCodec,
            onDismiss = { showVideoCodecDialog = false },
            onSelect = {
                Prefs.defaultVideoCodec = it
                selectedVideoCodec = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showAudioCodecDialog) {
        OptionDialog(
            title = "默认音频编码",
            options = Audio.entries.toTypedArray(),
            selectedOption = selectedAudioCodec,
            onDismiss = { showAudioCodecDialog = false },
            onSelect = {
                Prefs.defaultAudio = it
                selectedAudioCodec = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showPlaySpeedDialog) {
        OptionDialog(
            title = "默认播放速度",
            options = PlaySpeedItem.entries.toTypedArray(),
            selectedOption = selectedPlaySpeed,
            onDismiss = { showPlaySpeedDialog = false },
            onSelect = {
                Prefs.defaultPlaySpeed = it
                selectedPlaySpeed = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showActionAfterPlayDialog) {
        OptionDialog(
            title = "播放结束动作",
            options = ActionAfterPlayItems.entries.toTypedArray(),
            selectedOption = selectedActionAfterPlay,
            onDismiss = { showActionAfterPlayDialog = false },
            onSelect = {
                Prefs.actionAfterPlay = it
                selectedActionAfterPlay = it
            },
            getDisplayName = { it.getDisplayName(context) }
        )
    }

    if (showPlayerCustomShortcutsDialog) {
        PlayerCustomShortcutsDialog(
            onDismiss = { showPlayerCustomShortcutsDialog = false },
            onShortcutsChanged = { playerCustomShortcuts = it }
        )
    }

    if (showPlayerControllerButtonsDialog) {
        PlayerControllerButtonsDialog(
            onDismiss = { showPlayerControllerButtonsDialog = false },
            onConfigsChanged = { controllerButtons = it }
        )
    }
}

enum class ActionAfterPlayItems (val code: Int, private val displayName: String){
    Pause(0, "暂停"),
    PlayNext(1, "播放下一集"),
    PlayRelated(3, "播放首个相关视频"),
    Exit(2, "退出播放器");


    companion object{
        fun fromCode(code: Int): ActionAfterPlayItems {
            return ActionAfterPlayItems.entries.find { it.code == code } ?: Exit
        }
    }

    fun getDisplayName(context: Context): String {
        return displayName
    }
}

