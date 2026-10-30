package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The most lines one bulk receipt takes. */
const val BULK_RECEIPT_MAX_LINES = 30

/** How a bulk line's mark is drawn. */
enum class BulkMarkTone {
    /** A category: the filled brand circle. */
    Category,

    /** An income, an aporte or a dividend: green on soft green. */
    Gain,

    /** A retiro: soft ink on dim paper. */
    Neutral,

    /** A valuation: brand ink on soft brand. */
    Value
}

/** The ink of a bulk line's amount or of the receipt's total. */
enum class BulkAmountTone { Ink, Gain }

/**
 * One line of a bulk receipt, already worded and formatted by the application.
 *
 * @param key stable across recompositions, so a removed line takes its own drag with it.
 * @param mark the line's icon; null while it has no category, source or type, drawn as a
 * dashed "?" circle.
 * @param title its category, source or type, or what stands in for it (the note,
 * "Sin categoría"); [titleSoft] draws a stand-in in the soft ink.
 * @param detail the note, or the date and note, under the title.
 * @param amount null while nothing is typed: a soft 0.
 * @param reason why the line cannot be saved: outlines it in loss and replaces [detail].
 */
@Immutable
data class BulkLine(
    val key: String,
    val mark: SelloIcon?,
    val markTone: BulkMarkTone,
    val title: String,
    val amount: MoneyTextValue?,
    val titleSoft: Boolean = false,
    val detail: String? = null,
    val amountTone: BulkAmountTone = BulkAmountTone.Ink,
    val reason: String? = null
)

/** What the receipt's header says, at its start. */
@Immutable
sealed interface BulkCaption {
    /** Normally a title and the line count; a goal's or investment's receipt has only the count. */
    data class Count(val title: String, val count: String? = null) : BulkCaption

    /** What stops the receipt, such as "Falta 1 categoría"; [icon] leads it with the error mark. */
    data class Problem(val text: String, val icon: Boolean = true) : BulkCaption
}

/** The foot's figure: [label] ("Total", "Aportado") and the already formatted [value]. */
@Immutable
data class BulkTotal(
    val label: String,
    val value: MoneyTextValue,
    val tone: BulkAmountTone = BulkAmountTone.Ink
)

/**
 * The bulk receipt of Anotar varios: a pinked slip with the [caption] and [headerEnd] (the
 * receipt's date) on top, the [lines], a tear line and a foot with **Otro renglón** and the
 * [total]. It takes the height its [modifier] gives it: the lines scroll inside, fading at
 * the edge that hides more, while the header and the foot stay.
 *
 * Tapping a line calls [onSelect]; the [highlighted] one is edged in ink, shows a caret and
 * carries [highlightedFoot] (a goal's or investment's date pill). [onRemove] runs from a swipe
 * towards the start past 40 % of the line, from its long-press menu or from the action a
 * screen reader offers; the last line offers none of them. Each line is read as one item:
 * its title, detail, amount and reason. While not [enabled] nothing responds.
 */
@Composable
fun BulkReceipt(
    lines: List<BulkLine>,
    highlighted: Int,
    caption: BulkCaption,
    total: BulkTotal,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    addEnabled: Boolean = true,
    enabled: Boolean = true,
    headerEnd: @Composable () -> Unit = {},
    highlightedFoot: (@Composable () -> Unit)? = null
) {
    require(lines.size in 1..BULK_RECEIPT_MAX_LINES) {
        "A bulk receipt takes 1 to $BULK_RECEIPT_MAX_LINES lines, not ${lines.size}"
    }
    require(highlighted in lines.indices) { "Line $highlighted is not on the receipt" }
    val list = rememberLazyListState()
    val reduced = SelloTheme.reducedMotion
    // Keep the line the pad types into in sight, such as a line just added at the end.
    LaunchedEffect(highlighted, lines.size) {
        val shown = list.layoutInfo.visibleItemsInfo.any { item ->
            item.index == highlighted &&
                item.offset >= list.layoutInfo.viewportStartOffset &&
                item.offset + item.size <= list.layoutInfo.viewportEndOffset
        }
        if (!shown) {
            if (reduced) list.scrollToItem(highlighted) else list.animateScrollToItem(highlighted)
        }
    }
    Slip(modifier.fillMaxWidth(), contentPadding = ReceiptPadding) {
        ReceiptHeader(caption, headerEnd)
        LazyColumn(
            state = list,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadeEdges(list.canScrollBackward, list.canScrollForward)
                .testTag(BULK_RECEIPT_LINES_TAG)
        ) {
            itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
                BulkLineRow(
                    line = line,
                    highlighted = index == highlighted,
                    ruled = index < lines.lastIndex,
                    removable = lines.size > 1,
                    enabled = enabled,
                    onSelect = { onSelect(index) },
                    onRemove = { onRemove(index) },
                    foot = highlightedFoot.takeIf { index == highlighted }
                )
            }
        }
        TearLine(bleed = ReceiptSide, verticalMargin = 8.dp)
        ReceiptFoot(total, onAdd, addEnabled && enabled)
    }
}

