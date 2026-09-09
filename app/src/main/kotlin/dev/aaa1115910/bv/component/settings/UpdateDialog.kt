package dev.aaa1115910.bv.component.settings

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.network.GithubApi
import dev.aaa1115910.bv.network.entity.Release
import dev.aaa1115910.bv.util.fException
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.util.toMBString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.content.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger("UpdateDialog")

    var updateStatus by remember { mutableStateOf(UpdateStatus.UpdatingInfo) }

    var bytesSentTotal: Long by remember { mutableLongStateOf(0L) }
    var contentLength: Long by remember { mutableLongStateOf(0L) }
    var targetProgress by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        label = "update progress"
    )
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var latestReleaseBuild by remember { mutableStateOf<Release?>(null) }

    DisposableEffect(show) {
        if(!show) {
            downloadJob?.cancel()
        }
        onDispose {
            downloadJob?.cancel()
        }
    }

    val checkUpdate: () -> Unit = {
        updateStatus = UpdateStatus.UpdatingInfo

        scope.launch(Dispatchers.IO) {
            runCatching {
                latestReleaseBuild = GithubApi.getLatestBuild()
                val revision = latestReleaseBuild!!
                    .assets.first { it.name.startsWith("BV") }
                    .name.split("_")[1].toInt()
                if (revision <= BuildConfig.VERSION_CODE) {
                    updateStatus = UpdateStatus.NoAvailableUpdate
                    return@launch
                }
            }.onFailure {
                logger.fException(it) { "Failed to get latest version" }
                updateStatus = UpdateStatus.CheckError
            }.onSuccess {
                logger.fInfo { "Find latest version ${latestReleaseBuild!!.name}" }
                updateStatus = UpdateStatus.Ready
            }
        }
    }

    val installUpdate: (File) -> Unit = { file ->
        updateStatus = UpdateStatus.Installing
        runCatching {
            val uri =
                FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }.onFailure {
            updateStatus = UpdateStatus.InstallError
        }
    }

    val startUpdate: () -> Unit = {
        updateStatus = UpdateStatus.Downloading
        downloadJob = scope.launch(Dispatchers.IO) {
            val tempFilename = latestReleaseBuild!!.assets.first { it.name.startsWith("BV") }.name
            val tempDir = File(context.cacheDir, "update_downloader")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, tempFilename)
            tempFile.createNewFile()
            runCatching {
                GithubApi.downloadUpdate(
                    latestReleaseBuild!!,
                    tempFile,
                    object : ProgressListener {
                        override suspend fun onProgress(downloaded: Long, total: Long?) {
                            bytesSentTotal = downloaded
                            contentLength = total ?: 0
                            targetProgress =
                                runCatching { bytesSentTotal.toFloat() / contentLength }
                                    .getOrDefault(0f)
                        }
                    })
                if (show) installUpdate(tempFile)
            }.onFailure {
                logger.fException(it) { "Failed to download update" }
                updateStatus = UpdateStatus.DownloadError
            }
        }
    }

    LaunchedEffect(Unit) {
        checkUpdate()
    }

    LaunchedEffect(show) {
        if (show) {
            checkUpdate()
        } else {
            updateStatus = UpdateStatus.UpdatingInfo
        }
    }

    if (show) {
        AlertDialog(
            modifier = modifier
                .width(400.dp),
            onDismissRequest = { onHideDialog() },
            icon = {
                // 每种状态给一个图标，扫一眼就知道现在卡在哪一步
                val (icon, tint) = when (updateStatus) {
                    UpdateStatus.UpdatingInfo -> Icons.Rounded.Sync to BVColor.TextSecondary
                    UpdateStatus.Ready -> Icons.Rounded.Update to BVColor.PinkBright
                    UpdateStatus.Downloading -> Icons.Rounded.Download to BVColor.Cyan
                    UpdateStatus.Installing -> Icons.Rounded.InstallMobile to BVColor.Cyan
                    UpdateStatus.NoAvailableUpdate -> Icons.Rounded.CheckCircle to BVColor.Cyan
                    UpdateStatus.CheckError, UpdateStatus.DownloadError, UpdateStatus.InstallError ->
                        Icons.Rounded.ErrorOutline to BVColor.Error
                }
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            },
            title = {
                Text(
                    text = when (updateStatus) {
                        UpdateStatus.UpdatingInfo -> "获取更新信息中"
                        UpdateStatus.Ready -> latestReleaseBuild!!.name
                        UpdateStatus.Downloading -> "下载中"
                        UpdateStatus.Installing -> "安装中"
                        UpdateStatus.NoAvailableUpdate -> "无可用更新"
                        UpdateStatus.CheckError -> "检查更新失败"
                        UpdateStatus.DownloadError -> "下载失败"
                        UpdateStatus.InstallError -> "安装失败"
                    }
                )
            },
            text = {
                when (updateStatus) {
                    UpdateStatus.UpdatingInfo -> {
                        Text(text = "检查更新中...")
                    }

                    UpdateStatus.Ready -> {
                        // release note 可能很长，之前会把弹窗顶出屏幕，这里限高并允许滚动
                        Text(
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            text = latestReleaseBuild?.body ?: "Empty content",
                            style = MaterialTheme.typography.bodySmall,
                            color = BVColor.TextSecondary
                        )
                    }

                    UpdateStatus.Downloading -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = BVColor.Pink,
                                trackColor = Color.White.copy(alpha = 0.10f),
                                strokeCap = StrokeCap.Round,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BVColor.TextPrimary
                                )
                                Text(
                                    text = "${bytesSentTotal.toMBString()} / ${contentLength.toMBString()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BVColor.TextTertiary
                                )
                            }
                        }
                    }

                    UpdateStatus.Installing -> {
                        Text(text = "请坐和放宽")
                    }

                    UpdateStatus.DownloadError -> {
                        Text(text = "下载失败")
                    }

                    UpdateStatus.InstallError -> {
                        Text(text = "安装失败")
                    }

                    UpdateStatus.CheckError -> {
                        Text(text = "获取更新信息失败")
                    }

                    UpdateStatus.NoAvailableUpdate -> {
                        Text(text = "真没更新，骗你是小狗！")
                    }
                }
            },
            confirmButton = {
                when (updateStatus) {
                    UpdateStatus.UpdatingInfo, UpdateStatus.NoAvailableUpdate, UpdateStatus.Downloading, UpdateStatus.Installing -> {}

                    UpdateStatus.Ready -> {
                        Button(onClick = startUpdate) {
                            Text(text = "立即更新")
                        }
                    }

                    UpdateStatus.InstallError, UpdateStatus.DownloadError, UpdateStatus.CheckError -> {
                        Button(onClick = checkUpdate) {
                            Text(text = "再试一次")
                        }
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !(updateStatus == UpdateStatus.Downloading || updateStatus == UpdateStatus.Installing),
                    onClick = { onHideDialog() }
                ) {
                    Text(
                        text = when (updateStatus) {
                            UpdateStatus.UpdatingInfo -> "我点错了"
                            UpdateStatus.Ready -> "打死不更"
                            UpdateStatus.NoAvailableUpdate -> "走了走了"
                            UpdateStatus.CheckError, UpdateStatus.DownloadError, UpdateStatus.InstallError -> "算了算了"
                            UpdateStatus.Downloading, UpdateStatus.Installing -> "你已经无路可逃！"
                        }
                    )
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        )
    }
}

enum class UpdateStatus {
    UpdatingInfo, Ready, Downloading, Installing,
    NoAvailableUpdate, CheckError, DownloadError, InstallError
}