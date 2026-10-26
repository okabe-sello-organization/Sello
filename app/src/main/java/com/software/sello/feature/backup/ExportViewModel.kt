package com.software.sello.feature.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.ExportFormat
import com.software.sello.domain.port.ExportWords
import com.software.sello.domain.port.SharedExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exportar in Tus datos. Choosing a destination is the system's; writing belongs to
 * [ExportSession], so it outlives this screen. The format and suggested name are kept
 * across process death while the picker is open, because its answer arrives in a new
 * process. The words come with each request, from the app's text.
 */
class ExportViewModel(
    private val handle: SavedStateHandle,
    private val session: ExportSession
) : ViewModel() {
    private val choosing = MutableStateFlow(false)
    private val pickerRequest = MutableStateFlow<ExportPickerRequest?>(null)

    val state: StateFlow<ExportState> =
        combine(session.progress, choosing, pickerRequest) { progress, picking, request ->
            ExportState(
                working = progress == ExportProgress.Working,
                choosing = picking,
                pickerRequest = request,
                saved = (progress as? ExportProgress.Saved)?.name,
                share = (progress as? ExportProgress.ReadyToShare)?.share,
                problem = (progress as? ExportProgress.Failed)?.problem
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), ExportState())

    fun onAction(action: ExportAction) {
        when (action) {
            is ExportAction.Save -> save(action.format)

            ExportAction.PickerOpened -> pickerRequest.value = null

            is ExportAction.DestinationChosen -> chosen(action.destination, action.words)

            is ExportAction.Share -> if (!state.value.busy) {
                session.share(action.format, action.words)
            }

            // The share sheet has the file; what happens there is the other app's.
            ExportAction.ShareOpened, ExportAction.Dismiss -> session.acknowledge()
        }
    }

    private fun save(format: ExportFormat) {
        if (state.value.busy) return
        choosing.value = true
        session.acknowledge()
        viewModelScope.launch {
            val name = session.suggestedName(format)
            handle[SUGGESTED_NAME] = name
            handle[FORMAT] = format.name
            pickerRequest.value = ExportPickerRequest(name, format)
        }
    }

    /** [destination] is null when the picker was dismissed: nothing happens, nothing is said. */
    private fun chosen(destination: String?, words: ExportWords) {
        choosing.value = false
        val name = handle.get<String>(SUGGESTED_NAME) ?: return
        val format = handle.get<String>(FORMAT)?.let(ExportFormat::valueOf) ?: return
        handle.remove<String>(SUGGESTED_NAME)
        handle.remove<String>(FORMAT)
        if (destination != null) {
            session.save(format, words, DocumentDestination(destination), name)
        }
    }

    private companion object {
        const val SUGGESTED_NAME = "export.suggestedName"
        const val FORMAT = "export.format"
        const val STOP_MILLIS = 5_000L
    }
}

/** A save picker to open: the name to suggest, and the format, which sets its type. */
data class ExportPickerRequest(val name: String, val format: ExportFormat)

sealed interface ExportAction {
    data class Save(val format: ExportFormat) : ExportAction

    /** The screen has handed [ExportState.pickerRequest] to the system. */
    data object PickerOpened : ExportAction

    /** The picker's answer: a document, or null when it was dismissed or unavailable. */
    data class DestinationChosen(val destination: String?, val words: ExportWords) :
        ExportAction

    data class Share(val format: ExportFormat, val words: ExportWords) : ExportAction

    /** The screen has opened the share sheet with [ExportState.share]. */
    data object ShareOpened : ExportAction

    /** A problem or a saved file has been read. */
    data object Dismiss : ExportAction
}

data class ExportState(
    val working: Boolean = false,
    val choosing: Boolean = false,
    val pickerRequest: ExportPickerRequest? = null,
    /** The file name last saved, until the next export. */
    val saved: String? = null,
    val share: SharedExport? = null,
    val problem: ExportProblem? = null
) {
    val busy: Boolean get() = working || choosing
}
