package com.software.sello.designsystem.component

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * A short statement about something that just happened, with at most one thing to do
 * about it, such as "Gasto borrado" and "Deshacer". It is announced when it appears.
 * [spoken] is what is read aloud, when that should say more than fits on the line.
 * While [busy] the action stays in view and cannot be pressed. An [icon] leads the
 * message in place of the confirmation check, such as the repeat icon on what Sello
 * recorded by itself.
 *
 * It holds no time: whoever shows it decides how long, and takes it away. With
 * [remaining] (1 to 0) it shows that time as a lighter band over the bar that shrinks
 * from the end, and a thin line along its foot.
 */
@Composable
fun SelloNotice(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    busy: Boolean = false,
    spoken: String = message,
    confirmation: Boolean = false,
    icon: SelloIcon? = null,
    remaining: Float? = null
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .widthIn(max = NoticeMaxWidth)
            .fillMaxWidth()
            .clip(SelloTheme.shapes.control)
            .background(colors.ink)
            .drawBehind {
                val left = remaining?.coerceIn(0f, 1f) ?: return@drawBehind
                val width = size.width * left
                drawRect(
                    colors.paper.copy(alpha = TIME_BAND_ALPHA),
                    size = Size(width, size.height)
                )
                val line = TIME_LINE.toPx()
                drawRect(
                    colors.brandSoft,
                    topLeft = Offset(0f, size.height - line),
                    size = Size(width, line)
                )
            }
            .padding(start = SelloTheme.spacing.lg, end = SelloTheme.spacing.xs)
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
    ) {
        if (icon != null) {
            // Brand-soft on the ink bar, light or dark: the brand itself is too faint on
            // the dark scheme's light bar.
            Icon(icon.painter(), null, Modifier.size(22.dp), colors.brandSoft)
        } else if (confirmation) {
            Icon(
                SelloIcon.Check.painter(),
                null,
                Modifier.size(22.dp).testTag(SELLO_NOTICE_CHECK_TAG),
                colors.semantic.onGain.takeIf { colors.isDark } ?: colors.semantic.gain
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.paper,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = SelloTheme.spacing.sm)
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                }
        )
        if (actionLabel != null && onAction != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(SelloTheme.shapes.control)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onAction)
                    .heightIn(min = SelloTheme.spacing.minTouchTarget)
                    .padding(horizontal = SelloTheme.spacing.md)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.paper.copy(alpha = if (busy) BUSY_ALPHA else 1f)
                )
            }
        }
    }
}

/** Something a line offers besides being pressed, such as "Borrar". */
class LineAction(val label: String, val onClick: () -> Unit)

/**
 * A [LeaderLine] that stands for something that can be opened and acted on, such as a
 * recorded expense. Pressing it is [onClick]. Every one of [actions] is reachable three
 * ways: a long press opens them as a menu, a screen reader is offered them by name,
 * and [swipeAway], when given, also runs by dragging the line towards the start past
 * 40 % of its width. Nothing depends on a gesture alone.
 *
 * The line does not remove itself. After a swipe it returns to its place, and it is
 * gone when what it stands for is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionLine(
    label: String,
    onClick: () -> Unit,
    actions: List<LineAction>,
    modifier: Modifier = Modifier,
    caption: String? = null,
    swipeAway: LineAction? = null,
    value: @Composable () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val line: @Composable () -> Unit = {
        LeaderLine(
            label = label,
            caption = caption,
            value = value,
            modifier = modifier
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

private val NoticeMaxWidth = 560.dp
private const val BUSY_ALPHA = 0.5f
private const val SWIPE_THRESHOLD = 0.4f

const val ACTION_LINE_MENU_TAG = "action-line:menu"

/** The confirmation check of a [SelloNotice]; decorative, so it says nothing. */
const val SELLO_NOTICE_CHECK_TAG = "sello-notice:check"

/** How much the time left lightens the bar, and the line along its foot. */
private const val TIME_BAND_ALPHA = 0.14f
private val TIME_LINE = 3.dp
