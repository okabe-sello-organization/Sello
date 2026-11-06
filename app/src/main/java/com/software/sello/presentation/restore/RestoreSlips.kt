package com.software.sello.presentation.restore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.RestorePending
import com.software.sello.designsystem.component.RestoredSlip as RestoredSlipPart
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.presentation.appearance.label
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.investments.label
import com.software.sello.presentation.investments.newInvestmentLabel

/**
 * A replaced history: Restaurado when every preference was applied too, otherwise D09's
 * slip naming only the preferences still pending, or saying the backup's preferences could
 * not be read. Also shown at start-up. [note], when given, is one more line before the
 * buttons.
 */
@Composable
internal fun RestoredSlip(
    outcome: RestoreOutcome.Restored,
    applying: Boolean,
    onApply: () -> Unit,
    onLeave: () -> Unit,
    note: String? = null
) {
    val leave = stringResource(R.string.restore_go_to_recibo)
    val preferences = outcome.preferences
    if (outcome.preferencesUnreadable) {
        // D09's heading: the history is restored; the backup's preferences could not be read.
        RestoredSlipPart(
            title = stringResource(R.string.restore_pending_title),
            body = stringResource(R.string.restore_preferences_unreadable),
            leaveLabel = leave,
            onLeave = onLeave,
            modifier = Modifier.testTag(RESTORE_PREFERENCES_UNREADABLE_TAG),
            note = note
        )
    } else if (preferences.done) {
        val summary = outcome.summary
        RestoredSlipPart(
            title = stringResource(R.string.restore_done_title),
            body = stringResource(
                R.string.restore_done_body,
                contentsText(
                    summary,
                    withGoals = summary.goals > 0,
                    withSchedules = summary.schedules > 0
                )
            ),
            leaveLabel = leave,
            onLeave = onLeave,
            modifier = Modifier.testTag(RESTORE_DONE_TAG),
            stamp = stringResource(R.string.restore_done_stamp),
            note = note
        )
    } else {
        // D09's approved wording: the history is restored; only these preferences are not.
        RestoredSlipPart(
            title = stringResource(R.string.restore_pending_title),
            body = stringResource(R.string.restore_pending_body),
            leaveLabel = leave,
            onLeave = onLeave,
            modifier = Modifier.testTag(RESTORE_PENDING_TAG),
            note = note,
            pending = RestorePending(
                items = pendingItems(preferences),
                label = stringResource(applyLabel(preferences)),
                note = stringResource(R.string.restore_pending_note),
                applying = applying
            ),
            onApply = onApply
        )
    }
}

@Composable
private fun pendingItems(preferences: PendingPreferences): List<String> = listOfNotNull(
    preferences.appearance?.let {
        stringResource(
            R.string.restore_pending_appearance,
            stringResource(it.theme.label()),
            it.ink.label()
        )
    },
    preferences.ordering?.let {
        stringResource(
            R.string.restore_pending_ordering,
            stringResource(it.order.label()),
            stringResource(it.direction.label())
        )
    },
    preferences.investments?.let {
        stringResource(
            R.string.restore_pending_investments,
            stringResource(it.newCurrency.newInvestmentLabel()),
            stringResource(it.order.label())
        )
    }
)

internal fun RestoreRejection.message() = when (this) {
    RestoreRejection.Unreadable -> R.string.restore_rejected_unreadable
    RestoreRejection.TooLarge -> R.string.restore_rejected_too_large
    RestoreRejection.NotABackup -> R.string.restore_rejected_not_backup
    RestoreRejection.FinanceTrackerFile -> R.string.restore_rejected_finance_tracker
    RestoreRejection.FinanceTrackerNewer -> R.string.restore_rejected_finance_tracker_newer
    RestoreRejection.NewerVersion -> R.string.restore_rejected_newer
    RestoreRejection.Damaged -> R.string.restore_rejected_damaged
    RestoreRejection.InvalidContent -> R.string.restore_rejected_content
}

/** One group by its name; appearance and order together as D09 words them; more, as a whole. */
private fun applyLabel(preferences: PendingPreferences): Int {
    val appearance = preferences.appearance != null
    val ordering = preferences.ordering != null
    val investments = preferences.investments != null
    return when {
        appearance && ordering && !investments -> R.string.restore_apply_both
        listOf(appearance, ordering, investments).count { it } > 1 -> R.string.restore_apply_all
        appearance -> R.string.restore_apply_appearance
        ordering -> R.string.restore_apply_ordering
        else -> R.string.restore_apply_investments
    }
}

private fun ThemePreference.label() = when (this) {
    ThemePreference.System -> R.string.settings_theme_system
    ThemePreference.Light -> R.string.settings_theme_light
    ThemePreference.Dark -> R.string.settings_theme_dark
}

private fun CategoryOrder.label() = when (this) {
    CategoryOrder.MostUsed -> R.string.settings_order_most_used
    CategoryOrder.Alphabetical -> R.string.settings_order_alphabetical
    CategoryOrder.AmountSpent -> R.string.settings_order_amount_spent
    CategoryOrder.Limit -> R.string.settings_order_limit
    CategoryOrder.LatestExpenseAdded -> R.string.settings_order_latest_expense_added
}

private fun CategoryOrderDirection.label() = when (this) {
    CategoryOrderDirection.Descending -> R.string.restore_pending_descending
    CategoryOrderDirection.Ascending -> R.string.restore_pending_ascending
}

const val RESTORE_DONE_TAG = "restore:done"
const val RESTORE_PENDING_TAG = "restore:pending"
const val RESTORE_PREFERENCES_UNREADABLE_TAG = "restore:preferences-unreadable"

/**
 * What a backup or the phone holds, in words: "2 categorías, 3 gastos y 1 ingreso",
 * with "y 3 metas" at the end only when [withGoals], so nothing changes for a history
 * without goals; likewise schedules, investments and carry chains.
 */
@Composable
fun contentsText(
    summary: BackupSummary,
    withGoals: Boolean,
    withSchedules: Boolean,
    withInvestments: Boolean = summary.investments > 0,
    withCarryChains: Boolean = summary.carryChains > 0
): String {
    val items = buildList {
        add(
            pluralStringResource(
                R.plurals.restore_categories,
                summary.categories,
                countText(summary.categories)
            )
        )
        add(
            pluralStringResource(
                R.plurals.restore_expenses,
                summary.expenses,
                countText(summary.expenses)
            )
        )
        add(
            pluralStringResource(
                R.plurals.restore_incomes,
                summary.incomes,
                countText(summary.incomes)
            )
        )
        if (withGoals) {
            add(
                pluralStringResource(
                    R.plurals.restore_goals,
                    summary.goals,
                    countText(summary.goals)
                )
            )
        }
        if (withSchedules) {
            add(
                pluralStringResource(
                    R.plurals.restore_schedules,
                    summary.schedules,
                    countText(summary.schedules)
                )
            )
        }
        if (withInvestments) {
            add(
                pluralStringResource(
                    R.plurals.restore_investments,
                    summary.investments,
                    countText(summary.investments)
                )
            )
        }
        if (withCarryChains) {
            add(
                pluralStringResource(
                    R.plurals.restore_carry_chains,
                    summary.carryChains,
                    countText(summary.carryChains)
                )
            )
        }
    }
    var joined = stringResource(R.string.restore_list_two, items[items.size - 2], items.last())
    for (item in items.dropLast(2).reversed()) {
        joined = stringResource(R.string.restore_list_more, item, joined)
    }
    return joined
}
