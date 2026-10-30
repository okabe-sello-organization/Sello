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

    /** One category in one month: carry-in (null outside a chain), capacity, spent, remaining. */
    private data class Row(
        val carryIn: Long?,
        val capacity: Long,
        val spent: Long,
        val remaining: Long
    )

    private fun JvmSandbox.row(year: Int, month: Int, name: String): Row {
        val line = month(year, month).category(name)
        val budget = checkNotNull(line.budget) { "$name has no limit in $year-$month" }
        return Row(
            budget.carry?.carryIn?.minorUnits,
            budget.limit.minorUnits,
            line.spent.minorUnits,
            budget.remaining.minorUnits
        )
    }

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
    fun sixMonthsOfEverything() {
        val six = load(Scenarios.SixMonths)

        // October to the 24th: rent 1.200.000, the gym 120.000 on the 15th, the bus on 7,
        // 14 and 21 October, Luna 90.000 on the 14th, Mercado 460.000 + 290.000, Salidas
        // 230.000 and Farmacia 45.000, against 1.500.000 + 900.000 + 250.000 + 300.000 +
        // 150.000 + 200.000 + 200.000 = 3.500.000 plus Mascota's carry-in of 120.000;
        // Salud has no limit and Ocio is archived.
        val october = six.month(2026, 10)
        assertEquals(pesos(2_525_000), october.spent)
        assertEquals(pesos(3_620_000), october.limited().budget.limit)
        assertEquals(pesos(1_095_000), october.limited().budget.remaining)
        // Still due: internet 95.000 and the bus 30.000 on the 28th.
        val reservation = checkNotNull(october.limited().reservation)
        assertEquals(pesos(125_000), reservation.total)
        assertEquals(pesos(970_000), reservation.margin)
        // The quincena of the 15th and 150.000 of passive income; the 30th still expected.
        // Por asignar counts the 3.500.000 of bases only, never the carry.
        val summary = six.summary(2026, 10)
        assertEquals(pesos(2_350_000), summary.income)
        assertEquals(pesos(-175_000), summary.netFlow)
        assertEquals(pesos(-1_150_000), summary.unallocated)
        val book = runBlocking { six.koin.get<ScheduleReads>().book().value() }
        val plan = IncomePlanPolicy.plan(
            summary.income,
            summary.allocated,
            IncomePlanPolicy.expected(book.schedules, YearMonth.of(2026, 10))
        ).value()
        assertEquals(pesos(2_200_000), plan?.expected)
        assertEquals(pesos(4_550_000), plan?.planned)
        assertEquals(pesos(1_050_000), plan?.unassigned)

        // August: Salidas 600.000 that month only, the gym paused, Ocio archived on the 1st
        // but keeping its 150.000 that month (ADR 0001). Rent, internet, four buses, Luna,
        // the phone, Mercado 780.000, Salidas 540.000 and Farmacia 45.000, against bases of
        // 3.950.000 and Mascota's carry-in of −100.000.
        val august = six.month(2026, 8)
        assertEquals(pesos(3_020_000), august.spent)
        assertEquals(pesos(3_850_000), august.limited().budget.limit)
        assertEquals(pesos(830_000), august.limited().budget.remaining)
        assertEquals(pesos(60_000), august.category("Salidas").budget?.remaining)
        // Two quincenas and 650.000 freelance.
        assertEquals(pesos(5_050_000), six.summary(2026, 8).income)
        // September: the duplicated meal deleted and 350.000 corrected to 305.000; bases
        // 3.500.000 and Mascota's carry-in of 10.000.
        val september = six.month(2026, 9)
        assertEquals(pesos(2_820_000), september.spent)
        assertEquals(pesos(3_510_000), september.limited().budget.limit)
        assertEquals(pesos(690_000), september.limited().budget.remaining)

        // Mascota carries from April, base 200.000: Luna on 1 and 29 April, then once a
        // month; the vet's 400.000 on 12 June; the vaccine of 18 July, 50.000, recorded on
        // 24 October and confirmed. By hand, K(m+1) = K(m) + 200.000 − S(m):
        // abr 0 + 200.000 − 180.000 = 20.000; may 20.000 + 200.000 − 90.000 = 130.000;
        // jun 130.000 + 200.000 − 490.000 = −160.000; jul −160.000 + 200.000 − 140.000 =
        // −100.000; ago −100.000 + 200.000 − 90.000 = 10.000; sep 10.000 + 200.000 −
        // 90.000 = 120.000.
        assertEquals(
            listOf(
                Row(0, 200_000, 180_000, 20_000),
                Row(20_000, 220_000, 90_000, 130_000),
                Row(130_000, 330_000, 490_000, -160_000),
                Row(-160_000, 40_000, 140_000, -100_000),
                Row(-100_000, 100_000, 90_000, 10_000),
                Row(10_000, 210_000, 90_000, 120_000),
                Row(120_000, 320_000, 90_000, 230_000)
            ),
            (4..10).map { six.row(2026, it, "Mascota") }
        )
        // Ocio carries from May, base 150.000 and 80.000 of Cine a month: may 70.000, jun
        // 70.000 + 70.000 = 140.000, jul 210.000, dropped when it was archived on 1 August
        // with exactly that confirmed. April, before it was turned on, never counts.
        assertEquals(
            listOf(
                Row(null, 150_000, 80_000, 70_000),
                Row(0, 150_000, 80_000, 70_000),
                Row(70_000, 220_000, 80_000, 140_000),
                Row(140_000, 290_000, 80_000, 210_000),
                Row(null, 150_000, 0, 150_000)
            ),
            (4..8).map { six.row(2026, it, "Ocio") }
        )
        // Resumen's graph, abr–oct. Limit is Σ B + Σ K: bases 3.550.000 abr–jun, 3.650.000
        // jul (Mercado 900.000), 3.950.000 ago (Salidas 600.000), 3.500.000 sep–oct (Ocio
        // archived); carry-in Mascota + Ocio: abr 0, may 20.000 + 0, jun 130.000 + 70.000,
        // jul −160.000 + 140.000, ago −100.000, sep 10.000, oct 120.000. Spent: abr
        // 1.295.000 + 730.000 + 150.000 + 180.000 + 45.000 + 120.000 + 180.000 + 150.000 +
        // 80.000; may 1.295.000 + 750.000 + 120.000 + 220.000 + 120.000 + 90.000 + 150.000
        // + 80.000; jun 1.295.000 + 750.000 + 120.000 + 260.000 + 120.000 + 120.000 +
        // 490.000 + 150.000 + 80.000; jul 1.295.000 + 830.000 + 150.000 + 240.000 +
        // 140.000 + 150.000 + 80.000; ago, sep and oct as above.
        val graph = six.summary(2026, 10).history.associateBy { it.month }
        listOf(
            listOf(4L, 3_550_000L, 0L, 3_550_000L, 2_930_000L),
            listOf(5L, 3_550_000L, 20_000L, 3_570_000L, 2_825_000L),
            listOf(6L, 3_550_000L, 200_000L, 3_750_000L, 3_385_000L),
            listOf(7L, 3_650_000L, -20_000L, 3_630_000L, 2_885_000L),
            listOf(8L, 3_950_000L, -100_000L, 3_850_000L, 3_020_000L),
            listOf(9L, 3_500_000L, 10_000L, 3_510_000L, 2_820_000L),
            listOf(10L, 3_500_000L, 120_000L, 3_620_000L, 2_525_000L)
        ).forEach { (month, base, carryIn, limit, spent) ->
            val point = graph.getValue(YearMonth.of(2026, month.toInt()))
            val carry = checkNotNull(point.carry) { "carry $month" }
            assertEquals("base $month", pesos(base), carry.base)
            assertEquals("carry-in $month", pesos(carryIn), carry.carryIn)
            assertEquals("limit $month", pesos(limit), point.limit)
            assertEquals("spent $month", pesos(spent), point.spent)
        }
        assertEquals(
            listOf("Ocio|1"),
            six.rows("SELECT name, archived FROM category WHERE archived = 1")
        )
        // Paused on 1 July, resumed on 1 September: no gym in July or August.
        assertEquals(
            listOf("2026-04-15", "2026-05-15", "2026-06-15", "2026-09-15", "2026-10-15"),
            six.rows("SELECT effective_date FROM expense WHERE note = 'Gimnasio' ORDER BY 1")
        )
        // Six instalments, the last on its end date.
        assertEquals(
            listOf(
                "2026-04-20",
                "2026-05-20",
                "2026-06-20",
                "2026-07-20",
                "2026-08-20",
                "2026-09-20"
            ),
            six.rows(
                "SELECT effective_date FROM expense WHERE note = 'Cuota del celular' ORDER BY 1"
            )
        )

        // Goals: 6 × 300.000 − 400.000; 7 × 500.000 on Recibo; 4 × 150.000 lograda.
        val goals: GoalBoard = runBlocking { six.koin.get<GoalReads>().board().value() }
        fun goal(name: String) = goals.goals.single { it.history.goal.name.value == name }
        assertEquals(pesos(1_400_000), goal("Viaje a Cartagena").progress.saved)
        assertEquals(pesos(3_500_000), goal("Fondo de emergencia").progress.saved)
        assertEquals(goal("Fondo de emergencia").history.goal.id, goals.featured)
        assertEquals(pesos(600_000), goal("Regalo de Navidad").progress.saved)
        assertTrue(goal("Regalo de Navidad").progress.completed)

        // Investments: CDT 5.180.000 on 5.000.000, +3,6 %; Fondo 2.820.000 + 300.000
        // withdrawn on 3.000.000, +4,0 %; Acciones USD 765,00 + 4,20 on USD 700,00.
        val board: InvestmentBoard =
            runBlocking { six.koin.get<InvestmentReads>().board().value() }
        fun figures(name: String) =
            board.investments.single { it.history.investment.name.value == name }.figures
        val cdt = figures("CDT Banco")
        assertEquals(pesos(5_180_000), cdt.value?.amount)
        assertEquals(pesos(180_000), cdt.gain)
        assertEquals(GainDirection.Gain to 36L, cdt.`return`?.direction to cdt.`return`?.tenths)
        val fondo = figures("Fondo de inversión")
        assertEquals(pesos(2_820_000), fondo.value?.amount)
        assertEquals(LocalDate.of(2026, 10, 12), fondo.value?.valuedOn)
        assertEquals(pesos(120_000), fondo.gain)
        assertEquals(GainDirection.Gain to 40L, fondo.`return`?.direction to fondo.`return`?.tenths)
        val acciones = figures("Acciones EE. UU.")
        assertEquals(76_500L, acciones.value?.amount?.minorUnits)
        assertEquals(6_920L, acciones.gain?.minorUnits)
        val rate = board.overview.value().rate
        assertEquals(405_050L, rate?.rate?.hundredths)
        assertEquals(LocalDate.of(2026, 10, 24), rate?.date)
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
}
