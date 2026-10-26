package com.software.sello.feature.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupRecords
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceRead
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Respaldo screen. Choosing a destination is the system's; saving belongs to
 * [BackupSession], so it outlives this screen. The suggested name is kept across
 * process death while the picker is open, because the picker's answer arrives in a
 * new process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModel(
    private val handle: SavedStateHandle,
    private val session: BackupSession,
    private val records: BackupRecords,
    clock: FinancialClock
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val choosing = MutableStateFlow(false)

    /** The name to suggest while the picker is waiting to be opened, then null. */
    private val pickerRequest = MutableStateFlow<String?>(null)

    private val record: Flow<PreferenceRead<BackupRecord>> =
        retry.flatMapLatest { records.observeBackups() }

    val state: StateFlow<BackupState> = combine(
        record,
        session.progress,
        combine(choosing, pickerRequest, ::Pair),
        clock.today
    ) { read, progress, (picking, request), today ->
        val (stored, failed) = when (read) {
            is PreferenceRead.Ready -> read.value to false
            is PreferenceRead.Failed -> (read.lastGood ?: BackupRecord()) to true
        }
        val saving = progress == BackupProgress.Saving
        BackupState(
            loading = false,
            last = (progress as? BackupProgress.Saved)?.backup ?: stored.last,
            zone = today.zone,
            today = today.date,
            recordFailed = failed,
            // While this process is saving, the recorded start is its own.
            unknownOutcome = stored.unfinished && !saving,
            choosing = picking,
            pickerRequest = request,
            saving = saving,
            justSaved = progress is BackupProgress.Saved,
            failure = (progress as? BackupProgress.Failed)?.failure
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), BackupState())

    fun onAction(action: BackupAction) {
        when (action) {
            BackupAction.Create -> create()
            BackupAction.PickerOpened -> pickerRequest.value = null
            is BackupAction.DestinationChosen -> chosen(action.destination)
            BackupAction.RetryRecord -> retry.update { it + 1 }
        }
    }

    private fun create() {
        if (choosing.value || state.value.saving) return
        choosing.value = true
        session.acknowledge()
        viewModelScope.launch {
            val name = session.suggestedName()
            handle[SUGGESTED_NAME] = name
            pickerRequest.value = name
        }
    }

    /** [destination] is null when the picker was dismissed: nothing happens, nothing is said. */
    private fun chosen(destination: String?) {
        choosing.value = false
        val name = handle.get<String>(SUGGESTED_NAME) ?: return
        handle.remove<String>(SUGGESTED_NAME)
        if (destination != null) session.save(DocumentDestination(destination), name)
    }

    private companion object {
        const val SUGGESTED_NAME = "backup.suggestedName"
        const val STOP_MILLIS = 5_000L
    }
}

sealed interface BackupAction {
    data object Create : BackupAction

    /** The screen has handed [BackupState.pickerRequest] to the system. */
    data object PickerOpened : BackupAction

    /** The picker's answer: a document, or null when it was dismissed or unavailable. */
    data class DestinationChosen(val destination: String?) : BackupAction

    data object RetryRecord : BackupAction
}

data class BackupState(
    val loading: Boolean = true,
    val last: LastBackup? = null,
    val zone: ZoneId? = null,
    val today: LocalDate? = null,
    val recordFailed: Boolean = false,
    val unknownOutcome: Boolean = false,
    val choosing: Boolean = false,
    val pickerRequest: String? = null,
    val saving: Boolean = false,
    val justSaved: Boolean = false,
    val failure: BackupFailure? = null
) {
    val busy: Boolean get() = loading || choosing || saving
}
