package dev.aaa1115910.bv.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerDebugOverlayTest {
    @get:Rule val compose = createComposeRule()
    private val info = """
        视频        3840 x 2160 59.94 fps 25.0 Mbps
        音频        192 kbps 44.1 kHz 2ch
        编码        video/hevc (c2.vendor.hevc.decoder)
                    audio/mp4a-latm (FfmpegAudioRenderer)
        渲染        59.9 / 期望 59.9 fps 丢帧 0
        网络接收    123.4 Mbps（近 5 秒，含预下载）
        进度        05:00 / 20:00
        内存缓冲    15s / 目标 30s；采样 48 / 128 MiB
        缓冲终点    占全片 26%（非内存使用率）
        磁盘缓存    512 / 1024 MiB（目录占用 / 配额）
        读取前方    video:连续 128 MiB / 窗口已存 128 MiB  audio:连续 8 MiB / 窗口已存 8 MiB
        预下载状态  video:下载中 audio:窗口已满
        CDN         upos-sz-mirror-example.bilivideo.com, upos-sz-mirror-example2.bilivideo.com
        播放器      AndroidXMedia3/1.8.0
    """.trimIndent()

    @Test fun showingPanelDoesNotTakeRemoteFocusAndTextFitsTvViewport() {
        val visible = mutableStateOf(false)
        val focus = FocusRequester()
        compose.setContent {
            Box(Modifier.requiredSize(960.dp, 540.dp)) {
                Box(Modifier.requiredSize(40.dp).testTag("control").focusRequester(focus).focusable())
                if (visible.value) PlayerDebugOverlay(info)
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        compose.onNodeWithTag("control").assertIsFocused()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("control").assertIsFocused()
        assertTextFits()
    }

    @Test fun textFitsSmallViewportWithLongHostnames() {
        compose.setContent { Box(Modifier.requiredSize(320.dp, 240.dp)) { PlayerDebugOverlay(info) } }
        assertTextFits()
    }

    private fun assertTextFits() {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("player_debug_info").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        assertFalse("debug rows must not be clipped", layouts.single().hasVisualOverflow)
    }
}
