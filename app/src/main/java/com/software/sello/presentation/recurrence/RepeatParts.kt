package com.software.sello.presentation.recurrence

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.FollowingChoice as FollowingChoicePart
import com.software.sello.designsystem.component.RepeatChoiceSheet as RepeatChoiceSheetPart
import com.software.sello.designsystem.component.RepeatRow as RepeatRowPart
import com.software.sello.designsystem.component.RepeatSheetLabels
import com.software.sello.designsystem.component.RepeatSpacing
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.movement.MovementDatePicker
import java.time.LocalDate

/*
 * The repeat parts an expense, an income and a schedule share, given the app's copy and
 * rules. What they look like is the design system's (component/RepeatParts.kt); this only
 * turns a [RepeatUi] into words, so the screens keep calling them as before.
 */

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
        RepeatRowPart(
            text = stringResource(
                R.string.repeat_row,
                label.replaceFirstChar {
                    it.uppercase()
                },
                day
            ),
            spoken = stringResource(R.string.repeat_row_spoken, label, day),
            enabled = enabled,
            onClick = { onAction(RepeatAction.Open) },
            modifier = modifier,
            action = stringResource(R.string.repeat_change)
        )
    } else {
        val text = stringResource(
            if (income) R.string.repeat_add_income else R.string.repeat_add_expense
        )
        RepeatRowPart(
            text = text,
            spoken = stringResource(R.string.repeat_row_off_spoken, text),
            enabled = enabled,
            onClick = { onAction(RepeatAction.Open) },
            modifier = modifier
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
    val dayLabel = stringResource(R.string.repeat_day_description)
    val next = repeat.sheetNext
    RepeatChoiceSheetPart(
        labels = RepeatSheetLabels(
            title = stringResource(R.string.repeat_title),
            kinds = KINDS.map { (_, label) -> stringResource(label) },
            end = stringResource(R.string.repeat_end),
            endNever = stringResource(R.string.repeat_end_never),
            decrease = stringResource(R.string.repeat_decrease),
            increase = stringResource(R.string.repeat_increase),
            remove = stringResource(R.string.repeat_remove),
            done = stringResource(R.string.repeat_done)
        ),
        kind = KINDS.indexOfFirst { (kind, _) -> kind == sheet.kind },
        spacing = spacing(sheet),
        endDate = sheet.end?.let { dates.day(it, today) }
            ?: stringResource(R.string.repeat_end_on_date),
        ended = sheet.end != null,
        next = when {
            next == null -> stringResource(R.string.repeat_never_records)
            next == today -> stringResource(R.string.repeat_next_today)
            else -> stringResource(R.string.repeat_next, dates.day(next, today))
        },
        doneEnabled = repeat.sheetComplete,
        dayDescription = { dayLabel.format(it) },
        onKind = { onAction(RepeatAction.KindPicked(KINDS[it].first)) },
        onDayToggled = { onAction(RepeatAction.DayToggled(it)) },
        onDecrease = { onAction(RepeatAction.Decrease) },
        onIncrease = { onAction(RepeatAction.Increase) },
        onNoEnd = { onAction(RepeatAction.NoEnd) },
        onPickEnd = { onAction(RepeatAction.OpenEndPicker) },
        onRemove = { onAction(RepeatAction.Remove) },
        onDone = { onAction(RepeatAction.Done) },
        onDismiss = { onAction(RepeatAction.Dismiss) }
    )
    if (repeat.endPickerOpen) {
        // An end on or after [start]; none before it can be picked.
        MovementDatePicker(
            sheet.end ?: start,
            onPicked = { onAction(RepeatAction.EndPicked(it)) },
            onDismiss = { onAction(RepeatAction.CloseEndPicker) },
            first = start
        )
    }
}

@Composable
private fun spacing(sheet: RepeatChoice): RepeatSpacing = when (sheet.kind) {
    RepeatKind.Monthly, RepeatKind.TwiceMonthly -> RepeatSpacing.MonthDays(
        label = stringResource(
            if (sheet.kind == RepeatKind.Monthly) R.string.repeat_day else R.string.repeat_two_days
        ),
        chosen = sheet.days.toSet(),
        note = if (sheet.days.any { it > SHORTEST_MONTH }) {
            stringResource(R.string.repeat_short_months)
        } else {
            null
        }
    )

    RepeatKind.Weeks, RepeatKind.Days -> RepeatSpacing.Interval(
        label = stringResource(R.string.repeat_every),
        value = sheet.interval,
        unit = pluralStringResource(
            if (sheet.kind == RepeatKind.Weeks) {
                R.plurals.repeat_weeks_unit
            } else {
                R.plurals.repeat_days_unit
            },
            sheet.interval
        ),
        canDecrease = sheet.canDecrease,
        canIncrease = sheet.canIncrease
    )
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
    val at = template.indexOf(NAME_MARK)
    FollowingChoicePart(
        title = stringResource(R.string.following_title),
        lead = template.substring(0, at),
        name = name,
        rest = template.substring(at + NAME_MARK.length),
        onlyThisLabel = stringResource(R.string.following_only_this),
        alsoLabel = stringResource(R.string.following_also),
        backLabel = stringResource(R.string.following_back),
        busy = busy,
        onChoose = onChoose,
        onBack = onBack
    )
}

/** The kinds in the order the sheet offers them, with their chip's words. */
private val KINDS = listOf(
    RepeatKind.Monthly to R.string.repeat_kind_monthly,
    RepeatKind.TwiceMonthly to R.string.repeat_kind_twice_monthly,
    RepeatKind.Weeks to R.string.repeat_kind_weeks,
    RepeatKind.Days to R.string.repeat_kind_days
)

private const val SHORTEST_MONTH = 28

private const val NAME_MARK = "\u0000"
