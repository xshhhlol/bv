package dev.aaa1115910.bv.screen.settings.content

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.MediaCodecActivity
import dev.aaa1115910.bv.component.settings.SettingsCard
import dev.aaa1115910.bv.component.settings.SettingsGroupTitle
import dev.aaa1115910.bv.component.settings.SettingsInfoRow
import dev.aaa1115910.bv.component.settings.SettingsPage
import dev.aaa1115910.bv.component.settings.SettingsUsageRow
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import java.text.DecimalFormat
import kotlin.math.pow

/** 可用 / 总量，单位统一到 GB；取不到就当作 0，界面上退化成 Unknown */
private data class UsageInfo(
    val availableBytes: Long,
    val totalBytes: Long
) {
    val known get() = totalBytes > 0

    /** 已用比例，给占用条用 */
    val usedFraction: Float?
        get() = if (known) ((totalBytes - availableBytes).toFloat() / totalBytes) else null

    private fun format(bytes: Long) = DecimalFormat("###.##").format(bytes / 1024.0.pow(3)) + " GB"

    fun availableText() = format(availableBytes)
    fun totalText() = format(totalBytes)
}

@Composable
fun InfoSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val memoryInfo by remember {
        mutableStateOf(
            runCatching {
                val info = ActivityManager.MemoryInfo()
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                    .getMemoryInfo(info)
                UsageInfo(availableBytes = info.availMem, totalBytes = info.totalMem)
            }.getOrDefault(UsageInfo(0L, 0L))
        )
    }

    val storageInfo by remember {
        mutableStateOf(
            runCatching {
                val statFs = StatFs(Environment.getExternalStorageDirectory().absolutePath)
                UsageInfo(
                    availableBytes = statFs.availableBytes,
                    totalBytes = statFs.totalBytes
                )
            }.getOrDefault(UsageInfo(0L, 0L))
        )
    }

    @Suppress("DEPRECATION")
    val screenInfo by remember {
        mutableStateOf(
            run {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display!!
                } else {
                    (context as Activity).windowManager.defaultDisplay
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val mode = display.mode
                    Triple(mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
                } else {
                    Triple(display.width, display.height, display.refreshRate)
                }
            }
        )
    }

    SettingsPage(
        modifier = modifier,
        title = SettingsMenuNavItem.Info.getDisplayName(context),
        subtitle = SettingsMenuNavItem.Info.getDescription(context)
    ) {
        item { SettingsGroupTitle(text = "硬件") }
        item {
            SettingsCard {
                SettingsInfoRow(
                    icon = Icons.Rounded.Devices,
                    label = stringResource(R.string.settings_info_manufacturer),
                    value = Build.MANUFACTURER
                )
                SettingsInfoRow(
                    icon = Icons.Rounded.Tv,
                    label = stringResource(R.string.settings_info_model),
                    value = stringResource(
                        R.string.settings_info_value_model,
                        Build.MODEL,
                        Build.PRODUCT
                    )
                )
                SettingsInfoRow(
                    icon = Icons.Rounded.Android,
                    label = stringResource(R.string.settings_info_system),
                    value = stringResource(
                        R.string.settings_info_value_system,
                        Build.VERSION.RELEASE
                    )
                )
                SettingsInfoRow(
                    icon = Icons.Rounded.AspectRatio,
                    label = stringResource(R.string.settings_info_screen),
                    // 原来刷新率用 %f 打，屏幕上会出现「60.000000Hz」这种数字
                    value = stringResource(
                        R.string.settings_info_value_screen,
                        screenInfo.first,
                        screenInfo.second,
                        formatRefreshRate(screenInfo.third)
                    )
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    SettingsInfoRow(
                        icon = Icons.Rounded.DeveloperBoard,
                        label = stringResource(R.string.settings_info_soc),
                        value = stringResource(
                            R.string.settings_info_value_soc,
                            Build.SOC_MANUFACTURER,
                            Build.SOC_MODEL
                        )
                    )
                }
            }
        }

        item { SettingsGroupTitle(text = "占用") }
        item {
            SettingsCard {
                SettingsUsageRow(
                    icon = Icons.Rounded.Memory,
                    label = stringResource(R.string.settings_info_memory),
                    value = if (memoryInfo.known) stringResource(
                        R.string.settings_info_value_usage,
                        memoryInfo.availableText(),
                        memoryInfo.totalText()
                    ) else "Unknown",
                    usedFraction = memoryInfo.usedFraction
                )
                SettingsUsageRow(
                    icon = Icons.Rounded.Storage,
                    label = stringResource(R.string.settings_info_storage),
                    value = if (storageInfo.known) stringResource(
                        R.string.settings_info_value_usage,
                        storageInfo.availableText(),
                        storageInfo.totalText()
                    ) else "Unknown",
                    usedFraction = storageInfo.usedFraction
                )
            }
        }

        item {
            Button(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                onClick = {
                    context.startActivity(Intent(context, MediaCodecActivity::class.java))
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Rounded.DeveloperBoard,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.title_activity_media_codec))
                }
            }
        }
    }
}

/** 60.0 显示成 60，59.94 保留两位，避免出现一串没意义的小数 */
private fun formatRefreshRate(rate: Float): String =
    if (kotlin.math.abs(rate - Math.round(rate)) < 0.01f) Math.round(rate).toString()
    else DecimalFormat("###.##").format(rate)
