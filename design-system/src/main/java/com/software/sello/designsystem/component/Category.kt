package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.delay

/**
 * A circle that fills with ink from the bottom to the share of the limit spent. The
 * category is known by its icon and name, never by a colour of its own.
 *
 * @param fraction spent over limit; drawn clamped to 0..1. Null means no limit:
 * outline and icon only.
 * @param over spent more than the limit: full, in loss ink.
 * @param unlimited no limit on purpose ("Sin límite"): an open, dashed ring and a small
 * ∞ badge at its lower edge, so it never looks like a limit with nothing spent yet.
 * @param archived drawn in the soft outline ink instead of the brand or loss ink; the
 * fill and the badge keep their meaning.
 * @param staggerIndex position in a group, so the circles of a screen fill in turn.
 */
@Composable
fun CategoryCircle(
    icon: SelloIcon,
    fraction: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    over: Boolean = false,
    animate: Boolean = true,
    staggerIndex: Int = 0,
    unlimited: Boolean = false,
    archived: Boolean = false
) {
    val colors = SelloTheme.colors
    val target = if (unlimited) 0f else fillLevel(fraction, over)
    val moving = animate && !SelloTheme.reducedMotion
    val level = remember { Animatable(if (moving) 0f else target) }
    LaunchedEffect(target, moving) {
        if (moving) {
            if (level.value == 0f && target > 0f) delay(FILL_STAGGER_MILLIS * staggerIndex)
            level.animateTo(target, SelloMotion.fill())
        } else {
            level.snapTo(target)
        }
    }
    val ink = when {
        archived -> colors.outline
        over -> colors.semantic.loss
        else -> colors.brand
    }
    val onInk = when {
        archived -> colors.paper
        over -> colors.semantic.onLoss
        else -> colors.onBrand
    }
    val outline = 2.dp
    val tagged = if (archived) Modifier.testTag(CATEGORY_ARCHIVED_TAG) else Modifier
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).then(tagged)) {
        Canvas(Modifier.size(size)) {
            val stroke = outline.toPx()
            val inner = this.size.minDimension - 2 * stroke
            drawCircle(colors.paper)
            val circle = Path().apply {
                addOval(Rect(Offset(stroke, stroke), Size(inner, inner)))
            }
            clipPath(circle) {
                val filled = inner * level.value
                drawRect(ink, Offset(0f, stroke + inner - filled), Size(this.size.width, filled))
            }
            val ring = if (unlimited) {
                // Short dashes, scaled with the circle, read as "no edge" at every size.
                val dash = this.size.minDimension * DASH_SHARE
                Stroke(
                    stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(
                            dash,
                            dash * 0.75f
                        )
                    )
                )
            } else {
                Stroke(stroke)
            }
            drawCircle(ink, radius = (this.size.minDimension - stroke) / 2, style = ring)
        }
        val iconSize = size / 2
        Icon(icon.painter(), null, Modifier.size(iconSize), ink)
        // The same icon again in the "on" ink, kept only where the fill has reached.
        Icon(
            painter = icon.painter(),
            contentDescription = null,
            tint = onInk,
            modifier = Modifier.size(iconSize).drawWithContent {
                val circleSize = size.toPx()
                val inner = circleSize - 2 * outline.toPx()
                val fillTop = outline.toPx() + inner * (1f - level.value)
                val iconTop = (circleSize - this.size.height) / 2
                clipRect(top = (fillTop - iconTop).coerceIn(0f, this.size.height)) {
                    this@drawWithContent.drawContent()
                }
            }
        )
        if (unlimited) UnlimitedBadge(size, ink)
    }
}

/** The ∞ mark on a circle without a limit, overlapping its lower right edge. */
@Composable
private fun BoxScope.UnlimitedBadge(circle: Dp, ink: Color) {
    val colors = SelloTheme.colors
    val badge = circle * BADGE_SHARE
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = badge * BADGE_OVERHANG, y = badge * BADGE_OVERHANG)
            .size(badge)
            .background(colors.paper, CircleShape)
            .border(1.5.dp, ink, CircleShape)
            .testTag(CATEGORY_UNLIMITED_TAG)
    ) {
        Icon(SelloIcon.AllInclusive.painter(), null, Modifier.size(badge * 0.7f), ink)
    }
}

private const val DASH_SHARE = 0.09f

private const val BADGE_SHARE = 0.42f

private const val BADGE_OVERHANG = 0.2f

private const val FILL_STAGGER_MILLIS = 30L

/** The test tag of the ∞ badge a circle without a limit carries. */
const val CATEGORY_UNLIMITED_TAG = "category-circle:unlimited"

/** The test tag of a circle drawn in grey because its category is archived. */
const val CATEGORY_ARCHIVED_TAG = "category-circle:archived"
