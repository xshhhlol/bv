package dev.aaa1115910.biliapi.entity

import dev.aaa1115910.biliapi.entity.home.DislikeReason
import dev.aaa1115910.biliapi.entity.home.DislikeType
import dev.aaa1115910.biliapi.entity.home.RecommendDislikeArgs
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.http.entity.home.RcmdIndexData
import dev.aaa1115910.biliapi.http.entity.home.RcmdTopData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RecommendDislikeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // 按 /x/v2/feed/index 返回的一个 av 条目裁剪
    private val appItem = """
        {"args":{"aid":170001,"rid":17,"rname":"单机游戏","tid":4000,"tname":"原神","up_id":2333,"up_name":"某UP"},
         "card_goto":"av","card_type":"small_cover_v2","goto":"av","idx":1758800000,
         "title":"标题","cover":"https://i0.hdslb.com/a.jpg","cover_left_text_1":"1.2万",
         "three_point":{"dislike_reasons":[{"id":4,"name":"UP主:某UP","toast":"老版提示"},{"id":1,"name":"不感兴趣","toast":"老版提示"}]},
         "three_point_v2":[
           {"title":"添加至稍后再看","type":"watch_later"},
           {"title":"反馈","type":"feedback","reasons":[{"id":1,"name":"恐怖血腥","toast":"将优化首页此类内容"}]},
           {"title":"不感兴趣","type":"dislike","reasons":[
             {"id":4,"name":"UP主:某UP","toast":"将减少相似内容推荐"},
             {"id":3,"name":"频道:原神","toast":"将减少相似内容推荐"},
             {"id":1,"name":"不感兴趣","toast":"将减少相似内容推荐"}]}
         ]}
    """.trimIndent()

    private fun parseAppItem(raw: String = appItem) =
        UgcItem.fromRcmdItem(json.decodeFromString<RcmdIndexData.RcmdItem>(raw))

    @Test
    fun `app item keeps region, channel and dislike reasons from three_point_v2`() {
        val args = assertIs<RecommendDislikeArgs.App>(parseAppItem().dislikeArgs)

        assertEquals("av", args.goto)
        assertEquals(17, args.rid)
        assertEquals(4000, args.tagId)
        // 取的是 dislike 那一组，不能把 feedback 的「恐怖血腥」(id 同为 1) 混进来
        assertEquals(listOf(4, 3, 1), args.reasons.map { it.id })
        assertEquals("不感兴趣", args.reasons.last().name)
    }

    @Test
    fun `app reasons fall back to legacy three_point`() {
        val legacy = appItem.replace(""""type":"dislike","reasons":[""", """"type":"dislike","ignored":[""")
        val args = assertIs<RecommendDislikeArgs.App>(parseAppItem(legacy).dislikeArgs)

        assertEquals(listOf(4, 1), args.reasons.map { it.id })
        assertEquals("老版提示", args.reasons.first().toast)
    }

    @Test
    fun `app picks the reason the item offers`() {
        val args = assertIs<RecommendDislikeArgs.App>(parseAppItem().dislikeArgs)

        assertEquals(1, args.reasonFor(DislikeType.Video).id)
        assertEquals(4, args.reasonFor(DislikeType.Up).id)
        // 条目里没有「此类内容过多」，退到频道
        assertEquals(DislikeReason(3, "频道:原神", "将减少相似内容推荐"), args.reasonFor(DislikeType.Similar))
    }

    @Test
    fun `app similar prefers too many over channel and region`() {
        val args = RecommendDislikeArgs.App(
            goto = "av",
            rid = 17,
            tagId = 4000,
            reasons = listOf(
                DislikeReason(2, "分区:单机游戏"),
                DislikeReason(3, "频道:原神"),
                DislikeReason(12, "此类内容过多")
            )
        )
        assertEquals(12, args.reasonFor(DislikeType.Similar).id)
    }

    @Test
    fun `app without reasons falls back by available params`() {
        val bare = RecommendDislikeArgs.App(goto = "av")
        assertEquals(1, bare.reasonFor(DislikeType.Video).id)
        assertEquals(4, bare.reasonFor(DislikeType.Up).id)
        assertEquals(12, bare.reasonFor(DislikeType.Similar).id)

        assertEquals(3, bare.copy(rid = 17, tagId = 4000).reasonFor(DislikeType.Similar).id)
        assertEquals(2, bare.copy(rid = 17, tagId = 0).reasonFor(DislikeType.Similar).id)
    }

    @Test
    fun `web item uses the fixed web reasons`() {
        val raw = """
            {"bvid":"BV1xx411c7mD","cid":1,"duration":120,"enable_vt":0,"goto":"av","id":170001,
             "is_followed":0,"is_stock":0,"owner":{"face":"","mid":2333,"name":"某UP"},
             "pic":"https://i0.hdslb.com/a.jpg","pos":1,"pubdate":1758800000,"show_info":1,
             "title":"标题","track_id":"web_pegasus_0.router-abc","uri":"","business_info":null}
        """.trimIndent()
        val item = UgcItem.fromRcmdItem(json.decodeFromString<RcmdTopData.RcmdItem>(raw))
        val args = assertIs<RecommendDislikeArgs.Web>(item.dislikeArgs)

        assertEquals("web_pegasus_0.router-abc", args.trackId)
        assertEquals(1, args.reasonFor(DislikeType.Video).id)
        assertEquals(4, args.reasonFor(DislikeType.Up).id)
        assertEquals(12, args.reasonFor(DislikeType.Similar).id)
    }
}
