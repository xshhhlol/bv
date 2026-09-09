package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.user.SpaceVideoData
import dev.aaa1115910.biliapi.entity.user.SpaceVideoPage
import dev.aaa1115910.biliapi.http.entity.RiskControlException

/** A fresh list may use the App endpoint; never mix Web page numbers with App cursors. */
internal suspend fun loadSpaceVideoPage(
    page: SpaceVideoPage,
    preferApiType: ApiType,
    loadWeb: suspend () -> SpaceVideoData,
    loadApp: suspend () -> SpaceVideoData
): SpaceVideoData {
    suspend fun appPage(): SpaceVideoData = loadApp().let {
        it.copy(page = it.page.copy(apiType = ApiType.App))
    }
    if ((page.apiType ?: preferApiType) == ApiType.App) return appPage()
    return try {
        loadWeb().let { it.copy(page = it.page.copy(apiType = ApiType.Web)) }
    } catch (risk: RiskControlException) {
        if (page.nextWebPageNumber != 1 || page.lastAvid != 0L) throw risk
        appPage()
    }
}
