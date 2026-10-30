package com.software.sello.devtools

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.NightlyOnly
import com.software.sello.composition.DebugComposition
import com.software.sello.composition.SandboxNames
import com.software.sello.composition.eraseFinancialStorage
import com.software.sello.composition.openStorage
import com.software.sello.composition.productionModules
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
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/**
 * The sandbox's time controls driving the real app graph on the sandbox's own data:
 * the same storage, commands, month session and snapshots the screens use, with only
 * the financial clock replaced. The installed app's own graph and data are not used.
 */
@RunWith(AndroidJUnit4::class)
class TimeControlsJourneyTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bogota = ZoneId.of("America/Bogota")
    private val october31 = LocalDate.of(2026, 10, 31)
    private val store = MemoryStore(SandboxSession(active = true, date = october31))
    private var restarts = 0
    private var tools: SandboxTools? = null
    private lateinit var graph: KoinApplication

    private val time get() = checkNotNull(tools?.time)

    @Before
    fun openTheSandbox() {
        eraseFinancialStorage(context, SandboxNames)
        val opened = runBlocking {
            openStorage(
                context,
                bogota,
                SystemDispatcherProvider(),
                SystemAuditClock(),
                SandboxNames
            )
        } as Outcome.Success
        val composition = DebugComposition(store, { restarts++ }) { tools = it }
        val zone = ProcessFinancialZone().apply { initialize(opened.value.profile.zone) }
        graph = koinApplication {
            allowOverride(true)
            modules(
                productionModules(context, zone, opened.value.storage, SandboxNames) +
                    composition.modules(opened.value.profile)
            )
        }
    }

    @After
    fun closeTheSandbox() {
        graph.endProcess()
        eraseFinancialStorage(context, SandboxNames)
    }

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun operation() = OperationId.of(UUID.randomUUID().toString()).value()

    private fun expenseIn(category: String, date: LocalDate) = runBlocking {
        val created = graph.koin.get<CategoryCommands>().submit(
            CreateCategory(
                operation(),
                1,
                CategoryName.of(category).value(),
                IconKey.of("home").value(),
                BudgetLimit.Unlimited
            )
        ) as CommandOutcome.Committed
        graph.koin.get<ExpenseCommands>().create(
            CreateExpense(
                operation(),
                1,
                created.value.categoryId,
                TransactionAmount.of(Money.cop(48_700)).value(),
                date,
                null
            )
        ) as CommandOutcome.Committed
    }

    @Test
    fun steppingFromOctober31ToNovember1RollsTheMonthOverThroughTheNormalPaths() = runBlocking {
        val session = graph.koin.get<MonthSession>()
        val snapshots = graph.koin.get<MonthlySnapshots>()
        assertEquals(YearMonth.of(2026, 10), session.currentMonth.first())

        time.advanceDays(1)

        assertEquals(LocalDate.of(2026, 11, 1), graph.koin.get<FinancialClock>().today.value.date)
        assertEquals(YearMonth.of(2026, 11), withTimeout(5_000) { session.currentMonth.first() })
        val november = withTimeout(5_000) {
            snapshots.observe(YearMonth.of(2026, 11)).first { it is ReadState.Ready }
        } as ReadState.Ready
        assertEquals(LocalDate.of(2026, 11, 1), november.value.asOf)
        assertEquals(LocalDate.of(2026, 11, 1), store.session.date)
    }

    // Nightly only: the undo window's own clock is tested on the JVM (DebugGraphTest).
    @NightlyOnly
    @Test
    fun aYearOfFinancialTimeLaterUndoStillHasItsSixRealSeconds() = runBlocking {
        val expense = expenseIn("Comida", october31).value
        val commands = graph.koin.get<ExpenseCommands>()
        val deletion = operation()
        val deleted = commands.delete(DeleteExpense(deletion, 1, expense.expenseId, 1))
        assertTrue("$deleted", deleted is CommandOutcome.Committed)

        time.advanceMonths(12)

        assertNotNull(commands.undoOffer())
        val undone = commands.restore(RestoreExpense(operation(), 1, deletion))
        assertTrue("$undone", undone is CommandOutcome.Committed)
        assertEquals(LocalDate.of(2027, 10, 31), graph.koin.get<FinancialClock>().today.value.date)
    }

    @Test
    fun theToolsStepTheDateAndRefuseGoingBackOnceSomethingWasSaved() {
        rule.setContent { SelloTheme(reducedMotion = true) { ToolsScreen(checkNotNull(tools)) } }
        rule.onNodeWithTag(TOOLS_DATE_TAG).assertTextContains("31 de octubre de 2026")

        rule.onNodeWithTag(TOOLS_PLUS_DAY_TAG).performClick()
        rule.onNodeWithTag(TOOLS_DATE_TAG).assertTextContains("1 de noviembre de 2026")
        rule.onNodeWithTag(TOOLS_PLUS_MONTH_TAG).performClick()
        rule.onNodeWithTag(TOOLS_DATE_TAG).assertTextContains("1 de diciembre de 2026")

        expenseIn("Comida", LocalDate.of(2026, 12, 1))
        rule.onNodeWithText("Ya hay movimientos guardados: la fecha solo puede avanzar.")
            .performScrollTo().assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TOOLS_JUMP_FIELD_TAG)))
            .performScrollTo()
            .performTextInput("2026-10-01")
        rule.onNodeWithTag(TOOLS_JUMP_TAG).performScrollTo().performClick()
        rule.onNodeWithText(
            "Ya hay movimientos guardados después de esa fecha. Para volver atrás, reinicia el sandbox."
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(TOOLS_DATE_TAG).assertTextContains("1 de diciembre de 2026")
    }

    @Test
    fun resetAsksFirstThenErasesOnlyAfterConfirmingAndRestarts() {
        rule.setContent { SelloTheme(reducedMotion = true) { ToolsScreen(checkNotNull(tools)) } }

        rule.onNodeWithTag(TOOLS_RESET_TAG).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(0, restarts)

        rule.onNodeWithTag(TOOLS_RESET_CONFIRM_TAG).performScrollTo().performClick()
        rule.waitUntil(5_000) { restarts == 1 }
        assertEquals(SandboxSession(active = true, wipe = true), store.session)
    }

    @Test
    fun theBadgeSaysSandboxTheDateAndTheZoneAndOpensTheTools() {
        var opened = 0
        rule.setContent {
            SelloTheme(reducedMotion = true) { SandboxBadge(onOpen = { opened++ }, time = time) }
        }

        // Read as one button; the label drawn is "SANDBOX · 31 oct 2026 · America/Bogota".
        rule.onNodeWithContentDescription(
            "Sandbox activo, 31 de octubre de 2026, zona America/Bogota. Abrir herramientas"
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, opened)

        time.advanceDays(1)
        rule.onNodeWithContentDescription(
            "Sandbox activo, 1 de noviembre de 2026, zona America/Bogota. Abrir herramientas"
        ).assertIsDisplayed()
    }
}
