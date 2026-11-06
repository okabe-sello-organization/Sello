package com.software.sello.feature.startup

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonPair
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.RestoreBody
import com.software.sello.designsystem.component.RestoreHeading
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.restore.RestoredSlip
import com.software.sello.presentation.restore.contentsText
import com.software.sello.presentation.restore.message

/**
 * Owns the view model and the one thing that leaves the screen: [onStartedOver] is
 * called once the unreadable data has been set aside. A retry that works needs no
 * call: the app replaces this screen as soon as the data is open.
 */
@Composable
fun StartupFailureRoot(viewModels: ViewModelProvider.Factory, onStartedOver: () -> Unit) {
    val viewModel: StartupFailureViewModel = viewModel(factory = viewModels)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.startedOver) {
        if (state.startedOver) onStartedOver()
    }
    // Any file may be a backup: providers name JSON differently, and the check decides.
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onAction(StartupFailureAction.Chosen(uri?.toString()))
    }
    LaunchedEffect(state.pickerRequest) {
        if (!state.pickerRequest) return@LaunchedEffect
        viewModel.onAction(StartupFailureAction.PickerOpened)
        try {
            opener.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            viewModel.onAction(StartupFailureAction.Chosen(null))
        }
    }
    // Back closes the warning or a preview first; with none open it leaves the app as
    // usual. Once a restore began it waits for it, then starts the app again.
    val restore = state.restore
    val closable =
        restore is StartupRestoreStep.Previewing || restore is StartupRestoreStep.Rejected
    BackHandler(enabled = state.confirming || closable || state.restoreBegun) {
        viewModel.onAction(
            when {
                state.restoreBegun -> StartupFailureAction.Leave
                closable -> StartupFailureAction.CancelRestore
                else -> StartupFailureAction.Cancel
            }
        )
    }
    StartupFailureScreen(state, viewModel::onAction)
}

@Composable
fun StartupFailureScreen(
    state: StartupFailureState,
    onAction: (StartupFailureAction) -> Unit,
    modifier: Modifier = Modifier
) {
    SelloScaffold(title = stringResource(R.string.app_name), modifier = modifier) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
            // The scaffold's padding already leaves room for the keyboard. It is applied
            // outside the scroll so the keyboard shrinks what can be seen, and the word
            // being typed and its two buttons stay reachable above it.
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Explanation(state, onAction)
            RestoreFromBackup(state.restore, onAction)
            if (state.notSetAside) {
                FieldMessageText(
                    FieldMessage(stringResource(R.string.startup_failure_not_set_aside)),
                    Modifier.testTag(STARTUP_NOT_SET_ASIDE_TAG)
                )
            }
            if (state.confirming) Warning(state, onAction)
        }
    }
}

@Composable
private fun Explanation(state: StartupFailureState, onAction: (StartupFailureAction) -> Unit) {
    Slip(modifier = Modifier.fillMaxWidth().testTag(STARTUP_FAILURE_TAG)) {
        RestoreHeading(stringResource(R.string.startup_failure_title))
        RestoreBody(stringResource(R.string.startup_failure_body))
        if (state.stillFailing) {
            Spacer(Modifier.height(SelloTheme.spacing.sm))
            FieldMessageText(
                FieldMessage(stringResource(R.string.startup_failure_still_failing)),
                Modifier.testTag(STARTUP_STILL_FAILING_TAG)
            )
        }
        Spacer(Modifier.height(SelloTheme.spacing.lg))
        SelloButton(
            text = stringResource(R.string.startup_failure_retry),
            onClick = { onAction(StartupFailureAction.Retry) },
            modifier = Modifier.fillMaxWidth().testTag(STARTUP_RETRY_TAG),
            enabled = !state.restoreBegun
        )
        Spacer(Modifier.height(SelloTheme.spacing.md))
        val checking = state.restore == StartupRestoreStep.Checking
        SelloButton(
            text = stringResource(
                if (checking) R.string.restore_checking else R.string.startup_failure_restore
            ),
            onClick = { onAction(StartupFailureAction.RestoreBackup) },
            modifier = Modifier.fillMaxWidth().testTag(STARTUP_RESTORE_TAG),
            kind = ButtonKind.Outlined,
            loading = checking,
            enabled = !state.choosing && !state.restoreBegun
        )
        Spacer(Modifier.height(SelloTheme.spacing.md))
        SelloButton(
            text = stringResource(R.string.startup_failure_start_over),
            onClick = { onAction(StartupFailureAction.StartOver) },
            modifier = Modifier.fillMaxWidth().testTag(STARTUP_START_OVER_TAG),
            kind = ButtonKind.Outlined,
            enabled = !state.confirming && !state.choosing && !state.restoreBegun
        )
    }
}

