package dev.aaa1115910.biliapi.repositories

import bilibili.main.community.reply.v1.ReplyGrpcKt
import bilibili.main.community.reply.v1.cursorReq
import bilibili.main.community.reply.v1.detailListReq
import bilibili.main.community.reply.v1.mainListReq
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.reply.CommentPage
import dev.aaa1115910.biliapi.entity.reply.CommentReplyPage
import dev.aaa1115910.biliapi.entity.reply.CommentSort
import dev.aaa1115910.biliapi.grpc.utils.handleGrpcException
import dev.aaa1115910.biliapi.http.BiliHttpApi
import org.koin.core.annotation.Single

/**
 * 视频评论区，目前只用到视频（含番剧剧集）的评论，oid 就是稿件的 aid
 */
@Single
class CommentRepository(
    private val authRepository: AuthRepository,
    private val channelRepository: ChannelRepository
) {
    companion object {
        private const val TYPE_VIDEO = 1
    }

    private val replyStub
        get() = runCatching {
            ReplyGrpcKt.ReplyCoroutineStub(channelRepository.defaultChannel!!)
        }.getOrNull()

    /**
     * 获取一级评论
     *
     * @param cursor 第一页传 0，之后传上一页的 [CommentPage.nextCursor]
     */
    suspend fun getComments(
        aid: Long,
        sort: CommentSort = CommentSort.Hot,
        cursor: Long = 0,
        preferApiType: ApiType = ApiType.Web
    ): CommentPage {
        return when (preferApiType) {
            ApiType.Web -> {
                val data = BiliHttpApi.getReplyMain(
                    oid = aid,
                    type = TYPE_VIDEO,
                    mode = sort.mode,
                    next = cursor,
                    sessData = authRepository.sessionData
                ).getResponseData()
                CommentPage.fromReplyMainData(data)
            }

            ApiType.App -> {
                val reply = runCatching {
                    replyStub!!.mainList(mainListReq {
                        oid = aid
                        type = TYPE_VIDEO.toLong()
                        this.cursor = cursorReq {
                            next = cursor
                            mode = sort.toGrpcMode()
                        }
                    })
                }.onFailure { handleGrpcException(it) }.getOrThrow()
                CommentPage.fromMainListReply(reply)
            }
        }
    }

    /**
     * 获取评论[rootId]的楼中楼
     *
     * @param cursor 第一页传 0，之后传上一页的 [CommentReplyPage.nextCursor]
     */
    suspend fun getCommentReplies(
        aid: Long,
        rootId: Long,
        cursor: Long = 0,
        preferApiType: ApiType = ApiType.Web
    ): CommentReplyPage {
        return when (preferApiType) {
            ApiType.Web -> {
                val data = BiliHttpApi.getReplyDetail(
                    oid = aid,
                    root = rootId,
                    type = TYPE_VIDEO,
                    pageNumber = cursor.toInt().coerceAtLeast(1),
                    sessData = authRepository.sessionData
                ).getResponseData()
                CommentReplyPage.fromReplyDetailData(data)
            }

            ApiType.App -> {
                val reply = runCatching {
                    replyStub!!.detailList(detailListReq {
                        oid = aid
                        type = TYPE_VIDEO.toLong()
                        root = rootId
                        this.cursor = cursorReq { next = cursor }
                    })
                }.onFailure { handleGrpcException(it) }.getOrThrow()
                CommentReplyPage.fromDetailListReply(reply)
            }
        }
    }
}
