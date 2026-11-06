package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.EntryHeading
import com.software.sello.designsystem.component.ScheduleRow
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.recurrence.repeatRuleLabel

/**
 * Pagos fijos (docs/design/system/features/category.html#screen-fixed): the category's payments
 * still to come this month, then its other payments that repeat, each opening its schedule.
 */
@Composable
internal fun CategoryFixedPayments(
    state: CategoryDetailState.Ready,
    months: MonthNames,
    dates: DateLabels,
    onAction: (CategoryDetailAction) -> Unit,
    onOpenSchedule: (String) -> Unit,
    onAllSchedules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val month = months.inSentence(state.month)
    when (val fixed = state.fixed) {
        DetailFixedState.Hidden -> Unit

        DetailFixedState.Loading -> SlipSkeleton(
            modifier.testTag(CATEGORY_DETAIL_FIXED_LOADING_TAG)
        )

        DetailFixedState.Failed -> StatusSlip(
            kind = StatusKind.LoadFailed(stringResource(R.string.schedules_failed_title)),
            message = stringResource(R.string.category_detail_fixed_failed, month),
            modifier = modifier.testTag(CATEGORY_DETAIL_FIXED_FAILED_TAG),
            action = StatusAction(StatusSlipDefaults.retryLabel) {
                onAction(CategoryDetailAction.Retry)
            }
        )

        is DetailFixedState.Ready -> Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
        ) {
            // Above the slip it speaks of, as every other older list says it.
            if (fixed.stale) {
                StatusSlip(
                    kind = StatusKind.Older,
                    message = stringResource(R.string.category_detail_fixed_stale),
                    modifier = Modifier.testTag(CATEGORY_DETAIL_FIXED_STALE_TAG),
                    action = StatusAction(StatusSlipDefaults.retryLabel) {
                        onAction(CategoryDetailAction.Retry)
                    }
                )
            }
            FixedPaymentsSlip(fixed, state, month, dates, onOpenSchedule, onAllSchedules)
        }
    }
}

@Composable
private fun FixedPaymentsSlip(
    fixed: DetailFixedState.Ready,
    state: CategoryDetailState.Ready,
    month: String,
    dates: DateLabels,
    onOpenSchedule: (String) -> Unit,
    onAllSchedules: () -> Unit
) {
    Slip(Modifier.fillMaxWidth().testTag(CATEGORY_DETAIL_FIXED_TAG)) {
        Text(
            stringResource(R.string.category_detail_fixed_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Group(stringResource(R.string.category_detail_fixed_coming, month), first = true)
        if (fixed.upcoming.isEmpty()) {
            Text(
                stringResource(R.string.category_detail_fixed_none_left, month),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.testTag(CATEGORY_DETAIL_FIXED_NONE_LEFT_TAG)
            )
        }
        fixed.upcoming.forEachIndexed { index, payment ->
            val day = dates.day(payment.date, state.today)
            // The day already says "Hoy" for today; tomorrow says so too.
            val caption = if (payment.date == state.today.plusDays(1)) {
                stringResource(R.string.schedules_due_tomorrow, day)
            } else {
                day
            }
            ScheduleRow(
                name = payment.name,
                caption = caption,
                amount = payment.amount,
                icon = SelloIcon.Event,
                onClick = { onOpenSchedule(payment.scheduleId) },
                spoken = stringResource(
                    R.string.upcoming_row_spoken,
                    payment.name,
                    payment.amount.spoken,
                    caption
                ),
                modifier = Modifier.testTag(
                    "$CATEGORY_DETAIL_FIXED_TAG:coming:${payment.scheduleId}.${payment.date}"
                ),
                last = index == fixed.upcoming.lastIndex
            )
        }
        if (fixed.later.isNotEmpty()) {
            Group(stringResource(R.string.category_detail_fixed_later), first = false)
        }
        fixed.later.forEachIndexed { index, schedule ->
            val rule = repeatRuleLabel(schedule.rule, schedule.start)
            val status = schedule.pausedFrom?.let {
                stringResource(R.string.schedules_paused_since, dates.day(it, state.today))
            } ?: schedule.next?.let {
                stringResource(R.string.schedules_next, dates.day(it, state.today))
            }
            val caption = listOfNotNull(rule, status).joinToString(" · ")
            ScheduleRow(
                name = schedule.name,
                caption = caption,
                amount = schedule.amount,
                icon = if (schedule.pausedFrom !=
                    null
                ) {
                    SelloIcon.Pause
                } else {
                    SelloIcon.Autorenew
                },
                onClick = { onOpenSchedule(schedule.id) },
                spoken = stringResource(
                    R.string.upcoming_row_spoken,
                    schedule.name,
                    schedule.amount.spoken,
                    caption
                ),
                modifier = Modifier.testTag("$CATEGORY_DETAIL_FIXED_TAG:later:${schedule.id}"),
                dimmed = schedule.pausedFrom != null,
                last = index == fixed.later.lastIndex
            )
        }
        SelloButton(
            text = stringResource(R.string.upcoming_all_schedules),
            onClick = onAllSchedules,
            kind = ButtonKind.Text,
            // Starts at the slip's content edge, under the rows (design .text-link).
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.testTag(CATEGORY_DETAIL_FIXED_ALL_TAG)
        )
    }
}

@Composable
private fun Group(text: String, first: Boolean) {
    EntryHeading(text, Modifier.padding(top = if (first) 10.dp else 14.dp, bottom = 2.dp))
}

const val CATEGORY_DETAIL_FIXED_TAG = "detail:fixed"
const val CATEGORY_DETAIL_FIXED_LOADING_TAG = "detail:fixed-loading"
const val CATEGORY_DETAIL_FIXED_FAILED_TAG = "detail:fixed-failed"
const val CATEGORY_DETAIL_FIXED_NONE_LEFT_TAG = "detail:fixed-none-left"
const val CATEGORY_DETAIL_FIXED_ALL_TAG = "detail:fixed-all"
const val CATEGORY_DETAIL_FIXED_STALE_TAG = "detail:fixed-stale"
