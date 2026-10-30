package com.software.sello.feature.goals

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.software.sello.designsystem.component.MilestonePlace
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import java.util.Locale
import kotlin.math.hypot

/*
 * The big moment's timeline, in milliseconds from opening, from the locked reference
 * (docs/design/v2/celebrate.js): the stamps land one by one on the desk, green floods out
 * of the Meta stamp, the stamps rise and turn white, ¡Lo lograste! rises, the plate is
 * stamped, paper chads fall and the actions fade in. Everything below is derived from one
 * clock so the beats combine as they do in the reference.
 */
private val LandAt = listOf(300, 650, 1000, 1400)

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

/**
 * Marcar como lograda's big moment, presentation only: the host opens this after a
 * confirmed completion, never during submission. With reduced motion it opens on the final
 * screen, without chads.
 */
@Composable
internal fun CelebrationOverlay(
    title: String,
    plateTitle: String,
    name: String,
    icon: SelloIcon,
    amount: MoneyTextValue,
    detail: String,
    places: List<MilestoneSpec>,
    doneLabel: String,
    viewLabel: String,
    onDone: () -> Unit,
    onView: () -> Unit
) {
    val reduced = SelloTheme.reducedMotion
    val clock = remember { Animatable(if (reduced) END_MS.toFloat() else 0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(reduced) {
        if (reduced) {
            clock.snapTo(END_MS.toFloat())
            return@LaunchedEffect
        }
        // Run the clock in legs so each landing and the plate get their haptic as they hit.
        val hits = LandAt.mapIndexed { i, at -> (at + LAND_MS * 0.6f) to (i == LandAt.lastIndex) } +
            ((PLATE_AT + PLATE_MS * 0.65f) to true)
        for ((at, strong) in hits) {
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
    val gain = colors.semantic.gain
    val onGain = colors.semantic.onGain
    var metaCenter by remember { mutableStateOf<Offset?>(null) }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // The system bars' ink follows the reference: dark on the pale desk, white once the
        // green has flooded in. A dark desk keeps white ink throughout.
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val darkBarIcons = !colors.isDark && t < INK_AT + INK_MS / 2
        DisposableEffect(window, darkBarIcons) {
            val apply = { window?.let { setDarkBarIcons(it, darkBarIcons) } }
            // Android 11 drops an appearance set before the dialog's window is attached.
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    apply()
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            if (view.isAttachedToWindow) apply() else view.addOnAttachStateChangeListener(listener)
            onDispose { view.removeOnAttachStateChangeListener(listener) }
        }
        Box(Modifier.fillMaxSize().background(colors.desk).semantics { paneTitle = title }) {
            // Green floods out of the Meta stamp.
            val flood = FloodEasing.transform(beat(t, FLOOD_AT, FLOOD_MS))
            Canvas(Modifier.fillMaxSize()) {
                if (flood > 0f) {
                    val center = metaCenter ?: Offset(size.width * 0.87f, size.height * 0.49f)
                    val reach = 1.5f * hypot(size.width, size.height) / 1.4142f
                    drawCircle(gain, reach * flood, center)
                }
            }
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Stamps(t, places, gain, onGain) { metaCenter = it }
                        Title(t, title, onGain)
                        Plate(t, plateTitle, name, icon, amount, detail, onGain)
                    }
                }
                Actions(t, doneLabel, viewLabel, onDone, onView, gain, onGain)
            }
            if (!reduced) Confetti(t)
        }
    }
}

/**
 * Asks for dark (or white) status- and navigation-bar icons. Android 11 also needs the
 * older light-bar flags on the decor view, as AndroidX's insets controller sets them.
 */
@Suppress("DEPRECATION")
private fun setDarkBarIcons(window: Window, dark: Boolean) {
    val bars = APPEARANCE_LIGHT_STATUS_BARS or APPEARANCE_LIGHT_NAVIGATION_BARS
    window.insetsController?.setSystemBarsAppearance(if (dark) bars else 0, bars)
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
        val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val decor = window.decorView
        decor.systemUiVisibility = if (dark) {
            decor.systemUiVisibility or flags
        } else {
            decor.systemUiVisibility and flags.inv()
        }
    }
}

/** Visible and announced only from [at]; before then the slot keeps its place, unseen. */
private fun Modifier.from(t: Float, at: Int) =
    if (t >= at) this else this.graphicsLayer { alpha = 0f }.clearAndSetSemantics {}

