package com.software.sello.domain.port

import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/** What a screen knows about a month at any moment. A failure is never shown as zeros. */
typealias SnapshotState = ReadState<MonthlySnapshot, SnapshotFailure>

interface MonthlySnapshots {
    /** One consistent snapshot of [month] as of the current financial day. */
    suspend fun read(month: YearMonth): Outcome<MonthlySnapshot, SnapshotFailure>

    /**
     * Starts with [ReadState.Loading], then a fresh state after every committed
     * change and every change of the financial day, real or simulated.
     */
    fun observe(month: YearMonth): Flow<SnapshotState>
}
