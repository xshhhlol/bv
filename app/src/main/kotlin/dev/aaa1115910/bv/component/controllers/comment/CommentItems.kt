package dev.aaa1115910.bv.component.controllers.comment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.reply.Comment
import dev.aaa1115910.biliapi.entity.reply.CommentContent
import dev.aaa1115910.biliapi.entity.reply.CommentPicture
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.util.formatPubTimeString
import dev.aaa1115910.bv.util.toWanString
import java.util.Date

/** @、「共 N 条回复」这类链接样式的文字 */
internal val CommentLinkColor = BVColor.Cyan

private val CommentBodyStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = BVColor.TextPrimary)
private val ReplyBodyStyle = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = BVColor.TextPrimary)
private val PreviewStyle = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = BVColor.TextSecondary)

/**
 * B 站图床支持在地址后面拼尺寸，头像、表情原图动辄几十 KB，列表里全换成小图。
 * 老评论的图片还有 http 地址，一起换成 https
 */
internal fun String.hdslbResized(spec: String): String {
    val url = if (startsWith("http://")) "https://" + removePrefix("http://") else this
    return if ("hdslb.com" in url && '@' !in url.substringAfterLast('/')) "$url@$spec.webp" else url
}

/**
 * 一条一级评论
 *
 * @param expanded 楼中楼页顶部的楼主评论：正文不折叠，也不再显示楼中楼预览
 */
@Composable
internal fun CommentCard(
    modifier: Modifier = Modifier,
    comment: Comment,
    upMid: Long,
    expanded: Boolean = false,
    onClick: () -> Unit,
    onOpenPicture: (pictures: List<CommentPicture>, index: Int) -> Unit
) {
    val pictures = comment.content.pictures
    val pictureLink = rememberPictureFocusLink(pictures)
    CommentSurface(
        modifier = modifier.then(pictureLink?.cardModifier ?: Modifier),
        highlighted = expanded,
        interactionSource = pictureLink?.cardInteraction,
        onClick = onClick
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            CommentAvatar(url = comment.member.avatar, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                CommentAuthorLine(comment = comment, upMid = upMid)
                Spacer(Modifier.height(6.dp))
                CommentText(
                    content = comment.content,
                    style = CommentBodyStyle,
                    maxLines = if (expanded) Int.MAX_VALUE else 6
                )
                if (pictureLink != null) {
                    Spacer(Modifier.height(8.dp))
                    CommentPictures(
                        pictures = pictures,
                        link = pictureLink,
                        onOpen = { onOpenPicture(pictures, it) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                CommentMetaLine(comment = comment)
                if (!expanded && comment.replyCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    PreviewReplies(comment = comment, upMid = upMid)
                }
            }
        }
    }
}

/** 楼中楼里的一条回复，比一级评论小一号 */
@Composable
internal fun ReplyCard(
    modifier: Modifier = Modifier,
    reply: Comment,
    upMid: Long,
    onOpenPicture: (pictures: List<CommentPicture>, index: Int) -> Unit
) {
    val pictures = reply.content.pictures
    val pictureLink = rememberPictureFocusLink(pictures)
    CommentSurface(
        modifier = modifier.then(pictureLink?.cardModifier ?: Modifier),
        interactionSource = pictureLink?.cardInteraction,
        onClick = {}
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            CommentAvatar(url = reply.member.avatar, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                CommentAuthorLine(comment = reply, upMid = upMid)
                Spacer(Modifier.height(4.dp))
                CommentText(content = reply.content, style = ReplyBodyStyle)
                if (pictureLink != null) {
                    Spacer(Modifier.height(8.dp))
                    CommentPictures(
                        pictures = pictures,
                        link = pictureLink,
                        onOpen = { onOpenPicture(pictures, it) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                CommentMetaLine(comment = reply)
            }
        }
    }
}

/**
 * 评论卡片的底板。不放大：抽屉很窄，放大会被两边裁掉，只靠底色和描边表示焦点
 */
@Composable
private fun CommentSurface(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlighted) Color.White.copy(alpha = 0.05f) else Color.Transparent,
            contentColor = BVColor.TextPrimary,
            focusedContainerColor = Color.White.copy(alpha = 0.1f),
            focusedContentColor = BVColor.TextPrimary,
            pressedContainerColor = BVColor.Pink.copy(alpha = 0.16f),
            pressedContentColor = BVColor.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.99f)
    ) {
        content()
    }
}

@Composable
private fun CommentAvatar(url: String, size: Dp) {
    AsyncImage(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(BVColor.SurfaceHighlight),
        model = url.takeIf { it.isNotBlank() }?.hdslbResized("96w_96h_1c"),
        contentDescription = null,
        contentScale = ContentScale.Crop
    )
}

/** 昵称 + 等级 + UP / 置顶标记 */
@Composable
private fun CommentAuthorLine(comment: Comment, upMid: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = comment.member.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (comment.member.isVip) BVColor.PinkBright else BVColor.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LevelBadge(level = comment.member.level)
        if (upMid != 0L && comment.member.mid == upMid) {
            TagBadge(text = "UP", background = BVColor.Pink, color = Color.White)
        }
        if (comment.isTop) {
            TagBadge(text = "置顶", background = BVColor.Pink.copy(alpha = 0.16f), color = BVColor.PinkBright)
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    // B 站的等级配色，6 级的正红在深色底上太刺眼，稍微压了一点
    val color = when {
        level <= 1 -> Color(0xFFBFBFBF)
        level == 2 -> Color(0xFF95DDB2)
        level == 3 -> Color(0xFF92D1E5)
        level == 4 -> Color(0xFFFFB37C)
        level == 5 -> Color(0xFFFF8A3D)
        else -> Color(0xFFF25D5A)
    }
    TagBadge(text = "LV$level", background = color.copy(alpha = 0.16f), color = color)
}

@Composable
private fun TagBadge(text: String, background: Color, color: Color) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        text = text,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1
    )
}

