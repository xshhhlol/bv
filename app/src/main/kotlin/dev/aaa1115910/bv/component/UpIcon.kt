package dev.aaa1115910.bv.component

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun UpIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Icon(
        modifier = modifier,
        painter = painterResource(id = R.drawable.ic_up),
        contentDescription = null,
        tint = color
    )
}

/**
 * 颜色在绘制阶段才读取的版本，给焦点颜色动画用：动画期间只重绘图标，不重组。
 *
 * 画出来和上面的 [UpIcon] 一样：tv Icon 的 tint 就是给 painter 加 ColorFilter.tint，
 * 这里把同一个滤镜挪到绘制时再算，尺寸仍取原 painter 的固有尺寸。
 */
@Composable
fun UpIcon(
    color: () -> Color,
    modifier: Modifier = Modifier
) {
    val painter = painterResource(id = R.drawable.ic_up)
    val tintedPainter = remember(painter, color) { DrawTimeTintPainter(painter, color) }
    Icon(
        modifier = modifier,
        painter = tintedPainter,
        contentDescription = null,
        tint = Color.Unspecified
    )
}

private class DrawTimeTintPainter(
    private val painter: Painter,
    private val tint: () -> Color
) : Painter() {
    override val intrinsicSize: Size
        get() = painter.intrinsicSize

    override fun DrawScope.onDraw() {
        with(painter) { draw(size, colorFilter = ColorFilter.tint(tint())) }
    }
}

@Preview
@Composable
fun UpIconPreview() {
    BVTheme {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpIcon()
            Text(text = "bishi")
        }
    }
}