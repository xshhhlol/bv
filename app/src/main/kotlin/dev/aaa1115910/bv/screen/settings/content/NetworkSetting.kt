package dev.aaa1115910.bv.screen.settings.content

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Https
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.http.BiliHttpProxyApi
import dev.aaa1115910.biliapi.repositories.ChannelRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.activities.settings.SpeedTestActivity
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingSwitchListItem
import dev.aaa1115910.bv.component.settings.SettingsGroupTitle
import dev.aaa1115910.bv.component.settings.SettingsPage
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import org.koin.compose.getKoin

@Composable
fun NetworkSetting(
    modifier: Modifier = Modifier,
    channelRepository: ChannelRepository = getKoin().get()
) {
    val context = LocalContext.current
    var enableProxy by remember { mutableStateOf(Prefs.enableProxy) }
    var proxyHttpServer by remember { mutableStateOf(Prefs.proxyHttpServer) }
    var proxyGRPCServer by remember { mutableStateOf(Prefs.proxyGRPCServer) }
    var preferOfficialCdn by remember { mutableStateOf(Prefs.preferOfficialCdn) }
    var showProxyHttpServerEditDialog by remember { mutableStateOf(false) }
    var showProxyGRPCServerEditDialog by remember { mutableStateOf(false) }

    SettingsPage(
        modifier = modifier,
        title = SettingsMenuNavItem.Network.getDisplayName(context),
        subtitle = SettingsMenuNavItem.Network.getDescription(context)
    ) {
        item { SettingsGroupTitle(text = "代理") }
        item {
            // 两个服务器地址跟着开关一起展开/收起，所以整块放在同一个 item 里，
            // 拆成三个 item 的话 AnimatedVisibility 收起时 LazyColumn 会直接跳一下
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingSwitchListItem(
                    title = stringResource(R.string.settings_network_enable_proxy_title),
                    supportText = stringResource(R.string.settings_network_enable_proxy_text),
                    icon = Icons.Rounded.VpnKey,
                    checked = Prefs.enableProxy,
                    onCheckedChange = { enable ->
                        enableProxy = enable
                        Prefs.enableProxy = enable
                        if (enable) BVApp.instance?.initProxy()
                    }
                )
                AnimatedVisibility(visible = enableProxy) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingListItem(
                            title = stringResource(R.string.settings_network_proxy_http_server_title),
                            supportText = "HTTP 接口走这台服务器",
                            value = proxyHttpServer.ifBlank {
                                stringResource(R.string.settings_network_proxy_server_content_empty)
                            },
                            icon = Icons.Rounded.Https,
                            onClick = { showProxyHttpServerEditDialog = true }
                        )
                        SettingListItem(
                            title = stringResource(R.string.settings_network_proxy_grpc_server_title),
                            supportText = "gRPC 接口走这台服务器",
                            value = proxyGRPCServer.ifBlank {
                                stringResource(R.string.settings_network_proxy_server_content_empty)
                            },
                            icon = Icons.Rounded.SettingsEthernet,
                            onClick = { showProxyGRPCServerEditDialog = true }
                        )
                    }
                }
            }
        }

        item { SettingsGroupTitle(text = "线路") }
        item {
            SettingSwitchListItem(
                title = stringResource(R.string.settings_network_prefer_official_cdn_title),
                supportText = stringResource(R.string.settings_network_prefer_official_cdn_text),
                icon = Icons.Rounded.Cloud,
                checked = Prefs.preferOfficialCdn,
                onCheckedChange = { enable ->
                    preferOfficialCdn = enable
                    Prefs.preferOfficialCdn = enable
                }
            )
        }
        item {
            SettingListItem(
                title = stringResource(R.string.settings_network_test_title),
                supportText = stringResource(R.string.settings_network_test_text),
                icon = Icons.Rounded.NetworkCheck,
                onClick = {
                    context.startActivity(Intent(context, SpeedTestActivity::class.java))
                }
            )
        }
    }

    ProxyServerEditDialog(
        show = showProxyHttpServerEditDialog,
        onHideDialog = { showProxyHttpServerEditDialog = false },
        title = stringResource(R.string.settings_network_proxy_http_server_title),
        proxyServer = proxyHttpServer,
        onProxyServerChange = {
            proxyHttpServer = it
            Prefs.proxyHttpServer = it
            BiliHttpProxyApi.createClient(it)
        }
    )
    ProxyServerEditDialog(
        show = showProxyGRPCServerEditDialog,
        onHideDialog = { showProxyGRPCServerEditDialog = false },
        title = stringResource(R.string.settings_network_proxy_grpc_server_title),
        proxyServer = proxyGRPCServer,
        onProxyServerChange = {
            proxyGRPCServer = it
            Prefs.proxyGRPCServer = it
            runCatching {
                channelRepository.initProxyChannel(
                    accessKey = Prefs.accessToken,
                    buvid = Prefs.buvid,
                    proxyServer = it
                )
            }
        }
    )
}

@Composable
fun ProxyServerEditDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    title: String,
    proxyServer: String,
    onProxyServerChange: (String) -> Unit
) {
    var proxyServerString by remember(show) { mutableStateOf(proxyServer) }

    if (show) {
        AlertDialog(
            modifier = modifier,
            title = { Text(text = title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = proxyServerString,
                        onValueChange = { proxyServerString = it },
                        singleLine = true,
                        maxLines = 1,
                        shape = MaterialTheme.shapes.medium,
                        placeholder = { Text(text = stringResource(R.string.proxy_server_edit_dialog_input_field_label)) }
                    )
                    // 图标原来单独占一行，孤零零挂在文字上面；挪到行首才像一条提示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp),
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = BVColor.TextTertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.proxy_server_edit_dialog_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = BVColor.TextTertiary
                        )
                    }
                }
            },
            onDismissRequest = onHideDialog,
            confirmButton = {
                Button(onClick = {
                    onProxyServerChange(
                        proxyServerString
                            .replace("\n", "")
                            .replace("https://", "")
                            .replace("http://", "")
                    )
                    onHideDialog()
                }) {
                    Text(text = stringResource(id = R.string.common_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onHideDialog) {
                    Text(text = stringResource(id = R.string.common_cancel))
                }
            }
        )
    }
}

@Preview
@Composable
fun ProxyServerEditDialogPreview() {
    BVTheme {
        ProxyServerEditDialog(
            show = true,
            onHideDialog = {},
            title = "title",
            proxyServer = "",
            onProxyServerChange = {}
        )
    }
}