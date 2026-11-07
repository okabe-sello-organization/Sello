package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.DialogBarIcons
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.hypot

/*
 * A big moment's timeline, in milliseconds from opening, from the goal's locked reference
 * (docs/design/system/features/goals.html#screen-celebrations): the stamps land one by one on the
 * desk, the flood colour spreads out of the last stamp, the stamps rise and take the on-flood
 * ink, the title rises, the plate is stamped, paper chads fall and the actions fade in.
 * Everything below is derived from one clock so the beats combine as they do in the reference.
 */
private const val LAND_MS = 420

private const val FLOOD_AT = 1750

private const val FLOOD_MS = 600

private const val INK_AT = 1900

private const val INK_MS = 300

private const val RISE_AT = 2050

private const val RISE_MS = 550

private const val TITLE_AT = 2400

private const val TITLE_MS = 350

private const val PLATE_AT = 2750

private const val PLATE_MS = 360

private const val ACTIONS_AT = 3100

private const val ACTIONS_MS = 300

private const val END_MS = 7200

/** Landings closer than this to the last one that buzzed land without a haptic of their own. */
private const val HAPTIC_GAP_MS = 300

private val LandEasing = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)

private val FloodEasing = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)

private val RiseEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private val PlateEasing = CubicBezierEasing(0.3f, 1.4f, 0.5f, 1f)

private val ChadEasing = CubicBezierEasing(0.25f, 0.55f, 0.45f, 1f)

/** Linear progress through a beat that starts [at] and lasts [ms], clamped to 0..1. */
private fun beat(t: Float, at: Int, ms: Int) = ((t - at) / ms).coerceIn(0f, 1f)

/**
 * A CSS-style keyframe track: [values] at [stops] (0..1), with [easing] applied inside
 * each segment as a browser does.
 */
private fun track(p: Float, stops: List<Float>, values: List<Float>, easing: Easing): Float {
    val i = stops.indexOfLast { it <= p }.coerceIn(0, stops.size - 2)
    val local = ((p - stops[i]) / (stops[i + 1] - stops[i])).coerceIn(0f, 1f)
    return values[i] + (values[i + 1] - values[i]) * easing.transform(local)
}

/** One falling paper chad, seeded exactly as the reference's confetti. */
private class Chad(
    val kind: Int,
    val left: Float,
    val durationMs: Float,
    val delayMs: Float,
    val driftDp: Float,
    val spin: Float,
    val color: Int
)

private val Chads: List<Chad> = run {
    var seed = 7L
    fun next(): Float {
        seed = seed * 16807 % 2147483647
        return seed / 2147483647f
    }
    List(64) { i ->
        val left = next()
        val duration = 2600 + next() * 1600
        val delay = 2000 + next() * 900
        val drift = Math.round(next() * 80 - 40).toFloat()
        val spin = Math.round(next() * 720 - 360).toFloat()
        Chad(i % 3, left, duration, delay, drift, spin, i % 5)
    }
}

/** One stamp of a big moment. A [faded] stamp lands with less ink, as a month that went over. */
@Immutable
data class CelebrationStamp(val spec: MilestoneSpec, val faded: Boolean = false)

/** The button a big moment ends on, filled or as a text link. */
@Immutable
data class CelebrationAction(
    val label: String,
    val onClick: () -> Unit,
    val trailingIcon: SelloIcon? = null
)

/**
 * A big moment, presentation only, as a goal's ¡Lo lograste!: a full-screen dialog on the
 * desk where [stamps] land at [landAtMillis] (each by 1400 ms, before the flood), [flood]
 * spreads out of the last one, the stamps rise in [onFlood], [title] rises, the [plate] is
 * stamped, paper chads in [chadColors] (five, taken in turn) fall, then [primary] (filled) and [secondary]
 * (a link) fade in, both in [actionTone]. With reduced motion it opens on the final screen,
 * without chads. [onDismiss] is Back. It is decoration, so a mismatch degrades instead of
 * failing: a stamp without its own time lands with the last one, and no colours means no chads.
 */
