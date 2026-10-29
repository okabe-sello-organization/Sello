package com.software.sello.feature.investments

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A signed return on one line: the arrow, the [percentage] large and the [amount] in
 * words smaller, all in gain or loss ink. With [even] (neither gain nor loss) there is
 * no arrow and the ink is neutral. The amount wraps on its own at large text.
 */
@Composable
internal fun PerformanceLine(
    percentage: String,
    amount: String,
    loss: Boolean,
    modifier: Modifier = Modifier,
    even: Boolean = false
) {
    val colors = SelloTheme.colors
    val ink = when {
        even -> colors.ink
        loss -> colors.semantic.loss
        else -> colors.semantic.gain
    }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        if (!even) {
            Icon(
                (if (loss) SelloIcon.TrendingDown else SelloIcon.TrendingUp).painter(),
                null,
                Modifier.size(26.dp),
                tint = ink
            )
        }
        Text(
            percentage,
            color = ink,
            // Proportional figures: tabular ones give the decimal comma a digit's width.
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.W900,
                fontFeatureSettings = null
            )
        )
        Text(
            amount,
            Modifier.weight(1f, fill = false),
            color = ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800)
        )
    }
}

private val ShadeMix = listOf(1f, 0.68f, 0.42f, 0.22f)

/**
 * The brand mixed into paper for the [rank]-th largest investment, from 0: the circle
 * in its row and its share of the split bar use the same shade. After the fourth the
 * shades repeat in order; the rows carry the exact values.
 */
@Composable
internal fun investmentShade(rank: Int): Color {
    val colors = SelloTheme.colors
    return lerp(colors.paper, colors.brand, ShadeMix[rank.coerceAtLeast(0) % ShadeMix.size])
}

/**
 * One investment in the overview: a [shade] circle with its icon, the name over its
 * kind and valuation date, and on the right the value over its signed change. Without a
 * value the circle is dashed and [missingLabel] and [secondary] read plainly. [divider]
 * rules it off from the next row. Without [onOpen] it is not a button.
 */
@Composable
internal fun InvestmentRow(
    name: String,
    detail: String,
    icon: SelloIcon,
    shade: Color,
    value: MoneyTextValue?,
    missingLabel: String,
    secondary: String,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    loss: Boolean = false,
    divider: Boolean = true,
    even: Boolean = false
) {
    val colors = SelloTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .drawBehind {
                if (divider) {
                    val line = 1.dp.toPx()
                    drawLine(
                        colors.rule,
                        Offset(0f, size.height - line / 2),
                        Offset(size.width, size.height - line / 2),
                        line
                    )
                }
            }
            .then(
                if (onOpen ==
                    null
                ) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onOpen)
                }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).then(
                if (value == null) {
                    Modifier.drawBehind {
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            colors.outline,
                            radius = (size.minDimension - stroke) / 2,
                            style = Stroke(
                                stroke,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                                )
                            )
                        )
                    }
                } else {
                    Modifier.background(shade, CircleShape)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon.painter(),
                null,
                Modifier.size(20.dp),
                tint = if (value == null) colors.inkSoft else colors.paper
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyLarge)
            // It wraps, so "sin valor anotado" is never cut beside "aportaste …".
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (value == null) {
                Text(missingLabel, fontWeight = FontWeight.W800, color = colors.inkSoft)
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            } else {
                MoneyText(
                    value,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.W800
                    )
                )
                val ink = when {
                    even -> colors.inkSoft
                    loss -> colors.semantic.loss
                    else -> colors.semantic.gain
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (!even) {
                        Icon(
                            (if (loss) SelloIcon.TrendingDown else SelloIcon.TrendingUp).painter(),
                            null,
                            Modifier.size(16.dp),
                            tint = ink
                        )
                    }
                    Text(
                        secondary,
                        color = ink,
                        fontWeight = FontWeight.W800,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** Caller-mapped chart coordinates; the design system never derives a financial return. */
@Immutable
internal data class RecordedMark(val id: String, val x: Float, val y: Float, val spoken: String)

/**
 * Recorded values as dots joined by a thin line, and what was put in as a dashed step
 * line; nothing between recorded points is claimed. With [axis] the first and last
 * month are written under it, and with [legend] what the line and the steps are.
 * Each value is also a named row for keyboard and screen-reader users.
 */
@Composable
internal fun RecordedValuesChart(
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
            Row(Modifier.fillMaxWidth().padding(horizontal = SelloTheme.spacing.sm)) {
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
                Modifier.fillMaxWidth().padding(SelloTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
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
                    }.padding(SelloTheme.spacing.sm),
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
