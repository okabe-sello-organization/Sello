package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.software.sello.designsystem.theme.SelloAlpha
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.designsystem.theme.SelloWeights

enum class MoneySign { None, Plus, Minus }

/**
 * An amount already formatted by the application: the design system draws it and
 * never computes, rounds or abbreviates it.
 *
 * @param symbol the currency code drawn small and raised before the figure, such as
 * `USD`; empty for pesos, which carry no mark.
 * @param digits the full grouped figure without sign or symbol, such as `937.200`.
 * @param spoken what a screen reader says, with the currency: `937.200 pesos`.
 */
@Immutable
data class MoneyTextValue(
    val sign: MoneySign,
    val symbol: String,
    val digits: String,
    val spoken: String
)

/** Hero 60sp, Total 44sp, Card 32sp, Title 19sp, Line 14sp. Line drops the currency code. */
enum class MoneyStyle { Hero, Total, Card, Title, Line }

/**
 * The only way an amount is drawn: tabular figures, a raised currency code, tightened
 * thousands points and a true minus. A figure too wide for its place wraps between
 * groups, never clips; Hero first steps down to Total.
 */
@Composable
fun MoneyText(
    value: MoneyTextValue,
    modifier: Modifier = Modifier,
    style: MoneyStyle = MoneyStyle.Line,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textStyle: TextStyle? = null,
    singleLine: Boolean = false,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val ink = color.takeOrElse { LocalContentColor.current }
    val semantics = modifier.clearAndSetSemantics {
        contentDescription = value.spoken
        text = AnnotatedString(moneyPlainText(value, style))
    }
    if (textStyle != null) {
        val tabularStyle = textStyle.copy(
            fontFeatureSettings = listOfNotNull(
                textStyle.fontFeatureSettings?.takeIf { it.isNotBlank() },
                "tnum"
            ).distinct().joinToString(",")
        )
        Text(
            moneyAnnotatedString(value, style, tabularStyle, ink),
            semantics,
            ink,
            style = tabularStyle,
            softWrap = !singleLine,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            onTextLayout = onTextLayout
        )
        return
    }
    if (style != MoneyStyle.Hero) {
        val textStyle = style.textStyle(fontWeight)
        Text(moneyAnnotatedString(value, style, textStyle, ink), semantics, ink, style = textStyle)
        return
    }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = semantics) {
        val hero = style.textStyle(fontWeight)
        val heroText = moneyAnnotatedString(value, style, hero, ink)
        val fits = measurer.measure(heroText, hero, softWrap = false).size.width <=
            constraints.maxWidth
        val chosen = if (fits) hero else MoneyStyle.Total.textStyle(fontWeight)
        Text(moneyAnnotatedString(value, style, chosen, ink), color = ink, style = chosen)
    }
}

@Composable
private fun MoneyStyle.textStyle(weight: FontWeight?): TextStyle {
    val typography = MaterialTheme.typography
    val base = when (this) {
        MoneyStyle.Hero -> typography.displayLarge

        MoneyStyle.Total -> typography.displayMedium

        MoneyStyle.Card -> typography.displaySmall

        MoneyStyle.Title -> typography.titleLarge

        MoneyStyle.Line -> typography.bodyLarge.copy(
            fontWeight = SelloTheme.type.weight.strong,
            fontFeatureSettings = "tnum"
        )
    }
    return if (weight == null) base else base.copy(fontWeight = weight)
}

private const val TRUE_MINUS = "\u2212"
private const val ZERO_WIDTH_SPACE = "\u200B"
private const val GROUP_POINT = '.'

/** A space between a currency code and its figure that never becomes a line break. */
private const val MARK_GAP = "\u00A0"
private const val MARK_GAP_EXTRA_EM = 0.2f
private const val DECIMAL_COMMA = ','

/**
 * The raised currency code: [SelloWeights.soft] at [SelloAlpha.secondary], 0.56 of the figure's
 * size, untracked. Read from the token defaults: the string is built outside composition.
 */
private val MarkWeight = SelloWeights().soft
private val MarkAlpha = SelloAlpha().secondary
private val MarkSize = 0.56.em
private val NoTracking = 0.em

/** How far a thousands point or decimal comma is pulled towards its neighbours, per side. */
private const val POINT_TIGHTENING_EM = 0.13f

private fun signText(sign: MoneySign) = when (sign) {
    MoneySign.None -> ""
    MoneySign.Plus -> "+"
    MoneySign.Minus -> TRUE_MINUS
}

/**
 * The amount as a sentence writes it, with the sign [MoneyText] draws: "+80.000",
 * "\u2212150.000" (a true minus), or "937.200". No currency code: the sentence names
 * the currency if it needs to. With [showPlus] false a gain is written bare ("80.000")
 * and only a negative figure keeps its sign, for sentences that state a limit or a
 * balance rather than a change.
 */
fun MoneyTextValue.signedText(showPlus: Boolean = true): String =
    (if (sign == MoneySign.Plus && !showPlus) "" else signText(sign)) + digits

private fun markText(value: MoneyTextValue, style: MoneyStyle) =
    if (style == MoneyStyle.Line || value.symbol.isEmpty()) "" else value.symbol + MARK_GAP

/** The figure as plain characters, for tests and semantics. */
internal fun moneyPlainText(value: MoneyTextValue, style: MoneyStyle): String =
    signText(value.sign) + markText(value, style) + value.digits

internal fun moneyAnnotatedString(
    value: MoneyTextValue,
    style: MoneyStyle,
    textStyle: TextStyle,
    ink: Color = Color.Unspecified
): AnnotatedString = buildAnnotatedString {
    val tracking = if (textStyle.letterSpacing.isEm) textStyle.letterSpacing.value else 0f
    val tight = SpanStyle(letterSpacing = (tracking - POINT_TIGHTENING_EM).em)
    append(signText(value.sign))
    if (style != MoneyStyle.Line && value.symbol.isNotEmpty()) {
        val mark = SpanStyle(
            fontSize = MarkSize,
            fontWeight = MarkWeight,
            baselineShift = BaselineShift(0.5f),
            letterSpacing = NoTracking,
            color = if (ink.isSpecified) ink.copy(alpha = ink.alpha * MarkAlpha) else ink
        )
        withStyle(mark) { append(value.symbol) }
        // Full-size and widened: this font's own space is too thin beside a heavy figure.
        withStyle(SpanStyle(letterSpacing = MARK_GAP_EXTRA_EM.em)) { append(MARK_GAP) }
    }
    value.digits.forEachIndexed { index, char ->
        val isMark = char == GROUP_POINT || char == DECIMAL_COMMA
        val next = value.digits.getOrNull(index + 1)
        if (isMark || next == GROUP_POINT || next == DECIMAL_COMMA) {
            withStyle(tight) { append(char) }
        } else {
            append(char)
        }
        // A long figure may break after a group, never inside one.
        if (char ==
            GROUP_POINT
        ) {
            withStyle(SpanStyle(letterSpacing = NoTracking)) { append(ZERO_WIDTH_SPACE) }
        }
    }
}
