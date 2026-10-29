package com.software.sello.feature.backup

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.DocumentRead
import com.software.sello.domain.port.DocumentReader
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedRestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a restore stands. Only [Restored] means the history was replaced. */
sealed interface RestoreStep {
    data object Idle : RestoreStep

    /** Reading and checking a chosen file. */
    data object Checking : RestoreStep

    data class Rejected(val reason: RestoreRejection) : RestoreStep

    /** Waiting for the person to confirm. [refreshed]: the history changed and this is a new count. */
    data class Previewing(val staged: StagedRestore, val refreshed: Boolean = false) : RestoreStep

    data class Replacing(val staged: StagedRestore) : RestoreStep

    /** Nothing was replaced. */
    data object Failed : RestoreStep

    /** The replacement may have committed; asking again with the same staging finds out. */
    data class Unknown(val staged: StagedRestore) : RestoreStep

    /** Replaced. Preferences that are not done yet are named in [outcome]. */
    data class Restored(val outcome: RestoreOutcome.Restored, val applying: Boolean = false) :
        RestoreStep
}

/**
 * One restore at a time, for as long as the process lives, so leaving the screen
 * never cuts a replacement or a preference write short. Created with the graph: a
 * replacement left with preferences pending by an earlier process is resumed then.
 */
class RestoreSession(
    private val restores: Restores,
    private val reader: DocumentReader,
    private val scope: CoroutineScope,
    private val maxBytes: Int
) {
    private val state = MutableStateFlow<RestoreStep>(RestoreStep.Idle)
    val step: StateFlow<RestoreStep> = state.asStateFlow()

    init {
        scope.launch {
            // Finished quietly if it can be; only preferences still failing are shown.
            restores.applyPending()?.takeUnless { it.preferences.done }?.let {
                state.compareAndSet(RestoreStep.Idle, RestoreStep.Restored(it))
            }
        }
    }

    /**
     * On opening the screen: a replacement still pending, or a staged file still
     * waiting. Failing both, [chosen], a file an earlier process was given but had not
     * finished with, is checked again; it is never replaced without a confirmation.
     */
    fun resume(chosen: DocumentSource? = null) {
        if (state.value != RestoreStep.Idle) return
        scope.launch {
            val pending = restores.pending()
            val next = when {
                pending != null -> RestoreStep.Restored(pending)
                else -> restores.staged()?.let { RestoreStep.Previewing(it) }
            }
            when {
                next != null -> state.compareAndSet(RestoreStep.Idle, next)
                chosen != null -> choose(chosen)
            }
        }
    }

    fun choose(source: DocumentSource) = begin(RestoreStep.Checking, from = ::choosable) {
        when (val read = reader.read(source, maxBytes)) {
            DocumentRead.Failed -> RestoreStep.Rejected(RestoreRejection.Unreadable)

            is DocumentRead.TooLarge -> RestoreStep.Rejected(RestoreRejection.TooLarge)

            is DocumentRead.Read -> when (val staged = restores.stage(read.bytes)) {
                is Outcome.Success -> RestoreStep.Previewing(staged.value)

                is Outcome.Failure -> when (val failure = staged.error) {
                    is StageFailure.Rejected -> RestoreStep.Rejected(failure.reason)
                    StageFailure.Storage -> RestoreStep.Failed
                }
            }
        }
    }

    fun confirm() {
        val staged = when (val current = state.value) {
            is RestoreStep.Previewing -> current.staged
            is RestoreStep.Unknown -> current.staged
            else -> return
        }
        val confirmable = { step: RestoreStep ->
            step is RestoreStep.Previewing || step is RestoreStep.Unknown
        }
        begin(RestoreStep.Replacing(staged), from = confirmable) {
            when (val outcome = restores.replace(staged)) {
                is RestoreOutcome.Restored -> RestoreStep.Restored(outcome)

                RestoreOutcome.Failed -> RestoreStep.Failed

                is RestoreOutcome.OutcomeUnknown -> RestoreStep.Unknown(staged)

                RestoreOutcome.Stale ->
                    restores.staged()?.let { RestoreStep.Previewing(it, refreshed = true) }
                        ?: RestoreStep.Failed
            }
        }
    }

    /** Cancels a preview: the file is forgotten and nothing changes. */
    fun cancel() = begin(RestoreStep.Idle, from = { it is RestoreStep.Previewing }) {
        restores.discard()
        RestoreStep.Idle
    }

    fun applyPreferences() {
        val current = state.value as? RestoreStep.Restored ?: return
        if (current.applying || current.outcome.preferences.done) return
        state.value = current.copy(applying = true)
        scope.launch {
            val outcome = restores.applyPending() ?: current.outcome
            state.value = RestoreStep.Restored(outcome)
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

    private fun choosable(step: RestoreStep) =
        step == RestoreStep.Idle || step is RestoreStep.Rejected || step == RestoreStep.Failed

    /** Moves to [busy] only from a state [from] accepts, then runs [work] and shows its result. */
    private fun begin(
        busy: RestoreStep,
        from: (RestoreStep) -> Boolean,
        work: suspend () -> RestoreStep
    ) {
        var started = false
        state.update { current ->
            if (from(current)) {
                started = true
                busy
            } else {
                current
            }
        }
        if (started) scope.launch { state.value = work() }
    }
}
