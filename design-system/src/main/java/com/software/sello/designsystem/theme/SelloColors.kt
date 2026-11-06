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

    /**
     * The colour a picker shows for this ink: its light band, the same in both themes. It
     * reads the ink's own palette on purpose, not [SelloTheme.colors]: a swatch previews an
     * ink that may not be applied, so it cannot follow the active scheme.
     */
    val swatch: Color get() = light().band
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
    /** The band of the goal forms (Aportar, Retirar): green, as Metas' dock. */
    val goalBand: Color,
    /** The band of the investment forms: the ink of Inversiones' dock, deepened in dark. */
    val investmentBand: Color,
    val pastBar: Color,
    val onPastBar: Color,
    val pastBarSoft: Color,
    val onPastBarSoft: Color,
    /** The dim behind a sheet or a dialog slip, drawn at [SelloAlpha.scrim]. Material `scrim`. */
    val scrim: Color,
    val semantic: SelloSemanticColors,
    val investments: List<InvestmentInk>
) {
    /**
     * The [rank]-th investment's colour, from 0: the brand first, then [investments],
     * repeating every six. Light and dark fills alternate, so neighbours in a bar ordered
     * by rank never share one, even where the cycle starts again.
     */
    fun investmentInk(rank: Int): InvestmentInk {
        val slot = rank.coerceAtLeast(0) % (investments.size + 1)
        return if (slot == 0) InvestmentInk(brand, onBrand) else investments[slot - 1]
    }
}

/** A fill for one investment and the colour written on it. */
@Immutable
data class InvestmentInk(val fill: Color, val on: Color)

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
    val warnSoft: Color
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
    warnSoft = Color(0xFFFFE3BF)
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
    warnSoft = Color(0xFF55340C)
)

/**
 * After the brand: amber, slate, sky, plum, teal; mid and deep fills alternate. The mid
 * fills take the palette's [ink], so they move with it.
 */
private fun lightInvestments(ink: Color) = listOf(
    InvestmentInk(Color(0xFFB87600), ink),
    InvestmentInk(Color(0xFF3D4A5C), Color(0xFFFFFFFF)),
    InvestmentInk(Color(0xFF2A8BCB), ink),
    InvestmentInk(Color(0xFF9C2A6E), Color(0xFFFFFFFF)),
    InvestmentInk(Color(0xFF1B9385), ink)
)

/**
 * After the brand: amber, slate, sky, pink, teal; mid and light fills alternate. The light
 * slate takes [lightInk], the ink of the same family's light palette.
 */
private fun darkInvestments(lightInk: Color) = listOf(
    InvestmentInk(Color(0xFF9A6B0E), Color(0xFFFFFFFF)),
    InvestmentInk(Color(0xFFA3ADB8), lightInk),
    InvestmentInk(Color(0xFF2B7CB5), Color(0xFFFFFFFF)),
    InvestmentInk(Color(0xFFEE82BE), Color(0xFF2A0A1C)),
    InvestmentInk(Color(0xFF1D8174), Color(0xFFFFFFFF))
)

/** The dim behind sheets and dialog slips in every palette, as the platform's window dim. */
private val Scrim = Color(0xFF000000)

/**
 * A light palette from an ink's own colours. The roles that are another role by design
 * are set from it here, so they move together: the band is the brand and [onBrand] is
 * written on it, the goal band is gain, and the investment band is the ink.
 */
internal fun lightPalette(
    family: SelloInk,
    brand: Color,
    onBrand: Color,
    brandSoft: Color,
    onBrandSoft: Color,
    desk: Color,
    paper: Color,
    paperAlt: Color,
    paperDim: Color,
    ink: Color,
    inkSoft: Color,
    outline: Color,
    rule: Color,
    pastBar: Color,
    onPastBar: Color,
    pastBarSoft: Color,
    onPastBarSoft: Color
) = SelloColors(
    family = family,
    isDark = false,
    brand = brand,
    onBrand = onBrand,
    brandSoft = brandSoft,
    onBrandSoft = onBrandSoft,
    desk = desk,
    paper = paper,
    paperAlt = paperAlt,
    paperDim = paperDim,
    ink = ink,
    inkSoft = inkSoft,
    outline = outline,
    rule = rule,
    band = brand,
    onBand = onBrand,
    goalBand = LightSemantic.gain,
    investmentBand = ink,
    pastBar = pastBar,
    onPastBar = onPastBar,
    pastBarSoft = pastBarSoft,
    onPastBarSoft = onPastBarSoft,
    scrim = Scrim,
    semantic = LightSemantic,
    investments = lightInvestments(ink)
)

/**
 * A dark palette from an ink's own colours. Its bands are their own, deeper than the
 * brand and gain they stand for; [lightInk] is the ink of the family's light palette,
 * written on the dark palette's light investment fill.
 */
internal fun darkPalette(
    family: SelloInk,
    brand: Color,
    onBrand: Color,
    brandSoft: Color,
    onBrandSoft: Color,
    desk: Color,
    paper: Color,
    paperAlt: Color,
    paperDim: Color,
    ink: Color,
    inkSoft: Color,
    outline: Color,
    rule: Color,
    band: Color,
    onBand: Color,
    goalBand: Color,
    investmentBand: Color,
    pastBar: Color,
    onPastBar: Color,
    pastBarSoft: Color,
    onPastBarSoft: Color,
    lightInk: Color
) = SelloColors(
    family = family,
    isDark = true,
    brand = brand,
    onBrand = onBrand,
    brandSoft = brandSoft,
    onBrandSoft = onBrandSoft,
    desk = desk,
    paper = paper,
    paperAlt = paperAlt,
    paperDim = paperDim,
    ink = ink,
    inkSoft = inkSoft,
    outline = outline,
    rule = rule,
    band = band,
    onBand = onBand,
    goalBand = goalBand,
    investmentBand = investmentBand,
    pastBar = pastBar,
    onPastBar = onPastBar,
    pastBarSoft = pastBarSoft,
    onPastBarSoft = onPastBarSoft,
    scrim = Scrim,
    semantic = DarkSemantic,
    investments = darkInvestments(lightInk)
)

internal val LocalSelloColors = staticCompositionLocalOf { CobaltoLight }
