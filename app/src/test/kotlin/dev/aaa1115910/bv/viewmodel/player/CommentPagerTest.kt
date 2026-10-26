package dev.aaa1115910.bv.viewmodel.player

import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.entity.reply.CommentContent
import dev.aaa1115910.biliapi.entity.reply.CommentMember
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentPagerTest {
    private fun comment(rpid: Long) = Comment(
        rpid = rpid,
        oid = 1,
        root = 0,
        parent = 0,
        member = CommentMember(mid = 1, name = "user", avatar = "", level = 6, isVip = false, officialType = -1),
        content = CommentContent(message = "comment $rpid", emotes = emptyMap(), pictures = emptyList(), mentions = emptyList()),
        likeCount = 0,
        replyCount = 0,
        publishTime = 0,
        timeDesc = "",
        location = null,
        isTop = false,
        upLiked = false,
        previewReplies = emptyList()
    )

    private fun page(vararg rpids: Long, next: Long, isEnd: Boolean = false) = CommentPageResult(
        items = rpids.map { comment(it) },
        nextCursor = next,
        isEnd = isEnd,
        totalCount = 100,
        upMid = 42
    )

    @Test
    fun `pages are appended with the returned cursor until the end`() = runBlocking {
        val requestedCursors = mutableListOf<Long>()
        val pages = ArrayDeque(listOf(page(1, 2, next = 2), page(3, 4, next = 3, isEnd = true)))
        val pager = CommentPager(this) { cursor ->
            requestedCursors.add(cursor)
            pages.removeFirst()
        }

        pager.loadMore()
        yield()
        assertEquals(CommentLoadState.Idle, pager.state)
        assertEquals(100, pager.totalCount)
        assertEquals(42, pager.upMid)

        pager.loadMore()
        yield()
        assertEquals(listOf(1L, 2L, 3L, 4L), pager.items.map { it.rpid })
        assertEquals(listOf(0L, 2L), requestedCursors)
        assertEquals(CommentLoadState.End, pager.state)

        // 到底之后不再请求
        pager.loadMore()
        yield()
        assertEquals(2, requestedCursors.size)
    }

    @Test
    fun `duplicated comments across pages are dropped`() = runBlocking {
        val pages = ArrayDeque(listOf(page(1, 2, next = 2), page(2, 3, next = 3)))
        val pager = CommentPager(this) { pages.removeFirst() }

        pager.loadMore()
        yield()
        pager.loadMore()
        yield()

        assertEquals(listOf(1L, 2L, 3L), pager.items.map { it.rpid })
    }

    @Test
    fun `only one request runs at a time`() = runBlocking {
        val response = CompletableDeferred<CommentPageResult>()
        var requestCount = 0
        val pager = CommentPager(this) {
            requestCount++
            response.await()
        }

        pager.loadMore()
        pager.loadMore()
        yield()
        assertEquals(CommentLoadState.Loading, pager.state)
        response.complete(page(1, next = 2))
        yield()

        assertEquals(1, requestCount)
        assertEquals(listOf(1L), pager.items.map { it.rpid })
    }

    @Test
    fun `result of a request started before reset is discarded`() = runBlocking {
        val staleResponse = CompletableDeferred<CommentPageResult>()
        val responses = ArrayDeque(listOf(staleResponse, CompletableDeferred(page(10, next = 11))))
        val pager = CommentPager(this) {
            val response = responses.removeFirst()
            // 模拟请求已经回来、协程来不及响应取消的情况
            withContext(NonCancellable) { response.await() }
        }

        pager.loadMore()
        yield()
        pager.reset()
        pager.loadMore()
        yield()
        staleResponse.complete(page(1, 2, next = 3))
        yield()
        yield()

        assertEquals(listOf(10L), pager.items.map { it.rpid })
        assertEquals(CommentLoadState.Idle, pager.state)
    }

    @Test
    fun `failed page can be retried with the same cursor`() = runBlocking {
        val requestedCursors = mutableListOf<Long>()
        var fail = true
        val pager = CommentPager(this) { cursor ->
            requestedCursors.add(cursor)
            if (fail) error("network error")
            page(1, next = 2)
        }

        pager.loadMore()
        yield()
        assertEquals(CommentLoadState.Error, pager.state)
        // 出错后滚动到底部不会自动重试，只能手动点重试
        pager.loadMore()
        yield()
        assertEquals(1, requestedCursors.size)

        fail = false
        pager.retry()
        yield()
        assertEquals(listOf(0L, 0L), requestedCursors)
        assertEquals(CommentLoadState.Idle, pager.state)
        assertTrue(pager.items.isNotEmpty())
    }
}