@Composable
fun CelebrationMoment(
    title: String,
    stamps: List<CelebrationStamp>,
    landAtMillis: List<Int>,
    flood: Color,
    onFlood: Color,
    chadColors: List<Color>,
    actionTone: ButtonTone,
    primary: CelebrationAction,
    secondary: CelebrationAction,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    stampDiameter: Dp = 66.dp,
    stampsPerRow: Int = stamps.size,
    stampRowWidth: Dp = 300.dp,
    plate: @Composable ColumnScope.() -> Unit
) {
    val reduced = SelloTheme.reducedMotion
    val clock = remember { Animatable(if (reduced) END_MS.toFloat() else 0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(reduced) {
        if (reduced) {
            clock.snapTo(END_MS.toFloat())
            return@LaunchedEffect
        }
        // Run the clock in legs so the landings and the plate get their haptic as they hit.
        for ((at, strong) in hits(stamps.indices.map { landAt(landAtMillis, it) })) {
            clock.animateTo(
                at,
                tween((at - clock.value).toInt().coerceAtLeast(0), easing = LinearEasing)
            )
            haptics.performHapticFeedback(
                if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm
            )
        }
        clock.animateTo(
            END_MS.toFloat(),
            tween((END_MS - clock.value).toInt(), easing = LinearEasing)
        )
    }
    val t = clock.value
    val colors = SelloTheme.colors
    var lastCenter by remember { mutableStateOf<Offset?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Both bars' ink: the theme's ink on the desk, then the on-flood ink once the flood
        // has spread.
        val darkBarIcons = if (t < INK_AT + INK_MS / 2) {
            !colors.isDark
        } else {
            onFlood.luminance() < 0.5f
        }
        DialogBarIcons(darkStatusIcons = darkBarIcons, darkNavigationIcons = darkBarIcons)
        Box(
            modifier.fillMaxSize().background(colors.desk).semantics { paneTitle = title }
        ) {
            val spread = FloodEasing.transform(beat(t, FLOOD_AT, FLOOD_MS))
            Canvas(Modifier.fillMaxSize()) {
                if (spread > 0f) {
                    val center = lastCenter ?: Offset(size.width * 0.87f, size.height * 0.49f)
                    val reach = 1.5f * hypot(size.width, size.height) / 1.4142f
                    drawCircle(flood, reach * spread, center)
                }
            }
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Stamps(
                            t,
                            stamps,
                            landAtMillis,
                            lerp(flood, onFlood, Ease.transform(beat(t, INK_AT, INK_MS))),
                            stampDiameter,
                            stampsPerRow,
                            stampRowWidth
                        ) { lastCenter = it }
                        Title(t, title, onFlood)
                        Plate(t, onFlood, plate)
                    }
                }
                Actions(t, actionTone, primary, secondary)
            }
            if (!reduced && chadColors.isNotEmpty()) Confetti(t, chadColors)
        }
    }
}

/**
 * Where the clock stops to buzz: each landing at 60 % of its fall, unless the last one that
 * buzzed was under [HAPTIC_GAP_MS] before (a long row lands in a few strong beats), the last
 * landing strongly, then the plate.
 */
private fun hits(landAtMillis: List<Int>): List<Pair<Float, Boolean>> {
    val hits = mutableListOf<Pair<Float, Boolean>>()
    var buzzed: Int? = null
    landAtMillis.forEachIndexed { i, at ->
        val last = i == landAtMillis.lastIndex
        val previous = buzzed
        if (last || previous == null || at - previous >= HAPTIC_GAP_MS) {
            hits += (at + LAND_MS * 0.6f) to last
            buzzed = at
        }
    }
    return hits + ((PLATE_AT + PLATE_MS * 0.65f) to true)
}

/** When stamp [index] lands: its own time, or with the last one when it has none. */
private fun landAt(landAtMillis: List<Int>, index: Int): Int =
    landAtMillis.getOrElse(index) { landAtMillis.lastOrNull() ?: 0 }

/** Visible and announced only from [at]; before then the slot keeps its place, unseen. */
private fun Modifier.from(t: Float, at: Int) =
    if (t >= at) this else this.graphicsLayer { alpha = 0f }.clearAndSetSemantics {}

