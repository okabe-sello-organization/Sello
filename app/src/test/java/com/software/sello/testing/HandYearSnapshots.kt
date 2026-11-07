package com.software.sello.testing

import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.YearSnapshotState
import com.software.sello.domain.port.YearSnapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Years set by hand, Loading until a test sets one. [observed] lists each year read. */
class HandYearSnapshots : YearSnapshots {
    private val states = mutableMapOf<Int, MutableStateFlow<YearSnapshotState>>()
    val observed = mutableListOf<Int>()
    val started: Int get() = observed.size

    fun of(year: Int) = states.getOrPut(year) {
        MutableStateFlow<YearSnapshotState>(ReadState.Loading)
    }

    /** Each observation is new, as the real one is: Loading first, no earlier read kept. */
    override fun observe(year: Int): Flow<YearSnapshotState> {
        observed += year
        return of(year).asNewObservation()
    }
}
