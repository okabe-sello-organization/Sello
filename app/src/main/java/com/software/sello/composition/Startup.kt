package com.software.sello.composition

import androidx.lifecycle.ViewModelProvider
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.feature.startup.StartupRecovery
import com.software.sello.feature.startup.StartupRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether the app has its data. No case shows the app without it. */
sealed interface StartupState {
    /** The data is being opened. Neither the app nor the failure screen is shown yet. */
    data object Opening : StartupState

    /** The data opened and the graph was built on it. */
    class Ready(val viewModels: ViewModelProvider.Factory) : StartupState

    /** The data could not be opened. No graph exists and nothing was written. */
    data class Failed(val failure: StorageFailure) : StartupState
}

/** A graph built on storage that opened: its view models, and restores into that storage. */
class Started(val viewModels: ViewModelProvider.Factory, val restores: Restores)

/** A chosen file that passed the whole-file check, held until it is restored or forgotten. */
class ChosenBackup(val bytes: ByteArray, val checked: CheckedBackup)

/**
 * Opens the data once for the process and keeps the result. [start] opens storage and
 * builds the graph on it, or says why it could not; a failure is a state to show, not
 * an exception. It is called again only while the start is failed, so a second store
 * is never opened beside one in use. [moveUnreadable] sets aside the file that would
 * not open and is likewise refused once the data has opened.
 *
 * A restore from the failure screen reads a backup with [readBackup] before anything
 * moves, then sets the data aside and opens the new store without showing it: its graph
 * is used only to restore, and the app starts again afterwards in a new process, which
 * reads the restored zone and history from the start. Once that store is open nothing
 * here opens or moves anything again.
 *
 * Creating it only begins the first open, in [scope]: the state is [StartupState.Opening]
 * until [start] answers, so the application's start never waits on the disk. [start] and
 * [moveUnreadable] do their file work off the main thread themselves. Opens, moves and
 * restores take turns, one at a time, and each runs to its end in [scope] even when the
 * caller goes away; the caller's cancellation is still rethrown to it.
 */
class Startup(
    private val scope: CoroutineScope,
    private val start: suspend () -> Outcome<Started, StorageFailure>,
    private val moveUnreadable: suspend () -> Outcome<Unit, StorageFailure>,
    private val readBackup: suspend (DocumentSource) -> Outcome<ChosenBackup, RestoreRejection>
) : StartupRecovery {
    private val mutable = MutableStateFlow<StartupState>(StartupState.Opening)
    val state: StateFlow<StartupState> = mutable.asStateFlow()

    private val turns = Mutex()

    private var chosen: ChosenBackup? = null

    /** The store a restore opened. It is never shown and never opened twice. */
    private var restoring: Started? = null

    init {
        scope.launch { turns.withLock { mutable.value = attempt() } }
    }

    /** A set-aside whose new start has not been asked for yet. */
    private var setAsidePending = false

    override suspend fun retry(): Boolean = inTurn {
        openAgain()
        mutable.value is StartupState.Ready
    }

    override suspend fun setAside(): Boolean = inTurn {
        val moved = mutable.value is StartupState.Failed &&
            restoring == null &&
            moveUnreadable() is Outcome.Success
        if (moved) setAsidePending = true
        moved
    }

    /**
     * After a set-aside, opens the empty data and then calls [relaunch], in [scope], so
     * a caller that goes away stops neither. It happens once per set-aside: asking again,
     * even after the empty data also failed to open, opens and relaunches nothing.
     */
    fun startOver(relaunch: () -> Unit) {
        scope.launch {
            val due = turns.withLock {
                setAsidePending.also {
                    setAsidePending = false
                    if (it) openAgain()
                }
            }
            if (due) relaunch()
        }
    }

    override suspend fun check(source: DocumentSource): Outcome<CheckedBackup, RestoreRejection> {
        chosen = null
        return when (val read = readBackup(source)) {
            is Outcome.Success -> {
                chosen = read.value
                Outcome.Success(read.value.checked)
            }

            is Outcome.Failure -> read
        }
    }

    override fun forget() {
        chosen = null
    }

    override suspend fun restore(): StartupRestore = inTurn {
        val backup = chosen
        if (backup == null || mutable.value !is StartupState.Failed || restoring != null) {
            return@inTurn StartupRestore.NotSetAside
        }
        if (moveUnreadable() !is Outcome.Success) return@inTurn StartupRestore.NotSetAside
        val started = when (val opened = start()) {
            is Outcome.Failure -> {
                mutable.value = StartupState.Failed(opened.error)
                return@inTurn StartupRestore.Interrupted
            }

            is Outcome.Success -> opened.value
        }
        restoring = started
        chosen = null
        val staged = when (val stage = started.restores.stage(backup.bytes)) {
            is Outcome.Success -> stage.value
            is Outcome.Failure -> return@inTurn StartupRestore.Interrupted
        }
        when (val outcome = started.restores.replace(staged)) {
            is RestoreOutcome.Restored -> StartupRestore.Restored(outcome)
            else -> StartupRestore.Interrupted
        }
    }

    override suspend fun applyPending(): RestoreOutcome.Restored? =
        restoring?.restores?.applyPending()

    /** Runs [step] after any step before it, to its end; a cancelled caller stops waiting. */
    private suspend fun <T> inTurn(step: suspend () -> T): T =
        scope.async { turns.withLock { step() } }.await()

    /** Opens again while the start is failed and no restore has opened its store. */
    private suspend fun openAgain() {
        if (mutable.value is StartupState.Failed && restoring == null) {
            mutable.value = StartupState.Opening
            mutable.value = attempt()
        }
    }

    private suspend fun attempt(): StartupState = when (val started = start()) {
        is Outcome.Success -> StartupState.Ready(started.value.viewModels)
        is Outcome.Failure -> StartupState.Failed(started.error)
    }
}
