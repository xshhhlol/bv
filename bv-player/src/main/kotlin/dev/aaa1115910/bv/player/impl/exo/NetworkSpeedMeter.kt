package dev.aaa1115910.bv.player.impl.exo

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** 播放与预下载实际接收的网络字节；缓存文件读取不计入网速。 */
@OptIn(UnstableApi::class)
internal class NetworkSpeedMeter : TransferListener {
    private val rate = RollingNetworkRate()
    var enabled: Boolean
        get() = rate.enabled
        set(value) { rate.enabled = value }

    fun clear() = rate.clear()
    fun bitsPerSecond(): Long = rate.bitsPerSecond()

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (isNetwork) rate.add(bytesTransferred)
    }
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
}
