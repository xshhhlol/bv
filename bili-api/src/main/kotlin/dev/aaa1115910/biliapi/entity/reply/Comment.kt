package dev.aaa1115910.biliapi.entity.reply

import bilibili.main.community.reply.v1.DetailListReply
import bilibili.main.community.reply.v1.MainListReply
import bilibili.main.community.reply.v1.Mode
import bilibili.main.community.reply.v1.ReplyInfo
import dev.aaa1115910.biliapi.http.entity.reply.ReplyDetailData
import dev.aaa1115910.biliapi.http.entity.reply.ReplyItem
import dev.aaa1115910.biliapi.http.entity.reply.ReplyMainData

/**
 * 评论排序
 *
 * @param mode web 接口和 gRPC 的 mode 取值一致
 */
enum class CommentSort(val mode: Int) {
    Hot(3),
    Time(2);

    internal fun toGrpcMode(): Mode = when (this) {
        Hot -> Mode.MAIN_LIST_HOT
        Time -> Mode.MAIN_LIST_TIME
    }
}

/**
 * 一页一级评论
 *
 * @param topComments 置顶评论，只有第一页有
 * @param nextCursor 请求下一页时带上的游标
 * @param totalCount 评论总数（含楼中楼）
 * @param upMid UP 主的 mid，用来给 UP 自己发的评论打标
 */
data class CommentPage(
    val comments: List<Comment>,
    val topComments: List<Comment>,
    val nextCursor: Long,
    val isEnd: Boolean,
    val totalCount: Int,
    val upMid: Long
) {
    companion object {
        fun fromReplyMainData(data: ReplyMainData) = CommentPage(
            comments = data.replies.orEmpty().map { Comment.fromReplyItem(it) },
            topComments = data.topReplies.orEmpty().map { Comment.fromReplyItem(it, isTop = true) },
            nextCursor = data.cursor.next,
            // 服务端偶尔 is_end 为 false 却什么都不返回，不当作结束会一直空翻页
            isEnd = data.cursor.isEnd || data.replies.isNullOrEmpty(),
            totalCount = data.cursor.allCount,
            upMid = data.upper?.mid ?: 0
        )

        fun fromMainListReply(reply: MainListReply) = CommentPage(
            comments = reply.repliesList.map { Comment.fromReplyInfo(it) },
            topComments = buildList {
                if (reply.hasUpTop()) add(Comment.fromReplyInfo(reply.upTop, isTop = true))
                addAll(reply.topRepliesList.map { Comment.fromReplyInfo(it, isTop = true) })
            }.distinctBy { it.rpid },
            nextCursor = reply.cursor.next,
            isEnd = reply.cursor.isEnd || reply.repliesList.isEmpty(),
            totalCount = reply.subjectControl.count.toInt(),
            upMid = reply.subjectControl.upMid
        )
    }
}

/**
 * 一页楼中楼
 *
 * @param root 楼主评论
 * @param nextCursor web 端是下一页的页码，gRPC 是服务端给的游标，调用方只管原样传回来
 * @param totalCount 楼中楼条数
 */
data class CommentReplyPage(
    val root: Comment?,
    val replies: List<Comment>,
    val nextCursor: Long,
    val isEnd: Boolean,
    val totalCount: Int,
    val upMid: Long
) {
    companion object {
        fun fromReplyDetailData(data: ReplyDetailData): CommentReplyPage {
            val page = data.page
            val replies = data.replies.orEmpty()
            return CommentReplyPage(
                root = data.root?.let { Comment.fromReplyItem(it) },
                replies = replies.map { Comment.fromReplyItem(it) },
                nextCursor = page.num + 1L,
                isEnd = replies.isEmpty() || page.num * page.size >= page.count,
                totalCount = page.count,
                upMid = data.upper?.mid ?: 0
            )
        }

        fun fromDetailListReply(reply: DetailListReply) = CommentReplyPage(
            root = if (reply.hasRoot()) Comment.fromReplyInfo(reply.root) else null,
            replies = reply.root.repliesList.map { Comment.fromReplyInfo(it) },
            nextCursor = reply.cursor.next,
            isEnd = reply.cursor.isEnd || reply.root.repliesList.isEmpty(),
            totalCount = reply.root.count.toInt(),
            upMid = reply.subjectControl.upMid
        )
    }
}

/**
 * 单条评论
 *
 * @param replyCount 楼中楼条数
 * @param publishTime 发布时间，秒
 * @param timeDesc 服务端格式化好的发布时间，例如 "3小时前"，拿不到时为空
 * @param location 例如 "IP属地：广东"，老评论没有
 * @param isTop 是否置顶
 * @param upLiked UP 主是否点过赞
 * @param previewReplies 一级评论下附带的几条楼中楼预览
 */
