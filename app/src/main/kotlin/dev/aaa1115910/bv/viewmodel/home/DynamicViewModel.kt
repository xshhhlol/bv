package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.user.DynamicVideo
import dev.aaa1115910.biliapi.entity.user.FollowedUser
import dev.aaa1115910.biliapi.entity.user.SpaceVideo
import dev.aaa1115910.biliapi.entity.user.SpaceVideoPage
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.UserRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.BuildConfig
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.swapListWithMainContext
import dev.aaa1115910.bv.util.toWanString
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.aaa1115910.bv.repository.UserRepository as BvUserRepository
import org.koin.android.annotation.KoinViewModel

/** UP 主投稿最多缓存这么多份，关注几百个 UP 时全留着太占内存 */
private const val UpFeedCacheSize = 12

@KoinViewModel
class DynamicViewModel(
    private val bvUserRepository: BvUserRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    /** 侧栏「全部」栏目的内容：所有关注 UP 的最新投稿 */
    val dynamicList = mutableStateListOf<VideoCardData>()

    private var currentPage = 0
    var loading by mutableStateOf(false)
        private set

    var hasMore by mutableStateOf(true)
        private set

    private var historyOffset: String? = null
    private var updateBaseline: String? = null
    private var dynamicAttempted by mutableStateOf(false)

    /** 「全部」栏目正在跑的加载任务，刷新时先掐掉 */
    private var dynamicLoadJob: Job? = null

    /** 「全部」栏目的刷新代次，作用同 [UpVideoFeed.generation] */
    private var dynamicGeneration = 0

    val isLogin get() = bvUserRepository.isLogin

    /**
     * 当前栏目是否已经试着拉过第一页。
     *
     * 用来区分「还没开始加载」和「这一栏确实是空的」：前者铺骨架，后者才显示空提示，
     * 不然刚切过去的那一两帧会先闪一下「空空如也」。
     */
    val currentTabAttempted get() = currentUpFeed?.attempted ?: dynamicAttempted

    /** 侧栏里的关注列表，「全部」不在其中 */
    val followedUps = mutableStateListOf<FollowedUser>()
    var loadingUps by mutableStateOf(false)
        private set
    private var upsLoaded = false

    /** 侧栏当前选中的 UP，null 表示选中的是「全部」 */
    var selectedUp by mutableStateOf<FollowedUser?>(null)
        private set

    /** 当前选中 UP 的投稿，选中「全部」时为 null */
    var currentUpFeed by mutableStateOf<UpVideoFeed?>(null)
        private set

    /**
     * 每个栏目的滚动位置。
     *
     * 放在 ViewModel 里而不是页面里：动态页在顶部标签切走时会被销毁，位置留在页面里的话
     * 切回来就跳回顶部了，还得重新往下翻。
     */
    private val gridStates = mutableMapOf<Long?, LazyGridState>()

    fun gridStateOf(mid: Long?): LazyGridState = gridStates.getOrPut(mid) { LazyGridState() }

    /** 看过的 UP 投稿留一份，来回切栏目不用重新拉；按访问顺序淘汰最久没看的 */
    private val upFeedCache = object : LinkedHashMap<Long, UpVideoFeed>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, UpVideoFeed>) =
            size > UpFeedCacheSize
    }

    /** 拉关注列表，只会成功拉一次 */
    fun loadFollowedUps() {
        if (upsLoaded || loadingUps || !isLogin) return
        loadingUps = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                logger.fInfo { "Load followed ups" }
                userRepository.getFollowedUsers(
                    mid = Prefs.uid,
                    preferApiType = Prefs.apiType
                )
            }.onSuccess { ups ->
                logger.fInfo { "Loaded followed up count: ${ups.size}" }
                followedUps.swapListWithMainContext(ups)
                upsLoaded = true
            }.onFailure {
                if (it is CancellationException) throw it
                // 侧栏拉不到就只剩「全部」，不至于让整个页面不能用，所以不弹提示
                logger.fWarn { "Load followed ups failed: ${it.stackTraceToString()}" }
            }
            loadingUps = false
        }
    }

    /** 切到侧栏第一栏「全部」 */
    fun selectAll() {
        if (selectedUp == null) return
        selectedUp = null
        currentUpFeed = null
        ensureCurrentTabLoaded()
    }

    /** 切到某个 UP 的栏目 */
    fun selectUp(up: FollowedUser) {
        if (selectedUp?.mid == up.mid) return
        selectedUp = up
        currentUpFeed = upFeedCache.getOrPut(up.mid) { UpVideoFeed(up) }
        ensureCurrentTabLoaded()
    }

    /** 当前栏目还是空的就去拉第一页，已经有内容就什么都不做 */
    fun ensureCurrentTabLoaded() {
        if (!isLogin) return
        val feed = currentUpFeed
        if (feed == null) {
            if (!loading && dynamicList.isEmpty()) launchLoadData()
        } else {
            if (!feed.loading && !feed.loaded) launchLoadUpVideos(feed)
        }
    }

    private fun launchLoadData() {
        dynamicLoadJob = viewModelScope.launch(Dispatchers.IO) { loadData() }
    }

    private fun launchLoadUpVideos(feed: UpVideoFeed) {
        feed.loadJob = viewModelScope.launch(Dispatchers.IO) { loadUpVideos(feed) }
    }

    /** 给当前栏目翻页 */
    suspend fun loadMore() {
        val feed = currentUpFeed
        if (feed == null) {
            if (!loading) loadData()
        } else {
            loadUpVideos(feed)
        }
    }

    /** 重新加载当前栏目 */
    fun refresh() {
        val feed = currentUpFeed
        if (feed == null) {
            // clearData / reset 会取消在跑的请求并换掉 generation，
            // 掐不掉的那些（页面 scope 里直接调的 loadMore）拿回结果时也会被 generation 挡下来
            clearData()
            launchLoadData()
        } else {
            feed.reset()
            launchLoadUpVideos(feed)
        }
    }

    private suspend fun loadData() {
        // 置起 loading 时的代次，null 表示这次压根没置位，收尾时就什么都不用动。
        // 必须在主线程块里就记下来：withContext 恢复时会检查取消，块跑完了也可能抛出来，
        // 记在外面的话这次置位就没人负责复位了
        var generation: Int? = null
        var attempted = false

        try {
            // 分页状态一律在主线程上改：刷新也是从主线程来的，两边就天然串起来了。
            // 只在 IO 上校验 generation 挡不住「校验完、还没写回」这段时间里发生的刷新
            val request = withContext(Dispatchers.Main) {
                if (!hasMore || !bvUserRepository.isLogin) return@withContext null
                if (loading) return@withContext null
                loading = true
                val current = dynamicGeneration
                generation = current
                DynamicPageRequest(
                    generation = current,
                    page = currentPage + 1,
                    offset = historyOffset.orEmpty(),
                    updateBaseline = updateBaseline.orEmpty()
                )
            } ?: return

            logger.fInfo { "Load dynamic page: ${request.page}, offset=${request.offset}" }

            val data = userRepository.getDynamicVideos(
                page = request.page,
                offset = request.offset,
                updateBaseline = request.updateBaseline,
                preferApiType = Prefs.apiType
            )
            val videos = data.videos.map { it.toCardData() }
            attempted = true

            withContext(Dispatchers.Main) {
                // 拉的过程中被刷新过了，这份是过期数据，写回去会把新列表搞乱
                if (request.generation != dynamicGeneration) {
                    logger.fInfo { "Drop stale dynamic page=${request.page}" }
                    return@withContext
                }

                currentPage = request.page
                dynamicList.addAll(videos)
                historyOffset = data.historyOffset
                updateBaseline = data.updateBaseline
                hasMore = data.hasMore

                logger.fInfo { "Loaded page=$currentPage size=${videos.size}" }
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            attempted = true
            logger.fWarn { "Load dynamic failed: ${e.stackTraceToString()}" }

            when (e) {
                is AuthFailureException -> {
                    withContext(Dispatchers.Main) {
                        BVApp.context.getString(R.string.exception_auth_failure)
                            .toast(BVApp.context)
                    }
                    if (!BuildConfig.DEBUG) bvUserRepository.logout()
                }

                else -> {
                    withContext(Dispatchers.Main) {
                        "加载动态失败: ${e.localizedMessage}".toast(BVApp.context)
                    }
                }
            }

        } finally {
            // 被取消时也得复位 loading，所以这段不能跟着一起取消掉；
            // 刷新后新的加载已经接手了 loading，过期的这次别去动它
            val startedGeneration = generation
            if (startedGeneration != null) {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (startedGeneration == dynamicGeneration) {
                        loading = false
                        if (attempted) dynamicAttempted = true
                    }
                }
            }
        }
    }

    private suspend fun loadUpVideos(feed: UpVideoFeed) {
        // loadMore 跑在页面的 composition scope 里，切走 tab 会取消它。
        // loading 标记必须在 finally 里复位，否则这一栏会永远卡在骨架屏上再也加载不了；
        // 置位本身也要包在 try 里，withContext 恢复时抛取消一样会漏掉复位（同 loadData）
        var generation: Int? = null
        var attempted = false

        try {
            // 同 loadData：状态改动全放主线程，和刷新串行
            val start = withContext(Dispatchers.Main) {
                if (feed.loading || !feed.hasMore) return@withContext null
                feed.loading = true
                val current = feed.generation
                generation = current
                current to feed.page
            } ?: return
            val (startedGeneration, page) = start

            logger.fInfo { "Load up [${feed.up.mid}] videos, page=$page" }
            val data = userRepository.getSpaceVideos(
                mid = feed.up.mid,
                page = page,
                preferApiType = Prefs.apiType
            )
            val videos = data.videos.map { it.toCardData(feed.up) }
            attempted = true

            withContext(Dispatchers.Main) {
                // 拉的过程中被刷新过了，这份是过期数据，写回去会把第一页顶掉
                if (startedGeneration != feed.generation) {
                    logger.fInfo { "Drop stale videos of up [${feed.up.mid}]" }
                    return@withContext
                }

                feed.videos.addAll(videos)
                feed.page = data.page
                feed.loaded = true
                logger.fInfo { "Loaded up [${feed.up.mid}] videos size=${videos.size}" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempted = true
            logger.fWarn { "Load up [${feed.up.mid}] videos failed: ${e.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载 ${feed.up.name} 的投稿失败: ${e.localizedMessage}".toast(BVApp.context)
            }
        } finally {
            // 被取消时也得复位；被取消 / 过期的这次不算「试过了」，下次切回来还会再拉一遍
            val startedGeneration = generation
            if (startedGeneration != null) {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (startedGeneration == feed.generation) {
                        feed.loading = false
                        if (attempted) feed.attempted = true
                    }
                }
            }
        }
    }

    fun clearData() {
        dynamicLoadJob?.cancel()
        dynamicLoadJob = null
        dynamicGeneration++
        dynamicList.clear()
        currentPage = 0
        loading = false
        hasMore = true
        historyOffset = null
        updateBaseline = null
        dynamicAttempted = false
    }
}

