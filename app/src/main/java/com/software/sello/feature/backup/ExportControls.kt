package com.software.sello.feature.backup

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.software.sello.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ChoiceSlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SettingRow
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.port.ExportFormat
import com.software.sello.domain.port.ExportWords
import com.software.sello.presentation.income.incomeSourceLabel

/** The words a CSV export is written in, from the app's text. */
@Composable
internal fun exportWords(): ExportWords = ExportWords(
    date = stringResource(R.string.export_column_date),
    kind = stringResource(R.string.export_column_kind),
    name = stringResource(R.string.export_column_name),
    amount = stringResource(R.string.export_column_amount),
    currency = stringResource(R.string.export_column_currency),
    note = stringResource(R.string.export_column_note),
    expense = stringResource(R.string.export_kind_expense),
    income = stringResource(R.string.export_kind_income),
    goalContribution = stringResource(R.string.export_kind_goal_contribution),
    goalWithdrawal = stringResource(R.string.export_kind_goal_withdrawal),
    investmentContribution = stringResource(R.string.export_kind_investment_contribution),
    investmentWithdrawal = stringResource(R.string.export_kind_investment_withdrawal),
    dividend = stringResource(R.string.export_kind_dividend),
    investmentValue = stringResource(R.string.export_kind_investment_value),
    sources = IncomeSource.keys.filter { it != IncomeSource.Other.KEY }
        .associateWith { incomeSourceLabel(it) }
)

/**
 * Hands [ExportState.pickerRequest] to the system's save picker, one per format so the
 * document gets its type, and [ExportState.share] to the share sheet, granting the
 * chosen app read access to that one file.
 */
@Composable
internal fun ExportPickers(state: ExportState, onAction: (ExportAction) -> Unit) {
    val words = exportWords()
    val currentWords by rememberUpdatedState(words)
    val csv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> onAction(ExportAction.DestinationChosen(uri?.toString(), currentWords)) }
    val json = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> onAction(ExportAction.DestinationChosen(uri?.toString(), currentWords)) }
    LaunchedEffect(state.pickerRequest) {
        val request = state.pickerRequest ?: return@LaunchedEffect
        onAction(ExportAction.PickerOpened)
        try {
            (if (request.format == ExportFormat.Csv) csv else json).launch(request.name)
        } catch (_: ActivityNotFoundException) {
            onAction(ExportAction.DestinationChosen(null, currentWords))
        }
    }
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.export_share_title)
    LaunchedEffect(state.share) {
        val share = state.share ?: return@LaunchedEffect
        val uri = share.uri.toUri()
        val send = Intent(Intent.ACTION_SEND)
            .setType(share.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        try {
            context.startActivity(
                Intent.createChooser(send, chooserTitle)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: ActivityNotFoundException) {
            Unit
        }
        onAction(ExportAction.ShareOpened)
    }
}

/** Tus datos' Exportar row: while exporting it says so, after a save it names the file. */
@Composable
internal fun ExportRow(state: ExportState, onOpen: () -> Unit) {
    SettingRow(
        title = stringResource(R.string.settings_export),
        icon = SelloIcon.Download,
        caption = when {
            state.working -> stringResource(R.string.settings_export_working)
            state.saved != null -> stringResource(R.string.settings_export_saved, state.saved)
            else -> null
        },
        onClick = onOpen,
        divider = false,
        enabled = !state.busy,
        modifier = Modifier.testTag(EXPORT_ROW_TAG)
    )
}

/** An export that failed, said in plain words; trying again opens the choice again. */
@Composable
internal fun ExportProblems(state: ExportState, onRetry: () -> Unit) {
    val problem = state.problem ?: return
    ErrorSlip(
        title = stringResource(R.string.export_failed_title),
        message = stringResource(
            when (problem) {
                ExportProblem.Storage -> R.string.export_failed_storage
                ExportProblem.Write -> R.string.export_failed_write
                ExportProblem.Share -> R.string.export_failed_share
            }
        ),
        retryLabel = stringResource(R.string.export_retry),
        onRetry = onRetry,
        modifier = Modifier.testTag(EXPORT_FAILED_TAG)
    )
}

/**
 * The choice before anything is written: CSV or JSON, what the file is and is not, then
 * Compartir or Guardar.
 */
@Composable
internal fun ExportSlip(
    onShare: (ExportFormat) -> Unit,
    onSave: (ExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var format by rememberSaveable { mutableStateOf(ExportFormat.Csv) }
    ChoiceSlip(
        title = stringResource(R.string.export_title),
        firstLabel = stringResource(R.string.export_share),
        onFirst = { onShare(format) },
        secondLabel = stringResource(R.string.export_save),
        onSecond = { onSave(format) },
        backLabel = stringResource(R.string.export_cancel),
        onBack = onDismiss,
        modifier = Modifier.testTag(EXPORT_SLIP_TAG)
    ) {
        SegmentedSwitch(
            options = listOf(
                stringResource(R.string.export_csv),
                stringResource(R.string.export_json)
            ),
            selectedIndex = ExportFormat.entries.indexOf(format),
            onSelect = { format = ExportFormat.entries[it] },
            modifier = Modifier.testTag(EXPORT_FORMAT_TAG)
        )
        Text(
            text = stringResource(
                if (format == ExportFormat.Csv) {
                    R.string.export_csv_caption
                } else {
                    R.string.export_json_caption
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
        BulletList(
            listOf(
                Bullet(
                    stringResource(R.string.export_readable_lead),
                    stringResource(R.string.export_readable)
                ),
                Bullet(
                    stringResource(R.string.export_not_backup_lead),
                    stringResource(R.string.export_not_backup)
                ),
                Bullet(
                    stringResource(R.string.export_shared_lead),
                    stringResource(R.string.export_shared)
                )
            ),
            Modifier.padding(top = SelloTheme.spacing.md)
        )
    }
}

const val EXPORT_ROW_TAG = "settings:export"
const val EXPORT_SLIP_TAG = "settings:export:slip"
const val EXPORT_FORMAT_TAG = "settings:export:format"
const val EXPORT_FAILED_TAG = "settings:export:failed"
