package com.software.sello.feature.reset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.port.ResetPreview
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.restore.RestoreBody
import com.software.sello.presentation.restore.RestoreHeading
import com.software.sello.presentation.restore.contentsText

/**
 * Owns the view model and leaving the screen: [onClose] when nothing was erased.
 * Once the history is or may be erased, Back starts the app again instead, and while
 * the counts are read or the erase runs it waits.
 */
@Composable
fun ResetRoot(
    viewModels: ViewModelProvider.Factory,
    contentPadding: PaddingValues,
    onClose: () -> Unit
) {
    val model: ResetViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler { model.back(onClose) }
    ResetScreen(state, model::onAction, contentPadding, onClose)
}

/**
 * Back, from the system or the top bar: restarts once the history is or may be erased,
 * waits while it is being erased, closes the second confirmation, or else [close]s.
 */
fun ResetViewModel.back(close: () -> Unit) {
    val now = state.value
    when {
        now.erased -> onAction(ResetAction.Leave)
        now.step is ResetStep.Erasing -> Unit
        now.confirming -> onAction(ResetAction.Cancel)
        else -> close()
    }
}

@Composable
fun ResetScreen(
    state: ResetState,
    onAction: (ResetAction) -> Unit,
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .testTag(RESET_TAG)
    ) {
        when (val step = state.step) {
            ResetStep.Idle, ResetStep.Counting -> SlipSkeleton(Modifier.testTag(RESET_LOADING_TAG))

            ResetStep.Unreadable -> ErrorSlip(
                title = stringResource(R.string.reset_unreadable_title),
                message = stringResource(R.string.reset_unreadable_body),
                retryLabel = stringResource(R.string.reset_retry),
                onRetry = { onAction(ResetAction.Retry) },
                modifier = Modifier.testTag(RESET_UNREADABLE_TAG)
            )

            is ResetStep.Ready -> if (state.confirming) {
                Word(state, erasing = false, onAction)
            } else {
                Counts(step.preview, step.refreshed, onAction, onClose)
            }

            is ResetStep.Erasing -> Word(state, erasing = true, onAction)

            ResetStep.Failed -> ErrorSlip(
                title = stringResource(R.string.reset_failed_title),
                message = stringResource(R.string.reset_failed_body),
                retryLabel = stringResource(R.string.reset_failed_action),
                onRetry = { onAction(ResetAction.Retry) },
                modifier = Modifier.testTag(RESET_FAILED_TAG)
            )

            is ResetStep.Unknown -> ErrorSlip(
                title = stringResource(R.string.reset_unknown_title),
                message = stringResource(R.string.reset_unknown_body),
                retryLabel = stringResource(R.string.reset_unknown_action),
                onRetry = { onAction(ResetAction.Check) },
                modifier = Modifier.testTag(RESET_UNKNOWN_TAG)
            )

            is ResetStep.Erased -> if (step.preferencesPending) {
                Pending(step.applying, onAction)
            } else {
                Erased(onAction)
            }
        }
    }
}

