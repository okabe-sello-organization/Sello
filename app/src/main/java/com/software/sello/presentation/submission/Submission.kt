package com.software.sello.presentation.submission

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.RecordIdSource

/** Asks storage whether an operation committed: its change, null when nothing was written. */
typealias Lookup<C> = suspend (OperationId) -> Outcome<C?, StorageFailure>

/**
 * One money write at a time, the way every form sends it (ARCHITECTURE §5–§6).
 *
 * The operation ID is made once per send and kept in [saved] under [key] before the
 * command leaves, with the [step] it was for, so a restart can tell this attempt apart
 * from a new one. A send while another is in flight sends nothing. An unknown outcome is
 * asked about with the same ID; only a found change counts as saved, and until the
 * question has an answer the operation stays saved: [pending] is true, and the form asks
 * again ([recover]) instead of sending anything new. Cancellation is rethrown and leaves
 * the operation saved, since it does not prove anything was rolled back.
 */
class Submission(
    private val saved: SavedStateHandle,
    private val key: String,
    private val ids: RecordIdSource
) {
    private val stepKey = "$key.step"
    private var inFlight = false

    /** An operation was sent and how it ended is not settled yet. */
    val pending: Boolean get() = saved.get<String>(key) != null

    /** What the [pending] operation was for, as the feature named it when sending. */
    val step: String? get() = saved[stepKey]

    /**
     * Sends [command] with a new operation ID saved first; null when another send or
     * lookup is still in flight, and nothing was sent.
     */
    suspend fun <C, F> send(
        step: String,
        find: Lookup<C>,
        command: suspend (OperationId) -> CommandOutcome<C, F>
    ): Settled<C, F>? {
        if (inFlight) return null
        inFlight = true
        try {
            val operation = (OperationId.of(ids.next()) as Outcome.Success).value
            saved[key] = operation.value
            saved[stepKey] = step
            return when (val outcome = command(operation)) {
                is CommandOutcome.Committed -> {
                    clear()
                    Settled.Committed(outcome.value, found = false)
                }

                is CommandOutcome.Rejected -> {
                    // Certain: nothing was written, so this attempt is over.
                    clear()
                    Settled.Rejected(outcome.reason)
                }

                is CommandOutcome.OutcomeUnknown -> lookUp(operation, find)
            }
        } finally {
            inFlight = false
        }
    }

    /**
     * Asks how the saved operation ended, with its own ID, after an unknown outcome or a
     * restart. Null when nothing is [pending] or something is already in flight.
     */
    suspend fun <C> recover(find: Lookup<C>): Settled<C, Nothing>? {
        if (inFlight || !pending) return null
        val operation = (saved.get<String>(key)?.let(OperationId::of) as? Outcome.Success)?.value
        // Not an ID this class wrote; there is nothing it could be asked about.
        if (operation == null) return clear().let { null }
        inFlight = true
        try {
            return lookUp(operation, find)
        } finally {
            inFlight = false
        }
    }

    /** Forgets the operation: the feature settled it some other way. */
    fun clear() {
        saved[key] = null
        saved[stepKey] = null
    }

    private suspend fun <C> lookUp(operation: OperationId, find: Lookup<C>): Settled<C, Nothing> =
        when (val found = find(operation)) {
            is Outcome.Failure -> Settled.Unknown(found.error)

            is Outcome.Success -> {
                clear()
                found.value?.let { Settled.Committed(it, found = true) } ?: Settled.NotCommitted
            }
        }
}

/** How a [Submission] ended, for the feature to show. */
sealed interface Settled<out C, out F> {
    /**
     * Saved. [found] is true when it was learned by asking after an unknown outcome or a
     * restart: the change itself was not seen, so it is never celebrated.
     */
    data class Committed<out C>(val value: C, val found: Boolean) : Settled<C, Nothing>

    /** Refused: nothing was written. */
    data class Rejected<out F>(val reason: CommandRejection<F>) : Settled<Nothing, F>

    /** Asked with its own ID: nothing was written. */
    data object NotCommitted : Settled<Nothing, Nothing>

    /** Still not known; the operation stays [Submission.pending] for the next ask. */
    data class Unknown(val failure: StorageFailure) : Settled<Nothing, Nothing>
}
