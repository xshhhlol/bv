package dev.aaa1115910.bv.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * 页面/标签切换的统一转场。
 *
 * 进场比出场慢一点、并且带一点点放大，视觉上是新内容「推」着旧内容走，
 * 而不是两块内容同时平移——后者在电视上很容易看出撕裂感。
 */
fun tabContentTransform(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val enterSpec = tween<Float>(BVMotion.DurationSlow, easing = BVMotion.EmphasizedDecelerateEasing)
    val exitSpec = tween<Float>(BVMotion.DurationFast + 60, easing = BVMotion.StandardEasing)

    return (
            fadeIn(animationSpec = enterSpec) +
                    scaleIn(animationSpec = enterSpec, initialScale = 0.985f) +
                    slideInHorizontally(
                        animationSpec = tween(
                            BVMotion.DurationSlow,
                            easing = BVMotion.EmphasizedDecelerateEasing
                        )
                    ) { direction * it / 12 }
            ) togetherWith (
            fadeOut(animationSpec = exitSpec) +
                    slideOutHorizontally(
                        animationSpec = tween(
                            BVMotion.DurationMedium,
                            easing = BVMotion.StandardEasing
                        )
                    ) { -direction * it / 20 }
            )
}

/** 左侧导航切主页面时用竖向位移，和顶部 tab 的横向位移区分开 */
fun sectionContentTransform(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val enterSpec = tween<Float>(BVMotion.DurationSlow, easing = BVMotion.EmphasizedDecelerateEasing)
    val exitSpec = tween<Float>(BVMotion.DurationFast + 60, easing = BVMotion.StandardEasing)

    return (
            fadeIn(animationSpec = enterSpec) +
                    scaleIn(animationSpec = enterSpec, initialScale = 0.99f) +
                    slideInVertically(
                        animationSpec = tween(
                            BVMotion.DurationSlow,
                            easing = BVMotion.EmphasizedDecelerateEasing
                        )
                    ) { direction * it / 16 }
            ) togetherWith (
            fadeOut(animationSpec = exitSpec) +
                    slideOutVertically(
                        animationSpec = tween(
                            BVMotion.DurationMedium,
                            easing = BVMotion.StandardEasing
                        )
                    ) { -direction * it / 24 }
            )
}
