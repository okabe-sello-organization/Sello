package com.software.sello.feature.backup

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonPair
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.RestoreBody
import com.software.sello.designsystem.component.RestoreHeading
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StagedRestore
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.restore.LegacyReportSection
import com.software.sello.presentation.restore.RestoredSlip
import com.software.sello.presentation.restore.message

/**
 * Restoring from a file: choose, check, preview, confirm, then the outcome. Nothing is
 * replaced before the preview is confirmed; a failure says the data did not change.
 */
@Composable
fun RestoreSection(state: RestoreState, onAction: (RestoreAction) -> Unit) {
    when (val step = state.step) {
        RestoreStep.Idle, RestoreStep.Checking -> Slip(
            Modifier.fillMaxWidth().testTag(RESTORE_TAG),
            pinked = false
        ) {
            RestoreHeading(stringResource(R.string.restore_title))
            RestoreBody(stringResource(R.string.restore_body))
            SelloButton(
                text = stringResource(
                    if (step ==
                        RestoreStep.Checking
                    ) {
                        R.string.restore_checking
                    } else {
                        R.string.restore_choose
                    }
                ),
                onClick = { onAction(RestoreAction.Choose) },
                kind = ButtonKind.Outlined,
                loading = step == RestoreStep.Checking,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
                    .testTag(RESTORE_CHOOSE_TAG)
            )
        }

        is RestoreStep.Rejected -> StatusSlip(
            kind = StatusKind.ActionFailed(stringResource(R.string.restore_rejected_title)),
            message = stringResource(step.reason.message()),
            modifier = Modifier.testTag(RESTORE_REJECTED_TAG),
            action = StatusAction(stringResource(R.string.restore_choose_other)) {
                onAction(RestoreAction.Choose)
            }
        )

        RestoreStep.Failed -> StatusSlip(
            kind = StatusKind.ActionFailed(stringResource(R.string.restore_failed_title)),
            message = stringResource(R.string.restore_failed_body),
            modifier = Modifier.testTag(RESTORE_FAILED_TAG),
            action = StatusAction(stringResource(R.string.restore_failed_action)) {
                onAction(RestoreAction.Dismiss)
            }
        )

        is RestoreStep.Previewing -> Preview(state, step.staged, step.note(), false, onAction)

        is RestoreStep.Replacing -> Preview(state, step.staged, null, true, onAction)

        is RestoreStep.Unknown -> StatusSlip(
            kind = StatusKind.Unknown(stringResource(R.string.restore_unknown_title)),
            message = listOfNotNull(
                stringResource(R.string.restore_unknown_body),
                chosenSetAside(step.chosenSetAside)
            ).joinToString("\n\n"),
            modifier = Modifier.testTag(RESTORE_UNKNOWN_TAG),
            action = StatusAction(
                stringResource(
                    if (step.checking) {
                        R.string.restore_unknown_checking
                    } else {
                        R.string.restore_unknown_action
                    }
                ),
                busy = step.checking
            ) { onAction(RestoreAction.Check) }
        )

        is RestoreStep.Restored -> RestoredSlip(
            step.outcome,
            step.applying,
            note = chosenSetAside(step.chosenSetAside),
            onApply = { onAction(RestoreAction.ApplyPreferences) },
            onLeave = { onAction(RestoreAction.Leave) }
        )
    }
}

/** That a file chosen meanwhile was not checked, if it was set aside. */
@Composable
private fun chosenSetAside(setAside: Boolean): String? =
    if (setAside) stringResource(R.string.restore_chosen_set_aside) else null

/** Why a preview is shown again, if it is. */
private fun RestoreStep.Previewing.note(): Int? = when {
    notReplaced -> R.string.restore_preview_not_replaced
    refreshed -> R.string.restore_preview_refreshed
    else -> null
}

