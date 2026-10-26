package com.software.sello.feature.backup

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonPair
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SettingRow
import com.software.sello.designsystem.component.SettingTone
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.dateLabels

/**
 * Configuración's Tus datos: the backup card, then Borrar todos los datos. It saves and
 * restores through the same sessions as Respaldo; a chosen file is checked, previewed
 * and confirmed on Respaldo, never here.
 */
@Composable
fun TusDatosRoot(
    viewModels: ViewModelProvider.Factory,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit
) {
    val model: BackupViewModel = viewModel(factory = viewModels)
    val backup by model.state.collectAsStateWithLifecycle()
    val restoreModel: RestoreViewModel = viewModel(factory = viewModels)
    val restore by restoreModel.state.collectAsStateWithLifecycle()
    val exportModel: ExportViewModel = viewModel(factory = viewModels)
    val export by exportModel.state.collectAsStateWithLifecycle()
    BackupSavePicker(backup, model::onAction)
    RestoreFilePicker(restore, restoreModel::onAction, onChosen = onOpenBackup)
    ExportPickers(export, exportModel::onAction)
    TusDatos(
        backup = backup,
        onBackupAction = model::onAction,
        export = export,
        onExportAction = exportModel::onAction,
        restoreEnabled = !restore.pickerRequest,
        onRestore = {
            // A restore already under way is Respaldo's to show.
            if (restore.step == RestoreStep.Idle) {
                restoreModel.onAction(RestoreAction.Choose)
            } else {
                onOpenBackup()
            }
        },
        onOpenBackup = onOpenBackup,
        onOpenReset = onOpenReset
    )
}

/**
 * The section itself: a save's problems first, then the slip. Respaldar shows what a
 * backup is, and is not, before anything is asked of the system.
 */
@Composable
fun TusDatos(
    backup: BackupState,
    onBackupAction: (BackupAction) -> Unit,
    export: ExportState,
    onExportAction: (ExportAction) -> Unit,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit
) {
    var disclosing by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    val words = exportWords()
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
        BackupProblems(backup) { disclosing = true }
        ExportProblems(export) {
            onExportAction(ExportAction.Dismiss)
            exporting = true
        }
        if (backup.loading) {
            SlipSkeleton(Modifier.testTag(BACKUP_CARD_TAG))
        } else {
            Slip(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_data),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(bottom = SelloTheme.spacing.sm)
                        .semantics { heading() }
                )
                BackupCard(
                    backup = backup,
                    onBackupAction = onBackupAction,
                    onCreate = { disclosing = true },
                    restoreEnabled = restoreEnabled,
                    onRestore = onRestore,
                    onOpenBackup = onOpenBackup
                )
                Box(Modifier.padding(top = SelloTheme.spacing.sm)) {
                    ExportRow(export) { exporting = true }
                }
                SettingRow(
                    title = stringResource(R.string.settings_reset),
                    icon = SelloIcon.DeleteForever,
                    caption = stringResource(R.string.settings_reset_caption),
                    onClick = onOpenReset,
                    tone = SettingTone.Loss,
                    modifier = Modifier.testTag(BACKUP_CARD_RESET_TAG)
                )
            }
        }
    }
    if (exporting) {
        ExportSlip(
            onShare = { format ->
                exporting = false
                onExportAction(ExportAction.Share(format, words))
            },
            onSave = { format ->
                exporting = false
                onExportAction(ExportAction.Save(format))
            },
            onDismiss = { exporting = false }
        )
    }
    if (disclosing) {
        ConfirmSlip(
            title = stringResource(R.string.backup_disclosure_title),
            confirmLabel = stringResource(R.string.settings_backup_choose),
            onConfirm = {
                disclosing = false
                onBackupAction(BackupAction.Create)
            },
            onDismiss = { disclosing = false },
            destructive = false,
            modifier = Modifier.testTag(BACKUP_CARD_DISCLOSURE_TAG)
        ) {
            BulletList(backupDisclosure())
        }
    }
}

/**
 * When the last backup was made, how old and how big it is, a stamp for whether a new one
 * is due, and the way to make one or restore one.
 */
