package dev.aaa1115910.biliapi.entity

import dev.aaa1115910.biliapi.entity.reply.CommentPage
import dev.aaa1115910.biliapi.entity.reply.CommentReplyPage
import dev.aaa1115910.biliapi.http.entity.BiliResponse
import dev.aaa1115910.biliapi.http.entity.reply.ReplyDetailData
import dev.aaa1115910.biliapi.http.entity.reply.ReplyMainData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentDataTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // 从 /x/v2/reply/main?type=1&mode=3 的真实返回里截取
    private val mainResponse = """
        {"code":0,"message":"OK","ttl":1,"data":{
          "cursor":{"is_begin":true,"prev":0,"next":2,"is_end":false,"pagination_reply":{"next_offset":"CAEiAggC"},
                    "mode":3,"all_count":860,"support_mode":[2,3],"name":"热门评论"},
          "upper":{"mid":630921713},
          "top_replies":[
            {"rpid":317700000001,"oid":117285742251158,"type":1,"mid":630921713,"root":0,"parent":0,"count":5,"rcount":4,
             "like":120,"ctime":1758100000,
             "member":{"mid":"630921713","uname":"韩小魔-筑基期","avatar":"https://i2.hdslb.com/bfs/face/a.jpg",
                       "level_info":{"current_level":6},"vip":{"vipStatus":1,"nickname_color":"#FB7299"},
                       "official_verify":{"type":0,"desc":"知名UP主"}},
             "content":{"message":"本视频由updream制作","members":[],"jump_url":{}},
             "replies":null,
             "reply_control":{"is_up_top":true,"time_desc":"3小时前发布"},
             "up_action":{"like":false,"reply":false}}
          ],
          "replies":[
            {"rpid":317744655456,"oid":117285742251158,"type":1,"mid":12345,"root":0,"parent":0,"count":25,"rcount":22,
             "like":3021,"ctime":1758080000,
             "member":{"mid":"12345","uname":"年轻气盛小十四","avatar":"http://i0.hdslb.com/bfs/face/b.jpg",
                       "level_info":{"current_level":5},"vip":{"vipStatus":0,"nickname_color":""},
                       "official_verify":{"type":-1,"desc":""}},
             "content":{"message":"笑死[doge_金箍][未知表情] @韩小魔-筑基期 ",
                        "members":[{"mid":"630921713","uname":"韩小魔-筑基期"}],
                        "emote":{"[doge_金箍]":{"id":83964,"text":"[doge_金箍]","url":"https://i0.hdslb.com/bfs/emote/c.png"},
                                 "[空表情]":{"text":"[空表情]","url":""}},
                        "pictures":[{"img_src":"http://i0.hdslb.com/bfs/new_dyn/d.jpg","img_width":1280,"img_height":685,"img_size":361.12}]},
             "replies":[
               {"rpid":317744655999,"oid":117285742251158,"mid":630921713,"root":317744655456,"parent":317744655456,
                "count":0,"rcount":0,"like":8,"ctime":1758081000,
                "member":{"mid":"630921713","uname":"韩小魔-筑基期","avatar":"","level_info":{"current_level":6},"vip":{}},
                "content":{"message":"回复 @年轻气盛小十四 :你没有胜算的"},
                "reply_control":{"time_desc":"14小时前发布"}}
             ],
             "reply_control":{"up_reply":true,"sub_reply_entry_text":"共22条回复","time_desc":"15小时前发布","location":"IP属地：广东"},
             "up_action":{"like":true,"reply":true}}
          ]}}
    """.trimIndent()

    // 从 /x/v2/reply/reply 的真实返回里截取
    private fun detailResponse(pageNumber: Int, count: Int, replyCount: Int) = """
        {"code":0,"message":"OK","ttl":1,"data":{
          "page":{"num":$pageNumber,"size":10,"count":$count},
          "upper":{"mid":122541},
          "root":{"rpid":1353715029,"oid":170001,"mid":266952687,"root":0,"parent":0,"count":698,"rcount":616,"like":180906,
                  "ctime":1549166850,"member":{"mid":"266952687","uname":"君之自由","avatar":"","level_info":{"current_level":6}},
                  "content":{"message":"妖王为了提倡节约用水"},"reply_control":{"time_desc":"2783天前发布"}},
          "replies":[${(1..replyCount).joinToString(",") { """{"rpid":$it,"root":1353715029,"parent":1353715029,"content":{"message":"reply $it"}}""" }}]
        }}
    """.trimIndent()

    private fun parseMain() = CommentPage.fromReplyMainData(
        json.decodeFromString<BiliResponse<ReplyMainData>>(mainResponse).getResponseData()
    )

    private fun parseDetail(pageNumber: Int, count: Int, replyCount: Int) = CommentReplyPage.fromReplyDetailData(
        json.decodeFromString<BiliResponse<ReplyDetailData>>(detailResponse(pageNumber, count, replyCount)).getResponseData()
    )

    @Test
    fun `parse main list page info`() {
        val page = parseMain()

        assertEquals(2, page.nextCursor)
        assertFalse(page.isEnd)
        assertEquals(860, page.totalCount)
        assertEquals(630921713, page.upMid)
        assertEquals(1, page.comments.size)
        assertEquals(1, page.topComments.size)
        assertTrue(page.topComments.single().isTop)
        assertFalse(page.comments.single().isTop)
    }

    @Test
    fun `parse comment fields`() {
        val comment = parseMain().comments.single()

        assertEquals(317744655456, comment.rpid)
        assertEquals(12345, comment.member.mid)
        assertEquals("年轻气盛小十四", comment.member.name)
        assertEquals(5, comment.member.level)
        assertFalse(comment.member.isVip)
        assertEquals(-1, comment.member.officialType)
        assertEquals(3021, comment.likeCount)
        // 用可见条数，和「共22条回复」对得上
        assertEquals(22, comment.replyCount)
        assertEquals("15小时前", comment.timeDesc)
        assertEquals("IP属地：广东", comment.location)
        assertTrue(comment.upLiked)
        assertEquals(1758080000, comment.publishTime)
    }

    @Test
    fun `parse comment content`() {
        val content = parseMain().comments.single().content

        assertEquals("笑死[doge_金箍][未知表情] @韩小魔-筑基期 ", content.message)
        // 没有图片地址的表情直接丢掉，按普通文本显示
        assertEquals(mapOf("[doge_金箍]" to "https://i0.hdslb.com/bfs/emote/c.png"), content.emotes)
        assertEquals(listOf("韩小魔-筑基期"), content.mentions)
        assertEquals(1, content.pictures.size)
        assertEquals(1280f, content.pictures.single().width)
        assertEquals(685f, content.pictures.single().height)
    }

    @Test
    fun `parse preview replies and vip member`() {
        val page = parseMain()
        val preview = page.comments.single().previewReplies.single()

        assertEquals("回复 @年轻气盛小十四 :你没有胜算的", preview.content.message)
        assertEquals(317744655456, preview.root)
        assertEquals(630921713, preview.member.mid)
        assertTrue(preview.content.emotes.isEmpty())
        assertTrue(page.topComments.single().member.isVip)
        assertNull(page.topComments.single().location)
    }

    @Test
    fun `detail list pages until count is reached`() {
        val first = parseDetail(pageNumber = 1, count = 25, replyCount = 10)
        assertEquals("君之自由", first.root?.member?.name)
        assertEquals(616, first.root?.replyCount)
        assertEquals(10, first.replies.size)
        assertEquals(2, first.nextCursor)
        assertFalse(first.isEnd)
        assertEquals(25, first.totalCount)
        assertEquals(122541, first.upMid)

        val last = parseDetail(pageNumber = 3, count = 25, replyCount = 5)
        assertTrue(last.isEnd)
    }

    @Test
    fun `empty detail page is the end`() {
        // 楼中楼被删到比 count 少时，最后几页会是空的
        assertTrue(parseDetail(pageNumber = 3, count = 60, replyCount = 0).isEnd)
    }
}
