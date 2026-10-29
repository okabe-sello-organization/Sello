package com.software.sello.feature.startup

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection

/**
 * What the person can do when their data could not be opened at start. Implemented
 * by the composition root; nothing here opens or moves a file itself.
 */
interface StartupRecovery {
    /** Opens the data again. True when it opened and the app can be shown. */
    suspend fun retry(): Boolean

    /**
     * Moves the data that could not be opened out of the way, keeping it, so the next
     * start is an empty one. True when it was moved. Never touches data that opened.
     */
    suspend fun setAside(): Boolean

    /** Reads and checks a backup the person chose, whole. Nothing is opened, moved or written. */
    suspend fun check(source: DocumentSource): Outcome<CheckedBackup, RestoreRejection>

    /** Forgets the backup last checked. */
    fun forget()

    /**
     * Sets aside the data that could not be opened, as [setAside] does, opens a new store
     * and fills it from the backup last checked. Once begun it runs to the end even if
     * the screen goes away; afterwards the app must start again.
     */
    suspend fun restore(): StartupRestore

    /** Applies the restored backup's preferences that are still pending, and only those. */
    suspend fun applyPending(): RestoreOutcome.Restored?
}

/** How a restore from this screen ended. */
sealed interface StartupRestore {
    /** The data could not be set aside, so nothing was moved, opened or restored. */
    data object NotSetAside : StartupRestore

    /**
     * The data was set aside, but the backup was not restored, or it is not known
     * whether it was. A restart shows which; Respaldo can restore it again.
     */
    data object Interrupted : StartupRestore

    /** The new store holds the backup. Preferences not applied yet are named in [outcome]. */
    data class Restored(val outcome: RestoreOutcome.Restored) : StartupRestore
}

/** Restoring from this screen. Only [Restored] means the backup is in the new store. */
sealed interface StartupRestoreStep {
    data object Idle : StartupRestoreStep

    /** Reading and checking a chosen file. */
    data object Checking : StartupRestoreStep

    data class Rejected(val reason: RestoreRejection) : StartupRestoreStep

    /** Waiting for the person to confirm. */
    data class Previewing(val backup: CheckedBackup) : StartupRestoreStep

    data class Restoring(val backup: CheckedBackup) : StartupRestoreStep

    data object Interrupted : StartupRestoreStep

    data class Restored(val outcome: RestoreOutcome.Restored, val applying: Boolean = false) :
        StartupRestoreStep
}

/**
 * The screen shown instead of the app when the data could not be opened. It holds no
 * figure from that data and no reason: the failure does not say whether the file is
 * damaged or was written by a newer version, so the screen does not guess. The only
 * counts it shows are a chosen backup's.
 */
data class StartupFailureState(
    /** Trying again was asked for and the data still did not open. */
    val stillFailing: Boolean = false,
    /** Trying again or setting the data aside is under way; nothing else starts meanwhile. */
    val busy: Boolean = false,
    /** The warning before starting from zero is open. */
    val confirming: Boolean = false,
    /** Exactly what was typed to confirm. */
    val typed: String = "",
    /** Starting from zero was confirmed and the data could not be moved. Nothing changed. */
    val notSetAside: Boolean = false,
    /** The data was moved; the app must now start again from nothing. */
    val startedOver: Boolean = false,
    /** Restoring a backup instead of starting from nothing. */
    val restore: StartupRestoreStep = StartupRestoreStep.Idle,
    /** The system's open dialog should be shown. */
    val pickerRequest: Boolean = false
) {
    val canConfirm: Boolean get() = typed.trim() == CONFIRM_WORD

    /**
     * A restore began: the data may already be set aside, so trying again, starting
     * over and choosing another file no longer apply, and every way out restarts.
     */
    val restoreBegun: Boolean
        get() = restore is StartupRestoreStep.Restoring ||
            restore is StartupRestoreStep.Restored ||
            restore == StartupRestoreStep.Interrupted

    /** A file is being chosen or checked. */
    val choosing: Boolean get() = pickerRequest || restore == StartupRestoreStep.Checking

    companion object {
        /** The word D09 requires before financial data is replaced by nothing. */
        const val CONFIRM_WORD = "BORRAR"
    }
}

sealed interface StartupFailureAction {
    data object Retry : StartupFailureAction

    data object StartOver : StartupFailureAction

    data class Typed(val text: String) : StartupFailureAction

    data object Confirm : StartupFailureAction

    data object Cancel : StartupFailureAction

    /** Restaurar un respaldo: asks for the system's open dialog. */
    data object RestoreBackup : StartupFailureAction

    data object PickerOpened : StartupFailureAction

    /** The open dialog's answer: a document, or null when it was dismissed. */
    data class Chosen(val source: String?) : StartupFailureAction

    data object ConfirmRestore : StartupFailureAction

    data object CancelRestore : StartupFailureAction

    data object ApplyPreferences : StartupFailureAction

    /** Ir al recibo or Continuar, or Back, once a restore began: the app starts again. */
    data object Leave : StartupFailureAction
}
