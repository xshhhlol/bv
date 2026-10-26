package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.reply.CommentSort
import dev.aaa1115910.biliapi.http.BiliHttpApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CommentRepositoryTest {
    companion object {
        private val authRepository = AuthRepository()
        private val channelRepository = ChannelRepository()
        private val commentRepository = CommentRepository(authRepository, channelRepository)

        // JUnit 每个用例都会新建实例，init 放在这里只跑一次：
        // 再次 init 会清空刚取到的 buvid3，而指纹接口 10 分钟内不会重取，之后的 web 请求就会被 -352
        init {
            BiliHttpApi.init(buvid3 = "")
            channelRepository.initDefaultChannel(
                FavoriteRepositoryTest.ACCESS_TOKEN,
                FavoriteRepositoryTest.BUVID
            )
            authRepository.sessionData = FavoriteRepositoryTest.SESSDATA
        }
    }

    private val aid = 170001L

    @Test
    fun `get comments with web api`() = runBlocking {
        CommentSort.entries.forEach { sort ->
            val first = commentRepository.getComments(aid, sort, 0, ApiType.Web)
            println("$sort first: total=${first.totalCount}, next=${first.nextCursor}, size=${first.comments.size}")
            first.comments.take(3).forEach { println("  ${it.member.name}: ${it.content.message.take(40)}") }
            val second = commentRepository.getComments(aid, sort, first.nextCursor, ApiType.Web)
            println("$sort second: next=${second.nextCursor}, size=${second.comments.size}")
        }
    }

    @Test
    fun `get comment replies with web api`() = runBlocking {
        val root = commentRepository.getComments(aid, CommentSort.Hot, 0, ApiType.Web)
            .comments.first { it.replyCount > 0 }
        val first = commentRepository.getCommentReplies(aid, root.rpid, 0, ApiType.Web)
        println("root=${first.root?.member?.name}, total=${first.totalCount}, next=${first.nextCursor}, size=${first.replies.size}")
        val second = commentRepository.getCommentReplies(aid, root.rpid, first.nextCursor, ApiType.Web)
        println("second: next=${second.nextCursor}, size=${second.replies.size}")
    }

    @Test
    fun `get comments with app api`() = runBlocking {
        val first = commentRepository.getComments(aid, CommentSort.Hot, 0, ApiType.App)
        println("first: total=${first.totalCount}, next=${first.nextCursor}, size=${first.comments.size}, upMid=${first.upMid}, top=${first.topComments.size}")
        first.comments.take(3).forEach {
            println("  ${it.member.name}(lv${it.member.level}, vip=${it.member.isVip}, avatar=${it.member.avatar.isNotBlank()}): ${it.content.message.take(40)}")
            println("    like=${it.likeCount}, replies=${it.replyCount}, time=${it.timeDesc}, location=${it.location}, preview=${it.previewReplies.size}")
        }
        println("  emotes=${first.comments.sumOf { it.content.emotes.size }}, pictures=${first.comments.sumOf { it.content.pictures.size }}")
        val second = commentRepository.getComments(aid, CommentSort.Hot, first.nextCursor, ApiType.App)
        println("second: next=${second.nextCursor}, size=${second.comments.size}")
    }

    @Test
    fun `get comment replies with app api`() = runBlocking {
        val root = commentRepository.getComments(aid, CommentSort.Hot, 0, ApiType.App)
            .comments.first { it.replyCount > 0 }
        val first = commentRepository.getCommentReplies(aid, root.rpid, 0, ApiType.App)
        println("root=${first.root?.member?.name}, total=${first.totalCount}, next=${first.nextCursor}, size=${first.replies.size}")
        val second = commentRepository.getCommentReplies(aid, root.rpid, first.nextCursor, ApiType.App)
        println("second: next=${second.nextCursor}, size=${second.replies.size}")
    }
}
