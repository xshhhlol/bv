package dev.aaa1115910.bv.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.LocalTextStyle

/**
 * 画出来和 androidx.tv.material3.Text 一样，只是颜色在绘制阶段才读取。
 *
 * 把颜色动画的值直接传给 Text，动画期间每帧都要重组；这里把颜色交给 BasicText 的 [ColorProducer]，
 * 动画只触发重绘。tv Text（1.1.0-alpha01）内部会给文字套一层 Offscreen 合成的 graphicsLayer，
 * 并传一个空的 onTextLayout（走 AnnotatedString 那条排版路径），这里逐项照做，排版和绘制结果都和原来一致。
 */
@Composable
fun AnimatedColorText(
    text: String,
    color: ColorProducer,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        style = style,
        onTextLayout = NoOpTextLayout,
        overflow = overflow,
        softWrap = true,
        maxLines = maxLines,
        minLines = 1,
        color = color
    )
}

private val NoOpTextLayout: (TextLayoutResult) -> Unit = {}
