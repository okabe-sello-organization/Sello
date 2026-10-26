package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.delay

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
 * One stamp place. [diameter] is 60dp on cards and 66dp in the celebration; [ink] replaces the
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
    landDelayMillis: Long = 0
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
            }.drawBehind {
                // Empty and ready places are dashed; an earned or kept place is a
                // double ring: a 2.5dp edge, a paper gap, then a thin inner ring.
                val edge = if (earned ||
                    spec.state == MilestoneState.Ready
                ) {
                    2.5.dp.toPx()
                } else {
                    2.dp.toPx()
                }
                drawCircle(
                    color,
                    radius = size.minDimension / 2 - edge / 2,
                    style = Stroke(
                        edge,
                        pathEffect = if (earned) {
                            null
                        } else {
                            PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    )
                )
                if (earned) {
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
                style = SelloTheme.type.stampSmall.copy(fontSize = 14.sp),
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

private const val KEPT_ALPHA = 0.42f

/** Between stamps earned together, as on the v2 celebrations page. */
private const val LAND_STAGGER_MILLIS = 160L

/**
 * Recibo's goal: the pill itself is the progress bar, filled to [progress] with a brand
 * edge where the fill ends. The main part opens the goal and, with [openDescription], is
 * said that way ("Viaje a la costa, 57 por ciento. Ver meta") with its exact progress;
 * the round **+** is Aportar, said as [contributeLabel].
 */
@Composable
fun GoalPill(
    name: String,
    percentage: String,
    progress: Float,
    icon: SelloIcon,
    contributeLabel: String,
    onOpen: () -> Unit,
    onContribute: () -> Unit,
    modifier: Modifier = Modifier,
    openDescription: String? = null
) {
    val colors = SelloTheme.colors
    val fill = progress.coerceIn(0f, 1f)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape)
            .background(colors.paper).drawBehind {
                val width = size.width * fill
                drawRect(colors.brandSoft, size = Size(width, size.height))
                if (fill > 0f) {
                    val edge = 2.dp.toPx()
                    drawRect(
                        colors.brand,
                        topLeft = Offset(width - edge, 0f),
                        size = Size(edge, size.height)
                    )
                }
            }.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    openDescription?.let { contentDescription = it }
                    progressBarRangeInfo = ProgressBarRangeInfo(fill, 0f..1f)
                }
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon.painter(), null, Modifier.size(22.dp), tint = colors.onBrandSoft)
            Text(
                name,
                Modifier.weight(1f),
                fontWeight = FontWeight.W800,
                color = colors.onBrandSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                percentage,
                color = colors.ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.brand)
                .clickable(role = Role.Button, onClick = onContribute)
                .semantics { contentDescription = contributeLabel }
        ) {
            Icon(SelloIcon.Add.painter(), null, tint = colors.onBrand)
        }
    }
}

/**
 * Another goal at 100 %, on Recibo: a green pill with [name], [message] ("Llegó al
 * 100 %") and a filled [actionLabel] that marks it lograda; [busy] while that is saved.
 * Where the three do not fit on one line (a narrow screen, a long name, large text) the
 * action moves under the name and the row becomes a card, so nothing is cut.
 */
@Composable
fun ReachedRow(
    name: String,
    message: String,
    actionLabel: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    val colors = SelloTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.W800)
    val messageStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700)
    val actionStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widths = with(density) {
            listOf(name to nameStyle, message to messageStyle, actionLabel to actionStyle)
                .map { (text, style) -> measurer.measure(text, style).size.width.toDp() }
        }
        // Start padding, icon and gaps, the wider text, the action and its padding, end padding.
        val inline = 14.dp + 22.dp + 10.dp + maxOf(widths[0], widths[1]) + 10.dp +
            widths[2] + 28.dp + 6.dp <= maxWidth
        val shape = if (inline) CircleShape else SelloTheme.shapes.control
        val texts = @Composable { textModifier: Modifier ->
            Column(textModifier.semantics(mergeDescendants = true) {}) {
                Text(
                    name,
                    color = colors.ink,
                    style = nameStyle,
                    maxLines = if (inline) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
                Text(message, color = colors.semantic.gain, style = messageStyle)
            }
        }
        val check = @Composable {
            Icon(SelloIcon.Check.painter(), null, Modifier.size(22.dp), tint = colors.semantic.gain)
        }
        val action = @Composable {
            // The v2 row's own compact button: 44dp, so the name keeps its room.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.heightIn(min = 44.dp).clip(CircleShape)
                    .background(colors.semantic.gain)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onComplete)
                    .alpha(if (busy) 0.6f else 1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(actionLabel, color = colors.semantic.onGain, style = actionStyle)
            }
        }
        val surface = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(colors.semantic.gainSoft)
        if (inline) {
            Row(
                surface.padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                check()
                texts(Modifier.weight(1f))
                action()
            }
        } else {
            Column(
                surface.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    check()
                    texts(Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
            }
        }
    }
}

