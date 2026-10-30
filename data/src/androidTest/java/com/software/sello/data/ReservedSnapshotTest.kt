package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ScheduleCommandOutcome
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The month's snapshot reserves payments on the real database, read with the rest of the
 * month at one revision (recurrence examples RE25 and RE26; ADR 0012 BG-E02 and BG-E03).
 * Hogar is record 100, limited to 1.000.000.
 */
@RunWith(AndroidJUnit4::class)
class ReservedSnapshotTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val october = YearMonth.of(2026, 10)

    private suspend fun limited() =
        ledger.snapshots.read(october).valueOrFail().overall as OverallBudget.Limited

    @Test
    fun aReservedRentLowersTheHeadlineUntilCatchUpRecordsItAndThenNothingMoves() = runBlocking {
        ledger.goTo("2026-10-01")
        ledger.categories.submit(
            ledger.createCategory("Hogar", BudgetLimit.finite(Money.cop(1_000_000)).valueOrFail())
        )
        val created = ledger.schedules.submit(
            CreateSchedule(
                ledger.operation(),
                1,
                ScheduledEntry.Payment(
                    ledger.categoryId(100),
                    TransactionAmount.of(Money.cop(200_000)).valueOrFail(),
                    Note.of("Arriendo").valueOrFail()
                ),
                RepeatRule.Monthly.of(28).valueOrFail(),
                LocalDate.parse("2026-10-01"),
                null
            )
        )
        assertTrue(created is CommandOutcome.Committed)

        // RE25 on 22 October: R 600.000, Q 200.000, M 400.000, 40.000 per day for ten days.
        ledger.goTo("2026-10-22")
        assertTrue(
            ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 400_000)) is
                CommandOutcome.Committed
        )
        val reserved = limited()
        assertEquals(Money.cop(600_000), reserved.budget.remaining)
        assertEquals(Money.cop(200_000), reserved.reservation?.total)
        assertEquals(Money.cop(400_000), reserved.reservation?.margin)
        assertEquals(Money.cop(40_000), reserved.allowance?.amount)
        assertEquals(ledger.revision(), ledger.snapshots.read(october).valueOrFail().revision)

        // RE26 on 28 October, before catch-up: still reserved, 400.000 over four days.
        ledger.goTo("2026-10-28")
        val due = limited()
        assertEquals(Money.cop(400_000), due.reservation?.margin)
        assertEquals(Money.cop(100_000), due.allowance?.amount)

        // Catch-up records it: E 600.000, nothing reserved, the same 400.000 and 100.000.
        val batch = RoomScheduleCatchUp(ledger.database, {
            ledger.audit
        }, { uuid(ledger.nextRecord++) })
            .recordDue(LocalDate.parse("2026-10-28"), 50)
        assertTrue(batch is CatchUpBatch.Recorded)
        val recorded = limited()
        assertNull(recorded.reservation)
        assertEquals(Money.cop(400_000), recorded.budget.remaining)
        assertEquals(Money.cop(100_000), recorded.allowance?.amount)
    }

    /**
     * SELLO-135: category detail reads its category's reserved payments with the rest of
     * the category, at one revision, and works its daily figure out from what they leave.
     */
    @Test
    fun categoryDetailReservesItsOwnPaymentAndAgreesWithTheMonth() = runBlocking {
        ledger.goTo("2026-10-01")
        ledger.categories.submit(
            ledger.createCategory("Hogar", BudgetLimit.finite(Money.cop(1_000_000)).valueOrFail())
        )
        assertTrue(
            ledger.schedules.submit(
                CreateSchedule(
                    ledger.operation(),
                    1,
                    ScheduledEntry.Payment(
                        ledger.categoryId(100),
                        TransactionAmount.of(Money.cop(200_000)).valueOrFail(),
                        Note.of("Arriendo").valueOrFail()
                    ),
                    RepeatRule.Monthly.of(28).valueOrFail(),
                    LocalDate.parse("2026-10-01"),
                    null
                )
            ) is CommandOutcome.Committed
        )
        ledger.goTo("2026-10-22")
        assertTrue(
            ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 400_000)) is
                CommandOutcome.Committed
        )

        // R 600.000, Q 200.000: 400.000 over ten days, 40.000 per day.
        val insights = ledger.categoryInsights.read(ledger.categoryId(100), october).valueOrFail()
        val line = insights.budget.categories.single()
        assertEquals(Money.cop(400_000), line.reservation?.margin)
        assertEquals(Money.cop(40_000), insights.allowance?.amount)
        val month = ledger.snapshots.read(october).valueOrFail()
        assertEquals(line.reservation, month.categories.single().reservation)
        assertEquals(month.revision, insights.budget.revision)

        // Recorded by catch-up: nothing reserved, 400.000 left, the same 40.000 per day.
        ledger.goTo("2026-10-28")
        val batch = RoomScheduleCatchUp(ledger.database, {
            ledger.audit
        }, { uuid(ledger.nextRecord++) })
            .recordDue(LocalDate.parse("2026-10-28"), 50)
        assertTrue(batch is CatchUpBatch.Recorded)
        val after = ledger.categoryInsights.read(ledger.categoryId(100), october).valueOrFail()
        assertNull(after.budget.categories.single().reservation)
        assertEquals(Money.cop(100_000), after.allowance?.amount)
    }
}
