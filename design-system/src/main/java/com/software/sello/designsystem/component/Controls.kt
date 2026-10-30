package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A full-width tab action. Its purpose, enabled state and busy state belong to the caller.
 * A disabled dock is not faded: it is outlined in the soft ink at full strength, so its
 * [label] stays readable as the reason it cannot be used.
 */
@Composable
fun EntryDock(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Brand,
    icon: SelloIcon = SelloIcon.Add,
    enabled: Boolean = true,
    busy: Boolean = false
) {
    if (!enabled && !busy) {
        UnavailableDock(label, icon, modifier)
        return
    }
    SelloButton(
        label,
        onClick,
        modifier.fillMaxWidth(),
        icon = icon,
        enabled = enabled,
        loading = busy,
        tone = tone
    )
}

@Composable
private fun UnavailableDock(label: String, icon: SelloIcon, modifier: Modifier) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colors.paper, SelloTheme.shapes.button)
            .border(1.5.dp, colors.outline, SelloTheme.shapes.button)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                disabled()
            }
            .padding(horizontal = 24.dp)
    ) {
        Icon(icon.painter(), contentDescription = null, Modifier.size(20.dp), colors.inkSoft)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkSoft,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SelloNavigationBar(navigation: SelloNavigation, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    require(navigation.destinations.size in 2..4)
    Row(modifier.fillMaxWidth().background(colors.paper).selectableGroup()) {
        navigation.destinations.forEach { destination ->
            val selected = destination.id == navigation.selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                    .selectable(selected, role = Role.Tab) { navigation.onSelect(destination.id) }
                    .padding(vertical = 8.dp, horizontal = 2.dp)
            ) {
                Box(
                    Modifier.size(
                        56.dp,
                        30.dp
                    ).background(
                        if (selected) colors.brandSoft else Color.Transparent,
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        destination.icon.painter(selected),
                        null,
                        Modifier.size(22.dp),
                        if (selected) colors.onBrandSoft else colors.inkSoft
                    )
                }
                Text(
                    destination.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.ink else colors.inkSoft
                )
            }
        }
    }
}

/**
 * A secondary action on the band, as Metas' **+ Nueva meta** beside its title: a paper
 * pill with a plus, the same paper on band as [PastMonthRibbon]'s return, so it stands
 * out without competing with the dock. Its touch target is 48dp; the pill itself is 36dp.
 *
 * With [onPaper] the same pill sits on a slip, as Recibo's **+ Nueva categoría**: the
 * soft brand fill instead of paper, which would not show on paper. [compact] is the
 * round + alone, for a [TitleBar] whose title would not fit beside the label.
 */
@Composable
fun BandChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPaper: Boolean = false,
    compact: Boolean = false
) {
    val colors = SelloTheme.colors
    val content = when {
        onPaper -> colors.onBrandSoft
        colors.isDark -> colors.ink
        else -> colors.band
    }
    val fill = when {
        onPaper -> colors.brandSoft
        colors.isDark -> colors.paper
        else -> Color.White
    }
    if (compact) {
        // Where the label would not fit: the round + alone, still said with its label.
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .clip(CircleShape)
                .background(fill)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label }
        ) {
            Icon(SelloIcon.Add.painter(), null, Modifier.size(22.dp), tint = content)
        }
        return
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(SelloIcon.Add.painter(), null, Modifier.size(20.dp), tint = content)
        Text(
            label,
            color = content,
            fontWeight = FontWeight.W800,
            style = MaterialTheme.typography.bodyMedium,
            // On a narrow slip the label wraps rather than being cut.
            maxLines = if (onPaper) 2 else 1
        )
    }
}

/**
 * At most two rows that scroll sideways together; the trailing fade is shown only when
 * more choices can be reached. Choices fill down each column first, and every choice in
 * a column takes the widest one's width so the columns line up. Each row is at least a
 * 48dp touch target tall; a shorter chip sits centred in it, leaving a visible gap.
 */
