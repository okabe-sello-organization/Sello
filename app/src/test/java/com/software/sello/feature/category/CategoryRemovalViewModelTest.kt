package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.DeleteCategory
import com.software.sello.domain.port.Disposal
import com.software.sello.domain.port.RemovalPreview
import com.software.sello.domain.port.RemovalPreviewFailure
import com.software.sello.domain.port.TargetPreview
import com.software.sello.domain.usecase.CatchUpResult
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deleting a category from its editor (SELLO-138, ADR 0024). Storage holds Café (1), limit
 * 50.000 at version 5, with 34 expenses for 412.000 from May to October 2026 and one fixed
 * payment, 60.000 of them spent in October; Comida (2), limit 200.000, 80.000 spent in
 * October; Hogar (3), limit 400.000, 200.000 spent; and Mercado (4), archived. Today is
 * 22 October 2026; the generation is 7. Every figure expected is worked out by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRemovalViewModelTest {
    private fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun id(number: Int) = (CategoryId.of(uuid(number)) as Outcome.Success).value

    private val october = YearMonth.of(2026, 10)
    private val may = YearMonth.of(2026, 5)

    private inner class HandCommands : CategoryCommands {
        val submitted = mutableListOf<CategoryCommand>()
        val looked = mutableListOf<OperationId>()
        val answers = ArrayDeque<CategoryCommandOutcome>()
        var hold: CompletableDeferred<Unit>? = null
        var found: (OperationId) -> Outcome<CommittedCategoryChange?, StorageFailure> =
            { Outcome.Success(null) }

        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome {
            submitted += command
            hold?.await()
            return answers.removeFirstOrNull() ?: committed(command.operationId)
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

    private fun rejected(reason: CategoryRejection) = CommandOutcome.Rejected(Specific(reason))

    private val commands = HandCommands()
    private val previews = HandRemovalPreviews()
    private val reads = HandCategoryReads().apply { generation = 7 }
    private val snapshots = HandSnapshots()
    private val session = MonthSession(HandFinancialClock("2026-10-22"), HandMonotonicClock())
    private var nextId = 500
    private lateinit var notices: Corrections

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun finite(pesos: Long) = (BudgetLimit.finite(cop(pesos)) as Outcome.Success).value

    private fun stored(number: Int, name: String, archived: Boolean = false, version: Long = 5) =
        CategoryBudget(
            Category(
                id(number),
                (CategoryName.of(name) as Outcome.Success).value,
                (IconKey.of("local_cafe") as Outcome.Success).value,
                archived = archived,
                version = version,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH
            ),
            if (archived) {
                MonthBudgetState.Paused
            } else {
                MonthBudgetState.Limited(finite(50_000), explicit = false)
            }
        )

    /** Deleting Café with all it holds: 34 expenses for 412.000, May to October, 1 payment. */
    private fun everything(expenses: Int = 34, total: Long = 412_000) = RemovalPreview(
        categoryId = id(1),
        expenses = expenses,
        total = cop(total),
        firstMonth = may,
        lastMonth = october,
        schedules = 1,
        month = october,
        spentThisMonth = cop(60_000),
        limitThisMonth = finite(50_000),
        target = null
    )

    private val nothing = RemovalPreview(
        id(1), 0, cop(0), null, null, 0, october, cop(0), finite(50_000), null
    )

    /** Moving them into Comida: 80.000 spent there this October, limit 200.000. */
    private fun intoComida(spent: Long = 80_000) = everything().copy(
        target = TargetPreview(id(2), cop(spent), finite(200_000))
    )

    private fun answer(
        all: Outcome<RemovalPreview, RemovalPreviewFailure> = Outcome.Success(everything()),
        move: Outcome<RemovalPreview, RemovalPreviewFailure> = Outcome.Success(intoComida())
    ) {
        previews.answer = { _, disposal -> if (disposal is Disposal.MoveTo) move else all }
    }

    private fun publishOctober(comidaArchived: Boolean = false) {
        snapshots.ready(
            monthOf(
                "2026-10-22",
                listOf(
                    categoryIn(1, "Café", 50_000),
                    categoryIn(2, "Comida", 200_000, archived = comidaArchived),
                    categoryIn(3, "Hogar", 400_000),
                    categoryIn(4, "Mercado", null, archived = true)
                ),
                listOf(
                    storedExpense(10, 1, "2026-10-06", 60_000),
                    storedExpense(11, 2, "2026-10-05", 80_000),
                    storedExpense(12, 3, "2026-10-07", 200_000)
                )
            )
        )
    }

    @Before
    fun mainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        notices = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { Duration.ZERO },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
        )
        reads.categories = listOf(stored(1, "Café"), stored(2, "Comida"))
        publishOctober()
    }

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun opening() =
        SavedStateHandle(mapOf(CategoryManagementViewModel.KEY_CATEGORY to uuid(1)))

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    private fun form(saved: SavedStateHandle = opening()) = CategoryManagementViewModel(
        saved,
        commands,
        reads,
        snapshots,
        session,
        { uuid(nextId++) },
        testMoney(),
        notices,
        { CatchUpResult(emptyList(), stopped = null) },
        previews
    )

    private val CategoryManagementViewModel.removal get() = state.value.removal
    private val CategoryManagementViewModel.step get() = removal?.step

    private fun CategoryManagementViewModel.act(vararg actions: RemovalAction) =
        actions.forEach(::onAction)

    private fun sent(index: Int = 0) = commands.submitted[index] as DeleteCategory

    private fun counts(step: RemovalStep?) = when (step) {
        is RemovalStep.Choose -> step.counts
        is RemovalStep.All -> step.counts
        is RemovalStep.Move -> step.counts
        else -> null
    }?.let { listOf(it.expenses, it.total.digits, it.firstMonth, it.lastMonth, it.schedules) }

    // --- Nothing in it ---

    @Test
    fun anEmptyCategoryIsAskedOnceAndDeletedWithItsLimits() {
        answer(all = Outcome.Success(nothing))
        val form = form()

        form.act(RemovalAction.Start)
        val asked = form.step
        form.act(RemovalAction.Confirm)

        assertEquals(RemovalStep.Empty, asked)
        val sent = sent()
        assertEquals(
            listOf(uuid(500), 7L, uuid(1), 5L, Disposal.DeleteAll, nothing, null),
            listOf(
                sent.operationId.value,
                sent.generation,
                sent.categoryId.value,
                sent.expectedVersion,
                sent.disposal,
                sent.confirmed,
                sent.confirmation
            )
        )
        assertEquals(EditorResult.Deleted, form.state.value.result)
        assertEquals(CorrectionNotice.CategoryDeleted("Café", 0, null), notices.notice.value)
    }

    @Test
    fun cancelingDeletesNothing() {
        answer(all = Outcome.Success(nothing))
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Dismiss)

        assertNull(form.removal)
        assertTrue(commands.submitted.isEmpty())
        assertNull(form.state.value.result)
    }

    // --- It holds expenses: choose ---

    @Test
    fun aCategoryWithExpensesSaysWhatItHoldsAndOffersTheChoices() {
        answer()
        val form = form()

        form.act(RemovalAction.Start)

        val choose = form.step as RemovalStep.Choose
        assertEquals(listOf(34, "412.000", may, october, 1), counts(choose))
        assertTrue(choose.canArchive)
        assertEquals(listOf(id(1) to Disposal.DeleteAll), previews.asked)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun archivingInsteadAsksTheArchiveQuestionAndDeletesNothing() {
        answer()
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.ArchiveInstead)

        assertNull(form.removal)
        assertFalse(form.state.value.archiveQuestion!!.unarchive)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun anArchivedCategoryIsNotPointedToArchiving() {
        reads.categories = listOf(stored(1, "Café", archived = true))
        answer()
        val form = form()

        form.act(RemovalAction.Start)

        assertFalse((form.step as RemovalStep.Choose).canArchive)
    }

    // --- Mover ---

    @Test
    fun theListHasEveryActiveCategoryButThisOneWithWhatItsReciboCellShows() {
        answer()
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move)

        val pick = form.step as RemovalStep.Pick
        // Comida 200.000 − 80.000 = 120.000; Hogar 400.000 − 200.000 = 200.000. Café and
        // the archived Mercado are not offered.
        assertEquals(
            mapOf("Comida" to "120.000", "Hogar" to "200.000"),
            pick.targets!!.associate { it.name to it.amount.digits }
        )
        assertEquals(october, pick.month)
    }

    @Test
    fun movingIntoComidaShowsTheExactFactsAndSendsThePreviewShown() {
        answer()
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))
        val move = form.step as RemovalStep.Move
        form.act(RemovalAction.Confirm)

        // 80.000 + 60.000 = 140.000.
        assertEquals(
            listOf("Comida", october, "80.000", "140.000"),
            listOf(
                move.facts.target,
                move.facts.month,
                move.facts.targetSpent.digits,
                move.facts.targetSpentAfter.digits
            )
        )
        assertEquals("50.000", (move.facts.sourceLimit as RemovalLimit.Finite).amount.digits)
        assertEquals("200.000", (move.facts.targetLimit as RemovalLimit.Finite).amount.digits)
        assertEquals(id(1) to Disposal.MoveTo(id(2)), previews.asked.last())
        val sent = sent()
        assertEquals(Disposal.MoveTo(id(2)), sent.disposal)
        assertEquals(intoComida(), sent.confirmed)
        assertEquals(EditorResult.Deleted, form.state.value.result)
        assertEquals(CorrectionNotice.CategoryDeleted("Café", 34, "Comida"), notices.notice.value)
    }

    @Test
    fun anUnlimitedSourceSaysSoAndATargetWithoutALimitHasNoRow() {
        answer(
            move = Outcome.Success(
                intoComida().copy(
                    limitThisMonth = BudgetLimit.Unlimited,
                    target = TargetPreview(id(2), cop(80_000), null)
                )
            )
        )
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))

        val facts = (form.step as RemovalStep.Move).facts
        assertEquals(RemovalLimit.Unlimited, facts.sourceLimit)
        assertNull(facts.targetLimit)
    }

    @Test
    fun aSpendingSumThatDoesNotFitOffersNoDeletion() {
        answer(move = Outcome.Success(intoComida(spent = Long.MAX_VALUE - 1)))
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))
        form.act(RemovalAction.Confirm)

        assertEquals(RemovalStep.Problem(RemovalProblem.CannotCount), form.step)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun movingIntoACarryingCategoryShowsTheListAndSendsItsConfirmation() {
        answer()
        val confirmation = CorrectionConfirmation(41, "move")
        commands.answers += rejected(
            CategoryRejection.CorrectionUnconfirmed(
                confirmation,
                CarryCorrection(emptyList(), emptyList(), emptyList(), october, emptyMap())
            )
        )
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))
        form.act(RemovalAction.Confirm)
        val asked = form.step as RemovalStep.Correction
        form.act(RemovalAction.Confirm)

        assertEquals("Comida" to false, asked.target to asked.changed)
        assertNull(sent(0).confirmation)
        assertEquals(confirmation, sent(1).confirmation)
        assertEquals(intoComida(), sent(1).confirmed)
        assertEquals(EditorResult.Deleted, form.state.value.result)
    }

    // --- Borrar todo ---

    @Test
    fun borrarTodoWaitsForTheWordAsResetReadsIt() {
        answer()
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Confirm)
        val untyped = form.step as RemovalStep.All
        form.act(RemovalAction.Word("borrar"), RemovalAction.Confirm)
        val lower = form.step as RemovalStep.All
        form.act(RemovalAction.Word(" BORRAR "), RemovalAction.Confirm)

        assertEquals(listOf(34, "412.000", may, october, 1), counts(untyped))
        assertFalse(untyped.canDelete || lower.canDelete)
        val sent = sent()
        assertEquals(1, commands.submitted.size)
        assertEquals(Disposal.DeleteAll to everything(), sent.disposal to sent.confirmed)
        assertEquals(CorrectionNotice.CategoryDeleted("Café", 34, null), notices.notice.value)
    }

    @Test
    fun aDoubleTapDeletesOnce() {
        answer()
        commands.hold = CompletableDeferred()
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Word("BORRAR"))
        form.act(RemovalAction.Confirm, RemovalAction.Confirm, RemovalAction.Dismiss)
        val busy = form.removal!!
        commands.hold!!.complete(Unit)

        assertTrue(busy.busy)
        assertEquals(1, commands.submitted.size)
        assertEquals(EditorResult.Deleted, form.state.value.result)
    }

    // --- An unknown outcome ---

    @Test
    fun anUnknownOutcomeIsCheckedWithTheSameOperationAndNeverSentAgain() {
        answer()
        commands.answers +=
            CommandOutcome.OutcomeUnknown((OperationId.of(uuid(500)) as Outcome.Success).value)
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Word("BORRAR"))
        form.act(RemovalAction.Confirm)
        val checking = form.step
        form.act(RemovalAction.Dismiss)
        commands.found = { Outcome.Success(change(it)) }
        form.act(RemovalAction.Next)

        assertEquals(RemovalStep.Checking(failed = true), checking)
        assertEquals(listOf(uuid(500), uuid(500)), commands.looked.map { it.value })
        assertEquals(1, commands.submitted.size)
        assertEquals(EditorResult.Deleted, form.state.value.result)
        assertEquals(CorrectionNotice.CategoryDeleted("Café", 34, null), notices.notice.value)
    }

    @Test
    fun anOperationInFlightWhenTheProcessDiedIsCheckedBeforeAnythingElse() {
        answer()
        commands.hold = CompletableDeferred()
        val saved = opening()
        val first = form(saved)
        first.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))
        first.act(RemovalAction.Confirm)
        commands.found = { Outcome.Success(change(it)) }
        previews.asked.clear()

        val restored = form(saved.afterProcessDeath())

        assertEquals(listOf(uuid(500)), commands.looked.map { it.value })
        assertEquals(1, commands.submitted.size)
        assertTrue(previews.asked.isEmpty())
        assertEquals(EditorResult.Deleted, restored.state.value.result)
        assertEquals(CorrectionNotice.CategoryDeleted("Café", 34, "Comida"), notices.notice.value)
    }

    @Test
    fun anOperationFoundNotWrittenAfterTheProcessDiedSaysSoAndCountsAgainToRetry() {
        answer()
        commands.hold = CompletableDeferred()
        val saved = opening()
        form(saved).act(
            RemovalAction.Start,
            RemovalAction.DeleteAll,
            RemovalAction.Word("BORRAR"),
            RemovalAction.Confirm
        )

        val restored = form(saved.afterProcessDeath())
        val said = restored.step
        restored.act(RemovalAction.Next)

        assertEquals(RemovalStep.Problem(RemovalProblem.NotSaved(null)), said)
        assertTrue(restored.step is RemovalStep.Choose)
        assertEquals(1, commands.submitted.size)
    }

    @Test
    fun aStepOpenWhenTheProcessDiedIsCountedAgainAndReopenedWithTheWordTyped() {
        answer()
        val saved = opening()
        form(saved).act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Word("BOR"))
        answer(all = Outcome.Success(everything(expenses = 35, total = 472_000)))

        val restored = form(saved.afterProcessDeath())

        val all = restored.step as RemovalStep.All
        assertEquals("BOR", all.typed)
        assertEquals(listOf(35, "472.000", may, october, 1), counts(all))
        assertTrue(commands.submitted.isEmpty())
    }

    // --- When it does not happen ---

    @Test
    fun changedContentsShowTheNewCountsAndRevisarAsksAgainWithThem() {
        answer()
        val current = everything(expenses = 35, total = 472_000)
        commands.answers += rejected(CategoryRejection.ContentsChanged(current))
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Word("BORRAR"))
        form.act(RemovalAction.Confirm)
        val changed = (form.step as RemovalStep.Problem).problem as RemovalProblem.Changed
        form.act(RemovalAction.Next)
        val again = form.step as RemovalStep.All
        form.act(RemovalAction.Word("BORRAR"), RemovalAction.Confirm)

        assertEquals(
            "412.000" to "472.000",
            changed.before!!.total.digits to changed.now.total.digits
        )
        assertEquals(34 to 35, changed.before!!.expenses to changed.now.expenses)
        assertEquals("" to listOf(35, "472.000", may, october, 1), again.typed to counts(again))
        assertEquals(current, sent(1).confirmed)
        assertEquals(CorrectionNotice.CategoryDeleted("Café", 35, null), notices.notice.value)
    }

    @Test
    fun aTargetArchivedMeanwhileSaysSoAndElegirOtraReopensAFreshList() {
        answer()
        commands.answers += rejected(CategoryRejection.TargetArchived)
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))
        form.act(RemovalAction.Confirm)
        val said = form.step
        publishOctober(comidaArchived = true)
        form.act(RemovalAction.Next)

        assertEquals(RemovalStep.Problem(RemovalProblem.TargetClosed("Comida")), said)
        assertEquals(listOf("Hogar"), (form.step as RemovalStep.Pick).targets!!.map { it.name })
    }

    @Test
    fun aTargetGoneBeforeThePreviewSaysSoToo() {
        answer(move = Outcome.Failure(RemovalPreviewFailure.TargetMissing))
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))

        assertEquals(RemovalStep.Problem(RemovalProblem.TargetClosed("Comida")), form.step)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun storageThatFailedSaysWhatStaysAndReintentarSendsTheSamePreviewAsANewOperation() {
        answer()
        commands.answers += CommandOutcome.Rejected(
            CommandRejection.StorageFailed(StorageFailure.Unavailable("x"))
        )
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.Move, RemovalAction.Pick(uuid(2)))
        form.act(RemovalAction.Confirm)
        val said = (form.step as RemovalStep.Problem).problem as RemovalProblem.NotSaved
        form.act(RemovalAction.Next)

        assertEquals(34 to 1, said.counts!!.expenses to said.counts!!.schedules)
        assertEquals(listOf(uuid(500), uuid(501)), commands.submitted.map { it.operationId.value })
        assertEquals(sent(0).confirmed, sent(1).confirmed)
        assertEquals(EditorResult.Deleted, form.state.value.result)
    }

    @Test
    fun aRefusalThisFormDoesNotKnowIsNotSavedEither() {
        answer()
        commands.answers += rejected(CategoryRejection.SameCategory)
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Word("BORRAR"))
        form.act(RemovalAction.Confirm)

        assertTrue((form.step as RemovalStep.Problem).problem is RemovalProblem.NotSaved)
    }

    @Test
    fun countsThatCannotBeReadOfferNoDeletionAndReintentarCountsAgain() {
        answer(
            all = Outcome.Failure(RemovalPreviewFailure.Storage(StorageFailure.Unavailable("x")))
        )
        val form = form()

        form.act(RemovalAction.Start)
        val storage = form.step
        answer(all = Outcome.Failure(RemovalPreviewFailure.TotalOverflow))
        form.act(RemovalAction.Next)
        val overflow = form.step
        answer()
        form.act(RemovalAction.Next)

        assertEquals(RemovalStep.Problem(RemovalProblem.CannotCount), storage)
        assertEquals(RemovalStep.Problem(RemovalProblem.CannotCount), overflow)
        assertTrue(form.step is RemovalStep.Choose)
        assertTrue(commands.submitted.isEmpty())
    }

    @Test
    fun aVersionConflictReadsTheCategoryAgainAndSendsAgainstTheNewVersion() {
        answer()
        commands.answers += CommandOutcome.Rejected(CommandRejection.VersionConflict(current = 6))
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Word("BORRAR"))
        reads.categories = listOf(stored(1, "Café", version = 6), stored(2, "Comida"))
        answer(all = Outcome.Success(everything(expenses = 35, total = 472_000)))
        form.act(RemovalAction.Confirm)
        val changed = (form.step as RemovalStep.Problem).problem as RemovalProblem.Changed
        form.act(RemovalAction.Next, RemovalAction.Word("BORRAR"), RemovalAction.Confirm)

        assertEquals(35, changed.now.expenses)
        assertEquals(
            listOf(5L, 6L),
            commands.submitted.map {
                (it as DeleteCategory).expectedVersion
            }
        )
        assertEquals(EditorResult.Deleted, form.state.value.result)
    }

    @Test
    fun aCategoryGoneMeanwhileIsSaidInTheEditorsOwnWords() {
        answer(all = Outcome.Failure(RemovalPreviewFailure.CategoryMissing))
        val form = form()

        form.act(RemovalAction.Start)

        assertNull(form.removal)
        assertEquals(SaveError.CategoryGone, form.state.value.saveError)
    }

    @Test
    fun replacedDataIsSaidInTheEditorsOwnWords() {
        answer()
        commands.answers += CommandOutcome.Rejected(CommandRejection.StaleGeneration(current = 8))
        val form = form()

        form.act(RemovalAction.Start, RemovalAction.DeleteAll, RemovalAction.Word("BORRAR"))
        form.act(RemovalAction.Confirm)

        assertNull(form.removal)
        assertEquals(SaveError.DataChanged, form.state.value.saveError)
        assertNull(form.state.value.result)
    }

    @Test
    fun deletingCannotStartOnAFormThatIsSavingOrGone() {
        answer()
        val form = form(
            SavedStateHandle(mapOf(CategoryManagementViewModel.KEY_CATEGORY to uuid(99)))
        )

        form.act(RemovalAction.Start)

        assertNull(form.removal)
        assertTrue(previews.asked.isEmpty())
    }
}
