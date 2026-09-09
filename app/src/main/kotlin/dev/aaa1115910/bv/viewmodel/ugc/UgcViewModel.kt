package dev.aaa1115910.bv.viewmodel.ugc

import androidx.compose.foundation.lazy.grid.LazyGridState

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ugc.region.UgcFeedPage
import dev.aaa1115910.biliapi.repositories.UgcRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.component.UgcTopNavItem
import dev.aaa1115910.bv.screen.main.ugc.UgcScaffoldState
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UgcViewModel(private val ugcRepository: UgcRepository) : ViewModel() {
    private val logger = KotlinLogging.logger("UgcViewModel")

    private val _ugcScaffoldStateMap = mutableMapOf<UgcTopNavItem, UgcScaffoldState>()
    val ugcScaffoldStateMap: Map<UgcTopNavItem, UgcScaffoldState> get() = _ugcScaffoldStateMap

    fun addUgcScaffoldState(item: UgcTopNavItem, state: UgcScaffoldState) {
        _ugcScaffoldStateMap[item] = state
        launchWithIO { initUgcRegionData(item) }
    }

    /**
     * 取某个分区的状态，没有就建一个。
     *
     * 之前这一步是在 AnimatedContent 的内容里用 for 循环 + 条件调用 `rememberLazyGridState()` 做的——
     * remember 的槽位数量会跟着条件变，是会把 Compose 的 slot table 搞乱的写法，而且每次组合都要跑一遍循环。
     * 状态本来就该活得比页面久，直接放这儿建。
     */
    fun scaffoldStateOf(item: UgcTopNavItem): UgcScaffoldState =
        _ugcScaffoldStateMap[item] ?: UgcScaffoldState(
            lazyGridState = LazyGridState(),
            ugcType = item.ugcTypeV2
        ).also { addUgcScaffoldState(item, it) }

    fun reloadAll(item: UgcTopNavItem) {
        _ugcScaffoldStateMap[item]?.let { state ->
            logger.fInfo { "reload all ${state.ugcType} data" }
            state.nextPage = UgcFeedPage()
            state.hasMore = true
            state.ugcItems.clear()

            if (!state.updating) {
                launchWithIO { initUgcRegionData(item) }
            } else {
                Log.d("UgcViewModel", "正在更新中")
            }
        }
    }

    fun loadMoreData(item: UgcTopNavItem) {
        launchWithIO { loadData(item, isInit = false) }
    }

    private suspend fun initUgcRegionData(item: UgcTopNavItem) {
        _ugcScaffoldStateMap[item]?.let {
            loadData(item, isInit = true)
        }
    }

    private suspend fun loadData(item: UgcTopNavItem, isInit: Boolean) {
        val state = _ugcScaffoldStateMap[item] ?: return
        if (!state.hasMore || state.updating) return

        state.updating = true
        try {
            if (isInit) {
                logger.fInfo { "load ugc ${state.ugcType} region data" }
                val feedData = ugcRepository.getRegionFeedRcmd(state.ugcType, state.nextPage)

                state.ugcItems.clear()
                state.ugcItems.addAll(feedData.items)
                state.nextPage = feedData.nextPage
                state.hasMore = true
            } else {
                logger.fInfo { "load more ${state.ugcType} region data" }
                val feedData = ugcRepository.getRegionFeedRcmd(state.ugcType, state.nextPage)
                state.ugcItems.addAll(feedData.items)
                state.nextPage = feedData.nextPage
                state.hasMore = feedData.items.isNotEmpty()
            }
        } catch (e: Exception) {
            logger.fInfo { "load ${state.ugcType} data failed: ${e.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                val msg = if (isInit) "加载 ${state.ugcType} 数据失败" else "加载 ${state.ugcType} 更多推荐失败"
                "$msg: ${e.message}".toast(BVApp.context)
            }
        } finally {
            state.updating = false
        }
    }

    private fun launchWithIO(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
        }
    }
}
