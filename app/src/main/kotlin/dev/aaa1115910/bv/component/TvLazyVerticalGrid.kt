package dev.aaa1115910.bv.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.util.Prefs

/**
 * 焦点定轴：让获焦的 Item 停在容器的固定位置，而不是「刚好露出来就不动了」。
 *
 * @param pivotFraction 焦点 Item 的停留位置比例 (0.0 - 1.0)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberPivotBringIntoViewSpec(pivotFraction: Float): BringIntoViewSpec =
    remember(pivotFraction) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val targetPosition = containerSize * pivotFraction
                return offset - targetPosition
            }
        }
    }

/**
 * 一个封装了 TV 焦点定轴逻辑的 LazyVerticalGrid。
 *
 * @param pivotFraction 焦点 Item 在屏幕上的停留位置比例 (0.0 - 1.0)。
 * 默认 0.3f (即屏幕上方 30% 处)，符合 TV 端习惯。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    pivotFraction: Float = 0.3f, // 默认定在 30% 处
    content: LazyGridScope.() -> Unit
) {
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides rememberPivotBringIntoViewSpec(pivotFraction)
    ) {
        LazyVerticalGrid(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content
        )
    }
}

/**
 * 一个封装了 TV 焦点定轴逻辑的 LazyColumn。
 *
 * @param pivotFraction 焦点 Item 在容器里的停留位置比例，默认 0.4f，
 * 让侧栏这种细长列表在上下翻的时候焦点大致停在中间偏上。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    pivotFraction: Float = 0.4f,
    content: LazyListScope.() -> Unit
) {
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides rememberPivotBringIntoViewSpec(pivotFraction)
    ) {
        LazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

/**
 * 视频列表统一的列数，可在「设置 - 界面」里调整。
 *
 * 用 Flow 读而不是直接读 [Prefs.videoGridColumns]，改完设置退回列表页就能立刻生效，
 * 不用等页面重建。
 */
@Composable
fun videoGridColumns(): Int {
    val columns by Prefs.videoGridColumnsFlow.collectAsState()
    return columns.coerceIn(VideoGridColumnsRange)
}

/** 每行 3~8 个，再多封面就小到看不清了 */
val VideoGridColumnsRange = 3..8
