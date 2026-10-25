package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * The days of the month, 1 to 31, as round toggles in rows of seven, or four from font
 * scale 1.6 so the numbers keep their size. Every day keeps a 48dp touch target; where
 * seven of those do not fit the width, a row holds fewer. A chosen day is filled with
 * the brand. Each is read as [dayDescription] with whether it is chosen. Which days may
 * be chosen together belongs to the caller.
 */
@Composable
fun DayGrid(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    dayDescription: (Int) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wanted = if (fontScale >= LARGE_TYPE_SCALE) 4 else 7
        val columns = minOf(wanted, (maxWidth / DayTarget).toInt().coerceAtLeast(1))
        Column {
            (1..DAYS).chunked(columns).forEach { row ->
                Row {
                    row.forEach { day ->
                        val chosen = day in selected
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = DayTarget)
                                .toggleable(
                                    value = chosen,
                                    enabled = enabled,
                                    role = Role.Checkbox,
                                    onValueChange = { onToggle(day) }
                                )
                                .semantics { contentDescription = dayDescription(day) }
                                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                                .padding(horizontal = 2.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .clip(CircleShape)
                                .background(if (chosen) colors.brand else colors.paperAlt)
                        ) {
                            Text(
                                day.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.W700
                                ),
                                color = if (chosen) colors.onBrand else colors.ink,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * How many weeks or days between one and the next: a minus, the [value], a plus and the
 * [unit] after them, such as "semanas, desde el día que elegiste". The value is read with
 * its unit and said again whenever it changes. The bounds belong to the caller, who
 * turns a button off with [canDecrease] or [canIncrease].
 */
@Composable
fun IntervalStepper(
    value: Int,
    unit: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp)
    ) {
        StepButton(MINUS, decreaseLabel, canDecrease, onDecrease)
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp).semantics {
                contentDescription = "$value $unit"
                liveRegion = LiveRegionMode.Polite
            }
        )
        StepButton(PLUS, increaseLabel, canIncrease, onIncrease)
        Text(
            unit,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
            color = colors.ink,
            modifier = Modifier.weight(1f).clearAndSetSemantics {}
        )
    }
}

@Composable
private fun StepButton(sign: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = SelloTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .sizeIn(minWidth = DayTarget, minHeight = DayTarget)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(StepShape)
            .background(colors.brandSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
    ) {
        Text(
            sign,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onBrandSoft,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/**
 * The sheet that says how something repeats, over the screen it belongs to: a scrim that
 * closes it when tapped, and paper docked at the bottom with [title] as its heading and
 * [content] under it, scrolling when it is taller than the screen, as at large text. It
 * keeps clear of the system bars. Back is the screen's to handle, as is what lies behind.
 */
@Composable
fun RepeatSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SelloTheme.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .clearAndSetSemantics {}
        )
        Surface(
            color = colors.paper,
            shape = SelloTheme.shapes.sheet,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = SheetTopGap)
                .semantics { paneTitle = title }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    modifier = Modifier
                        .padding(start = 2.dp, end = 2.dp, bottom = 10.dp)
                        .semantics { heading() }
                )
                content()
            }
        }
    }
}

/**
 * The last row of a [RepeatSheet]: [dismissLabel] outlined in a third of the width and
 * [confirmLabel] filled in the rest. When a label would not fit beside the other, as at
 * large text, the two go one above the other.
 */
@Composable
fun RepeatSheetActions(
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true
) {
    ButtonPair(
        firstWeight = 1f,
        secondWeight = 2f,
        modifier = modifier,
        first = { SelloButton(dismissLabel, onDismiss, kind = ButtonKind.Outlined) },
        second = { SelloButton(confirmLabel, onConfirm, enabled = confirmEnabled) }
    )
}

/** When the next one is recorded, such as "Próximo: lun 5 nov. Se anota solo en su fecha." */
@Composable
fun RepeatNext(text: String, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Icon(SelloIcon.Autorenew.painter(), null, Modifier.size(20.dp), colors.brand)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
    }
}

