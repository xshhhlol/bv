package dev.aaa1115910.bv.component.controllers.comment

import dev.aaa1115910.bv.component.controllers.comment.CommentSegment.Emote
import dev.aaa1115910.bv.component.controllers.comment.CommentSegment.Mention
import dev.aaa1115910.bv.component.controllers.comment.CommentSegment.Plain
import dev.aaa1115910.bv.component.controllers.comment.CommentSegment.ReplyPrefix
import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.entity.reply.CommentContent
import dev.aaa1115910.biliapi.entity.reply.CommentMember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CommentTextParserTest {
    private val emotes = mapOf(
        "[doge]" to "https://i0.hdslb.com/doge.png",
        "[笑哭]" to "https://i0.hdslb.com/xk.png"
    )

    @Test
    fun `known emotes become images and unknown brackets stay text`() {
        assertEquals(
            listOf(Plain("前面"), Emote("[doge]", "https://i0.hdslb.com/doge.png"), Plain("[不存在]后面"), Emote("[笑哭]", "https://i0.hdslb.com/xk.png")),
            parseCommentText("前面[doge][不存在]后面[笑哭]", emotes, emptyList())
        )
    }

    @Test
    fun `mentions only match listed names and prefer the longest one`() {
        assertEquals(
            listOf(Plain("看 "), Mention("小明同学"), Plain(" 和 test@example.com")),
            parseCommentText("看 @小明同学 和 test@example.com", emptyMap(), listOf("小明", "小明同学"))
        )
    }

    @Test
    fun `reply prefix is highlighted even if the name is not in the mention list`() {
        assertEquals(
            listOf(ReplyPrefix, Mention("韩小魔-筑基期"), Plain(" :你没有胜算的"), Emote("[doge]", "https://i0.hdslb.com/doge.png")),
            parseCommentText("回复 @韩小魔-筑基期 :你没有胜算的[doge]", emotes, emptyList())
        )
    }

    @Test
    fun `adjacent brackets still find the emote`() {
        assertEquals(
            listOf(Plain("["), Emote("[doge]", "https://i0.hdslb.com/doge.png")),
            parseCommentText("[[doge]", emotes, emptyList())
        )
    }

    @Test
    @org.junit.jupiter.api.Timeout(5)
    fun `lots of unclosed brackets are scanned only once`() {
        // 每个 [ 都一路找到末尾的 ] 的话，这里是 O(n²)，要跑好几秒
        val message = "[".repeat(500_000) + "[doge]"
        val segments = parseCommentText(message, emotes, emptyList())
        assertEquals(listOf(Plain("[".repeat(500_000)), Emote("[doge]", "https://i0.hdslb.com/doge.png")), segments)
    }

    @Test
    fun `emote length limit preserves surrounding text and later valid emotes`() {
        val accepted = "[" + "a".repeat(30) + "]"
        val tooLong = "[" + "b".repeat(31) + "]"
        val dictionary = emotes + mapOf(accepted to "accepted", tooLong to "tooLong")
        assertEquals(
            listOf(Emote(accepted, "accepted"), Plain(tooLong), Emote("[doge]", emotes.getValue("[doge]"))),
            parseCommentText(accepted + tooLong + "[doge]", dictionary, emptyList())
        )
    }

    @Test
    fun `prepared text dedupes emotes and keeps inline placeholders`() {
        val content = CommentContent(
            message = "[doge][doge] @小明 [笑哭]",
            emotes = emotes,
            pictures = emptyList(),
            mentions = listOf("小明")
        )
        val prepared = prepareCommentText(content)

        assertEquals(listOf("https://i0.hdslb.com/doge.png", "https://i0.hdslb.com/xk.png"), prepared.emotes.map { it.url })
        // 表情在文字里用原文占位，图片加载不出来时显示原文
        assertEquals("[doge][doge] @小明 [笑哭]", prepared.text.text)
        assertEquals(3, prepared.text.getStringAnnotations(0, prepared.text.length).size)
    }

    @Test
    fun `cache returns the same prepared text and warms preview replies`() {
        val preview = comment(2, "回复 @a :[doge]")
        val root = comment(1, "楼主 [笑哭]").copy(previewReplies = listOf(preview))

        CommentTextCache.warm(listOf(root))

        assertSame(CommentTextCache.get(root.content), CommentTextCache.get(root.content))
        assertSame(CommentTextCache.get(preview.content), CommentTextCache.get(preview.content))
        assertEquals(1, CommentTextCache.get(preview.content).emotes.size)
    }

    private fun comment(rpid: Long, message: String) = Comment(
        rpid = rpid, oid = 1, root = 0, parent = 0,
        member = CommentMember(mid = 1, name = "a", avatar = "", level = 6, isVip = false, officialType = -1),
        content = CommentContent(message = message, emotes = emotes, pictures = emptyList(), mentions = emptyList()),
        likeCount = 0, replyCount = 0, publishTime = 0, timeDesc = "", location = null,
        isTop = false, upLiked = false, previewReplies = emptyList()
    )

    @Test
    fun `unclosed bracket and plain text are kept as is`() {
        assertEquals(listOf(Plain("[doge 没闭合 @某人")), parseCommentText("[doge 没闭合 @某人", emotes, emptyList()))
        assertEquals(emptyList(), parseCommentText("", emotes, emptyList()))
    }
}
