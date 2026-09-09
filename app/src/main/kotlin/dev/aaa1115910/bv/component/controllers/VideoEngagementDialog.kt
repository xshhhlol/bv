package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.biliapi.repositories.CoinRepository
import dev.aaa1115910.biliapi.repositories.FavoriteRepository
import dev.aaa1115910.biliapi.repositories.LikeRepository
import dev.aaa1115910.bv.component.settings.SettingsDialogSurface
import dev.aaa1115910.bv.component.settings.SettingsDialogTitle
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** aid 固定为打开弹窗时的稿件；切换视频后由调用方移除弹窗。 */
@Composable
fun VideoEngagementDialog(
    aid: Long,
    onDismiss: () -> Unit,
    likes: LikeRepository = koinInject(),
    coins: CoinRepository = koinInject(),
    favorites: FavoriteRepository = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("actions") }
    var busy by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var folders by remember { mutableStateOf<List<FavoriteFolderMetadata>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (Prefs.isLogin) {
            try {
                val initialLiked = withContext(Dispatchers.IO) { likes.checkVideoLiked(aid) }
                if (liked == null && !busy) liked = initialLiked
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* 点击时重新查询，不能把查询失败当作未点赞。 */ }
        }
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        if (!Prefs.isLogin) {
            "请先登录".toast(context)
            return
        }
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { (e.message ?: "操作失败，请稍后重试").toast(context) }
            finally { busy = false }
        }
    }

    LaunchedEffect(page, busy) {
        if (!busy) {
            listState.scrollToItem(0)
            withFrameNanos { }
            focus.requestFocus()
        }
    }

    SettingsDialogSurface(onDismiss = { if (!busy) onDismiss() }) { listModifier ->
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsDialogTitle(text = when (page) {
                "coin" -> "选择投币数量"
                "favorite" -> "选择收藏夹"
                else -> "视频操作"
            })
            if (busy) Text("处理中…", style = MaterialTheme.typography.bodySmall)
            LazyColumn(state = listState, modifier = listModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (page == "actions") {
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth().focusRequester(focus), enabled = !busy,
                            onClick = { runAction {
                                val current = liked ?: withContext(Dispatchers.IO) { likes.checkVideoLiked(aid) }
                                withContext(Dispatchers.IO) { likes.updateVideoLiked(aid = aid, like = !current) }
                                liked = !current
                                (if (current) "已取消点赞" else "点赞成功").toast(context)
                            } }
                        ) { Text(if (liked == true) "取消点赞" else "点赞") }
                    }
                    item {
                        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy,
                            onClick = { if (Prefs.isLogin) page = "coin" else "请先登录".toast(context) }
                        ) { Text("投币") }
                    }
                    item {
                        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { runAction {
                            folders = withContext(Dispatchers.IO) {
                                favorites.getAllFavoriteFolderMetadataList(Prefs.uid, rid = aid, preferApiType = Prefs.apiType)
                            }
                            selected = folders.filter { it.videoInThisFav }.map { it.id }.toSet()
                            page = "favorite"
                        } }) { Text("收藏") }
                    }
                } else if (page == "coin") {
                    items(listOf(1, 2)) { count ->
                        Button(
                            modifier = Modifier.fillMaxWidth().then(if (count == 1) Modifier.focusRequester(focus) else Modifier),
                            enabled = !busy, onClick = { runAction {
                                withContext(Dispatchers.IO) { coins.sendVideoCoin(aid = aid, multiply = count) }
                                "投币成功".toast(context)
                                page = "actions"
                            } }
                        ) { Text("投 $count 枚硬币") }
                    }
                } else {
                    if (folders.isEmpty()) item { Text("暂无收藏夹，请先创建收藏夹") }
                    items(folders, key = { it.id }) { folder ->
                        Button(modifier = Modifier.fillMaxWidth().then(
                            if (folder.id == folders.first().id) Modifier.focusRequester(focus) else Modifier
                        ), enabled = !busy,
                            onClick = { selected = if (folder.id in selected) selected - folder.id else selected + folder.id }
                        ) { Text("${if (folder.id in selected) "✓ " else ""}${folder.title}") }
                    }
                    item {
                        Button(modifier = Modifier.fillMaxWidth().then(
                            if (folders.isEmpty()) Modifier.focusRequester(focus) else Modifier
                        ), enabled = !busy,
                            onClick = { runAction {
                                val previous = folders.filter { it.videoInThisFav }.map { it.id }.toSet()
                                val add = (selected - previous).toList()
                                val remove = (previous - selected).toList()
                                if (add.isNotEmpty() || remove.isNotEmpty()) withContext(Dispatchers.IO) {
                                    favorites.updateVideoToFavoriteFolder(aid, add, remove, Prefs.apiType)
                                }
                                "收藏已更新".toast(context)
                                page = "actions"
                            } }
                        ) { Text("保存收藏") }
                    }
                }
                item {
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        onClick = { if (page == "actions") onDismiss() else page = "actions" }
                    ) { Text(if (page == "actions") "关闭" else "返回") }
                }
            }
        }
    }
}
