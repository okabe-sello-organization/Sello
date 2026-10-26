package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.max
import kotlin.math.roundToInt

object SlipDefaults {
    /** Sides 16, top 15, bottom 20 so content clears the teeth. */
    val Padding = PaddingValues(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 20.dp)

    /** For slips without teeth, such as dialogs. */
    val PlainPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)
}

/**
 * The basic surface: a piece of paper on the desk. Everything that carries a figure
 * sits on one. With [onClick] the whole slip is a single button for accessibility. An
 * [outline] edges it, as the figure the Sello pad is typing into.
 */
@Composable
fun Slip(
    modifier: Modifier = Modifier,
    pinked: Boolean = true,
    contentPadding: PaddingValues = SlipDefaults.Padding,
    onClick: (() -> Unit)? = null,
    outline: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SelloTheme.colors
    val shape = if (pinked) remember { PinkedBottomShape() } else PlainSlipShape
    val click = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = colors.brand),
            role = Role.Button,
            onClick = onClick
        )
    }
    CompositionLocalProvider(LocalContentColor provides colors.ink) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(colors.paper)
                .then(if (outline == null) Modifier else Modifier.border(2.dp, outline, shape))
                .then(click)
                .padding(contentPadding),
            content = content
        )
    }
}

private val PlainSlipShape = RoundedCornerShape(11.dp)

/**
 * Rounded top corners and a zig-zag bottom edge. The teeth are stretched slightly so a
 * whole number of them always fits the width.
 */
class PinkedBottomShape(
    private val corner: Dp = 11.dp,
    private val toothWidth: Dp = 10.dp,
    private val toothHeight: Dp = 5.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val radius = corner.toPx().coerceAtMost(size.minDimension / 2)
        val teeth = toothCount(size.width, toothWidth.toPx())
        val tooth = size.width / teeth
        val valley = (size.height - toothHeight.toPx()).coerceAtLeast(radius)
        val path = Path().apply {
            moveTo(0f, radius)
            quadraticTo(0f, 0f, radius, 0f)
            lineTo(size.width - radius, 0f)
            quadraticTo(size.width, 0f, size.width, radius)
            lineTo(size.width, valley)
            for (index in teeth downTo 1) {
                lineTo((index - 0.5f) * tooth, size.height)
                lineTo((index - 1) * tooth, valley)
            }
            close()
        }
        Outline.Generic(path)
    }
}

/** How many teeth fit [width] when each is as close to [toothWidth] as possible. */
internal fun toothCount(width: Float, toothWidth: Float): Int =
    max(1, (width / toothWidth).roundToInt())
