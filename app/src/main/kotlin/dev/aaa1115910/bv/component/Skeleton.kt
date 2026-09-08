package dev.aaa1115910.bv.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme

/**
 * 骨架屏的微光扫过效果。
 *
 * 首屏加载时先铺一层和真实卡片同尺寸的骨架，比转圈更不容易让人觉得「卡住了」。
 * 动画值只在 draw lambda 里读取，所以整屏骨架也不会带来重组开销。
 */
fun Modifier.shimmer(
    shape: Shape = RoundedCornerShape(12.dp)
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing)
        ),
        label = "shimmer progress"
    )

    drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline = outline, color = BVColor.SurfaceVariant)

        // 高光从左侧扫到右侧，两端留出余量避免出现硬边
        val band = size.width * 0.45f
        val center = (progress * (size.width + 2 * band)) - band
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                start = Offset(center - band, 0f),
                end = Offset(center + band, 0f)
            )
        )
        drawContent()
    }
}

/** 视频卡片的骨架，尺寸和 [dev.aaa1115910.bv.component.videocard.SmallVideoCard] 对齐 */
@Composable
fun VideoCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .shimmer(RoundedCornerShape(20.dp))
        )
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .shimmer(RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(11.dp)
                    .shimmer(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Preview(widthDp = 260)
@Composable
private fun VideoCardSkeletonPreview() {
    BVTheme {
        VideoCardSkeleton(modifier = Modifier.padding(16.dp))
    }
}
