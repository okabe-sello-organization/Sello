package com.software.sello.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * Sello's theme. The ink is the user's choice; light/dark and reduced motion follow the
 * system unless the caller says otherwise. Wallpaper-derived Material colours are never
 * applied.
 */
@Composable
fun SelloTheme(
    ink: SelloInk = SelloInk.Cobalto,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = systemReducedMotion(),
    content: @Composable () -> Unit
) {
    val colors = selloColors(ink, darkTheme)
    val shapes = remember { SelloShapes() }
    CompositionLocalProvider(
        LocalSelloColors provides colors,
        LocalSelloShapes provides shapes,
        LocalSelloReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = remember(colors) { colors.toMaterialColorScheme() },
            typography = SelloTypography,
            shapes = remember(shapes) { shapes.toMaterialShapes() },
            content = content
        )
    }
}

/** Reads the tokens of the nearest [SelloTheme]. */
object SelloTheme {
    val colors: SelloColors
        @Composable @ReadOnlyComposable
        get() = LocalSelloColors.current

    val type: SelloType
        @Composable @ReadOnlyComposable
        get() = LocalSelloType.current

    val shapes: SelloShapes
        @Composable @ReadOnlyComposable
        get() = LocalSelloShapes.current

    val spacing: SelloSpacing
        @Composable @ReadOnlyComposable
        get() = LocalSelloSpacing.current

    val alpha: SelloAlpha
        @Composable @ReadOnlyComposable
        get() = LocalSelloAlpha.current

    /** When true, every animation cuts to its end state. */
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalSelloReducedMotion.current
}
