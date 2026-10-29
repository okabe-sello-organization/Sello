package com.software.sello.feature.expense

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.SnapshotState
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.feature.recurrence.HandScheduleCommands
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.feature.recurrence.scheduleId
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatKind
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseFormViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val commands = HandExpenseCommands()
    private val reads = HandExpenseReads()
    private val snapshots = HandSnapshots()
    private val scheduleBook = HandScheduleReads()
    private val schedules = HandScheduleCommands(scheduleBook)
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

    /** The reference's month: 937.200 left overall, 287.600 left in Alimentación. */
    private val food = categoryIn(1, "Alimentación", 900_000, icon = "restaurant")
    private val coffee = categoryIn(2, "Café", 50_000, icon = "local_cafe")
    private val transport = categoryIn(3, "Transporte", null)
    private val bills = categoryIn(4, "Servicios", 650_000)
    private val archived = categoryIn(5, "Vieja", 100_000, archived = true)
    private val spending = listOf(
        storedExpense(100, 1, "2026-10-05", 612_400),
        storedExpense(101, 2, "2026-10-06", 41_200),
        storedExpense(102, 3, "2026-10-07", 86_000),
        storedExpense(103, 4, "2026-10-08", 400)
    )

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun shown(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    /** What the frame is told: one for the whole test, as in the app. */
    private val corrections by lazy {
        Corrections(
            commands,
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            {
                Duration.ZERO
            },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main)
        )
    }

    private val said get() = corrections.notice.value

    private fun form(saved: SavedStateHandle = SavedStateHandle()) = ExpenseEntryViewModel(
        saved,
        commands,
        reads,
        snapshots,
        clock,
        { uuid(nextId++) },
        money,
        corrections,
        schedules,
        scheduleBook
    )

    /** A form over a month with the given categories, already read. */
    private fun ready(
        vararg categories: com.software.sello.domain.policy.CategoryInMonth = arrayOf(food, coffee),
        saved: SavedStateHandle = SavedStateHandle()
    ): ExpenseEntryViewModel {
        snapshots.ready(monthOf("2026-10-22", categories.toList()))
        return form(saved)
    }

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private fun ExpenseEntryViewModel.type(digits: String) =
        digits.forEach { onAction(ExpenseEntryAction.Digit(it.digitToInt())) }

    private fun ExpenseEntryViewModel.pick(number: Int) =
        onAction(ExpenseEntryAction.CategoryPicked(uuid(number)))

    private fun ExpenseEntryViewModel.submit() = onAction(ExpenseEntryAction.Submit)

    /** No category is chosen for the person; these tests choose Alimentación themselves. */
    private fun ExpenseEntryViewModel.chosen() = also { it.pick(1) }

    private val ExpenseEntryViewModel.now get() = state.value

    // --- Opening ---

    @Test
    fun itLoadsThenOffersOnlyCategoriesThatCanTakeAnExpense() {
        val form = form()
        val loading = form.now

        snapshots.ready(monthOf("2026-10-22", listOf(food, archived, coffee)))

        assertTrue(loading.loading && !loading.canSubmit)
        assertEquals(listOf(october), snapshots.observed)
        assertEquals(
            listOf("Alimentación" to "restaurant", "Café" to "local_cafe"),
            form.now.categories.map { it.name to it.iconKey }
        )
        assertEquals(LocalDate.of(2026, 10, 22), form.now.date)
        assertEquals(Missing.Amount, form.now.missing)
        assertNull(form.now.amount)
        assertNull(form.now.recorded)
    }

    @Test
    fun whenTheCategoriesCannotBeReadItSaysSoAndCanBeRetried() {
        val form = form()
        val failure = SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException"))
        snapshots.of(october).value = SnapshotState.Failed(failure, lastGood = null)
        val failed = form.now

        form.onAction(ExpenseEntryAction.Retry)
        snapshots.ready(monthOf("2026-10-22", listOf(food)))

        assertTrue(failed.loadFailed && !failed.canSubmit)
        assertEquals(listOf(october, october), snapshots.observed)
        assertFalse(form.now.loadFailed)
    }

    // --- Which category starts selected ---

    @Test
    fun noCategoryIsChosenWhenTheFormOpensEvenWithOnlyOneOrOneUsedBefore() {
        val single = ready(food)
        reads.lastUsed = categoryId(2)
        val withHistory = ready(food, coffee)

        assertNull(single.now.categoryId)
        assertNull(withHistory.now.categoryId)
        assertEquals(Missing.Category, single.also { it.type("5000") }.now.missing)
        assertFalse(single.now.canSubmit)
    }

    @Test
    fun aCategoryCreatedFromNuevaIsChosenWhenTheEditorReturns() {
        val saved = SavedStateHandle()
        snapshots.ready(monthOf("2026-10-22", listOf(food)))
        val form = form(saved)
        form.type("5000")

        // The editor saved Café and handed back its identifier before the month had it.
        form.onAction(ExpenseEntryAction.CategoryCreated(uuid(2)))
        val beforeTheMonthHasIt = form.now.categoryId
        snapshots.ready(monthOf("2026-10-22", listOf(food, coffee)))

        assertNull(beforeTheMonthHasIt)
        assertEquals(uuid(2), form.now.categoryId)
        assertEquals(shown("5.000"), form.now.amount)
    }

    @Test
    fun aCategoryAskedForByTheCallerIsChosenOnlyIfItCanTakeAnExpense() {
        reads.lastUsed = categoryId(1)
        fun asking(id: String) = ready(
            food,
            coffee,
            archived,
            saved = SavedStateHandle(mapOf(ExpenseEntryViewModel.KEY_PREFILL_CATEGORY to id))
        ).now.categoryId

        assertEquals(uuid(2), asking(uuid(2)))
        // Archived, unknown or malformed: nothing is selected, not even the last used.
        assertNull(asking(uuid(5)))
        assertNull(asking(uuid(77)))
        assertNull(asking("' OR 1=1 --"))
    }

    @Test
    fun aValidPrefilledAmountIsShownAndAnInvalidOneIsSimplyAbsent() {
        fun asking(amount: String) = ready(
            saved = SavedStateHandle(mapOf(ExpenseEntryViewModel.KEY_PREFILL_AMOUNT to amount))
        ).now

        assertEquals(shown("48.700"), asking("48700").amount)
        for (bad in listOf("1e3", "0", "-5", "1000000000000", "12.34")) {
            val state = asking(bad)
            assertNull(bad, state.amount)
            assertNull(bad, state.amountText)
            assertNull(bad, state.amountProblem)
        }
    }

    // --- The amount ---

    @Test
    fun theKeypadBuildsTheAmountDigitByDigit() {
        val form = ready()

        form.type("48")
        form.onAction(ExpenseEntryAction.TripleZero)
        val typed = form.now.amount
        form.onAction(ExpenseEntryAction.Backspace)
        val shorter = form.now.amount
        form.onAction(ExpenseEntryAction.ClearAmount)

        assertEquals(shown("48.000"), typed)
        assertEquals(shown("4.800"), shorter)
        assertNull(form.now.amount)
        assertEquals(Missing.Amount, form.now.missing)
    }

    @Test
    fun aThirteenthDigitIsRefusedAndSaidSoWithoutChangingTheAmount() {
        val form = ready()
        form.type("999999999999")
        val full = form.now

        form.type("9")
        form.onAction(ExpenseEntryAction.TripleZero)

        assertEquals(shown("999.999.999.999"), full.amount)
        assertEquals(0 to false, full.limitPulse to full.atLimit)
        assertEquals(shown("999.999.999.999"), form.now.amount)
        assertEquals(2 to true, form.now.limitPulse to form.now.atLimit)
    }

    @Test
    fun maximoDoceDigitosStaysUntilTheNextKeyThatChangesTheAmount() {
        val form = ready()
        form.type("999999999999")
        form.type("9")
        val refused = form.now.atLimit
        form.onAction(ExpenseEntryAction.Backspace)
        val afterBackspace = form.now.atLimit
        form.type("9")
        form.type("9")
        form.onAction(ExpenseEntryAction.ClearAmount)

        assertEquals(true to false, refused to afterBackspace)
        assertFalse(form.now.atLimit)
    }

    @Test
    fun pastedTextIsUsedWholeOrKeptAsItWasWithTheReason() {
        val form = ready()
        form.onAction(ExpenseEntryAction.Paste("48.700"))
        val good = form.now.amount
        val cases = listOf(
            "1e3" to AmountProblem.NotAnAmount,
            "12.34" to AmountProblem.NotAnAmount,
            "$48.700" to AmountProblem.NotAnAmount,
            "0" to AmountProblem.Zero,
            "1000000000000" to AmountProblem.TooLarge
        )

        assertEquals(shown("48.700"), good)
        for ((text, problem) in cases) {
            form.onAction(ExpenseEntryAction.Paste(text))

            assertNull(text, form.now.amount)
            assertEquals(text, text, form.now.amountText)
            assertEquals(text, problem, form.now.amountProblem)
            assertEquals(text, Missing.Amount, form.now.missing)
        }
        // Typing after a refused paste starts again; it never builds on the refused text.
        form.type("7")
        assertEquals(shown("7"), form.now.amount)
        assertNull(form.now.amountText)
    }

    // --- What is missing ---

    @Test
    fun theButtonStaysOffAndSaysWhatIsMissingInOrder() {
        val form = ready()
        val nothing = form.now
        form.type("5000")
        val noCategory = form.now
        form.pick(1)
        val complete = form.now
        form.onAction(ExpenseEntryAction.NoteChanged("a".repeat(61)))
        val longNote = form.now
        form.submit()

        assertEquals(Missing.Amount to false, nothing.missing to nothing.canSubmit)
        assertEquals(Missing.Category to false, noCategory.missing to noCategory.canSubmit)
        assertEquals(null to true, complete.missing to complete.canSubmit)
        assertEquals(Missing.Note to NoteProblem.TooLong, longNote.missing to longNote.noteProblem)
        assertEquals("a".repeat(61), longNote.note)
        assertEquals(emptyList<CreateExpense>(), commands.created)
    }

    @Test
    fun aCategoryThatIsNotOfferedCannotBePicked() {
        val form = ready(food, coffee, archived)

        form.pick(5)
        form.pick(77)

        assertNull(form.now.categoryId)
    }

    // --- The note, written in its composer ---

    @Test
    fun theNoteOpensOnWhatWasWrittenAndOnlyAValidOneIsKept() {
        val form = ready(food)
        form.onAction(ExpenseEntryAction.OpenNote)
        val opened = form.now
        form.onAction(ExpenseEntryAction.NoteDraftChanged("Pan"))
        form.onAction(ExpenseEntryAction.NoteDone)
        val kept = form.now
        form.onAction(ExpenseEntryAction.OpenNote)
        form.onAction(ExpenseEntryAction.NoteDraftChanged("Pan y café"))
        form.onAction(ExpenseEntryAction.NoteCancelled)

        assertEquals("", opened.noteDraft)
        assertEquals("Pan" to null, kept.note to kept.noteDraft)
        assertEquals("Pan" to null, form.now.note to form.now.noteDraft)
    }

    @Test
    fun overSixtyCharactersAsPeopleCountThemTheTextIsKeptAndCannotBeFinished() {
        val form = ready(food)
        form.onAction(ExpenseEntryAction.OpenNote)
        // Sixty faces are sixty characters, though each is two UTF-16 units.
        form.onAction(ExpenseEntryAction.NoteDraftChanged("\uD83D\uDE00".repeat(60)))
        val sixty = form.now
        form.onAction(ExpenseEntryAction.NoteDraftChanged("\uD83D\uDE00".repeat(61)))
        form.onAction(ExpenseEntryAction.NoteDone)

        assertEquals(60 to null, sixty.noteDraftLength to sixty.noteDraftProblem)
        assertEquals(
            61 to NoteProblem.TooLong,
            form.now.noteDraftLength to form.now.noteDraftProblem
        )
        // Still open, the long text still there, the note itself untouched.
        assertEquals("\uD83D\uDE00".repeat(61), form.now.noteDraft)
        assertEquals("", form.now.note)
    }

    @Test
    fun anOpenComposerAndItsTextComeBackAfterTheProcessIsKilled() {
        val saved = SavedStateHandle()
        val first = ready(food, saved = saved)
        first.onAction(ExpenseEntryAction.OpenNote)
        first.onAction(ExpenseEntryAction.NoteDraftChanged("Almuer"))

        val restored = form(saved.afterProcessDeath()).now

        assertEquals("Almuer", restored.noteDraft)
        assertEquals("", restored.note)
    }

    @Test
    fun whileSavingTheNoteCannotBeOpened() {
        commands.hold = CompletableDeferred()
        val form = ready(food)
        form.type("5000")
        form.pick(1)
        form.submit()

        form.onAction(ExpenseEntryAction.OpenNote)

        assertNull(form.now.noteDraft)
    }

    @Test
    fun aDayAfterTodayCannotBeChosenAndAnEarlierOneCan() {
        val form = ready(food)
        form.onAction(ExpenseEntryAction.OpenDatePicker)
        val open = form.now.datePickerOpen

        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.of(2026, 10, 23)))
        val afterFuture = form.now
        form.onAction(ExpenseEntryAction.OpenDatePicker)
        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.of(2024, 2, 29)))

        assertTrue(open)
        assertEquals(
            LocalDate.of(2026, 10, 22) to false,
            afterFuture.date to afterFuture.datePickerOpen
        )
        assertEquals(LocalDate.of(2024, 2, 29), form.now.date)
        assertEquals(LocalDate.of(2026, 10, 22), form.now.today)
    }

    @Test
    fun aDraftKeepsTheDayItWasStartedWhenTheDayChanges() {
        val form = ready(food)

        clock.goTo("2026-10-23")

        assertEquals(LocalDate.of(2026, 10, 22), form.now.date)
        assertEquals(LocalDate.of(2026, 10, 23), form.now.today)
    }

    // --- Saving ---

    @Test
    fun aCompleteDraftSendsOneCommandWithExactlyWhatWasEntered() {
        snapshots.ready(monthOf("2026-10-22", listOf(food, coffee), generation = 4))
        val form = form()
        form.type("48700")
        form.pick(2)
        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.of(2026, 10, 20)))
        form.onAction(ExpenseEntryAction.NoteChanged("  Pan y café "))

        form.submit()

        val command = commands.created.single()
        assertEquals(uuid(500), command.operationId.value)
        assertEquals(4L, command.generation)
        assertEquals(categoryId(2), command.categoryId)
        assertEquals(pesos(48_700), command.amount)
        assertEquals(LocalDate.of(2026, 10, 20), command.date)
        assertEquals("Pan y café", command.note?.value)
    }

    @Test
    fun anEmptyNoteIsNoNote() {
        val form = ready(food).chosen()
        form.type("5000")
        form.onAction(ExpenseEntryAction.NoteChanged("   "))

        form.submit()

        assertNull(commands.created.single().note)
    }

    @Test
    fun aConfirmedSaveClosesTheFormAndSaysWhatStorageHoldsNotWhatWasTyped() {
        commands.hold = CompletableDeferred()
        // Storage is the authority: what it returns is shown, whatever the draft said.
        reads.stored[expenseId(900)] = storedExpense(900, 1, "2026-10-21", 10_000, "Pan")
        val form = ready(food).chosen()
        form.type("10000")

        form.submit()
        val saving = form.now
        val saidWhileSaving = said
        commands.hold!!.complete(Unit)

        assertTrue(saving.busy)
        assertNull(saving.recorded)
        assertFalse(saving.closed)
        assertNull(saidWhileSaving)
        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
        assertEquals(CorrectionNotice.ExpenseRecorded(shown("10.000"), "Alimentación"), said)
        assertFalse(form.now.busy)
        assertEquals(1, commands.created.size)
    }

    @Test
    fun whileSavingASecondTapAndAnyEditAreIgnored() {
        commands.hold = CompletableDeferred()
        val form = ready(food, coffee)
        form.type("10000")
        form.pick(1)

        form.submit()
        form.submit()
        form.type("9")
        form.pick(2)
        form.onAction(ExpenseEntryAction.NoteChanged("otra"))
        form.onAction(ExpenseEntryAction.Back)

        assertEquals(1, commands.created.size)
        assertEquals(shown("10.000"), form.now.amount)
        assertEquals(uuid(1) to "", form.now.categoryId to form.now.note)
        assertFalse(form.now.confirmDiscard || form.now.closed)
        assertFalse(form.now.canSubmit || form.now.editable)
    }

    @Test
    fun aSavedExpenseThatCannotBeReadBackIsStillSaidToBeSavedAndNotSentAgain() {
        reads.failure = StorageFailure.Unavailable("SQLiteException")
        val form = ready(food).chosen()
        form.type("10000")

        form.submit()
        form.submit()

        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
        assertEquals(CorrectionNotice.ExpenseRecorded(amount = null, categoryName = null), said)
        assertFalse(form.now.canSubmit)
        assertEquals(1, commands.created.size)
    }

    // --- Rejections: certain, nothing saved, the draft stays ---

    @Test
    fun aCategoryThatStoppedAcceptingExpensesIsDeselectedAndTheNextTryIsANewOperation() {
        commands.answer = { ExpenseCommandOutcome.Rejected(ExpenseRejection.CategoryArchived) }
        val form = ready(food, coffee)
        form.type("10000")
        form.pick(1)

        form.submit()
        val refused = form.now
        commands.answer =
            { ExpenseCommandOutcome.Committed(commands.committed(it.operationId), false) }
        form.pick(2)
        form.submit()

        assertEquals(EntryError.CategoryGone, refused.error)
        assertNull(refused.categoryId)
        assertEquals(shown("10.000"), refused.amount)
        assertNull(refused.recorded)
        assertEquals(listOf(uuid(500), uuid(501)), commands.created.map { it.operationId.value })
    }

    @Test
    fun everyOtherRejectionKeepsTheDraftAndSaysNothingWasSaved() {
        val cases = listOf(
            ExpenseRejection.CategoryMissing to EntryError.CategoryGone,
            ExpenseRejection.FutureDate(LocalDate.of(2026, 10, 21)) to EntryError.FutureDate,
            ExpenseRejection.InvalidDate to EntryError.InvalidDate,
            ExpenseRejection.StaleGeneration(current = 2) to EntryError.DataChanged,
            ExpenseRejection.OperationConflict to EntryError.NotSaved,
            ExpenseRejection.StorageFailed(StorageFailure.Unavailable("SQLiteException")) to
                EntryError.NotSaved
        )

        for ((reason, error) in cases) {
            commands.answer = { ExpenseCommandOutcome.Rejected(reason) }
            val form = ready(food).chosen()
            form.type("10000")
            form.onAction(ExpenseEntryAction.NoteChanged("Pan"))

            form.submit()

            assertEquals("$reason", error, form.now.error)
            assertEquals("$reason", shown("10.000") to "Pan", form.now.amount to form.now.note)
            assertNull("$reason", form.now.recorded)
            assertNull("$reason", said)
            assertFalse("$reason", form.now.busy)
        }
    }

    @Test
    fun afterTheDataWasReplacedTheNextAttemptUsesTheNewGeneration() {
        commands.answer = { ExpenseCommandOutcome.Rejected(ExpenseRejection.StaleGeneration(2)) }
        val form = ready(food).chosen()
        form.type("10000")
        form.submit()

        snapshots.ready(monthOf("2026-10-22", listOf(food), generation = 2))
        commands.answer =
            { ExpenseCommandOutcome.Committed(commands.committed(it.operationId), false) }
        form.submit()

        assertEquals(listOf(1L, 2L), commands.created.map { it.generation })
    }

    // --- An outcome that is not known ---

    @Test
    fun anUnknownOutcomeIsSettledByAskingAboutTheSameOperationNeverBySendingAgain() {
        commands.answer = { ExpenseCommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Success(commands.committed(it)) }
        val form = ready(food).chosen()
        form.type("10000")

        form.submit()

        assertEquals(1, commands.created.size)
        assertEquals(listOf(uuid(500)), commands.looked.map { it.value })
        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
    }

    @Test
    fun anUnknownOutcomeThatWasNotSavedReturnsToTheDraftWithNothingRecorded() {
        commands.answer = { ExpenseCommandOutcome.OutcomeUnknown(it.operationId) }
        val form = ready(food).chosen()
        form.type("10000")

        form.submit()

        assertNull(form.now.recorded)
        assertNull(said)
        assertFalse(form.now.closed)
        assertNull(form.now.error)
        assertTrue(form.now.editable && form.now.canSubmit)
        assertEquals(shown("10.000"), form.now.amount)
    }

    @Test
    fun whileTheOutcomeCannotBeReadTheDraftIsLockedAndRetryOnlyAsksAgain() {
        commands.answer = { ExpenseCommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("SQLiteException")) }
        val form = ready(food).chosen()
        form.type("10000")

        form.submit()
        val unknown = form.now
        form.type("9")
        form.submit()
        commands.found = { Outcome.Success(commands.committed(it)) }
        form.onAction(ExpenseEntryAction.Retry)

        assertEquals(EntryError.Unknown, unknown.error)
        assertFalse(unknown.editable || unknown.canSubmit || unknown.closed)
        assertNull(unknown.recorded)
        assertEquals(1, commands.created.size)
        assertEquals(listOf(uuid(500), uuid(500)), commands.looked.map { it.value })
        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
    }

    // --- Rotation and the process being killed ---

    @Test
    fun theDraftComesBackAfterTheProcessIsKilled() {
        val saved = SavedStateHandle()
        val first = ready(food, coffee, saved = saved)
        first.type("48700")
        first.pick(2)
        first.onAction(ExpenseEntryAction.DatePicked(LocalDate.of(2026, 10, 20)))
        first.onAction(ExpenseEntryAction.NoteChanged("Pan"))

        clock.goTo("2026-10-25")
        val restored = form(saved.afterProcessDeath()).now

        assertEquals(shown("48.700"), restored.amount)
        assertEquals(uuid(2), restored.categoryId)
        assertEquals(LocalDate.of(2026, 10, 20), restored.date)
        assertEquals("Pan", restored.note)
        assertEquals(emptyList<CreateExpense>(), commands.created)
    }

    @Test
    fun aDraftStartedTodayIsStillDatedThatDayWhenItComesBackTomorrow() {
        val saved = SavedStateHandle()
        ready(food, saved = saved).type("5000")

        clock.goTo("2026-10-23")
        val restored = form(saved.afterProcessDeath()).now

        assertEquals(LocalDate.of(2026, 10, 22), restored.date)
        assertEquals(LocalDate.of(2026, 10, 23), restored.today)
    }

    @Test
    fun aRejectedAttemptLeavesNothingToRecoverAfterARestart() {
        commands.answer = { ExpenseCommandOutcome.Rejected(ExpenseRejection.CategoryArchived) }
        val saved = SavedStateHandle()
        val first = ready(food, saved = saved)
        first.type("10000")
        first.submit()

        val restored = form(saved.afterProcessDeath())

        // The rejection was certain, so there is nothing to ask storage about.
        assertEquals(emptyList<Any>(), commands.looked)
        assertNull(restored.now.recorded)
        assertEquals(shown("10.000"), restored.now.amount)
    }

    @Test
    fun aRefusedPasteAlsoComesBackAsItWas() {
        val saved = SavedStateHandle()
        ready(food, saved = saved).onAction(ExpenseEntryAction.Paste("1e3"))

        val restored = form(saved.afterProcessDeath()).now

        assertEquals(
            "1e3" to AmountProblem.NotAnAmount,
            restored.amountText to restored.amountProblem
        )
        assertNull(restored.amount)
    }

    @Test
    fun aSaveInterruptedByTheProcessDyingIsRecoveredFromItsReceiptWithoutSendingAgain() {
        commands.hold = CompletableDeferred()
        reads.stored[expenseId(900)] = storedExpense(900, 1, "2026-10-22", 10_000)
        val saved = SavedStateHandle()
        val first = ready(food, saved = saved).chosen()
        first.type("10000")
        first.submit()
        // The process dies here: the command left, its answer never arrived.
        val afterDeath = saved.afterProcessDeath()
        commands.found = { Outcome.Success(commands.committed(it)) }

        val restored = form(afterDeath)

        assertEquals(1, commands.created.size)
        assertEquals(listOf(uuid(500)), commands.looked.map { it.value })
        assertTrue(restored.now.closed)
        assertEquals(CorrectionNotice.ExpenseRecorded(shown("10.000"), "Alimentación"), said)
    }

    @Test
    fun anInterruptedSaveThatNeverCommittedLeavesTheDraftAndTheNextTryIsANewOperation() {
        commands.hold = CompletableDeferred()
        val saved = SavedStateHandle()
        val first = ready(food, saved = saved).chosen()
        first.type("10000")
        first.submit()
        val afterDeath = saved.afterProcessDeath()
        commands.hold = null

        val restored = form(afterDeath)
        val waiting = restored.now
        restored.submit()

        assertNull(waiting.recorded)
        assertEquals(shown("10.000"), waiting.amount)
        assertTrue(waiting.canSubmit)
        assertEquals(listOf(uuid(500), uuid(501)), commands.created.map { it.operationId.value })
    }

    @Test
    fun aSaveConfirmedJustBeforeTheProcessDiedIsSaidAfterTheRestartWithoutSendingAgain() {
        reads.stored[expenseId(900)] = storedExpense(900, 1, "2026-10-22", 10_000)
        val saved = SavedStateHandle()
        val first = ready(food, saved = saved).chosen()
        first.type("10000")
        first.submit()
        val readsBefore = reads.reads

        corrections.dismiss()
        val restored = form(saved.afterProcessDeath())
        restored.submit()
        restored.type("5")

        assertEquals(uuid(900) to true, restored.now.recorded to restored.now.closed)
        assertEquals(CorrectionNotice.ExpenseRecorded(shown("10.000"), "Alimentación"), said)
        assertEquals(1, commands.created.size)
        assertEquals(emptyList<Any>(), commands.looked)
        assertEquals(readsBefore + 1, reads.reads)
    }

    // --- Leaving ---

    @Test
    fun backLeavesAnUntouchedFormAsksAboutATypedOneAndASavedOneHasAlreadyClosed() {
        val untouched = ready(food, coffee)
        untouched.pick(1)
        untouched.onAction(ExpenseEntryAction.Back)

        val typed = ready(food).chosen()
        typed.type("5")
        typed.onAction(ExpenseEntryAction.Back)
        val asked = typed.now
        typed.onAction(ExpenseEntryAction.KeepEditing)
        val kept = typed.now
        typed.onAction(ExpenseEntryAction.Back)
        typed.onAction(ExpenseEntryAction.DiscardConfirmed)

        val saved = ready(food).chosen()
        saved.type("10000")
        saved.submit()
        val closedBySaving = saved.now.closed
        saved.onAction(ExpenseEntryAction.Back)

        assertTrue(untouched.now.closed)
        assertTrue(asked.confirmDiscard && !asked.closed)
        assertEquals(false to shown("5"), kept.confirmDiscard to kept.amount)
        assertTrue(typed.now.closed)
        assertTrue(closedBySaving && saved.now.closed && !saved.now.confirmDiscard)
        assertEquals(1, commands.created.size)
    }

    @Test
    fun aNoteAloneAlsoCountsAsSomethingTyped() {
        val form = ready(food)
        form.onAction(ExpenseEntryAction.NoteChanged("Pan"))

        form.onAction(ExpenseEntryAction.Back)

        assertTrue(form.now.confirmDiscard)
    }

    // --- Se repite: a schedule instead of one expense (SELLO-100) ---

    private fun ExpenseEntryViewModel.repeat(action: RepeatAction) =
        onAction(ExpenseEntryAction.Repeat(action))

    private fun rule(day: Int) = (RepeatRule.Monthly.of(day) as Outcome.Success).value

    private fun operationIdOf(text: String) = (OperationId.of(text) as Outcome.Success).value

    @Test
    fun seRepiteKeepsASchedulePaymentAndRecordsNothingItself() {
        val form = ready().chosen()
        form.type("950000")
        form.repeat(RepeatAction.Open)
        val sheet = form.now.repeat
        form.repeat(RepeatAction.DayToggled(5))
        form.repeat(RepeatAction.Done)
        val chosen = form.now.repeat

        form.submit()

        // It opens on the entry's day, 22; the 5th comes next on jue 5 nov.
        assertEquals(listOf(22), sheet.sheet?.days)
        assertEquals(LocalDate.parse("2026-10-22"), sheet.sheetNext)
        assertEquals(LocalDate.parse("2026-11-05"), chosen.next)
        assertNull(chosen.sheet)
        assertEquals(emptyList<CreateExpense>(), commands.created)
        assertEquals(
            listOf(
                CreateSchedule(
                    operationIdOf(uuid(500)),
                    1,
                    ScheduledEntry.Payment(categoryId(1), pesos(950_000), null),
                    rule(5),
                    LocalDate.parse("2026-10-22"),
                    null
                )
            ),
            schedules.submitted
        )
        // Said from what storage holds: the category names a payment without a note.
        assertEquals(
            CorrectionNotice.ScheduleSaved(
                "Alimentación",
                rule(5),
                LocalDate.parse("2026-10-22"),
                income = false
            ),
            said
        )
        assertTrue(form.now.closed)
    }

    @Test
    fun whileSeRepiteIsOnTheDateIsTodayOrLaterAndOffItIsTodayOrEarlier() {
        val form = ready().chosen()
        form.type("950000")
        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.parse("2026-10-10")))
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.Done)
        // A schedule starts today or later: the 10th moved to today.
        val on = form.now.date
        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.parse("2026-11-05")))
        val later = form.now.date
        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.parse("2026-10-21")))
        val earlierRefused = form.now.date
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.Remove)

        assertEquals(LocalDate.parse("2026-10-22"), on)
        assertEquals(LocalDate.parse("2026-11-05"), later)
        assertEquals(LocalDate.parse("2026-11-05"), earlierRefused)
        // Off again, a single expense cannot be dated after today.
        assertEquals(LocalDate.parse("2026-10-22"), form.now.date)
        assertNull(form.now.repeat.choice)
    }

    @Test
    fun anIncompleteChoiceIsNotKeptAndDismissingKeepsTheOneBefore() {
        val form = ready().chosen()
        form.type("950000")
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.Done)
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.KindPicked(RepeatKind.TwiceMonthly))
        form.repeat(RepeatAction.Done)
        val incomplete = form.now.repeat
        form.repeat(RepeatAction.Dismiss)

        // Twice a month with one day: nothing to keep, the sheet stays open.
        assertFalse(incomplete.sheetComplete)
        assertEquals(RepeatKind.TwiceMonthly, incomplete.sheet?.kind)
        assertEquals(rule(22), form.now.repeat.choice?.rule())
        assertNull(form.now.repeat.sheet)
    }

    @Test
    fun anEndThatLeavesNothingToRecordStopsTheSave() {
        val form = ready().chosen()
        form.type("950000")
        form.onAction(ExpenseEntryAction.DatePicked(LocalDate.parse("2026-10-22")))
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.DayToggled(5))
        // Ending on the 30th: the 5th of November never comes.
        form.repeat(RepeatAction.EndPicked(LocalDate.parse("2026-10-30")))
        val sheet = form.now.repeat
        form.repeat(RepeatAction.Done)

        assertNull(sheet.sheetNext)
        assertFalse(sheet.sheetComplete)
        assertNull(form.now.repeat.choice)
        // An end before the start is not taken at all.
        form.repeat(RepeatAction.EndPicked(LocalDate.parse("2026-10-21")))
        assertEquals(LocalDate.parse("2026-10-30"), form.now.repeat.sheet?.end)
    }

    @Test
    fun aRefusedScheduleKeepsTheDraftAndSaysWhy() {
        val form = ready().chosen()
        form.type("950000")
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.Done)
        schedules.answer = {
            ScheduleCommandOutcome.Rejected(
                ScheduleRejection.StartTooEarly(LocalDate.parse("2026-10-23"))
            )
        }
        form.submit()
        val early = form.now
        schedules.answer = { ScheduleCommandOutcome.Rejected(ScheduleRejection.CategoryArchived) }
        form.submit()

        assertEquals(EntryError.StartTooEarly, early.error)
        assertEquals(shown("950.000"), early.amount)
        assertEquals(rule(22), early.repeat.choice?.rule())
        assertEquals(EntryError.CategoryGone, form.now.error)
        assertNull(form.now.categoryId)
        assertEquals(emptyList<CreateExpense>(), commands.created)
        assertNull(said)
    }

    @Test
    fun anUnknownOutcomeAsksTheScheduleCommandsNeverTheExpenseOnes() {
        val saved = SavedStateHandle()
        val form = ready(saved = saved).chosen()
        form.type("950000")
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.Done)
        schedules.answer = { ScheduleCommandOutcome.OutcomeUnknown(it.operationId) }
        schedules.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        form.submit()
        val unknown = form.now

        // The process stops; storage then answers that it was saved.
        schedules.found = { Outcome.Success(schedules.committed(it)) }
        scheduleBook.keep(schedules.submitted.single() as CreateSchedule, scheduleId(700))
        val restored = ready(saved = saved.afterProcessDeath())

        assertEquals(EntryError.Unknown, unknown.error)
        assertEquals(listOf(operationIdOf(uuid(500)), operationIdOf(uuid(500))), schedules.looked)
        assertEquals(emptyList<CreateExpense>(), commands.created)
        assertEquals(1, schedules.submitted.size)
        assertTrue(restored.now.closed)
        assertEquals(rule(22), (said as CorrectionNotice.ScheduleSaved).rule)
    }

    @Test
    fun theChoiceAndItsOpenSheetSurviveTheProcessStopping() {
        val saved = SavedStateHandle()
        val form = ready(saved = saved).chosen()
        form.type("950000")
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.KindPicked(RepeatKind.Weeks))
        form.repeat(RepeatAction.Increase)

        val restored = ready(saved = saved.afterProcessDeath())

        assertEquals(RepeatKind.Weeks, restored.now.repeat.sheet?.kind)
        assertEquals(2, restored.now.repeat.sheet?.interval)
        assertNull(restored.now.repeat.choice)
    }
}
