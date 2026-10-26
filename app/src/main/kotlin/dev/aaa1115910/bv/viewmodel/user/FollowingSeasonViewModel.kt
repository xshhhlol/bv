package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.season.FollowingSeason
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonStatus
import dev.aaa1115910.biliapi.entity.season.FollowingSeasonType
import dev.aaa1115910.biliapi.repositories.SeasonRepository
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class FollowingSeasonViewModel(
    private val seasonRepository: SeasonRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    val followingSeasons = mutableStateListOf<FollowingSeason>()
    var followingSeasonType = FollowingSeasonType.Bangumi
    var followingSeasonStatus = FollowingSeasonStatus.All

    private var pageNumber = 1
    private val pageSize = 20
    var noMore by mutableStateOf(false)
    private var updating = false

    private var updateJob: Job? = null

    // Activity 重建时 ViewModel 会保留下来，不能再用启动参数覆盖用户在筛选里的选择
    private var initialTypeApplied = false

    init {
        followingSeasonType = FollowingSeasonType.Bangumi
        followingSeasonStatus = FollowingSeasonStatus.All
    }

    /**
     * 应用进入页面时指定的追剧类型，只在 ViewModel 首次创建时生效
     */
    fun applyInitialType(type: FollowingSeasonType?) {
        if (initialTypeApplied) return
        initialTypeApplied = true
        type?.let { followingSeasonType = it }
    }

    fun clearData() {
        updateJob?.cancel()
        pageNumber = 1
        updating = false
        noMore = false
        followingSeasons.clear()
    }

    fun loadMore() {
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            updateData()
        }
    }

    private suspend fun updateData() {
        if (updating) return
        updating = true
        runCatching {
            logger.fInfo { "Updating following season data" }
            val response = seasonRepository.getFollowingSeasons(
                type = followingSeasonType,
                status = followingSeasonStatus,
                pageNumber = pageNumber,
                pageSize = pageSize,
                preferApiType = Prefs.apiType
            )
            withContext(Dispatchers.Main) {
                if (pageSize * pageNumber >= response.total) noMore = true
                pageNumber++
                followingSeasons.addAll(response.list)
            }
            logger.fInfo { "Following season count: ${response.list.size}" }
        }.onFailure {
            logger.fInfo { "Update following seasons failed: ${it.stackTraceToString()}" }
        }
        updating = false
    }
}

