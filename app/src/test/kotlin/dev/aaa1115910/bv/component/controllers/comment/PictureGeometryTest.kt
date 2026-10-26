package dev.aaa1115910.bv.component.controllers.comment

import dev.aaa1115910.biliapi.entity.reply.CommentPicture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PictureGeometryTest {
    // 1080p 电视，左右各留 100，上下各留 50
    private fun geometry(width: Float, height: Float) = PictureGeometry.of(
        picture = CommentPicture(url = "", width = width, height = height),
        containerWidth = 1920f,
        containerHeight = 1080f,
        horizontalPadding = 100f,
        verticalPadding = 50f
    )

    @Test
    fun `landscape picture fits the width and zooms 2x around the center`() {
        val g = geometry(2000f, 1000f)

        assertEquals(1720f, g.fitWidth)
        assertEquals(860f, g.fitHeight)
        assertFalse(g.isTall)
        assertEquals(2f, g.zoomScale)
        assertEquals((1720f * 2 - 1920f) / 2, g.maxPanX)
        assertEquals((860f * 2 - 1080f) / 2, g.maxPanY)
    }

    @Test
    fun `tall screenshot fills the screen width when zoomed`() {
        val g = geometry(1080f, 2400f)

        assertEquals(980f, g.fitHeight)
        assertEquals(980f * 1080f / 2400f, g.fitWidth, 0.01f)
        assertTrue(g.isTall)
        assertEquals(1920f / g.fitWidth, g.zoomScale, 0.001f)
        // 按宽度铺满后左右没得移，上下可以从头看到尾
        assertEquals(0f, g.maxPanX, 0.01f)
        assertEquals((g.fitHeight * g.zoomScale - 1080f) / 2, g.maxPanY, 0.01f)
    }

    @Test
    fun `small square picture still zooms at least 2x and never pans off screen`() {
        val g = geometry(300f, 300f)

        assertEquals(980f, g.fitWidth)
        assertFalse(g.isTall)
        assertEquals(2f, g.zoomScale)
        assertEquals((980f * 2 - 1920f) / 2, g.maxPanX)
    }

    @Test
    fun `missing size falls back to a square`() {
        val g = geometry(0f, 0f)

        assertEquals(g.fitWidth, g.fitHeight)
        assertFalse(g.isTall)
    }
}
