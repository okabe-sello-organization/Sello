package com.software.sello.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/** Past is a finished period, Current the one in progress, Over one above its limit, Upcoming one not yet happened. */
enum class ChartPeriod { Past, Current, Over, Upcoming }

/**
 * One mark of a chart.
 *
 * @param id what a selection reports. The app looks the exact amount up by it; nothing
 * is ever read back from where a tap landed.
 * @param value used only to size the mark. It may be drawn imprecisely.
 * @param spoken the mark in words with its exact amount, such as "sábado, 86.000 pesos".
 * @param valueLabel a short figure above the mark, for the few that carry one.
 * @param shortAxisLabel what the axis shows instead of [axisLabel] when, at the text size
 * in use, a column is too narrow to hold [axisLabel] whole, such as "2" for "sem 2". It
 * is never spoken: [spoken] says everything.
 */
@Immutable
data class ChartPoint(
    val id: String,
    val axisLabel: String,
    val value: Long,
    val spoken: String,
    val period: ChartPeriod = ChartPeriod.Past,
    val valueLabel: String? = null,
    /** Solid recorded part below this value, striped planning part above it. Geometry only. */
    val stripedFromValue: Long? = null,
    /** Optional reference for this period, such as a historical monthly limit. */
    val referenceValue: Long? = null,
    val shortAxisLabel: String? = null
)

/**
 * The single reference line a chart may carry, with its own [label]. The label may be
 * shortened like an axis; [spoken] says the exact amount, such as "Límite 350.000 pesos".
 */
@Immutable
data class ChartReference(val value: Long, val label: String, val spoken: String = label)

/** The smallest a chart may be. */
val ChartMinHeight = 130.dp

/**
 * Columns for a handful of periods. Each column is a selectable item that says its
 * [ChartPoint.spoken] text, so the chart works with a screen reader, a keyboard or
 * Switch Access; [summary] describes it as a whole. With no [points] it shows
 * [emptyText] at the same size.
 */
@Composable
fun ColumnsChart(
    points: List<ChartPoint>,
    summary: String,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onSelect: ((String) -> Unit)? = null,
    reference: ChartReference? = null,
    height: Dp = 150.dp,
    emptyText: String? = null,
    stripedCurrent: Boolean = false
) {
    ChartFrame(points, summary, modifier, selectedId, onSelect, reference, height, emptyText) {
            point,
            fraction,
            selected,
            dimmed,
            barArea
        ->
        val colors = SelloTheme.colors
        val ink = when (point.period) {
            ChartPeriod.Past -> colors.pastBar
            ChartPeriod.Current, ChartPeriod.Upcoming -> colors.brand
            ChartPeriod.Over -> colors.semantic.loss
        }.copy(alpha = if (dimmed) DIMMED_ALPHA else 1f)
        val barHeight = if (fraction > 0f) (barArea * fraction).coerceAtLeast(2.dp) else 0.dp
        Box(
            modifier = Modifier
                .fillMaxWidth(COLUMN_WIDTH_SHARE)
                .height(barHeight)
                .drawBehind {
                    val corner = CornerRadius(4.dp.toPx())
                    val bar = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = 0f,
                                top = 0f,
                                right = size.width,
                                bottom = size.height,
                                topLeftCornerRadius = corner,
                                topRightCornerRadius = corner
                            )
                        )
                    }
                    if (point.period == ChartPeriod.Upcoming) {
                        // Not yet happened: an outline, never a solid bar.
                        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                        drawPath(bar, ink, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                    } else {
                        drawPath(
                            bar,
                            ink.copy(
                                alpha = if (stripedCurrent &&
                                    point.period == ChartPeriod.Current
                                ) {
                                    0.15f
                                } else {
                                    ink.alpha
                                }
                            )
                        )
                        if ((stripedCurrent && point.period == ChartPeriod.Current) ||
                            point.stripedFromValue != null
                        ) {
                            clipPath(bar) {
                                val topHeight =
                                    size.height *
                                        (
                                            1f -
                                                chartFraction(
                                                    point.stripedFromValue ?: 0,
                                                    point.value
                                                )
                                            )
                                clipRect(bottom = topHeight) {
                                    drawRect(colors.paper)
                                    drawRect(ink.copy(alpha = 0.15f))
                                    var x = -size.height
                                    while (x < size.width) {
                                        drawLine(
                                            ink,
                                            Offset(x, size.height),
                                            Offset(x + size.height, 0f),
                                            2.dp.toPx()
                                        )
                                        x += 7.dp.toPx()
                                    }
                                }
                            }
                        }
                    }
                    if (selected) drawPath(bar, colors.ink, style = Stroke(2.dp.toPx()))
                }
        )
    }
}