data class Comment(
    val rpid: Long,
    val oid: Long,
    val root: Long,
    val parent: Long,
    val member: CommentMember,
    val content: CommentContent,
    val likeCount: Int,
    val replyCount: Int,
    val publishTime: Long,
    val timeDesc: String,
    val location: String?,
    val isTop: Boolean,
    val upLiked: Boolean,
    val previewReplies: List<Comment>
) {
    companion object {
        fun fromReplyItem(item: ReplyItem, isTop: Boolean = false): Comment = Comment(
            rpid = item.rpid,
            oid = item.oid,
            root = item.root,
            parent = item.parent,
            member = CommentMember(
                mid = item.member.mid.toLongOrNull() ?: item.mid,
                name = item.member.uname,
                avatar = item.member.avatar,
                level = item.member.levelInfo.currentLevel,
                isVip = item.member.vip.vipStatus == 1 && item.member.vip.nicknameColor.isNotBlank(),
                officialType = item.member.officialVerify.type
            ),
            content = CommentContent(
                message = item.content.message,
                emotes = item.content.emote.orEmpty()
                    .filterValues { it.url.isNotBlank() }
                    .mapValues { it.value.url },
                pictures = item.content.pictures.orEmpty()
                    .filter { it.imgSrc.isNotBlank() }
                    .map { CommentPicture(it.imgSrc, it.imgWidth, it.imgHeight) },
                mentions = item.content.members.orEmpty().map { it.uname }.filter { it.isNotBlank() }
            ),
            likeCount = item.like,
            // count 里还算上了已删除的，界面上要和「共 N 条回复」对得上，用可见条数
            replyCount = item.rcount,
            publishTime = item.ctime,
            timeDesc = item.replyControl.timeDesc.removeSuffix("发布"),
            location = item.replyControl.location.takeIf { it.isNotBlank() },
            isTop = isTop || item.replyControl.isUpTop,
            upLiked = item.upAction.like,
            previewReplies = item.replies.orEmpty().map { fromReplyItem(it) }
        )

        fun fromReplyInfo(info: ReplyInfo, isTop: Boolean = false): Comment {
            // 新版服务端可能只填 member_v2
            val member = if (info.member.name.isBlank() && info.hasMemberV2()) {
                val v2 = info.memberV2
                CommentMember(
                    mid = v2.basic.mid.takeIf { it != 0L } ?: info.mid,
                    name = v2.basic.name,
                    avatar = v2.basic.face,
                    level = v2.basic.level.toInt(),
                    isVip = v2.vip.status == 1L && v2.vip.nicknameColor.isNotBlank(),
                    officialType = v2.official.verifyType.toInt()
                )
            } else {
                CommentMember(
                    mid = info.member.mid.takeIf { it != 0L } ?: info.mid,
                    name = info.member.name,
                    avatar = info.member.face,
                    level = info.member.level.toInt(),
                    isVip = info.member.vipStatus == 1L && info.member.vipNicknameColor.isNotBlank(),
                    officialType = info.member.officialVerifyType.toInt()
                )
            }
            return Comment(
                rpid = info.id,
                oid = info.oid,
                root = info.root,
                parent = info.parent,
                member = member,
                content = CommentContent(
                    message = info.content.message,
                    emotes = info.content.emoteMap
                        .filterValues { it.url.isNotBlank() }
                        .mapValues { it.value.url },
                    pictures = info.content.picturesList
                        .filter { it.imgSrc.isNotBlank() }
                        .map { CommentPicture(it.imgSrc, it.imgWidth.toFloat(), it.imgHeight.toFloat()) },
                    mentions = info.content.atNameToMidMap.keys.filter { it.isNotBlank() }
                ),
                likeCount = info.like.toInt(),
                replyCount = info.count.toInt(),
                publishTime = info.ctime,
                timeDesc = info.replyControl.timeDesc.removeSuffix("发布"),
                location = info.replyControl.location.takeIf { it.isNotBlank() },
                isTop = isTop || info.replyControl.isUpTop,
                upLiked = info.replyControl.upLike,
                previewReplies = info.repliesList.map { fromReplyInfo(it) }
            )
        }
    }
}

/**
 * @param isVip 大会员，昵称显示成粉色
 * @param officialType -1: 无认证 0: 个人认证 1: 机构认证
 */
data class CommentMember(
    val mid: Long,
    val name: String,
    val avatar: String,
    val level: Int,
    val isVip: Boolean,
    val officialType: Int
)

/**
 * @param emotes 正文里的表情文本到图片地址，例如 "[doge]" -> url
 * @param mentions 正文里 @ 到的用户名
 */
data class CommentContent(
    val message: String,
    val emotes: Map<String, String>,
    val pictures: List<CommentPicture>,
    val mentions: List<String>
)

data class CommentPicture(
    val url: String,
    val width: Float,
    val height: Float
)
