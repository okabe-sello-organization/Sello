package com.software.sello.devtools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.composition.DebugComposition
import com.software.sello.composition.SandboxNames
import com.software.sello.composition.eraseFinancialStorage
import com.software.sello.composition.openStorage
import com.software.sello.composition.productionModules
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.Resets
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/**
 * Scenarios replayed in the sandbox on a real database, through the app's own
 * commands, month session and snapshots. Each [open] is a new process on the stored
 * session: a new graph, clock and runner, as after the app restarts.
 */
@RunWith(AndroidJUnit4::class)
class ScenarioReplayTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var graph: KoinApplication? = null
    private var tools: SandboxTools? = null
    private lateinit var store: MemoryStore

    private val runner get() = checkNotNull(tools?.scenarios)

    @Before
    fun start() = eraseFinancialStorage(context, SandboxNames)

    @After
    fun end() {
        graph?.endProcess()
        eraseFinancialStorage(context, SandboxNames)
    }

    private fun load(scenario: Scenario) {
        store = MemoryStore(
            SandboxSession(
                active = true,
                date = scenario.start,
                zone = scenario.zone,
                scenario = scenario.key
            )
        )
        open()
    }

    private fun open() {
        graph?.endProcess()
        val composition = DebugComposition(store, {}) { tools = it }
        val opened = openStorage(
            context,
            checkNotNull(composition.newDataZone),
            SystemDispatcherProvider(),
            SystemAuditClock(),
            SandboxNames
        ) as Outcome.Success
        val zone = ProcessFinancialZone().apply { initialize(opened.value.profile.zone) }
        graph = koinApplication {
            allowOverride(true)
            modules(
                productionModules(context, zone, opened.value.storage, SandboxNames) +
                    composition.modules(opened.value.profile)
            )
        }
    }

    private fun runToTheEnd() = runBlocking {
        while (runner.step()) Unit
        assertTrue("${runner.state.value.problem}", runner.state.value.finished)
    }

    private fun <T> rows(sql: String): List<String> = SQLiteDatabase.openDatabase(
        context.getDatabasePath("sandbox.db").path,
        null,
        SQLiteDatabase.OPEN_READONLY
    ).use { db ->
        db.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        (0 until cursor.columnCount).joinToString("|") {
                            cursor.getString(it)
                                ?: "null"
                        }
                    )
                }
            }
        }
    }

    private fun history() = rows<String>(
        "SELECT operation_id, kind, revision FROM operation_receipt ORDER BY revision"
    ) +
        rows<String>(
            "SELECT amount_minor, effective_date FROM expense ORDER BY effective_date, amount_minor"
        ) +
        rows<String>("SELECT amount_minor, effective_date, source_key FROM income")

    private fun cop(pesos: Long) = Money.cop(pesos)

    @Test
    fun g01ReplaysToTheApprovedFigures() = runBlocking {
        load(Scenarios.G01)
        runToTheEnd()

        val october = YearMonth.of(2026, 10)
        val koin = checkNotNull(graph).koin
        val budget = (koin.get<MonthlySnapshots>().read(october) as Outcome.Success).value
        assertEquals(cop(30_000), budget.spent)
        val limited = budget.overall as OverallBudget.Limited
        assertEquals(cop(70_000), limited.budget.remaining)
        val resumen = (koin.get<ResumenSnapshots>().read(october) as Outcome.Success).value
        assertEquals(cop(200_000), resumen.income)
        assertEquals(cop(170_000), resumen.netFlow)
        assertEquals(cop(100_000), resumen.unallocated)
        assertEquals(
            listOf(10_000L, 25_000L, 30_000L).map(::cop),
            resumen.daily.filter { it.date <= LocalDate.of(2026, 10, 3) }.map { it.cumulative }
        )
        assertEquals(listOf("3"), rows<String>("SELECT COUNT(*) FROM expense"))
    }

    @Test
    fun aRunInterruptedAfterACommitAndResumedMatchesAFreshRunExactly() = runBlocking {
        load(Scenarios.G01)
        runToTheEnd()
        val fresh = history()
        graph?.endProcess()
        graph = null
        eraseFinancialStorage(context, SandboxNames)

        load(Scenarios.G01)
        repeat(4) { runner.step() }
        // Step 4 (the 15.000 expense) committed, but the process ended before its checkpoint.
        store.session = store.session.copy(nextStep = 3)
        open()
        runToTheEnd()

        assertEquals(fresh, history())
        assertEquals(listOf("3"), rows<String>("SELECT COUNT(*) FROM expense"))
        assertEquals("Ya estaba guardado", runner.state.value.log.first { it.index == 3 }.result)
    }

    @Test
    fun sixMonthsGrowTheMonthByMonthHistoryWithTheScenariosOwnTotals() = runBlocking {
        val scenario = Scenarios.SixMonths
        load(scenario)
        runToTheEnd()

        // The expected totals come from the scenario's own steps, month by month.
        val expected = mutableMapOf<YearMonth, Long>()
        var month = YearMonth.from(scenario.start)
        scenario.steps.forEach { step ->
            when (step) {
                is ScenarioStep.AdvanceTo -> month = YearMonth.from(step.date)
                is ScenarioStep.Expense -> expected.merge(month, step.pesos, Long::plus)
                else -> Unit
            }
        }
        val resumen = (
            checkNotNull(graph).koin.get<ResumenSnapshots>().read(YearMonth.of(2026, 10))
                as Outcome.Success
            ).value
        val shown = resumen.history.filter { it.expenseCount > 0 }
        assertEquals(expected.keys.sorted(), shown.map { it.month })
        assertEquals(expected.toSortedMap().values.map(::cop), shown.map { it.spent })
        assertTrue(shown.all { it.expenseCount == 6 })
    }

    @Test
    fun theToolsStepPlayAndPauseTheLoadedScenario() {
        load(Scenarios.G01)
        rule.setContent { SelloTheme(reducedMotion = true) { ToolsScreen(checkNotNull(tools)) } }
        val progress = rule.onNodeWithTag(TOOLS_SCENARIO_PROGRESS_TAG)
        progress.performScrollTo().assertTextEquals("Paso 1 de 8")

        rule.onNodeWithTag(TOOLS_SCENARIO_STEP_TAG).performScrollTo().performClick()
        rule.waitUntil(5_000) { runner.state.value.next == 1 }
        progress.assertTextEquals("Paso 2 de 8")

        rule.onNodeWithTag(TOOLS_SCENARIO_PLAY_TAG).performScrollTo().performClick()
        rule.waitUntil(10_000) { runner.state.value.next >= 3 }
        rule.onNodeWithTag(TOOLS_SCENARIO_PLAY_TAG).performScrollTo().performClick()
        rule.waitUntil(5_000) { !runner.state.value.playing }
        // Paused between steps: what is shown is what is kept for the next process.
        assertEquals(runner.state.value.next, store.session.nextStep)
    }

    @Test
    fun aResetBetweenStepsKeepsTheRunnerOutOfTheNewHistory() = runBlocking {
        load(Scenarios.G01)
        repeat(3) { runner.step() }
        assertTrue(rows<String>("SELECT id FROM category").isNotEmpty())
        val resets = checkNotNull(graph).koin.get<Resets>()
        val preview = (resets.preview() as Outcome.Success).value
        val erased = resets.reset(
            preview,
            (OperationId.of("00000000-0000-4000-8000-000000009001") as Outcome.Success).value
        )
        assertTrue("$erased", erased is ResetOutcome.Erased)

        // The runner was built for the history before the reset; its next step is refused.
        runner.step()

        assertTrue("${runner.state.value}", runner.state.value.problem != null)
        for (table in listOf("category", "expense", "income")) {
            assertEquals(table, emptyList<String>(), rows<String>("SELECT * FROM $table"))
        }
    }

    @Test
    fun afterAResetANewProcessBringsBackNoneOfTheScenarioItErased() = runBlocking {
        load(Scenarios.G01)
        repeat(4) { runner.step() }
        val resets = checkNotNull(graph).koin.get<Resets>()
        resets.reset(
            (resets.preview() as Outcome.Success).value,
            (OperationId.of("00000000-0000-4000-8000-000000009002") as Outcome.Success).value
        )

        // A new process on the same session, which still names the scenario and its step.
        open()
        runner.recover().join()
        runner.step()

        // Its earlier steps find no receipt, and it does not move past the step it was on:
        // nothing it wrote comes back.
        assertEquals(listOf("history.reset"), rows<String>("SELECT kind FROM operation_receipt"))
        assertTrue(runner.state.value.log.none { it.revision != null })
        assertEquals(5, runner.state.value.next)
        for (table in listOf("category", "default_limit", "expense", "income")) {
            assertEquals(table, emptyList<String>(), rows<String>("SELECT * FROM $table"))
        }
    }
}
