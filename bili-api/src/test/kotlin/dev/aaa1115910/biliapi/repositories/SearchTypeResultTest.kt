package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.http.entity.search.SearchResultData
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchTypeResultTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun searchResultData(result: String) = json.decodeFromString<SearchResultData>(
        """
        {"seid":"1","page":1,"pagesize":20,"numResults":0,"numPages":0,
         "suggest_keyword":"","rqt_type":"search","egg_hit":0,"show_column":0,
         "in_black_key":0,"in_white_key":0,"result":$result}
        """.trimIndent()
    )

    @Test
    fun `a type with no results is not an error`() {
        // 关键词只匹配到视频时，番剧/影视/用户这几类返回的就是空列表
        val result = SearchTypeResult.fromSearchTypeResult(searchResultData("[]"))

        assertTrue(result.videos.isEmpty())
        assertTrue(result.pgcs.isEmpty())
        assertTrue(result.users.isEmpty())
        assertEquals(2, result.page.nextPageForWeb)
    }

    @Test
    fun `unknown result type is skipped instead of failing`() {
        val result = SearchTypeResult.fromSearchTypeResult(
            searchResultData("""[{"result_type":"live_room","data":[]}]""")
        )

        assertTrue(result.videos.isEmpty())
        assertEquals(2, result.page.nextPageForWeb)
    }
}
