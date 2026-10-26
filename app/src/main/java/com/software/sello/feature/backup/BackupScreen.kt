package com.software.sello.feature.backup

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.dateLabels

@Composable
fun BackupRoot(viewModels: ViewModelProvider.Factory, contentPadding: PaddingValues) {
    val model: BackupViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    val restoreModel: RestoreViewModel = viewModel(factory = viewModels)
    val restore by restoreModel.state.collectAsStateWithLifecycle()
    RestoreFilePicker(restore, restoreModel::onAction)
    // Once the history is replaced, leaving starts the app again from Recibo.
    BackHandler(enabled = restore.replaced) { restoreModel.onAction(RestoreAction.Leave) }
    BackupSavePicker(state, model::onAction)
    BackupScreen(
        state,
        model::onAction,
        contentPadding,
        restoreFirst = restore.step != RestoreStep.Idle && restore.step != RestoreStep.Checking,
        restoreOnly = restore.replaced
    ) {
        RestoreSection(restore, restoreModel::onAction)
    }
}

/**
 * Making a backup file. What a backup is, and is not, is always on screen before the
 * person chooses where to save it. There is no restore here yet.
 */
@Composable
fun BackupScreen(
    state: BackupState,
    onAction: (BackupAction) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    restoreFirst: Boolean = false,
    restoreOnly: Boolean = false,
    restore: @Composable () -> Unit = {}
) {
    val scroll = rememberScrollState()
    // A preview, rejection or outcome appears at the top; bring it into view from
    // wherever the person tapped Elegir archivo.
    val reducedMotion = SelloTheme.reducedMotion
    LaunchedEffect(restoreFirst, restoreOnly) {
        if (restoreFirst || restoreOnly) {
            if (reducedMotion) scroll.scrollTo(0) else scroll.animateScrollTo(0)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(contentPadding)
            .testTag(BACKUP_TAG)
    ) {
        if (state.loading) {
            SlipSkeleton(Modifier.testTag(BACKUP_LOADING_TAG))
            return@Column
        }
        // A restore under way or just answered is the thing to read first; once the
        // history is replaced it is the only thing on the screen.
        if (restoreFirst || restoreOnly) restore()
        if (restoreOnly) return@Column
        BackupProblems(state) { onAction(BackupAction.Create) }
        LastBackupSlip(state, onAction)
        Disclosure()
        SelloButton(
            text = stringResource(
                when {
                    state.saving -> R.string.backup_saving
                    state.last == null -> R.string.backup_create
                    else -> R.string.backup_create_another
                }
            ),
            onClick = { onAction(BackupAction.Create) },
            loading = state.saving,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag(BACKUP_CREATE_TAG)
        )
        Text(
            text = stringResource(R.string.backup_create_caption),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (!restoreFirst) restore()
    }
}

@Composable
private fun LastBackupSlip(state: BackupState, onAction: (BackupAction) -> Unit) {
    val last = state.last
    Slip(Modifier.fillMaxWidth().testTag(BACKUP_LAST_TAG), pinked = false) {
        // The stamp sits beside the title when there is room and under it when not.
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.backup_last),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )
            if (state.justSaved) {
                Stamp(
                    text = stringResource(R.string.backup_saved_stamp),
                    ink = StampInk.Good,
                    land = true,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(BACKUP_SAVED_TAG)
                )
            }
        }
        if (last == null || state.zone == null) {
            Text(
                text = stringResource(
                    if (state.recordFailed) R.string.backup_record_failed else R.string.backup_none
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = SelloTheme.colors.inkSoft
            )
            if (state.recordFailed) {
                SelloButton(
                    text = stringResource(R.string.backup_retry),
                    onClick = { onAction(BackupAction.RetryRecord) },
                    kind = ButtonKind.Text
                )
            }
            return@Slip
        }
        val labels = dateLabels()
        val context = LocalContext.current
        LeaderLine(
            stringResource(R.string.backup_date),
            labels.moment(last.at, state.zone, stringResource(R.string.backup_moment))
        )
        LeaderLine(stringResource(R.string.backup_categories), countText(last.summary.categories))
        LeaderLine(stringResource(R.string.backup_expenses), countText(last.summary.expenses))
        LeaderLine(stringResource(R.string.backup_incomes), countText(last.summary.incomes))
        if (last.summary.goals > 0) {
            LeaderLine(stringResource(R.string.backup_goals), countText(last.summary.goals))
        }
        if (last.summary.schedules > 0) {
            LeaderLine(stringResource(R.string.backup_schedules), countText(last.summary.schedules))
        }
        if (last.summary.investments > 0) {
            LeaderLine(
                stringResource(R.string.backup_investments),
                countText(last.summary.investments)
            )
        }
        LeaderLine(
            stringResource(R.string.backup_size),
            Formatter.formatShortFileSize(context, last.bytes)
        )
        Text(
            text = last.name,
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.xs)
        )
    }
}

