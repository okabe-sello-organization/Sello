package com.software.sello.feature.recibo

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.categoryColumns
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
internal fun CategoryCell(
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
    actions: List<LineAction> = emptyList(),
    unlimited: Boolean = false,
    archived: Boolean = false
) {
    val colors = SelloTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
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
        CategoryCircle(
            icon,
            fraction,
            over = over,
            staggerIndex = staggerIndex,
            unlimited = unlimited,
            archived = archived
        )
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                color = colors.ink,
                maxLines = 1,
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

/**
 * Lays [items] out in rows of [categoryColumns] for the width it is given: two columns,
 * three when wide. Not lazy; a month has a handful of categories.
 */
@Composable
internal fun <T> CategoryGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    cell: @Composable (index: Int, item: T) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = categoryColumns(maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
            items.withIndex().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
                    row.forEach { (index, item) ->
                        Box(modifier = Modifier.weight(1f)) { cell(index, item) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Something a line offers besides being pressed, such as "Borrar". */
internal class LineAction(val label: String, val onClick: () -> Unit)

/**
 * A line that stands for something that can be opened and acted on, such as a recorded
 * expense. [content] draws it, putting the modifier it is given on its outermost layout,
 * and is read as one node. Pressing it is [onClick]. Every one of [actions] is reachable
 * three ways: a long press opens them as a menu, a screen reader is offered them by name,
 * and [swipeAway], when given, also runs by dragging the line towards the start past
 * 40 % of its width. Nothing depends on a gesture alone.
 *
 * The line does not remove itself. After a swipe it returns to its place, and it is
 * gone when what it stands for is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActionRow(
    onClick: () -> Unit,
    actions: List<LineAction>,
    modifier: Modifier = Modifier,
    swipeAway: LineAction? = null,
    content: @Composable (Modifier) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val line: @Composable () -> Unit = {
        content(
            modifier
                .background(SelloTheme.colors.paper)
                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                .combinedClickable(
                    role = Role.Button,
                    onLongClick = { menuOpen = true },
                    onClick = onClick
                )
                .semantics {
                    customActions = actions.map { action ->
                        CustomAccessibilityAction(action.label) {
                            action.onClick()
                            true
                        }
                    }
                }
        )
    }
    Box {
        if (swipeAway == null) line() else SwipeAway(swipeAway, line)
        LineActionMenu(actions, menuOpen) { menuOpen = false }
    }
}

/** [actions] as a menu. Choosing one closes it and then runs it. */
@Composable
internal fun LineActionMenu(actions: List<LineAction>, open: Boolean, onClose: () -> Unit) {
    DropdownMenu(
        expanded = open,
        onDismissRequest = onClose,
        containerColor = SelloTheme.colors.paper,
        modifier = Modifier.testTag(ACTION_LINE_MENU_TAG)
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SelloTheme.colors.ink
                    )
                },
                onClick = {
                    onClose()
                    action.onClick()
                }
            )
        }
    }
}

/**
 * Dragging [line] towards the start uncovers what letting go will do, in the colour of
 * a loss. Let go past [SWIPE_THRESHOLD] of the width and it is done, once; either way
 * the line goes back to its place. What is uncovered is not read aloud: the same
 * action is already offered by name.
 */
@Composable
private fun SwipeAway(action: LineAction, line: @Composable () -> Unit) {
    val run by rememberUpdatedState(action.onClick)
    val reduced = SelloTheme.reducedMotion
    val towardsStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val scope = rememberCoroutineScope()
    // How far the line has been dragged towards the start: zero or negative.
    val dragged = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    Box(Modifier.onSizeChanged { width = it.width }) {
        if (dragged.value < 0f) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .matchParentSize()
                    .background(SelloTheme.colors.semantic.loss)
                    .padding(horizontal = SelloTheme.spacing.lg)
                    .clearAndSetSemantics {}
            ) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = SelloTheme.colors.paper
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset((dragged.value * towardsStart).roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    reverseDirection = towardsStart < 0,
                    state = rememberDraggableState { delta ->
                        val to = (dragged.value + delta).coerceIn(-width.toFloat(), 0f)
                        scope.launch { dragged.snapTo(to) }
                    },
                    onDragStopped = {
                        if (width > 0 && dragged.value <= -width * SWIPE_THRESHOLD) run()
                        if (reduced) dragged.snapTo(0f) else dragged.animateTo(0f)
                    }
                )
        ) { line() }
    }
}

private const val BUSY_ALPHA = 0.5f

private const val SWIPE_THRESHOLD = 0.4f

internal const val ACTION_LINE_MENU_TAG = "action-line:menu"

/**
 * One category whose limit can be changed: its circle, [name] with a [caption] under it
 * ("gastaste 90.000 de 50.000"), the signed [amount] it has left (in loss ink when
 * [over]) and a chevron. Without a limit, [fraction] is null and [amount] is what it
 * spent; with [unlimited] (no limit on purpose) the circle carries the ∞ badge, and an
 * [archived] one is drawn in grey. The row is one button, read as [spoken].
 */
@Composable
internal fun LimitRow(
    name: String,
    icon: SelloIcon,
    caption: String,
    amount: MoneyTextValue?,
    fraction: Float?,
    over: Boolean,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unlimited: Boolean = false,
    archived: Boolean = false
) {
    val colors = SelloTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = null) {
                    onClick()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CategoryCircle(
            icon,
            fraction,
            size = 36.dp,
            over = over,
            animate = false,
            unlimited = unlimited,
            archived = archived
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W700)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        }
        amount?.let {
            MoneyText(
                it,
                color = if (over) colors.semantic.loss else colors.ink,
                fontWeight = FontWeight.W800
            )
        }
        Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(18.dp), colors.outline)
    }
}

/** A small label with a larger value under it, used in twos and threes under a tear line. */
@Composable
internal fun KeyValue(
    label: String,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    caption: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    value: @Composable () -> Unit
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = horizontalAlignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs)
        ) {
            if (icon != null) {
                Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = SelloTheme.colors.brand,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = SelloTheme.colors.inkSoft
            )
        }
        value()
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = SelloTheme.colors.inkSoft,
                maxLines = 2
            )
        }
    }
}

/** A [KeyValue] whose value is plain text. */
@Composable
internal fun KeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    caption: String? = null,
    valueColor: Color = Color.Unspecified
) {
    KeyValue(label, modifier, icon, caption) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor.takeOrElse { SelloTheme.colors.ink }
        )
    }
}