@Composable
private fun Stamps(
    t: Float,
    stamps: List<CelebrationStamp>,
    landAtMillis: List<Int>,
    ink: Color,
    diameter: Dp,
    perRow: Int,
    rowWidth: Dp,
    onLast: (Offset) -> Unit
) {
    val density = LocalDensity.current
    // On the desk the stamps sit 190dp lower and a little larger, then rise into place.
    val rise = RiseEasing.transform(beat(t, RISE_AT, RISE_MS))
    val lift = with(density) { 190.dp.toPx() } * (1f - rise)
    val grow = 1.15f - 0.15f * rise
    Column(
        Modifier.widthIn(max = rowWidth).fillMaxWidth().graphicsLayer {
            translationY = lift
            scaleX = grow
            scaleY = grow
        },
        verticalArrangement = Arrangement.spacedBy(StampRowGap)
    ) {
        stamps.withIndex().chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { (index, stamp) ->
                    val at = landAt(landAtMillis, index)
                    val p = beat(t, at, LAND_MS)
                    val stops = listOf(0f, 0.6f, 0.82f, 1f)
                    Box(
                        Modifier
                            .from(t, at)
                            .graphicsLayer {
                                alpha = track(p, stops, listOf(0f, 1f, 1f, 1f), LandEasing)
                                val scale =
                                    track(p, stops, listOf(2.4f, 0.9f, 1.05f, 1f), LandEasing)
                                scaleX = scale
                                scaleY = scale
                                // The place already tilts −8°; the landing adds the swing from −24°.
                                rotationZ = track(p, stops, listOf(-16f, 0f, 0f, 0f), LandEasing)
                            }
                            .then(
                                if (index == stamps.lastIndex) {
                                    Modifier.onGloballyPositioned {
                                        onLast(it.boundsInRoot().center)
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        MilestonePlace(
                            stamp.spec,
                            diameter = diameter,
                            ink = if (stamp.faded) ink.copy(alpha = ink.alpha * FADED) else ink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(t: Float, title: String, onFlood: Color) {
    val density = LocalDensity.current
    val p = EaseOut.transform(beat(t, TITLE_AT, TITLE_MS))
    Text(
        title,
        Modifier
            .padding(top = 30.dp)
            .from(t, TITLE_AT)
            .graphicsLayer {
                alpha = p
                translationY = with(density) { 24.dp.toPx() } * (1f - p)
            },
        color = onFlood,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = TitleSize,
            lineHeight = TitleSize
        )
    )
}

/** The rubber-stamp plate: a double outline around what the moment has to say. */
@Composable
private fun Plate(t: Float, onFlood: Color, content: @Composable ColumnScope.() -> Unit) {
    val p = beat(t, PLATE_AT, PLATE_MS)
    val stops = listOf(0f, 0.65f, 1f)
    Column(
        Modifier
            .padding(top = 30.dp)
            .widthIn(max = 316.dp)
            .fillMaxWidth()
            .from(t, PLATE_AT)
            .graphicsLayer {
                alpha = track(p, stops, listOf(0f, 1f, 1f), PlateEasing)
                val scale = track(p, stops, listOf(2.4f, 0.95f, 1f), PlateEasing)
                scaleX = scale
                scaleY = scale
                rotationZ = track(p, stops, listOf(-18f, -4f, -4f), PlateEasing)
            }
            .border(4.dp, onFlood, SelloTheme.shapes.stampHero)
            .padding(5.dp)
            .border(1.5.dp, onFlood, PlateInnerShape)
            .padding(horizontal = 14.dp, vertical = SelloTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        content = content
    )
}

@Composable
private fun Actions(
    t: Float,
    tone: ButtonTone,
    primary: CelebrationAction,
    secondary: CelebrationAction
) {
    val p = EaseOut.transform(beat(t, ACTIONS_AT, ACTIONS_MS))
    val ready = t >= ACTIONS_AT
    Column(
        Modifier
            .fillMaxWidth()
            .from(t, ACTIONS_AT)
            .graphicsLayer { alpha = p }
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs)
    ) {
        SelloButton(
            text = primary.label,
            onClick = primary.onClick,
            tone = tone,
            trailingIcon = primary.trailingIcon,
            enabled = ready,
            modifier = Modifier.fillMaxWidth()
        )
        SelloButton(
            text = secondary.label,
            onClick = secondary.onClick,
            kind = ButtonKind.Text,
            tone = tone,
            trailingIcon = secondary.trailingIcon,
            enabled = ready
        )
    }
}

/** Punched holes, receipt teeth and strips falling over the flood. Decorative only. */
@Composable
private fun Confetti(t: Float, palette: List<Color>) {
    Canvas(Modifier.fillMaxSize()) {
        val start = -24.dp.toPx()
        val fall = size.height + 48.dp.toPx()
        Chads.forEach { chad ->
            val linear = ((t - chad.delayMs) / chad.durationMs).coerceIn(0f, 1f)
            if (linear <= 0f) return@forEach
            val p = ChadEasing.transform(linear)
            val alpha = (linear / 0.08f).coerceAtMost(1f)
            val (w, h) = when (chad.kind) {
                2 -> 7.dp.toPx() to 18.dp.toPx()
                1 -> 16.dp.toPx() to 9.dp.toPx()
                else -> 12.dp.toPx() to 12.dp.toPx()
            }
            val x = size.width * chad.left + chad.driftDp.dp.toPx() * p
            val y = start + fall * p
            val color = palette[chad.color % palette.size].copy(alpha = alpha)
            rotate(chad.spin * p, Offset(x + w / 2, y + h / 2)) {
                when (chad.kind) {
                    0 -> drawCircle(color, w / 2, Offset(x + w / 2, y + h / 2))

                    1 -> drawPath(
                        Path().apply {
                            moveTo(x, y)
                            lineTo(x + w, y)
                            lineTo(x + w / 2, y + h)
                            close()
                        },
                        color
                    )

                    else -> drawRect(color, Offset(x, y), Size(w, h))
                }
            }
        }
    }
}

/** A faded stamp's ink, as the mock's month that went over (no alpha token). */
private const val FADED = 0.45f

// The celebration's one-off type, spacing and corners: no token covers them.
private val TitleSize = 48.sp
private val StampRowGap = 12.dp
private val PlateInnerShape = RoundedCornerShape(9.dp)
