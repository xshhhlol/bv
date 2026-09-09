package dev.aaa1115910.biliapi.entity.user

import dev.aaa1115910.biliapi.http.entity.user.UserCardData

/**
 * UP 主的名片信息
 *
 * @param mid 用户 id
 * @param name 昵称
 * @param face 头像
 * @param sign 个人签名，也就是空间里的「详情介绍」
 * @param follower 粉丝数
 * @param following 关注数
 * @param archiveCount 投稿数
 * @param likeNum 获赞数
 * @param level 用户等级，取不到时为 0
 * @param officialTitle 认证信息，没有认证时为空串
 * @param topPhoto 空间头图，没有时为 null
 */
data class UserCard(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val follower: Int,
    val following: Int,
    val archiveCount: Int,
    val likeNum: Int,
    val level: Int,
    val officialTitle: String,
    val topPhoto: String?,
    val isFollowing: Boolean = false
) {
    companion object {
        fun fromUserCardData(data: UserCardData): UserCard {
            val card = data.card
            // 认证信息优先取 Official.title，它比 official_verify.desc 更常填
            val officialTitle = card.official?.title?.takeIf { it.isNotBlank() }
                ?: card.officialVerify?.desc?.takeIf { it.isNotBlank() }
                ?: ""
            return UserCard(
                mid = card.mid.toLongOrNull() ?: 0L,
                name = card.name,
                face = card.face,
                sign = card.sign,
                // follower 是接口顶层的字段，card.fans 偶尔为 0，取大的那个
                follower = maxOf(data.follower, card.fans),
                following = card.attention,
                archiveCount = data.archiveCount,
                likeNum = data.likeNum,
                level = card.levelInfo?.currentLevel ?: 0,
                officialTitle = officialTitle,
                topPhoto = data.space?.lImg?.takeIf { it.isNotBlank() },
                isFollowing = data.following
            )
        }
    }
}
