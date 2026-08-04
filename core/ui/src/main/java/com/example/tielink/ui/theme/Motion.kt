package com.example.tielink.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared motion language for calm, professional feedback across the app. */
object AppMotion {
    const val quick = 180
    const val standard = 240
    const val emphasis = 320
    val easing = FastOutSlowInEasing
}

fun <T> appMotionTween(durationMillis: Int = AppMotion.standard): TweenSpec<T> =
    tween(durationMillis, easing = AppMotion.easing)

fun motionExpandIn(): EnterTransition =
    fadeIn(appMotionTween()) + expandVertically(appMotionTween())

fun motionCollapseOut(): ExitTransition =
    fadeOut(appMotionTween(AppMotion.quick)) + shrinkVertically(appMotionTween(AppMotion.quick))

fun motionEmphasizedExpandIn(): EnterTransition =
    motionExpandIn() + scaleIn(appMotionTween(), initialScale = 0.98f)

fun motionFadeThrough(): ContentTransform =
    fadeIn(appMotionTween()) togetherWith fadeOut(appMotionTween(AppMotion.quick))

fun motionQuickFadeThrough(): ContentTransform =
    fadeIn(appMotionTween(AppMotion.quick)) togetherWith fadeOut(appMotionTween(AppMotion.quick))

fun motionIconTransform(): ContentTransform =
    (fadeIn(appMotionTween(AppMotion.quick)) +
        scaleIn(appMotionTween(AppMotion.quick), initialScale = 0.96f)) togetherWith
        (fadeOut(appMotionTween(AppMotion.quick)) +
            scaleOut(appMotionTween(AppMotion.quick), targetScale = 0.96f))

fun motionForwardEnter(): EnterTransition =
    fadeIn(appMotionTween()) + slideInHorizontally(appMotionTween()) { it / 12 }

fun motionForwardExit(): ExitTransition =
    fadeOut(appMotionTween(AppMotion.quick)) +
        slideOutHorizontally(appMotionTween(AppMotion.quick)) { -it / 16 }

fun motionBackEnter(): EnterTransition =
    fadeIn(appMotionTween()) + slideInHorizontally(appMotionTween()) { -it / 12 }

fun motionBackExit(): ExitTransition =
    fadeOut(appMotionTween(AppMotion.quick)) +
        slideOutHorizontally(appMotionTween(AppMotion.quick)) { it / 16 }

fun motionBreathingSpec(): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(
        animation = appMotionTween(1800),
        repeatMode = RepeatMode.Reverse
    )

@Composable
fun rememberEnterFromBottom(offset: Dp = 16.dp): EnterTransition {
    val offsetPx = with(LocalDensity.current) { offset.roundToPx() }
    return remember(offsetPx) {
        fadeIn(appMotionTween()) +
            slideInVertically(
                animationSpec = appMotionTween(),
                initialOffsetY = { offsetPx }
            )
    }
}

@Composable
fun rememberExitToBottom(offset: Dp = 8.dp): ExitTransition {
    val offsetPx = with(LocalDensity.current) { offset.roundToPx() }
    return remember(offsetPx) {
        fadeOut(appMotionTween(AppMotion.quick)) +
            slideOutVertically(
                animationSpec = appMotionTween(AppMotion.quick),
                targetOffsetY = { offsetPx }
            )
    }
}
