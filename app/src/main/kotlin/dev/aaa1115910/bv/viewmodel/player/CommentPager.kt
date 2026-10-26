package dev.aaa1115910.bv.viewmodel.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.aaa1115910.biliapi.entity.reply.Comment
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class CommentLoadState {
    /** 还有下一页，当前没有在请求 */
    Idle,
    Loading,
    Error,

    /** 已经没有下一页了 */
    End
}

/**
 * @param nextCursor 下一页的游标，下次请求原样传回去
 * @param totalCount 总条数，用在标题上
 * @param upMid UP 主 mid，给 UP 自己的评论打标
 */
data class CommentPageResult(
    val items: List<Comment>,
    val nextCursor: Long,
    val isEnd: Boolean,
    val totalCount: Int,
    val upMid: Long
)

/**
 * 评论的分页加载，一级评论和楼中楼共用
 *
 * 同一时间只跑一个请求。[reset] 之后还没回来的旧请求结果直接丢掉，
 * 否则切换排序或视频时，旧的那一页会混进新列表里
 *
 * @param fetch 按游标取一页，第一页的游标是 0
 */
class CommentPager(
    private val scope: CoroutineScope,
    private val fetch: suspend (cursor: Long) -> CommentPageResult
) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    val items = mutableStateListOf<Comment>()
    var state by mutableStateOf(CommentLoadState.Idle)
        private set
    var totalCount by mutableIntStateOf(0)
        private set
    var upMid by mutableLongStateOf(0L)
        private set

    private var cursor = 0L
    private var generation = 0
    private var job: Job? = null

    // 热门排序翻页时偶尔会带回上一页已经有的评论，重复的 key 会让 LazyColumn 直接崩掉
    private val loadedIds = HashSet<Long>()

    /** 加载下一页。正在加载、出错或者已经到底时什么都不做 */
    fun loadMore() {
        if (state != CommentLoadState.Idle) return
        state = CommentLoadState.Loading
        val requestGeneration = generation
        val requestCursor = cursor
        job = scope.launch {
            try {
                val result = fetch(requestCursor)
                if (requestGeneration != generation) return@launch
                items.addAll(result.items.filter { loadedIds.add(it.rpid) })
                cursor = result.nextCursor
                totalCount = result.totalCount
                upMid = result.upMid
                state = if (result.isEnd) CommentLoadState.End else CommentLoadState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration != generation) return@launch
                logger.warn { "Load comments failed: ${e.stackTraceToString()}" }
                state = CommentLoadState.Error
            }
        }
    }

    /** 上一页出错时重新请求同一页 */
    fun retry() {
        if (state != CommentLoadState.Error) return
        state = CommentLoadState.Idle
        loadMore()
    }

    /** 清空列表，下次 [loadMore] 从第一页开始 */
    fun reset() {
        generation++
        job?.cancel()
        job = null
        items.clear()
        loadedIds.clear()
        cursor = 0
        totalCount = 0
        upMid = 0
        state = CommentLoadState.Idle
    }
}
