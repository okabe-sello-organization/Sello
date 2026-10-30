package com.software.sello.presentation.recurrence

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonPair
import com.software.sello.designsystem.component.ChoiceSlip
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

const val REPEAT_ROW_TAG = "repeat:row"

const val REPEAT_SHEET_TAG = "repeat:sheet"

const val REPEAT_DONE_TAG = "repeat:done"

const val REPEAT_END_CONFIRM_TAG = "repeat:end-confirm"

/**
 * Se repite's row under the note: while the entry is recorded once, an invitation like
 * Agregar nota ("Repetir este gasto", or ingreso when [income]); otherwise the rule and its
 * next date with Cambiar. [start] is the day the rule counts from.
 */
@Composable
fun RepeatRow(
    repeat: RepeatUi,
    income: Boolean,
    start: LocalDate,
    today: LocalDate,
    dates: DateLabels,
    enabled: Boolean,
    onAction: (RepeatAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val rule = repeat.choice?.rule()
    val next = repeat.next
    if (rule != null && next != null) {
        val label = repeatRuleLabel(rule, start)
        val day = dates.day(next, today)
        val spoken = stringResource(R.string.repeat_row_spoken, label, day)
        EntryLine(
            text = stringResource(
                R.string.repeat_row,
                label.replaceFirstChar {
                    it.uppercase()
                },
                day
            ),
            icon = SelloIcon.Autorenew,
            onClick = { onAction(RepeatAction.Open) },
            modifier = modifier.testTag(REPEAT_ROW_TAG).semantics { contentDescription = spoken },
            action = stringResource(R.string.repeat_change),
            enabled = enabled
        )
    } else {
        val text = stringResource(
            if (income) R.string.repeat_add_income else R.string.repeat_add_expense
        )
        val spoken = stringResource(R.string.repeat_row_off_spoken, text)
        EntryLine(
            text = text,
            icon = SelloIcon.Autorenew,
            onClick = { onAction(RepeatAction.Open) },
            modifier = modifier.testTag(REPEAT_ROW_TAG).semantics { contentDescription = spoken },
            placeholder = true,
            enabled = enabled
        )
    }
}

/**
 * The sheet that chooses how it repeats: the kind, then the days or the interval, the end,
 * when the first one comes, and Quitar or Listo. Listo waits for a choice that records
 * something. The screen shows it while [RepeatUi.sheet] is set and owns Back.
 */
@Composable
fun RepeatChoiceSheet(
    repeat: RepeatUi,
    start: LocalDate,
    today: LocalDate,
    dates: DateLabels,
    onAction: (RepeatAction) -> Unit
) {
    val sheet = repeat.sheet ?: return
    RepeatSheet(
        title = stringResource(R.string.repeat_title),
        onDismiss = { onAction(RepeatAction.Dismiss) },
        modifier = Modifier.testTag(REPEAT_SHEET_TAG)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                RepeatKind.Monthly to R.string.repeat_kind_monthly,
                RepeatKind.TwiceMonthly to R.string.repeat_kind_twice_monthly,
                RepeatKind.Weeks to R.string.repeat_kind_weeks,
                RepeatKind.Days to R.string.repeat_kind_days
            ).forEach { (kind, label) ->
                SelloChip(
                    label = stringResource(label),
                    selected = sheet.kind == kind,
                    onClick = { onAction(RepeatAction.KindPicked(kind)) }
                )
            }
        }
        Days(sheet, onAction)
        Label(stringResource(R.string.repeat_end))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelloChip(
                label = stringResource(R.string.repeat_end_never),
                selected = sheet.end == null,
                onClick = { onAction(RepeatAction.NoEnd) }
            )
            SelloChip(
                label = sheet.end?.let { dates.day(it, today) }
                    ?: stringResource(R.string.repeat_end_on_date),
                selected = sheet.end != null,
                onClick = { onAction(RepeatAction.OpenEndPicker) },
                icon = SelloIcon.EventBusy
            )
        }
        val next = repeat.sheetNext
        RepeatNext(
            text = when {
                next == null -> stringResource(R.string.repeat_never_records)
                next == today -> stringResource(R.string.repeat_next_today)
                else -> stringResource(R.string.repeat_next, dates.day(next, today))
            },
            modifier = Modifier.padding(vertical = SelloTheme.spacing.sm)
        )
        RepeatSheetActions(
            dismissLabel = stringResource(R.string.repeat_remove),
            onDismiss = { onAction(RepeatAction.Remove) },
            confirmLabel = stringResource(R.string.repeat_done),
            onConfirm = { onAction(RepeatAction.Done) },
            modifier = Modifier.testTag(REPEAT_DONE_TAG),
            confirmEnabled = repeat.sheetComplete
        )
    }
    if (repeat.endPickerOpen) EndPicker(sheet, start, onAction)
}

