package com.software.sello.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.R

private val SchibstedWeights = listOf(400, 500, 600, 700, 800, 900)

/**
 * Sets the whole interface. One bundled variable font, no downloadable fonts. Picking a
 * weight from a variable font is still an experimental Compose API in the pinned BOM.
 */
@OptIn(ExperimentalTextApi::class)
val SchibstedGrotesk = FontFamily(
    SchibstedWeights.map { weight ->
        Font(
            resId = R.font.schibsted_grotesk,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight))
        )
    }
)

/** Stamps only. Never use it for running text. */
internal val SairaStencilOne = FontFamily(Font(R.font.saira_stencil_one, FontWeight.Normal))

/**
 * Figures line up in columns. Only the roles that carry figures ask for it: in this
 * font the feature also widens points, commas and colons, which spoils a sentence.
 */
private const val TABULAR_FIGURES = "tnum"

private fun figures(size: Double, line: Int, weight: Int, tracking: TextUnit = 0.em) =
    text(size, line, weight, tracking).copy(fontFeatureSettings = TABULAR_FIGURES)

private fun text(size: Double, line: Int, weight: Int, tracking: TextUnit = 0.em) = TextStyle(
    fontFamily = SchibstedGrotesk,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking
)

/**
 * Material roles as the reference sizes them. 12sp is the smallest text in the app.
 * Display, headline and title roles hold figures; body and label roles hold sentences.
 */
val SelloTypography = Typography(
    displayLarge = figures(60.0, 61, 900, (-0.04).em),
    displayMedium = figures(44.0, 46, 900, (-0.04).em),
    displaySmall = figures(32.0, 34, 900, (-0.035).em),
    headlineLarge = figures(28.0, 32, 800, (-0.02).em),
    headlineMedium = figures(26.0, 30, 800, (-0.02).em),
    headlineSmall = figures(23.0, 26, 800, (-0.02).em),
    titleLarge = figures(19.0, 22, 800, (-0.01).em),
    titleMedium = figures(16.0, 20, 800),
    titleSmall = figures(14.0, 19, 800),
    bodyLarge = text(14.0, 19, 400),
    bodyMedium = text(13.5, 18, 400),
    bodySmall = text(12.5, 16, 400),
    labelLarge = text(17.0, 20, 800),
    labelMedium = text(12.5, 16, 400),
    labelSmall = text(12.0, 14, 600)
)

/**
 * Text styles outside Material's roles: the stamps, and the combinations of a role with
 * another size or weight that several places repeat. Each is named for what it sets, and
 * is the Material role with only the listed change, so a screen that switches to it from
 * a hand-written `copy` draws the same text.
 */
@Immutable
data class SelloType(
    val stampSmall: TextStyle = stamp(15),
    val stampLarge: TextStyle = stamp(34),
    val stampHero: TextStyle = stamp(52),
    /** The title on the top band: headlineSmall at 24sp. */
    val barTitle: TextStyle = SelloTypography.headlineSmall.copy(fontSize = 24.sp),
    /** A dialog slip's title: headlineSmall at 22sp. */
    val dialogTitle: TextStyle = SelloTypography.headlineSmall.copy(fontSize = 22.sp),
    /** The name on a list row (a category, goal, investment, schedule or choice). */
    val rowTitle: TextStyle = SelloTypography.bodyLarge.copy(fontWeight = FontWeight.W800),
    /** The title of a compact line: a bulk line, a change row, a chip on the band. */
    val lineTitle: TextStyle = SelloTypography.bodyMedium.copy(fontWeight = FontWeight.W800),
    /** The heading over a group of rows. */
    val groupLabel: TextStyle = SelloTypography.labelMedium.copy(fontWeight = FontWeight.W800),
    /** A chip's or a pill's label. */
    val chipLabel: TextStyle = SelloTypography.bodyMedium.copy(fontWeight = FontWeight.W600),
    /** A figure beside a title, such as a goal's percentage on its pill. */
    val figure: TextStyle = SelloTypography.titleMedium.copy(fontWeight = FontWeight.W900),
    /** The figure that leads a card, such as a goal's percentage on its card. */
    val figureLarge: TextStyle = SelloTypography.headlineSmall.copy(
        fontWeight = FontWeight.W900
    ),
    /** The weights by role, for a span or an amount that keeps its own style. */
    val weight: SelloWeights = SelloWeights()
)

/**
 * The weights the bundled font is set in, by role. The Material roles already carry
 * them; these are for a [androidx.compose.ui.text.SpanStyle] inside a sentence or a
 * `fontWeight` on an amount.
 */
@Immutable
data class SelloWeights(
    /** Running text. */
    val regular: FontWeight = FontWeight.W400,
    /** A quiet label: a chip, a year beside its month, typed text in a field. */
    val soft: FontWeight = FontWeight.W600,
    /** A value on a line, or a fact stressed in a sentence. */
    val strong: FontWeight = FontWeight.W700,
    /** A name or a title, or a word stressed in a heading or note. */
    val emphasis: FontWeight = FontWeight.W800,
    /** The figure a card or line leads with. */
    val figure: FontWeight = FontWeight.W900
)

private fun stamp(size: Int) = TextStyle(
    fontFamily = SairaStencilOne,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    letterSpacing = 0.05.em
)

internal val LocalSelloType = staticCompositionLocalOf { SelloType() }
