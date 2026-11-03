package com.software.sello.presentation.read

import com.software.sello.domain.port.ReadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LastGoodReadTest {
    private val kept = LastGoodRead<String, String>()

    private suspend fun observe(of: Any, vararg reads: ReadState<String, String>) =
        kept.keep(of, flowOf(*reads)).toList()

    private fun reads(vararg reads: ReadState<String, String>): Flow<ReadState<String, String>> =
        flowOf(*reads)

    @Test
    fun nothingEverReadIsLoadingAndThenTheErrorWithNothingKept() = runTest {
        assertEquals(
            listOf(ReadState.Loading, ReadState.Failed("caído", null)),
            observe("oct", ReadState.Loading, ReadState.Failed("caído", null))
        )
        assertEquals(listOf(ReadState.Loading), observe("oct", ReadState.Loading))
    }

    @Test
    fun aNewObservationRepeatsWhatWasShownInsteadOfLoading() = runTest {
        observe("oct", ReadState.Loading, ReadState.Ready("octubre"))

        assertEquals(
            listOf(ReadState.Ready("octubre"), ReadState.Ready("octubre 2")),
            observe(
                "oct",
                ReadState.Loading,
                ReadState.Ready("octubre"),
                ReadState.Ready("octubre 2")
            )
        )
    }

    @Test
    fun aFailureWithoutALastGoodValueCarriesTheOneShown() = runTest {
        observe("oct", ReadState.Ready("octubre"))

        assertEquals(
            listOf(ReadState.Ready("octubre"), ReadState.Failed("caído", "octubre")),
            observe("oct", ReadState.Loading, ReadState.Failed("caído", null))
        )
        // Retrying while older: still older, never Loading or the bare error.
        assertEquals(
            listOf(ReadState.Failed("caído", "octubre"), ReadState.Failed("otra vez", "octubre")),
            observe("oct", ReadState.Loading, ReadState.Failed("otra vez", null))
        )
    }

    @Test
    fun aFailureWithItsOwnLastGoodValueKeepsThatNewerOne() = runTest {
        observe("oct", ReadState.Ready("octubre"))

        observe("oct", ReadState.Failed("caído", "octubre 2"))

        assertEquals(
            listOf(ReadState.Failed("caído", "octubre 2")),
            observe("oct", ReadState.Loading)
        )
    }

    @Test
    fun anotherThingReadStartsAfresh() = runTest {
        observe("oct", ReadState.Ready("octubre"))

        assertEquals(
            listOf(ReadState.Loading, ReadState.Failed("caído", null)),
            observe("sep", ReadState.Loading, ReadState.Failed("caído", null))
        )
    }

    @Test
    fun theSameReadRepeatedIsToldOnce() = runTest {
        assertEquals(
            listOf(ReadState.Ready("octubre")),
            kept.keep("oct", reads(ReadState.Ready("octubre"), ReadState.Ready("octubre"))).toList()
        )
    }
}
