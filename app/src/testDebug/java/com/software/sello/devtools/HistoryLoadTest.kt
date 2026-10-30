package com.software.sello.devtools

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.policy.IncomePlanPolicy
import com.software.sello.domain.port.BackupExports
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleReads
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each history the tools offer, loaded on real Room through the app's own commands and
 * catch-up, ends on its day with the figures written in docs/testing/sandbox-scenarios.md.
 * Every expected amount was worked out by hand from the history's steps or copied from
 * the approved example it names; none is computed by the code being checked. A load cut
 * short and carried on, and a backup restored, keep the same rows.
 */
@RunWith(AndroidJUnit4::class)
class HistoryLoadTest {
    private var sandbox: JvmSandbox? = null

    @After
    fun end() {
        sandbox?.erase()
    }

    private fun load(scenario: Scenario): JvmSandbox = JvmSandbox(scenario).also {
        sandbox = it
        it.erase()
        it.open()
        it.finish()
        assertEquals(
            scenario.key,
            scenario.today,
            it.koin.get<FinancialClock>().today.value.date
        )
    }

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun JvmSandbox.month(year: Int, month: Int): MonthlySnapshot =
        runBlocking { koin.get<MonthlySnapshots>().read(YearMonth.of(year, month)).value() }

    private fun JvmSandbox.summary(year: Int, month: Int): ResumenSnapshot =
        runBlocking { koin.get<ResumenSnapshots>().read(YearMonth.of(year, month)).value() }

    private fun MonthlySnapshot.limited() = overall as OverallBudget.Limited

    private fun MonthlySnapshot.category(name: String) =
        categories.single { it.category.name.value == name }

    private fun pesos(amount: Long) = Money.cop(amount)

    @Test
    fun aNormalYear() {
        val year = load(Scenarios.NormalYear)

        // October to the 24th: 410.000 + 330.000 + 120.000 + 180.000 + 60.000 against
        // Mercado 900.000 (from March), Transporte 200.000 and Salidas 300.000; Salud has
        // no limit and Ocio, archived in June, no budget.
        val october = year.month(2026, 10)
        assertEquals(pesos(1_100_000), october.spent)
        assertEquals(pesos(1_400_000), october.limited().budget.limit)
        assertEquals(pesos(300_000), october.limited().budget.remaining)
        val summary = year.summary(2026, 10)
        assertEquals(pesos(3_500_000), summary.income)
        assertEquals(pesos(2_400_000), summary.netFlow)
        assertEquals(pesos(2_100_000), summary.unallocated)
        // 31 December in December, 1 January in January.
        val december = year.month(2025, 12)
        assertEquals(pesos(1_180_000), december.spent)
        assertEquals(pesos(1_450_000), december.limited().budget.limit)
        assertEquals(pesos(270_000), december.limited().budget.remaining)
        assertEquals(pesos(970_000), year.month(2026, 1).spent)
        // September: 450.000 typed, corrected to 45.000; the repeated bus deleted.
        assertEquals(pesos(1_185_000), year.month(2026, 9).spent)
        assertEquals(pesos(215_000), year.month(2026, 9).limited().budget.remaining)
        assertEquals(
            listOf("Ocio|1"),
            year.rows("SELECT name, archived FROM category WHERE archived = 1")
        )
    }

    @Test
    fun aTightMonth() {
        val tight = load(Scenarios.TightMonth)

        // 1.200.000 + 690.000 + 180.000 + 120.000 + 250.000 against 2.150.000.
        val october = tight.month(2026, 10)
        assertEquals(pesos(2_440_000), october.spent)
        assertEquals(pesos(2_150_000), october.limited().budget.limit)
        assertEquals(pesos(-290_000), october.limited().budget.remaining)
        assertEquals(pesos(-90_000), october.category("Mercado").budget?.remaining)
        assertEquals(pesos(-30_000), october.category("Transporte").budget?.remaining)
        val summary = tight.summary(2026, 10)
        assertEquals(pesos(2_000_000), summary.income)
        assertEquals(pesos(-440_000), summary.netFlow)
        // Not clamped to 0 (I02).
        assertEquals(pesos(-150_000), summary.unallocated)
        // September stayed within its 2.150.000.
        assertEquals(pesos(2_010_000), tight.month(2026, 9).spent)
        assertEquals(pesos(140_000), tight.month(2026, 9).limited().budget.remaining)
        assertEquals(pesos(150_000), tight.summary(2026, 9).unallocated)
    }

