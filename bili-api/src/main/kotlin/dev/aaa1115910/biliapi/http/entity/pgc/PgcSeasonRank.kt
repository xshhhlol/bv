package dev.aaa1115910.biliapi.http.entity.pgc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PGC 排行榜
 *
 * @param note 榜单说明，例如「『电影』投稿在 xx 的数据综合得分，每小时更新一次」
 */
@Serializable
data class PgcSeasonRankData(
    val list: List<RankItem> = emptyList(),
    val note: String = "",
    @SerialName("season_type")
    val seasonType: Int? = null
) {
    /**
     * @param desc 例如 "2.6万弹幕"
     * @param iconFont 其中的 text 为格式化后的播放量，例如 "1012.3万"
     * @param rating 例如 "9.8分"，没有评分时为空字符串
     */
    @Serializable
    data class RankItem(
        val badge: String = "",
        @SerialName("badge_info")
        val badgeInfo: BadgeInfo? = null,
        val cover: String,
        val desc: String = "",
        @SerialName("icon_font")
        val iconFont: IconFont? = null,
        @SerialName("new_ep")
        val newEp: NewEp? = null,
        val rank: Int,
        val rating: String = "",
        @SerialName("season_id")
        val seasonId: Int,
        @SerialName("ss_horizontal_cover")
        val horizontalCover: String? = null,
        val stat: Stat? = null,
        val title: String,
        val url: String = ""
    ) {
        @Serializable
        data class BadgeInfo(
            @SerialName("bg_color")
            val bgColor: String = "",
            @SerialName("bg_color_night")
            val bgColorNight: String = "",
            val text: String = ""
        )

        @Serializable
        data class IconFont(
            val name: String = "",
            val text: String = ""
        )

        /**
         * @param indexShow 例如 "2026-03-20上映"
         */
        @Serializable
        data class NewEp(
            val cover: String = "",
            @SerialName("index_show")
            val indexShow: String = ""
        )

        @Serializable
        data class Stat(
            val danmaku: Long = 0,
            val follow: Long = 0,
            @SerialName("series_follow")
            val seriesFollow: Long = 0,
            val view: Long = 0
        )
    }
}
