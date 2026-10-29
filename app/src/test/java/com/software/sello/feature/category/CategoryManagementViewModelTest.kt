package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryEdit
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.ChangeCategoryIcon
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import com.software.sello.domain.usecase.CatchUpResult
import com.software.sello.domain.usecase.CatchUpStop
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.expense.HandSnapshots
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The form changing an existing category. Storage holds category 1, "Café", icon
 * local_cafe, limit 100.000, at version 5, and category 2, "Mercado", archived. Today
 * is 9 October 2026; the generation is 7.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagementViewModelTest {
    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun id(number: Int) = (CategoryId.of(uuid(number)) as Outcome.Success).value

    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)

    /** Commands a test scripts. Unless told otherwise every command commits. */
    private inner class HandCommands : CategoryCommands {
        val submitted = mutableListOf<CategoryCommand>()
        val looked = mutableListOf<OperationId>()
        var answer: (CategoryCommand) -> CategoryCommandOutcome = { committed(it.operationId) }
        var found: (OperationId) -> Outcome<CommittedCategoryChange?, StorageFailure> =
            { Outcome.Success(null) }
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome {
            submitted += command
            events += command::class.simpleName.orEmpty()
            hold?.await()
            return answer(command)
        }

        override suspend fun find(
            operationId: OperationId
        ): Outcome<CommittedCategoryChange?, StorageFailure> {
            looked += operationId
            return found(operationId)
        }
    }

    private fun change(operation: OperationId) = CommittedCategoryChange(
        OperationReceipt(operation, OperationKind.RenameCategory, 1, 1, Instant.EPOCH),
        id(1)
    )

    private fun committed(operation: OperationId) =
        CommandOutcome.Committed(change(operation), replayed = false)

    private fun rejected(reason: CommandRejection<CategoryRejection>) =
        CommandOutcome.Rejected(reason)

    private val storageFailed =
        rejected(CommandRejection.StorageFailed(StorageFailure.Unavailable("x")))

    private val commands = HandCommands()

    /** What happened, in order: each command sent and each catch-up run. */
    private val events = mutableListOf<String>()
    private var caughtUp = CatchUpResult(emptyList(), stopped = null)
    private val reads = HandCategoryReads().apply { generation = 7 }
    private val snapshots = HandSnapshots()
    private val session = MonthSession(HandFinancialClock("2026-10-09"), HandMonotonicClock())
    private var nextId = 500
    private val money = MoneyFormatter(
        object : MoneyLabels {
            override fun amount(currency: Currency, digits: String, isOne: Boolean) =
                "$digits pesos"

            override fun negative(amount: String) = amount

            override fun positive(amount: String) = amount
        }
    )
    private lateinit var notices: ExpenseCorrections

    private fun finite(pesos: Long) =
        (BudgetLimit.finite(Money.cop(pesos)) as Outcome.Success).value

    private fun stored(
        number: Int,
        name: String,
        state: MonthBudgetState,
        icon: String = "local_cafe",
        archived: Boolean = false,
        version: Long = 5
    ) = CategoryBudget(
        Category(
            id(number),
            (CategoryName.of(name) as Outcome.Success).value,
            (IconKey.of(icon) as Outcome.Success).value,
            archived = archived,
            version = version,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        ),
        state
    )

    private val limited = MonthBudgetState.Limited(finite(100_000), explicit = false)
    private val cafe = stored(1, "Café", limited)
    private val mercado =
        stored(2, "Mercado", MonthBudgetState.Paused, icon = "home", archived = true)

    @Before
    fun mainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        notices = ExpenseCorrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { Duration.ZERO },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main)
        )
        reads.categories = listOf(cafe, mercado)
    }

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun opening(category: Int = 1, month: YearMonth? = null) = SavedStateHandle(
        buildMap {
            put(CategoryManagementViewModel.KEY_CATEGORY, uuid(category))
            if (month != null) put(CategoryManagementViewModel.KEY_MONTH, month.toString())
        }
    )

    private fun form(saved: SavedStateHandle = opening()) = CategoryManagementViewModel(
        saved,
        commands,
        reads,
        snapshots,
        session,
        { uuid(nextId++) },
        money,
        notices,
        {
            events += "catch-up"
            caughtUp
        }
    )

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private val CategoryManagementViewModel.now get() = state.value

    private fun CategoryManagementViewModel.name(text: String) =
        onAction(CategoryEditorAction.NameChanged(text))

    private fun CategoryManagementViewModel.limit(text: String) =
        onAction(CategoryEditorAction.LimitChanged(text))

    private fun CategoryManagementViewModel.save() = onAction(CategoryEditorAction.Submit)

    private fun versions() = commands.submitted.map { (it as CategoryEdit).expectedVersion }

    // --- Opening ---

    @Test
    fun itOpensOnWhatIsStoredForThisMonthWithNothingToSave() {
        val form = form()

        val now = form.now
        assertTrue(now.editing && !now.loading && !now.archived)
        assertEquals(
            listOf("Café", "local_cafe", false, "100000"),
            listOf(now.name, now.iconKey, now.unlimited, now.limitText)
        )
        assertEquals(october to false, now.targetMonth to now.historical)
        // Café's own 100.000, topped up with the first two starting suggestions.
        assertEquals(
            listOf("100.000 pesos", "200.000 pesos", "300.000 pesos"),
            now.suggestions.map { it.spoken }
        )
        assertEquals("100.000" to false, now.limitTotal?.amount?.digits to now.limitTotal?.changes)
        assertFalse(now.changed || now.canSubmit || now.limitLocked)
        assertTrue(now.canArchive)
        assertEquals(emptyList<CategoryCommand>(), commands.submitted)
    }

    @Test
    fun anUnlimitedCategoryOpensWithSinLimiteOnAndNoAmount() {
        reads.categories =
            listOf(stored(1, "Café", MonthBudgetState.Limited(BudgetLimit.Unlimited, false)))

        val form = form()

        assertEquals(true to "", form.now.unlimited to form.now.limitText)
        assertFalse(form.now.changed)
    }

    @Test
    fun aCategoryThatIsNotThereSaysSoAndNothingCanBeSent() {
        val form = form(opening(category = 99))

        assertEquals(SaveError.CategoryGone, form.now.saveError)
        assertFalse(form.now.canSubmit || form.now.canArchive || form.now.editable)
    }

    @Test
    fun aCategoryThatCannotBeReadCanBeAskedForAgain() {
        reads.failure = StorageFailure.Unavailable("x")
        val form = form()
        val failed = form.now
        reads.failure = null

        form.onAction(CategoryEditorAction.Retry)

        assertTrue(failed.loadFailed && !failed.canSubmit && !failed.canArchive)
        assertEquals("Café", form.now.name)
        assertFalse(form.now.loadFailed)
    }

    // --- The name is checked while it is typed ---

    @Test
    fun aNameAnotherCategoryHasIsSaidWhileTypingBeforeAnythingIsSent() {
        val form = form()

        form.name("  mercadó ")
        val taken = form.now
        form.save()
        form.name("Mercadito")

        // Ignoring case, accents and outer spaces, and counting archived categories.
        assertEquals(NameError.Taken, taken.nameError)
        assertFalse(taken.canSubmit)
        assertEquals(emptyList<CategoryCommand>(), commands.submitted)
        assertNull(form.now.nameError)
        assertTrue(form.now.canSubmit)
    }

    @Test
    fun itsOwnNameWrittenAnotherWayIsNotTakenAndIsSavedAsARename() {
        val form = form()

        form.name("CAFE")
        val typed = form.now
        form.save()

        assertNull(typed.nameError)
        assertTrue(typed.changed && typed.canSubmit)
        assertEquals("CAFE", (commands.submitted.single() as RenameCategory).name.value)
    }

    // --- Saving ---

    @Test
    fun renamingSendsOnlyARenameOfTheVersionReadAndSaysTheCategoryWasUpdated() {
        val form = form()
        form.name("Cafetería")

        form.save()

        val sent = commands.submitted.single() as RenameCategory
        assertEquals(
            listOf(uuid(500), 7L, uuid(1), 5L, "Cafetería"),
            listOf(
                sent.operationId.value,
                sent.generation,
                sent.categoryId.value,
                sent.expectedVersion,
                sent.name.value
            )
        )
        assertEquals(EditorResult.Updated, form.now.result)
        assertEquals(CorrectionNotice.CategorySaved(limitOf = null), notices.notice.value)
    }

    @Test
    fun nameIconAndLimitAreThreeChangesEachAgainstTheVersionTheOneBeforeLeft() {
        val form = form()
        form.name("Cafetería")
        form.onAction(CategoryEditorAction.IconPicked("restaurant"))
        form.limit("120000")

        form.save()

        assertEquals(
            listOf(RenameCategory::class, ChangeCategoryIcon::class, SetDefaultLimit::class),
            commands.submitted.map { it::class }
        )
        assertEquals(listOf(5L, 6L, 7L), versions())
        assertEquals(
            listOf(uuid(500), uuid(501), uuid(502)),
            commands.submitted.map {
                it.operationId.value
            }
        )
        // In the current month the limit is the one from this month onwards.
        assertEquals(finite(120_000), (commands.submitted[2] as SetDefaultLimit).limit)
        assertEquals("restaurant", (commands.submitted[1] as ChangeCategoryIcon).icon.value)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun fromAFinishedMonthOnlyThatMonthsLimitIsChangedAndTheNoticeNamesIt() {
        reads.categories =
            listOf(stored(1, "Café", MonthBudgetState.Limited(finite(80_000), true)), mercado)
        val form = form(opening(month = september))
        val opened = form.now
        form.limit("90000")

        form.save()

        assertEquals(september to true, opened.targetMonth to opened.historical)
        assertEquals("80000", opened.limitText)
        val sent = commands.submitted.single() as SetMonthLimit
        assertEquals(
            listOf(september, finite(90_000), 5L),
            listOf(sent.month, sent.limit, sent.expectedVersion)
        )
        assertEquals(CorrectionNotice.CategorySaved(limitOf = september), notices.notice.value)
    }

    @Test
    fun turningSinLimiteOnIsALimitChangeToUnlimitedNotToZero() {
        val form = form()

        form.onAction(CategoryEditorAction.UnlimitedChanged(true))
        form.save()

        assertEquals(BudgetLimit.Unlimited, (commands.submitted.single() as SetDefaultLimit).limit)
    }

    @Test
    fun puttingEverythingBackLeavesNothingToSave() {
        val form = form()
        form.name("Otra")
        form.limit("5")
        form.onAction(CategoryEditorAction.IconPicked("restaurant"))
        val changed = form.now.changed

        form.name(" Café ")
        form.limit("100.000")
        form.onAction(CategoryEditorAction.IconPicked("local_cafe"))
        form.save()

        assertTrue(changed)
        assertFalse(form.now.changed || form.now.canSubmit)
        assertEquals(emptyList<CategoryCommand>(), commands.submitted)
    }

    @Test
    fun aLimitCannotBeLeftEmptyAndAMonthThatNeverHadOneCanStayWithout() {
        val withLimit = form()
        withLimit.limit("")
        withLimit.save()
        reads.categories = listOf(stored(1, "Café", MonthBudgetState.Unconfigured))
        val without = form(opening(month = september))
        val opened = without.now
        without.name("Cafetería")
        without.save()

        assertEquals(LimitError.Required, withLimit.now.limitError)
        assertEquals("" to false, opened.limitText to opened.unlimited)
        assertEquals(listOf(RenameCategory::class), commands.submitted.map { it::class })
    }

    @Test
    fun whenALaterChangeFailsItSaysWhatWasSavedAndTheNextAttemptSendsOnlyTheRest() {
        val form = form()
        form.name("Cafetería")
        form.limit("120000")
        commands.answer =
            { if (it is SetDefaultLimit) storageFailed else committed(it.operationId) }

        form.save()
        val stopped = form.now
        commands.answer = { committed(it.operationId) }
        form.save()

        assertEquals(SaveError.NotSaved, stopped.saveError)
        assertEquals(listOf(EditPart.Name), stopped.savedParts)
        assertNull(stopped.result)
        assertEquals("120000", stopped.limitText)
        assertTrue(stopped.changed && stopped.canSubmit)
        assertEquals(
            listOf(RenameCategory::class, SetDefaultLimit::class, SetDefaultLimit::class),
            commands.submitted.map { it::class }
        )
        // The rename moved the version on; both limit attempts are against that one.
        assertEquals(listOf(5L, 6L, 6L), versions())
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun aSecondPressWhileSavingSendsNothingMore() {
        val form = form()
        form.name("Cafetería")
        commands.hold = CompletableDeferred()

        form.save()
        val saving = form.now
        form.save()
        form.onAction(CategoryEditorAction.Archive)
        form.onAction(CategoryEditorAction.Back)

        assertTrue(saving.busy && !saving.editable && !saving.canArchive)
        assertEquals(1, commands.submitted.size)
        assertNull(form.now.result)
        assertNull(form.now.archiveQuestion)
    }

    // --- Conflicts ---

    @Test
    fun aCategoryChangedMeanwhileStopsTheFormUntilThePersonChooses() {
        commands.answer = { rejected(CommandRejection.VersionConflict(current = 6)) }
        val form = form()
        form.name("Cafetería")

        form.save()
        form.save()
        form.name("Otra")

        assertEquals(SaveError.Conflict, form.now.saveError)
        assertFalse(form.now.canSubmit || form.now.editable)
        assertEquals("Cafetería", form.now.name)
        assertEquals(1, commands.submitted.size)
    }

    @Test
    fun keepingTheDraftReadsTheCategoryAgainAndSavesOverItsNewVersion() {
        commands.answer = { rejected(CommandRejection.VersionConflict(current = 6)) }
        val form = form()
        form.name("Cafetería")
        form.save()
        reads.categories = listOf(stored(1, "Tinto", limited, version = 6), mercado)
        commands.answer = { committed(it.operationId) }

        form.onAction(CategoryEditorAction.ReloadKeepingDraft)
        val kept = form.now
        form.save()

        assertEquals("Cafetería", kept.name)
        assertNull(kept.saveError)
        assertTrue(kept.changed)
        assertEquals(listOf(5L, 6L), versions())
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun discardingTheDraftStartsAgainFromWhatTheCategoryIsNow() {
        commands.answer = { rejected(CommandRejection.VersionConflict(current = 6)) }
        val form = form()
        form.name("Cafetería")
        form.save()
        reads.categories = listOf(stored(1, "Tinto", limited, icon = "home", version = 6), mercado)

        form.onAction(CategoryEditorAction.ReloadDiscardingDraft)

        assertEquals(listOf("Tinto", "home"), listOf(form.now.name, form.now.iconKey))
        assertFalse(form.now.changed)
        assertNull(form.now.saveError)
    }

    @Test
    fun otherRefusalsKeepTheDraftAndSayWhatHappened() {
        val refusals = listOf(
            Specific(CategoryRejection.CategoryMissing) to SaveError.CategoryGone,
            CommandRejection.StaleGeneration(8) to SaveError.DataChanged,
            CommandRejection.StorageFailed(StorageFailure.Unavailable("x")) to SaveError.NotSaved,
            Specific(CategoryRejection.AlreadyArchived) to SaveError.Conflict
        )

        val seen = refusals.map { (reason, _) ->
            commands.answer = { rejected(reason) }
            val form = form()
            form.name("Cafetería")
            form.save()
            Triple(form.now.saveError, form.now.name, form.now.result)
        }

        assertEquals(refusals.map { Triple(it.second, "Cafetería", null) }, seen)
        assertNull(notices.notice.value)
    }

    @Test
    fun aNameTakenMeanwhileIsShownOnTheNameAndNothingIsSaved() {
        commands.answer = { rejected(Specific(CategoryRejection.NameTaken)) }
        val form = form()
        form.name("Nueva")

        form.save()

        assertEquals(NameError.Taken, form.now.nameError)
        assertNull(form.now.result)
        assertEquals(emptyList<EditPart>(), form.now.savedParts)
    }

    // --- Outcomes that are not known ---

    @Test
    fun anUnknownOutcomeIsLookedUpAndNeverSentAgain() {
        commands.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val form = form()
        form.name("Cafetería")
        form.save()
        val unknown = form.now
        // It did commit: storage now holds the new name at the next version.
        commands.found = { Outcome.Success(change(it)) }
        reads.categories = listOf(stored(1, "Cafetería", limited, version = 6), mercado)

        form.onAction(CategoryEditorAction.Retry)

        assertEquals(SaveError.Unknown, unknown.saveError)
        assertFalse(unknown.canSubmit || unknown.editable || unknown.canArchive)
        assertEquals(1, commands.submitted.size)
        assertEquals(EditorResult.Updated, form.now.result)
    }

    @Test
    fun anUnknownOutcomeThatDidNotCommitLeavesTheChangeReadyToSendAgain() {
        commands.answer = { CommandOutcome.OutcomeUnknown(it.operationId) }
        val form = form()
        form.name("Cafetería")

        form.save()

        assertEquals(listOf(commands.submitted.single().operationId), commands.looked)
        assertEquals(SaveError.NotSaved, form.now.saveError)
        assertTrue(form.now.changed && form.now.canSubmit)
        assertNull(form.now.result)
    }

    @Test
    fun afterTheProcessWasKilledMidSaveTheOutcomeIsLookedUpAndTheDraftIsKept() {
        val saved = opening()
        val first = form(saved)
        first.name("Cafetería")
        first.limit("120000")
        commands.hold = CompletableDeferred()
        first.save()
        // The rename had committed; the limit was never sent.
        commands.found = { Outcome.Success(change(it)) }
        reads.categories = listOf(stored(1, "Cafetería", limited, version = 6), mercado)
        commands.hold = null

        val restored = form(saved.afterProcessDeath())
        val back = restored.now
        restored.save()

        assertEquals(listOf(commands.submitted.first().operationId), commands.looked)
        assertEquals(listOf("Cafetería", "120000"), listOf(back.name, back.limitText))
        assertTrue(back.changed && back.canSubmit)
        assertNull(back.result)
        assertEquals(
            listOf(RenameCategory::class, SetDefaultLimit::class),
            commands.submitted.map { it::class }
        )
        assertEquals(6L, (commands.submitted.last() as CategoryEdit).expectedVersion)
    }

    @Test
    fun aDraftRestoredAfterTheProcessWasKilledIsNotReadOverAndKeepsTheVersionItRead() {
        val saved = opening()
        val first = form(saved)
        first.name("Cafetería")
        reads.categories = listOf(stored(1, "Tinto", limited, version = 9), mercado)

        val restored = form(saved.afterProcessDeath())
        restored.save()

        assertEquals("Cafetería", restored.now.name)
        assertEquals(5L, (commands.submitted.single() as CategoryEdit).expectedVersion)
    }

    // --- Archiving ---

    @Test
    fun archivingAsksFirstWithHowManyExpensesStayAndDoesNothingIfDismissed() {
        snapshots.ready(
            monthOf(
                "2026-10-09",
                listOf(categoryIn(1, "Café", 100_000)),
                List(13) { storedExpense(100 + it, 1, "2026-10-05", 1_000) }
            )
        )
        val form = form()
        form.name("Cafetería")

        form.onAction(CategoryEditorAction.Archive)
        val asked = form.now.archiveQuestion
        form.onAction(CategoryEditorAction.ArchiveDismissed)

        assertEquals(
            ArchiveQuestion(
                unarchive = false,
                expensesThisMonth = 13,
                currentMonth = october,
                resumesBudget = false,
                discardsChanges = true
            ),
            asked
        )
        assertNull(form.now.archiveQuestion)
        assertEquals(emptyList<CategoryCommand>(), commands.submitted)
        assertEquals("Cafetería", form.now.name)
    }

    /**
     * Rule 9 with a payment due and not recorded yet: Sello records what is due, then
     * archives under a new operation, since the refused one wrote nothing (owner,
     * 2026-10-25).
     */
    @Test
    fun archivingWhileAPaymentIsDueRecordsItFirstThenArchives() {
        var answers = 0
        commands.answer = {
            if (answers++ ==
                0
            ) {
                rejected(Specific(CategoryRejection.SchedulesPending))
            } else {
                committed(it.operationId)
            }
        }
        val form = form()
        form.onAction(CategoryEditorAction.Archive)

        form.onAction(CategoryEditorAction.ArchiveConfirmed)

        assertEquals(listOf("ArchiveCategory", "catch-up", "ArchiveCategory"), events)
        val (first, second) = commands.submitted.map { it as ArchiveCategory }
        assertNotEquals(first.operationId, second.operationId)
        assertEquals(5L to 5L, first.expectedVersion to second.expectedVersion)
        assertEquals(EditorResult.Archived, form.now.result)
        assertEquals(CorrectionNotice.CategoryArchived, notices.notice.value)
    }

    @Test
    fun aCatchUpThatStopsArchivesNothingAndSaysWhy() {
        commands.answer = { rejected(Specific(CategoryRejection.SchedulesPending)) }
        caughtUp = CatchUpResult(
            emptyList(),
            CatchUpStop.Failed(StorageFailure.Unavailable("SQLException"))
        )
        val form = form()
        form.onAction(CategoryEditorAction.Archive)

        form.onAction(CategoryEditorAction.ArchiveConfirmed)

        assertEquals(listOf("ArchiveCategory", "catch-up"), events)
        assertEquals(SaveError.PaymentsPending, form.now.saveError)
        assertFalse(form.now.busy)
        assertNull(form.now.result)
    }

    @Test
    fun aPaymentStillDueAfterCatchUpIsAskedOnceThenSaid() {
        commands.answer = { rejected(Specific(CategoryRejection.SchedulesPending)) }
        val form = form()
        form.onAction(CategoryEditorAction.Archive)

        form.onAction(CategoryEditorAction.ArchiveConfirmed)

        assertEquals(listOf("ArchiveCategory", "catch-up", "ArchiveCategory"), events)
        assertEquals(SaveError.PaymentsPending, form.now.saveError)
        assertNull(form.now.result)
    }

    @Test
    fun unarchivingNeverWaitsForCatchUp() {
        val form = form(opening(category = 2))
        form.onAction(CategoryEditorAction.Archive)

        form.onAction(CategoryEditorAction.ArchiveConfirmed)

        assertEquals(listOf("UnarchiveCategory"), events)
        assertEquals(EditorResult.Unarchived, form.now.result)
    }

    @Test
    fun confirmingArchivesTheVersionReadAndSaysSo() {
        val form = form()
        form.onAction(CategoryEditorAction.Archive)
        val asked = form.now.archiveQuestion

        form.onAction(CategoryEditorAction.ArchiveConfirmed)

        // The month could not be counted: the question is asked without a number.
        assertEquals(null to false, asked?.expensesThisMonth to asked?.discardsChanges)
        val sent = commands.submitted.single() as ArchiveCategory
        assertEquals(
            listOf(uuid(1), 5L, 7L),
            listOf(sent.categoryId.value, sent.expectedVersion, sent.generation)
        )
        assertEquals(EditorResult.Archived, form.now.result)
        assertEquals(CorrectionNotice.CategoryArchived, notices.notice.value)
    }

    @Test
    fun anArchivedCategoryIsOfferedToUnarchiveAndToldWhetherItsBudgetComesBack() {
        val pausedSince = form(opening(category = 2))
        pausedSince.onAction(CategoryEditorAction.Archive)
        val later = pausedSince.now.archiveQuestion
        reads.categories = listOf(cafe, stored(2, "Mercado", limited, archived = true))
        val sameMonth = form(opening(category = 2))
        sameMonth.onAction(CategoryEditorAction.Archive)
        val same = sameMonth.now.archiveQuestion

        sameMonth.onAction(CategoryEditorAction.ArchiveConfirmed)

        assertTrue(pausedSince.now.archived)
        assertEquals(true to true, later?.unarchive to later?.resumesBudget)
        assertEquals(true to false, same?.unarchive to same?.resumesBudget)
        assertEquals(uuid(2), (commands.submitted.single() as UnarchiveCategory).categoryId.value)
        assertEquals(EditorResult.Unarchived, sameMonth.now.result)
        assertEquals(CorrectionNotice.CategoryUnarchived, notices.notice.value)
    }

    @Test
    fun anArchiveThatWasRefusedKeepsTheFormOpenAndSaysWhy() {
        commands.answer = { rejected(CommandRejection.VersionConflict(6)) }
        val form = form()
        form.onAction(CategoryEditorAction.Archive)

        form.onAction(CategoryEditorAction.ArchiveConfirmed)

        assertEquals(SaveError.Conflict, form.now.saveError)
        assertNull(form.now.result)
        assertNull(form.now.archiveQuestion)
    }

    @Test
    fun anArchivedCategoryKeepsItsNameEditableButHasNoLimitToChangeGoingForward() {
        val now = form(opening(category = 2))
        now.limit("5")
        now.onAction(CategoryEditorAction.UnlimitedChanged(true))
        now.name("Plaza")
        now.save()
        reads.categories =
            listOf(
                cafe,
                stored(
                    2,
                    "Mercado",
                    MonthBudgetState.Limited(finite(50_000), true),
                    archived = true
                )
            )
        val past = form(opening(category = 2, month = september))
        past.limit("60000")
        past.save()

        assertTrue(now.state.value.result == EditorResult.Updated)
        assertFalse(past.now.limitLocked)
        assertEquals(
            listOf(RenameCategory::class, SetMonthLimit::class),
            commands.submitted.map { it::class }
        )
    }

    @Test
    fun theLimitOfAnArchivedCategoryIsLockedInTheCurrentMonth() {
        val form = form(opening(category = 2))

        assertTrue(form.now.limitLocked && form.now.archived)
    }

    // --- Leaving ---

    @Test
    fun backAsksOnlyWhenSomethingWasChanged() {
        val untouched = form()
        untouched.onAction(CategoryEditorAction.Back)
        val changed = form()
        changed.name("Cafetería")

        changed.onAction(CategoryEditorAction.Back)

        assertEquals(EditorResult.Discarded, untouched.now.result)
        assertTrue(changed.now.confirmDiscard)
        assertNull(changed.now.result)
    }

    @Test
    fun aChangedLimitReplacesTheOldOneInTheMonthsTotalOfTheMonthBeingEdited() {
        reads.categories = listOf(
            stored(1, "Café", MonthBudgetState.Limited(finite(80_000), true)),
            stored(3, "Comida", MonthBudgetState.Limited(finite(900_000), false)),
            mercado
        )
        val form = form(opening(month = september))

        form.limit("120000")

        val total = form.now.limitTotal!!
        assertEquals("1.020.000", total.amount.digits)
        assertTrue(total.changes)
        assertEquals(september, total.month)
    }

    @Test
    fun withSinLimiteOnTheTotalIsNotShown() {
        reads.categories =
            listOf(cafe, stored(3, "Comida", MonthBudgetState.Limited(finite(900_000), false)))
        val form = form()

        form.onAction(CategoryEditorAction.UnlimitedChanged(true))

        assertNull(form.now.limitTotal)
    }

    // --- The limit on the Sello pad ---

    @Test
    fun thePadChangesTheStoredLimitAndShowsItAsAnAmount() {
        val form = form()

        form.onAction(CategoryEditorAction.LimitBackspace)
        form.onAction(CategoryEditorAction.LimitTripleZero)

        // 100000 → 10000 → 10000000.
        assertEquals("10000000", form.now.limitText)
        assertEquals("10.000.000 pesos", form.now.limitAmount?.spoken)
        assertTrue(form.now.changed)
    }

    @Test
    fun itOpensShowingTheStoredLimitAsAnAmount() {
        assertEquals("100.000 pesos", form().now.limitAmount?.spoken)
    }

    @Test
    fun theIconsTheOtherCategoriesUseAreKnownButNotItsOwn() {
        // Café is local_cafe; Mercado, archived, is home.
        assertEquals(setOf("home"), form().now.usedIconKeys)
    }
}
