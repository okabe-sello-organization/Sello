package com.software.sello.devtools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.feature.recurrence.CatchUpRunner
import com.software.sello.feature.recurrence.CatchUpState
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SELLO-098's working checkpoint on the debug app's real graph and Room: opening Sello
 * two weeks later records each missed occurrence on its own date, once, with nothing
 * but the graph's own catch-up (docs/testing/recurrence-examples.md, rule 6, RE10).
 */
@RunWith(AndroidJUnit4::class)
class CatchUpOnOpeningTest {
    private val sandbox = SandboxGraph(
        SandboxSession(
            active = true,
            date = LocalDate.of(2026, 10, 1),
            zone = ZoneId.of("America/Bogota")
        )
    )

    @After
    fun end() = sandbox.erase()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun operation(): OperationId =
        OperationId.of(sandbox.koin.get<RecordIdSource>().next()).value()

    /** Opens Sello on [date] as a new process and waits for its first catch-up. */
    private fun openOn(date: LocalDate) = runBlocking {
        sandbox.store.session = sandbox.store.session.copy(date = date)
        sandbox.open()
        withTimeout(30_000) {
            sandbox.koin.get<CatchUpRunner>().state.first { it is CatchUpState.Finished }
        }
    }

    private fun busRides() = sandbox.rows(
        "SELECT effective_date, amount_minor, origin_slot FROM expense ORDER BY effective_date"
    )

    @Test
    fun openingTwoWeeksLaterRecordsEachMissedOccurrenceOnItsOwnDateOnce() = runBlocking {
        sandbox.erase()
        openOn(LocalDate.of(2026, 10, 1))
        val category = sandbox.koin.get<CategoryCommands>().submit(
            CreateCategory(
                operation(),
                1,
                CategoryName.of("Transporte").value(),
                IconKey.of("directions_bus").value(),
                BudgetLimit.finite(Money.cop(200_000)).value()
            )
        )
        val transporte: CategoryId =
            (category as CategoryCommandOutcome.Committed).change.categoryId
        val created = sandbox.koin.get<ScheduleCommands>().submit(
            CreateSchedule(
                operation(),
                1,
                ScheduledEntry.Payment(
                    transporte,
                    TransactionAmount.of(Money.cop(20_000)).value(),
                    null
                ),
                RepeatRule.EveryWeeks.of(1).value(),
                LocalDate.of(2026, 10, 1),
                null
            )
        )
        assertTrue("$created", created is CommandOutcome.Committed)

        // Sello closes, and opens again two weeks later.
        openOn(LocalDate.of(2026, 10, 15))

        val expected = listOf("2026-10-01|20000|1", "2026-10-08|20000|1", "2026-10-15|20000|1")
        assertEquals(expected, busRides())

        // Opened again the same day: nothing more.
        openOn(LocalDate.of(2026, 10, 15))
        assertEquals(expected, busRides())
    }
}
