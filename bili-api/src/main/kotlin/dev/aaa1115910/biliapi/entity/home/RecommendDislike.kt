package dev.aaa1115910.biliapi.entity.home

/** 推荐卡片菜单里的三种「不感兴趣」 */
enum class DislikeType {
    /** 只是不想看这一个视频 */
    Video,

    /** 不想看这个 UP 主 */
    Up,

    /** 这类内容推得太多了 */
    Similar
}

/**
 * 给推荐条目提交「不感兴趣」要用到的参数。
 *
 * web 和 app 推荐流走的是两个完全不同的反馈接口，reason_id 的取值也对不上，
 * 所以跟着条目记下它来自哪一边，提交时不看当前的 apiType 设置——
 * 否则中途切了接口类型，拿 web 条目去调 app 接口只会得到一个参数错误。
 */
sealed interface RecommendDislikeArgs {
    val goto: String

    fun reasonFor(type: DislikeType): DislikeReason

    /**
     * web 推荐流的条目不带 three_point，原因只能用网页首页写死的那几个，
     * 取值和 BewlyCat / Bilibili-Gate 抓到的一致
     */
    data class Web(
        override val goto: String,
        val trackId: String
    ) : RecommendDislikeArgs {
        override fun reasonFor(type: DislikeType) = when (type) {
            DislikeType.Video -> DislikeReason(id = 1, name = "内容不感兴趣")
            DislikeType.Up -> DislikeReason(id = 4, name = "不想看此UP主")
            DislikeType.Similar -> DislikeReason(id = 12, name = "此类内容过多")
        }
    }

    /**
     * app 推荐流每个条目自带一组可选原因（three_point_v2 里 type = dislike 那组），
     * 服务端按 reason_id 决定拉黑的是视频本身，还是跟着传的 mid / rid / tag_id 里对应的那个
     */
    data class App(
        override val goto: String,
        /** 分区 id，对应「分区:xxx」 */
        val rid: Int? = null,
        /** 频道（tag）id，对应「频道:xxx」 */
        val tagId: Int? = null,
        val reasons: List<DislikeReason> = emptyList()
    ) : RecommendDislikeArgs {
        override fun reasonFor(type: DislikeType): DislikeReason {
            // 条目给了哪个就用哪个，服务端的原因文案和 toast 都比我们写死的准
            val preferred = when (type) {
                DislikeType.Video -> listOf(REASON_VIDEO)
                DislikeType.Up -> listOf(REASON_UP)
                // 「此类内容过多」最贴切；老一些的返回里没有它，就退到频道、再退到分区
                DislikeType.Similar -> listOf(REASON_TOO_MANY, REASON_CHANNEL, REASON_REGION)
            }
            preferred.firstNotNullOfOrNull { id -> reasons.find { it.id == id } }
                ?.let { return it }

            // 条目没带原因列表时按老规矩拼：reason 对上了参数，服务端一样认
            val fallbackId = when (type) {
                DislikeType.Video -> REASON_VIDEO
                DislikeType.Up -> REASON_UP
                DislikeType.Similar -> when {
                    tagId != null && tagId != 0 -> REASON_CHANNEL
                    rid != null && rid != 0 -> REASON_REGION
                    else -> REASON_TOO_MANY
                }
            }
            return DislikeReason(id = fallbackId, name = "")
        }

        companion object {
            const val REASON_VIDEO = 1
            const val REASON_REGION = 2
            const val REASON_CHANNEL = 3
            const val REASON_UP = 4
            const val REASON_TOO_MANY = 12
        }
    }
}

data class DislikeReason(
    val id: Int,
    val name: String,
    /** 提交成功后服务端建议弹的提示，web 端没有 */
    val toast: String? = null
)
