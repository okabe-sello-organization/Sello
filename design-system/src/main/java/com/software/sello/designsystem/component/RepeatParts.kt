package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * The parts an expense, an income and a schedule use to say how something repeats. Every
 * text arrives written and every rule is the caller's; these only draw.
 */

const val REPEAT_ROW_TAG = "repeat:row"

const val REPEAT_SHEET_TAG = "repeat:sheet"

const val REPEAT_DONE_TAG = "repeat:done"

const val REPEAT_END_CONFIRM_TAG = "repeat:end-confirm"

/**
 * Se repite's row under the note, read as [spoken]: with an [action] ("Cambiar"), [text]
 * says the rule and its next date; without one, [text] is an invitation like Agregar nota
 * ("Repetir este gasto").
 */
@Composable
fun RepeatRow(
    text: String,
    spoken: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null
) {
    EntryLine(
        text = text,
        icon = SelloIcon.Autorenew,
        onClick = onClick,
        modifier = modifier.testTag(REPEAT_ROW_TAG).semantics { contentDescription = spoken },
        action = action,
        placeholder = action == null,
        enabled = enabled
    )
}

/** The words of a [RepeatChoiceSheet]; [kinds] are its chips, in order. */
@Immutable
data class RepeatSheetLabels(
    val title: String,
    val kinds: List<String>,
    val end: String,
    val endNever: String,
    val decrease: String,
    val increase: String,
    val remove: String,
    val done: String
)

/** How far apart the repeats are, as the sheet asks it. */
@Immutable
sealed interface RepeatSpacing {
    /**
     * Days of the month: [label] over the [DayGrid] with the [chosen] days filled, and
     * [note] under it when given.
     */
    data class MonthDays(val label: String, val chosen: Set<Int>, val note: String?) :
        RepeatSpacing

    /** One every [value] [unit]: [label] over the [IntervalStepper]. */
    data class Interval(
        val label: String,
        val value: Int,
        val unit: String,
        val canDecrease: Boolean,
        val canIncrease: Boolean
    ) : RepeatSpacing
}

/**
 * The sheet that chooses how it repeats: the kind ([kind] of [RepeatSheetLabels.kinds]),
 * then the days or the interval, the end ([endDate] is the dated chip's text, chosen when
 * [ended]), when the first one comes ([next]), and Quitar or Listo, which waits for
 * [doneEnabled]. The screen shows it while it is open and owns Back.
 */
@Composable
fun RepeatChoiceSheet(
    labels: RepeatSheetLabels,
    kind: Int,
    spacing: RepeatSpacing,
    endDate: String,
    ended: Boolean,
    next: String,
    doneEnabled: Boolean,
    dayDescription: (Int) -> String,
    onKind: (Int) -> Unit,
    onDayToggled: (Int) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onNoEnd: () -> Unit,
    onPickEnd: () -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit
) {
    RepeatSheet(
        title = labels.title,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(REPEAT_SHEET_TAG)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.kinds.forEachIndexed { index, label ->
                SelloChip(
                    label = label,
                    selected = kind == index,
                    onClick = { onKind(index) }
                )
            }
        }
        when (spacing) {
            is RepeatSpacing.MonthDays -> {
                SheetLabel(spacing.label)
                DayGrid(
                    selected = spacing.chosen,
                    onToggle = onDayToggled,
                    dayDescription = dayDescription
                )
                spacing.note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = SelloTheme.colors.inkSoft
                    )
                }
            }

            is RepeatSpacing.Interval -> {
                SheetLabel(spacing.label)
                IntervalStepper(
                    value = spacing.value,
                    unit = spacing.unit,
                    onDecrease = onDecrease,
                    onIncrease = onIncrease,
                    decreaseLabel = labels.decrease,
                    increaseLabel = labels.increase,
                    canDecrease = spacing.canDecrease,
                    canIncrease = spacing.canIncrease
                )
            }
        }
        SheetLabel(labels.end)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelloChip(
                label = labels.endNever,
                selected = !ended,
                onClick = onNoEnd
            )
            SelloChip(
                label = endDate,
                selected = ended,
                onClick = onPickEnd,
                icon = SelloIcon.EventBusy
            )
        }
        RepeatNext(
            text = next,
            modifier = Modifier.padding(vertical = SelloTheme.spacing.sm)
        )
        RepeatSheetActions(
            dismissLabel = labels.remove,
            onDismiss = onRemove,
            confirmLabel = labels.done,
            onConfirm = onDone,
            modifier = Modifier.testTag(REPEAT_DONE_TAG),
            confirmEnabled = doneEnabled
        )
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(top = SelloTheme.spacing.md, bottom = 6.dp)
    )
}

/**
 * The date picker for when a repeat ends, opened on [initial]: a day on or after [start];
 * none before it can be picked. [confirmLabel] picks.
 */
@Composable
fun RepeatEndPicker(
    initial: LocalDate,
    start: LocalDate,
    confirmLabel: String,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initial.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !day(utcTimeMillis).isBefore(start)

            override fun isSelectableYear(year: Int) = year >= start.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SelloButton(
                text = confirmLabel,
                onClick = { picker.selectedDateMillis?.let(::day)?.let(onPicked) ?: onDismiss() },
                modifier = Modifier.testTag(REPEAT_END_CONFIRM_TAG)
            )
        }
    ) {
        DatePicker(state = picker)
    }
}

/**
 * Asked when saving new terms on an entry a schedule recorded, with [name] (the schedule)
 * in bold between [lead] and [rest]: [onlyThisLabel] ("Solo este"), or [alsoLabel] ("Este
 * y los siguientes"), and [backLabel] ("Volver a editar").
 */
@Composable
fun FollowingChoice(
    title: String,
    lead: String,
    name: String,
    rest: String,
    onlyThisLabel: String,
    alsoLabel: String,
    backLabel: String,
    busy: Boolean,
    onChoose: (alsoFollowing: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val message = buildAnnotatedString {
        append(lead)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SelloTheme.colors.ink)) {
            append(name)
        }
        append(rest)
    }
    ChoiceSlip(
        title = title,
        message = message,
        firstLabel = onlyThisLabel,
        onFirst = { onChoose(false) },
        secondLabel = alsoLabel,
        onSecond = { onChoose(true) },
        backLabel = backLabel,
        onBack = onBack,
        busy = busy
    )
}

private fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun day(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

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
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = minOf(DAYS_PER_ROW, (maxWidth / DayTarget).toInt().coerceAtLeast(1))
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
                                .padding(horizontal = 2.dp, vertical = SelloTheme.spacing.xs)
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
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
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
 * The last row of a [RepeatSheet]: [dismissLabel] outlined in a third of the width and
 * [confirmLabel] filled in the rest. When a label would not fit beside the other, the
 * two go one above the other.
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
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 2.dp,
                end = 2.dp,
                top = SelloTheme.spacing.sm,
                bottom = SelloTheme.spacing.md
            )
    ) {
        Icon(SelloIcon.Autorenew.painter(), null, Modifier.size(20.dp), colors.brand)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
    }
}

private const val DAYS = 31

private const val DISABLED_ALPHA = 0.38f

private const val MINUS = "−"

private const val PLUS = "+"

private val DayTarget = 48.dp

private val StepShape = RoundedCornerShape(12.dp)

private const val DAYS_PER_ROW = 7
