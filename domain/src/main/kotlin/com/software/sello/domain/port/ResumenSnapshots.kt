package com.software.sello.domain.port

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

typealias ResumenSnapshotState = ReadState<ResumenSnapshot, SnapshotFailure>

interface ResumenSnapshots {
    suspend fun read(month: YearMonth): Outcome<ResumenSnapshot, SnapshotFailure>

    fun observe(month: YearMonth): Flow<ResumenSnapshotState>
}