/**
 * One payment or income that repeats: its [icon] in a circle, [name] over [caption] (the
 * category, the rule, the next date), an optional [pausedLabel] stamp and the [amount].
 * One still [soon] to come is in soft ink; an [income] is in gain ink. [dimmed], for one
 * paused or ended, softens it while every text keeps its contrast. The whole row is one
 * button read as [spoken]. From font scale 1.6 the amount goes under the name. A rule
 * separates it from the next row unless it is the [last].
 */
@Composable
fun ScheduleRow(
    name: String,
    caption: String,
    amount: MoneyTextValue,
    icon: SelloIcon,
    onClick: () -> Unit,
    spoken: String,
    modifier: Modifier = Modifier,
    soon: Boolean = false,
    income: Boolean = false,
    pausedLabel: String? = null,
    dimmed: Boolean = false,
    last: Boolean = false
) {
    val colors = SelloTheme.colors
    val large = LocalDensity.current.fontScale >= LARGE_TYPE_SCALE
    val amountInk = when {
        dimmed || soon -> colors.inkSoft
        income -> colors.semantic.gain
        else -> colors.ink
    }
    val figure: @Composable () -> Unit = {
        MoneyText(
            amount,
            color = amountInk,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (soon) FontWeight.W700 else FontWeight.W800
            )
        )
    }
    val stamp: @Composable () -> Unit = {
        if (pausedLabel != null) Stamp(pausedLabel, StampInk.Neutral)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .drawBehind {
                if (!last) {
                    val line = 1.dp.toPx()
                    drawLine(
                        colors.rule,
                        Offset(0f, size.height - line / 2),
                        Offset(size.width, size.height - line / 2),
                        line
                    )
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .padding(vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(34.dp).background(colors.paperAlt, CircleShape)
        ) {
            Icon(
                icon.painter(),
                null,
                Modifier.size(19.dp),
                if (dimmed) colors.inkSoft else colors.brand
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                color = if (dimmed) colors.inkSoft else colors.ink
            )
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            if (large) {
                stamp()
                figure()
            }
        }
        if (!large) {
            stamp()
            figure()
        }
    }
}

/**
 * Two buttons side by side, sharing the width by [firstWeight] and [secondWeight], or
 * one above the other at full width when a word of either label would not fit its share.
 */
@Composable
internal fun ButtonPair(
    firstWeight: Float,
    secondWeight: Float,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    Layout(
        contents = listOf(first, second),
        modifier = modifier.fillMaxWidth()
    ) { (firstButton, secondButton), constraints ->
        val space = gap.roundToPx()
        val width = constraints.maxWidth
        val firstWidth = ((width - space) * firstWeight / (firstWeight + secondWeight)).toInt()
        val secondWidth = width - space - firstWidth
        val a = firstButton.single()
        val b = secondButton.single()
        val besides = a.minIntrinsicWidth(Constraints.Infinity) <= firstWidth &&
            b.minIntrinsicWidth(Constraints.Infinity) <= secondWidth
        if (besides) {
            // Both as tall as the taller one, so a label on two lines leaves no step.
            val height = maxOf(a.minIntrinsicHeight(firstWidth), b.minIntrinsicHeight(secondWidth))
            val placedA = a.measure(Constraints.fixedWidth(firstWidth).copy(minHeight = height))
            val placedB = b.measure(Constraints.fixedWidth(secondWidth).copy(minHeight = height))
            layout(width, maxOf(placedA.height, placedB.height)) {
                placedA.placeRelative(0, 0)
                placedB.placeRelative(firstWidth + space, 0)
            }
        } else {
            val full = Constraints.fixedWidth(width)
            val placedA = a.measure(full)
            val placedB = b.measure(full)
            layout(width, placedA.height + space + placedB.height) {
                placedA.placeRelative(0, 0)
                placedB.placeRelative(0, placedA.height + space)
            }
        }
    }
}

private const val DAYS = 31
private const val LARGE_TYPE_SCALE = 1.6f
private const val DISABLED_ALPHA = 0.38f
private const val MINUS = "−"
private const val PLUS = "+"
private val DayTarget = 48.dp
private val StepShape = RoundedCornerShape(12.dp)
private val SheetTopGap = 24.dp

/** The reference's scrim: the desk's darkest ink at 55 %. */
private val Scrim = Color(0xFF050814).copy(alpha = 0.55f)
