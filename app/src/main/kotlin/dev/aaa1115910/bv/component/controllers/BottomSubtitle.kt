package dev.aaa1115910.bv.component.controllers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bilisubtitle.entity.SubtitleItem
import dev.aaa1115910.bv.BuildConfig

@Composable
fun BottomSubtitle(
    modifier: Modifier = Modifier,
    subtitleData: List<SubtitleItem>,
    currentTime: () -> Long,
    fontSize: TextUnit,
    opacity: Float,
    padding: Dp,
) {
    // 进度每 100ms 刷新一次，字幕却要几秒才换一句：进度只在 derivedStateOf 里读，
    // 显示的文字真的变了才重组，调用方也不会被进度带着一起重组
    val currentText by remember(subtitleData, currentTime) {
        derivedStateOf {
            runCatching {
                subtitleData.find { it.isShowing(currentTime()) }?.content
            }.getOrNull() ?: if (BuildConfig.DEBUG) "【DEBUG】无内容" else ""
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (currentText != "") {
            Text(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = padding)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.Black.copy(alpha = opacity))
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                text = currentText,
                fontSize = fontSize,
                textAlign = TextAlign.Center
            )
        }
    }
}
