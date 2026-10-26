package dev.aaa1115910.bv.component.controllers.comment

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentListScrollingTest {
    @Test
    fun `a stalled frame does not catch up by jumping across the viewport`() {
        val normalStep = holdScrollStep(1000f, 500, 16_000_000)
        assertEquals(12.8f, normalStep, 0.001f)
        assertEquals(normalStep * 2, holdScrollStep(1000f, 500, 500_000_000), 0.001f)
        assertEquals(3f, holdScrollStep(3f, 500, 16_000_000))
        assertEquals(0f, holdScrollStep(1000f, 500, -1))
    }

    @Test
    fun `hold replaces the single press animation and release cancels it`() = runBlocking {
        val scroller = TallItemScroller(LazyListState(), this)
        fun activeJobs() = coroutineContext[Job]!!.children.count { it.isActive }
        try {
            scroller.step("root", 300f)
            assertEquals(1, activeJobs())
            scroller.hold("root", true)
            assertEquals(1, activeJobs())
            repeat(10) { scroller.hold("root", true) }
            assertEquals(1, activeJobs())
            scroller.release(true)
            assertEquals(0, activeJobs())
        } finally {
            scroller.stop()
        }
    }

    @Test
    fun `old direction release and unrelated item focus loss preserve the new hold`() = runBlocking {
        val scroller = TallItemScroller(LazyListState(), this)
        try {
            scroller.hold("old", true)
            scroller.hold("new", false)
            scroller.release(true)
            scroller.stop("old")
            assertEquals(1, coroutineContext[Job]!!.children.count { it.isActive })
            scroller.stop("new")
            assertTrue(coroutineContext[Job]!!.children.none { it.isActive })
        } finally {
            scroller.stop()
        }
    }

    @Test
    fun `short press release preserves animation but leaving the list cancels it`() = runBlocking {
        val scroller = TallItemScroller(LazyListState(), this)
        try {
            scroller.step("root", 300f)
            scroller.release(true)
            assertEquals(1, coroutineContext[Job]!!.children.count { it.isActive })
            scroller.stop()
            assertTrue(coroutineContext[Job]!!.children.none { it.isActive })
        } finally {
            scroller.stop()
        }
    }
}
