package dev.aaa1115910.biliapi.http

import dev.aaa1115910.biliapi.http.entity.BiliResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/** Explicitly opt in: BILI_API_LIVE_AUDIT=true. Only reads public data; no account mutations. */
@EnabledIfEnvironmentVariable(named = "BILI_API_LIVE_AUDIT", matches = "true")
class LiveApiAuditTest {
    @Test
    fun `audit public read endpoints`() = runBlocking {
        BiliHttpApi.init(buvid3 = "")
        suspend fun audit(name: String, call: suspend () -> Any?) {
            val selected = System.getenv("BILI_API_AUDIT_FILTER")
            if (!selected.isNullOrBlank() && selected.split(',').none { name.contains(it.trim()) }) return
            delay(1250)
            try {
                val result = withTimeout(20000) { call() }
                if (result is BiliResponse<*>) {
                    println("AUDIT $name code=${result.code} data=${result.data != null || result.result != null}")
                } else println("AUDIT $name OK ${if (result is String) result else ""}")
            } catch (e: Exception) {
                println("AUDIT $name ERROR ${e.javaClass.simpleName}: ${e.message?.take(160)}")
            }
        }
        val bv = "BV1xx411c7mD"
        var aid = 2L
        var cid = 62131L
        audit("video/info") {
            BiliHttpApi.getVideoInfo(bv = bv).also { r ->
                r.data?.let { aid = it.aid; cid = it.cid }
            }
        }
        audit("video/detail") { BiliHttpApi.getVideoDetail(bv = bv) }
        audit("video/playurl") { BiliHttpApi.getVideoPlayUrl(bv = bv, cid = cid, fnval = 4048) }
        audit("video/player-info") { BiliHttpApi.getVideoMoreInfo(avid = aid, cid = cid, sessData = "", buvid3 = "") }
        audit("video/related") { BiliHttpApi.getRelatedVideos(avid = aid) }
        audit("video/tags") { BiliHttpApi.getVideoTags(bvid = bv) }
        audit("video/shot-web") { BiliHttpApi.getWebVideoShot(bvid = bv, cid = cid) }
        audit("video/shot-app") { BiliHttpApi.getAppVideoShot(aid, cid) }
        audit("video/danmaku-xml") { BiliHttpApi.getDanmakuXml(cid = cid) }
        audit("home/popular") { BiliHttpApi.getPopularVideoData() }
        audit("home/recommend-web") { BiliHttpApi.getFeedRcmd() }
        audit("home/recommend-app") { BiliHttpApi.getFeedIndex() }
        audit("user/info") { BiliHttpApi.getUserInfo(uid = 672328094) }
        audit("user/card") { BiliHttpApi.getUserCardInfo(uid = 672328094) }
        audit("user/space-web") {
            val data = BiliHttpApi.getWebUserSpaceVideos(mid = 672328094, sessData = "").getResponseData()
            check(!data.list?.vlist.isNullOrEmpty())
            "count=${data.list!!.vlist.size}"
        }
        audit("user/space-app") { BiliHttpApi.getAppUserSpaceVideos(mid = 672328094, lastAvid = 0, ts = System.currentTimeMillis(), accessKey = "") }
        audit("user/relation-stat") { BiliHttpApi.getRelationStat(mid = 672328094) }
        audit("user/followings") { BiliHttpApi.getUserFollow(mid = 672328094, sessData = "") }
        audit("search/hot-web") { BiliHttpApi.getWebSearchSquare() }
        audit("search/hot-app") { BiliHttpApi.getAppSearchSquare() }
        audit("search/trend-app") { BiliHttpApi.getSearchTrendRank() }
        audit("search/suggest") { BiliHttpApi.getKeywordSuggest(term = "测试", buvid = BiliHttpApi.buvid3) }
        audit("search/all") { BiliHttpApi.searchAll(keyword = "哔哩哔哩") }
        for (type in listOf("video", "bili_user", "media_bangumi", "media_ft")) {
            audit("search/$type") {
                val data = BiliHttpApi.searchType(keyword = "哔哩哔哩", type = type).getResponseData()
                "count=${data.searchTypeResults.size}"
            }
        }
        audit("search/special-characters") { BiliHttpApi.searchType(keyword = "游戏 'test' (PV) + 音乐", type = "video") }
        audit("region/recommend") { BiliHttpApi.getRegionFeedRcmd(displayId = 1, fromRegion = 1005) }
        audit("region/locations") { BiliHttpApi.getLocs(ids = listOf(4991)) }
        audit("region/app") { BiliHttpApi.getRegionDynamic(rid = 17, accessKey = "") }
        audit("pgc/season") { BiliHttpApi.getWebSeasonInfo(seasonId = 41410) }
        audit("pgc/timeline-web") { BiliHttpApi.getTimeline(type = 1, before = 1, after = 1) }
        audit("pgc/timeline-app") { BiliHttpApi.getTimeline(filterType = 1) }
        audit("pgc/feed-v3") { BiliHttpApi.getPgcFeedV3() }
        audit("pgc/feed") { BiliHttpApi.getPgcFeed() }
        audit("pgc/index-anime") { BiliHttpApi.seasonIndexAnimeResult() }
        audit("pgc/index-movie") { BiliHttpApi.seasonIndexMovieResult() }
        audit("live/room-info") { BiliLiveHttpApi.getLiveRoomPlayInfo(6) }
        audit("live/danmaku-info") { BiliLiveHttpApi.getLiveDanmuInfo(6) }
        audit("live/danmaku-history") { BiliLiveHttpApi.getLiveDanmuHistory(6) }
        audit("extra/user-space-pagination") {
            val repo = dev.aaa1115910.biliapi.repositories.UserRepository(
                dev.aaa1115910.biliapi.repositories.AuthRepository(),
                dev.aaa1115910.biliapi.repositories.ChannelRepository()
            )
            val first = repo.getSpaceVideos(672328094)
            check(first.videos.isNotEmpty())
            delay(1250)
            val next = repo.getSpaceVideos(672328094, page = first.page)
            check(next.videos.isNotEmpty())
            check(first.videos.map { it.aid }.intersect(next.videos.map { it.aid }.toSet()).isEmpty())
            "api=${first.page.apiType} first=${first.videos.size} next=${next.videos.size} duplicates=0"
        }
        audit("extra/pgc-playurls") {
            val episode = BiliHttpApi.getWebSeasonInfo(seasonId = 41410).getResponseData().episodes.first()
            delay(1250)
            val v1 = BiliHttpApi.getPgcVideoPlayUrl(av = episode.aid, cid = episode.cid, epid = episode.id).code
            delay(1250)
            val v2 = BiliHttpApi.getPgcVideoPlayUrlV2(av = episode.aid, cid = episode.cid, epid = episode.id).code
            "v1=$v1 v2=$v2"
        }
        audit("extra/season-app") { BiliHttpApi.getAppSeasonInfo(seasonId = 41410, mobiApp = "android") }
        audit("extra/index-guochuang") { BiliHttpApi.seasonIndexGuochuangResult() }
        audit("extra/index-tv") { BiliHttpApi.seasonIndexTvResult() }
        audit("extra/index-variety") { BiliHttpApi.seasonIndexVarietyResult() }
        audit("extra/index-documentary") { BiliHttpApi.seasonIndexDocumentaryResult() }
        audit("extra/tag-detail") { BiliHttpApi.getTagDetail(tagId = 1, pageNumber = 1, pageSize = 20) }
        audit("extra/tag-top") { BiliHttpApi.getTagTopVideos(tagId = 1, pageNumber = 1, pageSize = 20) }
        audit("extra/region-app-page") { BiliHttpApi.getRegionDynamicList(rid = 17, accessKey = "") }
        audit("extra/public-favorites") {
            val folders = BiliHttpApi.getAllFavoriteFoldersInfo(mid = 672328094, sessData = "").getResponseData()
            val folder = folders.list.firstOrNull()
            if (folder != null) {
                delay(1250)
                val info = BiliHttpApi.getFavoriteFolderInfo(mediaId = folder.id, sessData = "").code
                delay(1250)
                val list = BiliHttpApi.getFavoriteList(mediaId = folder.id, sessData = "").code
                delay(1250)
                BiliHttpApi.getFavoriteIdList(mediaId = folder.id, sessData = "")
                "folders=${folders.count} info=$info list=$list ids=OK"
            } else "no public folders"
        }
        audit("extra/self-info-guest") { BiliHttpApi.getUserSelfInfo() }
        audit("extra/history-guest") { BiliHttpApi.getHistories() }
        audit("extra/watch-later-guest") { BiliHttpApi.getToView(sessData = "") }
        audit("extra/dynamic-guest") { BiliHttpApi.getDynamicList() }
        audit("extra/relations-guest") { BiliHttpApi.getRelations(mid = 672328094, sessData = "") }
        audit("extra/following-seasons") { BiliHttpApi.getFollowingSeasons(type = 1, status = 0, mid = 672328094) }
    }
}
