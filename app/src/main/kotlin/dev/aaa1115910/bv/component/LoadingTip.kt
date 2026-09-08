package dev.aaa1115910.bv.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.ui.theme.BVColor
import dev.aaa1115910.bv.ui.theme.BVTheme

/**
 * 一圈旋转的渐变弧线。
 *
 * 比默认的 CircularProgressIndicator 更有辨识度，而且旋转写在 graphicsLayer 里，
 * 每帧不触发重组——它经常挂在长列表底部，重组代价会直接体现在滚动上。
 */
@Composable
fun GradientLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    strokeWidth: Dp = 3.dp
) {
    val transition = rememberInfiniteTransition(label = "loading indicator")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loading rotation"
    )
    val sweep by transition.animateFloat(
        initialValue = 60f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading sweep"
    )
    val brush = remember {
        Brush.sweepGradient(
            listOf(BVColor.Cyan, BVColor.Violet, BVColor.Pink, BVColor.Cyan)
        )
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = angle }
    ) {
        val stroke = strokeWidth.toPx()
        // 底圈，让弧线转到暗处时不会「断掉」
        drawArc(
            color = BVColor.SurfaceHighlight,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(
                this.size.width - stroke,
                this.size.height - stroke
            )
        )
        drawArc(
            brush = brush,
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(
                this.size.width - stroke,
                this.size.height - stroke
            )
        )
    }
}

@Composable
fun LoadingTip(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GradientLoadingIndicator()
        Text(
            text = stringResource(id = R.string.loading),
            style = MaterialTheme.typography.labelLarge,
            color = BVColor.TextSecondary
        )
    }
}

@Preview
@Composable
private fun LoadingTipPreview() {
    BVTheme {
        LoadingTip()
    }
}
