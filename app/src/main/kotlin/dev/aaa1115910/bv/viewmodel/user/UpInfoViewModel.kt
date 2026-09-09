package dev.aaa1115910.bv.viewmodel.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.toast
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.user.SpaceVideoPage
import dev.aaa1115910.biliapi.entity.user.UserCard
import dev.aaa1115910.biliapi.repositories.UserRepository
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.addWithMainContext
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.formatHourMinSec
import dev.aaa1115910.bv.util.toWanString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UpInfoViewModel(
    private val userRepository: UserRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var upName by mutableStateOf("")
    var upMid by mutableLongStateOf(0L)
    var spaceVideos = mutableStateListOf<VideoCardData>()

    /** UP 主名片：粉丝数、简介等，取不到时为 null，页面上直接不显示 */
    var userCard by mutableStateOf<UserCard?>(null)
        private set

    var changingFollow by mutableStateOf(false)
        private set

    fun toggleFollow() {
        val card = userCard ?: return
        if (changingFollow || card.mid <= 0 || card.mid == Prefs.uid) return
        if (!Prefs.isLogin) {
            "请先登录".toast(BVApp.context)
            return
        }
        changingFollow = true
        viewModelScope.launch {
            try {
                val follow = !card.isFollowing
                val success = withContext(Dispatchers.IO) {
                    if (follow) userRepository.followUser(card.mid, Prefs.apiType)
                    else userRepository.unfollowUser(card.mid, Prefs.apiType)
                }
                check(success) { "操作失败，请稍后重试" }
                if (userCard?.mid == card.mid) {
                    userCard = userCard?.copy(isFollowing = follow)
                }
                (if (follow) "关注成功" else "已取消关注").toast(BVApp.context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                (e.message ?: "操作失败，请稍后重试").toast(BVApp.context)
            } finally {
                changingFollow = false
            }
        }
    }

    private var page = SpaceVideoPage()

    /** 已经进过列表的 avid，用来去重 */
    private val loadedAids = mutableSetOf<Long>()
    private var updating = false
    private var userCardRequested = false
    val noMore get() = !page.hasNext

    fun update() {
        updateUserCard()
        // 抢占标记必须在这里（主线程）打，不能等协程起来再判断：
        // 进页面时 LaunchedEffect 和列表触底监听会几乎同时调进来，两个协程都读到 updating == false
        // 就会把同一页拉两遍，列表里出现重复的 avid，而网格是拿 avid 当 key 的，直接抛异常闪退
        if (updating || noMore) return
        updating = true
        viewModelScope.launch(Dispatchers.Default) {
            updateSpaceVideos()
        }
    }

    /** 名片只需要拉一次，翻页时重复调用 [update] 不会重复请求 */
    private fun updateUserCard() {
        if (userCardRequested || upMid == 0L) return
        userCardRequested = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val card = userRepository.getUserCard(upMid)
                if (Prefs.isLogin && Prefs.apiType == dev.aaa1115910.biliapi.entity.ApiType.App) {
                    val following = userRepository.checkIsFollowing(upMid, Prefs.apiType)
                        ?: error("无法获取关注状态，请稍后重试")
                    card.copy(isFollowing = following)
                } else card
            }.onSuccess { card ->
                withContext(Dispatchers.Main) {
                    userCard = card
                    if (upName.isBlank()) upName = card.name
                }
                logger.fInfo { "Update up [mid=$upMid] card success" }
            }.onFailure {
                if (it is CancellationException) throw it
                // 名片只是锦上添花，失败了不打扰用户，页面照常显示视频列表
                userCardRequested = false
                logger.fInfo { "Update up card failed: ${it.stackTraceToString()}" }
            }
        }
    }

    private suspend fun updateSpaceVideos() {
        logger.fInfo { "Updating up [mid=$upMid] space videos from page $page" }
        runCatching {
            val spaceVideoData = userRepository.getSpaceVideos(
                mid = upMid,
                page = page,
                preferApiType = Prefs.apiType
            )
            spaceVideoData.videos.forEach { spaceVideoItem ->
                // 翻页期间 UP 主发了新稿的话，接口会把同一个视频在两页里都返回一次；
                // 这里去个重，别让重复的 avid 进到列表里
                if (!loadedAids.add(spaceVideoItem.aid)) return@forEach
                spaceVideos.addWithMainContext(
                    VideoCardData(
                        avid = spaceVideoItem.aid,
                        title = spaceVideoItem.title,
                        //TODO 这里在改造 app 端接口时，没找到在空间内显示为合集样式封面的UP,没法进一步测试接口
                        cover = spaceVideoItem.cover,
                        upName = spaceVideoItem.author,
                        playString = spaceVideoItem.play.takeIf { it != -1 }.toWanString(),
                        danmakuString = spaceVideoItem.danmaku.takeIf { it != -1 }.toWanString(),
                        timeString = (spaceVideoItem.duration * 1000L).formatHourMinSec(),
                        pubTime = spaceVideoItem.pubTime
                    )
                )
            }
            page = spaceVideoData.page
            logger.fInfo { "Update up space videos success" }
        }.onFailure {
            if (it is CancellationException) throw it
            withContext(Dispatchers.Main) {
                ("UP 主视频加载失败：" + (it.message ?: "请稍后重试")).toast(BVApp.context)
            }
            logger.fInfo { "Update up space videos failed: ${it.stackTraceToString()}" }
        }
        // 回主线程再放开标记，和 update() 里的抢占读写在同一个线程上，不用担心可见性
        withContext(Dispatchers.Main) { updating = false }
    }
}