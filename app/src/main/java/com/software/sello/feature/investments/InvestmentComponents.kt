package com.software.sello.feature.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.dashedOutline
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.InvestmentInk
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A signed return on one line: the arrow, the [percentage] large and the [amount] in
 * words smaller, all in gain or loss ink. With [even] (neither gain nor loss) there is
 * no arrow and the ink is neutral. A long amount wraps on its own.
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

/**
 * The colour of the [rank]-th investment in the list, from 0: the circle in its row and
 * its part of the split bar use the same one (the design system's investment inks).
 */
@Composable
internal fun investmentInk(rank: Int): InvestmentInk = SelloTheme.colors.investmentInk(rank)

/**
 * One investment in the overview: a [shade] circle with its icon in the colour written on it, the name over its
 * kind and valuation date, and on the right the value over its signed change. Without a
 * value the circle is dashed and [missingLabel] and [secondary] read plainly. [divider]
 * rules it off from the next row. Without [onOpen] it is not a button.
 */
@Composable
internal fun InvestmentRow(
    name: String,
    detail: String,
    icon: SelloIcon,
    shade: InvestmentInk,
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
                    Modifier.dashedOutline(colors.outline, 2.dp, CircleShape)
                } else {
                    Modifier.background(shade.fill, CircleShape)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon.painter(),
                null,
                Modifier.size(20.dp),
                tint = if (value == null) colors.inkSoft else shade.on
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

/** A gridline at [y] in 0..1 and its short [label]: "2,5 M". */
@Immutable
internal data class GridMark(val y: Float, val label: String)

/**
 * Recorded values as dots joined by a thin line, and what was put in as a dashed step
 * line; nothing between recorded points is claimed. Faint [grid] lines carry short
 * amounts at the left; [axis] writes the first, middle and last month under it and
 * [legend] what the line and the steps are. The [selectedId] dot is drawn larger in ink
 * with a guide down the chart; a tap picks the dot nearest in time. Each dot is also a
 * named radio button for screen readers, with no visible text of its own.
 */
@Composable
internal fun RecordedValuesChart(
    values: List<RecordedMark>,
    contributions: List<RecordedMark>,
    grid: List<GridMark>,
    summary: String,
    selectedId: String?,
    onSelect: (String) -> Unit,
    axis: Triple<String, String, String>,
    legend: Pair<String, String>,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.outline)
    val density = LocalDensity.current
    // The gutter fits the widest amount, so a long label never runs into the lines.
    val labelWidth = with(density) {
        (grid.maxOfOrNull { measurer.measure(it.label, labelStyle).size.width } ?: 0).toDp()
    }
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(CHART_HEIGHT).semantics { contentDescription = summary }
        ) {
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { CHART_HEIGHT.toPx() }
            val gutter = with(density) { labelWidth.toPx() + LABEL_GAP.toPx() }
            val inset = with(density) { INSET.toPx() }
            fun point(mark: RecordedMark) = Offset(
                gutter + mark.x.coerceIn(0f, 1f) * (width - gutter - inset),
                inset + (1f - mark.y.coerceIn(0f, 1f)) * (height - 2 * inset)
            )
            val selected = values.firstOrNull { it.id == selectedId }
            Canvas(
                Modifier.fillMaxSize().pointerInput(values, width, gutter) {
                    detectTapGestures { tap ->
                        values.minByOrNull { kotlin.math.abs(point(it).x - tap.x) }
                            ?.let { onSelect(it.id) }
                    }
                }
            ) {
                grid.forEach { mark ->
                    val y = inset + (1f - mark.y) * (size.height - 2 * inset)
                    drawLine(colors.rule, Offset(gutter, y), Offset(size.width, y), 1.dp.toPx())
                    val text = measurer.measure(mark.label, labelStyle)
                    drawText(text, topLeft = Offset(0f, y - text.size.height / 2f))
                }
                selected?.let {
                    val x = point(it).x
                    drawLine(
                        colors.ink,
                        Offset(x, 0f),
                        Offset(x, size.height),
                        1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(2.dp.toPx(), 3.dp.toPx())
                        )
                    )
                }
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
                contributions.lastOrNull()?.let { steps.lineTo(size.width - inset, point(it).y) }
                drawPath(
                    steps,
                    colors.outline,
                    style = Stroke(
                        2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                        )
                    )
                )
                val line = Path()
                values.forEachIndexed { index, mark ->
                    val p = point(mark)
                    if (index == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
                }
                drawPath(line, colors.brand, style = Stroke(2.dp.toPx()))
                values.forEach { mark ->
                    val picked = mark.id == selectedId
                    val radius = if (picked) 6.dp.toPx() else 4.dp.toPx()
                    drawCircle(colors.paper, radius + 2.dp.toPx(), point(mark))
                    drawCircle(if (picked) colors.ink else colors.brand, radius, point(mark))
                }
            }
            // One named, selectable spot per dot for screen readers; taps go to the canvas.
            Box(Modifier.fillMaxSize().selectableGroup().semantics { isTraversalGroup = true }) {
                values.forEachIndexed { index, mark ->
                    val p = point(mark)
                    val half = with(density) { (DOT_TARGET / 2).toPx() }
                    Box(
                        Modifier
                            .offset { IntOffset((p.x - half).toInt(), (p.y - half).toInt()) }
                            .size(DOT_TARGET)
                            .testTag("investment.chart.dot.${mark.id}")
                            .semantics {
                                // Read by date, not by height.
                                traversalIndex = index.toFloat()
                                contentDescription = mark.spoken
                                this.selected = mark.id == selectedId
                                role = Role.RadioButton
                                onClick {
                                    onSelect(mark.id)
                                    true
                                }
                            }
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(
                start = labelWidth + LABEL_GAP,
                top = 2.dp
            )
        ) {
            val style = MaterialTheme.typography.bodySmall
            Text(axis.first, Modifier.weight(1f), style = style, color = colors.inkSoft)
            Text(
                axis.second,
                Modifier.weight(1f),
                style = style,
                color = colors.inkSoft,
                textAlign = TextAlign.Center
            )
            Text(
                axis.third,
                Modifier.weight(1f),
                style = style,
                color = colors.ink,
                fontWeight = FontWeight.W800,
                textAlign = TextAlign.End
            )
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
        ) {
            LegendItem(legend.first, dashed = false, color = colors.brand)
            LegendItem(legend.second, dashed = true, color = colors.outline)
        }
    }
}

private val CHART_HEIGHT = 150.dp
private val LABEL_GAP = 6.dp
private val INSET = 8.dp
private val DOT_TARGET = 24.dp

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
