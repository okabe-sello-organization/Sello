package com.software.sello.feature.backup

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.DocumentRead
import com.software.sello.domain.port.DocumentReader
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedLookup
import com.software.sello.domain.port.StagedRestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a restore stands. Only [Restored] means the history was replaced. */
sealed interface RestoreStep {
    data object Idle : RestoreStep

    /** Reading and checking a chosen file. */
    data object Checking : RestoreStep

    data class Rejected(val reason: RestoreRejection) : RestoreStep

    /**
     * Waiting for the person to confirm. [refreshed]: the history changed and this is a
     * new count. [notReplaced]: it was confirmed before, and a check after an unknown
     * outcome or a look after a restart found it did not happen.
     */
    data class Previewing(
        val staged: StagedRestore,
        val refreshed: Boolean = false,
        val notReplaced: Boolean = false
    ) : RestoreStep

    data class Replacing(val staged: StagedRestore) : RestoreStep

    /** Nothing was replaced. */
    data object Failed : RestoreStep

    /**
     * The replacement under [operationId] may have committed. Checking reads its receipt
     * again, never replaces; [checking] while that runs. [chosenSetAside]: a file chosen
     * meanwhile was not checked, because this one had to be settled first.
     */
    data class Unknown(
        val operationId: OperationId,
        val checking: Boolean = false,
        val chosenSetAside: Boolean = false
    ) : RestoreStep

    /**
     * Replaced. Preferences that are not done yet are named in [outcome]. [chosenSetAside]:
     * a file chosen meanwhile was not checked, because this one had to be finished first.
     */
    data class Restored(
        val outcome: RestoreOutcome.Restored,
        val applying: Boolean = false,
        val chosenSetAside: Boolean = false
    ) : RestoreStep
}

/**
 * One restore at a time, for as long as the process lives, so leaving the screen
 * never cuts a replacement or a preference write short. Created with the graph: a
 * replacement left with preferences pending by an earlier process is resumed then.
 * Nothing else here reads or writes the restore until that is over, and screens
 * opening together look in turn, so a look never finds another one's work half done.
 */
