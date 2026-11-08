package com.software.sello.feature.resumen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.FittedMoneyText
import com.software.sello.designsystem.component.HelpTerm
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

@Immutable
data class ComparisonBar(
    val label: String,
    val amount: MoneyTextValue,
    val fraction: Float,
    val current: Boolean
)

/**
 * A question, the difference as its answer with what it means under it, the rate and,
 * under a tear line, both amounts as bars on one scale. [trend] is left out when there
 * is no rate to give, such as against nothing spent. Each bar is one line: its label,
 * the bar, then the exact amount; every bar starts where the widest label ends.
 */
@Composable
internal fun ComparisonSlip(
    question: String,
    difference: MoneyTextValue,
    explanation: String,
    trend: String?,
    basis: String,
    bars: List<ComparisonBar>,
    modifier: Modifier = Modifier,
    trendTerm: String? = null,
    onTrendTerm: () -> Unit = {},
    labelHelp: (@Composable () -> Unit)? = null
) {
    val colors = SelloTheme.colors
    val typography = MaterialTheme.typography
    // The question is the answer's label; a difference has no stamp and is never a loss.
    AnswerSlip(
        label = question,
        amount = difference,
        stamp = null,
        ink = StampInk.Neutral,
        modifier = modifier,
        lossAmount = false,
        labelHelp = labelHelp
    ) {
        Text(
            explanation,
            style = typography.titleMedium,
            modifier = Modifier.padding(top = SelloTheme.spacing.xs)
        )
        if (trend != null) {
            val style = typography.bodyMedium.copy(fontWeight = SelloTheme.type.weight.strong)
            Row(
                Modifier.padding(top = SelloTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs)
            ) {
                Text(trend, style = style, color = colors.inkSoft)
                // [trendTerm] names the rate's days and opens what they mean.
                if (trendTerm != null) {
                    HelpTerm(
                        trendTerm,
                        onTrendTerm,
                        Modifier.testTag(RESUMEN_SAME_DAY_TERM_TAG),
                        style = style,
                        color = colors.inkSoft
                    )
                }
            }
        }
        TearLine()
        Text(
            basis,
            style = typography.labelSmall.copy(fontWeight = SelloTheme.type.weight.regular),
            color = colors.inkSoft
        )
        ComparisonBars(bars, Modifier.padding(top = SelloTheme.spacing.sm))
    }
}

@Composable
internal fun ComparisonBars(bars: List<ComparisonBar>, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    val style = MaterialTheme.typography.bodyMedium
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            bars.forEach { bar ->
                Text(bar.label, style = style, maxLines = 1)
                Box(
                    Modifier.height(BarHeight).background(colors.paperDim, BarShape)
                ) {
                    Box(
                        Modifier.fillMaxWidth(bar.fraction.coerceIn(0f, 1f)).height(BarHeight)
                            .background(if (bar.current) colors.brand else colors.pastBar, BarShape)
                    )
                }
                MoneyText(
                    bar.amount,
                    textStyle = style.copy(fontWeight = SelloTheme.type.weight.emphasis)
                )
            }
        }
    ) { measurables, constraints ->
        val gap = BarGap.roundToPx()
        val rows = measurables.chunked(3)
        val free = constraints.copy(minWidth = 0, minHeight = 0)
        val labels = rows.map { it[0].measure(free) }
        val amounts = rows.map { it[2].measure(free) }
        val labelWidth = labels.maxOfOrNull { it.width } ?: 0
        val amountWidth = amounts.maxOfOrNull { it.width } ?: 0
        val barWidth = (constraints.maxWidth - labelWidth - amountWidth - gap * 2).coerceAtLeast(0)
        val tracks = rows.map { it[1].measure(Constraints.fixed(barWidth, BarHeight.roundToPx())) }
        val heights = rows.indices.map {
            maxOf(labels[it].height, amounts[it].height, tracks[it].height)
        }
        val height = heights.sum() + RowGap.roundToPx() * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.indices.forEach { index ->
                val row = heights[index]
                labels[index].place(0, y + (row - labels[index].height) / 2)
                tracks[index].place(labelWidth + gap, y + (row - tracks[index].height) / 2)
                amounts[index].place(
                    constraints.maxWidth - amounts[index].width,
                    y + (row - amounts[index].height) / 2
                )
                y += row + RowGap.roundToPx()
            }
        }
    }
}

private val BarHeight = 14.dp

private val BarGap = 10.dp

private val RowGap = 10.dp

/** A comparison bar lies flat, so all four corners round (the bar token rounds a column's top). */
private val BarShape = RoundedCornerShape(4.dp)

/**
 * A category ranked by what it spent, with its bar. With [percent] its share is written
 * beside its name (Tu año); without [onClick] it opens nothing and has no chevron.
 */
@Composable
internal fun SpendingRankRow(
    name: String,
    icon: SelloIcon,
    amount: MoneyTextValue,
    share: Float,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    percent: String? = null
) {
    val open = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).then(open),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        CategoryCircle(icon, 1f, size = 36.dp, animate = false)
        // The name and its bar are one block, so the amount beside them is centred on both.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = SelloTheme.type.weight.strong,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (percent != null) {
                    Text(
                        percent,
                        style = MaterialTheme.typography.bodySmall,
                        color = SelloTheme.colors.inkSoft,
                        modifier = Modifier.padding(start = SelloTheme.spacing.xs)
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(
                    6.dp
                ).background(SelloTheme.colors.paperDim, SelloTheme.shapes.control)
            ) {
                Box(
                    Modifier.fillMaxWidth(
                        share.coerceIn(0f, 1f)
                    ).height(6.dp).background(SelloTheme.colors.brand, SelloTheme.shapes.control)
                )
            }
        }
        MoneyText(amount)
        if (onClick != null) {
            Icon(
                SelloIcon.ChevronRight.painter(),
                null,
                Modifier.size(18.dp),
                SelloTheme.colors.outline
            )
        }
    }
}
