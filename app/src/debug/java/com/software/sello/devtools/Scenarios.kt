package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.Aporte
import com.software.sello.devtools.ScenarioStep.Archive
import com.software.sello.devtools.ScenarioStep.CatchUp
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.ChooseForRecibo
import com.software.sello.devtools.ScenarioStep.DeleteExpense
import com.software.sello.devtools.ScenarioStep.EditExpense
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.Goal
import com.software.sello.devtools.ScenarioStep.GoalTarget
import com.software.sello.devtools.ScenarioStep.Income
import com.software.sello.devtools.ScenarioStep.Investment
import com.software.sello.devtools.ScenarioStep.MarkLograda
import com.software.sello.devtools.ScenarioStep.Movement
import com.software.sello.devtools.ScenarioStep.PauseSchedule
import com.software.sello.devtools.ScenarioStep.ResumeSchedule
import com.software.sello.devtools.ScenarioStep.Retiro
import com.software.sello.devtools.ScenarioStep.Schedule
import com.software.sello.devtools.ScenarioStep.SetDefaultLimit
import com.software.sello.devtools.ScenarioStep.SetMonthLimit
import com.software.sello.devtools.ScenarioStep.SetRate
import com.software.sello.domain.model.Currency
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private val Bogota = ZoneId.of("America/Bogota")