@Composable
private fun ReceiptHeader(caption: BulkCaption, end: @Composable () -> Unit) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
    ) {
        Box(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            when (caption) {
                is BulkCaption.Count -> Column {
                    Text(
                        caption.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.W800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    caption.count?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkSoft,
                            maxLines = 1
                        )
                    }
                }

                is BulkCaption.Problem -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (caption.icon) {
                        Icon(
                            SelloIcon.Error.painter(),
                            null,
                            Modifier.size(20.dp),
                            colors.semantic.loss
                        )
                    }
                    Text(
                        caption.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.W700,
                        color = colors.semantic.loss,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        end()
    }
}

@Composable
private fun ReceiptFoot(total: BulkTotal, onAdd: () -> Unit, addEnabled: Boolean) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
    ) {
        SelloButton(
            text = stringResource(R.string.sello_bulk_add_line),
            onClick = onAdd,
            kind = ButtonKind.Text,
            icon = SelloIcon.Add,
            enabled = addEnabled,
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier.offset(x = (-4).dp)
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            Text(
                total.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = colors.inkSoft
            )
            MoneyText(
                total.value,
                style = MoneyStyle.Title,
                fontWeight = FontWeight.W900,
                color = total.tone.ink()
            )
        }
    }
}

/**
 * One line, with the drag that removes it and its long-press menu. The uncovered strip is
 * not read aloud: the same removal is offered by name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BulkLineRow(
    line: BulkLine,
    highlighted: Boolean,
    ruled: Boolean,
    removable: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    foot: (@Composable () -> Unit)?
) {
    val colors = SelloTheme.colors
    val remove by rememberUpdatedState(onRemove)
    val reduced = SelloTheme.reducedMotion
    val towardsStart = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val scope = rememberCoroutineScope()
    // How far the line has been dragged towards the start: zero or negative.
    val dragged = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val canRemove = removable && enabled
    val removeLabel = stringResource(R.string.sello_bulk_remove_line)
    val spoken = listOfNotNull(
        line.title,
        line.detail.takeIf { line.reason == null },
        line.amount?.spoken ?: stringResource(R.string.sello_amount_empty),
        line.reason
    ).joinToString(", ")
    val edge = when {
        line.reason != null -> colors.semantic.loss
        highlighted -> colors.ink
        else -> null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(LineShape)
            .onSizeChanged { width = it.width }
    ) {
        if (dragged.value < 0f) {
            val past = width > 0 && dragged.value <= -width * SWIPE_THRESHOLD
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.semantic.loss)
                    .padding(horizontal = 18.dp)
                    .clearAndSetSemantics {}
            ) {
                Icon(SelloIcon.Close.painter(), null, Modifier.size(20.dp), colors.semantic.onLoss)
                Text(
                    stringResource(
                        if (past) {
                            R.string.sello_bulk_release_to_remove
                        } else {
                            R.string.sello_bulk_remove
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.W800,
                    color = colors.semantic.onLoss,
                    maxLines = 1
                )
            }
        }
        Column(
            Modifier
                .offset { IntOffset((dragged.value * towardsStart).roundToInt(), 0) }
                .fillMaxWidth()
                .clip(LineShape)
                .background(if (highlighted) colors.paperAlt else colors.paper)
                .then(
                    if (edge != null) {
                        Modifier.border(2.dp, edge, LineShape)
                    } else if (ruled) {
                        Modifier.drawBehind {
                            val rule = 1.dp.toPx()
                            drawLine(
                                colors.rule,
                                Offset(0f, size.height - rule / 2),
                                Offset(size.width, size.height - rule / 2),
                                rule
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .draggable(
                    enabled = canRemove,
                    orientation = Orientation.Horizontal,
                    reverseDirection = towardsStart < 0,
                    state = rememberDraggableState { delta ->
                        val to = (dragged.value + delta).coerceIn(-width.toFloat(), 0f)
                        scope.launch { dragged.snapTo(to) }
                    },
                    onDragStopped = {
                        val past = width > 0 && dragged.value <= -width * SWIPE_THRESHOLD
                        if (reduced) dragged.snapTo(0f) else dragged.animateTo(0f)
                        if (past) remove()
                    }
                )
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onLongClick = { menuOpen = true }.takeIf { canRemove },
                    onClick = onSelect
                )
                .clearAndSetSemantics {
                    contentDescription = spoken
                    testTag = BULK_LINE_TAG
                    role = Role.Button
                    selected = highlighted
                    if (enabled) {
                        onClick {
                            onSelect()
                            true
                        }
                    }
                    if (canRemove) {
                        customActions = listOf(
                            CustomAccessibilityAction(removeLabel) {
                                remove()
                                true
                            }
                        )
                    }
                }
                .heightIn(min = 54.dp)
                .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
        ) {
            LineContent(line, highlighted)
            if (foot != null) {
                Row(Modifier.padding(start = MarkSize + 10.dp, top = 2.dp)) { foot() }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.paper,
            modifier = Modifier.testTag(BULK_LINE_MENU_TAG)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        removeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.ink
                    )
                },
                onClick = {
                    menuOpen = false
                    remove()
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.LineContent(line: BulkLine, highlighted: Boolean) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
    ) {
        LineMark(line.mark, line.markTone, missingInLoss = line.reason != null)
        Column(Modifier.weight(1f)) {
            Text(
                line.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (line.titleSoft) FontWeight.W600 else FontWeight.W800,
                color = if (line.titleSoft) colors.inkSoft else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val under = line.reason ?: line.detail
            if (under != null) {
                Text(
                    under,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (line.reason != null) FontWeight.W700 else null,
                    color = if (line.reason != null) colors.semantic.loss else colors.inkSoft,
                    maxLines = if (line.reason != null) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (line.amount == null) {
                Text(
                    "0",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.W800,
                    color = colors.outline
                )
            } else {
                MoneyText(
                    line.amount,
                    fontWeight = FontWeight.W800,
                    color = line.amountTone.ink(),
                    singleLine = false
                )
            }
            if (highlighted) {
                Spacer(Modifier.width(2.dp))
                Box(Modifier.size(width = 2.dp, height = 18.dp).background(colors.brand))
            }
        }
    }
}

@Composable
private fun LineMark(icon: SelloIcon?, tone: BulkMarkTone, missingInLoss: Boolean) {
    val colors = SelloTheme.colors
    if (icon == null) {
        val ink = if (missingInLoss) colors.semantic.loss else colors.outline
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(MarkSize).drawBehind {
                val stroke = 2.dp.toPx()
                drawCircle(
                    ink,
                    radius = (size.minDimension - stroke) / 2,
                    style = Stroke(
                        stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                        )
                    )
                )
            }
        ) {
            Text("?", fontWeight = FontWeight.W900, color = ink)
        }
        return
    }
    if (tone == BulkMarkTone.Category) {
        CategoryCircle(icon, 1f, size = MarkSize, animate = false)
        return
    }
    val (back, ink) = when (tone) {
        BulkMarkTone.Gain -> colors.semantic.gainSoft to colors.semantic.gain
        BulkMarkTone.Neutral -> colors.paperDim to colors.inkSoft
        else -> colors.brandSoft to colors.onBrandSoft
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(MarkSize).background(back, CircleShape)
    ) {
        Icon(icon.painter(), null, Modifier.size(20.dp), ink)
    }
}

@Composable
private fun BulkAmountTone.ink(): Color = when (this) {
    BulkAmountTone.Ink -> SelloTheme.colors.ink
    BulkAmountTone.Gain -> SelloTheme.colors.semantic.gain
}

/** Fades the content out over [FadeLength] at each end that hides more of it. */
private fun Modifier.fadeEdges(top: Boolean, bottom: Boolean): Modifier = if (!top && !bottom) {
    this
} else {
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val fade = FadeLength.toPx().coerceAtMost(size.height / 2)
        if (top) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
                size = size.copy(height = fade),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottom) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    size.height - fade,
                    size.height
                ),
                topLeft = Offset(0f, size.height - fade),
                size = size.copy(height = fade),
                blendMode = BlendMode.DstIn
            )
        }
    }
}

private val ReceiptSide = 12.dp
private val ReceiptPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 20.dp)
private val LineShape = RoundedCornerShape(12.dp)
private val MarkSize = 36.dp
private val FadeLength = 18.dp
private const val SWIPE_THRESHOLD = 0.4f

/** The test tag of each line of a [BulkReceipt]. */
const val BULK_LINE_TAG = "bulk-receipt:line"

/** The test tag of the scrolling list of a [BulkReceipt]'s lines. */
const val BULK_RECEIPT_LINES_TAG = "bulk-receipt:lines"

/** The test tag of a line's long-press menu. */
const val BULK_LINE_MENU_TAG = "bulk-receipt:menu"
