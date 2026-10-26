package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/** One icon that can be chosen. [label] is what a screen reader calls it. */
@Immutable
data class IconChoice(val id: String, val icon: SelloIcon, val label: String)

/**
 * A grid of icons where exactly one can be chosen, as for a category. It shows what it
 * is given and reports a pick; which icons exist and which is chosen are the app's.
 * Up to [maxColumns] per row, fewer when the width or a large font leaves less room,
 * and every cell stays at least the minimum touch size. The chosen one is filled in
 * [tone]; [round] cells are circles. Icons in [inUse] are outlined dashed and remain
 * choosable; [inUseLabel] is added to what a screen reader says for them.
 */
@Composable
fun IconChoiceGrid(
    choices: List<IconChoice>,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxColumns: Int = 7,
    tone: ButtonTone = ButtonTone.Brand,
    round: Boolean = false,
    inUse: Set<String> = emptySet(),
    inUseLabel: String? = null
) {
    val colors = SelloTheme.colors
    val (fill, onFill) = tone.inks()
    val shape = if (round) CircleShape else SelloTheme.shapes.control
    val cell = SelloTheme.spacing.minTouchTarget
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = iconColumns(maxWidth, cell, maxColumns)
        Column(
            verticalArrangement = Arrangement.spacedBy(IconGap),
            modifier = Modifier.selectableGroup()
        ) {
            choices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(IconGap)) {
                    row.forEach { choice ->
                        val selected = choice.id == selectedId
                        val used = choice.id in inUse && !selected
                        val spoken = if (used && inUseLabel != null) {
                            "${choice.label}. $inUseLabel"
                        } else {
                            choice.label
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (round) Modifier.aspectRatio(1f) else Modifier)
                                .heightIn(min = cell)
                                .clip(shape)
                                .background(if (selected) fill else colors.paper)
                                .then(
                                    if (used) {
                                        Modifier.dashedBorder(colors.outline, shape)
                                    } else {
                                        Modifier.border(
                                            1.5.dp,
                                            if (selected) fill else colors.rule,
                                            shape
                                        )
                                    }
                                )
                                .selectable(
                                    selected = selected,
                                    enabled = enabled,
                                    role = Role.RadioButton,
                                    onClick = { onPick(choice.id) }
                                )
                                .semantics { contentDescription = spoken }
                        ) {
                            Icon(
                                painter = choice.icon.painter(),
                                contentDescription = null,
                                tint = when {
                                    selected -> onFill
                                    used -> colors.inkSoft
                                    else -> colors.ink
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    // Keeps the cells of a short last row the same width as the others.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** A dashed outline following [shape], for an icon another item already uses. */
private fun Modifier.dashedBorder(color: Color, shape: Shape) = drawBehind {
    val stroke = 1.5.dp.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline,
        color,
        style = Stroke(
            stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        )
    )
}

/** As many columns as fit at the minimum cell size, between one and [maxColumns]. */
fun iconColumns(width: Dp, cell: Dp, maxColumns: Int): Int {
    val fitting = ((width + IconGap) / (cell + IconGap)).toInt()
    return fitting.coerceIn(1, maxColumns)
}

private val IconGap = 6.dp
