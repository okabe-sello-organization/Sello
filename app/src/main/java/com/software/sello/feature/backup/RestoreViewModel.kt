package com.software.sello.feature.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.port.AppRestart
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.FinancialClock
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Restaurar part of Respaldo. Choosing a file is the system's; checking and
 * replacing belong to [RestoreSession], so they outlive this screen. After a
 * replacement, going on restarts the app, because the history, its generation and
 * possibly its financial zone are not the ones this process started with.
 *
 * The chosen file's address is kept across process death only while it is being
 * checked, so a file the process died checking is checked again, once. From then on
 * the staging alone stands for the file, whichever screen confirms or cancels it, so
 * a file cancelled, confirmed or no longer staged is never read again. The file itself
 * is not kept: the system keeps the read grant the picker gave; if that is gone the
 * file is named unreadable.
 */
class RestoreViewModel(
    private val handle: SavedStateHandle,
    private val session: RestoreSession,
    private val restart: AppRestart,
    clock: FinancialClock
) : ViewModel() {
    private val pickerRequest = MutableStateFlow(false)

    init {
        val chosen = handle.get<String>(CHOSEN)
        handle.remove<String>(CHOSEN)
        session.resume(chosen?.let(::DocumentSource))
        viewModelScope.launch {
            session.step.collect { if (it != RestoreStep.Checking) handle.remove<String>(CHOSEN) }
        }
    }

    val state: StateFlow<RestoreState> = combine(
        session.step,
        pickerRequest,
        clock.today
    ) { step, request, today ->
        RestoreState(step = step, pickerRequest = request, zone = today.zone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), RestoreState())

    fun onAction(action: RestoreAction) {
        when (action) {
            RestoreAction.Choose -> if (!state.value.busy) pickerRequest.value = true

            RestoreAction.PickerOpened -> pickerRequest.value = false

            is RestoreAction.Chosen -> action.source?.let {
                handle[CHOSEN] = it
                session.choose(DocumentSource(it))
                // Not checked after all, or checked already.
                if (session.step.value != RestoreStep.Checking) handle.remove<String>(CHOSEN)
            }

            RestoreAction.Confirm -> session.confirm()

            RestoreAction.Check -> session.check()

            RestoreAction.Cancel -> session.cancel()

            RestoreAction.Dismiss -> session.dismiss()

            RestoreAction.ApplyPreferences -> session.applyPreferences()

            RestoreAction.Leave -> {
                session.finish()
                restart.restart()
            }
        }
    }

    private companion object {
        const val CHOSEN = "restore.chosen"
        const val STOP_MILLIS = 5_000L
    }
}

sealed interface RestoreAction {
    data object Choose : RestoreAction

    data object PickerOpened : RestoreAction

    /** The picker's answer: a document, or null when it was dismissed. */
    data class Chosen(val source: String?) : RestoreAction

    data object Confirm : RestoreAction

    /** Comprobar, after an unknown outcome. */
    data object Check : RestoreAction

    data object Cancel : RestoreAction

    data object Dismiss : RestoreAction

    data object ApplyPreferences : RestoreAction

    /** Ir al recibo, or Back, once the history has been replaced. */
    data object Leave : RestoreAction
}

data class RestoreState(
    val step: RestoreStep = RestoreStep.Idle,
    val pickerRequest: Boolean = false,
    val zone: ZoneId? = null
) {
    val busy: Boolean
        get() = pickerRequest || step == RestoreStep.Checking || step is RestoreStep.Replacing ||
            (step as? RestoreStep.Unknown)?.checking == true

    /** Once the history is or may be replaced, every way out starts the app again. */
    val replaced: Boolean get() = step is RestoreStep.Restored || step is RestoreStep.Unknown
}
