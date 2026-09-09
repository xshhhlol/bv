package dev.aaa1115910.bv.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically

/**
 * 页面/标签切换的统一转场。
 *
 * 转场期间新旧两个页面是同时存在的，两块满屏网格一起布局、绘制，电视上很容易在这一下掉帧。
 * 所以这里刻意做得省：
 *
 * - 时间压到 260ms，重叠窗口小一半，新页面首次组合的开销不会和长动画撞在一起；
 * - 不再叠 scale——alpha 和 scale 同时作用在满屏子树上会多一层离屏合成；
 * - 出场只淡出、不再位移，少一个图层变换；
 * - 关掉 SizeTransform 的裁剪，三个页面本来就一样大，没必要为此多一层 clip。
 */
fun tabContentTransform(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val enterSpec =
        tween<Float>(BVMotion.DurationMedium, easing = BVMotion.EmphasizedDecelerateEasing)
    val exitSpec = tween<Float>(BVMotion.DurationFast, easing = BVMotion.StandardEasing)

    return ContentTransform(
        targetContentEnter = fadeIn(animationSpec = enterSpec) +
                slideInHorizontally(
                    animationSpec = tween(
                        BVMotion.DurationMedium,
                        easing = BVMotion.EmphasizedDecelerateEasing
                    )
                ) { direction * it / 16 },
        initialContentExit = fadeOut(animationSpec = exitSpec),
        sizeTransform = SizeTransform(clip = false)
    )
}

/** 左侧导航切主页面时用竖向位移，和顶部 tab 的横向位移区分开 */
fun sectionContentTransform(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val enterSpec =
        tween<Float>(BVMotion.DurationMedium, easing = BVMotion.EmphasizedDecelerateEasing)
    val exitSpec = tween<Float>(BVMotion.DurationFast, easing = BVMotion.StandardEasing)

    return ContentTransform(
        targetContentEnter = fadeIn(animationSpec = enterSpec) +
                slideInVertically(
                    animationSpec = tween(
                        BVMotion.DurationMedium,
                        easing = BVMotion.EmphasizedDecelerateEasing
                    )
                ) { direction * it / 20 },
        initialContentExit = fadeOut(animationSpec = exitSpec),
        sizeTransform = SizeTransform(clip = false)
    )
}