@Composable
private fun Preview(
    state: RestoreState,
    staged: StagedRestore,
    note: Int?,
    replacing: Boolean,
    onAction: (RestoreAction) -> Unit
) {
    Slip(
        Modifier.fillMaxWidth().testTag(RESTORE_PREVIEW_TAG),
        pinked = false
    ) {
        RestoreHeading(stringResource(R.string.restore_preview_title))
        state.zone?.let { zone ->
            RestoreBody(
                stringResource(
                    R.string.restore_preview_date,
                    dateLabels().moment(
                        staged.backupAt,
                        zone,
                        stringResource(R.string.backup_moment)
                    )
                )
            )
        }
        note?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                // Figures that changed are a doubt to check; a restore that did not happen is a problem.
                color = if (it == R.string.restore_preview_refreshed) {
                    SelloTheme.colors.semantic.warn
                } else {
                    SelloTheme.colors.semantic.loss
                },
                modifier = Modifier.padding(top = SelloTheme.spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Counts(staged.current, staged.replacement)
        staged.legacy?.let { LegacyReportSection(it) }
        RestoreBody(stringResource(R.string.restore_preview_warning))
        ButtonPair(
            firstWeight = 1f,
            secondWeight = 2f,
            gap = SelloTheme.spacing.md,
            modifier = Modifier.padding(top = SelloTheme.spacing.md),
            first = {
                SelloButton(
                    text = stringResource(R.string.restore_cancel),
                    onClick = { onAction(RestoreAction.Cancel) },
                    kind = ButtonKind.Outlined,
                    enabled = !replacing,
                    modifier = Modifier.testTag(RESTORE_CANCEL_TAG)
                )
            },
            second = {
                SelloButton(
                    text = stringResource(
                        if (replacing) R.string.restore_replacing else R.string.restore_confirm
                    ),
                    onClick = { onAction(RestoreAction.Confirm) },
                    tone = ButtonTone.Loss,
                    loading = replacing,
                    enabled = !replacing,
                    modifier = Modifier.testTag(RESTORE_CONFIRM_TAG)
                )
            }
        )
    }
}

/**
 * Now and backup, side by side: categories, expenses, incomes, and goals, recurring
 * payments and incomes, investments and carry chains when either has any.
 */
@Composable
internal fun Counts(current: BackupSummary, replacement: BackupSummary) {
    val now = stringResource(R.string.restore_preview_now)
    val backup = stringResource(R.string.restore_preview_backup)
    Row(Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)) {
        Spacer(Modifier.weight(1f))
        Cell(now, header = true)
        Cell(backup, header = true)
    }
    listOf(
        Triple(R.string.backup_categories, current.categories, replacement.categories),
        Triple(R.string.backup_expenses, current.expenses, replacement.expenses),
        Triple(R.string.backup_incomes, current.incomes, replacement.incomes),
        Triple(R.string.backup_goals, current.goals, replacement.goals)
            .takeIf { current.goals > 0 || replacement.goals > 0 },
        Triple(R.string.backup_schedules, current.schedules, replacement.schedules)
            .takeIf { current.schedules > 0 || replacement.schedules > 0 },
        Triple(R.string.backup_investments, current.investments, replacement.investments)
            .takeIf { current.investments > 0 || replacement.investments > 0 },
        Triple(R.string.backup_carry_chains, current.carryChains, replacement.carryChains)
            .takeIf { current.carryChains > 0 || replacement.carryChains > 0 }
    ).filterNotNull().forEach { (label, before, after) ->
        val name = stringResource(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Cell(countText(before), spoken = "$now ${countText(before)}")
            Cell(countText(after), spoken = "$backup ${countText(after)}")
        }
    }
}

@Composable
private fun Cell(text: String, header: Boolean = false, spoken: String? = null) {
    Text(
        text = text,
        style = with(MaterialTheme.typography) { if (header) labelMedium else bodyLarge },
        fontWeight = if (header) null else SelloTheme.type.weight.strong,
        color = if (header) SelloTheme.colors.inkSoft else SelloTheme.colors.ink,
        textAlign = TextAlign.End,
        modifier = Modifier.width(88.dp).semantics {
            if (spoken != null) contentDescription = spoken
        }
    )
}

const val RESTORE_TAG = "restore"
const val RESTORE_CHOOSE_TAG = "restore:choose"
const val RESTORE_REJECTED_TAG = "restore:rejected"
const val RESTORE_FAILED_TAG = "restore:failed"
const val RESTORE_PREVIEW_TAG = "restore:preview"
const val RESTORE_CONFIRM_TAG = "restore:confirm"
const val RESTORE_CANCEL_TAG = "restore:cancel"
const val RESTORE_UNKNOWN_TAG = "restore:unknown"
