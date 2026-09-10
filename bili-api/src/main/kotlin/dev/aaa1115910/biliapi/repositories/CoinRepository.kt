package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.http.BiliHttpApi
import org.koin.core.annotation.Single

@Single
class CoinRepository(private val authRepository: AuthRepository) {
    suspend fun checkVideoCoined(
        aid: Long,
        bvid: String? = null,
    ): Boolean {
        val like = BiliHttpApi.checkVideoSentCoin(
            avid = aid,
            bvid = bvid,
            sessData = authRepository.sessionData.orEmpty(),
            accessKey = authRepository.accessToken
        )
        return like
    }

    suspend fun sendVideoCoin(
        aid: Long,
        bvid: String? = null,
        multiply: Int = 1,
    ) {
        // 覆盖安装保留的 Web Cookie 可能过期；App 登录直接使用其 access_token，
        // 不再因旧 SESSDATA / bili_jct / buvid3 拦住本来有效的 App 投币请求。
        val (success, message) = BiliHttpApi.sendVideoCoin(
            avid = aid,
            bvid = bvid,
            multiply = multiply,
            csrf = authRepository.biliJct.orEmpty(),
            sessData = authRepository.sessionData.orEmpty(),
            buvid3 = authRepository.buvid3,
            accessKey = authRepository.accessToken,
        )
        if (!success) throw Exception("投币失败：$message")
    }
}
