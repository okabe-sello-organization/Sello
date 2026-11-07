package com.software.sello.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloTheme

/**
 * One ink in the choice: its [label], the colour it previews ([fill]; null for Tuya before it
 * has a hue, drawn as a rainbow with a +), whether it is [chosen] and its [badge], if any: the
 * wallpaper for Sistema, the pencil for Tuya once it opens Tu tinta again.
 */
internal class InkSwatch(
    val label: String,
    val fill: Color?,
    val chosen: Boolean,
    val badge: SelloIcon? = null,
    val onClick: () -> Unit
)

/**
 * The ink choice: one round swatch per ink in its own colour, named under it, with a
 * ring around the chosen one. Each swatch is a radio button.
 */
@Composable
internal fun InkSwatches(
    swatches: List<InkSwatch>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg),
        modifier = modifier.selectableGroup()
    ) {
        swatches.forEach { swatch ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .selectable(
                        selected = swatch.chosen,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = swatch.onClick
                    )
                    .alpha(if (enabled) 1f else SelloTheme.alpha.disabled)
                    .padding(SelloTheme.spacing.xs)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(SwatchRing)
                        .border(
                            width = 2.5.dp,
                            color = if (swatch.chosen) colors.ink else colors.paper,
                            shape = CircleShape
                        )
                ) {
                    // The ink's own swatch, not the active scheme: it previews an ink.
                    val fill = swatch.fill
                    if (fill != null) {
                        Box(Modifier.size(SwatchSize).background(fill, CircleShape))
                    } else {
                        Rainbow()
                    }
                    swatch.badge?.let { SwatchBadge(it, Modifier.align(Alignment.BottomEnd)) }
                }
                Text(
                    swatch.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = with(SelloTheme.type.weight) {
                            if (swatch.chosen) strong else soft
                        }
                    ),
                    color = if (swatch.chosen) colors.ink else colors.inkSoft,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Tuya before it has a hue: every hue the generator gives, round the circle, and a +. */
@Composable
private fun Rainbow() {
    val brush = remember { Brush.sweepGradient(rainbowStops()) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(SwatchSize).background(brush, CircleShape)
    ) {
        Icon(SelloIcon.Add.painter(), null, Modifier.size(26.dp), SelloTheme.colors.paper)
    }
}

/** A small paper disc with [icon] at the swatch's lower edge, saying what the ink is. */
@Composable
private fun SwatchBadge(icon: SelloIcon, modifier: Modifier) {
    val colors = SelloTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset(x = 4.dp, y = 4.dp)
            .size(22.dp)
            .background(colors.paper, CircleShape)
            .border(1.5.dp, colors.rule, CircleShape)
    ) {
        Icon(icon.painter(), null, Modifier.size(14.dp), colors.inkSoft)
    }
}

/** The light band of every [RAINBOW_STEP]th degree, closing the circle where it began. */
internal fun rainbowStops(): List<Color> =
    (0..360 step RAINBOW_STEP).map { HueInk(it % 360).swatch }

private const val RAINBOW_STEP = 6

private val SwatchSize = 44.dp

private val SwatchRing = 56.dp
