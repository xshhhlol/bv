package dev.aaa1115910.bv.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.component.settings.SettingListItem
import dev.aaa1115910.bv.component.settings.SettingsPage
import dev.aaa1115910.bv.screen.settings.SettingsMenuNavItem
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.util.LogCatcherUtil
import dev.aaa1115910.bv.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun StorageSetting(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger { }

    var loading by remember { mutableStateOf(false) }
    var imageCacheSize by remember { mutableLongStateOf(0L) }
    var updateCacheSize by remember { mutableLongStateOf(0L) }
    var crashLogsSize by remember { mutableLongStateOf(0L) }
    //var libVLCCacheSize by remember { mutableLongStateOf(0L) }
    //var libVLCFileSize by remember { mutableLongStateOf(0L) }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var clearFun: (() -> Unit)? by remember { mutableStateOf(null) }
    var content by remember { mutableStateOf("") }
    var size by remember { mutableLongStateOf(0L) }

    val calSize = {
        val imageCacheDir = File(context.cacheDir, "image_cache")
        val updateCacheDir = File(context.cacheDir, "update_downloader")
        val crashLogsDir = File(context.filesDir, LogCatcherUtil.LOG_DIR)
        //val libVLCCacheDir = File(context.cacheDir, "libvlc_downloader")
        //val libVLCFileDir = File(context.filesDir, "vlc_libs")

        imageCacheSize = getFolderSize(imageCacheDir)
        updateCacheSize = getFolderSize(updateCacheDir)
        crashLogsSize = getFolderSize(crashLogsDir)
        //libVLCCacheSize = getFolderSize(libVLCCacheDir)
        //libVLCFileSize = getFolderSize(libVLCFileDir)
    }

    val clearImageCaches: () -> Unit = {
        logger.fInfo { "clearImageCaches" }
        val imageCacheDir = File(context.cacheDir, "image_cache")
        imageCacheDir.deleteRecursively()
    }

    val clearCrashLogs: () -> Unit = {
        logger.fInfo { "clearCrashLogs" }
        val crashLogsDir = File(context.filesDir, LogCatcherUtil.LOG_DIR)
        crashLogsDir.deleteRecursively()
    }

    val clearOthersCaches: () -> Unit = {
        logger.fInfo { "clearOthersCaches" }
        val updateCacheDir = File(context.cacheDir, "update_downloader")
        //val libVLCCacheDir = File(context.cacheDir, "libvlc_downloader")
        updateCacheDir.deleteRecursively()
        //libVLCCacheDir.deleteRecursively()
    }

    //val clearLibVLCFiles: () -> Unit = {
    //    logger.fInfo { "clearLibVLCFiles" }
    //    val libVLCFileDir = File(context.filesDir, "vlc_libs")
    //    libVLCFileDir.deleteRecursively()
    //}

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            loading = true
            calSize()
            loading = false
        }
    }

    SettingsPage(
        modifier = modifier,
        title = SettingsMenuNavItem.Storage.getDisplayName(context),
        subtitle = SettingsMenuNavItem.Storage.getDescription(context)
    ) {
        item {
            SettingListItem(
                title = stringResource(R.string.settings_storage_image_cache),
                supportText = "已经加载过的封面和头像",
                value = if (loading) stringResource(R.string.settings_storage_calculating)
                else formatFileSize(imageCacheSize),
                icon = Icons.Rounded.Image,
                onClick = {
                    clearFun = clearImageCaches
                    content = context.getString(R.string.settings_storage_image_cache)
                    size = imageCacheSize
                    showConfirmDialog = true
                }
            )
        }
        item {
            SettingListItem(
                title = stringResource(R.string.settings_storage_others_cache),
                supportText = "更新包等临时文件",
                value = if (loading) stringResource(R.string.settings_storage_calculating)
                //else formatFileSize(updateCacheSize + libVLCCacheSize),
                else formatFileSize(updateCacheSize),
                icon = Icons.Rounded.Folder,
                onClick = {
                    clearFun = clearOthersCaches
                    content = context.getString(R.string.settings_storage_others_cache)
                    size = updateCacheSize// + libVLCCacheSize
                    showConfirmDialog = true
                }
            )
        }
        //item {
        //    SettingListItem(
        //        title = stringResource(R.string.settings_storage_libvlc_files),
        //        supportText = if (loading) stringResource(R.string.settings_storage_calculating)
        //        else "${libVLCFileSize / 1024 / 1024} MB",
        //        onClick = {
        //            clearFun = clearLibVLCFiles
        //            content = context.getString(R.string.settings_storage_libvlc_files)
        //            size = libVLCFileSize
        //            showConfirmDialog = true
        //        }
        //    )
        //}
        item {
            SettingListItem(
                title = stringResource(R.string.settings_storage_crash_logs),
                supportText = "崩溃时自动保存的日志文件",
                value = if (loading) stringResource(R.string.settings_storage_calculating)
                else formatFileSize(crashLogsSize),
                icon = Icons.Rounded.BugReport,
                onClick = {
                    clearFun = clearCrashLogs
                    content = context.getString(R.string.settings_storage_crash_logs)
                    size = crashLogsSize
                    showConfirmDialog = true
                }
            )
        }
    }

    ConfirmDeleteDialog(
        show = showConfirmDialog,
        onHideDialog = { showConfirmDialog = false },
        content = content,
        size = size,
        clearFiles = {
            clearFun?.invoke()
            calSize()
        }
    )
}

private fun getFolderSize(f: File): Long {
    var size: Long = 0
    if (f.isDirectory) {
        for (file in f.listFiles()!!) {
            size += getFolderSize(file)
        }
    } else {
        size = f.length()
    }
    return size
}

/**
 * 原来一律按 MB 取整显示，几百 KB 的缓存全都写成「0 MB」，
 * 看起来像是坏了；按量级选单位，小的也能看出来到底占了多少。
 */
private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
private fun ConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    content: String,
    size: Long,
    clearFiles: () -> Unit
) {
    if (show) {
        AlertDialog(
            modifier = modifier,
            onDismissRequest = onHideDialog,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CleaningServices,
                    contentDescription = null,
                    tint = BVColor.PinkBright
                )
            },
            title = { Text(text = "清除$content") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "将释放 ${formatFileSize(size)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = BVColor.TextPrimary
                    )
                    Text(
                        text = "清除后这些文件会重新生成，不影响已登录的账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = BVColor.TextTertiary
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    clearFiles()
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
