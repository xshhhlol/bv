package dev.aaa1115910.biliapi.repositories

import bilibili.app.show.v1.PopularGrpcKt
import bilibili.app.show.v1.popularResultReq
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.home.DislikeType
import dev.aaa1115910.biliapi.entity.home.RecommendData
import dev.aaa1115910.biliapi.entity.home.RecommendDislikeArgs
import dev.aaa1115910.biliapi.entity.home.RecommendPage
import dev.aaa1115910.biliapi.entity.rank.PopularVideoData
import dev.aaa1115910.biliapi.entity.rank.PopularVideoPage
import dev.aaa1115910.biliapi.entity.ugc.UgcItem
import dev.aaa1115910.biliapi.http.BiliHttpApi
import org.koin.core.annotation.Single

@Single
class RecommendVideoRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository
) {
    private val popularStub
        get() = runCatching {
            PopularGrpcKt.PopularCoroutineStub(channelRepository.defaultChannel!!)
        }.getOrNull()

    suspend fun getPopularVideos(
        page: PopularVideoPage,
        preferApiType: ApiType = ApiType.Web
    ): PopularVideoData {
        return when (preferApiType) {
            ApiType.Web -> {
                val response = BiliHttpApi.getPopularVideoData(
                    pageSize = page.nextWebPageSize,
                    pageNumber = page.nextWebPageNumber,
                    sessData = authRepository.sessionData ?: ""
                ).getResponseData()
                val list = response.list.map { UgcItem.fromVideoInfo(it) }
                val nextPage = PopularVideoPage(
                    nextWebPageSize = page.nextWebPageSize,
                    nextWebPageNumber = page.nextWebPageNumber + 1
                )
                PopularVideoData(
                    list = list,
                    nextPage = nextPage,
                    noMore = response.noMore
                )
            }

            ApiType.App -> {
                val reply = popularStub?.index(popularResultReq {
                    idx = page.nextAppIndex.toLong()
                })
                val list = reply?.itemsList
                    ?.filter { it.itemCase == bilibili.app.card.v1.Card.ItemCase.SMALL_COVER_V5 }
                    ?.map { UgcItem.fromSmallCoverV5(it.smallCoverV5) }
                    ?: emptyList()
                val nextPage = PopularVideoPage(
                    nextAppIndex = list.lastOrNull()?.idx ?: -1
                )
                PopularVideoData(
                    list = list,
                    nextPage = nextPage,
                    noMore = nextPage.nextAppIndex == -1
                )
            }
        }
    }

    suspend fun getRecommendVideos(
        page: RecommendPage = RecommendPage(),
        preferApiType: ApiType = ApiType.Web
    ): RecommendData {
        val items = when (preferApiType) {
            ApiType.Web -> BiliHttpApi.getFeedRcmd(
                idx = page.nextWebIdx,
                sessData = authRepository.sessionData
            )
                .getResponseData().item
                .map { UgcItem.fromRcmdItem(it) }

            ApiType.App -> BiliHttpApi.getFeedIndex(
                idx = page.nextAppIdx,
                accessKey = authRepository.accessToken
            )
                .getResponseData().items
                .filter { it.cardGoto == "av" }
                .map { UgcItem.fromRcmdItem(it) }
        }
        val nextPage = when (preferApiType) {
            ApiType.Web -> RecommendPage(
                nextWebIdx = page.nextWebIdx + 1
            )

            ApiType.App -> RecommendPage(
                nextAppIdx = items.first().idx + 1
            )
        }
        return RecommendData(
            items = items,
            nextPage = nextPage
        )
    }

    /**
     * 对首页推荐条目提交「不感兴趣」
     *
     * 走哪个接口看条目本身来自哪一边的推荐流，而不是当前的 apiType 设置。
     * 成功时返回服务端给的提示语，没有就是 null
     */
    suspend fun dislikeRecommendVideo(item: UgcItem, type: DislikeType): String? {
        val args = requireNotNull(item.dislikeArgs) { "这个视频不支持不感兴趣" }
        val reason = args.reasonFor(type)
        val response = when (args) {
            is RecommendDislikeArgs.Web -> BiliHttpApi.dislikeWebFeed(
                goto = args.goto,
                id = item.aid,
                mid = item.authorMid,
                trackId = args.trackId,
                reasonId = reason.id,
                csrf = authRepository.biliJct?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("请先登录"),
                sessData = authRepository.sessionData?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("请先登录")
            )

            is RecommendDislikeArgs.App -> BiliHttpApi.dislikeAppFeed(
                goto = args.goto,
                id = item.aid,
                reasonId = reason.id,
                mid = item.authorMid,
                rid = args.rid,
                tagId = args.tagId,
                accessKey = authRepository.accessToken?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("请先登录")
            )
        }
        check(response.code == 0) { response.message }
        return reason.toast
    }
}
