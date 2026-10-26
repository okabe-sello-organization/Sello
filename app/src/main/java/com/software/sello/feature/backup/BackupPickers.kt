package com.software.sello.feature.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Backups are plain JSON; the picker offers this type and the suggested name. */
private const val BACKUP_MIME = "application/json"

/**
 * Hands [BackupState.pickerRequest] to the system's save picker and its answer back.
 * The launcher is restored with the screen that holds it, so an answer that arrives
 * after the process was recreated still reaches the new view model.
 */
@Composable
internal fun BackupSavePicker(state: BackupState, onAction: (BackupAction) -> Unit) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri -> onAction(BackupAction.DestinationChosen(uri?.toString())) }
    LaunchedEffect(state.pickerRequest) {
        val name = state.pickerRequest ?: return@LaunchedEffect
        onAction(BackupAction.PickerOpened)
        try {
            picker.launch(name)
        } catch (_: ActivityNotFoundException) {
            onAction(BackupAction.DestinationChosen(null))
        }
    }
}

/**
 * Hands [RestoreState.pickerRequest] to the system's file picker and the chosen file
 * back; [onChosen] follows a file, never a dismissal. Any file may be a backup:
 * providers name JSON differently, and the check decides.
 */
@Composable
internal fun RestoreFilePicker(
    state: RestoreState,
    onAction: (RestoreAction) -> Unit,
    onChosen: () -> Unit = {}
) {
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        onAction(RestoreAction.Chosen(uri?.toString()))
        if (uri != null) onChosen()
    }
    LaunchedEffect(state.pickerRequest) {
        if (!state.pickerRequest) return@LaunchedEffect
        onAction(RestoreAction.PickerOpened)
        try {
            opener.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            onAction(RestoreAction.Chosen(null))
        }
    }
}
