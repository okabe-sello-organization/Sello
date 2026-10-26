package com.software.sello.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/** Fractions are approximate geometry only. The caller provides exact speech and adjacent figures. */
@Immutable
data class SplitSegment(
    val fraction: Float,
    val spoken: String,
    val color: Color,
    val striped: Boolean = false
)

/**
 * One bar split by [segments], with [gap] of paper between them (Inversiones uses 2dp).
 * The exact values belong in the lines or rows next to it.
 */
@Composable
fun SplitBar(segments: List<SplitSegment>, modifier: Modifier = Modifier, gap: Dp = 0.dp) {
    require(segments.all { it.fraction.isFinite() && it.fraction >= 0f })
    val total = segments.sumOf { it.fraction.toDouble() }.toFloat()
    val summary = segments.joinToString(". ") { it.spoken }
    Canvas(
        modifier.fillMaxWidth().height(14.dp).clip(SelloTheme.shapes.control).semantics {
            contentDescription =
                summary
        }
    ) {
        if (total == 0f) return@Canvas
        val drawn = segments.filter { it.fraction > 0f }
        val space = gap.toPx()
        val room = (size.width - space * (drawn.size - 1)).coerceAtLeast(0f)
        var start = 0f
        drawn.forEach { segment ->
            val width = room * (segment.fraction / total)
            if (segment.striped) {
                clipRect(start, 0f, start + width, size.height) {
                    drawRect(
                        segment.color.copy(alpha = 0.15f),
                        Offset(start, 0f),
                        androidx.compose.ui.geometry.Size(width, size.height)
                    )
                    var x = start - size.height
                    while (x < start + width) {
                        drawLine(
                            segment.color,
                            Offset(x, size.height),
                            Offset(x + size.height, 0f),
                            2.dp.toPx()
                        )
                        x += 7.dp.toPx()
                    }
                }
            } else {
                drawRect(
                    segment.color,
                    Offset(start, 0f),
                    androidx.compose.ui.geometry.Size(width, size.height)
                )
            }
            start += width + space
        }
    }
}

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
fun ComparisonSlip(
    question: String,
    difference: MoneyTextValue,
    explanation: String,
    trend: String?,
    basis: String,
    bars: List<ComparisonBar>,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    val typography = MaterialTheme.typography
    Slip(modifier.fillMaxWidth()) {
        Text(
            question,
            style = typography.bodyLarge,
            fontWeight = FontWeight.W600,
            color = colors.inkSoft,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        FittedMoneyText(difference)
        Text(
            explanation,
            style = typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (trend != null) {
            Text(
                trend,
                style = typography.bodyMedium,
                fontWeight = FontWeight.W700,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        TearLine()
        Text(
            basis,
            style = typography.labelSmall.copy(fontWeight = FontWeight.W400),
            color = colors.inkSoft
        )
        ComparisonBars(bars, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ComparisonBars(bars: List<ComparisonBar>, modifier: Modifier) {
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
                MoneyText(bar.amount, textStyle = style.copy(fontWeight = FontWeight.W800))
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
private val BarShape = RoundedCornerShape(4.dp)

@Composable
fun SpendingRankRow(
    name: String,
    icon: SelloIcon,
    amount: MoneyTextValue,
    share: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().heightIn(
            min = 52.dp
        ).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryCircle(icon, 1f, size = 36.dp, animate = false)
        // The name and its bar are one block, so the amount beside them is centred on both.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W700)
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
        Icon(
            SelloIcon.ChevronRight.painter(),
            null,
            Modifier.size(18.dp),
            SelloTheme.colors.outline
        )
    }
}

/** Caller-mapped chart coordinates; the design system never derives a financial return. */
@Immutable
data class RecordedMark(val id: String, val x: Float, val y: Float, val spoken: String)

/**
 * Recorded values as dots joined by a thin line, and what was put in as a dashed step
 * line; nothing between recorded points is claimed. With [axis] the first and last
 * month are written under it, and with [legend] what the line and the steps are.
 * Each value is also a named row for keyboard and screen-reader users.
 */
@Composable
fun RecordedValuesChart(
    values: List<RecordedMark>,
    contributions: List<RecordedMark>,
    summary: String,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    axis: Pair<String, String>? = null,
    legend: Pair<String, String>? = null
) {
    val colors = SelloTheme.colors
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = summary }) {
            fun point(mark: RecordedMark) = Offset(
                mark.x.coerceIn(0f, 1f) * (size.width - 16.dp.toPx()) + 8.dp.toPx(),
                (1f - mark.y.coerceIn(0f, 1f)) * (size.height - 16.dp.toPx()) + 8.dp.toPx()
            )
            val steps = Path()
            contributions.forEachIndexed { index, mark ->
                val p = point(mark)
                if (index == 0) {
                    steps.moveTo(p.x, p.y)
                } else {
                    steps.lineTo(p.x, point(contributions[index - 1]).y)
                    steps.lineTo(p.x, p.y)
                }
            }
            drawPath(
                steps,
                colors.inkSoft,
                style = Stroke(
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                )
            )
            val line = Path()
            values.forEachIndexed { index, mark ->
                val p = point(mark)
                if (index == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
            }
            drawPath(line, colors.brand, style = Stroke(1.5.dp.toPx()))
            values.forEach { mark ->
                drawCircle(
                    if (mark.id ==
                        selectedId
                    ) {
                        colors.ink
                    } else {
                        colors.brand
                    },
                    if (mark.id ==
                        selectedId
                    ) {
                        6.dp.toPx()
                    } else {
                        4.dp.toPx()
                    },
                    point(mark)
                )
            }
        }
        axis?.let { (start, end) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(
                    start,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft
                )
                Text(
                    end,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink,
                    fontWeight = FontWeight.W800
                )
            }
        }
        legend?.let { (value, put) ->
            FlowRow(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(value, dashed = false, color = colors.brand)
                LegendItem(put, dashed = true, color = colors.inkSoft)
            }
        }
        // Named controls expose each real recorded value to keyboard and screen-reader users.
        Column(Modifier.selectableGroup()) {
            values.forEach { mark ->
                Text(
                    mark.spoken,
                    Modifier.fillMaxWidth().heightIn(
                        min = 48.dp
                    ).selectable(mark.id == selectedId, role = Role.RadioButton) {
                        onSelect(mark.id)
                    }.padding(8.dp),
                    color = colors.ink
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, dashed: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(18.dp).height(10.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                2.dp.toPx(),
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                } else {
                    null
                }
            )
        }
        Text(
            label,
            Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft
        )
    }
}
