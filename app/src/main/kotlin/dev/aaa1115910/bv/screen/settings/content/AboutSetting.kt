package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.settings.SettingsCard
import dev.aaa1115910.bv.component.settings.SettingsPage
import dev.aaa1115910.bv.component.settings.UpdateDialog
import dev.aaa1115910.bv.network.GithubApi
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val RepositoryUrl = "https://github.com/Frost819/bv"

@Composable
fun AboutSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logger = KotlinLogging.logger("AboutSetting")

    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestVersionName by remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            runCatching {
                latestVersionName = GithubApi.getLatestBuild().name
                logger.fInfo { "Find latest version $latestVersionName" }
            }.onFailure {
                logger.fException(it) { "Failed to get latest version" }
                latestVersionName = "Error"
            }
        }
    }

    // 拿到的版本号和当前不一致才算「有新版本」，还在加载或者请求失败时不下这个结论
    val hasUpdate = latestVersionName != "Loading..." &&
            latestVersionName != "Error" &&
            !latestVersionName.contains(BuildConfig.VERSION_NAME)

    SettingsPage(
        modifier = modifier,
        title = SettingsMenuNavItem.About.getDisplayName(context),
        subtitle = SettingsMenuNavItem.About.getDescription(context)
    ) {
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BVColor.Ink),
                        painter = painterResource(R.drawable.bv_launcher_art),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = BVColor.TextPrimary
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_version_current_version,
                                BuildConfig.VERSION_NAME
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = BVColor.TextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.settings_version_latest_version,
                                    latestVersionName
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = BVColor.TextSecondary
                            )
                            if (hasUpdate) {
                                Spacer(modifier = Modifier.width(10.dp))
                                UpdateBadge()
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                onClick = { showUpdateDialog = true }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Rounded.Update,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.settings_version_check_update_button))
                }
            }
        }

        item {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = BVColor.TextTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = RepositoryUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = BVColor.TextTertiary
                )
            }
        }
    }

    UpdateDialog(
        show = showUpdateDialog,
        onHideDialog = { showUpdateDialog = false }
    )
}

/** 有新版本时挂在版本号后面的小标签 */
@Composable
private fun UpdateBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BVColor.Pink.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "有新版本",
            style = MaterialTheme.typography.labelSmall,
            color = BVColor.PinkBright
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun AboutSettingPreview() {
    BVTheme {
        AboutSetting()
    }
}
