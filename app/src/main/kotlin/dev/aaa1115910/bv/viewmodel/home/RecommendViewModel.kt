package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.home.DislikeType
import dev.aaa1115910.biliapi.entity.home.RecommendPage
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.repositories.RecommendVideoRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.addAllWithMainContext
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class RecommendViewModel(
    private val recommendVideoRepository: RecommendVideoRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}
    val recommendVideoList = mutableStateListOf<UgcItem>()

    private var nextPage = RecommendPage()
    var refreshing by mutableStateOf(true)
    var loading by mutableStateOf(false)

    suspend fun loadMore(
        beforeAppendData: () -> Unit = {}
    ) {
        var loadCount = 0
        val maxLoadMoreCount = 3
        if (!loading) {
            if (recommendVideoList.size == 0) {
                // first load data
                while (recommendVideoList.size < 24 && loadCount < maxLoadMoreCount) {
                    val emptyFun: () -> Unit = {}
                    if (!loadData(beforeAppendData = if (loadCount == 0) beforeAppendData else emptyFun)) break
                    if (loadCount != 0) logger.fInfo { "Load more recommend videos because items too less" }
                    loadCount++
                }
            } else {
                val emptyFun: () -> Unit = {}
                loadData(beforeAppendData = if (loadCount == 0) beforeAppendData else emptyFun)
            }
        }
    }

    private suspend fun loadData(
        beforeAppendData: () -> Unit
    ): Boolean {
        loading = true
        logger.fInfo { "Load more recommend videos" }
        val result = runCatching {
            val recommendData = recommendVideoRepository.getRecommendVideos(
                page = nextPage,
                preferApiType = Prefs.apiType
            )
            beforeAppendData()
            nextPage = recommendData.nextPage
            recommendVideoList.addAllWithMainContext(recommendData.items)
        }.onFailure {
            loading = false
            if (it is CancellationException) throw it
            logger.fError { "Load recommend video list failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载推荐视频失败: ${it.localizedMessage}".toast(BVApp.context)
            }
        }
        loading = false
        return result.isSuccess
    }

    fun dislike(item: UgcItem, type: DislikeType) {
        viewModelScope.launch(Dispatchers.IO) {
            logger.fInfo { "Dislike recommend video [aid=${item.aid}, type=$type]" }
            runCatching {
                recommendVideoRepository.dislikeRecommendVideo(item, type)
            }.onSuccess { toast ->
                withContext(Dispatchers.Main) {
                    // 不想看这个 UP 就把列表里这个 UP 的视频一起拿掉，不然刚点完，往后翻几张又是同一个人
                    if (type == DislikeType.Up && item.authorMid != null) {
                        recommendVideoList.removeAll { it.authorMid == item.authorMid }
                    } else {
                        recommendVideoList.removeAll { it.aid == item.aid }
                    }
                    (toast?.takeIf { it.isNotBlank() } ?: "将减少相似内容推荐").toast(BVApp.context)
                }
            }.onFailure {
                if (it is CancellationException) throw it
                logger.fWarn { "Dislike recommend video failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    "操作失败: ${it.localizedMessage}".toast(BVApp.context)
                }
            }
        }
    }

    fun clearData() {
        recommendVideoList.clear()
        resetPage()
        loading = false
    }

    fun resetPage() {
        nextPage = RecommendPage()
        refreshing = true
    }
}