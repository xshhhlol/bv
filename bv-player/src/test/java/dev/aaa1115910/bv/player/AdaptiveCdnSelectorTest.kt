package dev.aaa1115910.bv.player

import dev.aaa1115910.bv.player.impl.exo.AdaptiveCdnSelector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveCdnSelectorTest {
    @Test
    fun `selects fastest measured address rather than next backup`() = runBlocking {
        var selected = 0
        val scope = CoroutineScope(coroutineContext + Job())
        val speeds = mapOf("a" to 100.0, "b" to 60.0, "c" to 300.0)
        val selector = AdaptiveCdnSelector(speeds.keys.toList(), { selected }, { selected = it },
            { speeds[it] }, scope)
        try {
            selector.refresh()!!.join()
            assertEquals(2, selected)
        } finally { selector.close() }
    }

    @Test
    fun `small differences and failed probes do not interrupt current playback`() = runBlocking {
        var selected = 0
        var now = 0L
        var failed = false
        val scope = CoroutineScope(coroutineContext + Job())
        val selector = AdaptiveCdnSelector(listOf("a", "b"), { selected }, { selected = it },
            { if (failed) error("unreachable") else if (it == "a") 100.0 else 120.0 }, scope, { now })
        try {
            selector.refresh()!!.join()
            assertEquals(0, selected)
            assertNull(selector.refresh())
            now += 31_000_000_000L
            failed = true
            selector.refresh()!!.join()
            assertEquals(0, selected)
        } finally { selector.close() }
    }

    @Test
    fun `later slowdown can select another node after cooldown`() = runBlocking {
        var selected = 0
        var now = 0L
        var speeds = listOf(100.0, 300.0)
        val selector = AdaptiveCdnSelector(listOf("0", "1"), { selected }, { selected = it },
            { speeds[it.toInt()] }, CoroutineScope(coroutineContext + Job()), { now })
        try {
            selector.refresh()!!.join()
            assertEquals(1, selected)
            now += 31_000_000_000L
            speeds = listOf(300.0, 20.0)
            selector.refresh()!!.join()
            assertEquals(0, selected)
        } finally { selector.close() }
    }

    @Test
    fun `closing a stream cancels pending probes and prevents stale selection`() = runBlocking {
        var selected = 0
        val pending = CompletableDeferred<Double?>()
        val selector = AdaptiveCdnSelector(listOf("a", "b"), { selected }, { selected = it },
            { pending.await() }, CoroutineScope(coroutineContext + Job()))
        val job = selector.refresh()!!
        yield()
        selector.close()
        pending.complete(200.0)
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(0, selected)
        assertNull(selector.refresh())
    }
}