    @Test
    fun aGoalSaver() {
        val saver = load(Scenarios.GoalSaver)
        val board: GoalBoard = runBlocking { saver.koin.get<GoalReads>().board().value() }
        fun goal(name: String) = board.goals.single { it.history.goal.name.value == name }
        fun stamps(name: String) = goal(name).progress.places.map {
            when (it.state) {
                PlaceState.Empty -> "vacío"
                is PlaceState.Earned -> "ganado"
                is PlaceState.Kept -> "guardado"
                PlaceState.Ready -> "esperando"
            }
        }

        // 5 × 400.000 + 300.000 = 2.300.000 reached 75 %; less 800.000 is 1.500.000, 50 %.
        assertEquals(pesos(1_500_000), goal("Viaje a la costa").progress.saved)
        assertEquals(50L, goal("Viaje a la costa").progress.percentage)
        assertEquals(
            listOf("ganado", "ganado", "guardado", "vacío"),
            stamps("Viaje a la costa")
        )
        // 4 × 300.000 = 1.200.000, marked lograda.
        assertEquals(pesos(1_200_000), goal("Celular nuevo").progress.saved)
        assertTrue(goal("Celular nuevo").progress.completed)
        assertEquals(List(4) { "ganado" }, stamps("Celular nuevo"))
        // 1.000.000 + 3 × 500.000 = 2.500.000; the target lowered to 5.000.000 earns 50 %.
        val emergency = goal("Fondo de emergencia")
        assertEquals(pesos(2_500_000), emergency.progress.saved)
        assertEquals(50L, emergency.progress.percentage)
        assertEquals(listOf("ganado", "ganado", "vacío", "vacío"), stamps("Fondo de emergencia"))
        assertEquals(emergency.history.goal.id, board.featured)
        // MT06: the aportes leave the budget alone. 900.000 − 300.000 − 80.000.
        val october = saver.month(2026, 10)
        assertEquals(pesos(380_000), october.spent)
        assertEquals(pesos(520_000), october.limited().budget.remaining)
        assertEquals(pesos(2_100_000), saver.summary(2026, 10).unallocated)
    }

    @Test
    fun anInvestorWithDollars() {
        val investor = load(Scenarios.Investor)
        val board: InvestmentBoard =
            runBlocking { investor.koin.get<InvestmentReads>().board().value() }
        fun figures(name: String) =
            board.investments.single { it.history.investment.name.value == name }.figures

        // IN28: IN06 plus Acciones EE. UU. at 4.150,25 of 20 October.
        val overview = board.overview.value()
        val total = checkNotNull(overview.total)
        assertEquals(pesos(42_397_838), total.value)
        assertEquals(pesos(41_150_250), total.contributed)
        assertEquals(pesos(500_000), total.withdrawn)
        assertEquals(pesos(528_000), total.dividends)
        assertEquals(pesos(2_275_588), total.gain)
        assertEquals(GainDirection.Gain to 55L, total.`return`.direction to total.`return`.tenths)
        assertEquals(415_025L, overview.rate?.rate?.hundredths)
        assertEquals(LocalDate.of(2026, 10, 20), overview.rate?.date)
        // IN04: Fondo 5.242.000 of 14 October, −258.000, −4,3 %.
        val fondo = figures("Fondo de inversión")
        assertEquals(pesos(5_242_000), fondo.value?.amount)
        assertEquals(LocalDate.of(2026, 10, 14), fondo.value?.valuedOn)
        assertEquals(pesos(-258_000), fondo.gain)
        assertEquals(GainDirection.Loss to 43L, fondo.`return`?.direction to fondo.`return`?.tenths)
        // IN27: USD 1.050,50, +USD 50,50, +5,1 %.
        val acciones = figures("Acciones EE. UU.")
        assertEquals(105_050L, acciones.value?.amount?.minorUnits)
        assertEquals(5_050L, acciones.gain?.minorUnits)
        assertEquals(
            GainDirection.Gain to 51L,
            acciones.`return`?.direction to acciones.`return`?.tenths
        )
        // The budget beside them: 900.000 − 350.000.
        assertEquals(pesos(550_000), investor.month(2026, 10).limited().budget.remaining)
    }

