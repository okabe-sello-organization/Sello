package com.software.sello.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/** How much a receipt line stands out: softer for what is only expected, strong for a result. */
enum class LineEmphasis { Normal, Soft, Strong }

/**
 * A receipt line: label, dotted leader, value. For two to six facts. Read as one node,
 * "label, value". Put a [MoneyText] in [value] for amounts. A [LineEmphasis.Soft] line
 * draws its label and, by default, its value in the soft ink; a [LineEmphasis.Strong]
 * one sets its label in black weight, and the value should match.
 */
@Composable
fun LeaderLine(
    label: String,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    emphasis: LineEmphasis = LineEmphasis.Normal,
    value: @Composable () -> Unit
) {
    val minHeight = when {
        onClick != null && caption == null -> SelloTheme.spacing.minTouchTarget
        caption != null -> 52.dp
        else -> 36.dp
    }
    val interaction = if (onClick == null) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    LeaderLayout(
        modifier = modifier.fillMaxWidth().then(interaction).heightIn(min = minHeight),
        start = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        painter = icon.painter(),
                        contentDescription = null,
                        tint = SelloTheme.colors.brand,
                        modifier = Modifier.padding(end = 7.dp).size(18.dp)
                    )
                }
                Column {
                    val body = MaterialTheme.typography.bodyLarge
                    val soft = emphasis == LineEmphasis.Soft
                    Text(
                        label,
                        style = if (emphasis == LineEmphasis.Strong) {
                            body.copy(fontWeight = FontWeight.W900)
                        } else {
                            body
                        },
                        color = if (soft) SelloTheme.colors.inkSoft else Color.Unspecified
                    )
                    if (caption != null) {
                        Text(
                            caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = SelloTheme.colors.inkSoft
                        )
                    }
                }
            }
        },
        value = {
            if (emphasis == LineEmphasis.Soft) {
                CompositionLocalProvider(
                    LocalContentColor provides SelloTheme.colors.inkSoft,
                    value
                )
            } else {
                value()
            }
        }
    )
}

/** A [LeaderLine] whose value is plain text, such as a date or a count. */
@Composable
fun LeaderLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    caption: String? = null,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
) {
    LeaderLine(label, modifier, icon, caption, onClick) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.W700,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor.takeOrElse { SelloTheme.colors.ink }
        )
    }
}

/** How a [TotalLine] closes what is above it. */
enum class TotalRule {
    /** A receipt's total: a double rule and a bold label. */
    Double,

    /** A plan's sum: one firm rule and the label as any other line. */
    Single
}

/** The closing line of a receipt: a double rule, a bold label and the total. */
@Composable
fun TotalLine(
    label: String,
    modifier: Modifier = Modifier,
    rule: TotalRule = TotalRule.Double,
    value: @Composable () -> Unit
) {
    val ink = SelloTheme.colors.ink
    LeaderLayout(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .drawBehind {
                val line = 1.dp.toPx()
                if (rule == TotalRule.Single) {
                    drawLine(ink, Offset(0f, line), Offset(size.width, line), line * 2)
                } else {
                    drawLine(ink, Offset(0f, line / 2), Offset(size.width, line / 2), line)
                    drawLine(ink, Offset(0f, line * 2.5f), Offset(size.width, line * 2.5f), line)
                }
            }
            .padding(top = 10.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) {},
        start = {
            val body = MaterialTheme.typography.bodyLarge
            val bold = rule == TotalRule.Double
            Text(label, style = if (bold) body.copy(fontWeight = FontWeight.W700) else body)
        },
        value = value
    )
}

/**
 * Label at the start, value at the end, dotted leader between. The value is never
 * squeezed; the leader gives way first and disappears before the label has to wrap.
 */
@Composable
private fun LeaderLayout(
    modifier: Modifier,
    start: @Composable () -> Unit,
    value: @Composable () -> Unit
) {
    val dots = SelloTheme.colors.outline.copy(alpha = 0.6f)
    Layout(
        modifier = modifier,
        content = {
            start()
            Canvas(Modifier.testTag(LEADER_DOTS_TAG)) {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = dots,
                    start = Offset(stroke / 2, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, stroke * 2))
                )
            }
            value()
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = LeaderGap.roundToPx()
        val minLeader = LeaderMinWidth.roundToPx() + 2 * gap
        val valuePlaceable = measurables[2].measure(loose)
        val startMax = (constraints.maxWidth - valuePlaceable.width - gap).coerceAtLeast(0)
        val startPlaceable = measurables[0].measure(loose.copy(maxWidth = startMax))
        val free = constraints.maxWidth - startPlaceable.width - valuePlaceable.width
        val leaderWidth = if (free >= minLeader) free - 2 * gap else 0
        val leader = measurables[1].measure(
            androidx.compose.ui.unit.Constraints.fixed(leaderWidth, 2.dp.roundToPx())
        )
        // Like v1.1 `.ln`, the row sits on the label's first baseline: the value shares
        // that line and the dots run just above it, even with a caption under the label.
        val startBaseline = startPlaceable[FirstBaseline]
            .takeIf { it != AlignmentLine.Unspecified } ?: (startPlaceable.height / 2)
        val valueBaseline = valuePlaceable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified }
        val baseline = maxOf(startBaseline, valueBaseline ?: 0)
        val startTop = baseline - startBaseline
        val valueTop = valueBaseline?.let { baseline - it }
        val content = maxOf(
            startTop + startPlaceable.height,
            (valueTop ?: 0) + valuePlaceable.height
        )
        val height = maxOf(content, constraints.minHeight)
        val top = (height - content) / 2
        layout(constraints.maxWidth, height) {
            startPlaceable.placeRelative(0, top + startTop)
            valuePlaceable.placeRelative(
                constraints.maxWidth - valuePlaceable.width,
                valueTop?.let { top + it } ?: ((height - valuePlaceable.height) / 2)
            )
            if (leaderWidth > 0) {
                val dotsCentre = top + baseline - LeaderRise.roundToPx()
                leader.placeRelative(startPlaceable.width + gap, dotsCentre - leader.height / 2)
            }
        }
    }
}

internal const val LEADER_DOTS_TAG = "sello:leader-dots"

private val LeaderGap = 6.dp
private val LeaderMinWidth = 12.dp

/** Centre of the dots above the baseline: v1.1's 2 px border raised 4 px. */
private val LeaderRise = 5.dp

/** A small label with a larger value under it, used in twos and threes under a tear line. */
@Composable
fun KeyValue(
    label: String,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    caption: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    value: @Composable () -> Unit
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = horizontalAlignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = SelloTheme.colors.brand,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = SelloTheme.colors.inkSoft
            )
        }
        value()
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = SelloTheme.colors.inkSoft,
                maxLines = 2
            )
        }
    }
}

/** A [KeyValue] whose value is plain text. */
@Composable
fun KeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    caption: String? = null,
    valueColor: Color = Color.Unspecified
) {
    KeyValue(label, modifier, icon, caption) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor.takeOrElse { SelloTheme.colors.ink }
        )
    }
}
