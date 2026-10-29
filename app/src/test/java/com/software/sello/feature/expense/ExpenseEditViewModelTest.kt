package com.software.sello.feature.expense

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.feature.recurrence.HandScheduleCommands
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.feature.recurrence.scheduleId
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.ScheduleChange
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The form correcting a recorded expense. Storage holds expense 100: 48.700 in
 * Alimentación on 5 October with the note "Pan", at version 3. Today is 22 October.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseEditViewModelTest {
    private val commands = HandExpenseCommands()
    private val reads = HandExpenseReads()
    private val snapshots = HandSnapshots()
    private val clock = HandFinancialClock("2026-10-22")
    private var nextId = 500
    private val money = MoneyFormatter(
        object : MoneyLabels {
            override fun amount(currency: Currency, digits: String, isOne: Boolean) =
                "$digits pesos"

            override fun negative(amount: String) = "menos $amount"

            override fun positive(amount: String) = "más $amount"
        }
    )
    private lateinit var corrections: ExpenseCorrections

    private val food = categoryIn(1, "Alimentación", 900_000, icon = "restaurant")
    private val coffee = categoryIn(2, "Café", 50_000, icon = "local_cafe")
    private val old = categoryIn(5, "Vieja", 100_000, archived = true)
    private val older = categoryIn(6, "Más vieja", 100_000, archived = true)
    private val bread = storedExpense(100, 1, "2026-10-05", 48_700, "Pan").copy(version = 3)
    private val scheduleBook = HandScheduleReads()
    private val schedules = HandScheduleCommands(scheduleBook)

    @Before
    fun mainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        corrections = ExpenseCorrections(
            commands,
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { Duration.ZERO },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main)
        )
    }

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun shown(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun month(month: String, generation: Long = 7) = snapshots.ready(
        monthOf(
            "2026-10-22",
            listOf(food, old, coffee, older),
            month = YearMonth.parse(month),
            generation = generation
        )
    )

    private fun editing(expense: Expense = bread) =
        SavedStateHandle(mapOf(ExpenseEntryViewModel.KEY_EDITED_EXPENSE to expense.id.value))

    private fun form(saved: SavedStateHandle = editing()) =
        ExpenseEntryViewModel(saved, commands, reads, snapshots, clock, {
            uuid(nextId++)
        }, money, corrections, schedules, scheduleBook)

    /** The form opened on [expense], with it in storage and its months read. */
    private fun opened(
        expense: Expense = bread,
        saved: SavedStateHandle = editing(expense)
    ): ExpenseEntryViewModel {
        reads.stored[expense.id] = expense
        month("2026-10")
        month(YearMonth.from(expense.date).toString())
        return form(saved)
    }

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private fun ExpenseEntryViewModel.retype(digits: String) {
        onAction(ExpenseEntryAction.ClearAmount)
        digits.forEach { onAction(ExpenseEntryAction.Digit(it.digitToInt())) }
    }

    private val ExpenseEntryViewModel.now get() = state.value

    private fun ExpenseEntryState.draft() = listOf(amount, categoryId, date, note)

    private val stored = listOf(shown("48.700"), uuid(1), LocalDate.of(2026, 10, 5), "Pan")

    // --- Opening ---

    @Test
    fun itOpensOnExactlyWhatIsStoredWithNothingToSaveYet() {
        val form = opened()

        assertTrue(form.now.editing)
        assertEquals(stored, form.now.draft())
        assertEquals(Missing.Changes, form.now.missing)
        assertFalse(form.now.canSubmit)
        assertTrue(form.now.canDelete)
        assertEquals(emptyList<YearMonth>(), form.now.pastMonths)
        assertEquals(emptyList<Any>(), commands.edited + commands.created)
    }

    @Test
    fun itIsLoadingUntilTheExpenseAndItsMonthAreRead() {
        reads.stored[bread.id] = bread
        val form = form()
        val before = form.now

        month("2026-10")

        assertTrue(before.loading && !before.canSubmit && !before.canDelete)
        assertFalse(form.now.loading)
        assertEquals(stored, form.now.draft())
    }

    @Test
    fun anExpenseThatIsNotThereAnyMoreSaysSoAndOffersNothingToSend() {
        month("2026-10")

        val form = form()

        assertEquals(EntryError.ExpenseGone, form.now.error)
        assertFalse(form.now.canSubmit || form.now.canDelete || form.now.editable)
    }

    @Test
    fun anExpenseThatCannotBeReadCanBeAskedForAgain() {
        month("2026-10")
        reads.failure = StorageFailure.Unavailable("SQLiteException")
        val form = form()
        val failed = form.now
        reads.failure = null
        reads.stored[bread.id] = bread

        form.onAction(ExpenseEntryAction.Retry)

        assertTrue(failed.loadFailed && !failed.canSubmit && !failed.canDelete)
        assertFalse(form.now.loadFailed)
        assertEquals(stored, form.now.draft())
    }

    @Test
    fun theCategoryItIsInStaysOfferedEvenArchivedAndNoOtherArchivedOneIs() {
        val inArchived = opened(bread.copy(categoryId = categoryId(5)))

        assertEquals(
            listOf("Alimentación", "Vieja", "Café"),
            inArchived.now.categories.map {
                it.name
            }
        )
        assertEquals(uuid(5), inArchived.now.categoryId)
        assertEquals(Missing.Changes, inArchived.now.missing)
    }

    // --- Which months it touches ---

    @Test
    fun anExpenseOfAnEarlierMonthSaysWhichMonthsWouldChange() {
        val september = bread.copy(date = LocalDate.of(2026, 9, 12))
        month("2026-08")
        val form = opened(september)
        val opened = form.now.pastMonths

        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.of(2026, 8, 30)))
        val movedBack = form.now.pastMonths
        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.of(2026, 10, 22)))

        assertEquals(listOf(YearMonth.of(2026, 9)), opened)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), movedBack)
        // Moving it into the current month still takes it out of September.
        assertEquals(listOf(YearMonth.of(2026, 9)), form.now.pastMonths)
    }

    // --- Saving ---

    @Test
    fun savingSendsOneEditOfTheVersionReadThenClosesAndSaysItWasUpdated() {
        val form = opened()
        form.retype("52000")
        form.onAction(ExpenseEntryAction.NoteChanged("Pan y leche"))
        val ready = form.now

        form.onAction(ExpenseEntryAction.Submit)

        assertTrue(ready.canSubmit)
        assertNull(ready.missing)
        val sent = commands.edited.single()
        assertEquals(
            listOf(
                uuid(500),
                7L,
                uuid(100),
                3L,
                uuid(1),
                52_000L,
                LocalDate.of(2026, 10, 5),
                "Pan y leche"
            ),
            listOf(
                sent.operationId.value,
                sent.generation,
                sent.expenseId.value,
                sent.expectedVersion,
                sent.categoryId.value,
                sent.amount.money.minorUnits,
                sent.date,
                sent.note?.value
            )
        )
        assertTrue(form.now.closed)
        assertNull(form.now.recorded)
        assertEquals(CorrectionNotice.Updated, corrections.notice.value)
        assertEquals(emptyList<Any>(), commands.created)
    }

    @Test
    fun puttingEverythingBackAsItWasLeavesNothingToSave() {
        val form = opened()
        form.retype("52000")
        form.onAction(ExpenseEntryAction.CategoryPicked(uuid(2)))
        form.onAction(ExpenseEntryAction.NoteChanged(" Pan  "))
        val changed = form.now.canSubmit

        form.retype("48700")
        form.onAction(ExpenseEntryAction.CategoryPicked(uuid(1)))
        form.onAction(ExpenseEntryAction.Submit)

        assertTrue(changed)
        // The note differs only by spaces that are not stored.
        assertEquals(Missing.Changes, form.now.missing)
        assertEquals(emptyList<Any>(), commands.edited)
    }

    @Test
    fun aSecondPressWhileSavingSendsNothingMore() {
        val form = opened()
        form.retype("52000")
        commands.hold = CompletableDeferred()

        form.onAction(ExpenseEntryAction.Submit)
        val saving = form.now
        form.onAction(ExpenseEntryAction.Submit)
        form.onAction(ExpenseEntryAction.Delete)
        form.onAction(ExpenseEntryAction.Back)

        assertTrue(saving.busy && !saving.editable && !saving.canDelete)
        assertEquals(1, commands.edited.size)
        assertEquals(emptyList<Any>(), commands.deleted)
        assertFalse(form.now.closed)
    }

    @Test
    fun aRefusedEditKeepsWhatWasTypedAndSaysWhatHappened() {
        val refusals = listOf(
            CommandRejection.VersionConflict(current = 4) to EntryError.ExpenseChanged,
            Specific(ExpenseRejection.ExpenseMissing) to EntryError.ExpenseGone,
            CommandRejection.StaleGeneration(current = 8) to EntryError.DataChanged,
            Specific(ExpenseRejection.FutureDate(LocalDate.of(2026, 10, 22))) to
                EntryError.FutureDate,
            CommandRejection.StorageFailed(StorageFailure.Unavailable("x")) to EntryError.NotSaved,
            Specific(ExpenseRejection.Unchanged) to EntryError.NotSaved
        )

        val seen = refusals.map { (reason, _) ->
            commands.editAnswer = { Rejected(reason) }
            val form = opened()
            form.retype("52000")
            form.onAction(ExpenseEntryAction.Submit)
            Triple(form.now.error, form.now.amount, form.now.closed)
        }

        assertEquals(refusals.map { Triple(it.second, shown("52.000"), false) }, seen)
        assertNull(corrections.notice.value)
    }

    @Test
    fun onceItChangedOrIsGoneUnderneathNothingMoreCanBeSentFromThisForm() {
        commands.editAnswer = { Rejected(CommandRejection.VersionConflict(current = 4)) }
        val form = opened()
        form.retype("52000")
        form.onAction(ExpenseEntryAction.Submit)

        form.onAction(ExpenseEntryAction.Submit)
        form.onAction(ExpenseEntryAction.Delete)
        form.retype("1")

        assertFalse(form.now.canSubmit || form.now.canDelete || form.now.editable)
        assertEquals(1, commands.edited.size)
        assertEquals(emptyList<Any>(), commands.deleted)
        assertEquals(shown("52.000"), form.now.amount)
    }

    @Test
    fun movingItToACategoryThatStoppedAcceptingExpensesDeselectsThatCategory() {
        commands.editAnswer = { Rejected(Specific(ExpenseRejection.CategoryArchived)) }
        val form = opened()
        form.onAction(ExpenseEntryAction.CategoryPicked(uuid(2)))

        form.onAction(ExpenseEntryAction.Submit)

        assertEquals(EntryError.CategoryGone, form.now.error)
        assertNull(form.now.categoryId)
        assertEquals(Missing.Category, form.now.missing)
    }

    @Test
    fun anEditWhoseOutcomeIsUnknownIsLookedUpAndNeverSentAgain() {
        commands.editAnswer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val form = opened()
        form.retype("52000")
        form.onAction(ExpenseEntryAction.Submit)
        val unknown = form.now
        commands.found = { Outcome.Success(commands.committed(it)) }

        form.onAction(ExpenseEntryAction.Retry)

        assertEquals(EntryError.Unknown, unknown.error)
        assertFalse(unknown.canSubmit || unknown.editable || unknown.canDelete)
        assertEquals(1, commands.edited.size)
        assertTrue(form.now.closed)
        assertEquals(CorrectionNotice.Updated, corrections.notice.value)
    }

    @Test
    fun afterTheProcessWasKilledMidSaveTheOutcomeIsLookedUpNotSentAgain() {
        val saved = editing()
        val first = opened(saved = saved)
        first.retype("52000")
        commands.hold = CompletableDeferred()
        first.onAction(ExpenseEntryAction.Submit)
        commands.found = { Outcome.Success(commands.committed(it)) }

        val restored = form(saved.afterProcessDeath())

        assertEquals(1, commands.edited.size)
        assertEquals(listOf(commands.edited.single().operationId), commands.looked)
        assertTrue(restored.now.closed)
        assertEquals(emptyList<Any>(), commands.created)
    }

    @Test
    fun aDraftRestoredAfterTheProcessWasKilledKeepsTheChangesAndTheVersionItRead() {
        val saved = editing()
        val first = opened(saved = saved)
        first.retype("52000")
        first.onAction(ExpenseEntryAction.DatePicked(LocalDate.of(2026, 10, 1)))
        // Storage moved on meanwhile; the restored draft must not be read over, and
        // must still be sent against the version it was started from.
        reads.stored[bread.id] = bread.copy(version = 4, amount = pesos(1))

        val restored = form(saved.afterProcessDeath())
        restored.onAction(ExpenseEntryAction.Submit)

        assertEquals(
            listOf(shown("52.000"), uuid(1), LocalDate.of(2026, 10, 1), "Pan"),
            restored.now.draft()
        )
        val sent = commands.edited.single()
        assertEquals(
            listOf(52_000L, 3L),
            listOf(sent.amount.money.minorUnits, sent.expectedVersion)
        )
    }

    // --- Deleting ---

    @Test
    fun deletingFromTheFormDeletesTheVersionReadAndCloses() {
        val form = opened()
        form.retype("52000")

        form.onAction(ExpenseEntryAction.Delete)

        val sent = commands.deleted.single()
        assertEquals(
            listOf(uuid(100), 3L, 7L),
            listOf(sent.expenseId.value, sent.expectedVersion, sent.generation)
        )
        assertTrue(form.now.closed)
        assertEquals(emptyList<Any>(), commands.edited)
        assertTrue(corrections.notice.value is CorrectionNotice.Deleted)
    }

    @Test
    fun aDeletionThatWasRefusedKeepsTheFormOpenAndSaysWhy() {
        val refusals = listOf(
            CommandRejection.VersionConflict(current = 4) to EntryError.ExpenseChanged,
            Specific(ExpenseRejection.ExpenseMissing) to EntryError.ExpenseGone,
            CommandRejection.StaleGeneration(current = 8) to EntryError.DataChanged,
            CommandRejection.StorageFailed(StorageFailure.Unavailable("x")) to EntryError.NotDeleted
        )

        val seen = refusals.map { (reason, _) ->
            commands.deleteAnswer = { Rejected(reason) }
            val form = opened()
            form.onAction(ExpenseEntryAction.Delete)
            form.now.error to form.now.closed
        }

        assertEquals(refusals.map { it.second to false }, seen)
    }

    // --- Leaving ---

    @Test
    fun backAsksOnlyWhenSomethingWasChanged() {
        val untouched = opened()
        untouched.onAction(ExpenseEntryAction.Back)
        val changed = opened()
        changed.retype("52000")

        changed.onAction(ExpenseEntryAction.Back)

        assertTrue(untouched.now.closed)
        assertTrue(changed.now.confirmDiscard && !changed.now.closed)
    }

    // --- Solo este / Este y los siguientes (SELLO-100) ---

    /** The weekly bread, 48.700 on Mondays from 5 Oct at version 2, recorded 5 Oct. */
    private val weekly = StoredSchedule(
        Schedule(
            scheduleId(9),
            ScheduledEntry.Payment(
                categoryId(1),
                pesos(48_700),
                (Note.of("Pan") as Outcome.Success).value
            ),
            (
                Recurrence.of(
                    (RepeatRule.EveryWeeks.of(1) as Outcome.Success).value,
                    LocalDate.parse("2026-10-05"),
                    null,
                    emptyList()
                ) as Outcome.Success
                ).value
        ),
        sequence = 1,
        handled = Occurrence(LocalDate.parse("2026-10-19"), 1),
        version = 2,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private val recorded = bread.copy(
        origin = EntryOrigin(scheduleId(9), Occurrence(LocalDate.parse("2026-10-05"), 1))
    )

    private fun fromSchedule(scheduleExists: Boolean = true): ExpenseEntryViewModel {
        if (scheduleExists) scheduleBook.schedules += weekly
        return opened(recorded)
    }

    @Test
    fun newTermsOnARecordedPaymentAskAndSoloEsteCorrectsOnlyIt() {
        val form = fromSchedule()
        form.retype("52000")
        form.onAction(ExpenseEntryAction.Submit)
        val asked = form.now
        form.onAction(ExpenseEntryAction.FollowingChosen(alsoFollowing = false))

        assertTrue(asked.askFollowing)
        assertEquals("Pan", asked.following?.name)
        assertEquals(1, commands.edited.size)
        assertEquals(pesos(52_000), commands.edited.single().amount)
        assertTrue(schedules.submitted.isEmpty())
        assertEquals(CorrectionNotice.Updated, corrections.notice.value)
    }

    @Test
    fun esteYLosSiguientesChangesTheScheduleFirstThenThisPayment() {
        val form = fromSchedule()
        form.retype("52000")
        form.onAction(ExpenseEntryAction.Submit)
        form.onAction(ExpenseEntryAction.FollowingChosen(alsoFollowing = true))

        // The rule, its start and end stay; only the terms change, from tomorrow.
        assertEquals(
            listOf(
                ChangeSchedule(
                    (OperationId.of(uuid(500)) as Outcome.Success).value,
                    7,
                    scheduleId(9),
                    2,
                    ScheduledEntry.Payment(
                        categoryId(1),
                        pesos(52_000),
                        (Note.of("Pan") as Outcome.Success).value
                    ),
                    weekly.schedule.recurrence.rule,
                    LocalDate.parse("2026-10-05"),
                    null
                )
            ),
            schedules.submitted
        )
        assertEquals(1, commands.edited.size)
        assertEquals(
            CorrectionNotice.ScheduleChanged(ScheduleChange.Changed, income = false),
            corrections.notice.value
        )
    }

    @Test
    fun aDateAloneOrAScheduleThatIsGoneAsksNothing() {
        val moved = fromSchedule()
        moved.onAction(ExpenseEntryAction.DatePicked(LocalDate.parse("2026-10-06")))
        moved.onAction(ExpenseEntryAction.Submit)
        scheduleBook.schedules.clear()
        val gone = fromSchedule(scheduleExists = false)
        gone.retype("52000")
        gone.onAction(ExpenseEntryAction.Submit)

        assertFalse(moved.now.askFollowing)
        assertFalse(gone.now.askFollowing)
        assertEquals(2, commands.edited.size)
        assertTrue(schedules.submitted.isEmpty())
    }

    @Test
    fun refusedNewTermsWriteNothingAndTheChoiceIsAskedAgain() {
        val form = fromSchedule()
        form.retype("52000")
        schedules.answer = { CommandOutcome.Rejected(Specific(ScheduleRejection.CatchUpPending)) }
        form.onAction(ExpenseEntryAction.Submit)
        form.onAction(ExpenseEntryAction.FollowingChosen(alsoFollowing = true))
        val refused = form.now
        form.onAction(ExpenseEntryAction.Submit)

        assertEquals(EntryError.FollowingNotSaved, refused.error)
        assertTrue(commands.edited.isEmpty())
        assertTrue(form.now.askFollowing)
    }

    @Test
    fun whenOnlyTheFollowingChangedSavingAgainCorrectsThisOneWithoutResending() {
        val form = fromSchedule()
        form.retype("52000")
        commands.editAnswer =
            {
                CommandOutcome.Rejected(
                    CommandRejection.StorageFailed(StorageFailure.Unavailable("x"))
                )
            }
        form.onAction(ExpenseEntryAction.Submit)
        form.onAction(ExpenseEntryAction.FollowingChosen(alsoFollowing = true))
        val half = form.now
        commands.editAnswer = {
            CommandOutcome.Committed(
                commands.committed(it.operationId, it.expenseId, OperationKind.EditExpense),
                replayed = false
            )
        }
        form.onAction(ExpenseEntryAction.Submit)

        assertTrue(half.followingSaved)
        assertEquals(EntryError.NotSaved, half.error)
        assertFalse(form.now.askFollowing)
        assertEquals(1, schedules.submitted.size)
        assertEquals(2, commands.edited.size)
        assertTrue(form.now.closed)
    }
}
