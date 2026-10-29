package com.software.sello.devtools

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.NightlyOnly
import com.software.sello.composition.SandboxNames
import com.software.sello.composition.eraseFinancialStorage
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sandbox's simulated faults on real Room, through the app's own commands and
 * reads. They change only what a caller is told; storage holds the truth, and the
 * inspector reads it. Rollback itself is proved by the real-Room command contract
 * tests in `:data`, not here.
 */
@RunWith(AndroidJUnit4::class)
class ReceiptRecoveryDiagnosticJourneyTest {
    @get:Rule
    val rule = createComposeRule()

    private val october1 = LocalDate.of(2026, 10, 1)
    private val sandbox = SandboxGraph(
        SandboxSession(active = true, date = october1, zone = java.time.ZoneId.of("America/Bogota"))
    )
    private val diagnostics get() = checkNotNull(sandbox.tools?.diagnostics)

    @Before
    fun start() {
        sandbox.erase()
        sandbox.open()
    }

    @After
    fun end() = sandbox.erase()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun operation() = OperationId.of(UUID.randomUUID().toString()).value()

    private fun category() = runBlocking {
        val created = sandbox.koin.get<CategoryCommands>().submit(
            CreateCategory(
                operation(),
                1,
                CategoryName.of("Comida").value(),
                IconKey.of("restaurant").value(),
                BudgetLimit.Unlimited
            )
        ) as CategoryCommandOutcome.Committed
        created.change.categoryId
    }

    private fun expense(category: com.software.sello.domain.model.CategoryId, pesos: Long) =
        CreateExpense(
            operation(),
            1,
            category,
            TransactionAmount.of(Money.cop(pesos)).value(),
            october1,
            null
        )

    @Test
    fun aLostAnswerAfterARealCommitRecoversItsReceiptAndNeverWritesTwice() = runBlocking {
        val commands = sandbox.koin.get<ExpenseCommands>()
        val command = expense(category(), 10_000)
        diagnostics.faults.arm(Fault.LostAnswer)

        val told = commands.create(command)

        assertEquals(ExpenseCommandOutcome.OutcomeUnknown(command.operationId), told)
        assertEquals(listOf("1"), sandbox.rows("SELECT COUNT(*) FROM expense"))
        // What a screen does next: look the operation up, never send a new one.
        val found = commands.find(command.operationId).value()
        assertEquals(command.operationId, found?.receipt?.operationId)
        val again = commands.create(command) as ExpenseCommandOutcome.Committed
        assertTrue(again.replayed)
        assertEquals(found?.receipt, again.expense.receipt)
        assertEquals(listOf("1"), sandbox.rows("SELECT COUNT(*) FROM expense"))
        val logged = diagnostics.log.state.value.last {
            it.operation == command.operationId &&
                it.fault != null
        }
        assertEquals(
            StoredOutcome.Saved(found!!.receipt.revision),
            diagnostics.inspector.stored(logged)
        )
    }

    // A whole scenario on real Room: nightly only, with the scenario corpus.
    @NightlyOnly
    @Test
    fun theEdgesScenarioMeetsEachExpectedOutcomeOnRealRoom() = runBlocking {
        sandbox.erase()
        sandbox.store.session = SandboxSession(
            active = true,
            date = Scenarios.Edges.start,
            zone = Scenarios.Edges.zone,
            scenario = Scenarios.Edges.key
        )
        sandbox.open()
        val runner = checkNotNull(sandbox.tools?.scenarios)

        while (runner.step()) Unit

        assertTrue("${runner.state.value.problem}", runner.state.value.finished)
        assertEquals(
            listOf(
                "Guardado", "Guardado", "Ya estaba guardado",
                "Rechazado, como se esperaba: VersionConflict", "Guardado", "Guardado",
                "Guardado", "Guardado", "Rechazado, como se esperaba: UndoExpired",
                "No se envió, como se esperaba: AboveMaximum"
            ),
            runner.state.value.log.map { it.result }
        )
        // Both expenses were deleted and neither came back; the double submit wrote one.
        assertEquals(listOf("0"), sandbox.rows("SELECT COUNT(*) FROM expense"))
        assertEquals(
            listOf(
                "expense.create",
                "expense.create",
                "expense.delete",
                "expense.delete",
                "expense.edit"
            ),
            sandbox.rows(
                "SELECT kind FROM operation_receipt WHERE kind LIKE 'expense.%' ORDER BY kind"
            )
        )
    }

    @Test
    fun theNormalProfileGetsNoFaultsAndNoDiagnostics() {
        sandbox.erase()
        sandbox.store.session = SandboxSession(active = false)
        // modules() publishes the tools before any graph exists, so no storage is needed.
        var published: SandboxTools? = null
        com.software.sello.composition.DebugComposition(sandbox.store, {}) { published = it }
            .modules(
                com.software.sello.domain.model.FinancialProfile(java.time.ZoneId.of("UTC"), 1, 0)
            )
        assertNull(published?.time)
        assertNull(published?.diagnostics)
        eraseFinancialStorage(sandbox.context, SandboxNames)
    }

    @Test
    fun theToolsArmAndClearFaultsAndExportOnlyWhenAsked() {
        val shared = mutableListOf<String>()
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                ToolsScreen(checkNotNull(sandbox.tools), onShare = { shared += it })
            }
        }

        rule.onNodeWithTag(faultTag(Fault.LostAnswer)).performScrollTo().performClick()
        rule.onNodeWithTag(faultTag(Fault.LostAnswer)).assertTextContains("· 1", substring = true)
        rule.onNodeWithTag(TOOLS_FAULTS_CLEAR_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(TOOLS_FAULTS_CLEAR_TAG).assertIsNotEnabled()
        assertEquals(emptyMap<Fault, Int>(), diagnostics.faults.state.value)
        assertTrue(shared.isEmpty())

        val command = expense(category(), 10_000)
        runBlocking { sandbox.koin.get<ExpenseCommands>().create(command) }
        rule.onNodeWithTag(TOOLS_EXPORT_TAG).performScrollTo().performClick()
        rule.waitUntil(5_000) { shared.isNotEmpty() }

        val text = shared.single()
        assertTrue(text, command.operationId.value in text && "saved rev" in text)
        assertTrue(text, "Comida" !in text && "10000" !in text && "10.000" !in text)
    }
}
