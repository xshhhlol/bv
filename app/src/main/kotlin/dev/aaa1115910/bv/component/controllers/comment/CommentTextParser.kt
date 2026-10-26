package dev.aaa1115910.bv.component.controllers.comment

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.entity.reply.CommentContent
import dev.aaa1115910.bv.ui.theme.BVColor

/**
 * 评论正文拆出来的片段
 */
sealed interface CommentSegment {
    data class Plain(val text: String) : CommentSegment

    /** @param text 表情的文本，例如 "[doge]"，图片没加载出来时显示它 */
    data class Emote(val text: String, val url: String) : CommentSegment

    /** @param name 不带 @ 的用户名 */
    data class Mention(val name: String) : CommentSegment

    /** 楼中楼回复别人时开头的「回复 」 */
    data object ReplyPrefix : CommentSegment
}

private const val REPLY_PREFIX = "回复 @"
private const val REPLY_PREFIX_END = " :"

// 表情文本最长也就十来个字，限制一下省得遇到落单的 [ 一路扫到末尾
private const val MAX_EMOTE_LENGTH = 32

/**
 * 把评论正文拆成普通文本、表情和 @
 *
 * 只认 [emotes] 里有的表情和 [mentions] 里有的用户名，正文里随手打的 [xx] 和邮箱里的 @ 都按普通文本处理。
 * 楼中楼的「回复 @xxx :」开头就算 @ 列表里没有这个人也照样高亮
 */
fun parseCommentText(
    message: String,
    emotes: Map<String, String>,
    mentions: Collection<String>
): List<CommentSegment> {
    val segments = mutableListOf<CommentSegment>()
    val plain = StringBuilder()
    fun flushPlain() {
        if (plain.isNotEmpty()) {
            segments.add(CommentSegment.Plain(plain.toString()))
            plain.clear()
        }
    }

    var index = 0
    val replyTargetEnd = message.indexOf(REPLY_PREFIX_END)
    if (message.startsWith(REPLY_PREFIX) && replyTargetEnd > REPLY_PREFIX.length) {
        segments.add(CommentSegment.ReplyPrefix)
        segments.add(CommentSegment.Mention(message.substring(REPLY_PREFIX.length, replyTargetEnd)))
        index = replyTargetEnd
    }

    // 长名字优先，避免 "abc" 抢先匹配掉 "abcd"
    val sortedMentions = mentions.filter { it.isNotEmpty() }.sortedByDescending { it.length }

    while (index < message.length) {
        val char = message[index]
        if (char == '[' && emotes.isNotEmpty()) {
            val end = findEmoteEnd(message, index)
            if (end != -1) {
                val text = message.substring(index, end + 1)
                val url = emotes[text]
                if (url != null) {
                    flushPlain()
                    segments.add(CommentSegment.Emote(text, url))
                    index = end + 1
                    continue
                }
            }
        } else if (char == '@' && sortedMentions.isNotEmpty()) {
            val name = sortedMentions.firstOrNull { message.startsWith(it, index + 1) }
            if (name != null) {
                flushPlain()
                segments.add(CommentSegment.Mention(name))
                index += name.length + 1
                continue
            }
        }
        plain.append(char)
        index++
    }
    flushPlain()
    return segments
}

/**
 * 从 [start] 处的 [ 往后找配对的 ]，找不到返回 -1
 *
 * 只在表情文本的最大长度内找，碰到下一个 [ 就停：正文里一串没闭合的 [ 也只会被扫一遍，
 * 不会每个 [ 都一路扫到正文末尾
 */
private fun findEmoteEnd(message: String, start: Int): Int {
    val limit = minOf(message.length, start + MAX_EMOTE_LENGTH)
    for (i in start + 1 until limit) {
        when (message[i]) {
            ']' -> return i
            '[' -> return -1
        }
    }
    return -1
}

/**
 * 拆好、拼好样式的评论正文
 *
 * @param emotes 正文里出现的表情，按图片地址去重，界面上据此生成内嵌图片
 */
internal class PreparedCommentText(
    val text: AnnotatedString,
    val emotes: List<CommentSegment.Emote>
)

internal fun prepareCommentText(content: CommentContent): PreparedCommentText {
    val segments = parseCommentText(content.message, content.emotes, content.mentions)
    val text = buildAnnotatedString {
        segments.forEach { segment ->
            when (segment) {
                is CommentSegment.Plain -> append(segment.text)
                // 表情的 id 就是图片地址，同一个表情出现多次共用一个槽位
                is CommentSegment.Emote -> appendInlineContent(segment.url, segment.text)
                is CommentSegment.Mention -> withStyle(SpanStyle(color = CommentLinkColor)) {
                    append("@${segment.name}")
                }

                CommentSegment.ReplyPrefix -> withStyle(SpanStyle(color = BVColor.TextTertiary)) {
                    append("回复 ")
                }
            }
        }
    }
    return PreparedCommentText(
        text = text,
        emotes = segments.filterIsInstance<CommentSegment.Emote>().distinctBy { it.url }
    )
}

/**
 * 评论正文的富文本缓存
 *
 * 拉到一页评论后先在后台线程把正文拆好放进来，评论滑进屏幕、在界面线程上组合时直接取，
 * 不用在焦点移动的那一下再去逐字扫描。没命中（比如缓存被挤掉了）时就地生成，结果一样
 */
internal object CommentTextCache {
    // 一页 20 条，每条带两三条楼中楼预览，留够几页的量
    private const val MAX_SIZE = 600

    private val cache = object : LinkedHashMap<CommentContent, PreparedCommentText>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CommentContent, PreparedCommentText>?): Boolean =
            size > MAX_SIZE
    }

    fun get(content: CommentContent): PreparedCommentText {
        synchronized(cache) { cache[content] }?.let { return it }
        val prepared = prepareCommentText(content)
        synchronized(cache) { cache[content] = prepared }
        return prepared
    }

    /** 在后台线程调用，把这些评论和它们的楼中楼预览都准备好 */
    fun warm(comments: List<Comment>) {
        comments.forEach { comment ->
            get(comment.content)
            comment.previewReplies.forEach { get(it.content) }
        }
    }
}
