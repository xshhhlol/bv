package dev.aaa1115910.bv.entity

import android.content.Context
import androidx.media3.common.MimeTypes
import dev.aaa1115910.biliapi.entity.CodeType
import dev.aaa1115910.bv.R

enum class VideoCodec(private val strRes: Int, val prefix: String, val codecId: Int) {
    AVC(R.string.video_codec_avc, "avc1", 7),
    HEVC(R.string.video_codec_hevc, "hev1", 12),
    AV1(R.string.video_codec_av1, "av01", 13),
    DVH1(R.string.video_codec_dvh1, "dvh1", 0),
    HVC1(R.string.video_codec_hvc1, "hvc", 0);

    companion object {
        fun fromCode(code: Int?): VideoCodec {
            return entries.find { it.ordinal == code } ?: AVC
        }

        fun fromCodecString(codec: String) = runCatching {
            entries.forEach {
                if (codec.startsWith(it.prefix)) return@runCatching it
            }
            return@runCatching null
        }.getOrNull()

        fun fromCodecId(codecId: Int) = runCatching {
            entries.find { it.codecId == codecId }!!
        }.getOrDefault(AVC)
    }

    /** 对应的解码器 mime，用来查设备有没有硬件解码器 */
    val mimeType: String
        get() = when (this) {
            AVC -> MimeTypes.VIDEO_H264
            HEVC, DVH1, HVC1 -> MimeTypes.VIDEO_H265
            AV1 -> MimeTypes.VIDEO_AV1
        }

    fun getDisplayName(context: Context) = context.getString(strRes)

    fun toBiliApiCodeType() = when (this) {
        AVC -> CodeType.Code264
        HEVC -> CodeType.Code265
        AV1 -> CodeType.CodeAv1
        DVH1, HVC1 -> CodeType.Code265
    }
}