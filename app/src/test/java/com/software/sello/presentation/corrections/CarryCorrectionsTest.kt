package com.software.sello.presentation.corrections

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.CarryMonthChange
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.UndoOffer
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.expenseId
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.presentation.carry.ChangeKind
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.testing.TestMoneyLabels
import com.software.sello.testing.testMoney
import java.time.YearMonth
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting an expense of a closed carry month, and undoing that, show the later months
 * first (ADR 0020 CP20, CP21, SC25, SC34): nothing is sent twice, the confirmed change
 * carries what came with its list, and a list that moved is shown again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CarryCorrectionsTest {
    private val scope = TestScope()
    private val commands = HandExpenseCommands()
    private val monotonic =
        MonotonicClock { 5.hours + scope.testScheduler.currentTime.milliseconds }
    private var nextId = 500
    private val corrections = Corrections(
        commands,
        HandIncomeCommands(),
        HandGoalCommands(),
        HandInvestmentCommands(),
        monotonic,
        { uuid(nextId++) },
        scope.backgroundScope,
        HandCategoryReads().apply { categories = listOf(category(1, "Comida")) },
        testMoney()
    )
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)
    private val mar = YearMonth.of(2027, 3)
    private val abr = YearMonth.of(2027, 4)

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private fun category(number: Int, name: String) = HandCategoryReads().category(number, name)

    private fun closed(month: YearMonth, k: Long, c: Long, s: Long, r: Long, over: Boolean) =
        CarryMonth(
            month,
            ene,
            cop(100_000),
            cop(k),
            cop(c),
            cop(s),
            cop(r),
            CarryVerdict.Closed(if (over) PastVerdict.Over else PastVerdict.WithinLimit)
        )

    /** SC25: deleting ene's 20.000 lists feb, mar (still Excedido) and abr. */
    private val sc25 = CarryCorrection(
        months = listOf(
            CarryMonthChange(
                categoryId(1),
                feb,
                closed(feb, 80_000, 180_000, 70_000, 110_000, false),
                closed(feb, 100_000, 200_000, 70_000, 130_000, false),
                cop(20_000)
            ),
            CarryMonthChange(
                categoryId(1),
                mar,
                closed(mar, 110_000, 210_000, 250_000, -40_000, true),
                closed(mar, 130_000, 230_000, 250_000, -20_000, true),
                cop(20_000)
            ),
            CarryMonthChange(
                categoryId(1),
                abr,
                CarryMonth(
                    abr,
                    ene,
                    cop(100_000),
                    cop(-40_000),
                    cop(60_000),
                    cop(0),
                    cop(60_000),
                    CarryVerdict.InProgress(Verdict.OnTrack)
                ),
                CarryMonth(
                    abr,
                    ene,
                    cop(100_000),
                    cop(-20_000),
                    cop(80_000),
                    cop(0),
                    cop(80_000),
                    CarryVerdict.InProgress(Verdict.OnTrack)
                ),
                cop(20_000)
            )
        ),
        edited = emptyList(),
        settled = emptyList(),
        firstAffected = feb,
        overallCapacityChange = mapOf(feb to cop(20_000), mar to cop(20_000), abr to cop(20_000))
    )

    private val confirmation = CorrectionConfirmation(41, "delete-101")

    private fun unconfirmed(with: CorrectionConfirmation = confirmation) =
        CommandOutcome.Rejected(Specific(ExpenseRejection.CorrectionUnconfirmed(with, sc25)))

    private fun test(body: suspend TestScope.() -> Unit) = scope.runTest { body() }

    @Test
    fun sc25DeletingShowsTheLaterMonthsAndDeletesNothingYet() = test {
        commands.deleteAnswer = { unconfirmed() }

        val result = corrections.delete(expenseId(101), 3, 7)

        assertEquals(DeleteResult.AwaitsConfirmation, result)
        val question = corrections.question.value!!
        assertEquals(CorrectionKind.Delete, question.kind)
        assertEquals(3, question.list.months)
        val rows = question.list.groups.single().rows
        assertEquals("Comida", question.list.groups.single().name)
        assertEquals(listOf(feb, mar, abr), rows.map { it.month })
        assertEquals(
            listOf(ChangeKind.Later, ChangeKind.Later, ChangeKind.Later),
            rows.map {
                it.kind
            }
        )
        assertEquals(plain("210.000"), rows[1].before)
        assertEquals(plain("230.000"), rows[1].after)
        assertEquals(minus("40.000"), rows[1].remainingBefore)
        assertEquals(minus("20.000"), rows[1].remainingAfter)
        // Still Excedido: no verdict flips, so none is named.
        assertNull(rows[1].verdictBefore)
        assertNull(corrections.notice.value)
    }

    @Test
    fun confirmingDeletesOnceWithWhatCameWithTheList() = test {
        commands.deleteAnswer = { unconfirmed() }
        corrections.delete(expenseId(101), 3, 7)
        commands.deleteAnswer = {
            CommandOutcome.Committed(
                commands.committed(
                    it.operationId,
                    it.expenseId,
                    com.software.sello.domain.model.OperationKind.DeleteExpense
                ),
                replayed = false
            )
        }
        commands.offer =
            {
                commands.deleted.last().let {
                    UndoOffer(
                        it.operationId,
                        it.expenseId,
                        5.hours + 6.seconds
                    )
                }
            }
        commands.hold = CompletableDeferred()

        corrections.confirmQuestion()
        corrections.confirmQuestion()
        runCurrent()
        commands.hold!!.complete(Unit)
        runCurrent()

        assertEquals(2, commands.deleted.size)
        assertNull(commands.deleted.first().confirmation)
        assertEquals(confirmation, commands.deleted.last().confirmation)
        assertNotEquals(commands.deleted.first().operationId, commands.deleted.last().operationId)
        assertNull(corrections.question.value)
        // "Gasto borrado · cambió el límite de 3 meses", with Deshacer.
        assertEquals(
            CorrectionNotice.Deleted(canUndo = true, carryMonths = 3),
            corrections.notice.value
        )
    }

    @Test
    fun sc34AListThatMovedIsShownAgainAndNothingIsDeleted() = test {
        commands.deleteAnswer = { unconfirmed() }
        corrections.delete(expenseId(101), 3, 7)
        commands.deleteAnswer = { unconfirmed(CorrectionConfirmation(42, "delete-101")) }

        corrections.confirmQuestion()
        runCurrent()

        val question = corrections.question.value!!
        assertTrue(question.changed)
        assertEquals(false, question.busy)
        assertNull(corrections.notice.value)
    }

    @Test
    fun notConfirmingSendsNothingMore() = test {
        commands.deleteAnswer = { unconfirmed() }
        corrections.delete(expenseId(101), 3, 7)

        corrections.dismissQuestion()
        runCurrent()

        assertNull(corrections.question.value)
        assertEquals(1, commands.deleted.size)
    }

    @Test
    fun undoingADeletionThatChangesLaterMonthsAsksFirst() = test {
        commands.offer =
            {
                commands.deleted.lastOrNull()?.let {
                    UndoOffer(
                        it.operationId,
                        it.expenseId,
                        5.hours + 6.seconds
                    )
                }
            }
        corrections.delete(expenseId(101), 3, 7)
        commands.restoreAnswer = { unconfirmed() }

        corrections.undo()
        runCurrent()

        assertEquals(CorrectionKind.Undo, corrections.question.value?.kind)
        assertNull(commands.restored.single().confirmation)
        commands.restoreAnswer = {
            CommandOutcome.Committed(
                commands.committed(
                    it.operationId,
                    expenseId(101),
                    com.software.sello.domain.model.OperationKind.RestoreExpense
                ),
                replayed = false
            )
        }

        corrections.confirmQuestion()
        runCurrent()

        assertEquals(confirmation, commands.restored.last().confirmation)
        assertNotEquals(commands.restored.first().operationId, commands.restored.last().operationId)
        assertEquals(CorrectionNotice.Restored, corrections.notice.value)
        assertNull(corrections.question.value)
    }

    private fun offerFor6Seconds() {
        commands.offer = {
            commands.deleted.lastOrNull()?.let {
                UndoOffer(it.operationId, it.expenseId, 5.hours + 6.seconds)
            }
        }
    }

    @Test
    fun undoExpiringWhileItsListIsOpenClosesTheListAndSaysSo() = test {
        offerFor6Seconds()
        corrections.delete(expenseId(101), 3, 7)
        commands.restoreAnswer = { unconfirmed() }
        corrections.undo()
        runCurrent()
        assertEquals(CorrectionKind.Undo, corrections.question.value?.kind)

        // The six seconds run out with the list still open.
        advanceTimeBy(6_001)
        runCurrent()

        assertNull(corrections.question.value)
        assertEquals(CorrectionNotice.UndoTooLate, corrections.notice.value)
        // Deshacer pressed now does nothing more: nothing is sent again.
        corrections.confirmQuestion()
        runCurrent()
        assertEquals(1, commands.restored.size)
    }

    @Test
    fun aConfirmedUndoIsAnsweredByStorageNotByTheClock() = test {
        offerFor6Seconds()
        corrections.delete(expenseId(101), 3, 7)
        commands.restoreAnswer = { unconfirmed() }
        corrections.undo()
        runCurrent()
        commands.hold = CompletableDeferred()
        commands.restoreAnswer = {
            CommandOutcome.Rejected(Specific(ExpenseRejection.UndoExpired))
        }

        corrections.confirmQuestion()
        runCurrent()
        // In flight past the deadline: the list waits for its answer, still busy.
        advanceTimeBy(6_001)
        runCurrent()
        assertEquals(true, corrections.question.value?.busy)
        commands.hold!!.complete(Unit)
        runCurrent()

        assertNull(corrections.question.value)
        assertEquals(CorrectionNotice.UndoTooLate, corrections.notice.value)
    }

    @Test
    fun aConfirmedDeletionWhoseOutcomeCannotBeFoundIsSaidToBeUnknown() = test {
        commands.deleteAnswer = { unconfirmed() }
        corrections.delete(expenseId(101), 3, 7)
        commands.deleteAnswer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }

        corrections.confirmQuestion()
        runCurrent()

        // Asked with the same operation, never sent again, and never called "not deleted".
        assertEquals(listOf(commands.deleted.last().operationId), commands.looked)
        assertEquals(2, commands.deleted.size)
        assertEquals(CorrectionNotice.DeleteUnknown, corrections.notice.value)
        assertNull(corrections.question.value)
    }

    @Test
    fun aDeletionConfirmedAfterItsListIsKeptAsStateForTheFormOpenOnIt() = test {
        commands.deleteAnswer = { unconfirmed() }
        corrections.delete(expenseId(101), 3, 7)
        assertNull(corrections.confirmedDeletion.value)
        commands.deleteAnswer = {
            CommandOutcome.Committed(
                commands.committed(
                    it.operationId,
                    it.expenseId,
                    com.software.sello.domain.model.OperationKind.DeleteExpense
                ),
                replayed = false
            )
        }

        corrections.confirmQuestion()
        runCurrent()

        assertEquals(expenseId(101), corrections.confirmedDeletion.value?.expenseId)
    }

    @Test
    fun aCategoryWhoseNameCannotBeReadIsListedWithoutAName() = test {
        val unnamed = Corrections(
            commands,
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            monotonic,
            { uuid(nextId++) },
            scope.backgroundScope,
            HandCategoryReads().apply { failure = StorageFailure.Unavailable("x") },
            testMoney()
        )
        commands.deleteAnswer = { unconfirmed() }

        unnamed.delete(expenseId(101), 3, 7)

        // Null, drawn as "Una categoría": never a blank heading.
        assertNull(unnamed.question.value!!.list.groups.single().name)
    }

    @Test
    fun theBarsDeshacerIsHeldWhileItsListIsOpenAndWhileTheConfirmedUndoIsInFlight() = test {
        offerFor6Seconds()
        corrections.delete(expenseId(101), 3, 7)
        commands.restoreAnswer = { unconfirmed() }
        corrections.undo()
        runCurrent()
        assertEquals(1, commands.restored.size)

        // List open: a second Deshacer on the bar sends nothing.
        corrections.undo()
        runCurrent()
        assertEquals(1, commands.restored.size)

        commands.hold = CompletableDeferred()
        commands.restoreAnswer = {
            CommandOutcome.Committed(
                commands.committed(
                    it.operationId,
                    expenseId(101),
                    com.software.sello.domain.model.OperationKind.RestoreExpense
                ),
                replayed = false
            )
        }
        corrections.confirmQuestion()
        runCurrent()
        // In flight: the bar says it is being undone, and Deshacer sends nothing more.
        assertEquals(
            CorrectionNotice.Deleted(canUndo = true, undoing = true, carryMonths = null),
            corrections.notice.value
        )
        corrections.undo()
        runCurrent()
        assertEquals(2, commands.restored.size)
        assertEquals(confirmation, commands.restored.last().confirmation)

        commands.hold!!.complete(Unit)
        runCurrent()
        assertEquals(2, commands.restored.size)
        assertEquals(CorrectionNotice.Restored, corrections.notice.value)
    }
}