@Composable
private fun Disclosure() {
    Slip(Modifier.fillMaxWidth().testTag(BACKUP_DISCLOSURE_TAG), pinked = false) {
        Text(
            text = stringResource(R.string.backup_disclosure_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        BulletList(backupDisclosure(), Modifier.padding(top = SelloTheme.spacing.sm))
    }
}

/** What a backup file is, and is not, read before choosing where to save one. */
@Composable
internal fun backupDisclosure(): List<Bullet> = listOf(
    Bullet(
        stringResource(R.string.backup_disclosure_readable_lead),
        stringResource(R.string.backup_disclosure_readable)
    ),
    Bullet(
        stringResource(R.string.backup_disclosure_yours_lead),
        stringResource(R.string.backup_disclosure_yours)
    ),
    Bullet(
        stringResource(R.string.backup_disclosure_cloud_lead),
        stringResource(R.string.backup_disclosure_cloud)
    ),
    Bullet(null, stringResource(R.string.backup_disclosure_reset))
)

/**
 * A save that failed, or one a closed process may have left unfinished, as Respaldo and
 * Configuración's backup card both say it, with [onCreate] to try again the way that
 * screen starts a save. Nothing when there is neither.
 */
@Composable
internal fun BackupProblems(state: BackupState, onCreate: () -> Unit) {
    state.failure?.let { failure ->
        ErrorSlip(
            title = stringResource(R.string.backup_failed_title),
            message = stringResource(
                when (failure) {
                    BackupFailure.Write -> R.string.backup_failed_write
                    BackupFailure.Storage -> R.string.backup_failed_storage
                    BackupFailure.TooLarge -> R.string.backup_failed_too_large
                    BackupFailure.SelfCheck -> R.string.backup_failed_self_check
                }
            ),
            retryLabel = stringResource(R.string.backup_retry),
            onRetry = { if (!state.busy) onCreate() },
            modifier = Modifier.testTag(BACKUP_FAILED_TAG)
        )
    }
    if (state.unknownOutcome && state.failure == null) {
        ErrorSlip(
            title = stringResource(R.string.backup_unknown_title),
            message = stringResource(R.string.backup_unknown),
            retryLabel = stringResource(R.string.backup_unknown_action),
            onRetry = { if (!state.busy) onCreate() },
            modifier = Modifier.testTag(BACKUP_UNKNOWN_TAG)
        )
    }
}

const val BACKUP_TAG = "backup"
const val BACKUP_LOADING_TAG = "backup:loading"
const val BACKUP_LAST_TAG = "backup:last"
const val BACKUP_SAVED_TAG = "backup:saved"
const val BACKUP_DISCLOSURE_TAG = "backup:disclosure"
const val BACKUP_CREATE_TAG = "backup:create"
const val BACKUP_FAILED_TAG = "backup:failed"
const val BACKUP_UNKNOWN_TAG = "backup:unknown"
