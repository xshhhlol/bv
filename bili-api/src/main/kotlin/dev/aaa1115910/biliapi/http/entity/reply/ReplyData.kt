package dev.aaa1115910.biliapi.http.entity.reply

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 一级评论列表 /x/v2/reply/main
 *
 * @param topReplies 置顶评论，只在第一页返回
 */
@Serializable
data class ReplyMainData(
    val cursor: Cursor = Cursor(),
    val replies: List<ReplyItem>? = null,
    @SerialName("top_replies")
    val topReplies: List<ReplyItem>? = null,
    val upper: Upper? = null
) {
    /**
     * @param next 下一页的游标，请求下一页时原样带上
     * @param allCount 评论总数（含楼中楼）
     */
    @Serializable
    data class Cursor(
        @SerialName("is_end")
        val isEnd: Boolean = false,
        val next: Long = 0,
        val mode: Int = 0,
        @SerialName("all_count")
        val allCount: Int = 0
    )
}

/**
 * 楼中楼 /x/v2/reply/reply
 *
 * @param root 楼主评论，第一页之后也会返回
 */
@Serializable
data class ReplyDetailData(
    val page: Page = Page(),
    val root: ReplyItem? = null,
    val replies: List<ReplyItem>? = null,
    val upper: Upper? = null
) {
    @Serializable
    data class Page(
        val num: Int = 0,
        val size: Int = 0,
        val count: Int = 0
    )
}

@Serializable
data class Upper(
    val mid: Long = 0
)

/**
 * 单条评论
 *
 * @param count 楼中楼总数（含已删除的）
 * @param rcount 楼中楼可见条数
 * @param ctime 发布时间，秒
 * @param replies 楼中楼预览，一级评论下最多带几条
 */
@Serializable
data class ReplyItem(
    val rpid: Long,
    val oid: Long = 0,
    val mid: Long = 0,
    val root: Long = 0,
    val parent: Long = 0,
    val count: Int = 0,
    val rcount: Int = 0,
    val like: Int = 0,
    val ctime: Long = 0,
    val member: Member = Member(),
    val content: Content = Content(),
    val replies: List<ReplyItem>? = null,
    @SerialName("reply_control")
    val replyControl: ReplyControl = ReplyControl(),
    @SerialName("up_action")
    val upAction: UpAction = UpAction()
) {
    /**
     * web 端的 mid 是字符串
     */
    @Serializable
    data class Member(
        val mid: String = "0",
        val uname: String = "",
        val avatar: String = "",
        @SerialName("level_info")
        val levelInfo: LevelInfo = LevelInfo(),
        val vip: Vip = Vip(),
        @SerialName("official_verify")
        val officialVerify: OfficialVerify = OfficialVerify()
    ) {
        @Serializable
        data class LevelInfo(
            @SerialName("current_level")
            val currentLevel: Int = 0
        )

        @Serializable
        data class Vip(
            val vipStatus: Int = 0,
            @SerialName("nickname_color")
            val nicknameColor: String = ""
        )

        /**
         * @param type -1: 无认证 0: 个人认证 1: 机构认证
         */
        @Serializable
        data class OfficialVerify(
            val type: Int = -1
        )
    }

    /**
     * @param emote 表情，key 是正文里的 `[doge]` 这种文本
     * @param members 正文里 @ 到的用户
     */
    @Serializable
    data class Content(
        val message: String = "",
        val emote: Map<String, Emote>? = null,
        val pictures: List<Picture>? = null,
        val members: List<Member>? = null
    ) {
        @Serializable
        data class Emote(
            val text: String = "",
            val url: String = ""
        )

        @Serializable
        data class Picture(
            @SerialName("img_src")
            val imgSrc: String = "",
            @SerialName("img_width")
            val imgWidth: Float = 0f,
            @SerialName("img_height")
            val imgHeight: Float = 0f
        )
    }

    /**
     * @param timeDesc 例如 "3小时前发布"
     * @param location 例如 "IP属地：广东"，老评论没有
     */
    @Serializable
    data class ReplyControl(
        @SerialName("is_up_top")
        val isUpTop: Boolean = false,
        @SerialName("time_desc")
        val timeDesc: String = "",
        val location: String = ""
    )

    @Serializable
    data class UpAction(
        val like: Boolean = false,
        val reply: Boolean = false
    )
}
