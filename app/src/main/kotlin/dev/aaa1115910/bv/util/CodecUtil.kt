package dev.aaa1115910.bv.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Range
import androidx.annotation.RequiresApi
import androidx.core.util.toRange

object CodecUtil {
    fun parseCodecs(): List<CodecInfoData> {
        return MediaCodecList(MediaCodecList.ALL_CODECS)
            .codecInfos.toList()
            .map { CodecInfoData.fromCodecInfo(it) }
    }

    /**
     * 设备上有没有能**实时**扛下这个分辨率和帧率的硬件解码器。
     *
     * 只问 `isSizeSupported` 是不够的：它只回答「这个尺寸解得了吗」，不回答「解得过来吗」。
     * 廉价电视芯片的 H.264 硬件块通常是按 1080p 规格做的，4K 也能解，但只能跑十几帧——
     * 它会老老实实报告支持 3840x2160，然后播放时缓冲是满的、带宽是富余的，画面却一直顿，
     * 丢帧数一路涨。所以这里还要看厂商实测的可达帧率。
     *
     * 查不出来的时候返回 true——宁可放行也不要因为判断不了就把某个编码拦掉。
     */
    fun hasHardwareDecoder(
        mimeType: String,
        width: Int,
        height: Int,
        frameRate: Double = 0.0
    ): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder }
            .filter { codecInfo ->
                codecInfo.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
            .filter { CodecMode.fromMediaCodecInfo(it) == CodecMode.Hardware }
            .any { codecInfo ->
                val videoCapabilities = codecInfo
                    .getCapabilitiesForType(mimeType)
                    .videoCapabilities ?: return@any false
                if (!videoCapabilities.isSizeSupported(width, height)) return@any false
                if (frameRate <= 0) return@any true
                canSustain(videoCapabilities, width, height, frameRate)
            }
    }.getOrDefault(true)

    /**
     * 这个解码器能不能按内容的帧率实时解出来。
     *
     * [MediaCodecInfo.VideoCapabilities.areSizeAndRateSupported] 是能力声明，
     * [MediaCodecInfo.VideoCapabilities.getAchievableFrameRatesFor] 是厂商实测值，后者才反映真实性能。
     * 实测值拿不到（很多设备没提供）时只用能力声明，不因为查不到就判死。
     */
    private fun canSustain(
        capabilities: MediaCodecInfo.VideoCapabilities,
        width: Int,
        height: Int,
        frameRate: Double
    ): Boolean {
        if (!runCatching { capabilities.areSizeAndRateSupported(width, height, frameRate) }
                .getOrDefault(true)
        ) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val achievable = runCatching { capabilities.getAchievableFrameRatesFor(width, height) }
            .getOrNull() ?: return true
        // 留 10% 余量：卡在临界点上的解码器实际播放时一样会掉帧
        return achievable.upper >= frameRate * 0.9
    }

    /** 调试用：厂商实测这个解码器在该分辨率下能跑多少帧 */
    fun achievableFrameRate(mimeType: String, width: Int, height: Int): Double? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } }
            .filter { CodecMode.fromMediaCodecInfo(it) == CodecMode.Hardware }
            .mapNotNull { info ->
                info.getCapabilitiesForType(mimeType).videoCapabilities
                    ?.getAchievableFrameRatesFor(width, height)?.upper
            }
            .maxOrNull()
    }.getOrNull()
}

data class CodecInfoData(
    val name: String,
    val mimeType: String,
    val type: CodecType,
    val mode: CodecMode,
    val media: CodecMedia,
    //val codecProvider: CodecProvider,
    val maxSupportedInstances: Int?,
    val colorFormats: List<Int>,
    val audioBitrateRange: IntRange?,
    val videoBitrateRange: IntRange?,
    val videoFrame: IntRange?,
    val supportedFrameRates: List<SupportedFrameRate>,
    val achievableFrameRates: List<SupportedFrameRate>
) {
    companion object {
        fun fromCodecInfo(codecInfo: MediaCodecInfo): CodecInfoData {
            val capabilities = codecInfo.getCapabilitiesForType(codecInfo.supportedTypes.first())
            return CodecInfoData(
                name = codecInfo.name,
                mimeType = capabilities.mimeType,
                type = CodecType.fromMediaCodecInfo(codecInfo),
                mode = CodecMode.fromMediaCodecInfo(codecInfo),
                media = CodecMedia.fromMediaCodecInfo(codecInfo),
                maxSupportedInstances = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    capabilities.maxSupportedInstances else null,
                colorFormats = capabilities.colorFormats.toList(),
                audioBitrateRange = runCatching { with(capabilities.audioCapabilities.bitrateRange) { lower..upper } }.getOrNull(),
                videoBitrateRange = runCatching { with(capabilities.videoCapabilities.bitrateRange) { lower..upper } }.getOrNull(),
                videoFrame = runCatching { with(capabilities.videoCapabilities.supportedFrameRates) { lower..upper } }.getOrNull(),
                supportedFrameRates = runCatching { codecInfo.getSupportedFrameRates() }
                    .getOrDefault(emptyList()),
                achievableFrameRates = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) codecInfo.getAchievableFrameRates() else emptyList()
                }.getOrDefault(emptyList())
            )
        }
    }
}