@Composable
private fun Stamps(
    t: Float,
    places: List<MilestoneSpec>,
    gain: Color,
    onGain: Color,
    onMeta: (Offset) -> Unit
) {
    val density = LocalDensity.current
    // On the desk the stamps sit 190dp lower and a little larger, then rise into place.
    val rise = RiseEasing.transform(beat(t, RISE_AT, RISE_MS))
    val lift = with(density) { 190.dp.toPx() } * (1f - rise)
    val grow = 1.15f - 0.15f * rise
    val ink = lerp(gain, onGain, Ease.transform(beat(t, INK_AT, INK_MS)))
    Row(
        Modifier.widthIn(max = 300.dp).fillMaxWidth().graphicsLayer {
            translationY = lift
            scaleX = grow
            scaleY = grow
        },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        places.forEachIndexed { index, spec ->
            val at = LandAt.getOrElse(index) { LandAt.last() }
            val p = beat(t, at, LAND_MS)
            val stops = listOf(0f, 0.6f, 0.82f, 1f)
            Box(
                Modifier
                    .from(t, at)
                    .graphicsLayer {
                        alpha = track(p, stops, listOf(0f, 1f, 1f, 1f), LandEasing)
                        val scale = track(p, stops, listOf(2.4f, 0.9f, 1.05f, 1f), LandEasing)
                        scaleX = scale
                        scaleY = scale
                        // The place already tilts −8°; the landing adds the swing from −24°.
                        rotationZ = track(p, stops, listOf(-16f, 0f, 0f, 0f), LandEasing)
                    }
                    .then(
                        if (index == places.lastIndex) {
                            Modifier.onGloballyPositioned { onMeta(it.boundsInRoot().center) }
                        } else {
                            Modifier
                        }
                    )
            ) { MilestonePlace(spec, diameter = 66.dp, ink = ink) }
        }
    }
}

@Composable
private fun Title(t: Float, title: String, onGain: Color) {
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
        color = onGain,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = 48.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.W900,
            letterSpacing = (-0.035).em
        )
    )
}

/** The rubber-stamp plate: a double outline, the goal, its amount between dashed rules. */
@Composable
private fun Plate(
    t: Float,
    plateTitle: String,
    name: String,
    icon: SelloIcon,
    amount: MoneyTextValue,
    detail: String,
    onGain: Color
) {
    val p = beat(t, PLATE_AT, PLATE_MS)
    val stops = listOf(0f, 0.65f, 1f)
    val stencil = SelloTheme.type.stampSmall
    val digits = amount.digits.count { it.isDigit() }
    val amountSize = when {
        digits > 9 -> 29.sp
        digits > 6 -> 37.sp
        else -> 48.sp
    }
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
            .border(4.dp, onGain, RoundedCornerShape(14.dp))
            .padding(5.dp)
            .border(1.5.dp, onGain, RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = SelloTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        Text(
            plateTitle.uppercase(Locale.ROOT),
            color = onGain,
            style = stencil.copy(fontSize = 15.sp, letterSpacing = 0.14.em),
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            Icon(icon.painter(), null, Modifier.size(24.dp), tint = onGain)
            Text(
                name,
                Modifier.weight(1f, fill = false),
                color = onGain,
                fontWeight = FontWeight.W800,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp
                )
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val line = 2.dp.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    drawLine(
                        onGain,
                        Offset(0f, line / 2),
                        Offset(size.width, line / 2),
                        line,
                        pathEffect = dash
                    )
                    drawLine(
                        onGain,
                        Offset(0f, size.height - line / 2),
                        Offset(size.width, size.height - line / 2),
                        line,
                        pathEffect = dash
                    )
                }
                .padding(top = SelloTheme.spacing.sm, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            MoneyText(
                amount,
                color = onGain,
                singleLine = true,
                textStyle = MaterialTheme.typography.displayMedium.copy(
                    fontSize = amountSize,
                    lineHeight = amountSize * 1.05f,
                    fontWeight = FontWeight.W900,
                    letterSpacing = (-0.04).em
                )
            )
        }
        Text(
            detail.uppercase(Locale.ROOT),
            color = onGain,
            style = stencil.copy(fontSize = 13.sp, letterSpacing = 0.08.em),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Actions(
    t: Float,
    doneLabel: String,
    viewLabel: String,
    onDone: () -> Unit,
    onView: () -> Unit,
    gain: Color,
    onGain: Color
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
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(SelloTheme.shapes.button)
                .background(onGain)
                .clickable(enabled = ready, role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center
        ) {
            Text(
                doneLabel,
                color = gain,
                fontWeight = FontWeight.W800,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Row(
            Modifier
                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                .clip(SelloTheme.shapes.button)
                .clickable(enabled = ready, role = Role.Button, onClick = onView)
                .padding(horizontal = SelloTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                viewLabel,
                color = onGain,
                fontWeight = FontWeight.W800,
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(18.dp), tint = onGain)
        }
    }
}

/** Punched holes, receipt teeth and strips falling over the green. Decorative only. */
@Composable
private fun Confetti(t: Float) {
    val colors = SelloTheme.colors
    val palette =
        listOf(Color.White, colors.semantic.warn, colors.brandSoft, Color.White, colors.brand)
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
            val color = palette[chad.color].copy(alpha = alpha)
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
