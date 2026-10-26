package dev.aaa1115910.bv.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerButtonsCodecTest {
    @Test
    fun oldCustomOrderGetsActionsBeforeDanmakuWithoutLosingPreferences() {
        val result = ControllerButtonsCodec.parse("*settings,-danmaku,playPause")
        val buttons = result.map { it.button }
        assertEquals(buttons.indexOf(ControllerButton.Danmaku) - 2, buttons.indexOf(ControllerButton.Engagement))
        assertEquals(buttons.indexOf(ControllerButton.Engagement) + 1, buttons.indexOf(ControllerButton.Comment))
        assertEquals(ControllerButton.Settings, result.single { it.isDefaultFocus }.button)
        assertEquals(true, result.single { it.button == ControllerButton.Danmaku }.hidden)
        assertFalse(result.single { it.button == ControllerButton.Engagement }.hidden)
        assertFalse(result.single { it.button == ControllerButton.Comment }.hidden)
        assertEquals(result, ControllerButtonsCodec.parse(ControllerButtonsCodec.serialize(result)))
    }

    @Test
    fun defaultOrderPlacesActionsAndCommentImmediatelyBeforeDanmaku() {
        val buttons = ControllerButtonsCodec.parse("").map { it.button }
        assertEquals(buttons.indexOf(ControllerButton.Danmaku) - 2, buttons.indexOf(ControllerButton.Engagement))
        assertEquals(buttons.indexOf(ControllerButton.Engagement) + 1, buttons.indexOf(ControllerButton.Comment))
        assertEquals(ControllerButton.entries.size, buttons.distinct().size)
    }

    @Test
    fun commentFollowsEngagementEvenWhenUserMovedIt() {
        // 升级前保存的配置里还没有评论按钮，点赞被挪到了最后
        val buttons = ControllerButtonsCodec.parse(
            "playPause,danmaku,videoList,settings,videoDetail,upSpace,related,playMode,playerInfo,engagement"
        ).map { it.button }
        assertEquals(ControllerButton.Comment, buttons.last())
        assertEquals(buttons.size - 2, buttons.indexOf(ControllerButton.Engagement))
    }

    @Test
    fun savedCommentPreferenceIsKept() {
        val result = ControllerButtonsCodec.parse("comment,-engagement,playPause")
        assertEquals(ControllerButton.Comment, result.first().button)
        assertTrue(result.single { it.button == ControllerButton.Engagement }.hidden)
        assertEquals(1, result.count { it.button == ControllerButton.Comment })
    }
}
