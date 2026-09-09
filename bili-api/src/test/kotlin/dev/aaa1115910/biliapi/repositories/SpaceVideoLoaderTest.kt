package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.user.SpaceVideoData
import dev.aaa1115910.biliapi.entity.user.SpaceVideoPage
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.http.entity.RiskControlException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpaceVideoLoaderTest {
    @Test
    fun `first-page risk falls back once and next page keeps app cursor`() = runBlocking<Unit> {
        var webCalls = 0
        var appCalls = 0
        val web: suspend () -> SpaceVideoData = { webCalls++; throw RiskControlException(code = -412) }
        val app: suspend () -> SpaceVideoData = {
            appCalls++
            SpaceVideoData(emptyList(), SpaceVideoPage(lastAvid = 123))
        }
        val first = loadSpaceVideoPage(SpaceVideoPage(), ApiType.Web, web, app)
        assertEquals(ApiType.App, first.page.apiType)
        assertEquals(123L, first.page.lastAvid)
        loadSpaceVideoPage(first.page, ApiType.Web, web, app)
        assertEquals(1, webCalls)
        assertEquals(2, appCalls)
    }

    @Test
    fun `later web-page risk does not restart list with app first page`() = runBlocking<Unit> {
        assertFailsWith<RiskControlException> {
            loadSpaceVideoPage(SpaceVideoPage(nextWebPageNumber = 2), ApiType.Web,
                { throw RiskControlException(code = -412) }, { error("must not call App") })
        }
    }

    @Test
    fun `authentication failure is preserved`() = runBlocking<Unit> {
        assertFailsWith<AuthFailureException> {
            loadSpaceVideoPage(SpaceVideoPage(), ApiType.Web,
                { throw AuthFailureException() }, { error("must not call App") })
        }
    }
}
