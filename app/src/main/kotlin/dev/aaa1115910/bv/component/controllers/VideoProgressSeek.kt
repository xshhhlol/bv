package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun VideoProgressSeek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
    isPersistentSeek: Boolean
) {
    val trackWidthDp = if (isPersistentSeek) 3.dp else 8.dp
    val playedFraction = if (duration > 0) (position / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedFraction = (bufferedPercentage / 100f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isPersistentSeek) trackWidthDp else 16.dp)
    ) {
        val trackWidthPx = trackWidthDp.toPx()
        val startX = trackWidthPx / 2
        val endX = size.width - trackWidthPx / 2
        val usableWidth = (endX - startX).coerceAtLeast(0f)

        // 未播放部分
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(startX, center.y),
            end = Offset(endX, center.y),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )
        // 已缓冲部分
        if (!isPersistentSeek) {
            drawLine(
                color = Color.White.copy(alpha = 0.42f),
                start = Offset(startX, center.y),
                end = Offset(startX + usableWidth * bufferedFraction, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round
            )
        }
        // 已播放部分：粉 → 青的渐变，是整个播放器最主要的品牌色出场
        val playedEnd = startX + usableWidth * playedFraction
        if (playedFraction > 0f) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan),
                    startX = startX,
                    endX = playedEnd.coerceAtLeast(startX + 1f)
                ),
                start = Offset(startX, center.y),
                end = Offset(playedEnd, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round
            )
        }
        // 播放头：外圈一层淡光晕，暗背景上更容易找到
        if (!isPersistentSeek) {
            drawCircle(
                color = BVColor.Cyan.copy(alpha = 0.25f),
                radius = trackWidthPx * 1.5f,
                center = Offset(playedEnd, center.y)
            )
            drawCircle(
                color = Color.White,
                radius = trackWidthPx * 0.75f,
                center = Offset(playedEnd, center.y)
            )
        }
    }

}


@Preview(device = "id:tv_1080p")
@Composable
private fun SeekPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 1000,
            position = 300,
            bufferedPercentage = 50,
            isPersistentSeek = true
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeekWithThumbPreview(@PreviewParameter(ProgressProvider::class) data: Triple<Long, Long, Int>) {
    BVTheme {
        VideoProgressSeek(
            duration = data.first,
            position = data.second,
            bufferedPercentage = data.third,
            isPersistentSeek = false
        )
    }
}

private class ProgressProvider : PreviewParameterProvider<Triple<Long, Long, Int>> {
    override val values = sequenceOf(
        Triple(1234_000L, 0L, 3),
        Triple(1234_000L, 234_000L, 24),
        Triple(1234_000L, 555_000L, 57),
        Triple(1234_000L, 1234_000L, 100)
    )
}