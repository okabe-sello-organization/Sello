package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/** One top-level place. [id] is what a selection reports; the app decides what it opens. */
@Immutable
data class SelloDestination(val id: String, val label: String, val icon: SelloIcon)

/**
 * The top-level destinations of the app, however many there are. The same data feeds
 * the tab bar and the rail; neither keeps a selection or navigates on its own.
 */
@Immutable
class SelloNavigation(
    val destinations: List<SelloDestination>,
    val selectedId: String,
    val onSelect: (String) -> Unit
)

/**
 * The action that starts an entry from anywhere: the dock above the tabs, or the "+" on
 * a rail. [label] is also what it is called by a screen reader; the dock shows
 * [actionLabel]. [tone] and [icon] tell a tab's own action apart, as Metas' green flag
 * does; both the dock and the rail's button take them. A dock that is not [enabled]
 * keeps its place, drawn in the soft outline ink at full strength, so its [actionLabel]
 * and [icon] can say why nothing can be started here.
 */
@Immutable
class SelloDock(
    val label: String,
    val actionLabel: String,
    val onClick: () -> Unit,
    val tone: ButtonTone = ButtonTone.Brand,
    val icon: SelloIcon = SelloIcon.Add,
    val enabled: Boolean = true
)

/**
 * The rail that replaces the tab bar in short and expanded windows. With a [dock] its
 * button, in the dock's tone and with its icon, replaces the dock.
 */
@Composable
fun SelloNavigationRail(
    navigation: SelloNavigation,
    modifier: Modifier = Modifier,
    dock: SelloDock? = null
) {
    val colors = SelloTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // As wide as its longest label needs, so large text never shortens a tab's name.
        modifier = modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Max)
            .widthIn(min = RailWidth)
            .background(colors.paper)
            .padding(vertical = 8.dp)
    ) {
        if (dock != null) {
            val (container, content) = if (dock.enabled) {
                dock.tone.inks()
            } else {
                colors.paper to colors.inkSoft
            }
            IconButton(
                onClick = dock.onClick,
                enabled = dock.enabled,
                modifier = Modifier
                    .size(SelloTheme.spacing.minTouchTarget)
                    .background(container, SelloTheme.shapes.button)
                    .then(
                        if (dock.enabled) {
                            Modifier
                        } else {
                            Modifier.border(1.5.dp, colors.outline, SelloTheme.shapes.button)
                        }
                    )
            ) {
                Icon(dock.icon.painter(), dock.label, tint = content)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            navigation.destinations.forEach { destination ->
                val selected = destination.id == navigation.selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected, role = Role.Tab) {
                            navigation.onSelect(destination.id)
                        }
                ) {
                    SelectionMark(selected, Modifier.width(3.dp).height(56.dp))
                    DestinationContent(destination, selected, Modifier.weight(1f).height(56.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier) {
    Box(modifier.background(if (selected) SelloTheme.colors.brand else SelloTheme.colors.paper))
}

@Composable
private fun DestinationContent(
    destination: SelloDestination,
    selected: Boolean,
    modifier: Modifier
) {
    val colors = SelloTheme.colors
    val tint = if (selected) colors.brand else colors.inkSoft
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Icon(destination.icon.painter(filled = selected), null, Modifier.size(22.dp), tint)
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

internal val RailWidth = 80.dp

/** The month as a screen title that opens a month picker. It takes its ink from the bar. */
@Composable
fun MonthSwitcher(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .clip(SelloTheme.shapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(end = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 24.sp),
            // A second line before an ellipsis, as for any screen title at large text.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Icon(SelloIcon.KeyboardArrowDown.painter(), null, Modifier.size(22.dp))
    }
}

/**
 * One month in a [MonthGrid]. A month that is not [enabled] cannot be picked: the app
 * disables future months. [hasData] only dims a month that has nothing recorded.
 */
@Immutable
data class MonthCell(
    val id: String,
    val label: String,
    val spoken: String = label,
    val enabled: Boolean = true,
    val hasData: Boolean = true,
    val isCurrent: Boolean = false
)

/**
 * The twelve months of one year. It shows what it is given and reports a pick; which
 * months exist, which is current and which year comes next are the app's to decide. A
 * null year callback disables that arrow.
 */
@Composable
fun MonthGrid(
    yearLabel: String,
    months: List<MonthCell>,
    selectedId: String?,
    onPick: (String) -> Unit,
    previousYearLabel: String,
    nextYearLabel: String,
    modifier: Modifier = Modifier,
    onPreviousYear: (() -> Unit)? = null,
    onNextYear: (() -> Unit)? = null
) {
    val colors = SelloTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YearArrow(SelloIcon.ChevronLeft, previousYearLabel, onPreviousYear)
            Text(
                text = yearLabel,
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            YearArrow(SelloIcon.ChevronRight, nextYearLabel, onNextYear)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.selectableGroup()
        ) {
            months.chunked(MONTHS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { month ->
                        val selected = month.id == selectedId
                        val outline = when {
                            selected || month.isCurrent -> colors.brand
                            else -> colors.rule
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                                .alpha(if (month.enabled) 1f else DISABLED_MONTH_ALPHA)
                                .clip(SelloTheme.shapes.control)
                                .background(if (selected) colors.brand else colors.paper)
                                .border(
                                    if (month.isCurrent) 2.dp else 1.5.dp,
                                    outline,
                                    SelloTheme.shapes.control
                                )
                                .selectable(selected, month.enabled, Role.RadioButton) {
                                    onPick(month.id)
                                }
                                .clearAndSetSemantics { contentDescription = month.spoken }
                        ) {
                            Text(
                                text = month.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.W600
                                ),
                                color = when {
                                    selected -> colors.onBrand
                                    month.hasData -> colors.ink
                                    else -> colors.inkSoft
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearArrow(icon: SelloIcon, label: String, onClick: (() -> Unit)?) {
    IconButton(onClick = { onClick?.invoke() }, enabled = onClick != null) {
        Icon(
            painter = icon.painter(),
            contentDescription = label,
            tint = LocalContentColor.current.takeIf { onClick == null } ?: SelloTheme.colors.brand
        )
    }
}

private const val MONTHS_PER_ROW = 3
private const val DISABLED_MONTH_ALPHA = 0.38f
