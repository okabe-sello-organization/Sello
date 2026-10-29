package com.software.sello.devtools

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.NightlyOnly
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupExports
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.Restores
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole scenario corpus on real Room, goals, schedules, investments and rates included,
 * with the app's own catch-up. Each scenario runs fresh, then again cut
 * off after a committed step whose checkpoint was lost and resumed in a new process;
 * both runs must leave the same history and every `Verify` must match. Each check's
 * line is written to the log under [TAG] as the run's evidence. Each finished scenario
 * is also saved as a backup and restored into a new installation, where its last
 * check must find the same figures.
 */
@NightlyOnly
@RunWith(AndroidJUnit4::class)
class TemporalScenarioCorpusTest {
    private var sandbox: SandboxGraph? = null

    @After
    fun end() {
        sandbox?.erase()
    }

    private fun graph(scenario: Scenario): SandboxGraph {
        sandbox?.erase()
        val session = SandboxSession(
            active = true,
            date = scenario.start,
            zone = scenario.zone,
            scenario = scenario.key
        )
        return SandboxGraph(session).also {
            it.erase()
            sandbox = it
            it.open()
        }
    }

    private val SandboxGraph.runner get() = checkNotNull(tools?.scenarios)

    private fun SandboxGraph.finish() = runBlocking {
        while (runner.step()) Unit
        assertTrue(
            "${runner.state.value.scenario?.key}: ${runner.state.value.problem}\n" +
                runner.state.value.log.lastOrNull()?.result,
            runner.state.value.finished
        )
    }

    /**
     * Everything the scenario wrote, without the random record IDs or audit times. A
     * catch-up batch has no operation of its own: its receipt is compared by kind and
     * revision.
     */
    private fun SandboxGraph.history(): List<String> = rows(
        "SELECT CASE kind WHEN 'schedule.catch_up' THEN 'catch-up' ELSE operation_id END, " +
            "kind, revision FROM operation_receipt ORDER BY revision"
    ) +
        rows(
            "SELECT c.name, e.amount_minor, e.effective_date, e.origin_date, e.origin_slot " +
                "FROM expense e JOIN category c ON c.id = e.category_id " +
                "ORDER BY e.effective_date, e.sequence"
        ) +
        rows(
            "SELECT amount_minor, effective_date, source_key, origin_date, origin_slot " +
                "FROM income ORDER BY effective_date, sequence"
        ) + schedules(withVersions = true) +
        rows("SELECT name, archived, version FROM category ORDER BY name") +
        rows(
            "SELECT c.name, d.effective_month, d.kind, d.limit_minor FROM default_limit d " +
                "JOIN category c ON c.id = d.category_id ORDER BY c.name, d.effective_month"
        ) +
        rows(
            "SELECT c.name, m.month, m.kind, m.limit_minor FROM month_limit m " +
                "JOIN category c ON c.id = m.category_id ORDER BY c.name, m.month"
        ) + goals(withVersions = true) + investments(withVersions = true)

    /**
     * Each investment, its movements in recording order and the rates in the order they
     * were entered, without identifiers or audit times. A backup keeps no versions and no
     * next recording position, so a restore is compared [withVersions] false.
     */
    private fun SandboxGraph.investments(withVersions: Boolean): List<String> = rows(
        "SELECT name, icon_key, currency" +
            (if (withVersions) ", next_sequence, version" else "") +
            " FROM investment ORDER BY name"
    ) +
        rows(
            "SELECT i.name, m.sequence, m.kind, m.amount_minor, m.currency, m.effective_date" +
                (if (withVersions) ", m.version" else "") +
                " FROM investment_movement m " +
                "JOIN investment i ON i.id = m.investment_id ORDER BY i.name, m.sequence"
        ) +
        rows("SELECT rate_hundredths, effective_date, sequence FROM usd_rate ORDER BY sequence")

    /** Each schedule with its checkpoint, and its pauses. */
    private fun SandboxGraph.schedules(withVersions: Boolean): List<String> = rows(
        "SELECT note, kind, amount_minor, rule, rule_first, rule_second, start_date, end_date, " +
            "handled_date, handled_slot" + (if (withVersions) ", version" else "") +
            " FROM schedule ORDER BY sequence"
    ) +
        rows(
            "SELECT s.note, p.from_date, p.until_date FROM schedule_pause p " +
                "JOIN schedule s ON s.id = p.schedule_id ORDER BY s.note, p.from_date"
        )

    /** Each goal, its movements and target changes in recording order, and Recibo's choice. */
    private fun SandboxGraph.goals(withVersions: Boolean): List<String> = rows(
        "SELECT name, icon_key, target_month, completed_on" +
            (if (withVersions) ", version" else "") + " FROM goal ORDER BY name"
    ) +
        rows(
            "SELECT g.name, e.sequence, e.kind, e.amount_minor, e.effective_date " +
                "FROM goal_event e JOIN goal g ON g.id = e.goal_id ORDER BY g.name, e.sequence"
        ) +
        rows("SELECT g.name FROM featured_goal f JOIN goal g ON g.id = f.goal_id")

    /** The lines of every check the run took itself, from step [from] on. */
    private fun lines(scenario: Scenario, graph: SandboxGraph, from: Int): List<String> =
        graph.runner.state.value.log
            .filter { it.index >= from && scenario.steps[it.index] is ScenarioStep.Verify }
            .flatMap { it.result.lines() }

    private fun checks(
        scenario: Scenario,
        graph: SandboxGraph,
        run: String,
        from: Int = 0
    ): List<String> {
        val lines = lines(scenario, graph, from)
        lines.forEach { Log.i(TAG, "${scenario.key} [$run] $it") }
        return lines
    }

