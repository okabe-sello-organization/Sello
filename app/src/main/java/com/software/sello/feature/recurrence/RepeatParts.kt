package com.software.sello.feature.recurrence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ChoiceSlip
import com.software.sello.designsystem.component.DayGrid
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.IntervalStepper
import com.software.sello.designsystem.component.RepeatNext
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.RepeatSheetActions
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.expense.FollowingSchedule
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatKind
import com.software.sello.presentation.recurrence.RepeatUi
import com.software.sello.presentation.recurrence.repeatRuleLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

const val REPEAT_ROW_TAG = "repeat:row"
const val REPEAT_SHEET_TAG = "repeat:sheet"
const val REPEAT_DONE_TAG = "repeat:done"
const val REPEAT_END_CONFIRM_TAG = "repeat:end-confirm"

/**
 * Se repite's row under the note: "Se repite · No" while the entry is recorded once, or
 * the rule and its next date with Cambiar. [start] is the day the rule counts from.
 */
@Composable
fun RepeatRow(
    repeat: RepeatUi,
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
        val spoken = stringResource(R.string.repeat_row_off_spoken)
        EntryLine(
            text = stringResource(R.string.repeat_title),
            icon = SelloIcon.Autorenew,
            onClick = { onAction(RepeatAction.Open) },
            modifier = modifier.testTag(REPEAT_ROW_TAG).semantics { contentDescription = spoken },
            action = stringResource(R.string.repeat_off),
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
            modifier = Modifier.padding(vertical = 8.dp)
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
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
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
