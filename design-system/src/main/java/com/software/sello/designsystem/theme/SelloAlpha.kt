package com.software.sello.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opacity tokens, by role. A part fades with `Modifier.alpha(...)` or a colour's
 * `copy(alpha = ...)`; it never writes its own number.
 */
@Immutable
data class SelloAlpha(
    /** A control that cannot be used now. */
    val disabled: Float = 0.38f,
    /** What steps back while something else holds attention: a busy notice, a dimmed bar. */
    val muted: Float = 0.5f,
    /** The dim behind a dialog slip or a sheet. */
    val scrim: Float = 0.55f,
    /** A secondary mark beside its text: a currency code, a chip's trailing count. */
    val secondary: Float = 0.72f,
    /** The on-band ink laid over the band as a fill: a trough, a pill behind a label. */
    val bandTint: Float = 0.16f,
    /** A faint fill of an ink on paper: a chart's track, a bar's empty part. */
    val faintFill: Float = 0.15f
)

internal val LocalSelloAlpha = staticCompositionLocalOf { SelloAlpha() }
