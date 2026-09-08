package dev.aaa1115910.biliapi.repositories

import bilibili.app.view.v1.ViewGrpcKt
import bilibili.app.view.v1.viewReq
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.video.VideoDetail
import dev.aaa1115910.biliapi.entity.video.VideoPage
import dev.aaa1115910.biliapi.entity.video.season.SeasonDetail
import dev.aaa1115910.biliapi.grpc.utils.handleGrpcException
import dev.aaa1115910.biliapi.http.BiliHttpApi
import dev.aaa1115910.biliapi.util.AvBvConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class VideoDetailRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository,
    private val favoriteRepository: FavoriteRepository,
    private val likeRepository: LikeRepository,
    private val coinRepository: CoinRepository
) {
    private val viewStub
        get() = runCatching {
            ViewGrpcKt.ViewCoroutineStub(channelRepository.defaultChannel!!)
        }.getOrNull()

    suspend fun getVideoDetail(
        aid: Long,
        preferApiType: ApiType = ApiType.Web
    ): VideoDetail {
        return when (preferApiType) {
            ApiType.Web -> {
                withContext(Dispatchers.IO) {
                    // 这里刻意串行请求。并发打出一串 wbi 接口很容易被判定成机器行为，
                    // 详情页概率性返回 -352 风控校验失败，串行后请求节奏更接近真实 web 端
                    val sessData = authRepository.sessionData ?: ""

                    val detail = BiliHttpApi.getVideoDetail(
                        // 用 bvid 请求，web 端就是这么发的，用 aid 更容易被风控盯上
                        bv = AvBvConverter.av2bv(aid),
                        sessData = sessData
                    ).getResponseData().let { VideoDetail.fromVideoDetail(it) }

                    // 未登录时这三个状态必然是 false，没必要白白多打三个接口去撞风控
                    val userActions = if (sessData.isNotBlank()) {
                        val isFavoured = runCatching {
                            favoriteRepository.checkVideoFavoured(
                                aid = aid,
                                preferApiType = ApiType.Web
                            )
                        }.onFailure {
                            println("Check video favoured failed: $it")
                        }.getOrDefault(false)

                        val isLiked = runCatching {
                            likeRepository.checkVideoLiked(aid = aid)
                        }.onFailure {
                            println("Check video liked failed: $it")
                        }.getOrDefault(false)

                        val isCoined = runCatching {
                            coinRepository.checkVideoCoined(aid = aid)
                        }.onFailure {
                            println("Check video coined failed: $it")
                        }.getOrDefault(false)

                        detail.userActions.copy(
                            favorite = isFavoured,
                            like = isLiked,
                            coin = isCoined
                        )
                    } else {
                        detail.userActions
                    }

                    val history = runCatching {
                        val videoModeInfo = BiliHttpApi.getVideoMoreInfo(
                            avid = aid,
                            cid = detail.cid,
                            sessData = sessData,
                            buvid3 = authRepository.buvid3 ?: ""
                        ).getResponseData()
                        VideoDetail.History(
                            progress = videoModeInfo.lastPlayTime / 1000,
                            lastPlayedCid = videoModeInfo.lastPlayCid
                        )
                    }.onFailure {
                        println("Get video history failed: $it")
                    }.getOrDefault(VideoDetail.History(0, 0))

                    detail.copy(
                        userActions = userActions,
                        history = history
                    )
                }
            }

            ApiType.App -> {
                val viewReply = runCatching {
                    viewStub?.view(viewReq {
                        this.aid = aid.toLong()
                    }) ?: throw IllegalStateException("Player stub is not initialized")
                }.onFailure { handleGrpcException(it) }.getOrThrow()
                VideoDetail.fromViewReply(viewReply)
            }
        }
    }

    suspend fun getUgcPages(
        aid: Long,
        preferApiType: ApiType = ApiType.Web
    ): List<VideoPage> {
        return try {
            when (preferApiType) {
                ApiType.Web -> {
                    val detail = BiliHttpApi.getVideoInfo(
                        av = aid,
                        sessData = authRepository.sessionData ?: ""
                    ).getResponseData()
                    detail.pages.map { VideoPage.fromVideoPage(it) }
                }

                ApiType.App -> {
                    val viewReply = runCatching {
                        viewStub?.view(viewReq {
                            this.aid = aid
                        }) ?: throw IllegalStateException("Player stub is not initialized")
                    }.onFailure { handleGrpcException(it) }
                        .getOrThrow()

                    viewReply.pagesList.map { VideoPage.fromViewPage(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("Get ugc pages failed: aid=$aid, preferApiType=$preferApiType, error=${e.stackTraceToString()}")
            emptyList()
        }
    }

    suspend fun getPgcVideoDetail(
        epid: Int? = null,
        seasonId: Int? = null,
        preferApiType: ApiType = ApiType.Web
    ): SeasonDetail {
        when (preferApiType) {
            ApiType.Web -> {
                val webSeasonData = BiliHttpApi.getWebSeasonInfo(
                    epId = epid,
                    seasonId = seasonId,
                    sessData = authRepository.sessionData ?: ""
                ).getResponseData()
                val seasonDetail = SeasonDetail.fromSeasonData(webSeasonData)
                val firstEp = webSeasonData.episodes.firstOrNull() ?: return seasonDetail

                val playerIcon = runCatching {
                    val videoModeInfo = BiliHttpApi.getVideoMoreInfo(
                        avid = firstEp.aid,
                        cid = firstEp.cid,
                        sessData = authRepository.sessionData ?: "",
                        buvid3 = authRepository.buvid3 ?: ""
                    ).getResponseData()
                    val playerIcon = VideoDetail.PlayerIcon.fromPlayerIcon(videoModeInfo.playerIcon)
                    playerIcon
                }.onFailure {
                    println("Get video player icon failed: $it")
                }.getOrDefault(null)
                seasonDetail.playerIcon = playerIcon
                return seasonDetail
            }

            ApiType.App -> {
                val appSeasonData = BiliHttpApi.getAppSeasonInfo(
                    epId = epid,
                    seasonId = seasonId,
                    mobiApp = "android_hd",
                    accessKey = authRepository.accessToken ?: ""
                ).getResponseData()
                return SeasonDetail.fromSeasonData(appSeasonData)
            }
        }
    }
}