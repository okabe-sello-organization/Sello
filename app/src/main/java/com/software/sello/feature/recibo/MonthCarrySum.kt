package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.software.sello.R
import com.software.sello.designsystem.component.CarryBadge
import com.software.sello.designsystem.component.HelpTerm
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.LineEmphasis
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.signedText
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.date.dateLabels

/**
 * The month's limit while a category carries, under the per-day line: **Límite del mes**
 * as a receipt line, the sum that makes it under it ("5.500.000 base + ↻ 600.000
 * sobrante"), then **Gastado** and the day the figures were read (ADR 0020 CP16, CP27).
 * Which categories carry, and how much each, is on their cells; nothing here is added up.
 * [onBaseHelp] and [onCarryHelp] underline "base" and "sobrante" or "faltante" to explain them.
 */
@Composable
internal fun MonthCarrySum(
    carry: ReciboCarry,
    modifier: Modifier = Modifier,
    onBaseHelp: (() -> Unit)? = null,
    onCarryHelp: (() -> Unit)? = null
) {
    val figures = carry.figures
    val colors = SelloTheme.colors
    Column(
        modifier.fillMaxWidth().padding(top = SelloTheme.spacing.xs).testTag(RECIBO_CARRY_SUM_TAG)
    ) {
        LeaderLine(stringResource(R.string.carry_capacity), emphasis = LineEmphasis.Strong) {
            MoneyText(
                figures.capacity,
                color = if (figures.capacity.sign == MoneySign.Minus) {
                    colors.semantic.loss
                } else {
                    colors.ink
                },
                fontWeight = SelloTheme.type.weight.figure
            )
        }
        if (onBaseHelp == null && onCarryHelp == null) {
            CarrySumCaption(figures)
        } else {
            CarrySumTerms(figures, onBaseHelp, onCarryHelp)
        }
        figures.spent?.let { spent ->
            LeaderLine(stringResource(R.string.carry_spent)) {
                MoneyText(spent, color = colors.ink, fontWeight = SelloTheme.type.weight.strong)
            }
        }
        val dates = dateLabels()
        Text(
            stringResource(R.string.carry_read_day, dates.shortDay(carry.asOf, carry.asOf)),
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.xs)
        )
    }
}

/**
 * "5.500.000 base + ↻ 600.000 sobrante", or "− ↻ 150.000 faltante": the carry in gain or
 * loss ink with the badge the carrying cells wear; a carry of 0 is a sobrante (ADR 0020).
 * It wraps rather than shrinks, so tens of millions still fit.
 */
@Composable
private fun CarrySumCaption(figures: CarryFigures) {
    val colors = SelloTheme.colors
    val deficit = figures.direction == CarryDirection.Deficit
    val ink = when (figures.direction) {
        CarryDirection.Surplus -> colors.semantic.gain
        CarryDirection.Deficit -> colors.semantic.loss
        CarryDirection.Zero -> colors.inkSoft
    }
    val word =
        stringResource(if (deficit) R.string.carry_sum_deficit else R.string.carry_sum_surplus)
    val spoken = stringResource(
        if (deficit) R.string.carry_sum_spoken_deficit else R.string.carry_sum_spoken_surplus,
        figures.base.spoken,
        figures.carryMagnitude.spoken
    )
    val text = buildAnnotatedString {
        append(stringResource(R.string.carry_sum_base, figures.base.signedText(showPlus = false)))
        append(' ')
        // Kept together: a wrap never leaves the sign at the end of a line.
        withStyle(SpanStyle(color = ink, fontWeight = SelloTheme.type.weight.emphasis)) {
            append(if (deficit) '−' else '+')
            append(NO_BREAK)
            appendInlineContent(BADGE, "↻")
            append(NO_BREAK)
            append(figures.carryMagnitude.digits)
        }
        append(' ')
        append(word)
    }
    val style = MaterialTheme.typography.labelMedium
    val badge = with(LocalDensity.current) { (style.fontSize * BADGE_SIZE).toDp() }
    Text(
        text,
        style = style,
        color = colors.inkSoft,
        inlineContent = mapOf(
            BADGE to InlineTextContent(
                Placeholder(BADGE_SIZE.em, BADGE_SIZE.em, PlaceholderVerticalAlign.TextCenter)
            ) { CarryBadge(badge) }
        ),
        modifier = Modifier
            .padding(bottom = SelloTheme.spacing.xs)
            .testTag(RECIBO_CARRY_SUM_CAPTION_TAG)
            .clearAndSetSemantics { contentDescription = spoken }
    )
}