/** A chosen backup: why it cannot be restored, its preview, or how restoring it ended. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RestoreFromBackup(step: StartupRestoreStep, onAction: (StartupFailureAction) -> Unit) {
    if (step == StartupRestoreStep.Idle || step == StartupRestoreStep.Checking) return
    val place = remember { BringIntoViewRequester() }
    // Each step opens below the choices; on a small window it must not open unseen.
    LaunchedEffect(step::class) { place.bringIntoView() }
    val modifier = Modifier.fillMaxWidth().bringIntoViewRequester(place)
    when (step) {
        StartupRestoreStep.Idle, StartupRestoreStep.Checking -> Unit

        is StartupRestoreStep.Rejected -> StatusSlip(
            kind = StatusKind.ActionFailed(stringResource(R.string.restore_rejected_title)),
            message = stringResource(step.reason.message()),
            modifier = modifier.testTag(STARTUP_RESTORE_REJECTED_TAG),
            action = StatusAction(stringResource(R.string.restore_choose_other)) {
                onAction(StartupFailureAction.RestoreBackup)
            }
        )

        is StartupRestoreStep.Previewing -> Preview(step.backup, false, onAction, modifier)

        is StartupRestoreStep.Restoring -> Preview(step.backup, true, onAction, modifier)

        StartupRestoreStep.Interrupted -> StatusSlip(
            kind = StatusKind.Unknown(
                stringResource(R.string.startup_restore_interrupted_title)
            ),
            message = stringResource(R.string.startup_restore_interrupted_body),
            modifier = modifier.testTag(STARTUP_RESTORE_INTERRUPTED_TAG),
            action = StatusAction(stringResource(R.string.startup_restore_continue)) {
                onAction(StartupFailureAction.Leave)
            }
        )

        is StartupRestoreStep.Restored -> Box(modifier) {
            RestoredSlip(
                step.outcome,
                step.applying,
                onApply = { onAction(StartupFailureAction.ApplyPreferences) },
                onLeave = { onAction(StartupFailureAction.Leave) }
            )
        }
    }
}

/**
 * What the backup holds and what restoring it does. There are no current counts: the
 * data that would not open cannot be read. The date is the backup's own zone's.
 */
@Composable
private fun Preview(
    backup: CheckedBackup,
    restoring: Boolean,
    onAction: (StartupFailureAction) -> Unit,
    modifier: Modifier
) {
    val contents = backup.contents
    Slip(modifier.testTag(STARTUP_RESTORE_PREVIEW_TAG), pinked = false) {
        RestoreHeading(stringResource(R.string.startup_restore_title))
        RestoreBody(
            stringResource(
                R.string.restore_preview_date,
                dateLabels().moment(
                    backup.backupAt,
                    backup.zone,
                    stringResource(R.string.backup_moment)
                )
            )
        )
        RestoreBody(
            stringResource(
                R.string.startup_restore_counts,
                contentsText(
                    contents,
                    withGoals = contents.goals > 0,
                    withSchedules = contents.schedules > 0
                )
            )
        )
        RestoreBody(stringResource(R.string.startup_restore_warning))
        ButtonPair(
            firstWeight = 1f,
            secondWeight = 2f,
            gap = SelloTheme.spacing.md,
            modifier = Modifier.padding(top = SelloTheme.spacing.md),
            first = {
                SelloButton(
                    text = stringResource(R.string.restore_cancel),
                    onClick = { onAction(StartupFailureAction.CancelRestore) },
                    kind = ButtonKind.Outlined,
                    enabled = !restoring,
                    modifier = Modifier.testTag(STARTUP_RESTORE_CANCEL_TAG)
                )
            },
            second = {
                SelloButton(
                    text = stringResource(
                        if (restoring) {
                            R.string.restore_replacing
                        } else {
                            R.string.startup_restore_confirm
                        }
                    ),
                    onClick = { onAction(StartupFailureAction.ConfirmRestore) },
                    tone = ButtonTone.Loss,
                    loading = restoring,
                    enabled = !restoring,
                    modifier = Modifier.testTag(STARTUP_RESTORE_CONFIRM_TAG)
                )
            }
        )
    }
}

