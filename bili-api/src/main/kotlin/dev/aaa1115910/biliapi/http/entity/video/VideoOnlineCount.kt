package dev.aaa1115910.biliapi.http.entity.video

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 视频当前在线观看人数
 *
 * 数值是接口直接给的展示字符串，可能是 "1000+"、"1.2万" 这种模糊值，不是精确数字
 *
 * @param total 所有终端总计在线人数
 * @param count web 端在线人数
 * @param showSwitch 两个数值各自是否允许展示
 */
@Serializable
data class VideoOnlineCount(
    val total: String = "0",
    val count: String = "0",
    @SerialName("show_switch")
    val showSwitch: ShowSwitch? = null
) {
    @Serializable
    data class ShowSwitch(
        val total: Boolean = true,
        val count: Boolean = true
    )
}
