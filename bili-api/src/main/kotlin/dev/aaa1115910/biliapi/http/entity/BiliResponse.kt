package dev.aaa1115910.biliapi.http.entity

import kotlinx.serialization.Serializable

/**
 * @param code 0：成功 -101：账号未登录 -400：参数错误 -401：非法访问 -403：访问权限不足
 */
@Serializable
data class BiliResponse<T>(
    val code: Int,
    val message: String,
    val ttl: Int? = null,
    val data: T? = null,
    val result: T? = null
) {
    @Throws()
    fun getResponseData(): T {
        when (code) {
            0 -> {}
            -101 -> throw AuthFailureException(message)
            -352, -412, -509 -> throw RiskControlException(message, code = code)
            else -> throw IllegalStateException(message)
        }
        check(data != null || result != null) { "response data and result are both null" }
        data?.let { return it }
        result?.let { return it }
        error("response data and result are both null, and code should not run here")
    }
}

@Serializable
data class BiliResponseWithoutData(
    val code: Int,
    val message: String,
    // 部分接口不返回 ttl，声明成必填会直接反序列化失败
    val ttl: Int? = null
)

@Suppress("unused")
class AuthFailureException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
}

@Suppress("unused")
class RiskControlException(
    message: String? = null,
    val code: Int? = null,
    val requiresVerification: Boolean = false
) : RuntimeException(message) {
    constructor(message: String?, cause: Throwable?) : this(message) { initCause(cause) }
    constructor(cause: Throwable?) : this(cause?.message, cause)
}
