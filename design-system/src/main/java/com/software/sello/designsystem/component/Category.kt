package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    staggerIndex: Int = 0
) {
    val colors = SelloTheme.colors
    val target = fillLevel(fraction, over)
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
    val ink = if (over) colors.semantic.loss else colors.brand
    val onInk = if (over) colors.semantic.onLoss else colors.onBrand
    val outline = 2.dp
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
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
            drawCircle(ink, radius = (this.size.minDimension - stroke) / 2, style = Stroke(stroke))
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
    }
}

private const val FILL_STAGGER_MILLIS = 30L

/**
 * A circle with the category's name and one amount beside it: what is left, or what was
 * spent when there is no limit, with [amountCaption] ("gastado") under it. The amount is
 * exact even when the circle can only show "full".
 *
 * [actions] are offered by a long press, as a menu, and to a screen reader by name;
 * pressing the cell is still only [onClick].
 *
 * @param description the whole cell in words, such as "Alimentación. Quedan 287.600
 * pesos de 900.000. 68 por ciento usado." The cell is one button that says this.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryCell(
    name: String,
    icon: SelloIcon,
    amount: MoneyTextValue,
    fraction: Float?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    over: Boolean = false,
    amountCaption: String? = null,
    staggerIndex: Int = 0,
    actions: List<LineAction> = emptyList()
) {
    val colors = SelloTheme.colors
    val largeType = LocalDensity.current.fontScale >= LARGE_NAME_SCALE
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .combinedClickable(
                role = Role.Button,
                onLongClick = { menuOpen = true }.takeIf { actions.isNotEmpty() },
                onClick = onClick
            )
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
                // The same actions the long press offers, by name, with no gesture.
                if (actions.isNotEmpty()) {
                    customActions = actions.map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.onClick()
                            true
                        }
                    }
                }
            }
    ) {
        LineActionMenu(actions, menuOpen) { menuOpen = false }
        CategoryCircle(icon, fraction, over = over, staggerIndex = staggerIndex)
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                color = colors.ink,
                maxLines = if (largeType) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            MoneyText(
                value = amount,
                fontWeight = FontWeight.W800,
                color = when {
                    over -> colors.semantic.loss
                    amountCaption != null -> colors.inkSoft
                    else -> colors.ink
                }
            )
            // Under the amount, as in the v2 grid: beside it, it squeezed the amount.
            if (amountCaption != null) {
                Text(
                    amountCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkSoft,
                    maxLines = 1
                )
            }
        }
    }
}

private const val LARGE_NAME_SCALE = 1.3f

/**
 * Lays [items] out in rows of [categoryColumns] for the width it is given: two columns,
 * three when wide, one at large type. Not lazy; a month has a handful of categories.
 */
@Composable
fun <T> CategoryGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    cell: @Composable (index: Int, item: T) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = categoryColumns(maxWidth, fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.withIndex().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (index, item) ->
                        Box(modifier = Modifier.weight(1f)) { cell(index, item) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
