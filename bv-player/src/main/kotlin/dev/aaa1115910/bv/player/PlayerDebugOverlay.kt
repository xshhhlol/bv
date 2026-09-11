package dev.aaa1115910.bv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** 无焦点、无按键处理，文字按屏幕约束缩放，避免长 CDN/编码器名称挤出画面。 */
@Composable
fun PlayerDebugOverlay(text: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        BasicText(
            text = text.ifBlank { "正在读取播放信息…" },
            modifier = Modifier.padding(8.dp)
                .widthIn(max = minOf(740.dp, maxWidth * 0.88f))
                .heightIn(max = maxHeight * if (maxHeight < 360.dp) 0.9f else 0.65f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("player_debug_info"),
            style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, lineHeight = 1.3.em),
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp)
        )
    }
}
