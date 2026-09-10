package dev.aaa1115910.bv.player

import android.content.Context
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object OkHttpUtil {
    /** 连不上就赶紧换下一个 CDN，没必要在一个坏节点上干等 */
    private const val ConnectTimeoutSeconds = 6L

    /** 单次读取无数据的超时，不是整段 4K 视频下载的总时长。 */
    private const val ReadTimeoutSeconds = 8L

    fun generateCustomSslOkHttpClient(context: Context): OkHttpClient {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val customCaMap = mapOf(
            "custom:r5" to "GlobalSign ECC Root CA R5.crt"
        )

        val keyStoreType = KeyStore.getDefaultType()
        val systemKeyStore = KeyStore.getInstance("AndroidCAStore").apply {
            load(null, null)
        }
        val customKeyStore = KeyStore.getInstance(keyStoreType).apply {
            load(null, null)

            systemKeyStore.aliases().toList().forEach {
                setCertificateEntry(it, systemKeyStore.getCertificate(it))
            }
            customCaMap.forEach { (alias, caFilename) ->
                val certificateInputStream = context.assets.open(caFilename)
                val certificate = certificateFactory.generateCertificate(certificateInputStream)
                setCertificateEntry(alias, certificate)
            }
        }

        val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
        val trustManagerFactory: TrustManagerFactory =
            TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(customKeyStore)
            }

        val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }

        return OkHttpClient.Builder()
            .connectTimeout(ConnectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(ReadTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(ReadTimeoutSeconds, TimeUnit.SECONDS)
            // 播放期间会不断发 Range 请求续传，连接复用能省掉每次的握手
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .sslSocketFactory(
                sslContext.socketFactory,
                trustManagerFactory.trustManagers[0] as X509TrustManager
            )
            .hostnameVerifier { hostname, session ->
                // 允许 bilivideo.com 和 bilivideo.cn 域名证书互通
                val biliDomains = listOf("bilivideo.com", "bilivideo.cn")
                val isBiliDomain = biliDomains.any { domain ->
                    hostname == domain || hostname.endsWith(".$domain")
                }
                if (isBiliDomain) {
                    true
                } else {
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                }
            }
            .build()
    }
}