/** The first confirmation: what goes, what comes back, and that backup files stay. */
@Composable
private fun Counts(
    preview: ResetPreview,
    refreshed: Boolean,
    onAction: (ResetAction) -> Unit,
    onClose: () -> Unit
) {
    val current = preview.current
    Slip(Modifier.fillMaxWidth().testTag(RESET_COUNTS_TAG), pinked = false) {
        if (refreshed) {
            Text(
                text = stringResource(R.string.reset_refreshed),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.semantic.loss,
                modifier = Modifier.padding(bottom = SelloTheme.spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        RestoreHeading(stringResource(R.string.reset_counts_title))
        RestoreBody(
            stringResource(
                R.string.reset_counts_body,
                contentsText(
                    current,
                    withGoals = current.goals > 0,
                    withSchedules = current.schedules > 0
                )
            )
        )
        RestoreBody(stringResource(R.string.reset_backups_stay))
        SelloButton(
            text = stringResource(R.string.reset_continue),
            onClick = { onAction(ResetAction.Continue) },
            tone = ButtonTone.Loss,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
                .testTag(RESET_CONTINUE_TAG)
        )
        SelloButton(
            text = stringResource(R.string.reset_cancel),
            onClick = onClose,
            kind = ButtonKind.Outlined,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.sm)
                .testTag(RESET_CLOSE_TAG)
        )
    }
}

/** The second confirmation: the exact word, then the only button that erases. */
@Composable
private fun Word(state: ResetState, erasing: Boolean, onAction: (ResetAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(RESET_WORD_SLIP_TAG), pinked = false) {
        RestoreHeading(stringResource(R.string.reset_word_title))
        RestoreBody(stringResource(R.string.reset_word_body))
        FormField(
            label = stringResource(R.string.reset_word_label),
            value = state.typed,
            onValueChange = { onAction(ResetAction.Typed(it)) },
            modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag(RESET_WORD_TAG),
            enabled = !erasing,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            )
        )
        SelloButton(
            text = stringResource(if (erasing) R.string.reset_erasing else R.string.reset_confirm),
            onClick = { onAction(ResetAction.Confirm) },
            tone = ButtonTone.Loss,
            loading = erasing,
            enabled = !erasing && state.canConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
                .testTag(RESET_CONFIRM_TAG)
        )
        SelloButton(
            text = stringResource(R.string.reset_cancel),
            onClick = { onAction(ResetAction.Cancel) },
            kind = ButtonKind.Outlined,
            enabled = !erasing,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.sm)
                .testTag(RESET_CANCEL_TAG)
        )
    }
}

@Composable
private fun Erased(onAction: (ResetAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(RESET_DONE_TAG), pinked = false) {
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            RestoreHeading(stringResource(R.string.reset_done_title))
            Stamp(
                text = stringResource(R.string.reset_done_stamp),
                ink = StampInk.Good,
                land = true,
                modifier = Modifier.padding(vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        RestoreBody(stringResource(R.string.reset_done_body))
        StartAgain(onAction)
    }
}

/**
 * The history is gone; the preferences are not back to first-run. They are reset in
 * one write, so both are named, with what they will be, and retrying erases nothing.
 */
@Composable
private fun Pending(applying: Boolean, onAction: (ResetAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(RESET_PENDING_TAG), pinked = false) {
        RestoreHeading(stringResource(R.string.reset_pending_title))
        RestoreBody(stringResource(R.string.reset_pending_body))
        BulletList(
            listOf(
                Bullet(null, stringResource(R.string.reset_pending_appearance)),
                Bullet(null, stringResource(R.string.reset_pending_ordering))
            ),
            Modifier.padding(top = SelloTheme.spacing.sm)
        )
        SelloButton(
            text = stringResource(R.string.reset_pending_action),
            onClick = { onAction(ResetAction.ApplyPreferences) },
            loading = applying,
            enabled = !applying,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
                .testTag(RESET_APPLY_TAG)
        )
        Text(
            text = stringResource(R.string.reset_pending_note),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
        StartAgain(onAction, kind = ButtonKind.Text)
    }
}

@Composable
private fun StartAgain(onAction: (ResetAction) -> Unit, kind: ButtonKind = ButtonKind.Filled) {
    SelloButton(
        text = stringResource(R.string.reset_start_again),
        onClick = { onAction(ResetAction.Leave) },
        kind = kind,
        modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md)
            .testTag(RESET_LEAVE_TAG)
    )
}

const val RESET_TAG = "reset"
const val RESET_LOADING_TAG = "reset:loading"
const val RESET_UNREADABLE_TAG = "reset:unreadable"
const val RESET_COUNTS_TAG = "reset:counts"
const val RESET_CONTINUE_TAG = "reset:continue"
const val RESET_CLOSE_TAG = "reset:close"
const val RESET_WORD_SLIP_TAG = "reset:word-slip"
const val RESET_WORD_TAG = "reset:word"
const val RESET_CONFIRM_TAG = "reset:confirm"
const val RESET_CANCEL_TAG = "reset:cancel"
const val RESET_FAILED_TAG = "reset:failed"
const val RESET_UNKNOWN_TAG = "reset:unknown"
const val RESET_DONE_TAG = "reset:done"
const val RESET_PENDING_TAG = "reset:pending"
const val RESET_APPLY_TAG = "reset:apply"
const val RESET_LEAVE_TAG = "reset:leave"
