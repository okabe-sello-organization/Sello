package com.software.sello.presentation.restore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StagedRestore
import com.software.sello.presentation.appearance.choice
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.investments.label
import com.software.sello.presentation.investments.newInvestmentLabel

/**
 * A replaced history: Restaurado when every preference was applied too, otherwise D09's
 * slip naming only the preferences still pending. Also shown at start-up. [note], when
 * given, is one more line before the buttons.
 */
@Composable
internal fun RestoredSlip(
    outcome: RestoreOutcome.Restored,
    applying: Boolean,
    onApply: () -> Unit,
    onLeave: () -> Unit,
    note: String? = null
) {
    if (outcome.preferences.done) {
        Restored(outcome.summary, note, onLeave)
    } else {
        Pending(outcome.preferences, applying, note, onApply, onLeave)
    }
}

@Composable
private fun Restored(summary: BackupSummary, note: String?, onLeave: () -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(RESTORE_DONE_TAG), pinked = false) {
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RestoreHeading(stringResource(R.string.restore_done_title))
            Stamp(
                text = stringResource(R.string.restore_done_stamp),
                ink = StampInk.Good,
                land = true,
                modifier = Modifier.padding(vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        RestoreBody(
            stringResource(
                R.string.restore_done_body,
                contentsText(
                    summary,
                    withGoals = summary.goals > 0,
                    withSchedules = summary.schedules > 0
                )
            )
        )
        note?.let { RestoreBody(it) }
        GoToRecibo(onLeave)
    }
}

/** D09's approved wording: the history is restored; only these preferences are not. */
@Composable
private fun Pending(
    preferences: PendingPreferences,
    applying: Boolean,
    note: String?,
    onApply: () -> Unit,
    onLeave: () -> Unit
) {
    Slip(Modifier.fillMaxWidth().testTag(RESTORE_PENDING_TAG), pinked = false) {
        RestoreHeading(stringResource(R.string.restore_pending_title))
        RestoreBody(stringResource(R.string.restore_pending_body))
        BulletList(
            listOfNotNull(
                preferences.appearance?.let {
                    Bullet(
                        null,
                        stringResource(
                            R.string.restore_pending_appearance,
                            stringResource(it.theme.label()),
                            stringResource(it.ink.choice.label)
                        )
                    )
                },
                preferences.ordering?.let {
                    Bullet(
                        null,
                        stringResource(
                            R.string.restore_pending_ordering,
                            stringResource(it.order.label()),
                            stringResource(it.direction.label())
                        )
                    )
                },
                preferences.investments?.let {
                    Bullet(
                        null,
                        stringResource(
                            R.string.restore_pending_investments,
                            stringResource(it.newCurrency.newInvestmentLabel()),
                            stringResource(it.order.label())
                        )
                    )
                }
            ),
            Modifier.padding(top = SelloTheme.spacing.sm)
        )
        note?.let { RestoreBody(it) }
        SelloButton(
            text = stringResource(
                applyLabel(preferences)
            ),
            onClick = onApply,
            loading = applying,
            enabled = !applying,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
                .testTag(RESTORE_APPLY_TAG)
        )
        Text(
            text = stringResource(R.string.restore_pending_note),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
        GoToRecibo(onLeave, kind = ButtonKind.Text)
    }
}

@Composable
private fun GoToRecibo(onLeave: () -> Unit, kind: ButtonKind = ButtonKind.Filled) {
    SelloButton(
        text = stringResource(R.string.restore_go_to_recibo),
        onClick = onLeave,
        kind = kind,
        modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
            .testTag(RESTORE_LEAVE_TAG)
    )
}

@Composable
internal fun RestoreHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
internal fun RestoreBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(top = SelloTheme.spacing.xs)
    )
}

internal fun RestoreRejection.message() = when (this) {
    RestoreRejection.Unreadable -> R.string.restore_rejected_unreadable
    RestoreRejection.TooLarge -> R.string.restore_rejected_too_large
    RestoreRejection.NotABackup -> R.string.restore_rejected_not_backup
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
const val RESTORE_APPLY_TAG = "restore:apply"
const val RESTORE_LEAVE_TAG = "restore:leave"

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
