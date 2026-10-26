package dev.aaa1115910.biliapi.entity

import dev.aaa1115910.biliapi.entity.pgc.PgcRankData
import dev.aaa1115910.biliapi.http.entity.BiliResponse
import dev.aaa1115910.biliapi.http.entity.pgc.PgcSeasonRankData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PgcRankDataTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // 从 /pgc/season/rank/web/list?day=3&season_type=2 的真实返回里截取
    private val response = """
        {"code":0,"message":"success","data":{"list":[
          {"badge":"大会员","badge_info":{"bg_color":"#FB7299","bg_color_night":"#D44E7D","text":"大会员"},"badge_type":0,
           "cover":"https://i0.hdslb.com/a.png","desc":"2.6万弹幕","enable_vt":false,
           "icon_font":{"name":"playdata-square-line@500","text":"1012.3万"},
           "new_ep":{"cover":"https://i0.hdslb.com/b.png","index_show":"2026-03-20上映"},
           "rank":1,"rating":"9.8分","season_id":211802,
           "ss_horizontal_cover":"https://i0.hdslb.com/c.png",
           "stat":{"danmaku":26337,"follow":127575,"series_follow":0,"view":10123052},
           "title":"挽救计划","url":"https://www.bilibili.com/bangumi/play/ss211802?theme=movie"},
          {"badge":"","cover":"https://i0.hdslb.com/d.png","rank":8,"rating":"","season_id":216114,
           "new_ep":{"cover":"https://i0.hdslb.com/e.png","index_show":"2026-06-12上映"},
           "stat":{"danmaku":2116,"follow":0,"series_follow":0,"view":1},"title":"揭秘日"}
        ],"note":"『电影』投稿在 2026年09月11日 - 2026年09月14日 的数据综合得分，每小时更新一次","season_type":2}}
    """.trimIndent()

    private fun parse() = PgcRankData.fromPgcSeasonRankData(
        json.decodeFromString<BiliResponse<PgcSeasonRankData>>(response).getResponseData()
    )

    @Test
    fun `parse rank item with full fields`() {
        val data = parse()
        val item = data.items.first()

        assertEquals(2, data.items.size)
        assertEquals("『电影』投稿在 2026年09月11日 - 2026年09月14日 的数据综合得分，每小时更新一次", data.note)
        assertEquals(1, item.rank)
        assertEquals(211802, item.seasonId)
        assertEquals("挽救计划", item.title)
        assertEquals("9.8", item.rating)
        assertEquals("大会员", item.badge)
        assertEquals("#FB7299", item.badgeColor)
        assertEquals("2026-03-20上映", item.releaseInfo)
        assertEquals("1012.3万", item.playCount)
        assertEquals("2.6万弹幕", item.desc)
        assertEquals(127575, item.follow)
        assertEquals("https://i0.hdslb.com/c.png", item.horizontalCover)
    }

    @Test
    fun `missing rank item fields become null`() {
        val item = parse().items[1]

        assertEquals(8, item.rank)
        assertNull(item.rating)
        assertNull(item.badge)
        assertNull(item.badgeColor)
        assertNull(item.playCount)
        assertNull(item.desc)
        assertEquals(0, item.follow)
        // 没有横版封面时退回到最新一集的封面
        assertEquals("https://i0.hdslb.com/e.png", item.horizontalCover)
    }
}