    @Test
    fun everyScenarioMeetsItsFiguresAndResumesToTheSameHistory() {
        val report = mutableListOf<String>()
        Scenarios.all.forEach { scenario ->
            // Cut off after the last write before the middle; its checkpoint is lost.
            val cut = scenario.steps.indices
                .last { it < scenario.steps.size / 2 && scenario.steps[it].writes } + 1
            val fresh = graph(scenario)
            fresh.finish()
            val freshChecks = checks(scenario, fresh, "fresh")
            // What each catch-up step found, as the run's evidence; not compared, because
            // a resumed process may have caught up on opening.
            fresh.runner.state.value.log
                .filter { scenario.steps[it.index] == ScenarioStep.CatchUp }
                .filterNot { it.result.endsWith("nada pendiente") }
                .forEach {
                    Log.i(TAG, "${scenario.key} [fresh] paso ${it.index + 1}: ${it.result}")
                }
            val freshAfterCut = lines(scenario, fresh, from = cut)
            val freshHistory = fresh.history()

            val resumed = graph(scenario)
            runBlocking { repeat(cut) { resumed.runner.step() } }
            resumed.store.session = resumed.store.session.copy(nextStep = cut - 1)
            resumed.open()
            resumed.finish()
            // The new process takes the checks after the cut again; earlier ones it lists
            // as taken before, so those are compared from the fresh run alone.
            val resumedChecks = checks(scenario, resumed, "resumed", from = cut)

            assertEquals(scenario.key, freshHistory, resumed.history())
            assertEquals(scenario.key, freshAfterCut, resumedChecks)
            assertTrue(scenario.key, freshChecks.none { it.startsWith("✗") })
            val verifies = scenario.steps.count { it is ScenarioStep.Verify }
            val figures = scenario.steps.filterIsInstance<ScenarioStep.Verify>().sumOf {
                it.figures
            }
            assertEquals(scenario.key, figures, freshChecks.count { it.startsWith("✓") })
            report += "${scenario.key}: ${scenario.steps.size} steps, $verifies checks, " +
                "$figures figures matched, fresh = resumed (cut after step $cut)"
        }
        report.forEach { Log.i(TAG, it) }
        assertEquals(Scenarios.all.size, report.size)
    }

    @Test
    fun everyScenarioRestoredFromItsBackupKeepsItsRowsAndLastFigures() {
        val report = mutableListOf<String>()
        Scenarios.all.forEach { scenario ->
            val last = scenario.steps.indexOfLast { it is ScenarioStep.Verify }
            // Only a final check sees the whole history; earlier ones were taken mid-run. A
            // later step that is meant to be refused changes nothing.
            val checkable = last >= 0 && scenario.steps.drop(last + 1).none {
                it.writes && it.expect == Expect.Saved
            }
            val sandbox = graph(scenario)
            sandbox.finish()
            val expected = sandbox.runner.state.value.log.singleOrNull { it.index == last }?.result
            val before = sandbox.figures()
            val bytes = runBlocking {
                (sandbox.koin.get<BackupExports>().prepare() as Outcome.Success).value.bytes
            }

            // A new installation at the same simulated day, holding nothing yet.
            sandbox.erase()
            sandbox.store.session = sandbox.store.session.copy(
                nextStep = if (checkable) last else scenario.steps.size
            )
            sandbox.open()
            val restores = sandbox.koin.get<Restores>()
            val restored = runBlocking {
                restores.replace((restores.stage(bytes) as Outcome.Success).value)
            }
            assertTrue("${scenario.key}: $restored", restored is RestoreOutcome.Restored)
            assertEquals(scenario.key, before, sandbox.figures())

            if (!checkable) {
                report += "${scenario.key}: backup ${bytes.size} bytes restored; same rows; " +
                    "no check follows its last saved change"
                return@forEach
            }
            // After the restart a restore asks for, the last check runs on restored rows.
            sandbox.open()
            runBlocking { sandbox.runner.step() }
            val again = sandbox.runner.state.value.log.single { it.index == last }.result

            assertEquals(scenario.key, expected, again)
            assertTrue(scenario.key, again.lines().none { it.startsWith("✗") })
            report += "${scenario.key}: backup ${bytes.size} bytes restored; same rows; " +
                "${again.lines().count { it.startsWith("✓") }} final figures match"
        }
        report.forEach { Log.i(TAG, it) }
        assertEquals(Scenarios.all.size, report.size)
    }

    /** What the figures are computed from, by name: no identifiers, versions or receipts. */
    private fun SandboxGraph.figures(): List<String> = rows(
        "SELECT c.name, e.amount_minor, e.effective_date, e.note, e.origin_date, e.origin_slot " +
            "FROM expense e JOIN category c ON c.id = e.category_id " +
            "ORDER BY e.effective_date, e.sequence"
    ) +
        rows(
            "SELECT amount_minor, effective_date, source_key, origin_date, origin_slot " +
                "FROM income ORDER BY effective_date, sequence"
        ) + schedules(withVersions = false) +
        rows("SELECT name, archived FROM category ORDER BY name") +
        rows(
            "SELECT c.name, d.effective_month, d.kind, d.limit_minor FROM default_limit d " +
                "JOIN category c ON c.id = d.category_id ORDER BY c.name, d.effective_month"
        ) +
        rows(
            "SELECT c.name, m.month, m.kind, m.limit_minor FROM month_limit m " +
                "JOIN category c ON c.id = m.category_id ORDER BY c.name, m.month"
        ) + goals(withVersions = false) + investments(withVersions = false)

    private companion object {
        const val TAG = "ScenarioCorpus"
    }
}
