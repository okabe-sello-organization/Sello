package com.software.sello.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The brand inks the user picks from, in picker order: the one list of Sello's themes.
 * Each registers its light and dark palette; an ink never changes what gain, loss or
 * warn mean. A palette names its ink, so it is read when used rather than built here.
 */
enum class SelloInk(private val light: () -> SelloColors, private val dark: () -> SelloColors) {
    Cobalto({ CobaltoLight }, { CobaltoDark }),
    Violeta({ VioletaLight }, { VioletaDark });

    internal fun colors(isDark: Boolean): SelloColors = if (isDark) dark() else light()
}

/**
 * Sello's colour tokens. Every palette (each ink × light/dark) shares every name;
 * values come from the foundations of the approved visual reference.
 */
@Immutable
data class SelloColors(
    val family: SelloInk,
    val isDark: Boolean,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val onBrandSoft: Color,
    val desk: Color,
    val paper: Color,
    val paperAlt: Color,
    val paperDim: Color,
    val ink: Color,
    val inkSoft: Color,
    val outline: Color,
    val rule: Color,
    val band: Color,
    val onBand: Color,
    val pastBar: Color,
    val onPastBar: Color,
    val pastBarSoft: Color,
    val onPastBarSoft: Color,
    val semantic: SelloSemanticColors,
    val categories: List<Color>
)

/** Meanings that stay the same in both inks, so a stamp reads the same everywhere. */
@Immutable
data class SelloSemanticColors(
    val gain: Color,
    val onGain: Color,
    val gainSoft: Color,
    val onGainSoft: Color,
    val loss: Color,
    val onLoss: Color,
    val lossSoft: Color,
    val onLossSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val dividend: Color
)

fun selloColors(ink: SelloInk, isDark: Boolean): SelloColors = ink.colors(isDark)

internal val LightSemantic = SelloSemanticColors(
    gain = Color(0xFF0A6B4A),
    onGain = Color(0xFFFFFFFF),
    gainSoft = Color(0xFFCDEFE0),
    onGainSoft = Color(0xFF053B28),
    loss = Color(0xFFB81E2A),
    onLoss = Color(0xFFFFFFFF),
    lossSoft = Color(0xFFFBD9DB),
    onLossSoft = Color(0xFF5C0B12),
    warn = Color(0xFF8A4A00),
    warnSoft = Color(0xFFFFE3BF),
    dividend = Color(0xFF6A3FC0)
)

internal val DarkSemantic = SelloSemanticColors(
    gain = Color(0xFF4FD6A0),
    onGain = Color(0xFF04382A),
    gainSoft = Color(0xFF0E4A35),
    onGainSoft = Color(0xFFC9F2E0),
    loss = Color(0xFFFF8A8A),
    onLoss = Color(0xFF4D0A0A),
    lossSoft = Color(0xFF5A171B),
    onLossSoft = Color(0xFFFFDADA),
    warn = Color(0xFFFFB45C),
    warnSoft = Color(0xFF55340C),
    dividend = Color(0xFFB7A0F5)
)

internal val LightCategories = listOf(
    Color(0xFFC4343A),
    Color(0xFFB8521A),
    Color(0xFF9A6400),
    Color(0xFF7C6C00),
    Color(0xFF4F7F18),
    Color(0xFF1B7D45),
    Color(0xFF0B7F73),
    Color(0xFF1773AA),
    Color(0xFF4456C4),
    Color(0xFF7C4AC4),
    Color(0xFFB83A84),
    Color(0xFF5F6878)
)

internal val DarkCategories = listOf(
    Color(0xFFF2726B),
    Color(0xFFF28B4E),
    Color(0xFFE8A63A),
    Color(0xFFD4BC3E),
    Color(0xFF9CCB5A),
    Color(0xFF5BC489),
    Color(0xFF45C7B8),
    Color(0xFF5AB6EA),
    Color(0xFF8C9CF2),
    Color(0xFFB693F0),
    Color(0xFFEE82BE),
    Color(0xFFA3ADB8)
)

internal val LocalSelloColors = staticCompositionLocalOf { CobaltoLight }
