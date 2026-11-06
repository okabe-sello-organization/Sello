package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.delay

/** What a movement row's mark says: money in, money out, or a new value. */
enum class MovementTone { In, Out, Value }

/**
 * A figure under a movement's amount: a goal's "saldo 1.310.000", an investment's
 * "ganancia +228.000" in [color], or a label alone ("sin ganancia") when [value] is null.
 */
@Immutable
data class MovementBalance(
    val label: String,
    val value: MoneyTextValue?,
    val color: Color = Color.Unspecified
)

/**
 * One movement (docs/design/system/components.html#c-MovementRow): its mark, [title] over [detail],
 * and the amount at the end with [balance] under it. A movement that earned a stamp shows the stamp
 * as its mark. [rule] draws the line under the row; the last row of a group leaves it out.
 */
@Composable
fun MovementRow(
    title: String,
    detail: String,
    amount: MoneyTextValue,
    modifier: Modifier = Modifier,
    milestone: MilestoneSpec? = null,
    icon: SelloIcon = SelloIcon.Add,
    annotation: String? = null,
    tone: MovementTone = MovementTone.In,
    amountColor: Color = Color.Unspecified,
    balance: MovementBalance? = null,
    rule: Boolean = true
) {
    val colors = SelloTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (rule) {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(colors.rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
            }
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (milestone != null) {
            MilestonePlace(milestone, diameter = 34.dp, labelSize = 9.sp, innerRing = false)
        } else {
            val (fill, ink) = when (tone) {
                MovementTone.In -> colors.semantic.gainSoft to colors.semantic.gain
                MovementTone.Out -> colors.paperDim to colors.inkSoft
                MovementTone.Value -> colors.brandSoft to colors.onBrandSoft
            }
            Box(
                Modifier.size(32.dp).background(fill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon.painter(), null, Modifier.size(18.dp), tint = ink)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.W800)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSoft
            )
            annotation?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W700,
                    color = colors.semantic.gain
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(amount, color = amountColor, fontWeight = FontWeight.W800)
            balance?.let {
                val ink = it.color.takeOrElse { colors.inkSoft }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (it.value == null) it.label else it.label + " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = ink
                    )
                    it.value?.let { value ->
                        MoneyText(
                            value,
                            color = ink,
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * A month of a full movement list, over a rule: its name and, for a goal, its [net]
 * change. Goal and investment details share it.
 */
@Composable
fun MovementMonthLine(name: String, net: MoneyTextValue?, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.W800,
        letterSpacing = 0.06.em
    )
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(colors.rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name.uppercase(), Modifier.weight(1f), style = style, color = colors.outline)
        net?.let { MoneyText(it, color = colors.outline, textStyle = style) }
    }
}

/** Kept acknowledges an historical crossing; Ready is not a completed goal. */
enum class MilestoneState { Empty, Earned, Kept, Ready }

@Immutable
data class MilestoneSpec(
    val label: String,
    val description: String,
    val state: MilestoneState,
    val caption: String? = null
)

/**
 * One stamp place. [diameter] is 60dp on cards, 66dp in the celebration and 34dp, with
 * a smaller [labelSize] and no [innerRing], in a movement row; [ink] replaces the
 * state's colour where the place sits on green. [land] brings an earned stamp down once,
 * after [landDelayMillis] so stamps earned together come down in turn; [relight] lights a
 * stamp earned again from its kept ink instead. Reduced motion shows it at once.
 */
@Composable
fun MilestonePlace(
    spec: MilestoneSpec,
    modifier: Modifier = Modifier,
    land: Boolean = false,
    diameter: Dp = 60.dp,
    ink: Color? = null,
    relight: Boolean = false,
    landDelayMillis: Long = 0,
    labelSize: TextUnit = 14.sp,
    innerRing: Boolean = true
) {
    val colors = SelloTheme.colors
    val reduced = SelloTheme.reducedMotion
    val progress = remember { Animatable(if (land && !reduced) 0f else 1f) }
    val ring = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(land) {
        if (land) {
            if (!reduced) {
                delay(landDelayMillis)
                progress.animateTo(1f, SelloMotion.stamp())
                ring.animateTo(1f, SelloMotion.emphasized())
            } else {
                progress.snapTo(1f)
            }
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    // Back above a kept stamp: it lights up again in place, from the kept ink to the
    // earned green. Nothing new is earned, so there is no haptic.
    val lit = remember { Animatable(if (relight && !reduced) 0f else 1f) }
    LaunchedEffect(relight) {
        if (relight && !reduced) lit.animateTo(1f, SelloMotion.emphasized())
    }
    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
            ring.snapTo(1f)
            lit.snapTo(1f)
        }
    }
    val earned = spec.state == MilestoneState.Earned || spec.state == MilestoneState.Kept
    val keptInk = colors.semantic.gain.copy(alpha = KEPT_ALPHA)
    val color = ink ?: when (spec.state) {
        MilestoneState.Empty -> colors.outline
        MilestoneState.Kept -> keptInk
        MilestoneState.Earned -> lerp(keptInk, colors.semantic.gain, lit.value)
        MilestoneState.Ready -> colors.semantic.gain
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription =
                spec.description
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(diameter).graphicsLayer {
                scaleX =
                    SelloMotion.STAMP_RING_INITIAL_SCALE +
                    (1f - SelloMotion.STAMP_RING_INITIAL_SCALE) * progress.value
                scaleY = scaleX
                alpha = progress.value.coerceIn(0f, 1f)
                rotationZ = if (earned) -8f else 0f
            }.then(
                // Empty and ready places are dashed; an earned or kept place is a
                // double ring: a 2.5dp edge, a paper gap, then a thin inner ring.
                if (earned) {
                    Modifier
                } else {
                    val edge = if (spec.state == MilestoneState.Ready) 2.5.dp else 2.dp
                    Modifier.dashedOutline(color, edge, CircleShape)
                }
            ).drawBehind {
                val edge = 2.5.dp.toPx()
                if (earned) {
                    drawCircle(
                        color,
                        radius = size.minDimension / 2 - edge / 2,
                        style = Stroke(edge)
                    )
                }
                if (earned && innerRing) {
                    drawCircle(
                        color,
                        radius = size.minDimension / 2 - edge - 3.dp.toPx() - 0.75.dp.toPx(),
                        style = Stroke(1.5.dp.toPx())
                    )
                }
                if (ring.value > 0f &&
                    ring.value < 1f
                ) {
                    drawCircle(
                        color.copy(alpha = 1f - ring.value),
                        radius =
                            size.minDimension / 2 + ring.value * 8.dp.toPx(),
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
        ) {
            Text(
                spec.label,
                style = SelloTheme.type.stampSmall.copy(fontSize = labelSize),
                color = if (spec.state == MilestoneState.Empty &&
                    ink == null
                ) {
                    colors.inkSoft
                } else {
                    color
                }
            )
        }
        spec.caption?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.inkSoft)
        }
    }
}

private const val KEPT_ALPHA = 0.42f

/** Between stamps earned together. */
private const val LAND_STAGGER_MILLIS = 160L

/**
 * The stamps in a row. By default they sit side by side and scroll when they do not fit;
 * [evenly] centres each in an equal share of the width instead, as on Nueva meta.
 */
@Composable
fun MilestoneRow(
    places: List<MilestoneSpec>,
    modifier: Modifier = Modifier,
    landingIndex: Int? = null,
    landing: Set<Int> = setOfNotNull(landingIndex),
    relighting: Set<Int> = emptySet(),
    evenly: Boolean = false
) {
    val landingOrder = landing.sorted()
    val place: @Composable (Int, MilestoneSpec) -> Unit = { index, spec ->
        MilestonePlace(
            spec,
            land = index in landing,
            relight = index in relighting,
            landDelayMillis = landingOrder.indexOf(index).coerceAtLeast(0) * LAND_STAGGER_MILLIS
        )
    }
    if (evenly) {
        Row(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            places.forEachIndexed { index, spec ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    place(index, spec)
                }
            }
        }
    } else {
        Row(
            modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            places.forEachIndexed { index, spec -> place(index, spec) }
        }
    }
}

private val TicketShape = GenericShape { size, _ ->
    val notch = minOf(size.width / 20f, size.height / 6f)
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height / 2 - notch)
    cubicTo(
        size.width - 2 * notch,
        size.height / 2 - notch,
        size.width - 2 * notch,
        size.height / 2 + notch,
        size.width,
        size.height / 2 + notch
    )
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    lineTo(0f, size.height / 2 + notch)
    cubicTo(
        2 * notch,
        size.height / 2 + notch,
        2 * notch,
        size.height / 2 - notch,
        0f,
        size.height / 2 - notch
    )
    close()
}

/**
 * The paper ticket a save notice becomes when a stamp is earned where the goal's own
 * stamps are not in view: the stamps on the left, a dashed tear, then [title] and
 * [detail]. Two stamps sit smaller, side by side; with [land] they come down in turn.
 */
@Composable
fun StampTicket(
    places: List<MilestoneSpec>,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    land: Boolean = false
) {
    val colors = SelloTheme.colors
    Row(
        modifier.fillMaxWidth()
            .shadow(12.dp, TicketShape, ambientColor = colors.ink, spotColor = colors.ink)
            .background(colors.paper)
            .height(IntrinsicSize.Min)
            .padding(start = 18.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            places.forEachIndexed { index, spec ->
                MilestonePlace(
                    spec,
                    land = land,
                    diameter = if (places.size > 1) 46.dp else 54.dp,
                    landDelayMillis = index * LAND_STAGGER_MILLIS
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().drawBehind {
                drawLine(
                    colors.rule,
                    Offset.Zero,
                    Offset(0f, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                    )
                )
            }.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Text(title, fontWeight = FontWeight.W800)
            Text(detail, color = colors.inkSoft, style = MaterialTheme.typography.bodySmall)
        }
    }
}
