package dev.aaa1115910.bv.entity

import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.util.Prefs

/**
 * 播放器控制条上的按钮
 *
 * [id] 会被持久化到设置里，改名会导致用户已保存的配置失效，不要随意修改
 *
 * @param id 持久化用的标识
 * @param title 设置页里显示的名称
 * @param icon 控制条上的图标，弹幕/循环这类有开关态的按钮由控制条自己决定图标
 * @param seasonOnly 番剧（fromSeason）下是否仍然可用
 */
enum class ControllerButton(
    val id: String,
    val title: String,
    val icon: Int,
    val availableInSeason: Boolean = true
) {
    PlayPause("playPause", "播放/暂停", R.drawable.play_pause_24px),
    Danmaku("danmaku", "弹幕开关", R.drawable.danmaku_on_24px),
    VideoList("videoList", "播放列表", R.drawable.video_list_24px),
    Settings("settings", "打开设置", R.drawable.settings_24px),
    VideoDetail("videoDetail", "视频信息", R.drawable.info_24px, availableInSeason = false),
    UpSpace("upSpace", "up主页", R.drawable.contact_page_24px, availableInSeason = false),
    Related("related", "相关视频", R.drawable.related_videos_24px, availableInSeason = false),
    PlayMode("playMode", "循环播放", R.drawable.repeat_one_24px);

    companion object {
        fun fromId(id: String) = entries.find { it.id == id }
    }
}

/**
 * 单个按钮的配置
 *
 * @param button 对应的按钮
 * @param hidden 是否被用户隐藏
 * @param isDefaultFocus 控制条显示时是否默认聚焦到该按钮
 */
data class ControllerButtonConfig(
    val button: ControllerButton,
    val hidden: Boolean = false,
    val isDefaultFocus: Boolean = false
)

/**
 * 配置的序列化与解析
 *
 * 格式为逗号分隔的按钮 id，前缀含义：
 * - 无前缀：显示
 * - `-`：隐藏
 * - `*`：默认焦点
 *
 * 例如 `playPause,-danmaku,*settings`
 */
object ControllerButtonsCodec {
    /** 默认顺序，同时也是新增按钮插入位置的依据 */
    private val defaultOrder = ControllerButton.entries

    fun parse(configString: String): List<ControllerButtonConfig> {
        val configs = configString.split(",")
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val isDefaultFocus = trimmed.startsWith("*")
                val afterStar = if (isDefaultFocus) trimmed.drop(1) else trimmed
                val hidden = afterStar.startsWith("-")
                val id = if (hidden) afterStar.drop(1) else afterStar
                val button = ControllerButton.fromId(id) ?: return@mapNotNull null
                ControllerButtonConfig(button, hidden, isDefaultFocus)
            }
            // 同一个按钮只保留第一次出现的配置
            .distinctBy { it.button }
        return normalize(appendMissingButtons(configs))
    }

    fun serialize(configs: List<ControllerButtonConfig>): String =
        configs.joinToString(",") { config ->
            buildString {
                if (config.isDefaultFocus) append("*")
                if (config.hidden) append("-")
                append(config.button.id)
            }
        }

    /**
     * 补上配置里没有的按钮
     *
     * 版本升级新增按钮时，老用户存下来的配置里不会有它们，
     * 这里按默认顺序插到相邻按钮后面，避免新按钮全部堆到末尾
     */
    private fun appendMissingButtons(
        configs: List<ControllerButtonConfig>
    ): List<ControllerButtonConfig> {
        val existing = configs.map { it.button }.toSet()
        val missing = defaultOrder.filter { it !in existing }
        if (missing.isEmpty()) return configs

        val result = configs.toMutableList()
        missing.forEach { button ->
            val defaultIndex = defaultOrder.indexOf(button)
            val insertIndex = result
                .indexOfLast { defaultOrder.indexOf(it.button) < defaultIndex }
                .let { if (it == -1) 0 else it + 1 }
            result.add(insertIndex, ControllerButtonConfig(button))
        }
        return result
    }

    /** 保证最多只有一个默认焦点，且它不能是被隐藏的按钮 */
    fun normalize(configs: List<ControllerButtonConfig>): List<ControllerButtonConfig> {
        val focusTarget = configs.firstOrNull { it.isDefaultFocus && !it.hidden }?.button
        return configs.map { it.copy(isDefaultFocus = it.button == focusTarget) }
    }
}

object ControllerButtonsStore {
    fun get(): List<ControllerButtonConfig> =
        ControllerButtonsCodec.parse(Prefs.playerControllerButtons)

    fun save(configs: List<ControllerButtonConfig>): List<ControllerButtonConfig> {
        val normalized = ControllerButtonsCodec.normalize(configs)
        Prefs.playerControllerButtons = ControllerButtonsCodec.serialize(normalized)
        return normalized
    }

    fun reset(): List<ControllerButtonConfig> = save(
        ControllerButton.entries.map { ControllerButtonConfig(it) }
    )
}
