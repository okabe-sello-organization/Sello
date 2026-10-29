package com.software.sello.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a screen arranges itself in a window.
 *
 * @param useRail a rail at the leading edge replaces the tab bar, and its "+" the dock.
 * @param twoPane two independently scrolling columns instead of one.
 * @param contentMaxWidth cap for a single column, centred on the desk.
 * @param listPaneWidth width of the first pane when [twoPane]; null splits evenly.
 * @param sidePanelWidth width a sheet takes when it becomes a side panel.
 */
@Immutable
data class SelloWindowLayout(
    val useRail: Boolean,
    val twoPane: Boolean,
    val contentMaxWidth: Dp? = null,
    val listPaneWidth: Dp? = null,
    val sidePanelWidth: Dp? = null
)

/** The reference's breakpoints. A short window is a phone on its side, however wide. */
fun selloWindowLayout(width: Dp, height: Dp): SelloWindowLayout = when {
    height < ShortHeight ->
        SelloWindowLayout(useRail = true, twoPane = true)

    width >= ExpandedWidth -> SelloWindowLayout(
        useRail = true,
        twoPane = true,
        listPaneWidth = 400.dp,
        sidePanelWidth = 420.dp
    )

    width >= MediumWidth -> SelloWindowLayout(
        useRail = false,
        twoPane = false,
        contentMaxWidth = MediumContentWidth
    )

    else -> SelloWindowLayout(useRail = false, twoPane = false)
}

private val ShortHeight = 480.dp
private val MediumWidth = 600.dp
private val ExpandedWidth = 840.dp
private val MediumContentWidth = 560.dp

/**
 * Category cells per row for the [width] they are given: two, three once the column is
 * as wide as a medium window's, and one from font scale 1.3 so names have room.
 */
fun categoryColumns(width: Dp, fontScale: Float): Int = when {
    fontScale >= LARGE_TYPE_SCALE -> 1
    width >= MediumContentWidth -> 3
    else -> 2
}

private const val LARGE_TYPE_SCALE = 1.3f

/**
 * How much of a chart's height a value takes, from 0 to 1. This is geometry: it may
 * lose precision for very large amounts and is never shown or announced as a figure.
 */
internal fun chartFraction(value: Long, max: Long): Float = if (value <= 0 ||
    max <= 0
) {
    0f
} else {
    (value.toDouble() / max.toDouble()).toFloat().coerceIn(0f, 1f)
}

/** The share of a circle that is filled. Drawing is clamped; the figure beside it is not. */
internal fun fillLevel(fraction: Float?, over: Boolean): Float = when {
    over -> 1f
    fraction == null || fraction.isNaN() -> 0f
    else -> fraction.coerceIn(0f, 1f)
}
