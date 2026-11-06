package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme
import java.util.Locale

/** Semantic inks; Warn is reserved for a review status, never ordinary overspending. */
enum class StampInk { Good, Over, Neutral, Warn }

/** 15sp, 34sp and 52sp. */
enum class StampSize { Small, Large, Hero }

object StampDefaults {
    fun angle(size: StampSize): Float = if (size == StampSize.Hero) -9f else -6f
}

/**
 * One word in an outlined, rotated box: the verdict of a screen. It is decoration for a
 * result that already exists; it has no click and cannot cause a write. [text] is
 * sentence case and is what a screen reader says; it is drawn in capitals.
 *
 * With [land] the stamp comes down once when it first appears. Pass it only when the
 * verdict has just been confirmed, or with [haptic] false for a verdict revisited.
 * Reduced motion shows the stamp at rest.
 */
@Composable
fun Stamp(
    text: String,
    ink: StampInk,
    modifier: Modifier = Modifier,
    size: StampSize = StampSize.Small,
    angle: Float = StampDefaults.angle(size),
    land: Boolean = false,
    inkRing: Boolean = false,
    haptic: Boolean = true
) {
    val colors = SelloTheme.colors
    val color = when (ink) {
        StampInk.Good -> colors.semantic.gain
        StampInk.Over -> colors.semantic.loss
        StampInk.Neutral -> colors.inkSoft
        StampInk.Warn -> colors.semantic.warn
    }
    val spec = when (size) {
        StampSize.Small -> StampSpec(
            SelloTheme.type.stampSmall,
            2.5.dp,
            SelloTheme.shapes.stampSmall,
            PaddingValues(start = 9.dp, top = 3.dp, end = 9.dp, bottom = 1.dp)
        )

        StampSize.Large -> StampSpec(
            SelloTheme.type.stampLarge,
            4.dp,
            SelloTheme.shapes.stampLarge,
            PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 1.dp)
        )

        StampSize.Hero -> StampSpec(
            SelloTheme.type.stampHero,
            5.dp,
            SelloTheme.shapes.stampHero,
            PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp)
        )
    }
    val animate = land && !SelloTheme.reducedMotion
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val ring = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        if (land && haptic) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        if (animate) {
            progress.animateTo(1f, SelloMotion.stamp())
            if (inkRing) ring.animateTo(1f, SelloMotion.emphasized())
        }
    }
    Text(
        text = text.uppercase(Locale.ROOT),
        style = spec.style,
        color = color,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                val p = progress.value
                val initialScale = if (inkRing) {
                    SelloMotion.STAMP_RING_INITIAL_SCALE
                } else {
                    SelloMotion.STAMP_INITIAL_SCALE
                }
                val scale = initialScale + (1f - initialScale) * p
                scaleX = scale
                scaleY = scale
                alpha = p.coerceIn(0f, 1f)
                rotationZ = angle + SelloMotion.STAMP_SETTLE_DEGREES * (1f - p)
            }
            .drawBehind {
                if (inkRing && ring.value > 0f && ring.value < 1f) {
                    drawCircle(
                        color.copy(alpha = 1f - ring.value),
                        radius =
                            this@drawBehind.size.maxDimension / 2 + ring.value * 8.dp.toPx(),
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
            .border(spec.outline, color, spec.shape)
            .padding(spec.padding)
            .clearAndSetSemantics { contentDescription = text }
    )
}

private class StampSpec(
    val style: androidx.compose.ui.text.TextStyle,
    val outline: Dp,
    val shape: CornerBasedShape,
    val padding: PaddingValues
)
