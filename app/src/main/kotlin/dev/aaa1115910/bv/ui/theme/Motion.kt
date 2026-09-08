package dev.aaa1115910.bv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * 全局动效参数。
 *
 * 电视上遥控器按键是离散的，动画太慢会觉得迟钝，太快又没有「跟手」的感觉。
 * 这里统一收敛成几档：焦点相关用弹簧（有回弹，动感），颜色/透明度用补间（平滑，不抢戏）。
 */
object BVMotion {
    /** Material 3 的 emphasized 曲线，起步快、收尾缓，是「丝滑」的主要来源 */
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val DurationFast = 120
    const val DurationMedium = 260
    const val DurationSlow = 420

    /** 焦点缩放：略带回弹，让卡片「弹」出来 */
    fun <T> focusSpring() = spring<T>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 描边、光晕这类不该有回弹的属性 */
    fun <T> smoothSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun colorTween() = tween<Color>(
        durationMillis = DurationMedium,
        easing = EmphasizedEasing
    )

    fun floatTween(durationMillis: Int = DurationMedium) = tween<Float>(
        durationMillis = durationMillis,
        easing = EmphasizedEasing
    )

    fun dpTween(durationMillis: Int = DurationMedium) = tween<Dp>(
        durationMillis = durationMillis,
        easing = EmphasizedEasing
    )
}