enum class CodecType {
    Encoder,
    Decoder;

    companion object {
        fun fromMediaCodecInfo(mediaCodecInfo: MediaCodecInfo): CodecType {
            return if (mediaCodecInfo.isEncoder) Encoder else Decoder
        }

    }
}

enum class CodecMedia {
    Audio,
    Video;

    companion object {
        fun fromMediaCodecInfo(mediaCodecInfo: MediaCodecInfo): CodecMedia {
            return if (mediaCodecInfo.isAudioCodec()) Audio else Video
        }
    }
}

enum class CodecMode {
    Hardware,
    Software;

    companion object {
        fun fromMediaCodecInfo(mediaCodecInfo: MediaCodecInfo): CodecMode {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return if (mediaCodecInfo.isSoftwareOnly) Software else Hardware
            }

            if (mediaCodecInfo.isAudioCodec()) return Software

            val name = mediaCodecInfo.name
            if (name.contains("omx.brcm.video", true)
                && name.contains("hw", true)
            ) return Hardware
            if (name.startsWith("omx.marvell.video.hw", true)) return Hardware
            if (name.startsWith("omx.intel.hw_vd", true)) return Hardware
            if (name.startsWith("omx.qcom", true) && name.endsWith("hw")) return Hardware
            if (name.startsWith("c2.vda.arc", true)
                || name.startsWith("arc.")
            ) return Hardware

            return if (
                name.startsWith("omx.google.", true)
                || name.contains("ffmpeg", true)
                || (name.startsWith("omx.sec.", true) && name.contains(".sw.", true))
                || name.equals("omx.qcom.video.decoder.hevcswvdec", true)
                || name.startsWith("c2.android.", true)
                || name.startsWith("c2.google.", true)
                || name.startsWith("omx.sprd.soft.", true)
                || name.startsWith("omx.avcodec.", true)
                || name.startsWith("omx.pv", true)
                || name.endsWith("sw", true)
                || name.endsWith("sw.dec", true)
                || name.endsWith("sw_vd", true)
                || (!name.startsWith("omx.", true) && !name.startsWith("c2.", true))
            ) Software else Hardware
        }
    }
}

private fun MediaCodecInfo.isAudioCodec(): Boolean {
    return supportedTypes.joinToString().contains("audio")
}

private val resolutions = mapOf(
    480 to 360,
    720 to 480,
    1280 to 720,
    1920 to 1080,
    2560 to 1440,
    3840 to 2160,
    7680 to 4320
)

data class SupportedFrameRate(
    val resolution: Pair<Int, Int>,
    val frameRate: Range<Double>,
    val unsupported: Boolean
)

private fun MediaCodecInfo.getSupportedFrameRates(): List<SupportedFrameRate> {
    return resolutions.map { (width, height) ->
        val frameRates = runCatching {
            val videoCapabilities = getCapabilitiesForType(supportedTypes.first()).videoCapabilities
            videoCapabilities.getSupportedFrameRatesFor(width, height)
        }.getOrNull()
        SupportedFrameRate(
            resolution = width to height,
            frameRate = frameRates ?: ((0.0..0.0).toRange()),
            unsupported = frameRates == null
        )
    }
}

@RequiresApi(Build.VERSION_CODES.M)
private fun MediaCodecInfo.getAchievableFrameRates(): List<SupportedFrameRate> {
    return resolutions.map { (width, height) ->
        val frameRates = runCatching {
            val videoCapabilities = getCapabilitiesForType(supportedTypes.first()).videoCapabilities
            videoCapabilities.getAchievableFrameRatesFor(width, height)
        }.getOrNull()
        SupportedFrameRate(
            resolution = width to height,
            frameRate = frameRates ?: ((0.0..0.0).toRange()),
            unsupported = frameRates == null
        )
    }
}