/** The second step: what starting over does, and the word that confirms it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Warning(state: StartupFailureState, onAction: (StartupFailureAction) -> Unit) {
    val place = remember { BringIntoViewRequester() }
    // The warning opens below the choices; on a small window it must not open unseen.
    LaunchedEffect(Unit) { place.bringIntoView() }
    Slip(
        pinked = false,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(place)
            .testTag(STARTUP_WARNING_TAG)
    ) {
        RestoreHeading(stringResource(R.string.startup_start_over_title))
        // The warning of the step that deletes data keeps full ink.
        RestoreBody(stringResource(R.string.startup_start_over_body), soft = false)
        RestoreBody(stringResource(R.string.startup_failure_newer_hint))
        Spacer(Modifier.height(SelloTheme.spacing.md))
        FormField(
            label = stringResource(R.string.startup_start_over_word),
            value = state.typed,
            onValueChange = { onAction(StartupFailureAction.Typed(it)) },
            modifier = Modifier.testTag(STARTUP_WORD_TAG),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            )
        )
        Spacer(Modifier.height(SelloTheme.spacing.lg))
        ButtonPair(
            1f,
            1f,
            gap = SelloTheme.spacing.md,
            first = {
                SelloButton(
                    text = stringResource(R.string.startup_start_over_cancel),
                    onClick = { onAction(StartupFailureAction.Cancel) },
                    modifier = Modifier.testTag(STARTUP_CANCEL_TAG),
                    kind = ButtonKind.Outlined
                )
            },
            second = {
                SelloButton(
                    text = stringResource(R.string.startup_start_over_confirm),
                    onClick = { onAction(StartupFailureAction.Confirm) },
                    modifier = Modifier.testTag(STARTUP_CONFIRM_TAG),
                    tone = ButtonTone.Loss,
                    enabled = state.canConfirm
                )
            }
        )
    }
}

const val STARTUP_FAILURE_TAG = "startup-failure"
const val STARTUP_RETRY_TAG = "startup-failure:retry"
const val STARTUP_STILL_FAILING_TAG = "startup-failure:still-failing"
const val STARTUP_START_OVER_TAG = "startup-failure:start-over"
const val STARTUP_NOT_SET_ASIDE_TAG = "startup-failure:not-set-aside"
const val STARTUP_WARNING_TAG = "startup-failure:warning"
const val STARTUP_WORD_TAG = "startup-failure:word"
const val STARTUP_CONFIRM_TAG = "startup-failure:confirm"
const val STARTUP_CANCEL_TAG = "startup-failure:cancel"
const val STARTUP_RESTORE_TAG = "startup-failure:restore"
const val STARTUP_RESTORE_REJECTED_TAG = "startup-failure:restore-rejected"
const val STARTUP_RESTORE_PREVIEW_TAG = "startup-failure:restore-preview"
const val STARTUP_RESTORE_CONFIRM_TAG = "startup-failure:restore-confirm"
const val STARTUP_RESTORE_CANCEL_TAG = "startup-failure:restore-cancel"
const val STARTUP_RESTORE_INTERRUPTED_TAG = "startup-failure:restore-interrupted"
