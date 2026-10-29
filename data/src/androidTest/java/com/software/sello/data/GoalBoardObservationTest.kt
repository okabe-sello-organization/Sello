package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordGoalMovement
import java.time.LocalDate
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
 * Metas watches the goal board: every committed change reads it again, and a read that
 * fails is a failure with the last good board, never an empty one. Records are numbered
 * from 100 (the goal is 100, its target 101, its first aporte 102).
 */
@RunWith(AndroidJUnit4::class)
class GoalBoardObservationTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val viaje = GoalId.of(uuid(100)).valueOrFail()

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private suspend fun create() = assertTrue(
        ledger.goals.submit(
            CreateGoal(
                ledger.operation(),
                1,
                GoalName.of("Viaje a la costa").valueOrFail(),
                IconKey.of("flight").valueOrFail(),
                pesos(3_000_000),
                null
            )
        ) is CommandOutcome.Committed
    )

    private suspend fun aporte(pesos: Long) {
        val version = (ledger.goalReads.goal(viaje) as Outcome.Success).value!!.history.goal.version
        assertTrue(
            ledger.goals.submit(
                RecordGoalMovement(
                    ledger.operation(),
                    1,
                    viaje,
                    version,
                    GoalMovementKind.Contribution,
                    pesos(pesos),
                    LocalDate.of(2026, 10, 9),
                    null
                )
            ) is CommandOutcome.Committed
        )
    }

    @Test
    fun everyCommittedChangeIsReadAgainAndADamagedRowKeepsTheLastGoodBoard() = runBlocking {
        val states = Channel<GoalBoardState>(Channel.UNLIMITED)
        val collector = launch { ledger.goalReads.observeBoard().collect(states::send) }
        suspend fun next(matches: (GoalBoardState) -> Boolean): GoalBoardState =
            withTimeout(15_000) {
                var state = states.receive()
                while (!matches(state)) state = states.receive()
                state
            }
        fun saved(state: GoalBoardState) =
            (state as? ReadState.Ready<GoalBoard>)?.value?.goals?.singleOrNull()?.progress?.saved
        try {
            assertEquals(ReadState.Loading, states.receive())
            val empty = next { it is ReadState.Ready } as ReadState.Ready<GoalBoard>
            assertEquals(emptyList<Any>(), empty.value.goals)

            create()
            next { saved(it) == Money.cop(0) }
            aporte(750_000)
            val good = next { saved(it) == Money.cop(750_000) } as ReadState.Ready<GoalBoard>
            assertEquals(viaje, good.value.featured)

            ledger.database.openHelper.writableDatabase.execSQL(
                "UPDATE goal_event SET amount_minor = 0 WHERE id = '${uuid(102)}'"
            )
            // Any committed change reads the board again; this one touches no goal.
            ledger.categories.submit(ledger.createCategory("Mercado"))
            val failed = next {
                it is ReadState.Failed
            } as ReadState.Failed<GoalBoard, StorageFailure>
            assertEquals(good.value, failed.lastGood)
            assertEquals(
                StorageFailure.Integrity("goal_event", uuid(102), "amount_minor"),
                failed.failure
            )
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun aFirstReadFailureHasNoLastGoodBoardAndIsNotNoGoals() = runBlocking {
        create()
        aporte(750_000)
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE goal_event SET amount_minor = 0 WHERE id = '${uuid(102)}'"
        )

        val states = withTimeout(15_000) { ledger.goalReads.observeBoard().take(2).toList() }

        assertEquals(
            listOf(
                ReadState.Loading,
                ReadState.Failed(
                    StorageFailure.Integrity("goal_event", uuid(102), "amount_minor"),
                    null
                )
            ),
            states
        )
    }

    @Test
    fun observingAClosedDatabasePropagatesItsCancellationRatherThanNoGoals() = runBlocking {
        ledger.database.close()
        val states = mutableListOf<GoalBoardState>()
        try {
            withTimeout(15_000) { ledger.goalReads.observeBoard().take(2).toList(states) }
            throw AssertionError("A closed Room observation returned a board")
        } catch (cancelled: CancellationException) {
            assertTrue(cancelled !is TimeoutCancellationException)
        }
        assertEquals(listOf<GoalBoardState>(ReadState.Loading), states)
    }
}
