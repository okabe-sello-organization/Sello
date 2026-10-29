package com.software.sello.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Every animation in the app uses one of these specs. */
object SelloMotion {
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    const val QUICK_MILLIS = 120
    const val STANDARD_MILLIS = 240
    const val EMPHASIZED_MILLIS = 360
    const val FEED_MILLIS = 150

    const val STAMP_INITIAL_SCALE = 1.6f
    const val STAMP_RING_INITIAL_SCALE = 2.4f
    const val STAMP_SETTLE_DEGREES = 2f

    /** A key press, a chip selection. */
    fun <T> quick(): TweenSpec<T> = tween(QUICK_MILLIS, easing = LinearOutSlowInEasing)

    /** Cross-fades, a bar growing, a row appearing. */
    fun <T> standard(): TweenSpec<T> = tween(STANDARD_MILLIS, easing = StandardEasing)

    /** A sheet rising, a receipt feeding in. */
    fun <T> emphasized(): TweenSpec<T> = tween(EMPHASIZED_MILLIS, easing = EmphasizedEasing)

    /** Ink rising in a circle: slow, no bounce. */
    fun <T> fill(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 120f)

    /** A line landing in a list. */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** A rubber stamp coming down. */
    fun <T> stamp(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 700f)

    /** A receipt printer advancing one line. */
    fun <T> feed(): TweenSpec<T> = tween(FEED_MILLIS, easing = LinearEasing)
}