private fun scaleMax(points: List<ChartPoint>, reference: ChartReference?): Long = maxOf(
    points.maxOfOrNull { maxOf(it.value, it.referenceValue ?: 0) } ?: 0L,
    reference?.value ?: 0L
)

@Composable
private fun ChartFrame(
    points: List<ChartPoint>,
    summary: String,
    modifier: Modifier,
    selectedId: String?,
    onSelect: ((String) -> Unit)?,
    reference: ChartReference?,
    height: Dp,
    emptyText: String?,
    labelGap: Dp = 2.dp,
    behind: @Composable (labelRoom: Dp) -> Unit = {},
    mark: @Composable (
        ChartPoint,
        fraction: Float,
        selected: Boolean,
        dimmed: Boolean,
        barArea: Dp
    ) -> Unit
) {
    val colors = SelloTheme.colors
    val chartHeight = height.coerceAtLeast(ChartMinHeight)
    if (points.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxWidth().height(chartHeight + AxisRoom)
        ) {
            Text(
                text = emptyText ?: summary,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.inkSoft,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    val max = scaleMax(points, reference)
    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = summary }) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val barArea = maxHeight - LabelRoom
            behind(LabelRoom)
            if (points.any { it.referenceValue != null }) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = size.width / points.size
                    val area = size.height - LabelRoom.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                    points.forEachIndexed { index, point ->
                        point.referenceValue?.let { value ->
                            val y = LabelRoom.toPx() + area * (1f - chartFraction(value, max))
                            drawLine(
                                colors.inkSoft,
                                Offset(step * index, y),
                                Offset(step * (index + 1), y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dash
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
                points.forEach { point ->
                    val selected = point.id == selectedId
                    val pick = if (onSelect == null) {
                        Modifier
                    } else {
                        Modifier.selectable(selected, role = Role.RadioButton) {
                            onSelect(point.id)
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(pick)
                            .clearAndSetSemantics { contentDescription = point.spoken }
                    ) {
                        if (point.valueLabel != null) {
                            Text(
                                text = point.valueLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink,
                                maxLines = 1,
                                modifier = Modifier.padding(bottom = labelGap)
                            )
                        }
                        mark(
                            point,
                            chartFraction(point.value, max),
                            selected,
                            selectedId != null && !selected,
                            barArea
                        )
                    }
                }
            }
            if (reference != null) {
                val y = LabelRoom + barArea * (1f - chartFraction(reference.value, max))
                Canvas(Modifier.fillMaxWidth().offset(y = y)) {
                    drawLine(
                        color = colors.ink,
                        start = Offset.Zero,
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                        )
                    )
                }
                Text(
                    text = reference.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink,
                    modifier = Modifier
                        .semantics { contentDescription = reference.spoken }
                        .offset(y = (y - LabelRoom / 2).coerceAtLeast(0.dp))
                        .background(colors.paper)
                        .padding(horizontal = 4.dp)
                )
            }
        }
        val axis = Modifier.fillMaxWidth().clearAndSetSemantics {}
        if (points.any { it.shortAxisLabel != null }) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelSmall
            BoxWithConstraints(axis) {
                val column = constraints.maxWidth / points.size
                // One decision for the whole axis, so its labels never mix long and short.
                val short = points.any {
                    it.shortAxisLabel != null && !measurer.fitsWhole(it.axisLabel, style, column)
                }
                AxisLabels(points, short)
            }
        } else {
            AxisLabels(points, short = false, modifier = axis)
        }
    }
}

@Composable
private fun AxisLabels(points: List<ChartPoint>, short: Boolean, modifier: Modifier = Modifier) {
    Row(modifier) {
        points.forEach { point ->
            Text(
                text = if (short) point.shortAxisLabel ?: point.axisLabel else point.axisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SelloTheme.colors.inkSoft,
                textAlign = TextAlign.Center,
                // Large text on a narrow column wraps "sem 2" rather than cutting it.
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(top = 4.dp)
            )
        }
    }
}

/** Whether [text] fits a column [width] pixels wide in two lines without breaking a word. */
private fun TextMeasurer.fitsWhole(text: String, style: TextStyle, width: Int): Boolean {
    val wrapped = measure(text, style, maxLines = 2, constraints = Constraints(maxWidth = width))
    return !wrapped.multiParagraph.didExceedMaxLines &&
        text.split(' ').all { measure(it, style).size.width <= width }
}

private val LabelRoom = 18.dp
private val AxisRoom = 20.dp
private const val COLUMN_WIDTH_SHARE = 0.62f
private const val DIMMED_ALPHA = 0.5f
