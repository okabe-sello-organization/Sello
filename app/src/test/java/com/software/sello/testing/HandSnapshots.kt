package com.software.sello.testing

import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

/** A snapshot source a test drives; it records which months were observed. */
open class HandSnapshots : MonthlySnapshots {
    val observed = mutableListOf<YearMonth>()
    private val states = mutableMapOf<YearMonth, MutableStateFlow<SnapshotState>>()

    fun of(month: YearMonth) = states.getOrPut(month) { MutableStateFlow(ReadState.Loading) }

    fun ready(snapshot: MonthlySnapshot) {
        of(snapshot.month).value = ReadState.Ready(snapshot)
    }

    /** One read: the month as it was last published, or a failure when it never was. */
    override suspend fun read(month: YearMonth): Outcome<MonthlySnapshot, SnapshotFailure> =
        when (val state = of(month).value) {
            is ReadState.Ready -> Outcome.Success(state.value)
            else -> Outcome.Failure(SnapshotFailure.Storage(StorageFailure.Unavailable("unread")))
        }

    override fun observe(month: YearMonth): Flow<SnapshotState> =
        of(month).onStart { observed += month }
}