    @Test
    fun fixedPaymentsAndTwoWeeksAway() {
        val fixed = load(Scenarios.FixedPayments)

        // Rent 1.200.000, the bus on 7, 14 and 21 October and Mercado 360.000.
        val october = fixed.month(2026, 10)
        assertEquals(pesos(1_635_000), october.spent)
        assertEquals(pesos(3_600_000), october.limited().budget.limit)
        assertEquals(pesos(1_965_000), october.limited().budget.remaining)
        // Still due this month: internet on the 28th, the bus on the 28th and the gym on
        // the 31st; the paused classes are not.
        val reservation = checkNotNull(october.limited().reservation)
        assertEquals(pesos(235_000), reservation.total)
        assertEquals(pesos(1_730_000), reservation.margin)
        // September: rent, five buses, internet, the gym on the 30th and Mercado 750.000.
        assertEquals(pesos(2_285_000), fixed.month(2026, 9).spent)
        assertEquals(
            listOf("2026-07-31", "2026-08-31", "2026-09-30"),
            fixed.rows("SELECT effective_date FROM expense WHERE note = 'Gimnasio' ORDER BY 1")
        )
        assertEquals(
            listOf("2026-07-10", "2026-08-10"),
            fixed.rows(
                "SELECT effective_date FROM expense WHERE note = 'Clases de inglés' ORDER BY 1"
            )
        )
        assertEquals(
            listOf("2026-09-01|null"),
            fixed.rows("SELECT from_date, until_date FROM schedule_pause")
        )
        // Recorded on coming back: the bus of 14 and 21 October and the salary of the 15th.
        assertEquals(
            listOf("2026-10-14", "2026-10-21"),
            fixed.rows(
                "SELECT effective_date FROM expense WHERE note = 'Bus' AND " +
                    "effective_date > '2026-10-09' ORDER BY 1"
            )
        )
        // Plan del mes: 2.000.000 recorded, 2.000.000 expected on the 30th, budgets 3.600.000.
        val summary = fixed.summary(2026, 10)
        assertEquals(pesos(2_000_000), summary.income)
        assertEquals(pesos(-1_600_000), summary.unallocated)
        val book = runBlocking { fixed.koin.get<ScheduleReads>().book().value() }
        val plan = IncomePlanPolicy.plan(
            summary.income,
            summary.allocated,
            IncomePlanPolicy.expected(book.schedules, YearMonth.of(2026, 10))
        ).value()
        assertEquals(pesos(2_000_000), plan?.expected)
        assertEquals(pesos(4_000_000), plan?.planned)
        assertEquals(pesos(400_000), plan?.unassigned)
    }

    @Test
    fun aLoadCutShortAfterACommitWhoseCheckpointWasLostEndsTheSame() {
        Scenarios.all.forEach { scenario ->
            val fresh = load(scenario)
            val expected = fresh.history()
            fresh.erase()

            // Cut after the last write before the middle; its checkpoint is lost.
            val cut = scenario.steps.indices
                .last { it < scenario.steps.size / 2 && scenario.steps[it].writes } + 1
            val resumed = JvmSandbox(scenario).also { sandbox = it }
            resumed.open()
            runBlocking { repeat(cut) { checkNotNull(resumed.runner).step() } }
            resumed.store.session = resumed.store.session.copy(nextStep = cut - 1)
            resumed.open()
            resumed.finish()

            assertEquals(scenario.key, expected, resumed.history())
            assertNull(resumed.runner?.state?.value?.problem)
            resumed.erase()
        }
    }

    @Test
    fun everyHistorySavedAsABackupAndRestoredKeepsItsRows() {
        Scenarios.all.forEach { scenario ->
            val original = load(scenario)
            val before = original.restorable()
            val bytes = runBlocking {
                original.koin.get<BackupExports>().prepare().value().bytes
            }

            // A new installation at the same simulated day, holding nothing yet.
            original.erase()
            original.store.session = original.store.session.copy(nextStep = scenario.steps.size)
            original.open()
            val restores = original.koin.get<Restores>()
            val restored = runBlocking { restores.replace(restores.stage(bytes).value()) }

            assertTrue("${scenario.key}: $restored", restored is RestoreOutcome.Restored)
            assertEquals(scenario.key, before, original.restorable())
            original.erase()
        }
    }

