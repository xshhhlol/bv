package dev.aaa1115910.bv.screen.settings

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.settings.settingFocusBar
import dev.aaa1115910.bv.component.settings.settingListItemColors
import dev.aaa1115910.bv.component.settings.settingListItemScale
import dev.aaa1115910.bv.screen.settings.content.AboutSetting
import dev.aaa1115910.bv.screen.settings.content.AudioVideoSetting
import dev.aaa1115910.bv.screen.settings.content.InfoSetting
import dev.aaa1115910.bv.screen.settings.content.NetworkSetting
import dev.aaa1115910.bv.screen.settings.content.OtherSetting
import dev.aaa1115910.bv.screen.settings.content.PlayerTypeSetting
import dev.aaa1115910.bv.screen.settings.content.StorageSetting
import dev.aaa1115910.bv.screen.settings.content.UISetting
import dev.aaa1115910.bv.ui.theme.AccentBrush
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVMotion
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.requestFocus

/** 侧栏底色：从左边缘的浅色向右渐隐，和主界面左侧导航是同一套做法 */
private val SettingsRailBrush = Brush.horizontalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.065f),
        Color.White.copy(alpha = 0.015f)
    )
)

/** 侧栏右缘的一根发丝线，上下淡出，比硬切一条边自然 */
private fun Modifier.railEdge(): Modifier = drawWithContent {
    drawContent()
    val lineWidth = 1.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.10f),
                Color.Transparent
            )
        ),
        topLeft = Offset(x = size.width - lineWidth, y = 0f),
        size = Size(width = lineWidth, height = size.height)
    )
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    var currentMenu by remember { mutableStateOf(SettingsMenuNavItem.AudioVideo) }
    var focusInNav by remember { mutableStateOf(false) }

    // 「设置」这个标题原本是 Scaffold 的 topBar，横跨整个宽度压在两栏上面，
    // 结果右侧内容区被顶掉一截，左侧导航又显得没头没尾。
    // 把它收进侧栏顶部，侧栏就成了一个完整的单元，内容区也拿回了整个高度。
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        SettingsNav(
            modifier = Modifier
                .onFocusChanged { focusInNav = it.hasFocus }
                .weight(3f)
                .fillMaxHeight()
                .background(SettingsRailBrush)
                .railEdge(),
            currentMenu = currentMenu,
            onMenuChanged = { currentMenu = it },
            isFocusing = focusInNav
        )
        SettingContent(
            modifier = Modifier
                .weight(7f)
                .fillMaxSize(),
            onBackNav = { focusInNav = true },
            currentMenu = currentMenu
        )
    }
}

@Composable
fun SettingsNav(
    modifier: Modifier = Modifier,
    currentMenu: SettingsMenuNavItem,
    onMenuChanged: (SettingsMenuNavItem) -> Unit,
    isFocusing: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isFocusing) {
        if (isFocusing) focusRequester.requestFocus(scope)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus(scope)
    }

    Column(
        modifier = modifier
    ) {
        Column(
            // start 取 32dp：正好等于列表的 16dp contentPadding 加 tv ListItem 自带的
            // 16dp 内边距，标题和下面每一项的图标就落在同一条竖线上
            modifier = Modifier.padding(start = 32.dp, top = 32.dp, end = 24.dp, bottom = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.title_activity_settings),
                style = MaterialTheme.typography.headlineMedium,
                color = BVColor.TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 标题下的渐变短线，给整页定一个视觉锚点
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentBrush)
            )
        }
        LazyColumn(
            // 用 weight 而不是 fillMaxSize：Column 里的子项拿到的最大高度是整栏高度，
            // fillMaxSize 会让列表按整栏来量，上面标题占掉的那截就会把底部条目顶出屏幕
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (item in SettingsMenuNavItem.entries - listOf(SettingsMenuNavItem.PlayerType)) {
                val buttonModifier = if (currentMenu == item) Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth()
                else Modifier.fillMaxWidth()
                item {
                    SettingsMenuButton(
                        modifier = buttonModifier,
                        text = item.getDisplayName(context),
                        icon = item.icon,
                        selected = currentMenu == item,
                        onFocus = {
                            onMenuChanged(item)
                        }
                    )
                }
            }
        }
    }
}

