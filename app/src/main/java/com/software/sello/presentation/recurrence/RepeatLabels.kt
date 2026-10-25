package com.software.sello.presentation.recurrence

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.domain.model.RepeatRule
import java.time.LocalDate

/**
 * How a rule is written, from [start] for the weekday of every N weeks: "el 5 de cada mes",
 * "el 15 y el 30 de cada mes", "cada 2 semanas, los sábados", "cada 10 días".
 */
@Composable
fun repeatRuleLabel(rule: RepeatRule, start: LocalDate): String = when (rule) {
    is RepeatRule.Monthly -> stringResource(R.string.repeat_monthly, rule.day)

    is RepeatRule.TwiceMonthly ->
        stringResource(R.string.repeat_twice_monthly, rule.first, rule.second)

    is RepeatRule.EveryWeeks -> pluralStringResource(
        R.plurals.repeat_weeks,
        rule.weeks,
        rule.weeks,
        stringArrayResource(R.array.weekday_names_plural)[start.dayOfWeek.value - 1]
    )

    is RepeatRule.EveryDays -> pluralStringResource(R.plurals.repeat_days, rule.days, rule.days)
}
