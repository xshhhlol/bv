package dev.aaa1115910.bv.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ControllerButtonsCodecTest {
    @Test
    fun oldCustomOrderGetsActionsBeforeDanmakuWithoutLosingPreferences() {
        val result = ControllerButtonsCodec.parse("*settings,-danmaku,playPause")
        val buttons = result.map { it.button }
        assertEquals(buttons.indexOf(ControllerButton.Danmaku) - 1, buttons.indexOf(ControllerButton.Engagement))
        assertEquals(ControllerButton.Settings, result.single { it.isDefaultFocus }.button)
        assertEquals(true, result.single { it.button == ControllerButton.Danmaku }.hidden)
        assertFalse(result.single { it.button == ControllerButton.Engagement }.hidden)
        assertEquals(result, ControllerButtonsCodec.parse(ControllerButtonsCodec.serialize(result)))
    }

    @Test
    fun defaultOrderPlacesActionsImmediatelyBeforeDanmaku() {
        val buttons = ControllerButtonsCodec.parse("").map { it.button }
        assertEquals(buttons.indexOf(ControllerButton.Danmaku) - 1, buttons.indexOf(ControllerButton.Engagement))
        assertEquals(ControllerButton.entries.size, buttons.distinct().size)
    }
}
