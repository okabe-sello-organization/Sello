package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.ReadState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inversiones watches the investment board: every committed change reads it again, and a
 * read that fails is a failure with the last good board, never an empty one or a zero.
 * Records are numbered from 100 (the investment is 100, its first aporte 101, its first
 * value 102).
 */
@RunWith(AndroidJUnit4::class)
class InvestmentBoardObservationTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val cdt = investmentId(100)

    private suspend fun create() {
        ledger.created(ledger.createInvestment("CDT Banco", pesos(10_000_000), "2026-09-30"))
    }

    private fun damageTheFirstAporte() = ledger.database.openHelper.writableDatabase.execSQL(
        "UPDATE investment_movement SET amount_minor = 0 WHERE id = '${uuid(101)}'"
    )

    @Test
    fun everyCommittedChangeIsReadAgainAndADamagedRowKeepsTheLastGoodBoard() = runBlocking {
        val states = Channel<InvestmentBoardState>(Channel.UNLIMITED)
        val collector = launch { ledger.investmentReads.observeBoard().collect(states::send) }
        suspend fun next(matches: (InvestmentBoardState) -> Boolean): InvestmentBoardState =
            withTimeout(15_000) {
                var state = states.receive()
                while (!matches(state)) state = states.receive()
                state
            }
        fun value(state: InvestmentBoardState) = (state as? ReadState.Ready<InvestmentBoard>)
            ?.value?.investments?.singleOrNull()?.figures?.value?.amount
        try {
            assertEquals(ReadState.Loading, states.receive())
            val empty = next { it is ReadState.Ready } as ReadState.Ready<InvestmentBoard>
            assertEquals(emptyList<Any>(), empty.value.investments)
            assertEquals(null, empty.value.rates.current)

            create()
            next { value(it) == Money.cop(10_000_000) }
            ledger.recorded(cdt, Valuation, pesosValue(10_746_000), "2026-10-01")
            val good = next {
                value(it) == Money.cop(10_746_000)
            } as ReadState.Ready<InvestmentBoard>
            // A new rate is a committed change too.
            assertTrue(ledger.setRate(415_025, "2026-10-09") is CommandOutcome.Committed)
            val withRate = next {
                (it as? ReadState.Ready<InvestmentBoard>)?.value?.rates?.current != null
            } as ReadState.Ready<InvestmentBoard>
            assertEquals(good.value.investments, withRate.value.investments)

            damageTheFirstAporte()
            // Any committed change reads the board again; this one touches no investment.
            ledger.categories.submit(ledger.createCategory("Mercado"))
            val failed = next {
                it is ReadState.Failed
            } as ReadState.Failed<InvestmentBoard, StorageFailure>
            assertEquals(withRate.value, failed.lastGood)
            assertEquals(
                StorageFailure.Integrity("investment_movement", uuid(101), "amount_minor"),
                failed.failure
            )
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun aFirstReadFailureHasNoLastGoodBoardAndIsNotNoInvestments() = runBlocking {
        create()
        damageTheFirstAporte()

        val states =
            withTimeout(15_000) { ledger.investmentReads.observeBoard().take(2).toList() }

        assertEquals(
            listOf(
                ReadState.Loading,
                ReadState.Failed(
                    StorageFailure.Integrity("investment_movement", uuid(101), "amount_minor"),
                    null
                )
            ),
            states
        )
    }

    @Test
    fun observingAClosedDatabasePropagatesItsCancellationRatherThanNoInvestments() = runBlocking {
        ledger.database.close()
        val states = mutableListOf<InvestmentBoardState>()
        try {
            withTimeout(15_000) {
                ledger.investmentReads.observeBoard().take(2).toList(states)
            }
            throw AssertionError("A closed Room observation returned a board")
        } catch (cancelled: CancellationException) {
            assertTrue(cancelled !is TimeoutCancellationException)
        }
        assertEquals(listOf<InvestmentBoardState>(ReadState.Loading), states)
    }
}
