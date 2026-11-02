package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.software.sello.R
import com.software.sello.designsystem.component.CarryBadge
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.LineEmphasis
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.date.dateLabels

/**
 * The month's limit while a category carries, under the per-day line: **Límite del mes**
 * as a receipt line, the sum that makes it under it ("5.500.000 base + ↻ 600.000
 * sobrante"), then **Gastado** and the day the figures were read (ADR 0020 CP16, CP27).
 * Which categories carry, and how much each, is on their cells; nothing here is added up.
 */
@Composable
internal fun MonthCarrySum(carry: ReciboCarry, modifier: Modifier = Modifier) {
    val figures = carry.figures
    val colors = SelloTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 4.dp).testTag(RECIBO_CARRY_SUM_TAG)) {
        LeaderLine(stringResource(R.string.carry_capacity), emphasis = LineEmphasis.Strong) {
            MoneyText(
                figures.capacity,
                color = if (figures.capacity.sign == MoneySign.Minus) {
                    colors.semantic.loss
                } else {
                    colors.ink
                },
                fontWeight = FontWeight.W900
            )
        }
        CarrySumCaption(figures)
        figures.spent?.let { spent ->
            LeaderLine(stringResource(R.string.carry_spent)) {
                MoneyText(spent, color = colors.ink, fontWeight = FontWeight.W700)
            }
        }
        val dates = dateLabels()
        Text(
            stringResource(R.string.carry_read_day, dates.shortDay(carry.asOf, carry.asOf)),
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = 4.dp)
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
    val word = stringResource(if (deficit) R.string.carry_sum_deficit else R.string.carry_sum_surplus)
    val spoken = stringResource(
        if (deficit) R.string.carry_sum_spoken_deficit else R.string.carry_sum_spoken_surplus,
        figures.base.spoken,
        figures.carryMagnitude.spoken
    )
    val text = buildAnnotatedString {
        append(stringResource(R.string.carry_sum_base, figures.base.text()))
        append(' ')
        withStyle(SpanStyle(color = ink, fontWeight = FontWeight.W800)) {
            append(if (deficit) '−' else '+')
            append(' ')
            appendInlineContent(BADGE, "↻")
            append(' ')
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
            .padding(bottom = 4.dp)
            .testTag(RECIBO_CARRY_SUM_CAPTION_TAG)
            .clearAndSetSemantics { contentDescription = spoken }
    )
}

private const val BADGE = "carry-badge"

private const val BADGE_SIZE = 1.15f

const val RECIBO_CARRY_SUM_TAG = "recibo:carry.sum"

const val RECIBO_CARRY_SUM_CAPTION_TAG = "recibo:carry.sum.caption"
