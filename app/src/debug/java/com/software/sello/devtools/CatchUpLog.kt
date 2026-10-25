package com.software.sello.devtools

import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.ScheduleCatchUpStore
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One batch of catch-up as storage answered it. [number] counts this process's batches from 1. */
sealed interface LoggedBatch {
    val number: Long

    /** [count] occurrences committed, dated [first] to [last]. */
    data class Recorded(
        override val number: Long,
        val count: Int,
        val first: LocalDate,
        val last: LocalDate
    ) : LoggedBatch

    /** Certainly nothing written; [failure] names the storage failure. */
    data class Failed(override val number: Long, val failure: String) : LoggedBatch

    /** The commit's outcome is not known. */
    data class Unknown(override val number: Long) : LoggedBatch
}

/**
 * The catch-up batches of this process, newest last, whoever ran them: Sello on opening
 * or at a day change, or a scenario step. A run that found nothing due is not a batch.
 * It records; it never decides or changes an answer.
 */
class CatchUpLog(private val limit: Int = 50) {
    private val entries = MutableStateFlow<List<LoggedBatch>>(emptyList())
    val state: StateFlow<List<LoggedBatch>> = entries.asStateFlow()
    private var counted = 0L

    fun record(batch: CatchUpBatch) {
        val entry = synchronized(this) {
            val number = counted + 1
            when (batch) {
                is CatchUpBatch.Recorded -> batch.occurrences.takeIf { it.isNotEmpty() }?.let {
                    LoggedBatch.Recorded(
                        number,
                        it.size,
                        it.first().origin.occurrence.date,
                        it.last().origin.occurrence.date
                    )
                }

                is CatchUpBatch.Failed ->
                    LoggedBatch.Failed(number, batch.failure::class.simpleName ?: "StorageFailure")

                CatchUpBatch.Unknown -> LoggedBatch.Unknown(number)
            }?.also { counted = number }
        } ?: return
        entries.update { (it + entry).takeLast(limit) }
    }
}

/** Catch-up exactly as [store] does it, each batch's answer also kept in [log]. */
class LoggedScheduleCatchUp(private val store: ScheduleCatchUpStore, private val log: CatchUpLog) :
    ScheduleCatchUpStore {
    override suspend fun recordDue(today: LocalDate, limit: Int): CatchUpBatch =
        store.recordDue(today, limit).also(log::record)
}