@Composable
private fun BackupCard(
    backup: BackupState,
    onBackupAction: (BackupAction) -> Unit,
    onCreate: () -> Unit,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val colors = SelloTheme.colors
    val last = backup.last
    val zone = backup.zone
    val today = backup.today
    // Unknown when the record could not be read: no stamp says it is fine or due.
    val age = if (backup.recordFailed || zone == null || today == null) {
        null
    } else {
        backupAge(last?.at, zone, today)
    }
    Column(Modifier.testTag(BACKUP_CARD_TAG)) {
        // The stamp sits beside the label when there is room and under it when not.
        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(SelloIcon.Backup.painter(), null, Modifier.size(18.dp), colors.brand)
                Text(
                    stringResource(R.string.settings_backup),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inkSoft
                )
            }
            age?.let { BackupStamp(it, land = backup.justSaved) }
        }
        when {
            age == null -> {
                Text(
                    stringResource(R.string.backup_record_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkSoft,
                    modifier = Modifier.padding(top = SelloTheme.spacing.sm)
                )
                SelloButton(
                    text = stringResource(R.string.backup_retry),
                    onClick = { onBackupAction(BackupAction.RetryRecord) },
                    kind = ButtonKind.Text
                )
            }

            last == null -> BackupFigure(
                stringResource(R.string.settings_backup_never),
                stringResource(R.string.settings_backup_never_caption)
            )

            else -> {
                val day = last.at.atZone(zone).toLocalDate()
                val context = LocalContext.current
                BackupFigure(
                    dateLabels().dayMonthYear(day),
                    stringResource(
                        R.string.settings_backup_age_size,
                        ageText(age),
                        Formatter.formatShortFileSize(context, last.bytes)
                    )
                )
            }
        }
        ButtonPair(
            firstWeight = 1f,
            secondWeight = 1f,
            gap = SelloTheme.spacing.sm,
            modifier = Modifier.padding(top = SelloTheme.spacing.md),
            first = {
                SelloButton(
                    text = stringResource(
                        when {
                            backup.saving -> R.string.backup_saving
                            age == BackupAge.None -> R.string.settings_backup_create_first
                            else -> R.string.settings_backup_create
                        }
                    ),
                    onClick = onCreate,
                    icon = SelloIcon.Backup,
                    enabled = !backup.busy,
                    loading = backup.saving,
                    modifier = Modifier.testTag(BACKUP_CARD_CREATE_TAG)
                )
            },
            second = {
                SelloButton(
                    text = stringResource(R.string.settings_backup_restore),
                    onClick = onRestore,
                    kind = ButtonKind.Outlined,
                    icon = SelloIcon.UploadFile,
                    enabled = restoreEnabled && !backup.saving,
                    modifier = Modifier.testTag(BACKUP_CARD_RESTORE_TAG)
                )
            }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = SelloTheme.spacing.xs)
                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onOpenBackup)
                .testTag(BACKUP_CARD_CONTENTS_TAG)
        ) {
            Text(
                stringResource(R.string.settings_backup_contents),
                style = MaterialTheme.typography.labelLarge,
                color = colors.brand
            )
            Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(18.dp), colors.brand)
        }
    }
}

@Composable
private fun BackupStamp(age: BackupAge, land: Boolean) {
    val (text, ink) = when (age) {
        BackupAge.None -> R.string.settings_backup_stamp_none to StampInk.Over
        is BackupAge.Recent -> R.string.backup_saved_stamp to StampInk.Done
        is BackupAge.Old -> R.string.settings_backup_stamp_old to StampInk.Warn
    }
    Stamp(
        text = stringResource(text),
        ink = ink,
        land = land,
        modifier = Modifier.padding(vertical = 4.dp).testTag(BACKUP_CARD_STAMP_TAG)
    )
}

@Composable
private fun BackupFigure(figure: String, caption: String) {
    Text(
        figure,
        style = MaterialTheme.typography.displaySmall,
        color = SelloTheme.colors.ink,
        modifier = Modifier.padding(top = SelloTheme.spacing.sm)
    )
    Text(caption, style = MaterialTheme.typography.bodyMedium, color = SelloTheme.colors.inkSoft)
}

@Composable
private fun ageText(age: BackupAge): String {
    val days = when (age) {
        is BackupAge.Recent -> age.days
        is BackupAge.Old -> age.days
        BackupAge.None -> 0
    }
    return when (days) {
        0L -> stringResource(R.string.settings_backup_today)
        1L -> stringResource(R.string.settings_backup_yesterday)
        else -> pluralStringResource(R.plurals.settings_backup_days, days.toInt(), days.toInt())
    }
}

const val BACKUP_CARD_TAG = "settings:backup:card"
const val BACKUP_CARD_STAMP_TAG = "settings:backup:stamp"
const val BACKUP_CARD_CREATE_TAG = "settings:backup:create"
const val BACKUP_CARD_DISCLOSURE_TAG = "settings:backup:disclosure"
const val BACKUP_CARD_RESTORE_TAG = "settings:restore"

/** "Ver qué contiene": the way into Respaldo. */
const val BACKUP_CARD_CONTENTS_TAG = "settings:backup"
const val BACKUP_CARD_RESET_TAG = "settings:reset"