enum class SettingsMenuNavItem(
    private val strRes: Int,
    private val descRes: Int,
    val icon: ImageVector
) {
    AudioVideo(
        R.string.settings_item_audio_video_settings,
        R.string.settings_item_audio_video_settings_desc,
        Icons.Rounded.PlayCircle
    ),
    PlayerType(
        R.string.settings_item_player_type,
        R.string.settings_item_player_type_desc,
        Icons.Rounded.Memory
    ),
    UI(
        R.string.settings_item_ui,
        R.string.settings_item_ui_desc,
        Icons.Rounded.Palette
    ),
    Other(
        R.string.settings_item_other,
        R.string.settings_item_other_desc,
        Icons.Rounded.Tune
    ),
    Storage(
        R.string.settings_item_storage,
        R.string.settings_item_storage_desc,
        Icons.Rounded.Storage
    ),
    Network(
        R.string.settings_item_network,
        R.string.settings_item_network_desc,
        Icons.Rounded.Wifi
    ),
    Info(
        R.string.settings_item_info,
        R.string.settings_item_info_desc,
        Icons.Rounded.DeveloperBoard
    ),
    About(
        R.string.settings_item_about,
        R.string.settings_item_about_desc,
        Icons.Rounded.Info
    );

    fun getDisplayName(context: Context) = context.getString(strRes)

    fun getDescription(context: Context) = context.getString(descRes)
}

@Composable
fun SettingContent(
    modifier: Modifier = Modifier,
    onBackNav: () -> Unit,
    currentMenu: SettingsMenuNavItem
) {
    Box(
        modifier = modifier
            .padding(start = 28.dp, end = 40.dp, top = 32.dp)
    ) {
        SettingsDetail(
            modifier = Modifier.fillMaxSize(),
            onFocusBackMenuList = {
                onBackNav()
            }
        ) {
            when (currentMenu) {
                SettingsMenuNavItem.AudioVideo -> AudioVideoSetting()
                SettingsMenuNavItem.Info -> InfoSetting()
                SettingsMenuNavItem.About -> AboutSetting()
                SettingsMenuNavItem.Other -> OtherSetting()
                SettingsMenuNavItem.Network -> NetworkSetting()
                SettingsMenuNavItem.PlayerType -> PlayerTypeSetting()
                SettingsMenuNavItem.UI -> UISetting()
                SettingsMenuNavItem.Storage -> StorageSetting()
            }
        }
    }
}

@Composable
fun SettingsMenuButton(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    onFocus: () -> Unit,
    onLoseFocus: () -> Unit = {},
    onClick: () -> Unit = {},
    selected: Boolean
) {
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = BVMotion.smoothSpring(),
        label = "settings menu selection"
    )

    ListItem(
        modifier = modifier
            .settingFocusBar { selectedProgress }
            .onFocusChanged { if (it.hasFocus) onFocus() else onLoseFocus() },
        selected = selected,
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors = settingListItemColors(),
        scale = settingListItemScale(),
        onClick = onClick,
        leadingContent = {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = icon,
                contentDescription = null,
                tint = lerp(BVColor.TextTertiary, BVColor.PinkBright, selectedProgress)
            )
        },
        headlineContent = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

@Preview
@Composable
fun SettingsMenuButtonPreview() {
    BVTheme {
        Box(
            modifier = Modifier.size(240.dp, 100.dp)
        ) {
            SettingsMenuButton(
                modifier = Modifier.align(Alignment.Center),
                text = "播放设置",
                icon = Icons.Rounded.PlayCircle,
                selected = true,
                onFocus = {}
            )
        }
    }
}

@Composable
fun SettingsDetail(
    modifier: Modifier = Modifier,
    onFocusBackMenuList: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                val result = it.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                if (result) onFocusBackMenuList()
                result
            }
    ) {
        content()
    }
}
