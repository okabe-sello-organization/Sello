package com.software.sello.feature.recurrence

import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.usecase.CatchUpNow
import com.software.sello.domain.usecase.CatchUpResult
import com.software.sello.domain.usecase.CatchUpSchedules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where catch-up stands. [Finished] holds the last run's result. */
sealed interface CatchUpState {
    data object Idle : CatchUpState

    data object Running : CatchUpState

    data class Finished(val result: CatchUpResult) : CatchUpState
}

/**
 * Runs catch-up while Sello runs, never in the background (docs/testing/recurrence-
 * examples.md, rule 6): when it starts, each time the financial day changes, including
 * at midnight and on return to the foreground, and after every committed change, such
 * as a schedule saved, changed or resumed. One run at a time; a trigger that arrives
 * during a run starts one more run after it, not one per trigger. Created with the graph.
 */
class CatchUpRunner(
    private val catchUp: CatchUpSchedules,
    clock: FinancialClock,
    reads: ScheduleReads,
    scope: CoroutineScope
) : CatchUpNow {
    private val lock = Mutex()
    private val mutable = MutableStateFlow<CatchUpState>(CatchUpState.Idle)
    val state: StateFlow<CatchUpState> = mutable.asStateFlow()

    init {
        scope.launch {
            val revisions = reads.observe()
                .filterIsInstance<ScheduleBookState.Ready>()
                .map { it.book.generation to it.book.revision }
            combine(clock.today, revisions) { day, revision -> day to revision }
                .conflate()
                .collect { run() }
        }
    }

    /** One catch-up now, after any that is running. */
    override suspend fun run(): CatchUpResult = lock.withLock {
        mutable.value = CatchUpState.Running
        catchUp.run().also { mutable.value = CatchUpState.Finished(it) }
    }
}
