package com.software.sello.feature.income

import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommandOutcome.Rejected
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.expense.HandIncomeReads
import com.software.sello.feature.expense.incomeId
import com.software.sello.feature.expense.pesos
import com.software.sello.feature.expense.storedIncome
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.recurrence.HandScheduleCommands
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.feature.recurrence.scheduleId
import com.software.sello.feature.resumen.resumenSnapshot
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.ScheduleChange
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.presentation.recurrence.RepeatAction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
 * The income form over scripted commands. Today is 22 October 2026 and the generation
 * is 7. For corrections, storage holds income 100: 850.000 from "Otro: Diseño" on 5
 * October with the note "Logo", at version 3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomeFormViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val commands = HandIncomeCommands()
    private val reads = HandIncomeReads()
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
    private lateinit var corrections: ExpenseCorrections

    private val design = IncomeSource.Other((SourceName.of("Diseño") as Outcome.Success).value)
    private val logo = storedIncome(100, "2026-10-05", 850_000, design, "Logo").copy(version = 3)

    @Before
    fun mainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        corrections = ExpenseCorrections(
            HandExpenseCommands(),
            commands,
            HandGoalCommands(),
            { Duration.ZERO },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main)
        )
    }

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun shown(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun form(saved: SavedStateHandle = SavedStateHandle()) =
        IncomeFormViewModel(saved, commands, reads, clock, {
            uuid(nextId++)
        }, money, corrections, schedules, scheduleBook)

    /** A new-income form with the month already read. */
    private fun ready(saved: SavedStateHandle = SavedStateHandle()): IncomeFormViewModel {
        reads.publish(october, generation = 7)
        return form(saved)
    }

    private fun editing(income: Income = logo) =
        SavedStateHandle(mapOf(IncomeFormViewModel.KEY_EDITED_INCOME to income.id.value))

    // --- The composer, for the note and Otro's name ---

    @Test
    fun theNoteIsWrittenInTheComposerAndOnlyAValidOneIsKept() {
        val form = ready()
        form.onAction(IncomeFormAction.OpenComposer(IncomeComposer.Note))
        val opened = form.now
        form.onAction(IncomeFormAction.ComposerChanged("Marca"))
        form.onAction(IncomeFormAction.ComposerDone)
        val kept = form.now
        form.onAction(IncomeFormAction.OpenComposer(IncomeComposer.Note))
        form.onAction(IncomeFormAction.ComposerChanged("Otra"))
        form.onAction(IncomeFormAction.ComposerCancelled)

        assertEquals(IncomeComposer.Note to "", opened.composer to opened.composerDraft)
        assertEquals("Marca" to null, kept.note to kept.composer)
        assertEquals("Marca" to null, form.now.note to form.now.composer)
    }

    @Test
    fun otrosNameIsWrittenInTheComposerAndIsNeverLostWhenItCloses() {
        val form = ready()
        form.type("5000")
        form.onAction(IncomeFormAction.SourcePicked("other"))
        val needsName = form.now.missing
        form.onAction(IncomeFormAction.OpenComposer(IncomeComposer.SourceName))
        form.onAction(IncomeFormAction.ComposerChanged("Bono"))
        form.onAction(IncomeFormAction.ComposerDone)
        val named = form.now
        form.onAction(IncomeFormAction.OpenComposer(IncomeComposer.SourceName))
        val reopened = form.now.composerDraft
        form.onAction(IncomeFormAction.ComposerChanged(""))
        form.onAction(IncomeFormAction.ComposerCancelled)

        assertEquals(IncomeMissing.SourceName, needsName)
        assertEquals("Bono" to null, named.otherName to named.missing)
        assertEquals("Bono", reopened)
        assertEquals("Bono", form.now.otherName)
    }

    @Test
    fun overItsLimitTheComposerKeepsTheTextAndCannotBeFinished() {
        val form = ready()
        form.onAction(IncomeFormAction.OpenComposer(IncomeComposer.SourceName))
        // Twenty-four faces are twenty-four characters, though each is two UTF-16 units.
        form.onAction(IncomeFormAction.ComposerChanged("\uD83D\uDE00".repeat(24)))
        val full = form.now
        form.onAction(IncomeFormAction.ComposerChanged("\uD83D\uDE00".repeat(25)))
        form.onAction(IncomeFormAction.ComposerDone)

        assertEquals(24 to null, full.composerLength to full.composerProblem)
        assertEquals(
            25 to IncomeTextProblem.TooLong,
            form.now.composerLength to form.now.composerProblem
        )
        assertEquals(IncomeComposer.SourceName, form.now.composer)
        assertEquals("", form.now.otherName)
    }

    @Test
    fun anOpenComposerAndItsTextComeBackAfterTheProcessIsKilled() {
        val saved = SavedStateHandle()
        val first = ready(saved)
        first.onAction(IncomeFormAction.OpenComposer(IncomeComposer.Note))
        first.onAction(IncomeFormAction.ComposerChanged("Prim"))

        val restored = form(saved.afterProcessDeath()).now

        assertEquals(IncomeComposer.Note to "Prim", restored.composer to restored.composerDraft)
        assertEquals("", restored.note)
    }

    @Test
    fun maximoDoceDigitosStaysUntilTheNextKeyThatChangesTheAmount() {
        val form = ready()
        form.type("999999999999")
        form.type("9")
        val refused = form.now.atLimit
        form.onAction(IncomeFormAction.Backspace)

        assertEquals(true to false, refused to form.now.atLimit)
    }

    /** The form opened on [income], with it in storage and its months read. */
    private fun opened(
        income: Income = logo,
        saved: SavedStateHandle = editing(income)
    ): IncomeFormViewModel {
        reads.stored[income.id] = income
        reads.publish(october, generation = 7)
        reads.publish(YearMonth.from(income.date), generation = 7)
        return form(saved)
    }

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private fun IncomeFormViewModel.type(digits: String) =
        digits.forEach { onAction(IncomeFormAction.Digit(it.digitToInt())) }

    private fun IncomeFormViewModel.retype(digits: String) {
        onAction(IncomeFormAction.ClearAmount)
        type(digits)
    }

    private val IncomeFormViewModel.now get() = state.value

    // --- A new income ---

    @Test
    fun replacingHistoryBlocksTheExistingDraftInsteadOfSavingItInTheNewGeneration() {
        val form = ready()
        form.type("5000")
        reads.publish(october, generation = 8, revision = 2)
        form.onAction(IncomeFormAction.Submit)
        assertEquals(IncomeFormError.DataChanged, form.now.error)
        assertFalse(form.now.canSubmit || form.now.editable)
        assertTrue(commands.created.isEmpty())
    }

    @Test
    fun itLoadsThenOpensOnSalaryTodayWithOnlyTheAmountMissing() {
        val form = form()
        val loading = form.now

        reads.publish(october, generation = 7)

        assertTrue(loading.loading && !loading.canSubmit)
        assertEquals(listOf(october), reads.observed)
        assertEquals(
            listOf("salary", "freelance", "passive_income", "transfer", "other"),
            form.now.sourceKeys
        )
        assertEquals("salary" to LocalDate.of(2026, 10, 22), form.now.sourceKey to form.now.date)
        assertEquals(IncomeMissing.Amount, form.now.missing)
        assertFalse(form.now.editing || form.now.canDelete || form.now.loading)
    }

    @Test
    fun whenTheMonthCannotBeReadItSaysSoAndCanBeRetried() {
        val form = form()
        reads.of(october).value =
            IncomeMonthState.Failed(StorageFailure.Unavailable("x"), lastGood = null)
        val failed = form.now

        form.onAction(IncomeFormAction.Retry)
        reads.publish(october, generation = 7)

        assertTrue(failed.loadFailed && !failed.canSubmit)
        assertFalse(form.now.loadFailed)
    }

    @Test
    fun aValidIncomeSendsOneCommandWithExactlyWhatWasEnteredThenClosesSayingWhatStorageHolds() {
        val form = ready()
        form.type("150000")
        form.onAction(IncomeFormAction.SourcePicked("freelance"))
        form.onAction(IncomeFormAction.DatePicked(LocalDate.of(2026, 10, 20)))
        form.onAction(IncomeFormAction.NoteChanged("Marca"))
        // What is said is read back, not taken from the draft.
        reads.stored[incomeId(900)] =
            storedIncome(900, "2026-10-20", 150_000, IncomeSource.Freelance, "Marca")
        val saidBefore = corrections.notice.value

        form.onAction(IncomeFormAction.Submit)

        val sent = commands.created.single()
        assertEquals(
            listOf(
                uuid(500),
                7L,
                150_000L,
                IncomeSource.Freelance,
                LocalDate.of(2026, 10, 20),
                "Marca"
            ),
            listOf(
                sent.operationId.value,
                sent.generation,
                sent.amount.money.minorUnits,
                sent.source,
                sent.date,
                sent.note?.value
            )
        )
        assertNull(saidBefore)
        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
        assertEquals(CorrectionNotice.IncomeRecorded(shown("150.000")), corrections.notice.value)
    }

    @Test
    fun otroNeedsANameWhichIsSentWithItAndKeptButNotSentForAnotherSource() {
        val form = ready()
        form.type("5000")
        form.onAction(IncomeFormAction.SourcePicked("other"))
        val unnamed = form.now
        form.onAction(IncomeFormAction.OtherNameChanged("  Bono  "))
        val named = form.now
        form.onAction(IncomeFormAction.SourcePicked("salary"))
        val backToSalary = form.now

        form.onAction(IncomeFormAction.Submit)
        val other = ready()
        other.type("5000")
        other.onAction(IncomeFormAction.SourcePicked("other"))
        other.onAction(IncomeFormAction.OtherNameChanged("  Bono  "))
        other.onAction(IncomeFormAction.Submit)

        assertEquals(IncomeMissing.SourceName to false, unnamed.missing to unnamed.canSubmit)
        assertTrue(named.canSubmit)
        assertEquals("  Bono  " to null, backToSalary.otherName to backToSalary.missing)
        assertEquals(IncomeSource.Salary, commands.created[0].source)
        assertEquals("Bono", (commands.created[1].source as IncomeSource.Other).name.value)
    }

    @Test
    fun zeroOrSomethingThatIsNotAnAmountIsRefusedAndKeptAsItWasGiven() {
        val form = ready()
        form.onAction(IncomeFormAction.Paste("-150000"))
        val negative = form.now
        form.onAction(IncomeFormAction.Paste("0"))
        val zero = form.now
        form.onAction(IncomeFormAction.Submit)

        assertEquals(
            "-150000" to IncomeAmountProblem.NotAnAmount,
            negative.amountText to negative.amountProblem
        )
        assertEquals("0" to IncomeAmountProblem.Zero, zero.amountText to zero.amountProblem)
        assertNull(zero.amount)
        assertFalse(zero.canSubmit)
        assertEquals(emptyList<Any>(), commands.created)
    }

    @Test
    fun aDateAfterTodayCannotBePicked() {
        val form = ready()

        form.onAction(IncomeFormAction.DatePicked(LocalDate.of(2026, 10, 23)))

        assertEquals(LocalDate.of(2026, 10, 22), form.now.date)
    }

    @Test
    fun whileSavingASecondPressSendsNothingAndTheDraftIsLocked() {
        val form = ready()
        form.type("5000")
        commands.hold = CompletableDeferred()

        form.onAction(IncomeFormAction.Submit)
        val saving = form.now
        form.onAction(IncomeFormAction.Submit)
        form.type("9")
        form.onAction(IncomeFormAction.Back)

        assertTrue(saving.busy && !saving.editable)
        assertEquals(1, commands.created.size)
        assertEquals(shown("5.000"), form.now.amount)
        assertFalse(form.now.closed)
    }

    @Test
    fun aRefusedIncomeKeepsTheDraftAndSaysWhy() {
        val refusals = listOf(
            IncomeRejection.FutureDate(LocalDate.of(2026, 10, 22)) to IncomeFormError.FutureDate,
            IncomeRejection.InvalidDate to IncomeFormError.InvalidDate,
            IncomeRejection.StaleGeneration(8) to IncomeFormError.DataChanged,
            IncomeRejection.StorageFailed(StorageFailure.Unavailable("x")) to
                IncomeFormError.NotSaved
        )

        val seen = refusals.map { (reason, _) ->
            commands.createAnswer = { Rejected(reason) }
            val form = ready()
            form.type("5000")
            form.onAction(IncomeFormAction.Submit)
            Triple(form.now.error, form.now.amount, form.now.recorded)
        }

        assertEquals(refusals.map { Triple(it.second, shown("5.000"), null) }, seen)
    }

    @Test
    fun anUnknownSaveCannotBeDiscardedAndLoseTheOperationNeededForRecovery() {
        commands.createAnswer = { IncomeCommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val form = ready()
        form.type("5000")
        form.onAction(IncomeFormAction.Submit)
        form.onAction(IncomeFormAction.Back)
        assertFalse(form.now.confirmDiscard)
        form.onAction(IncomeFormAction.DiscardConfirmed)
        assertFalse(form.now.closed)
        assertNull(corrections.notice.value)
        commands.found = { Outcome.Success(commands.committed(it)) }
        form.onAction(IncomeFormAction.Retry)
        assertEquals(1, commands.created.size)
        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
    }

    @Test
    fun anUnknownOutcomeIsLookedUpAndNeverSentAgain() {
        commands.createAnswer = { IncomeCommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val form = ready()
        form.type("5000")
        form.onAction(IncomeFormAction.Submit)
        val unknown = form.now
        commands.found = { Outcome.Success(commands.committed(it)) }

        form.onAction(IncomeFormAction.Retry)

        assertEquals(IncomeFormError.Unknown, unknown.error)
        assertFalse(unknown.canSubmit || unknown.editable || unknown.closed)
        assertEquals(1, commands.created.size)
        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
    }

    @Test
    fun afterTheProcessWasKilledTheDraftIsBackAndAnInterruptedSaveIsLookedUpNotSentAgain() {
        val saved = SavedStateHandle()
        val first = ready(saved)
        first.type("5000")
        first.onAction(IncomeFormAction.SourcePicked("other"))
        first.onAction(IncomeFormAction.OtherNameChanged("Bono"))
        first.onAction(IncomeFormAction.NoteChanged("Extra"))
        val draft = saved.afterProcessDeath()
        commands.hold = CompletableDeferred()
        first.onAction(IncomeFormAction.Submit)
        commands.found = { Outcome.Success(commands.committed(it)) }

        val restoredDraft = form(draft)
        val restoredMidSave = form(saved.afterProcessDeath())

        assertEquals(
            listOf(shown("5.000"), "other", "Bono", "Extra"),
            listOf(
                restoredDraft.now.amount,
                restoredDraft.now.sourceKey,
                restoredDraft.now.otherName,
                restoredDraft.now.note
            )
        )
        assertEquals(1, commands.created.size)
        assertEquals(listOf(commands.created.single().operationId), commands.looked)
        assertEquals(uuid(900) to true, restoredMidSave.now.recorded to restoredMidSave.now.closed)
        assertTrue(corrections.notice.value is CorrectionNotice.IncomeRecorded)
    }

    @Test
    fun aSavedIncomeThatCannotBeReadBackIsStillSaidToBeSavedAndNotSentAgain() {
        val form = ready()
        form.type("5000")
        // Saved, then the read back fails.
        reads.failure = StorageFailure.Unavailable("x")

        form.onAction(IncomeFormAction.Submit)
        form.onAction(IncomeFormAction.Submit)

        assertEquals(uuid(900) to true, form.now.recorded to form.now.closed)
        assertEquals(CorrectionNotice.IncomeRecorded(amount = null), corrections.notice.value)
        assertEquals(1, commands.created.size)
    }

    @Test
    fun backAsksOnlyWhenSomethingWasTypedAndASavedIncomeClosesByItself() {
        val empty = ready()
        empty.onAction(IncomeFormAction.Back)
        val typed = ready()
        typed.type("5")
        typed.onAction(IncomeFormAction.Back)
        val asked = typed.now
        typed.onAction(IncomeFormAction.KeepEditing)
        typed.onAction(IncomeFormAction.Submit)

        assertTrue(empty.now.closed)
        assertTrue(asked.confirmDiscard && !asked.closed)
        assertTrue(typed.now.closed)
    }

    // --- Correcting a recorded income ---

    private val storedDraft =
        listOf(shown("850.000"), "other", "Diseño", LocalDate.of(2026, 10, 5), "Logo")

    private fun IncomeFormState.draft() = listOf(amount, sourceKey, otherName, date, note)

    @Test
    fun aRecordedIncomeOpensOnExactlyWhatIsStoredWithNothingToSaveYet() {
        val form = opened()

        assertTrue(form.now.editing && form.now.canDelete)
        assertEquals(storedDraft, form.now.draft())
        assertEquals(IncomeMissing.Changes, form.now.missing)
        assertFalse(form.now.canSubmit)
        assertEquals(emptyList<YearMonth>(), form.now.pastMonths)
        assertEquals(emptyList<Any>(), commands.edited + commands.created)
    }

    @Test
    fun anIncomeThatIsGoneOrCannotBeReadSaysSoAndTheSecondCanBeAskedForAgain() {
        reads.publish(october, generation = 7)
        val gone = form(editing())
        reads.failure = StorageFailure.Unavailable("x")
        val unreadable = form(editing())
        val failed = unreadable.now
        reads.failure = null
        reads.stored[logo.id] = logo

        unreadable.onAction(IncomeFormAction.Retry)

        assertEquals(IncomeFormError.IncomeGone, gone.now.error)
        assertFalse(gone.now.canSubmit || gone.now.canDelete || gone.now.editable)
        assertTrue(failed.loadFailed)
        assertEquals(storedDraft, unreadable.now.draft())
    }

    @Test
    fun savingSendsOneEditOfTheVersionReadThenClosesAndSaysItWasUpdated() {
        val form = opened()
        form.retype("900000")
        form.onAction(IncomeFormAction.SourcePicked("salary"))
        val ready = form.now

        form.onAction(IncomeFormAction.Submit)

        assertTrue(ready.canSubmit)
        val sent = commands.edited.single()
        assertEquals(
            listOf(
                uuid(500),
                7L,
                uuid(100),
                3L,
                900_000L,
                IncomeSource.Salary,
                LocalDate.of(2026, 10, 5),
                "Logo"
            ),
            listOf(
                sent.operationId.value,
                sent.generation,
                sent.incomeId.value,
                sent.expectedVersion,
                sent.amount.money.minorUnits,
                sent.source,
                sent.date,
                sent.note?.value
            )
        )
        assertTrue(form.now.closed)
        assertNull(form.now.recorded)
        assertEquals(CorrectionNotice.IncomeUpdated, corrections.notice.value)
        assertEquals(emptyList<Any>(), commands.created)
    }

    @Test
    fun puttingEverythingBackAsItWasLeavesNothingToSave() {
        val form = opened()
        form.retype("5")
        form.onAction(IncomeFormAction.OtherNameChanged("Otra cosa"))
        val changed = form.now.canSubmit

        form.retype("850000")
        form.onAction(IncomeFormAction.OtherNameChanged(" Diseño "))
        form.onAction(IncomeFormAction.Submit)

        assertTrue(changed)
        assertEquals(IncomeMissing.Changes, form.now.missing)
        assertEquals(emptyList<Any>(), commands.edited)
    }

    @Test
    fun aRefusedCorrectionKeepsWhatWasTypedAndOnceItChangedUnderneathNothingMoreIsSent() {
        val refusals = listOf(
            IncomeRejection.VersionConflict(4) to IncomeFormError.IncomeChanged,
            IncomeRejection.IncomeMissing to IncomeFormError.IncomeGone,
            IncomeRejection.StaleGeneration(8) to IncomeFormError.DataChanged,
            IncomeRejection.StorageFailed(StorageFailure.Unavailable("x")) to
                IncomeFormError.NotSaved
        )

        val seen = refusals.map { (reason, _) ->
            commands.editAnswer = { Rejected(reason) }
            val form = opened()
            form.retype("900000")
            form.onAction(IncomeFormAction.Submit)
            form.onAction(IncomeFormAction.Submit)
            Triple(form.now.error, form.now.amount, form.now.closed)
        }

        assertEquals(refusals.map { Triple(it.second, shown("900.000"), false) }, seen)
        // The two that block sent once; the two that can be retried sent twice.
        assertEquals(6, commands.edited.size)
        assertNull(corrections.notice.value)
    }

    @Test
    fun anIncomeOfAnEarlierMonthSaysWhichMonthsWouldChange() {
        val september = logo.copy(date = LocalDate.of(2026, 9, 12))
        reads.publish(YearMonth.of(2026, 8), generation = 7)
        val form = opened(september)
        val opened = form.now.pastMonths

        form.onAction(IncomeFormAction.DatePicked(LocalDate.of(2026, 8, 30)))

        assertEquals(listOf(YearMonth.of(2026, 9)), opened)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), form.now.pastMonths)
    }

    @Test
    fun aDraftRestoredAfterTheProcessWasKilledKeepsTheChangesAndTheVersionItRead() {
        val saved = editing()
        val first = opened(saved = saved)
        first.retype("900000")
        reads.stored[logo.id] = logo.copy(version = 4)

        val restored = form(saved.afterProcessDeath())
        restored.onAction(IncomeFormAction.Submit)

        assertEquals(shown("900.000"), restored.now.amount)
        assertEquals(3L, commands.edited.single().expectedVersion)
    }

    @Test
    fun anUncertainDeletionIsRecoveredAsThatDeletionWithoutAnotherWriteOrEditNotice() {
        commands.deleteAnswer = { IncomeCommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val form = opened()
        form.onAction(IncomeFormAction.Delete)
        assertEquals(IncomeFormError.Unknown, form.now.error)
        form.onAction(IncomeFormAction.Delete)
        assertEquals(1, commands.deleted.size)
        commands.found = { Outcome.Success(commands.committed(it, logo.id)) }
        form.onAction(IncomeFormAction.Retry)
        assertTrue(form.now.closed)
        assertTrue(corrections.notice.value is CorrectionNotice.IncomeDeleted)
        assertEquals(1, commands.deleted.size)
        assertTrue(commands.edited.isEmpty())
    }

    @Test
    fun deletingFromTheFormDeletesTheVersionReadAndClosesAndARefusalKeepsItOpen() {
        val form = opened()
        form.onAction(IncomeFormAction.Delete)
        commands.deleteAnswer = { Rejected(IncomeRejection.VersionConflict(4)) }
        val refused = opened()
        refused.onAction(IncomeFormAction.Delete)

        val sent = commands.deleted.first()
        assertEquals(
            listOf(uuid(100), 3L, 7L),
            listOf(sent.incomeId.value, sent.expectedVersion, sent.generation)
        )
        assertTrue(form.now.closed)
        assertTrue(corrections.notice.value is CorrectionNotice.IncomeDeleted)
        assertEquals(
            IncomeFormError.IncomeChanged to false,
            refused.now.error to refused.now.closed
        )
    }

    // --- Se repite: a schedule instead of one income (SELLO-100) ---

    private fun IncomeFormViewModel.repeat(action: RepeatAction) =
        onAction(IncomeFormAction.Repeat(action))

    @Test
    fun seRepiteKeepsAScheduledIncomeNamedByItsSource() {
        val form = ready()
        form.type("1000000")
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.DayToggled(30))
        form.repeat(RepeatAction.Done)
        val chosen = form.now.repeat
        form.onAction(IncomeFormAction.Submit)

        val monthly30 = (RepeatRule.Monthly.of(30) as Outcome.Success).value
        assertEquals(LocalDate.parse("2026-10-30"), chosen.next)
        assertEquals(emptyList<CreateIncome>(), commands.created)
        assertEquals(
            listOf(
                CreateSchedule(
                    (OperationId.of(uuid(500)) as Outcome.Success).value,
                    7,
                    ScheduledEntry.Income(IncomeSource.Salary, pesos(1_000_000), null),
                    monthly30,
                    LocalDate.parse("2026-10-22"),
                    null
                )
            ),
            schedules.submitted
        )
        assertEquals(
            CorrectionNotice.ScheduleSaved(
                null,
                monthly30,
                LocalDate.parse("2026-10-22"),
                income = true,
                sourceKey = "salary"
            ),
            corrections.notice.value
        )
        assertTrue(form.now.closed)
    }

    @Test
    fun aScheduleWhoseSaveWasNotHeardFromIsAskedAboutAfterARestart() {
        val saved = SavedStateHandle()
        val form = ready(saved)
        form.type("1000000")
        form.repeat(RepeatAction.Open)
        form.repeat(RepeatAction.Done)
        schedules.answer = { ScheduleCommandOutcome.OutcomeUnknown(it.operationId) }
        schedules.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        form.onAction(IncomeFormAction.Submit)
        val unknown = form.now.error
        schedules.found = { Outcome.Success(null) }

        // Storage says it was never saved: the draft and the choice are there to try again.
        val restored = ready(saved.afterProcessDeath())

        assertEquals(IncomeFormError.Unknown, unknown)
        assertEquals(2, schedules.looked.size)
        assertEquals(emptyList<CreateIncome>(), commands.created)
        assertNull(restored.now.error)
        assertTrue(restored.now.canSubmit)
        assertEquals(22, restored.now.repeat.choice?.days?.single())
    }

    // --- Solo este / Este y los siguientes for incomes (SELLO-100) ---

    private val salarySchedule = StoredSchedule(
        Schedule(
            scheduleId(8),
            ScheduledEntry.Income(IncomeSource.Salary, pesos(1_000_000), null),
            (
                Recurrence.of(
                    (RepeatRule.Monthly.of(5) as Outcome.Success).value,
                    LocalDate.parse("2026-10-01"),
                    null,
                    emptyList()
                ) as Outcome.Success
                ).value
        ),
        sequence = 1,
        handled = Occurrence(LocalDate.parse("2026-10-05"), 1),
        version = 4,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private val recordedSalary = storedIncome(101, "2026-10-05", 1_000_000)
        .copy(origin = EntryOrigin(scheduleId(8), Occurrence(LocalDate.parse("2026-10-05"), 1)))

    @Test
    fun esteYLosSiguientesChangesTheScheduledIncomeFirstThenThisOne() {
        scheduleBook.schedules += salarySchedule
        val form = opened(recordedSalary)
        form.retype("1200000")
        form.onAction(IncomeFormAction.Submit)
        val asked = form.now.askFollowing
        form.onAction(IncomeFormAction.FollowingChosen(alsoFollowing = true))

        assertTrue(asked)
        assertEquals(
            listOf(
                ChangeSchedule(
                    (OperationId.of(uuid(500)) as Outcome.Success).value,
                    7,
                    scheduleId(8),
                    4,
                    ScheduledEntry.Income(IncomeSource.Salary, pesos(1_200_000), null),
                    salarySchedule.schedule.recurrence.rule,
                    LocalDate.parse("2026-10-01"),
                    null
                )
            ),
            schedules.submitted
        )
        assertEquals(1, commands.edited.size)
        assertEquals(
            CorrectionNotice.ScheduleChanged(ScheduleChange.Changed, income = true),
            corrections.notice.value
        )
    }

    @Test
    fun soloEsteCorrectsOnlyThisIncome() {
        scheduleBook.schedules += salarySchedule
        val form = opened(recordedSalary)
        form.retype("1200000")
        form.onAction(IncomeFormAction.Submit)
        form.onAction(IncomeFormAction.FollowingChosen(alsoFollowing = false))

        assertTrue(schedules.submitted.isEmpty())
        assertEquals(pesos(1_200_000), commands.edited.single().amount)
    }
}
