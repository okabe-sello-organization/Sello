package com.software.sello.domain.port

import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.YearSnapshot
import kotlinx.coroutines.flow.Flow

typealias YearSnapshotState = ReadState<YearSnapshot, SnapshotFailure>

/** Tu año: one calendar year, read again after every committed change and each new day. */
interface YearSnapshots {
    fun observe(year: Int): Flow<YearSnapshotState>
}