/**
 * An active goal: the saved amount large, the target and its date on one line, a green
 * bar and the four places spread across the card. [flagged] shows the flag before
 * [date]; pass false with the caller's "Sin fecha" for a goal without one.
 */
@Composable
fun GoalCard(
    name: String,
    icon: SelloIcon,
    percentage: String,
    saved: MoneyTextValue,
    target: String,
    date: String,
    progress: Float,
    places: List<MilestoneSpec>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    flagged: Boolean = true
) {
    val colors = SelloTheme.colors
    Slip(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon.painter(), null, Modifier.size(24.dp), tint = colors.semantic.gain)
            Text(
                name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800)
            )
            Text(
                percentage,
                color = colors.semantic.gain,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.W900,
                    letterSpacing = (-0.02).em
                )
            )
        }
        FittedMoneyText(saved, Modifier.padding(top = 8.dp), startSize = 34)
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                target,
                Modifier.weight(1f),
                color = colors.inkSoft,
                style = MaterialTheme.typography.bodySmall
            )
            if (flagged) {
                Icon(
                    SelloIcon.Flag.painter(),
                    null,
                    Modifier.padding(end = 4.dp).size(16.dp),
                    tint = colors.inkSoft
                )
            }
            Text(date, color = colors.inkSoft, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(colors.paperDim)
        ) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                    .background(colors.semantic.gain)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            places.forEach { MilestonePlace(it) }
        }
    }
}

/**
 * A completed goal: its icon in ink, name and detail, and the stamp on the trailing edge.
 * With [land] the stamp comes down once as the row appears, as when revisiting Metas;
 * a revisit is no new result, so it makes no haptic.
 */
@Composable
fun CompletedGoalRow(
    name: String,
    detail: String,
    icon: SelloIcon,
    stamp: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    land: Boolean = false
) {
    Slip(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon.painter(), null, Modifier.size(24.dp), tint = SelloTheme.colors.ink)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyLarge)
                Text(
                    detail,
                    color = SelloTheme.colors.inkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Stamp(stamp, StampInk.Good, land = land, haptic = false)
        }
    }
}

@Composable
fun MovementRow(
    kind: String,
    detail: String,
    amount: MoneyTextValue,
    modifier: Modifier = Modifier,
    milestone: MilestoneSpec? = null,
    icon: SelloIcon = SelloIcon.Add,
    annotation: String? = null
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (milestone !=
            null
        ) {
            MilestonePlace(milestone)
        } else {
            Icon(icon.painter(), null, tint = SelloTheme.colors.inkSoft)
        }
        Column(Modifier.weight(1f)) {
            Text(kind, fontWeight = FontWeight.W800)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = SelloTheme.colors.inkSoft
            )
            annotation?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = SelloTheme.colors.semantic.gain
                )
            }
            MoneyText(amount)
        }
    }
}

/**
 * A signed return on one line: the arrow, the [percentage] large and the [amount] in
 * words smaller, all in gain or loss ink. With [even] (neither gain nor loss) there is
 * no arrow and the ink is neutral. The amount wraps on its own at large text.
 */
@Composable
fun PerformanceLine(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
fun investmentShade(rank: Int): Color {
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
fun InvestmentRow(
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

/**
 * A goal's icon in a large green circle, beside its name while creating or editing it.
 * Before an icon is chosen the circle is empty and dashed, with a soft [emptyIcon].
 * Decorative: the icon strip says which icon is chosen. An investment draws it in
 * [ButtonTone.Ink], with a trending line while empty (v2 Agregar inversión).
 */
@Composable
fun GoalIconCircle(
    icon: SelloIcon?,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Gain,
    emptyIcon: SelloIcon = SelloIcon.Flag
) {
    val colors = SelloTheme.colors
    val (fill, onFill) = tone.inks()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(64.dp).drawBehind {
            if (icon != null) {
                drawCircle(fill)
            } else {
                val stroke = 2.dp.toPx()
                drawCircle(
                    colors.outline,
                    radius = size.minDimension / 2 - stroke / 2,
                    style = Stroke(
                        stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                        )
                    )
                )
            }
        }
    ) {
        Icon(
            (icon ?: emptyIcon).painter(),
            contentDescription = null,
            tint = if (icon != null) onFill else colors.inkSoft,
            modifier = Modifier.size(30.dp)
        )
    }
}
