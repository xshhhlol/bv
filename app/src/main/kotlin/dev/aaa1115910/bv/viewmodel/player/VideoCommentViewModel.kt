package dev.aaa1115910.bv.viewmodel.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.entity.reply.CommentSort
import dev.aaa1115910.biliapi.repositories.CommentRepository
import dev.aaa1115910.bv.component.controllers.comment.CommentTextCache
import dev.aaa1115910.bv.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

/**
 * 播放器里的评论区
 *
 * 只在打开评论抽屉时才去加载；关掉再打开同一个视频不会重新请求，列表和位置都还在
 */
@KoinViewModel
class VideoCommentViewModel(
    private val commentRepository: CommentRepository
) : ViewModel() {
    var aid = 0L
        private set
    var sort by mutableStateOf(CommentSort.Hot)
        private set

    /** 正在查看楼中楼的那条评论，为 null 时显示一级评论 */
    var replyRoot by mutableStateOf<Comment?>(null)
        private set

    val comments = CommentPager(viewModelScope) { cursor ->
        val page = withContext(Dispatchers.IO) {
            commentRepository.getComments(aid, sort, cursor, apiType)
        }
        val items = page.topComments + page.comments
        // 进列表前先在后台把正文拆好，滑到哪条都不用在界面线程上现拆
        withContext(Dispatchers.Default) { CommentTextCache.warm(items) }
        CommentPageResult(
            items = items,
            nextCursor = page.nextCursor,
            isEnd = page.isEnd,
            totalCount = page.totalCount,
            upMid = page.upMid
        )
    }

    val replies = CommentPager(viewModelScope) { cursor ->
        val root = replyRoot ?: error("reply root is null")
        // 没有回复的评论点进去只是为了看完整正文，不用请求
        if (root.replyCount == 0) {
            return@CommentPager CommentPageResult(emptyList(), 0, isEnd = true, totalCount = 0, upMid = comments.upMid)
        }
        val page = withContext(Dispatchers.IO) {
            commentRepository.getCommentReplies(aid, root.rpid, cursor, apiType)
        }
        withContext(Dispatchers.Default) { CommentTextCache.warm(page.replies) }
        CommentPageResult(
            items = page.replies,
            nextCursor = page.nextCursor,
            isEnd = page.isEnd,
            totalCount = page.totalCount,
            upMid = page.upMid
        )
    }

    // web 接口对未登录用户只给 3 条评论、楼中楼只给第一页，gRPC 未登录也能正常翻页
    private val apiType: ApiType
        get() = if (Prefs.isLogin) Prefs.apiType else ApiType.App

    /** 打开评论区时调用，换了视频才会重新加载 */
    fun bind(aid: Long) {
        if (this.aid == aid) {
            if (comments.items.isEmpty()) comments.retry()
            return
        }
        this.aid = aid
        sort = CommentSort.Hot
        closeReplies()
        comments.reset()
        comments.loadMore()
    }

    fun changeSort(sort: CommentSort) {
        if (this.sort == sort) return
        this.sort = sort
        comments.reset()
        comments.loadMore()
    }

    fun openReplies(root: Comment) {
        replyRoot = root
        replies.reset()
        replies.loadMore()
    }

    fun closeReplies() {
        replyRoot = null
        replies.reset()
    }
}
