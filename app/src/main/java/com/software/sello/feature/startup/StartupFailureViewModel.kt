package com.software.sello.feature.startup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.AppRestart
import com.software.sello.domain.port.DocumentSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The choices on the screen shown when the data could not be opened. An open warning
 * and what was typed into it are kept in [saved], so a rotation loses neither; nothing
 * is moved until the word has been typed and the confirmation pressed.
 *
 * Restoring a backup is the other way out: a chosen file is checked whole and previewed,
 * and nothing is moved until that preview is confirmed. A preview is not kept across
 * process death, since the file it read is held only in memory; the person chooses again.
 */
class StartupFailureViewModel(
    private val saved: SavedStateHandle,
    private val recovery: StartupRecovery,
    private val restart: AppRestart
) : ViewModel() {
    private val mutable = MutableStateFlow(
        StartupFailureState(
            confirming = saved[KEY_CONFIRMING] ?: false,
            typed = saved[KEY_TYPED] ?: ""
        )
    )
    val state: StateFlow<StartupFailureState> = mutable.asStateFlow()

    fun onAction(action: StartupFailureAction) {
        val now = mutable.value
        // The data has been moved and the app is starting again: nothing else applies.
        if (now.startedOver) return
        // An open or a move is under way; its outcome decides what applies next.
        if (now.busy) return
        if (now.restoreBegun) return afterRestore(action)
        when (action) {
            StartupFailureAction.Retry -> {
                mutable.update { it.copy(busy = true) }
                viewModelScope.launch {
                    val opened = recovery.retry()
                    mutable.update { it.copy(busy = false, stillFailing = !opened) }
                }
            }

            StartupFailureAction.StartOver -> if (!now.choosing) {
                leavePreview()
                warning(open = true)
            }

            is StartupFailureAction.Typed -> if (now.confirming) {
                saved[KEY_TYPED] = action.text
                mutable.update { it.copy(typed = action.text) }
            }

            StartupFailureAction.Confirm -> if (now.confirming && now.canConfirm) {
                mutable.update { it.copy(busy = true) }
                viewModelScope.launch {
                    val moved = recovery.setAside()
                    warning(open = false)
                    mutable.update {
                        it.copy(busy = false, startedOver = moved, notSetAside = !moved)
                    }
                }
            }

            StartupFailureAction.Cancel -> warning(open = false)

            StartupFailureAction.RestoreBackup -> if (!now.choosing) {
                warning(open = false)
                leavePreview()
                mutable.update { it.copy(pickerRequest = true) }
            }

            StartupFailureAction.PickerOpened -> mutable.update { it.copy(pickerRequest = false) }

            is StartupFailureAction.Chosen -> action.source?.let(::check)

            StartupFailureAction.ConfirmRestore -> confirm()

            StartupFailureAction.CancelRestore -> leavePreview()

            StartupFailureAction.ApplyPreferences, StartupFailureAction.Leave -> Unit
        }
    }

    private fun check(source: String) {
        if (mutable.value.restore == StartupRestoreStep.Checking) return
        mutable.update { it.copy(restore = StartupRestoreStep.Checking, notSetAside = false) }
        viewModelScope.launch {
            val next = when (val checked = recovery.check(DocumentSource(source))) {
                is Outcome.Success -> StartupRestoreStep.Previewing(checked.value)
                is Outcome.Failure -> StartupRestoreStep.Rejected(checked.error)
            }
            mutable.update { it.copy(restore = next) }
        }
    }

    private fun confirm() {
        val previewing = mutable.value.restore as? StartupRestoreStep.Previewing ?: return
        mutable.update { it.copy(restore = StartupRestoreStep.Restoring(previewing.backup)) }
        viewModelScope.launch {
            when (val ended = recovery.restore()) {
                // Nothing moved: the screen is as it was, with the reason said.
                StartupRestore.NotSetAside -> mutable.update {
                    it.copy(restore = StartupRestoreStep.Idle, notSetAside = true)
                }

                StartupRestore.Interrupted -> mutable.update {
                    it.copy(restore = StartupRestoreStep.Interrupted)
                }

                is StartupRestore.Restored -> mutable.update {
                    it.copy(restore = StartupRestoreStep.Restored(ended.outcome))
                }
            }
        }
    }

    /** Once a restore began, only its outcome's choices apply. */
    private fun afterRestore(action: StartupFailureAction) {
        val step = mutable.value.restore
        when (action) {
            StartupFailureAction.Leave -> if (step !is StartupRestoreStep.Restoring) {
                restart.restart()
            }

            StartupFailureAction.ApplyPreferences -> {
                val restored = step as? StartupRestoreStep.Restored ?: return
                if (restored.applying || restored.outcome.preferences.done) return
                mutable.update { it.copy(restore = restored.copy(applying = true)) }
                viewModelScope.launch {
                    val outcome = recovery.applyPending() ?: restored.outcome
                    mutable.update { it.copy(restore = StartupRestoreStep.Restored(outcome)) }
                }
            }

            else -> Unit
        }
    }

    /** Closes a preview or a rejection; the checked file is forgotten. */
    private fun leavePreview() {
        val step = mutable.value.restore
        if (step is StartupRestoreStep.Previewing || step is StartupRestoreStep.Rejected) {
            recovery.forget()
            mutable.update { it.copy(restore = StartupRestoreStep.Idle) }
        }
    }

    private fun warning(open: Boolean) {
        saved[KEY_CONFIRMING] = open
        saved[KEY_TYPED] = ""
        mutable.update { it.copy(confirming = open, typed = "", notSetAside = false) }
    }

    private companion object {
        const val KEY_CONFIRMING = "startup.confirming"
        const val KEY_TYPED = "startup.typed"
    }
}