/** 发布时间 · IP 属地，右边是点赞数 */
@Composable
private fun CommentMetaLine(comment: Comment) {
    val time = comment.timeDesc.ifBlank {
        Date(comment.publishTime * 1000).formatPubTimeString()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = listOfNotNull(time, comment.location).joinToString("  ·  "),
            fontSize = 11.sp,
            color = BVColor.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (comment.upLiked) {
            Spacer(Modifier.width(8.dp))
            TagBadge(text = "UP主觉得很赞", background = BVColor.Pink.copy(alpha = 0.12f), color = BVColor.PinkBright)
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            modifier = Modifier.size(13.dp),
            imageVector = Icons.Rounded.ThumbUp,
            contentDescription = null,
            tint = BVColor.TextTertiary
        )
        if (comment.likeCount > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = comment.likeCount.toWanString(),
                fontSize = 11.sp,
                color = BVColor.TextTertiary,
                maxLines = 1
            )
        }
    }
}

/** 一级评论下面那块楼中楼预览 */
@Composable
private fun PreviewReplies(comment: Comment, upMid: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        comment.previewReplies.take(2).forEach { reply ->
            val isUp = upMid != 0L && reply.member.mid == upMid
            val leading = remember(reply.rpid, isUp) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BVColor.TextPrimary.copy(alpha = 0.78f), fontWeight = FontWeight.Medium)) {
                        append(reply.member.name)
                    }
                    if (isUp) {
                        withStyle(SpanStyle(color = BVColor.PinkBright, fontWeight = FontWeight.Bold, fontSize = 10.sp)) {
                            append(" UP")
                        }
                    }
                    append("：")
                }
            }
            CommentText(
                content = reply.content,
                style = PreviewStyle,
                maxLines = 2,
                leading = leading
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "共 ${comment.replyCount.toWanString()} 条回复",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = CommentLinkColor
            )
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = CommentLinkColor
            )
        }
    }
}

