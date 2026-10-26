package dev.aaa1115910.biliapi.entity.pgc

/**
 * PGC 排行榜
 *
 * @param note 榜单说明
 */
data class PgcRankData(
    val note: String,
    val items: List<PgcRankItem>
) {
    companion object {
        fun fromPgcSeasonRankData(data: dev.aaa1115910.biliapi.http.entity.pgc.PgcSeasonRankData): PgcRankData {
            return PgcRankData(
                note = data.note,
                items = data.list.map { PgcRankItem.fromRankItem(it) }
            )
        }
    }
}

/**
 * @param rating 评分，例如 "9.8"，没有评分时为 null
 * @param badge 角标文字，例如 "大会员"
 * @param badgeColor 角标背景色，例如 "#FB7299"
 * @param releaseInfo 上映信息，例如 "2026-03-20上映"
 * @param playCount 格式化后的播放量，例如 "1012.3万"
 * @param desc 例如 "2.6万弹幕"
 */
data class PgcRankItem(
    val rank: Int,
    val seasonId: Int,
    val title: String,
    val cover: String,
    val horizontalCover: String?,
    val rating: String?,
    val badge: String?,
    val badgeColor: String?,
    val releaseInfo: String?,
    val playCount: String?,
    val desc: String?,
    val follow: Long
) {
    companion object {
        fun fromRankItem(item: dev.aaa1115910.biliapi.http.entity.pgc.PgcSeasonRankData.RankItem): PgcRankItem {
            return PgcRankItem(
                rank = item.rank,
                seasonId = item.seasonId,
                title = item.title,
                cover = item.cover,
                horizontalCover = item.horizontalCover?.takeIf { it.isNotBlank() }
                    ?: item.newEp?.cover?.takeIf { it.isNotBlank() },
                rating = item.rating.removeSuffix("分").takeIf { it.isNotBlank() },
                badge = item.badge.takeIf { it.isNotBlank() },
                badgeColor = item.badgeInfo?.bgColor?.takeIf { it.isNotBlank() },
                releaseInfo = item.newEp?.indexShow?.takeIf { it.isNotBlank() },
                playCount = item.iconFont?.text?.takeIf { it.isNotBlank() },
                desc = item.desc.takeIf { it.isNotBlank() },
                follow = item.stat?.follow ?: 0
            )
        }
    }
}

/**
 * 排行榜统计周期
 */
enum class PgcRankDay(val day: Int) {
    ThreeDays(3),
    Week(7)
}