@Composable
private fun Days(sheet: RepeatChoice, onAction: (RepeatAction) -> Unit) {
    when (sheet.kind) {
        RepeatKind.Monthly, RepeatKind.TwiceMonthly -> {
            Label(
                stringResource(
                    if (sheet.kind ==
                        RepeatKind.Monthly
                    ) {
                        R.string.repeat_day
                    } else {
                        R.string.repeat_two_days
                    }
                )
            )
            val dayLabel = stringResource(R.string.repeat_day_description)
            DayGrid(
                selected = sheet.days.toSet(),
                onToggle = { onAction(RepeatAction.DayToggled(it)) },
                dayDescription = { dayLabel.format(it) }
            )
            if (sheet.days.any { it > SHORTEST_MONTH }) {
                Text(
                    stringResource(R.string.repeat_short_months),
                    style = MaterialTheme.typography.bodySmall,
                    color = SelloTheme.colors.inkSoft
                )
            }
        }

        RepeatKind.Weeks, RepeatKind.Days -> {
            Label(stringResource(R.string.repeat_every))
            IntervalStepper(
                value = sheet.interval,
                unit = pluralStringResource(
                    if (sheet.kind ==
                        RepeatKind.Weeks
                    ) {
                        R.plurals.repeat_weeks_unit
                    } else {
                        R.plurals.repeat_days_unit
                    },
                    sheet.interval
                ),
                onDecrease = { onAction(RepeatAction.Decrease) },
                onIncrease = { onAction(RepeatAction.Increase) },
                decreaseLabel = stringResource(R.string.repeat_decrease),
                increaseLabel = stringResource(R.string.repeat_increase),
                canDecrease = sheet.canDecrease,
                canIncrease = sheet.canIncrease
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(top = SelloTheme.spacing.md, bottom = 6.dp)
    )
}

/** An end on or after [start]; none before it can be picked. */
@Composable
private fun EndPicker(sheet: RepeatChoice, start: LocalDate, onAction: (RepeatAction) -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = (sheet.end ?: start).millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !day(utcTimeMillis).isBefore(start)

            override fun isSelectableYear(year: Int) = year >= start.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(RepeatAction.CloseEndPicker) },
        confirmButton = {
            SelloButton(
                text = stringResource(R.string.expense_entry_date_confirm),
                onClick = {
                    val picked = picker.selectedDateMillis?.let(::day)
                    onAction(
                        if (picked ==
                            null
                        ) {
                            RepeatAction.CloseEndPicker
                        } else {
                            RepeatAction.EndPicked(picked)
                        }
                    )
                },
                modifier = Modifier.testTag(REPEAT_END_CONFIRM_TAG)
            )
        }
    ) {
        DatePicker(state = picker)
    }
}

/**
 * Asked when saving new terms on an entry a schedule recorded: Solo este, or Este y los
 * siguientes, which also changes the schedule from tomorrow, and Volver a editar.
 */
@Composable
fun FollowingChoice(
    following: FollowingSchedule,
    income: Boolean,
    busy: Boolean,
    onChoose: (alsoFollowing: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val name = following.name ?: following.sourceKey?.let { incomeSourceLabel(it) }
        ?: stringResource(
            if (income) {
                R.string.notice_income_schedule_saved_plain
            } else {
                R.string.notice_payment_schedule_saved_plain
            }
        )
    val rule = repeatRuleLabel(following.rule, following.start)
    val template = stringResource(
        if (income) R.string.following_message_income else R.string.following_message_expense,
        NAME_MARK,
        rule
    )
    val message = buildAnnotatedString {
        val at = template.indexOf(NAME_MARK)
        append(template.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SelloTheme.colors.ink)) {
            append(name)
        }
        append(template.substring(at + NAME_MARK.length))
    }
    ChoiceSlip(
        title = stringResource(R.string.following_title),
        message = message,
        firstLabel = stringResource(R.string.following_only_this),
        onFirst = { onChoose(false) },
        secondLabel = stringResource(R.string.following_also),
        onSecond = { onChoose(true) },
        backLabel = stringResource(R.string.following_back),
        onBack = onBack,
        busy = busy
    )
}

private const val SHORTEST_MONTH = 28

private const val NAME_MARK = "\u0000"

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
internal fun DayGrid(
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
internal fun IntervalStepper(
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
internal fun RepeatSheetActions(
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
internal fun RepeatNext(text: String, modifier: Modifier = Modifier) {
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