/**
 * 带配图的卡片和它的配图之间的焦点关系：卡片获焦时按 → 进到第一张配图上，
 * 配图上按 ↑、或者在第一张上按 ← 回到卡片。↓ 照常走到下一条评论，不耽误往下翻
 */
@Stable
internal class PictureFocusLink {
    val card = FocusRequester()
    val firstPicture = FocusRequester()

    /** 只用来判断卡片本身有没有焦点，决定要不要露出「→ 大图」提示 */
    val cardInteraction = MutableInteractionSource()

    val cardModifier: Modifier = Modifier
        .focusRequester(card)
        .focusProperties { right = firstPicture }
}

/** 没有配图的评论不用这一套，省掉每条评论多出来的状态 */
@Composable
private fun rememberPictureFocusLink(pictures: List<CommentPicture>): PictureFocusLink? =
    if (pictures.isEmpty()) null else remember { PictureFocusLink() }

/** 列表里的缩略图地址，大图弹窗加载原图前先拿它垫底，命中的是同一份内存缓存 */
internal fun CommentPicture.thumbnailUrl(single: Boolean): String =
    url.hdslbResized(if (single) "360h" else "240h")

/**
 * 评论配图：一张时大一点，多张时排一行，最多三张，多出来的在第三张上标 +N
 *
 * 每张都能单独获焦，确认后打开大图
 */
@Composable
private fun CommentPictures(
    pictures: List<CommentPicture>,
    link: PictureFocusLink,
    onOpen: (index: Int) -> Unit
) {
    val single = pictures.size == 1
    val cardFocused by link.cardInteraction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pictures.take(3).forEachIndexed { index, picture ->
            val ratio = (picture.width / picture.height)
                .takeIf { it.isFinite() && it > 0f }
                ?.coerceIn(if (single) 0.6f else 0.75f, if (single) 1.8f else 1.4f)
                ?: 1f
            Surface(
                modifier = Modifier
                    .height(if (single) 120.dp else 72.dp)
                    .aspectRatio(ratio)
                    .then(if (index == 0) Modifier.focusRequester(link.firstPicture) else Modifier)
                    .focusProperties {
                        up = link.card
                        if (index == 0) left = link.card
                    },
                onClick = { onOpen(index) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = BVColor.SurfaceHighlight,
                    focusedContainerColor = BVColor.SurfaceHighlight,
                    pressedContainerColor = BVColor.SurfaceHighlight
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f, pressedScale = 0.98f)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = picture.thumbnailUrl(single),
                    contentDescription = null,
                    contentScale = ContentScale.Crop
                )
                if (index == 2 && pictures.size > 3) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+${pictures.size - 3}", fontSize = 15.sp, color = Color.White)
                    }
                }
                if (index == 0 && cardFocused) {
                    Text(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        text = "→ 大图",
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 评论正文：表情换成图片，@ 和「回复 @xxx」高亮
 *
 * @param leading 接在正文前面的内容，楼中楼预览用它放「昵称：」
 */
@Composable
internal fun CommentText(
    modifier: Modifier = Modifier,
    content: CommentContent,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    leading: AnnotatedString? = null
) {
    // 正文在拉取时已经在后台拆好了，这里只是取出来
    val prepared = remember(content) { CommentTextCache.get(content) }
    val text = remember(prepared, leading) {
        if (leading == null) prepared.text else leading + prepared.text
    }
    val inlineContent = remember(prepared) {
        prepared.emotes.associate { emote ->
            emote.url to InlineTextContent(
                Placeholder(1.4.em, 1.4.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = emote.url.hdslbResized("48w_48h"),
                    contentDescription = emote.text
                )
            }
        }
    }
    Text(
        // 宽度固定下来，Compose 就不再先把整段正文当成一行排一遍去算「固有宽度」。
        // 正文都是左对齐、放在铺满宽度的列里，换行和位置跟原来完全一样
        modifier = modifier.fillMaxWidth(),
        text = text,
        style = style,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
