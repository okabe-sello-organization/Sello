package com.software.sello.presentation.corrections

import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.ExpenseCommandOutcome.Rejected
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalUndoOffer
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.IncomeUndoOffer
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentUndoOffer
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.RestoreGoalMovement
import com.software.sello.domain.port.RestoreInvestmentMovement
import com.software.sello.domain.port.UndoGoalCompletion
import com.software.sello.domain.port.UndoOffer
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.expense.expenseId
import com.software.sello.feature.expense.incomeId
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deleting, undoing and what is said about it, over scripted commands. Time is the
 * test's own: the monotonic clock reads five hours plus the virtual time that passed,
 * unless a test moves it apart to stand for a device that slept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CorrectionsTest {
    private val scope = TestScope()
    private val commands = HandExpenseCommands()
    private val incomes = HandIncomeCommands()
    private val goals = HandGoalCommands()
    private val investments = HandInvestmentCommands()
    private var slept: Duration = Duration.ZERO
    private val monotonic =
        MonotonicClock { 5.hours + scope.testScheduler.currentTime.milliseconds + slept }
    private var nextId = 500
    private val corrections =
        Corrections(
            commands,
            incomes,
            goals,
            investments,
            monotonic,
            { uuid(nextId++) },
            scope.backgroundScope
        )

    private val storageFailed =
        Rejected(ExpenseRejection.StorageFailed(StorageFailure.Unavailable("SQLiteException")))

    init {
        // As the real commands do: a committed deletion is on offer for six seconds.
        commands.offer = {
            commands.deleted.lastOrNull()?.takeIf { stillOffered }?.let {
                UndoOffer(it.operationId, it.expenseId, deletedAt + 6.seconds)
            }?.takeIf { monotonic.elapsed() < it.until }
        }
    }

    private var stillOffered = true
    private var deletedAt: Duration = Duration.ZERO

    private fun test(body: suspend TestScope.() -> Unit) = scope.runTest { body() }

    private suspend fun delete(
        expense: Int = 101,
        version: Long = 3,
        generation: Long = 7
    ): DeleteResult {
        deletedAt = monotonic.elapsed()
        return corrections.delete(expenseId(expense), version, generation)
    }

    private val notice get() = corrections.notice.value

    // --- Deleting ---

    @Test
    fun deletingSendsOneDeletionOfThatVersionAndOffersUndo() = test {
        val result = delete()

        assertEquals(DeleteResult.Deleted, result)
        val sent = commands.deleted.single()
        assertEquals(
            listOf(uuid(500), uuid(101), 3L, 7L),
            listOf(
                sent.operationId.value,
                sent.expenseId.value,
                sent.expectedVersion,
                sent.generation
            )
        )
        assertEquals(CorrectionNotice.Deleted(canUndo = true), notice)
    }

    @Test
    fun aDeletionThatWasRefusedSaysWhyAndOffersNothing() = test {
        val answers = listOf(
            Rejected(ExpenseRejection.ExpenseMissing) to DeleteResult.Gone,
            Rejected(ExpenseRejection.VersionConflict(current = 4)) to DeleteResult.Changed,
            Rejected(ExpenseRejection.StaleGeneration(current = 8)) to DeleteResult.DataChanged,
            storageFailed to DeleteResult.Failed
        )

        val results = answers.map { (answer, _) ->
            commands.deleteAnswer = { answer }
            delete() to notice
        }

        assertEquals(answers.map { it.second to null }, results)
    }

    @Test
    fun aDeletionWhoseOutcomeIsUnknownIsLookedUpAndNeverSentAgain() = test {
        commands.deleteAnswer = { OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Success(null) }
        val notSaved = delete()
        // It did commit, but its time was not started: nothing can be undone.
        stillOffered = false
        commands.found = { Outcome.Success(commands.committed(it)) }
        val saved = delete()
        val savedNotice = notice
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("SQLiteException")) }
        val unreadable = delete()

        assertEquals(
            listOf(DeleteResult.Failed, DeleteResult.Deleted, DeleteResult.Failed),
            listOf(notSaved, saved, unreadable)
        )
        assertEquals(CorrectionNotice.Deleted(canUndo = false), savedNotice)
        assertEquals(3, commands.deleted.size)
        assertEquals(commands.deleted.map { it.operationId }, commands.looked)
    }

    @Test
    fun aDeletionFinishesAndIsOfferedEvenIfTheScreenThatAskedIsGone() = test {
        commands.hold = CompletableDeferred()
        val screen = launch { delete() }
        runCurrent()

        screen.cancelAndJoin()
        commands.hold!!.complete(Unit)
        runCurrent()

        assertEquals(1, commands.deleted.size)
        assertEquals(CorrectionNotice.Deleted(canUndo = true), notice)
    }

    // --- The six seconds ---

    @Test
    fun theOfferIsThereJustBeforeSixSecondsAndGoneAtSixWithNothingAsked() = test {
        delete()

        advanceTimeBy(5_999)
        runCurrent()
        val before = notice
        advanceTimeBy(1)
        runCurrent()

        assertEquals(CorrectionNotice.Deleted(canUndo = true), before)
        assertNull(notice)
        assertEquals(emptyList<Any>(), commands.restored)
    }

    @Test
    fun aSecondDeletionTakesTheFirstOnesPlaceAndKeepsItsOwnSixSeconds() = test {
        delete(expense = 101)
        advanceTimeBy(4_000)
        delete(expense = 102)

        advanceTimeBy(2_500)
        runCurrent()
        val afterTheFirstWouldHaveEnded = notice
        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.Deleted(canUndo = true), afterTheFirstWouldHaveEnded)
        assertEquals(commands.deleted[1].operationId, commands.restored.single().deletionId)
    }

    @Test
    fun whenTheDeviceSleptPastTheTimeComingBackRemovesTheOffer() = test {
        delete()
        advanceTimeBy(1_000)
        // Real time went on while nothing here ran.
        slept = 30.seconds

        corrections.refresh()

        assertNull(notice)
    }

    @Test
    fun comingBackInTimeLeavesTheOfferAndItStillEndsWhenItShould() = test {
        delete()
        advanceTimeBy(1_000)
        slept = 3.seconds

        corrections.refresh()
        val back = notice
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(CorrectionNotice.Deleted(canUndo = true), back)
        assertNull(notice)
    }

    // --- Undo ---

    @Test
    fun undoPutsBackThatDeletionInItsHistoryAndSaysSoForAMoment() = test {
        delete(generation = 7)
        advanceTimeBy(2_000)

        corrections.undo()
        runCurrent()
        val said = notice
        advanceTimeBy(4_000)
        runCurrent()

        val sent = commands.restored.single()
        assertEquals(
            listOf(uuid(501), 7L, uuid(500)),
            listOf(sent.operationId.value, sent.generation, sent.deletionId.value)
        )
        assertEquals(CorrectionNotice.Restored, said)
        assertNull(notice)
    }

    @Test
    fun undoPressedAgainWhileItIsBeingDoneAsksOnce() = test {
        delete()
        commands.hold = CompletableDeferred()

        corrections.undo()
        runCurrent()
        val during = notice
        corrections.undo()
        runCurrent()
        // The time running out meanwhile does not take away an undo already asked.
        advanceTimeBy(10_000)
        runCurrent()
        val stillDuring = notice
        commands.hold!!.complete(Unit)
        runCurrent()

        assertEquals(CorrectionNotice.Deleted(canUndo = true, undoing = true), during)
        assertEquals(during, stillDuring)
        assertEquals(1, commands.restored.size)
        assertEquals(CorrectionNotice.Restored, notice)
    }

    @Test
    fun anUndoThatCameTooLateOrIsBlockedSaysSoAndRestoresNothing() = test {
        commands.restoreAnswer = { Rejected(ExpenseRejection.UndoExpired) }
        delete()
        corrections.undo()
        runCurrent()
        val late = notice
        commands.restoreAnswer = { Rejected(ExpenseRejection.UndoConflict) }
        delete()
        corrections.undo()
        runCurrent()
        val conflict = notice
        commands.restoreAnswer = { Rejected(ExpenseRejection.StaleGeneration(current = 9)) }
        delete()
        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.UndoTooLate, late)
        assertEquals(CorrectionNotice.UndoBlocked, conflict)
        assertEquals(CorrectionNotice.UndoBlocked, notice)
    }

    @Test
    fun anUndoThatFailedCanBeTriedAgainInTimeAsTheSameOperation() = test {
        delete()
        commands.restoreAnswer = { storageFailed }
        corrections.undo()
        runCurrent()
        val failed = notice
        commands.restoreAnswer =
            {
                ExpenseCommandOutcome.Committed(
                    commands.committed(it.operationId),
                    replayed = false
                )
            }

        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.Deleted(canUndo = true, undoFailed = true), failed)
        assertEquals(CorrectionNotice.Restored, notice)
        assertEquals(listOf(uuid(501), uuid(501)), commands.restored.map { it.operationId.value })
    }

    @Test
    fun anUndoWhoseOutcomeIsUnknownIsLookedUpBeforeAnythingIsSaid() = test {
        delete()
        commands.restoreAnswer = { OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Success(null) }
        corrections.undo()
        runCurrent()
        val notDone = notice
        commands.found = { Outcome.Success(commands.committed(it)) }

        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.Deleted(canUndo = true, undoFailed = true), notDone)
        assertEquals(CorrectionNotice.Restored, notice)
        assertEquals(commands.restored.map { it.operationId }, commands.looked)
    }

    @Test
    fun undoWithNothingToUndoAsksNothing() = test {
        corrections.undo()
        delete()
        advanceTimeBy(6_000)
        runCurrent()
        corrections.undo()
        runCurrent()

        assertEquals(emptyList<Any>(), commands.restored)
        assertNull(notice)
    }

    // --- Other notices ---

    @Test
    fun aSavedEditIsSaidForAMomentAndCanBeDismissed() = test {
        corrections.updated()
        val said = notice
        advanceTimeBy(3_999)
        runCurrent()
        val stillSaid = notice
        advanceTimeBy(1)
        runCurrent()
        val gone = notice
        corrections.updated()
        corrections.dismiss()

        assertEquals(
            listOf(CorrectionNotice.Updated, CorrectionNotice.Updated, null),
            listOf(said, stillSaid, gone)
        )
        assertNull(notice)
    }

    private val saved = CorrectionNotice.Confirmed(
        "Gasto anotado: 48.700 en Comida",
        "Gasto anotado: 48.700 pesos en Comida"
    )

    @Test
    fun aConfirmedSaveIsSaidForFourSecondsAndCanBeDismissed() = test {
        corrections.confirmed(saved)
        val said = notice
        advanceTimeBy(3_999)
        runCurrent()
        val stillSaid = notice
        advanceTimeBy(1)
        runCurrent()
        val gone = notice
        corrections.confirmed(saved)
        corrections.dismiss()

        assertEquals(listOf(saved, saved, null), listOf(said, stillSaid, gone))
        assertNull(notice)
    }

    @Test
    fun aConfirmedSaveTakesThePlaceOfAnOfferToUndoAndLeavesNothingToUndo() = test {
        // Newest wins, as for every statement: the offer goes, its deletion stands.
        delete()
        corrections.confirmed(saved)
        corrections.undo()
        runCurrent()

        assertEquals(saved, notice)
        assertEquals(emptyList<Any>(), commands.restored)
    }

    @Test
    fun aLaterNoticeTakesThePlaceOfAConfirmedSave() = test {
        corrections.confirmed(saved)
        val first = notice
        advanceTimeBy(1_000)
        corrections.updated()
        advanceTimeBy(3_500)
        runCurrent()

        assertEquals(saved, first)
        assertEquals(CorrectionNotice.Updated, notice)
    }

    @Test
    fun aNewExpenseOrIncomeSavedWhileAnUndoIsOfferedTakesItsPlaceForFourSeconds() = test {
        val expense = CorrectionNotice.ExpenseRecorded(amount = null, categoryName = "Comida")
        val income = CorrectionNotice.IncomeRecorded(amount = null)
        delete()
        corrections.recorded(expense)
        corrections.undo()
        runCurrent()
        val afterExpense = notice
        corrections.incomeRecorded(income)
        val afterIncome = notice
        advanceTimeBy(4_000)
        runCurrent()

        assertEquals(listOf<CorrectionNotice?>(expense, income), listOf(afterExpense, afterIncome))
        assertEquals(emptyList<Any>(), commands.restored)
        assertNull(notice)
    }

    // --- Income: the same offer, the income's own commands and words ---

    private suspend fun deleteIncome(
        income: Int = 201,
        version: Long = 2,
        generation: Long = 7
    ): DeleteResult {
        deletedAt = monotonic.elapsed()
        incomes.offer = {
            incomes.deleted.lastOrNull()?.let {
                IncomeUndoOffer(it.operationId, it.incomeId, deletedAt + 6.seconds)
            }?.takeIf { monotonic.elapsed() < it.until }
        }
        return corrections.deleteIncome(incomeId(income), version, generation)
    }

    @Test
    fun deletingAnIncomeSendsOneIncomeDeletionAndOffersUndoInItsOwnWords() = test {
        val result = deleteIncome()

        assertEquals(DeleteResult.Deleted, result)
        val sent = incomes.deleted.single()
        assertEquals(
            listOf(uuid(500), uuid(201), 2L, 7L),
            listOf(
                sent.operationId.value,
                sent.incomeId.value,
                sent.expectedVersion,
                sent.generation
            )
        )
        assertEquals(CorrectionNotice.IncomeDeleted(canUndo = true), notice)
        assertEquals(emptyList<Any>(), commands.deleted)
    }

    @Test
    fun undoingADeletedIncomeRestoresItThroughTheIncomeCommandsOnly() = test {
        deleteIncome(generation = 7)
        advanceTimeBy(2_000)

        corrections.undo()
        runCurrent()

        val sent = incomes.restored.single()
        assertEquals(
            listOf(uuid(501), 7L, uuid(500)),
            listOf(sent.operationId.value, sent.generation, sent.deletionId.value)
        )
        assertEquals(emptyList<Any>(), commands.restored)
        assertEquals(CorrectionNotice.IncomeRestored, notice)
    }

    @Test
    fun anIncomeOfferEndsAtSixSecondsAndALateOrBlockedUndoSaysSoAboutTheIncome() = test {
        deleteIncome()
        advanceTimeBy(5_999)
        runCurrent()
        val before = notice
        advanceTimeBy(1)
        runCurrent()
        val after = notice
        incomes.restoreAnswer = { IncomeCommandOutcome.Rejected(IncomeRejection.UndoExpired) }
        deleteIncome()
        corrections.undo()
        runCurrent()
        val late = notice
        incomes.restoreAnswer = { IncomeCommandOutcome.Rejected(IncomeRejection.UndoConflict) }
        deleteIncome()
        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.IncomeDeleted(canUndo = true), before)
        assertNull(after)
        assertEquals(CorrectionNotice.IncomeUndoTooLate, late)
        assertEquals(CorrectionNotice.IncomeUndoBlocked, notice)
    }

    @Test
    fun anIncomeDeletionTakesAnExpenseOffersPlaceAndUndoThenGoesToTheIncome() = test {
        delete(expense = 101)
        advanceTimeBy(1_000)

        deleteIncome()
        corrections.undo()
        runCurrent()

        assertEquals(1, incomes.restored.size)
        assertEquals(emptyList<Any>(), commands.restored)
        assertEquals(CorrectionNotice.IncomeRestored, notice)
    }

    @Test
    fun aRefusedIncomeDeletionSaysWhyAndASavedIncomeEditIsSaidForAMoment() = test {
        val answers = listOf(
            IncomeRejection.IncomeMissing to DeleteResult.Gone,
            IncomeRejection.VersionConflict(current = 4) to DeleteResult.Changed,
            IncomeRejection.StaleGeneration(current = 8) to DeleteResult.DataChanged,
            IncomeRejection.StorageFailed(StorageFailure.Unavailable("x")) to DeleteResult.Failed
        )

        val results = answers.map { (reason, _) ->
            incomes.deleteAnswer = { IncomeCommandOutcome.Rejected(reason) }
            deleteIncome() to notice
        }
        corrections.incomeUpdated()

        assertEquals(answers.map { it.second to null }, results)
        assertEquals(CorrectionNotice.IncomeUpdated, notice)
    }

    // --- Investment movements: the same offer, the investment's own commands and words ---

    private fun investmentDeletion(generation: Long = 7) = DeleteInvestmentMovement(
        OperationId.of(uuid(910)).let { (it as Outcome.Success).value },
        generation,
        (InvestmentId.of(uuid(311)) as Outcome.Success).value,
        4,
        (InvestmentMovementId.of(uuid(312)) as Outcome.Success).value,
        1
    )

    private suspend fun deleteInvestmentMovement(generation: Long = 7): InvestmentCommandOutcome {
        deletedAt = monotonic.elapsed()
        val command = investmentDeletion(generation)
        investments.undoOffer = {
            InvestmentUndoOffer(command.operationId, command.investmentId, deletedAt + 6.seconds)
                .takeIf { monotonic.elapsed() < it.until }
        }
        return corrections.deleteInvestmentMovement(command)
    }

    @Test
    fun in21DeletingAnInvestmentMovementOffersUndoAndDeshacerRestoresItThroughItsCommands() = test {
        val outcome = deleteInvestmentMovement(generation = 7)
        val offered = notice
        advanceTimeBy(2_000)

        corrections.undo()
        runCurrent()

        assertEquals(true, outcome is InvestmentCommandOutcome.Committed)
        assertEquals(CorrectionNotice.InvestmentMovementDeleted(canUndo = true), offered)
        val sent = investments.sent.last() as RestoreInvestmentMovement
        assertEquals(
            listOf(uuid(500), 7L, uuid(910)),
            listOf(sent.operationId.value, sent.generation, sent.deletionId.value)
        )
        assertEquals(investmentDeletion(), investments.sent.first())
        assertEquals(emptyList<Any>(), goals.sent)
        assertEquals(emptyList<Any>(), commands.restored)
        assertEquals(CorrectionNotice.InvestmentMovementRestored, notice)
    }

    @Test
    fun anInvestmentOfferEndsAtSixSecondsAndALateOrBlockedUndoSaysSoAboutTheMovement() = test {
        deleteInvestmentMovement()
        advanceTimeBy(5_999)
        runCurrent()
        val before = notice
        advanceTimeBy(1)
        runCurrent()
        val after = notice
        deleteInvestmentMovement()
        investments.answers += InvestmentCommandOutcome.Rejected(InvestmentRejection.UndoExpired)
        corrections.undo()
        runCurrent()
        val late = notice
        deleteInvestmentMovement()
        investments.answers += InvestmentCommandOutcome.Rejected(InvestmentRejection.UndoConflict)
        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.InvestmentMovementDeleted(canUndo = true), before)
        assertNull(after)
        assertEquals(CorrectionNotice.InvestmentMovementUndoTooLate, late)
        assertEquals(CorrectionNotice.InvestmentMovementUndoBlocked, notice)
    }

    @Test
    fun anUnknownUndoOfAnInvestmentMovementIsAskedWithTheSameOperationNeverSentAgain() = test {
        deleteInvestmentMovement()
        investments.answers += InvestmentCommandOutcome.OutcomeUnknown(
            (OperationId.of(uuid(500)) as Outcome.Success).value
        )
        investments.found = Outcome.Failure(StorageFailure.Unavailable("x"))

        corrections.undo()
        runCurrent()
        val failed = notice

        assertEquals(listOf(uuid(500)), investments.asked.map { it.value })
        assertEquals(2, investments.sent.size)
        assertEquals(
            CorrectionNotice.InvestmentMovementDeleted(canUndo = true, undoFailed = true),
            failed
        )
    }

    @Test
    fun aRefusedInvestmentMovementDeletionOffersNothing() = test {
        investments.answers +=
            InvestmentCommandOutcome.Rejected(InvestmentRejection.LastContribution)

        val outcome = deleteInvestmentMovement()
        val afterRefusal = notice
        corrections.investmentMovementUpdated()

        assertEquals(true, outcome is InvestmentCommandOutcome.Rejected)
        assertNull(afterRefusal)
        assertEquals(CorrectionNotice.InvestmentMovementUpdated, notice)
    }

    // --- Goal movements: the same offer, the goal's own commands and words ---

    private fun movementDeletion(generation: Long = 7) = DeleteGoalMovement(
        OperationId.of(uuid(900)).let { (it as Outcome.Success).value },
        generation,
        (GoalId.of(uuid(301)) as Outcome.Success).value,
        4,
        (GoalMovementId.of(uuid(302)) as Outcome.Success).value,
        1
    )

    private suspend fun deleteMovement(generation: Long = 7): GoalCommandOutcome {
        deletedAt = monotonic.elapsed()
        val command = movementDeletion(generation)
        goals.undoOffer = {
            GoalUndoOffer(command.operationId, command.goalId, deletedAt + 6.seconds)
                .takeIf { monotonic.elapsed() < it.until }
        }
        return corrections.deleteGoalMovement(command)
    }

    @Test
    fun deletingAGoalMovementOffersUndoInItsOwnWords() = test {
        val outcome = deleteMovement()

        assertEquals(true, outcome is GoalCommandOutcome.Committed)
        assertEquals(listOf<Any>(movementDeletion()), goals.sent)
        assertEquals(CorrectionNotice.GoalMovementDeleted(canUndo = true), notice)
        assertEquals(emptyList<Any>(), commands.deleted)
    }

    @Test
    fun undoingADeletedMovementRestoresItThroughTheGoalCommandsOnly() = test {
        deleteMovement(generation = 7)
        advanceTimeBy(2_000)

        corrections.undo()
        runCurrent()

        val sent = goals.sent.last() as RestoreGoalMovement
        assertEquals(
            listOf(uuid(500), 7L, uuid(900)),
            listOf(sent.operationId.value, sent.generation, sent.deletionId.value)
        )
        assertEquals(emptyList<Any>(), commands.restored)
        assertEquals(emptyList<Any>(), incomes.restored)
        assertEquals(CorrectionNotice.GoalMovementRestored, notice)
    }

    @Test
    fun aMovementOfferEndsAtSixSecondsAndALateOrBlockedUndoSaysSoAboutTheMovement() = test {
        deleteMovement()
        advanceTimeBy(5_999)
        runCurrent()
        val before = notice
        advanceTimeBy(1)
        runCurrent()
        val after = notice
        deleteMovement()
        goals.queue += GoalCommandOutcome.Rejected(GoalRejection.UndoExpired)
        corrections.undo()
        runCurrent()
        val late = notice
        deleteMovement()
        goals.queue += GoalCommandOutcome.Rejected(GoalRejection.UndoConflict)
        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.GoalMovementDeleted(canUndo = true), before)
        assertNull(after)
        assertEquals(CorrectionNotice.GoalMovementUndoTooLate, late)
        assertEquals(CorrectionNotice.GoalMovementUndoBlocked, notice)
    }

    @Test
    fun anExpenseDeletionTakesAMovementOffersPlaceAndUndoThenGoesToTheExpense() = test {
        deleteMovement()
        advanceTimeBy(1_000)

        delete(expense = 101)
        corrections.undo()
        runCurrent()

        assertEquals(1, commands.restored.size)
        assertEquals(1, goals.sent.size)
        assertEquals(CorrectionNotice.Restored, notice)
    }

    @Test
    fun aRefusedMovementDeletionOffersNothingAndASavedMovementIsSaidForAMoment() = test {
        goals.queue += GoalCommandOutcome.Rejected(GoalRejection.MovementMissing)

        val outcome = deleteMovement()
        val afterRefusal = notice
        corrections.goalMovementUpdated()

        assertEquals(true, outcome is GoalCommandOutcome.Rejected)
        assertNull(afterRefusal)
        assertEquals(CorrectionNotice.GoalMovementUpdated, notice)
    }

    @Test
    fun aStampLandsOnceHoweverOftenTheGoalIsOpenedWhileItIsSaid() = test {
        val half =
            GoalCelebration(
                uuid(301),
                "Viaje a la costa",
                listOf(GoalPlace.Half),
                emptyList(),
                false
            )
        corrections.goalMovementRecorded(
            CorrectionNotice.GoalMovementRecorded(false, null, celebration = half)
        )
        val shown = notice!!.celebration!!

        val first = corrections.lands(shown)
        val reopened = corrections.lands(shown)
        corrections.goalMovementRecorded(
            CorrectionNotice.GoalMovementRecorded(
                false,
                null,
                celebration = half.copy(earned = listOf(GoalPlace.ThreeQuarters))
            )
        )

        assertEquals(listOf(true, false), listOf(first, reopened))
        // A later aporte is a new save, and its stamp lands.
        assertEquals(true, corrections.lands(notice!!.celebration!!))
    }

    // --- Completing a goal: Deshacer for the time the completion asked ---

    private fun completion(undoFor: Duration = 14.seconds) = CompleteGoal(
        OperationId.of(uuid(950)).let { (it as Outcome.Success).value },
        7,
        (GoalId.of(uuid(301)) as Outcome.Success).value,
        5,
        undoFor
    )

    private suspend fun complete(undoFor: Duration = 14.seconds): GoalCommandOutcome {
        val command = completion(undoFor)
        val at = monotonic.elapsed()
        goals.completionOffer = {
            GoalUndoOffer(command.operationId, command.goalId, at + undoFor)
                .takeIf { monotonic.elapsed() < it.until }
        }
        return corrections.completeGoal(command, "Celular nuevo")
    }

    @Test
    fun aCompletedGoalOffersDeshacerInItsNameForTheTimeAsked() = test {
        val outcome = complete(undoFor = 14.seconds)

        assertEquals(true, outcome is GoalCommandOutcome.Committed)
        assertEquals(listOf<Any>(completion(14.seconds)), goals.sent)
        assertEquals(CorrectionNotice.GoalCompleted("Celular nuevo", canUndo = true), notice)
        advanceTimeBy(13_999)
        runCurrent()
        assertEquals(CorrectionNotice.GoalCompleted("Celular nuevo", canUndo = true), notice)
        advanceTimeBy(1)
        runCurrent()
        assertNull(notice)
    }

    @Test
    fun mt24DeshacerReturnsTheGoalThroughItsOwnUndoCommand() = test {
        complete()
        advanceTimeBy(3_000)

        corrections.undo()
        runCurrent()

        val sent = goals.sent.last() as UndoGoalCompletion
        assertEquals(
            listOf(uuid(500), 7L, uuid(950)),
            listOf(sent.operationId.value, sent.generation, sent.completionId.value)
        )
        assertEquals(CorrectionNotice.GoalCompletionUndone("Celular nuevo"), notice)
    }

    @Test
    fun mt25ADeshacerTooLateLeavesTheGoalFinal() = test {
        complete()
        goals.queue += GoalCommandOutcome.Rejected(GoalRejection.UndoExpired)

        corrections.undo()
        runCurrent()

        assertEquals(CorrectionNotice.GoalCompletionUndoTooLate, notice)
    }
}
