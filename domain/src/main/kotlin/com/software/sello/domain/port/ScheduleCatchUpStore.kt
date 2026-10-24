package com.software.sello.domain.port

import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.StorageFailure
import java.time.LocalDate

/** One occurrence catch-up recorded, and the expense or income it became (rule 4). */
sealed interface RecordedOccurrence {
    val origin: EntryOrigin

    data class Payment(override val origin: EntryOrigin, val expenseId: ExpenseId) :
        RecordedOccurrence

    data class Income(override val origin: EntryOrigin, val incomeId: IncomeId) :
        RecordedOccurrence
}

/** How one batch of catch-up ended. */
sealed interface CatchUpBatch {
    /** Committed, oldest first; empty when nothing was due, and then nothing was written. */
    data class Recorded(val occurrences: List<RecordedOccurrence>) : CatchUpBatch

    /** Certainly nothing was written. */
    data class Failed(val failure: StorageFailure) : CatchUpBatch

    /** The commit's outcome is not known. The next run reads the checkpoints again. */
    data object Unknown : CatchUpBatch
}

/**
 * Records what schedules have due, one batch at a time (docs/testing/recurrence-examples.md,
 * rules 4 and 6). Each batch is one transaction: the entries, each schedule's checkpoint,
 * one revision and one receipt. An occurrence at or before a checkpoint is never recorded
 * again, so running a batch twice records nothing twice.
 */
fun interface ScheduleCatchUpStore {
    /** Up to [limit] occurrences due on [today] or earlier, by date, then schedule, then slot. */
    suspend fun recordDue(today: LocalDate, limit: Int): CatchUpBatch
}