    /**
     * What a backup keeps, by name: no identifiers, versions, receipts or recording
     * positions.
     */
    private fun JvmSandbox.restorable(): List<String> = rows(
        "SELECT c.name, e.amount_minor, e.effective_date, e.note, e.origin_date, " +
            "e.origin_slot FROM expense e JOIN category c ON c.id = e.category_id " +
            "ORDER BY e.effective_date, e.sequence"
    ) +
        rows(
            "SELECT amount_minor, effective_date, source_key, origin_date, origin_slot " +
                "FROM income ORDER BY effective_date, sequence"
        ) +
        rows(
            "SELECT note, kind, amount_minor, rule, start_date, handled_date, handled_slot " +
                "FROM schedule ORDER BY sequence"
        ) +
        rows(
            "SELECT s.note, p.from_date, p.until_date FROM schedule_pause p " +
                "JOIN schedule s ON s.id = p.schedule_id ORDER BY s.note, p.from_date"
        ) +
        rows("SELECT name, archived FROM category ORDER BY name") +
        rows(
            "SELECT c.name, d.effective_month, d.kind, d.limit_minor FROM default_limit d " +
                "JOIN category c ON c.id = d.category_id ORDER BY c.name, d.effective_month"
        ) +
        rows("SELECT name, target_month, completed_on FROM goal ORDER BY name") +
        rows(
            "SELECT g.name, e.sequence, e.kind, e.amount_minor, e.effective_date " +
                "FROM goal_event e JOIN goal g ON g.id = e.goal_id ORDER BY g.name, e.sequence"
        ) +
        rows("SELECT g.name FROM featured_goal f JOIN goal g ON g.id = f.goal_id") +
        rows("SELECT name, icon_key, currency FROM investment ORDER BY name") +
        rows(
            "SELECT i.name, m.sequence, m.kind, m.amount_minor, m.currency, m.effective_date " +
                "FROM investment_movement m JOIN investment i ON i.id = m.investment_id " +
                "ORDER BY i.name, m.sequence"
        ) +
        rows("SELECT rate_hundredths, effective_date, sequence FROM usd_rate ORDER BY sequence")

    /**
     * Everything a history wrote, without the random record IDs or audit times. A
     * catch-up batch has no operation of its own: its receipt is compared by kind and
     * revision.
     */
    private fun JvmSandbox.history(): List<String> = rows(
        "SELECT CASE kind WHEN 'schedule.catch_up' THEN 'catch-up' ELSE operation_id END, " +
            "kind, revision FROM operation_receipt ORDER BY revision"
    ) +
        rows(
            "SELECT c.name, e.amount_minor, e.effective_date, e.note, e.origin_date, " +
                "e.origin_slot FROM expense e JOIN category c ON c.id = e.category_id " +
                "ORDER BY e.effective_date, e.sequence"
        ) +
        rows(
            "SELECT amount_minor, effective_date, source_key, origin_date, origin_slot " +
                "FROM income ORDER BY effective_date, sequence"
        ) +
        rows(
            "SELECT note, kind, amount_minor, rule, start_date, handled_date, handled_slot, " +
                "version FROM schedule ORDER BY sequence"
        ) +
        rows("SELECT name, archived, version FROM category ORDER BY name") +
        rows(
            "SELECT c.name, d.effective_month, d.kind, d.limit_minor FROM default_limit d " +
                "JOIN category c ON c.id = d.category_id ORDER BY c.name, d.effective_month"
        ) +
        rows("SELECT name, target_month, completed_on, version FROM goal ORDER BY name") +
        rows(
            "SELECT g.name, e.sequence, e.kind, e.amount_minor, e.effective_date " +
                "FROM goal_event e JOIN goal g ON g.id = e.goal_id ORDER BY g.name, e.sequence"
        ) +
        rows("SELECT g.name FROM featured_goal f JOIN goal g ON g.id = f.goal_id") +
        rows("SELECT name, currency, next_sequence, version FROM investment ORDER BY name") +
        rows(
            "SELECT i.name, m.sequence, m.kind, m.amount_minor, m.effective_date " +
                "FROM investment_movement m JOIN investment i ON i.id = m.investment_id " +
                "ORDER BY i.name, m.sequence"
        ) +
        rows("SELECT rate_hundredths, effective_date, sequence FROM usd_rate ORDER BY sequence")
}