private fun day(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

/** One month of [Scenarios.NormalYear]: what was spent, in pesos, and on which day. */
private class Spending(
    val month: YearMonth,
    val mercado: Pair<Long, Long>,
    val salidas: Long,
    val salidasOn: Int = 14,
    val salud: Long? = null,
    val ocio: Boolean = false
)

/**
 * The histories the tools offer, all synthetic and each loaded up to its last day. They
 * are described, with the figures their tests check, in docs/testing/sandbox-scenarios.md.
 */
object Scenarios {
    /**
     * Twelve months, November 2025 to 24 October 2026: a salary each month, four limited
     * categories and an unlimited one, December 31 and January 1, Mercado raised from
     * March, Ocio archived in June, and a September expense corrected and another deleted.
     */
    val NormalYear: Scenario = run {
        val months = listOf(
            Spending(YearMonth.of(2025, 11), 420_000L to 300_000L, 200_000, ocio = true),
            Spending(
                YearMonth.of(2025, 12),
                450_000L to 380_000L,
                150_000,
                salidasOn = 31,
                ocio = true
            ),
            Spending(
                YearMonth.of(2026, 1),
                380_000L to 290_000L,
                40_000,
                salidasOn = 1,
                salud = 60_000,
                ocio = true
            ),
            Spending(YearMonth.of(2026, 2), 400_000L to 310_000L, 220_000, ocio = true),
            Spending(YearMonth.of(2026, 3), 410_000L to 350_000L, 260_000, ocio = true),
            Spending(
                YearMonth.of(2026, 4),
                430_000L to 330_000L,
                190_000,
                salud = 60_000,
                ocio = true
            ),
            Spending(YearMonth.of(2026, 5), 390_000L to 360_000L, 240_000, ocio = true),
            Spending(YearMonth.of(2026, 6), 440_000L to 340_000L, 210_000),
            Spending(YearMonth.of(2026, 7), 400_000L to 320_000L, 230_000, salud = 60_000),
            Spending(YearMonth.of(2026, 8), 420_000L to 350_000L, 280_000),
            Spending(YearMonth.of(2026, 9), 450_000L to 370_000L, 200_000),
            Spending(YearMonth.of(2026, 10), 410_000L to 330_000L, 180_000, salud = 60_000)
        )
        val today = day(2026, 10, 24)
        val steps = mutableListOf<ScenarioStep>(
            Category("mercado", "Mercado", "shopping_cart", 800_000),
            Category("transporte", "Transporte", "directions_bus", 200_000),
            Category("salidas", "Salidas", "restaurant", 300_000),
            Category("salud", "Salud", "medical_services", null),
            Category("ocio", "Ocio", "theaters", 150_000)
        )
        months.forEachIndexed { place, spent ->
            val month = spent.month
            if (place > 0) steps += AdvanceTo(month.atDay(1))
            steps += Income(3_500_000, "salary")
            if (month == YearMonth.of(2026, 3)) steps += SetDefaultLimit("mercado", 900_000, 1)
            if (month == YearMonth.of(2026, 6)) steps += Archive("ocio", 1)
            steps += AdvanceTo(if (month == YearMonth.from(today)) today else month.atEndOfMonth())
            steps += Expense("mercado", spent.mercado.first, "Mercado", month.atDay(3))
            steps += Expense("transporte", 120_000, "Recarga del bus", month.atDay(10))
            steps += Expense("salidas", spent.salidas, "Comida fuera", month.atDay(spent.salidasOn))
            steps += Expense("mercado", spent.mercado.second, "Mercado", month.atDay(17))
            spent.salud?.let { steps += Expense("salud", it, "Farmacia", month.atDay(20)) }
            if (spent.ocio) steps += Expense("ocio", 80_000, "Cine", month.atDay(22))
            if (month == YearMonth.of(2026, 9)) {
                // 45.000 typed as 450.000, then corrected; the bus entered twice, then deleted.
                steps += Expense("mercado", 450_000, "Mercado pequeño", month.atDay(25))
                steps += EditExpense(steps.lastIndex, 45_000, version = 1)
                steps += Expense("transporte", 120_000, "Recarga del bus", month.atDay(10))
                steps += DeleteExpense(steps.lastIndex, version = 1)
            }
        }
        Scenario(
            id = "un-ano",
            version = 1,
            seed = 1,
            zone = Bogota,
            start = day(2025, 11, 1),
            today = today,
            title = "Un año normal",
            summary = "Doce meses con salario, cinco categorías (una sin límite, una archivada " +
                "en junio), 31 dic y 1 ene, y una corrección en septiembre. Resumen con un año.",
            steps = steps
        )
    }

    /**
     * September within the budget, then an October whose income is below the budget and
     * whose spending passes it: Mercado and Transporte over their limits, an unplanned
     * expense in an unlimited category. It ends on 31 October, the month's last day.
     */
    val TightMonth = Scenario(
        id = "mes-apretado",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2026, 9, 1),
        today = day(2026, 10, 31),
        title = "Un mes apretado",
        summary = "Octubre gasta 2.440.000 de 2.150.000 con ingresos de 2.000.000: Excedido, " +
            "Mercado y Transporte pasados. Termina el 31 oct; +1 día abre noviembre.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 1_200_000),
            Category("mercado", "Mercado", "shopping_cart", 600_000),
            Category("transporte", "Transporte", "directions_bus", 150_000),
            Category("salidas", "Salidas", "restaurant", 200_000),
            Category("imprevistos", "Imprevistos", "medical_services", null),
            Income(2_300_000, "salary"),
            AdvanceTo(day(2026, 9, 30)),
            Expense("hogar", 1_200_000, "Arriendo", day(2026, 9, 5)),
            Expense("mercado", 520_000, "Mercado del mes", day(2026, 9, 12)),
            Expense("transporte", 140_000, "Recargas", day(2026, 9, 15)),
            Expense("salidas", 150_000, "Cumpleaños", day(2026, 9, 19)),
            AdvanceTo(day(2026, 10, 1)),
            Income(2_000_000, "salary"),
            AdvanceTo(day(2026, 10, 31)),
            Expense("hogar", 1_200_000, "Arriendo", day(2026, 10, 5)),
            Expense("mercado", 380_000, "Mercado", day(2026, 10, 6)),
            Expense("transporte", 95_000, "Recargas", day(2026, 10, 9)),
            Expense("salidas", 120_000, "Almuerzo", day(2026, 10, 17)),
            Expense("mercado", 310_000, "Mercado", day(2026, 10, 20)),
            Expense("transporte", 85_000, "Taxis", day(2026, 10, 23)),
            Expense("imprevistos", 250_000, "Arreglo del carro", day(2026, 10, 27))
        )
    )

    /**
     * Three goals from April to 24 October: Viaje a la costa reaches 75 % and falls back
     * to 50 % after a retiro, Celular nuevo is marked lograda, and Fondo de emergencia,
     * shown on Recibo, earns its 50 % when its target is lowered. A small budget beside
     * them shows that aportes leave it alone.
     */
    val GoalSaver = Scenario(
        id = "metas",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2026, 4, 1),
        today = day(2026, 10, 24),
        title = "Ahorro para metas",
        summary = "Viaje 1.500.000 de 3.000.000 con el 75 % guardado; Celular lograda; Fondo " +
            "de emergencia 2.500.000 de 5.000.000 en Recibo. Presupuesto aparte.",
        steps = listOf(
            Category("mercado", "Mercado", "shopping_cart", 700_000),
            Category("transporte", "Transporte", "directions_bus", 200_000),
            Goal("viaje", "Viaje a la costa", "flight", 3_000_000),
            Goal("celular", "Celular nuevo", "phone_iphone", 1_200_000),
            Goal("emergencia", "Fondo de emergencia", "home", 6_000_000),
            Income(3_000_000, "salary"),
            Expense("mercado", 450_000, "Mercado"),
            Aporte("viaje", 400_000, 1),
            Aporte("emergencia", 1_000_000, 1),
            AdvanceTo(day(2026, 5, 1)),
            Income(3_000_000, "salary"),
            Expense("mercado", 450_000, "Mercado"),
            Aporte("viaje", 400_000, 2),
            Aporte("celular", 300_000, 1),
            AdvanceTo(day(2026, 6, 1)),
            Income(3_000_000, "salary"),
            Expense("mercado", 450_000, "Mercado"),
            Aporte("viaje", 400_000, 3),
            Aporte("celular", 300_000, 2),
            Aporte("emergencia", 500_000, 2),
            AdvanceTo(day(2026, 7, 1)),
            Income(3_000_000, "salary"),
            Expense("mercado", 450_000, "Mercado"),
            Aporte("viaje", 400_000, 4),
            Aporte("celular", 300_000, 3),
            AdvanceTo(day(2026, 8, 1)),
            Income(3_000_000, "salary"),
            Expense("mercado", 450_000, "Mercado"),
            Aporte("viaje", 400_000, 5),
            Aporte("celular", 300_000, 4),
            Aporte("emergencia", 500_000, 3),
            AdvanceTo(day(2026, 8, 2)),
            MarkLograda("celular", 5),
            AdvanceTo(day(2026, 9, 1)),
            Income(3_000_000, "salary"),
            Expense("mercado", 450_000, "Mercado"),
            Aporte("viaje", 300_000, 6),
            AdvanceTo(day(2026, 10, 1)),
            Income(3_000_000, "salary"),
            Aporte("emergencia", 500_000, 4),
            AdvanceTo(day(2026, 10, 2)),
            GoalTarget("emergencia", 5_000_000, 5),
            ChooseForRecibo("emergencia"),
            AdvanceTo(day(2026, 10, 15)),
            Expense("mercado", 300_000, "Mercado", day(2026, 10, 10)),
            Expense("transporte", 80_000, "Recargas", day(2026, 10, 12)),
            Retiro("viaje", 800_000, 7),
            AdvanceTo(day(2026, 10, 24))
        )
    )

    /**
     * The four investments of the v2 page from May 2025, each movement on its own day
     * (IN02–IN06), then Acciones EE. UU. in dollars at 4.150,25 on 20 October (IN27,
     * IN28), with a small budget beside them.
     */
    val Investor = Scenario(
        id = "inversiones",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2025, 5, 3),
        today = day(2026, 10, 24),
        title = "Inversiones con dólares",
        summary = "Fondo, CDT, Parqueadero y Ecopetrol desde mayo de 2025, y Acciones EE. UU. " +
            "en USD: total 42.397.838, +5,5 %, dólar a 4.150,25 del 20 oct.",
        steps = listOf(
            Investment("fondo", "Fondo de inversión", "insights", Currency.COP, 3_000_000),
            AdvanceTo(day(2025, 10, 1)),
            Investment("cdt", "CDT Banco", "account_balance", Currency.COP, 10_000_000),
            AdvanceTo(day(2025, 11, 3)),
            Movement("fondo", Move.Aporte, 3_000_000, 1),
            AdvanceTo(day(2026, 1, 15)),
            Investment("parqueadero", "Parqueadero", "home", Currency.COP, 18_000_000),
            AdvanceTo(day(2026, 2, 10)),
            Investment("ecopetrol", "Ecopetrol", "trending_up", Currency.COP, 1_500_000),
            AdvanceTo(day(2026, 3, 10)),
            Movement("ecopetrol", Move.Aporte, 1_500_000, 1),
            AdvanceTo(day(2026, 4, 1)),
            Movement("parqueadero", Move.Dividendo, 350_000, 1),
            AdvanceTo(day(2026, 4, 15)),
            Movement("ecopetrol", Move.Dividendo, 89_000, 2),
            AdvanceTo(day(2026, 5, 20)),
            Movement("ecopetrol", Move.Valor, 3_100_000, 3),
            AdvanceTo(day(2026, 6, 12)),
            Movement("parqueadero", Move.Valor, 19_000_000, 2),
            AdvanceTo(day(2026, 7, 15)),
            Movement("ecopetrol", Move.Dividendo, 89_000, 4),
            Movement("ecopetrol", Move.Valor, 2_950_000, 5),
            AdvanceTo(day(2026, 8, 3)),
            Movement("fondo", Move.Retiro, 500_000, 2),
            AdvanceTo(day(2026, 8, 20)),
            Movement("ecopetrol", Move.Valor, 3_000_000, 6),
            AdvanceTo(day(2026, 9, 30)),
            Movement("cdt", Move.Valor, 10_746_000, 1),
            AdvanceTo(day(2026, 10, 1)),
            Category("mercado", "Mercado", "shopping_cart", 900_000),
            Income(4_500_000, "salary"),
            AdvanceTo(day(2026, 10, 9)),
            Movement("ecopetrol", Move.Valor, 3_050_000, 7),
            AdvanceTo(day(2026, 10, 14)),
            Movement("fondo", Move.Valor, 5_242_000, 3),
            AdvanceTo(day(2026, 10, 20)),
            Investment(
                "acciones",
                "Acciones EE. UU.",
                "trending_up",
                Currency.USD,
                100_000,
                value = 105_050,
                rate = Rate(415_025)
            ),
            AdvanceTo(day(2026, 10, 24)),
            Expense("mercado", 350_000, "Mercado")
        )
    )

    /**
     * Fixed payments from July: rent on the 5th, internet on the 28th, the gym on the
     * 31st (the 30th in September), a weekly bus, English classes paused in September and
     * a salary on the 15th and 30th. Sello was last opened on 9 October; the history ends
     * after two weeks away, on 24 October, with what is still due this month set aside.
     */
    val FixedPayments = Scenario(
        id = "pagos-fijos",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2026, 7, 1),
        today = day(2026, 10, 24),
        title = "Pagos fijos y dos semanas fuera",
        summary = "Arriendo, internet, gimnasio el 31, bus semanal, clases en pausa y quincena. " +
            "Al volver el 24 oct: 235.000 separados y una quincena esperada.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 2_000_000),
            Category("mercado", "Mercado", "shopping_cart", 900_000),
            Category("transporte", "Transporte", "directions_bus", 300_000),
            Category("gimnasio", "Gimnasio", "fitness_center", 150_000),
            Category("educacion", "Educación", "school", 250_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 1_200_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            Schedule("internet", Scheduled.Payment("hogar", 90_000, "Internet"), Every.Month(28)),
            CatchUp,
            Schedule(
                "gimnasio",
                Scheduled.Payment("gimnasio", 120_000, "Gimnasio"),
                Every.Month(31)
            ),
            CatchUp,
            Schedule("bus", Scheduled.Payment("transporte", 25_000, "Bus"), Every.Weeks(1)),
            CatchUp,
            Schedule(
                "clases",
                Scheduled.Payment("educacion", 200_000, "Clases de inglés"),
                Every.Month(10)
            ),
            CatchUp,
            Schedule(
                "quincena",
                Scheduled.Income("salary", 2_000_000, "Quincena"),
                Every.TwiceAMonth(15, 30)
            ),
            CatchUp,
            AdvanceTo(day(2026, 7, 31)),
            CatchUp,
            Expense("mercado", 380_000, "Mercado", day(2026, 7, 4)),
            Expense("mercado", 290_000, "Mercado", day(2026, 7, 18)),
            AdvanceTo(day(2026, 8, 31)),
            CatchUp,
            Expense("mercado", 400_000, "Mercado", day(2026, 8, 8)),
            Expense("mercado", 310_000, "Mercado", day(2026, 8, 22)),
            AdvanceTo(day(2026, 9, 1)),
            CatchUp,
            PauseSchedule("clases", 1),
            AdvanceTo(day(2026, 9, 30)),
            CatchUp,
            Expense("mercado", 420_000, "Mercado", day(2026, 9, 5)),
            Expense("mercado", 330_000, "Mercado", day(2026, 9, 19)),
            AdvanceTo(day(2026, 10, 9)),
            CatchUp,
            Expense("mercado", 360_000, "Mercado", day(2026, 10, 3)),
            AdvanceTo(day(2026, 10, 24)),
            CatchUp
        )
    )

    /**
     * Six months of everything, 1 April to 24 October 2026: fixed payments with a pause, an
     * end and every 28 days, a twice-monthly salary with extra income, expenses corrected
     * and deleted, a raised limit, a month's own limit, an archived category, three goals
     * (a retiro, one lograda, one on Recibo) and investments in pesos and dollars.
     */
    val SixMonths: Scenario = run {
        val steps = mutableListOf<ScenarioStep>(
            Category("hogar", "Hogar", "home", 1_500_000),
            Category("mercado", "Mercado", "shopping_cart", 800_000),
            Category("transporte", "Transporte", "directions_bus", 250_000),
            Category("salidas", "Salidas", "restaurant", 300_000),
            Category("salud", "Salud", "medical_services", null),
            Category("gimnasio", "Gimnasio", "fitness_center", 150_000),
            Category("mascota", "Mascota", "pets", 200_000),
            Category("tecnologia", "Tecnología", "phone_iphone", 200_000),
            Category("ocio", "Ocio", "theaters", 150_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 1_200_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            Schedule("internet", Scheduled.Payment("hogar", 95_000, "Internet"), Every.Month(28)),
            CatchUp,
            Schedule(
                "gimnasio",
                Scheduled.Payment("gimnasio", 120_000, "Gimnasio"),
                Every.Month(15)
            ),
            CatchUp,
            Schedule("bus", Scheduled.Payment("transporte", 30_000, "Bus"), Every.Weeks(1)),
            CatchUp,
            Schedule(
                "luna",
                Scheduled.Payment("mascota", 90_000, "Comida de Luna"),
                Every.Days(28)
            ),
            CatchUp,
            Schedule(
                "celular",
                Scheduled.Payment("tecnologia", 150_000, "Cuota del celular"),
                Every.Month(20),
                start = day(2026, 4, 20),
                end = day(2026, 9, 20)
            ),
            CatchUp,
            Schedule(
                "quincena",
                Scheduled.Income("salary", 2_200_000, "Quincena"),
                Every.TwiceAMonth(15, 30)
            ),
            CatchUp,
            Goal("viaje", "Viaje a Cartagena", "flight", 4_000_000),
            Goal("emergencia", "Fondo de emergencia", "savings", 6_000_000),
            Goal("regalo", "Regalo de Navidad", "redeem", 600_000),
            Investment("cdt", "CDT Banco", "account_balance", Currency.COP, 5_000_000),
            Investment("fondo", "Fondo de inversión", "insights", Currency.COP, 2_000_000),
            Aporte("viaje", 300_000, 1),
            Aporte("emergencia", 500_000, 1),
            AdvanceTo(day(2026, 4, 30)),
            CatchUp,
            Expense("mercado", 420_000, "Mercado", day(2026, 4, 4)),
            Expense("salidas", 180_000, "Comida fuera", day(2026, 4, 11)),
            Expense("mercado", 310_000, "Mercado", day(2026, 4, 18)),
            Expense("salud", 45_000, "Farmacia", day(2026, 4, 22)),
            Expense("ocio", 80_000, "Cine", day(2026, 4, 25)),
            // May: the dollar investment, a freelance job.
            AdvanceTo(day(2026, 5, 1)),
            CatchUp,
            Aporte("viaje", 300_000, 2),
            Aporte("emergencia", 500_000, 2),
            Aporte("regalo", 150_000, 1),
            Movement("fondo", Move.Aporte, 500_000, 1),
            Investment(
                "acciones",
                "Acciones EE. UU.",
                "trending_up",
                Currency.USD,
                50_000,
                rate = Rate(395_000)
            ),
            AdvanceTo(day(2026, 5, 31)),
            CatchUp,
            Expense("mercado", 430_000, "Mercado", day(2026, 5, 2)),
            Expense("salidas", 220_000, "Comida fuera", day(2026, 5, 9)),
            Expense("mercado", 320_000, "Mercado", day(2026, 5, 16)),
            Income(800_000, "freelance", day(2026, 5, 20)),
            Expense("ocio", 80_000, "Cine", day(2026, 5, 23)),
            // June: a new dollar rate and more dollars.
            AdvanceTo(day(2026, 6, 1)),
            CatchUp,
            Aporte("viaje", 300_000, 3),
            Aporte("emergencia", 500_000, 3),
            Aporte("regalo", 150_000, 2),
            SetRate(Rate(402_500)),
            Movement("acciones", Move.Aporte, 20_000, 1),
            AdvanceTo(day(2026, 6, 30)),
            CatchUp,
            Expense("mercado", 400_000, "Mercado", day(2026, 6, 6)),
            Expense("salud", 120_000, "Odontólogo", day(2026, 6, 10)),
            Expense("salidas", 260_000, "Comida fuera", day(2026, 6, 13)),
            Expense("mercado", 350_000, "Mercado", day(2026, 6, 20)),
            Expense("ocio", 80_000, "Cine", day(2026, 6, 27)),
            // July: the gym paused, Mercado raised, a dividend and a new value.
            AdvanceTo(day(2026, 7, 1)),
            CatchUp,
            PauseSchedule("gimnasio", 1),
            SetDefaultLimit("mercado", 900_000, 1),
            Aporte("viaje", 300_000, 4),
            Aporte("emergencia", 500_000, 4),
            Aporte("regalo", 150_000, 3),
            Movement("fondo", Move.Aporte, 500_000, 2),
            AdvanceTo(day(2026, 7, 15)),
            CatchUp,
            Movement("acciones", Move.Dividendo, 420, 2),
            Movement("fondo", Move.Valor, 3_080_000, 3),
            AdvanceTo(day(2026, 7, 31)),
            CatchUp,
            Expense("mercado", 450_000, "Mercado", day(2026, 7, 4)),
            Expense("salidas", 240_000, "Comida fuera", day(2026, 7, 11)),
            Expense("mercado", 380_000, "Mercado", day(2026, 7, 18)),
            Income(300_000, "transfer", day(2026, 7, 24)),
            Expense("ocio", 80_000, "Cine", day(2026, 7, 25)),
            // August: Ocio archived, a holiday limit for Salidas, a weekend trip.
            AdvanceTo(day(2026, 8, 1)),
            CatchUp,
            Archive("ocio", 1),
            SetMonthLimit("salidas", YearMonth.of(2026, 8), 600_000, 1),
            Aporte("emergencia", 500_000, 5),
            Aporte("regalo", 150_000, 4),
            AdvanceTo(day(2026, 8, 10)),
            CatchUp,
            Retiro("viaje", 400_000, 5),
            SetRate(Rate(410_075)),
            AdvanceTo(day(2026, 8, 31)),
            CatchUp,
            Expense("mercado", 420_000, "Mercado", day(2026, 8, 1)),
            Expense("salidas", 350_000, "Viaje de fin de semana", day(2026, 8, 8)),
            Expense("salidas", 190_000, "Comida fuera", day(2026, 8, 15)),
            Expense("mercado", 360_000, "Mercado", day(2026, 8, 22)),
            Expense("salud", 45_000, "Farmacia", day(2026, 8, 26)),
            Income(650_000, "freelance", day(2026, 8, 28)),
            // September: the gym back, the gift lograda, a fund retiro, two corrections.
            AdvanceTo(day(2026, 9, 1)),
            CatchUp,
            ResumeSchedule("gimnasio", 2),
            CatchUp,
            Aporte("viaje", 300_000, 6),
            Aporte("emergencia", 500_000, 6),
            MarkLograda("regalo", 5),
            Movement("fondo", Move.Retiro, 300_000, 4),
            AdvanceTo(day(2026, 9, 30)),
            CatchUp,
            Expense("mercado", 440_000, "Mercado", day(2026, 9, 5)),
            Expense("salidas", 210_000, "Comida fuera", day(2026, 9, 12)),
            // The same meal entered twice, then deleted; 305.000 typed as 350.000.
            Expense("salidas", 210_000, "Comida fuera", day(2026, 9, 12))
        )
        steps += DeleteExpense(steps.lastIndex, version = 1)
        steps += Expense("mercado", 350_000, "Mercado", day(2026, 9, 19))
        steps += EditExpense(steps.lastIndex, 305_000, version = 1)
        steps += listOf(
            Expense("salud", 60_000, "Farmacia", day(2026, 9, 23)),
            Movement("acciones", Move.Valor, 76_500, 3),
            // October: the emergency fund on Recibo, the CDT and the fund valued.
            AdvanceTo(day(2026, 10, 1)),
            CatchUp,
            Aporte("viaje", 300_000, 7),
            Aporte("emergencia", 500_000, 7),
            ChooseForRecibo("emergencia"),
            Movement("cdt", Move.Valor, 5_180_000, 1),
            Income(150_000, "passive_income"),
            AdvanceTo(day(2026, 10, 12)),
            CatchUp,
            Movement("fondo", Move.Valor, 2_820_000, 5),
            AdvanceTo(day(2026, 10, 24)),
            CatchUp,
            Expense("mercado", 460_000, "Mercado", day(2026, 10, 3)),
            Expense("salidas", 230_000, "Comida fuera", day(2026, 10, 10)),
            Expense("mercado", 290_000, "Mercado", day(2026, 10, 17)),
            Expense("salud", 45_000, "Farmacia", day(2026, 10, 21)),
            SetRate(Rate(405_050))
        )
        Scenario(
            id = "seis-meses",
            version = 1,
            seed = 1,
            zone = Bogota,
            start = day(2026, 4, 1),
            today = day(2026, 10, 24),
            title = "Seis meses con todo",
            summary = "Desde abril: pagos fijos, quincena, metas, inversiones en pesos y " +
                "dólares, límites cambiados, Ocio archivado y correcciones. Octubre " +
                "2.525.000 de 3.500.000.",
            steps = steps
        )
    }

    val all: List<Scenario> =
        listOf(NormalYear, TightMonth, GoalSaver, Investor, FixedPayments, SixMonths)

    fun find(key: String?): Scenario? = all.firstOrNull { it.key == key }
}