/**
 * [CarrySumCaption] in pieces, so "base" and the carry's word can each be a [HelpTerm]: the
 * base figure, "base", the signed carry with its badge, then "sobrante" or "faltante". It
 * wraps between pieces, so the sign still never ends a line. Read as one sentence, with each
 * underlined word its own button.
 */
@Composable
private fun CarrySumTerms(
    figures: CarryFigures,
    onBaseHelp: (() -> Unit)?,
    onCarryHelp: (() -> Unit)?
) {
    val colors = SelloTheme.colors
    val deficit = figures.direction == CarryDirection.Deficit
    val ink = when (figures.direction) {
        CarryDirection.Surplus -> colors.semantic.gain
        CarryDirection.Deficit -> colors.semantic.loss
        CarryDirection.Zero -> colors.inkSoft
    }
    val word =
        stringResource(if (deficit) R.string.carry_sum_deficit else R.string.carry_sum_surplus)
    val spoken = stringResource(
        if (deficit) R.string.carry_sum_spoken_deficit else R.string.carry_sum_spoken_surplus,
        figures.base.spoken,
        figures.carryMagnitude.spoken
    )
    // "%1$s base": the figure, then the word, in the order the sentence has them.
    val base = figures.base.signedText(showPlus = false)
    val baseSentence = stringResource(R.string.carry_sum_base, base)
    val baseWord = baseSentence.replace(base, "").trim()
    val carryText = buildAnnotatedString {
        withStyle(SpanStyle(color = ink, fontWeight = SelloTheme.type.weight.emphasis)) {
            append(if (deficit) '−' else '+')
            append(NO_BREAK)
            appendInlineContent(BADGE, "↻")
            append(NO_BREAK)
            append(figures.carryMagnitude.digits)
        }
    }
    val style = MaterialTheme.typography.labelMedium
    val badge = with(LocalDensity.current) { (style.fontSize * BADGE_SIZE).toDp() }
    val quiet = Modifier.clearAndSetSemantics {}
    val term = @Composable { text: String, onClick: (() -> Unit)? ->
        if (onClick == null) {
            Text(text, style = style, color = colors.inkSoft, modifier = quiet)
        } else {
            HelpTerm(text, onClick, style = style, color = colors.inkSoft)
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TermGap),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = SelloTheme.spacing.xs)
            .testTag(RECIBO_CARRY_SUM_CAPTION_TAG)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
    ) {
        Text(base, style = style, color = colors.inkSoft, modifier = quiet)
        term(baseWord, onBaseHelp)
        Text(
            carryText,
            style = style,
            color = colors.inkSoft,
            inlineContent = mapOf(
                BADGE to InlineTextContent(
                    Placeholder(BADGE_SIZE.em, BADGE_SIZE.em, PlaceholderVerticalAlign.TextCenter)
                ) { CarryBadge(badge) }
            ),
            modifier = quiet
        )
        term(word, onCarryHelp)
    }
}

/** About a space at the caption's size, so the pieces read as one line of words. */
private val TermGap = 4.dp

private const val BADGE = "carry-badge"

private const val NO_BREAK = '\u00A0'

private const val BADGE_SIZE = 1.15f

const val RECIBO_CARRY_SUM_TAG = "recibo:carry.sum"

const val RECIBO_CARRY_SUM_CAPTION_TAG = "recibo:carry.sum.caption"