/** 发起一次「全部」分页请求前，在主线程上一次性取好的参数，避免和刷新交错 */
private data class DynamicPageRequest(
    val generation: Int,
    val page: Int,
    val offset: String,
    val updateBaseline: String
)

/** 侧栏里一个 UP 主栏目的分页状态 */
class UpVideoFeed(val up: FollowedUser) {
    val videos = mutableStateListOf<VideoCardData>()
    var page by mutableStateOf(SpaceVideoPage())
    var loading by mutableStateOf(false)

    /** 是否已经成功拉到过一页，失败了下次切回来还能再试一次 */
    var loaded by mutableStateOf(false)

    /** 是否已经试着拉过一次，不管成没成 */
    var attempted by mutableStateOf(false)

    /** 正在跑的加载任务，刷新时先把它掐掉 */
    var loadJob: Job? = null

    /**
     * 每 [reset] 一次 +1。
     *
     * 请求发出去之后没法保证一定能取消（[DynamicViewModel.loadMore] 是在页面的 scope 里直接调的），
     * 所以写回结果前对一下这个号，刷新之前发出的响应直接丢掉，否则清空后的列表会被旧的第 N 页填上。
     */
    var generation = 0
        private set

    val hasMore get() = page.hasNext

    fun reset() {
        loadJob?.cancel()
        loadJob = null
        videos.clear()
        page = SpaceVideoPage()
        loaded = false
        attempted = false
        loading = false
        generation++
    }
}

private fun DynamicVideo.toCardData() = VideoCardData(
    avid = aid,
    epId = epid,
    title = title,
    cover = cover,
    upName = author,
    upMid = authorMid,
    playString = play.takeIf { it != -1 }.toWanString(),
    danmakuString = danmaku.takeIf { it != -1 }.toWanString(),
    timeString = (duration * 1000L).formatHourMinSec(),
    pubTime = pubTime
)

private fun SpaceVideo.toCardData(up: FollowedUser) = VideoCardData(
    avid = aid,
    title = title,
    cover = cover,
    // app 端接口有时候不返回作者名，反正这一栏就是这个 UP 的投稿，直接拿侧栏的名字兜底
    upName = author.ifBlank { up.name },
    upMid = up.mid,
    playString = play.takeIf { it != -1 }.toWanString(),
    danmakuString = danmaku.takeIf { it != -1 }.toWanString(),
    timeString = (duration * 1000L).formatHourMinSec(),
    pubTime = pubTime
)