@Composable
fun ChipRows(
    count: Int,
    modifier: Modifier = Modifier,
    rows: Int = 2,
    item: @Composable (Int) -> Unit
) {
    require(rows in 1..2)
    if (count == 0) return
    val scroll = rememberScrollState()
    val actualRows = minOf(rows, count)
    val fadeColor = SelloTheme.colors.desk
    Layout(
        content = { repeat(count) { item(it) } },
        modifier = modifier.fillMaxWidth().drawWithContent {
            drawContent()
            if (scroll.canScrollForward) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, fadeColor),
                        size.width - 16.dp.toPx(),
                        size.width
                    )
                )
            }
        }.horizontalScroll(scroll).selectableGroup()
    ) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val columns = measurables.chunked(actualRows).map { column ->
            val width = column.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
            column.map { it.measure(Constraints.fixedWidth(width)) }
        }
        val rowHeights = List(actualRows) { row ->
            columns.maxOf { it.getOrNull(row)?.height ?: 0 }
        }
        val width = columns.sumOf { it.first().width } + gap * (columns.size - 1)
        // At least as wide as the row, so a few chips start at the leading edge.
        layout(maxOf(width, constraints.minWidth), rowHeights.sum()) {
            var x = 0
            columns.forEach { column ->
                var y = 0
                column.forEachIndexed { row, chip ->
                    chip.placeRelative(x, y + (rowHeights[row] - chip.height) / 2)
                    y += rowHeights[row]
                }
                x += column.first().width + gap
            }
        }
    }
}

/**
 * A full-width line under the chips that opens an editor, such as the note or an income
 * source's name. Empty, it reads as a quiet [text] prompt; filled, it shows the value and
 * the brand [action] (Editar, Cambiar) on the trailing edge.
 */
@Composable
fun EntryLine(
    text: String,
    icon: SelloIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null,
    placeholder: Boolean = false,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(EntryShape)
            .background(colors.paper)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(icon.painter(), null, tint = colors.brand, modifier = Modifier.size(22.dp))
        Text(
            text,
            Modifier.weight(1f),
            color = if (placeholder) colors.inkSoft else colors.ink,
            fontWeight = if (placeholder) null else FontWeight.W600,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (action != null && !placeholder) {
            Text(
                action,
                color = colors.brand,
                fontWeight = FontWeight.W800,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private val EntryShape = RoundedCornerShape(12.dp)

/**
 * Native text input docked above the IME. [context] says what the text belongs to, with
 * its facts in bold spans, and [contextIcon] leads it; [contextDescription] is what a
 * screen reader hears for the box, with amounts spoken in full. Under the box, [hint] (such as
 * "Opcional") gives way to [error]. Validation and draft retention are caller-owned.
 * The paper reaches the screen's edges; its content keeps clear of [windowInsets] at the
 * sides, such as a navigation bar on the right of a phone held sideways.
 */
@Composable
fun NoteComposer(
    context: AnnotatedString,
    value: String,
    onValueChange: (String) -> Unit,
    counter: String,
    hint: String?,
    error: String?,
    doneLabel: String,
    cancelLabel: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    contextIcon: SelloIcon? = null,
    contextDescription: String = context.text,
    doneEnabled: Boolean = error == null,
    windowInsets: WindowInsets = WindowInsets.safeDrawing
) {
    val colors = SelloTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Opening on an existing note puts the cursor after it, ready to keep typing or delete.
    val edited = rememberEditedText(value)
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Surface(color = colors.paper, shape = SelloTheme.shapes.sheet) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (contextIcon != null) {
                                Icon(
                                    contextIcon.painter(),
                                    null,
                                    tint = colors.brand,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                context,
                                color = colors.inkSoft,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        SelloButton(cancelLabel, onCancel, kind = ButtonKind.Text)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = edited.field,
                            onValueChange = { edited.edit(it)?.let(onValueChange) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = colors.ink,
                                fontWeight = FontWeight.W600
                            ),
                            cursorBrush = SolidColor(colors.brand),
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focus)
                                .semantics {
                                    contentDescription = contextDescription
                                    if (error != null) this.error(error)
                                },
                            decorationBox = { field ->
                                Box(
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(EntryShape)
                                        .background(colors.paperAlt)
                                        .border(
                                            2.dp,
                                            if (error !=
                                                null
                                            ) {
                                                colors.semantic.loss
                                            } else {
                                                Color.Transparent
                                            },
                                            EntryShape
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) { field() }
                            }
                        )
                        Surface(
                            onClick = onDone,
                            enabled = doneEnabled,
                            shape = CircleShape,
                            color = if (doneEnabled) colors.brand else colors.paperDim,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    SelloIcon.Check.painter(),
                                    doneLabel,
                                    tint = if (doneEnabled) colors.onBrand else colors.inkSoft
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp)
                    ) {
                        Text(
                            error ?: hint.orEmpty(),
                            Modifier.weight(1f),
                            color = if (error != null) colors.semantic.loss else colors.inkSoft,
                            fontWeight = if (error != null) FontWeight.W600 else null,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            counter,
                            color = if (error != null) colors.semantic.loss else colors.inkSoft,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
