package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission

/**
 * Deleting expenses from a list of one category's month (its detail, every expense of it),
 * the way the expense form deletes one (SELLO-149). The operation is saved under the list
 * it was asked from, its [place], before it is sent, so an unknown outcome is asked about
 * with its own ID, after a restart too, and never sent again as a new one. While one is
 * unresolved that list sends no other deletion; another list is never held up by it.
 */
internal class ExpenseRowDeletions(
    private val saved: SavedStateHandle,
    private val key: String,
    private val commands: ExpenseCommands,
    private val ids: RecordIdSource,
    private val corrections: Corrections
) {
    private val submissions = HashMap<String, Submission>()

    /** Places whose deletion is being sent, or asked about, right now. */
    private val flying = HashSet<String>()

    private fun submission(place: String) =
        submissions.getOrPut(place) { Submission(saved, "$key.$place", ids) }

    /** A deletion of [place] is being sent or asked about: its own answer will be said. */
    fun busy(place: String): Boolean = place in flying

    /**
     * A deletion asked from [place] whose outcome is still not known, and not being sent or
     * asked about now: an operation in flight is not yet unknown.
     */
    fun unresolved(place: String): Boolean = !busy(place) && submission(place).pending

    /**
     * Deletes [expense] as it was listed. Null when nothing was sent: another deletion of
     * [place] is in flight or unresolved, or one of [expense] confirmed after its list is
     * still unresolved (the bar asks about that one instead).
     */
    suspend fun delete(
        place: String,
        expense: ExpenseId,
        version: Long,
        generation: Long
    ): RowDeletion? {
        if (busy(place) || corrections.holdsDeletionOf(expense)) return null
        return flown(place) {
            submission(place).send(commands::find) { operation ->
                corrections.deleteExpense(DeleteExpense(operation, generation, expense, version))
            }
        }
    }

    /** Asks again, with its own ID, how the unresolved deletion of [place] ended. */
    suspend fun check(place: String): RowDeletion? {
        if (busy(place)) return null
        return flown(place) { submission(place).recover(commands::find) }
    }

    /**
     * Runs [step] with [place] marked as busy. Cancelled, it leaves the operation saved, so
     * it is asked about the next time the place is shown.
     */
    private suspend fun flown(
        place: String,
        step: suspend () -> Settled<CommittedExpense, ExpenseRejection>?
    ): RowDeletion? {
        flying += place
        try {
            return ended(step())
        } finally {
            flying -= place
        }
    }

    private fun ended(settled: Settled<CommittedExpense, ExpenseRejection>?): RowDeletion? =
        when (settled) {
            null -> null

            // One sent and committed was offered to undo by [corrections]; one found after
            // an unknown outcome is offered now, while its time to undo lasts.
            is Settled.Committed -> {
                if (settled.found) corrections.expenseDeleted(settled.value.receipt)
                RowDeletion.Deleted
            }

            is Settled.Unknown -> RowDeletion.NotDeleted(DeleteProblem.Unknown)

            // Asked with its own ID: that deletion did not happen, and it can be asked again.
            Settled.NotCommitted -> RowDeletion.NotDeleted(DeleteProblem.NotDone)

            is Settled.Rejected -> when (val reason = settled.reason) {
                is CommandRejection.VersionConflict -> RowDeletion.NotDeleted(DeleteProblem.Changed)

                is CommandRejection.StaleGeneration ->
                    RowDeletion.NotDeleted(DeleteProblem.DataChanged)

                is CommandRejection.Specific -> when (reason.reason) {
                    ExpenseRejection.ExpenseMissing -> RowDeletion.NotDeleted(DeleteProblem.Gone)

                    // The list of later months is shown above every screen (CP20).
                    is ExpenseRejection.CorrectionUnconfirmed -> RowDeletion.Asked

                    else -> RowDeletion.NotDeleted(DeleteProblem.Failed)
                }

                else -> RowDeletion.NotDeleted(DeleteProblem.Failed)
            }
        }
}

/** How a deletion asked from a list ended. */
internal sealed interface RowDeletion {
    data object Deleted : RowDeletion

    /** It changes later months: their list is asked first and nothing was deleted yet. */
    data object Asked : RowDeletion

    data class NotDeleted(val problem: DeleteProblem) : RowDeletion
}
