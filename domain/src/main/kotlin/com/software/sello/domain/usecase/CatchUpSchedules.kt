package com.software.sello.domain.usecase

import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ScheduleCatchUpStore

/** Why a catch-up stopped before everything due was recorded. */
sealed interface CatchUpStop {
    data class Failed(val failure: StorageFailure) : CatchUpStop

    /** A batch may or may not have committed; the next run finds out from the checkpoints. */
    data object Unknown : CatchUpStop

    /** The history was restored or erased since this process started: nothing until it restarts. */
    data object Stale : CatchUpStop
}

/** What one catch-up recorded, oldest first, and why it stopped early, if it did. */
data class CatchUpResult(val recorded: List<RecordedOccurrence>, val stopped: CatchUpStop?)

/** Catch-up run now by something that needs it done first, after any run under way. */
fun interface CatchUpNow {
    suspend fun run(): CatchUpResult
}

/**
 * Records every occurrence due up to today, with no age limit, in batches of [BATCH]
 * (rule 6). Each committed batch stays committed whatever happens to the next, and
 * [onRecorded] hears of it as soon as it commits. Today is read once: a day that turns
 * during the run is the next run's.
 *
 * [generation] is the history's when this process started and read its financial zone,
 * the zone [clock] counts days in. Once a restore or an erase replaces that history,
 * possibly with another zone, nothing is recorded until a new process reads it again.
 */
class CatchUpSchedules(
    private val store: ScheduleCatchUpStore,
    private val clock: FinancialClock,
    private val generation: Long
) {
    suspend fun run(onRecorded: (List<RecordedOccurrence>) -> Unit = {}): CatchUpResult {
        val today = clock.today.value.date
        val recorded = mutableListOf<RecordedOccurrence>()
        while (true) {
            when (val batch = store.recordDue(generation, today, BATCH)) {
                is CatchUpBatch.Recorded -> {
                    recorded += batch.occurrences
                    if (batch.occurrences.isNotEmpty()) onRecorded(batch.occurrences)
                    if (batch.occurrences.size < BATCH) return CatchUpResult(recorded, null)
                }

                is CatchUpBatch.Failed ->
                    return CatchUpResult(recorded, CatchUpStop.Failed(batch.failure))

                CatchUpBatch.Unknown -> return CatchUpResult(recorded, CatchUpStop.Unknown)

                CatchUpBatch.Stale -> return CatchUpResult(recorded, CatchUpStop.Stale)
            }
        }
    }

    companion object {
        /** At most this many occurrences in one transaction. */
        const val BATCH = 50
    }
}