class RestoreSession(
    private val restores: Restores,
    private val reader: DocumentReader,
    private val scope: CoroutineScope,
    private val maxBytes: Int
) {
    private val state = MutableStateFlow<RestoreStep>(RestoreStep.Idle)
    val step: StateFlow<RestoreStep> = state.asStateFlow()

    private val startUp: Job = scope.launch {
        // Finished quietly if it can be; only preferences still failing are shown.
        restores.applyPending()?.takeUnless { it.preferences.done }?.let {
            state.compareAndSet(RestoreStep.Idle, RestoreStep.Restored(it))
        }
    }

    private val resuming = Mutex()

    /**
     * On opening the screen: a replacement still pending, one an earlier process died
     * in the middle of (restored, or not known yet), or a staged file still waiting,
     * saying it was not replaced if it had been confirmed; a confirmed file that can no
     * longer be offered is shown as not restored.
     * Failing all, [chosen], a file an earlier process was given but had not finished
     * with, is checked again; it is never replaced without a confirmation. A staged file
     * still waiting is not previewed then: the person chose another since.
     */
    fun resume(chosen: DocumentSource? = null) {
        if (state.value != RestoreStep.Idle) return
        scope.launch {
            startUp.join()
            resuming.withLock {
                // Another screen's look may have found it first.
                if (state.value != RestoreStep.Idle) return@withLock
                val next = look(chosen)
                when {
                    next != null -> state.compareAndSet(RestoreStep.Idle, next)
                    chosen != null -> choose(chosen)
                }
            }
        }
    }

    /** What [resume] finds, called with [resuming] held; null when there is nothing to show. */
    private suspend fun look(chosen: DocumentSource?): RestoreStep? =
        restores.pending()?.let { RestoreStep.Restored(it) }
            ?: when (val found = restores.staged()) {
                is StagedLookup.Waiting -> RestoreStep.Previewing(
                    found.staged,
                    notReplaced = found.attempted
                ).takeIf { chosen == null }

                is StagedLookup.Replaced -> replaced(found.operationId)

                is StagedLookup.Unknown -> RestoreStep.Unknown(found.operationId)

                // A confirmed file that can no longer be offered: say it did not
                // happen, unless a file chosen since is still to be checked.
                is StagedLookup.NotReplaced ->
                    RestoreStep.Failed.takeIf { found.attempted && chosen == null }

                StagedLookup.None -> null
            }

    /**
     * Stages [source] in turn with the looks. A replacement still pending, or a file
     * staged earlier that may have replaced the history, comes first: it is shown as it
     * is found, restored or not known yet, saying the chosen file was not checked. Found
     * not replaced, the chosen file is staged once more; the earlier file is never
     * previewed in answer to this choice.
     */
    fun choose(source: DocumentSource) = begin(RestoreStep.Checking, from = ::choosable) {
        when (val read = reader.read(source, maxBytes)) {
            DocumentRead.Failed -> RestoreStep.Rejected(RestoreRejection.Unreadable)

            is DocumentRead.TooLarge -> RestoreStep.Rejected(RestoreRejection.TooLarge)

            is DocumentRead.Read -> resuming.withLock {
                // A look that found it may have lost its turn to this choice.
                restores.pending()?.let { RestoreStep.Restored(it, chosenSetAside = true) }
                    ?: stage(read.bytes, again = true)
            }
        }
    }

    /** Called with [resuming] held. */
    private suspend fun stage(bytes: ByteArray, again: Boolean): RestoreStep =
        when (val staged = restores.stage(bytes)) {
            is Outcome.Success -> RestoreStep.Previewing(staged.value)

            is Outcome.Failure -> when (val failure = staged.error) {
                is StageFailure.Rejected -> RestoreStep.Rejected(failure.reason)

                StageFailure.Storage -> RestoreStep.Failed

                StageFailure.Unsettled -> when (val earlier = look(chosen = null)) {
                    is RestoreStep.Restored -> earlier.copy(chosenSetAside = true)
                    is RestoreStep.Unknown -> earlier.copy(chosenSetAside = true)
                    else -> if (again) stage(bytes, again = false) else RestoreStep.Failed
                }
            }
        }

    fun confirm() {
        val staged = (state.value as? RestoreStep.Previewing)?.staged ?: return
        begin(RestoreStep.Replacing(staged), from = { it is RestoreStep.Previewing }) {
            when (val outcome = restores.replace(staged)) {
                is RestoreOutcome.Restored -> RestoreStep.Restored(outcome)

                RestoreOutcome.Failed -> RestoreStep.Failed

                is RestoreOutcome.OutcomeUnknown -> RestoreStep.Unknown(outcome.operationId)

                RestoreOutcome.Stale -> when (val found = restores.staged()) {
                    is StagedLookup.Waiting -> RestoreStep.Previewing(
                        found.staged,
                        refreshed = true
                    )

                    else -> RestoreStep.Failed
                }
            }
        }
    }

    /**
     * After an unknown outcome, reads again what became of it: restored, or not, and
     * then its file is previewed to confirm again if it still can be; it is never
     * replaced from here. Still unknown, it says so again. Cancelled, it stays unknown
     * and can be checked again.
     */
    fun check() {
        val current = state.value as? RestoreStep.Unknown ?: return
        val checking = current.copy(checking = true)
        if (current.checking || !state.compareAndSet(current, checking)) return
        scope.launch {
            try {
                startUp.join()
                val next = checked(current.operationId)
                state.compareAndSet(
                    checking,
                    if (current.chosenSetAside) next.withChosenSetAside() else next
                )
            } finally {
                state.compareAndSet(checking, current)
            }
        }
    }

    /**
     * A pending replacement can only be this one: no other file is staged while one is.
     * Otherwise only what is read about [operation] itself answers it; nothing left to
     * read, or another operation, leaves it unknown rather than look like nothing happened.
     */
    private suspend fun checked(operation: OperationId): RestoreStep {
        restores.pending()?.let { return RestoreStep.Restored(it) }
        val found = restores.staged()
        if (found.operationId() != operation) return RestoreStep.Unknown(operation)
        return when (found) {
            is StagedLookup.Waiting -> RestoreStep.Previewing(found.staged, notReplaced = true)
            is StagedLookup.Replaced -> replaced(operation)
            is StagedLookup.NotReplaced -> RestoreStep.Failed
            is StagedLookup.Unknown, StagedLookup.None -> RestoreStep.Unknown(operation)
        }
    }

    /**
     * Still about the earlier file, after a file chosen meanwhile was set aside: it keeps
     * saying so, and it is never previewed, which could pass for the chosen file.
     */
    private fun RestoreStep.withChosenSetAside(): RestoreStep = when (this) {
        is RestoreStep.Restored -> copy(chosenSetAside = true)
        is RestoreStep.Unknown -> copy(chosenSetAside = true)
        is RestoreStep.Previewing -> RestoreStep.Failed
        else -> this
    }

    private fun StagedLookup.operationId(): OperationId? = when (this) {
        StagedLookup.None -> null
        is StagedLookup.Waiting -> staged.operationId
        is StagedLookup.NotReplaced -> operationId
        is StagedLookup.Replaced -> operationId
        is StagedLookup.Unknown -> operationId
    }

    /** Committed before a crash: its preferences are applied now and it is shown restored. */
    private suspend fun replaced(operation: OperationId): RestoreStep =
        restores.applyPending()?.let { RestoreStep.Restored(it) } ?: RestoreStep.Unknown(operation)

    /** Cancels a preview: the file is forgotten and nothing changes. */
    fun cancel() = begin(RestoreStep.Idle, from = { it is RestoreStep.Previewing }) {
        restores.discard()
        RestoreStep.Idle
    }

    fun applyPreferences() {
        val current = state.value as? RestoreStep.Restored ?: return
        if (current.applying || current.outcome.preferences.done) return
        val applying = current.copy(applying = true)
        state.value = applying
        afterStartUp(busy = applying, before = current) {
            val outcome = restores.applyPending() ?: current.outcome
            state.value = RestoreStep.Restored(outcome, chosenSetAside = current.chosenSetAside)
        }
    }

    /** The person is leaving a replaced history; the next screen starts from the new one. */
    fun finish() {
        state.update {
            if (it is RestoreStep.Restored || it is RestoreStep.Unknown) RestoreStep.Idle else it
        }
    }

    /** Leaves a rejection or failure, back to choosing a file. */
    fun dismiss() {
        state.update {
            if (it is RestoreStep.Rejected || it == RestoreStep.Failed) RestoreStep.Idle else it
        }
    }

    /** A file chosen while another is previewed takes its place, as staging allows. */
    private fun choosable(step: RestoreStep) =
        step == RestoreStep.Idle || step is RestoreStep.Rejected || step == RestoreStep.Failed ||
            step is RestoreStep.Previewing

    /** Moves to [busy] only from a state [from] accepts, then runs [work] and shows its result. */
    private fun begin(
        busy: RestoreStep,
        from: (RestoreStep) -> Boolean,
        work: suspend () -> RestoreStep
    ) {
        var before: RestoreStep? = null
        state.update { current ->
            if (from(current)) {
                before = current
                busy
            } else {
                before = null
                current
            }
        }
        before?.let { afterStartUp(busy, before = it) { state.value = work() } }
    }

    /**
     * Runs [work] once the start-up apply is over, cancelled or not. Cancelled while it
     * waits, the [busy] step shown meanwhile goes back to [before].
     */
    private fun afterStartUp(busy: RestoreStep, before: RestoreStep, work: suspend () -> Unit) {
        scope.launch {
            try {
                startUp.join()
            } catch (cancelled: CancellationException) {
                state.compareAndSet(busy, before)
                throw cancelled
            }
            work()
        }
    }
}
