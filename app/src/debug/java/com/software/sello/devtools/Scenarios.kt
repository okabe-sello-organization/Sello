package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.Aporte
import com.software.sello.devtools.ScenarioStep.Archive
import com.software.sello.devtools.ScenarioStep.CatchUp
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.ChangeSchedule
import com.software.sello.devtools.ScenarioStep.ChooseForRecibo
import com.software.sello.devtools.ScenarioStep.DeleteExpense
import com.software.sello.devtools.ScenarioStep.DeleteInvestment
import com.software.sello.devtools.ScenarioStep.DeleteMovement
import com.software.sello.devtools.ScenarioStep.DeleteOccurrence
import com.software.sello.devtools.ScenarioStep.DeleteSchedule
import com.software.sello.devtools.ScenarioStep.EditExpense
import com.software.sello.devtools.ScenarioStep.EditMovement
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.Goal
import com.software.sello.devtools.ScenarioStep.GoalTarget
import com.software.sello.devtools.ScenarioStep.Income
import com.software.sello.devtools.ScenarioStep.Investment
import com.software.sello.devtools.ScenarioStep.MarkLograda
import com.software.sello.devtools.ScenarioStep.Movement
import com.software.sello.devtools.ScenarioStep.PauseSchedule
import com.software.sello.devtools.ScenarioStep.Repeat
import com.software.sello.devtools.ScenarioStep.ResumeSchedule
import com.software.sello.devtools.ScenarioStep.Retiro
import com.software.sello.devtools.ScenarioStep.Schedule
import com.software.sello.devtools.ScenarioStep.SetDefaultLimit
import com.software.sello.devtools.ScenarioStep.SetMonthLimit
import com.software.sello.devtools.ScenarioStep.SetRate
import com.software.sello.devtools.ScenarioStep.Unarchive
import com.software.sello.devtools.ScenarioStep.UndoDelete
import com.software.sello.devtools.ScenarioStep.UndoLograda
import com.software.sello.devtools.ScenarioStep.UndoMovementDelete
import com.software.sello.devtools.ScenarioStep.Verify
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.Occurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.random.Random
import kotlin.time.Duration

private val Bogota = ZoneId.of("America/Bogota")
private val Sep = YearMonth.of(2026, 9)
private val Oct = YearMonth.of(2026, 10)

/** A goal's four places, none earned. */
private val None: List<Stamp> = List(4) { Stamp.Empty }

/** The first [count] places earned, the rest empty. */
private fun earned(count: Int): List<Stamp> =
    List(4) { if (it < count) Stamp.Earned else Stamp.Empty }

/** 25 %, 50 % and 75 % earned; the target reached, waiting for Marcar como lograda. */
private val MetaWaiting: List<Stamp> =
    listOf(Stamp.Earned, Stamp.Earned, Stamp.Earned, Stamp.Waiting)

/** A return of [tenths] of a percent with a gain, a loss or neither (investment rule 6). */
private fun gana(tenths: Long) = Yield(GainDirection.Gain, tenths)

private fun pierde(tenths: Long) = Yield(GainDirection.Loss, tenths)

private val Neutral = Yield(GainDirection.Even, 0)

private fun day(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

/** The month of Fondo's first aporte, the earliest on the v2 page (IN04). */
private val May2025 = YearMonth.of(2025, 5)

/** IN06: the four investments of the v2 page together, values from 12 jun to 14 oct. */
private val PageTotal = PortfolioCheck.Total(
    value = 38_038_000,
    contributed = 37_000_000,
    withdrawn = 500_000,
    dividends = 528_000,
    gain = 2_066_000,
    yield = gana(56),
    since = May2025,
    valuedFrom = day(2026, 6, 12),
    valuedTo = day(2026, 10, 14),
    rate = null,
    source = "IN06"
)

/** The scenarios the tools offer, all synthetic. */
object Scenarios {
    /**
     * G01 in the financial examples: cumulative spending 10.000, 25.000 and 30.000 of
     * a 100.000 limit, so 70.000 remain; income 200.000, so income less spending is
     * 170.000 and 100.000 is not assigned to the budget.
     */
    val G01 = Scenario(
        id = "g01",
        version = 2,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "G01 · Tres gastos y un ingreso",
        summary = "1–3 oct 2026: quedan 70.000 de 100.000; ingresos menos gastos 170.000.",
        steps = listOf(
            Category("comida", "Alimentación", "restaurant", 100_000),
            Expense("comida", 10_000),
            AdvanceTo(LocalDate.of(2026, 10, 2)),
            Expense("comida", 15_000),
            AdvanceTo(LocalDate.of(2026, 10, 3)),
            Expense("comida", 5_000),
            Income(200_000, "salary"),
            Verify(
                listOf(
                    Check(Oct, Figure.Spent, 30_000, "G01"),
                    Check(Oct, Figure.Remaining, 70_000, "G01"),
                    Check(Oct, Figure.NetFlow, 170_000, "G01"),
                    Check(Oct, Figure.Unallocated, 100_000, "G01")
                )
            )
        )
    )

    /**
     * Month and year changes: Comida goes over its 50.000 in October and starts again in
     * November; Ocio has no limit; the year turns from 31 Dec to 1 Jan.
     */
    val MonthAndYearEnds = Scenario(
        id = "fin-de-mes",
        version = 2,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 30),
        title = "Fin de mes y de año",
        summary = "Comida se pasa de su límite en octubre, vuelve a empezar en noviembre; " +
            "Ocio sin límite; 31 dic → 1 ene.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 50_000),
            Category("ocio", "Ocio", "theaters", null),
            Expense("comida", 30_000),
            AdvanceTo(LocalDate.of(2026, 10, 31)),
            Expense("comida", 35_000),
            Expense("ocio", 20_000),
            AdvanceTo(LocalDate.of(2026, 11, 1)),
            Expense("comida", 10_000),
            AdvanceTo(LocalDate.of(2026, 12, 31)),
            Expense("comida", 12_000),
            AdvanceTo(LocalDate.of(2027, 1, 1)),
            Expense("comida", 8_000),
            // Hand-derived from the steps under the B01/B04 rules: unlimited spending counts
            // in the total, and an overrun is shown negative, never clipped.
            Verify(
                listOf(
                    Check(Oct, Figure.Spent, 85_000, "a mano: 30.000 + 35.000 + 20.000"),
                    Check(Oct, Figure.Remaining, -35_000, "a mano, B01/B04: 50.000 − 85.000"),
                    Check(
                        YearMonth.of(2026, 11),
                        Figure.Remaining,
                        40_000,
                        "a mano, H05: 50.000 − 10.000"
                    ),
                    Check(
                        YearMonth.of(2026, 12),
                        Figure.Remaining,
                        38_000,
                        "a mano: 50.000 − 12.000"
                    ),
                    Check(YearMonth.of(2027, 1), Figure.Remaining, 42_000, "a mano: 50.000 − 8.000")
                )
            )
        )
    )

    /**
     * Six months, May to October 2026, of salary on the 1st and spending on the 3rd, 12th
     * and 24th, so Resumen's month-by-month columns grow one month at a time. The
     * amounts come from [Scenario.seed], so they are the same on every replay.
     */
    val SixMonths: Scenario = run {
        val seed = 20_261_001L
        val random = Random(seed)
        val steps = mutableListOf<ScenarioStep>(
            Category("comida", "Comida", "restaurant", 600_000),
            Category("hogar", "Hogar", "home", 400_000)
        )
        var month = YearMonth.of(2026, 5)
        repeat(6) { index ->
            if (index > 0) steps += AdvanceTo(month.atDay(1))
            steps += Income(1_800_000, "salary")
            for (day in listOf(3, 12, 24)) {
                steps += AdvanceTo(month.atDay(day))
                steps += Expense("comida", 1_000L * random.nextInt(40, 180))
                steps += Expense("hogar", 1_000L * random.nextInt(20, 140))
            }
            month = month.plusMonths(1)
        }
        Scenario(
            id = "seis-meses",
            version = 1,
            seed = seed,
            zone = Bogota,
            start = LocalDate.of(2026, 5, 1),
            title = "Seis meses",
            summary = "Mayo a octubre de 2026: un salario y seis gastos al mes.",
            steps = steps
        )
    }

    /**
     * Edges, each through the real commands: a double submit writes once, an edit of an
     * old version is refused, a deletion replaced by a later one can no longer be
     * undone, and an amount past the entry maximum never reaches a command.
     */
    val Edges = Scenario(
        id = "bordes",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 10),
        title = "Bordes",
        summary = "Doble envío, edición de una versión vieja, deshacer reemplazado y un " +
            "monto fuera de rango.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 100_000),
            Expense("comida", 10_000),
            Repeat(of = 1),
            EditExpense(of = 1, pesos = 12_000, version = 0, Expect.Rejected("VersionConflict")),
            EditExpense(of = 1, pesos = 12_000, version = 1),
            Expense("comida", 5_000),
            DeleteExpense(of = 1, version = 2),
            DeleteExpense(of = 5, version = 1),
            UndoDelete(of = 6, Expect.Rejected("UndoExpired")),
            Expense("comida", 1_000_000_000_000L, expect = Expect.InvalidInput)
        )
    )

    /** 28 Feb → 29 Feb → 1 Mar 2028. Not an approved example: the figures are derived by hand. */
    val LeapYear = Scenario(
        id = "bisiesto",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2028, 2, 28),
        title = "Año bisiesto",
        summary = "28 feb → 29 feb → 1 mar 2028; cifras calculadas a mano, no un ejemplo aprobado.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 100_000),
            Expense("comida", 10_000),
            AdvanceTo(LocalDate.of(2028, 2, 29)),
            Expense("comida", 20_000),
            AdvanceTo(LocalDate.of(2028, 3, 1)),
            Expense("comida", 5_000),
            Verify(
                listOf(
                    Check(YearMonth.of(2028, 2), Figure.Spent, 30_000, "a mano: 10.000 + 20.000"),
                    Check(YearMonth.of(2028, 2), Figure.Remaining, 70_000, "a mano"),
                    Check(YearMonth.of(2028, 3), Figure.Spent, 5_000, "a mano"),
                    Check(YearMonth.of(2028, 3), Figure.Remaining, 95_000, "a mano")
                )
            )
        )
    )

    /**
     * H03/H04: a default set in October, November never opened, December's default
     * changed; November still resolves October's 100.000, and a backdated November
     * expense of 10.000 leaves 90.000. History across skipped months, not recurrence.
     */
    val SkippedMonths = Scenario(
        id = "meses-saltados",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 5),
        title = "Meses saltados",
        summary = "H03/H04: noviembre sin abrir conserva 100.000; diciembre usa 120.000; " +
            "un gasto con fecha atrasada en noviembre deja 90.000.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 100_000),
            AdvanceTo(LocalDate.of(2026, 12, 3)),
            SetDefaultLimit("comida", 120_000, version = 1),
            Expense("comida", 10_000, on = LocalDate.of(2026, 11, 15)),
            Verify(
                listOf(
                    Check(YearMonth.of(2026, 11), Figure.Limit, 100_000, "H03"),
                    Check(YearMonth.of(2026, 12), Figure.Limit, 120_000, "H03"),
                    Check(YearMonth.of(2026, 11), Figure.Spent, 10_000, "H04"),
                    Check(YearMonth.of(2026, 11), Figure.Remaining, 90_000, "H04")
                )
            )
        )
    )

    /**
     * H05, H01 and H02 in order: an unchanged default carries without adding up; a new
     * October default leaves September as it was; correcting September changes only it.
     */
    val PastLimits = Scenario(
        id = "limite-pasado",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 9, 10),
        title = "Límite pasado",
        summary = "H05, H01, H02: septiembre conserva su límite cuando cambia octubre; " +
            "una corrección de septiembre no toca octubre.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 100_000),
            Expense("comida", 20_000),
            AdvanceTo(LocalDate.of(2026, 10, 2)),
            Verify(
                listOf(
                    Check(Sep, Figure.Remaining, 80_000, "H05"),
                    Check(Oct, Figure.Limit, 100_000, "H05")
                )
            ),
            SetDefaultLimit("comida", 120_000, version = 1),
            Verify(
                listOf(
                    Check(Sep, Figure.Remaining, 80_000, "H01"),
                    Check(Oct, Figure.Limit, 120_000, "H01")
                )
            ),
            SetMonthLimit("comida", Sep, 90_000, version = 2),
            Verify(
                listOf(
                    Check(Sep, Figure.Remaining, 70_000, "H02"),
                    Check(Oct, Figure.Limit, 120_000, "H02")
                )
            )
        )
    )

    /** AR01/AR02: archiving keeps October's history and gives November no budget. */
    val Archived = Scenario(
        id = "archivada",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 5),
        title = "Categoría archivada",
        summary = "AR01/AR02: octubre conserva 80.000 y su límite; no se anota en ella; " +
            "noviembre no tiene presupuesto.",
        steps = listOf(
            Category("ocio", "Ocio", "theaters", 100_000),
            Expense("ocio", 20_000),
            Archive("ocio", version = 1),
            Expense("ocio", 5_000, expect = Expect.Rejected("CategoryArchived")),
            Verify(
                listOf(
                    Check(Oct, Figure.Remaining, 80_000, "AR01"),
                    Check(Oct, Figure.Limit, 100_000, "AR01")
                )
            ),
            AdvanceTo(LocalDate.of(2026, 11, 3)),
            Verify(
                listOf(
                    Check(YearMonth.of(2026, 11), Figure.NoLimit, 0, "AR02"),
                    Check(Oct, Figure.Remaining, 80_000, "AR02")
                )
            )
        )
    )

    /** I01 in October and I02 in November: income against the budget, never clamped. */
    val IncomeAllocation = Scenario(
        id = "ingresos",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 5),
        title = "Ingresos y presupuesto",
        summary = "I01: sin asignar 50.000. I02: sin asignar −50.000, sin recortar a 0.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 100_000),
            Expense("comida", 30_000),
            Income(150_000, "salary"),
            Verify(
                listOf(
                    Check(Oct, Figure.NetFlow, 120_000, "I01"),
                    Check(Oct, Figure.Remaining, 70_000, "I01"),
                    Check(Oct, Figure.Unallocated, 50_000, "I01")
                )
            ),
            AdvanceTo(LocalDate.of(2026, 11, 3)),
            Expense("comida", 30_000),
            Income(50_000, "salary"),
            Verify(
                listOf(
                    Check(YearMonth.of(2026, 11), Figure.NetFlow, 20_000, "I02"),
                    Check(YearMonth.of(2026, 11), Figure.Remaining, 70_000, "I02"),
                    Check(YearMonth.of(2026, 11), Figure.Unallocated, -50_000, "I02")
                )
            )
        )
    )

    /** ADR 0015: exact category insight examples, using the real command paths. */
    val CategoryDetail = Scenario(
        id = "detalle-categoria",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 8, 1),
        title = "Detalle de categoría",
        summary = "22 oct: 100.000 gastados, 100.000 restantes; 70.000 por gastar esta semana. " +
            "Agosto 20.000 y septiembre 0: promedio histórico 10.000.",
        steps = listOf(
            Category("comida", "Alimentación", "restaurant", 200_000),
            Expense("comida", 20_000, "Mercado de agosto"),
            AdvanceTo(LocalDate.of(2026, 10, 1)),
            Expense("comida", 40_000, "Mercado"),
            AdvanceTo(LocalDate.of(2026, 10, 8)),
            Expense("comida", 30_000, "Almuerzo"),
            AdvanceTo(LocalDate.of(2026, 10, 15)),
            Expense("comida", 15_000, "Panadería"),
            AdvanceTo(LocalDate.of(2026, 10, 21)),
            Expense("comida", 10_000, "Fruta"),
            AdvanceTo(LocalDate.of(2026, 10, 22)),
            Expense("comida", 5_000, "Café"),
            Verify(
                listOf(
                    Check(
                        Oct,
                        Figure.Spent,
                        100_000,
                        "ADR 0015: 40.000 + 30.000 + 15.000 + 10.000 + 5.000"
                    ),
                    Check(Oct, Figure.Remaining, 100_000, "ADR 0015: 200.000 − 100.000"),
                    Check(Sep, Figure.Spent, 0, "ADR 0015: configured month with no expenses")
                )
            )
        )
    )

    /**
     * The approved goal examples for stamps (MT01–MT05) on *Viaje a la costa*, *Curso de
     * inglés* and *Celular nuevo*; a double tap writes nothing new (MT11), and Recibo's
     * remainder is untouched by aportes (MT06).
     */
    val GoalStamps = Scenario(
        id = "metas-sellos",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 23),
        title = "Metas · Sellos",
        summary = "749.999 no gana sello; 750.000 gana el 25 %; dos sellos en un aporte; " +
            "102 % con la Meta esperando; el presupuesto no cambia.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 500_000),
            Expense("comida", 100_000),
            Goal("viaje", "Viaje a la costa", "flight", 3_000_000),
            Aporte("viaje", 749_999, 1),
            Verify(emptyList(), listOf(GoalCheck("viaje", 749_999, 24, None, "MT02"))),
            Aporte("viaje", 1, 2),
            Verify(
                emptyList(),
                listOf(GoalCheck("viaje", 750_000, 25, earned(1), "MT01: 749.999 + 1 = 750.000"))
            ),
            Goal("curso", "Curso de inglés", "home", 3_000_000),
            Aporte("curso", 700_000, 1),
            Aporte("curso", 900_000, 2),
            Repeat(9),
            Goal("celular", "Celular nuevo", "phone_iphone", 1_200_000),
            Aporte("celular", 450_000, 1),
            Aporte("viaje", 2_200_000, 3),
            Aporte("viaje", 110_000, 4),
            Verify(
                listOf(Check(Oct, Figure.Remaining, 400_000, "MT06: 500.000 − 100.000")),
                listOf(
                    GoalCheck(
                        "curso",
                        1_600_000,
                        53,
                        earned(2),
                        "MT04, repetido sin escribir (MT11)"
                    ),
                    GoalCheck(
                        "celular",
                        450_000,
                        37,
                        earned(1),
                        "MT03; 25 % = 300.000, alcanzado (regla 3)"
                    ),
                    GoalCheck("viaje", 3_060_000, 102, MetaWaiting, "MT05")
                )
            )
        )
    )

    /** Withdrawals (MT07–MT10): a faded stamp stays yours and lights again. */
    val GoalWithdrawals = Scenario(
        id = "metas-retiros",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 23),
        title = "Metas · Retiros",
        summary = "Más de lo guardado se rechaza; el 50 % queda guardado y vuelve a " +
            "encenderse; retirar todo deja dos sellos guardados.",
        steps = listOf(
            Goal("viaje", "Viaje a la costa", "flight", 3_000_000),
            Aporte("viaje", 1_710_000, 1),
            Retiro("viaje", 1_710_001, 2, Expect.Rejected("AboveSaved")),
            Retiro("viaje", 400_000, 2),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck(
                        "viaje",
                        1_310_000,
                        43,
                        listOf(Stamp.Earned, Stamp.Kept, Stamp.Empty, Stamp.Empty),
                        "MT08 y MT07"
                    )
                )
            ),
            Aporte("viaje", 200_000, 3),
            Verify(
                emptyList(),
                listOf(GoalCheck("viaje", 1_510_000, 50, earned(2), "MT10"))
            ),
            Retiro("viaje", 1_510_000, 4),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck(
                        "viaje",
                        0,
                        0,
                        listOf(Stamp.Kept, Stamp.Kept, Stamp.Empty, Stamp.Empty),
                        "MT09, retirando todo desde 1.510.000"
                    )
                )
            )
        )
    )

    /** Target changes (MT17–MT19), each from 1.710.000 of 3.000.000. */
    val GoalTargets = Scenario(
        id = "metas-objetivo",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 23),
        title = "Metas · Cambiar el objetivo",
        summary = "A 4.000.000: 42 %, el 50 % guardado. A 2.000.000: 85 %, gana el 75 %. " +
            "A 1.700.000: 100 %, la Meta esperando.",
        steps = listOf(
            Goal("viaje", "Viaje a la costa", "flight", 3_000_000),
            Goal("moto", "Moto", "home", 3_000_000),
            Goal("casa", "Cuota de la casa", "home", 3_000_000),
            Aporte("viaje", 1_710_000, 1),
            Aporte("moto", 1_710_000, 1),
            Aporte("casa", 1_710_000, 1),
            GoalTarget("viaje", 4_000_000, 2),
            GoalTarget("moto", 2_000_000, 2),
            GoalTarget("casa", 1_700_000, 2),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck(
                        "viaje",
                        1_710_000,
                        42,
                        listOf(Stamp.Earned, Stamp.Kept, Stamp.Empty, Stamp.Empty),
                        "MT17"
                    ),
                    GoalCheck("moto", 1_710_000, 85, earned(3), "MT18"),
                    GoalCheck("casa", 1_710_000, 100, MetaWaiting, "MT19")
                )
            )
        )
    )

    /**
     * Recibo's goal (MT28–MT30) and Marcar como lograda (MT23, MT24, MT26). The completion
     * and its Deshacer come late, so a run cut off and resumed in a new process still has
     * the Deshacer on offer: it is gone with the process that marked it.
     */
    val GoalCompletion = Scenario(
        id = "metas-lograda",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 23),
        title = "Metas · Lograda y Recibo",
        summary = "Recibo muestra la de mayor avance, luego la elegida; Marcar y Deshacer; " +
            "la elegida lograda deja volver a la de mayor avance.",
        steps = listOf(
            Goal("viaje", "Viaje a la costa", "flight", 3_000_000),
            Goal("celular", "Celular nuevo", "phone_iphone", 1_200_000),
            Aporte("viaje", 2_950_000, 1),
            Aporte("celular", 450_000, 1),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck("viaje", 2_950_000, 98, earned(3), "MT28", onRecibo = true),
                    GoalCheck("celular", 450_000, 37, earned(1), "MT28; MT03", onRecibo = false)
                )
            ),
            ChooseForRecibo("celular"),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck("viaje", 2_950_000, 98, earned(3), "MT29", onRecibo = false),
                    GoalCheck("celular", 450_000, 37, earned(1), "MT29; MT03", onRecibo = true)
                )
            ),
            Aporte("viaje", 110_000, 2),
            MarkLograda("viaje", 3),
            UndoLograda(8),
            Verify(
                emptyList(),
                listOf(GoalCheck("viaje", 3_060_000, 102, MetaWaiting, "MT24"))
            ),
            MarkLograda("celular", 2),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck("celular", 450_000, 37, earned(4), "MT26", completed = true),
                    GoalCheck(
                        "viaje",
                        3_060_000,
                        102,
                        MetaWaiting,
                        "MT30",
                        onRecibo = true
                    )
                )
            ),
            MarkLograda("viaje", 5),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck(
                        "viaje",
                        3_060_000,
                        102,
                        earned(4),
                        "MT23; ninguna activa, nada en Recibo",
                        completed = true,
                        onRecibo = false
                    )
                )
            )
        )
    )

    /**
     * MT25: a Deshacer after the window is refused and the goal stays lograda. The window
     * is zero, real elapsed time like any other, so nothing waits.
     */
    val GoalTooLate = Scenario(
        id = "metas-tarde",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 23),
        title = "Metas · Deshacer tarde",
        summary = "Marcar con Deshacer de 0 s; Deshacer después se rechaza y la meta " +
            "queda lograda.",
        steps = listOf(
            Goal("celular", "Celular nuevo", "phone_iphone", 1_200_000),
            Aporte("celular", 1_200_000, 1),
            MarkLograda("celular", 2, undoFor = Duration.ZERO),
            UndoLograda(2, Expect.Rejected("UndoExpired")),
            Verify(
                emptyList(),
                listOf(
                    GoalCheck(
                        "celular",
                        1_200_000,
                        100,
                        earned(4),
                        "MT25: final, Deshacer fuera de tiempo",
                        completed = true
                    )
                )
            )
        )
    )

    /**
     * RE10: rent on the 5th and a weekly bus from 26 sep; the last catch-up was on 3 oct.
     * Opening on 24 oct records the 5 oct rent and the 10, 17 and 24 oct bus rides, each
     * on its own date: four entries, 1.010.000.
     */
    val WeekAway = Scenario(
        id = "semana-fuera",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 9, 26),
        title = "Programados · Una semana fuera",
        summary = "RE10: al volver el 24 oct se registran el arriendo del 5 y el bus del 10, " +
            "17 y 24, cada uno en su fecha: 1.010.000.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 1_500_000),
            Category("transporte", "Transporte", "directions_bus", 200_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 950_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            Schedule("bus", Scheduled.Payment("transporte", 20_000, "Bus"), Every.Weeks(1)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 3)),
            CatchUp,
            Verify(
                emptyList(),
                occurrences = listOf(
                    OccurrenceCheck(
                        "bus",
                        LocalDate.of(2026, 9, 26),
                        LocalDate.of(2026, 10, 3),
                        listOf(
                            Recorded(LocalDate.of(2026, 9, 26), 20_000),
                            Recorded(LocalDate.of(2026, 10, 3), 20_000)
                        ),
                        "RE10: la del 3 oct ya registrada"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("arriendo", null, "RE10: aún nada del arriendo"),
                    ScheduleCheck("bus", Occurrence(LocalDate.of(2026, 10, 3), 1), "RE10")
                )
            ),
            AdvanceTo(LocalDate.of(2026, 10, 24)),
            CatchUp,
            Verify(
                listOf(
                    Check(
                        Oct,
                        Figure.Spent,
                        1_030_000,
                        "a mano: 20.000 del 3 oct + 1.010.000 (RE10)"
                    )
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 4),
                        LocalDate.of(2026, 10, 24),
                        listOf(Recorded(LocalDate.of(2026, 10, 5), 950_000)),
                        "RE10"
                    ),
                    OccurrenceCheck(
                        "bus",
                        LocalDate.of(2026, 10, 4),
                        LocalDate.of(2026, 10, 24),
                        listOf(10, 17, 24).map { Recorded(LocalDate.of(2026, 10, it), 20_000) },
                        "RE10"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("arriendo", Occurrence(LocalDate.of(2026, 10, 5), 1), "RE10"),
                    ScheduleCheck("bus", Occurrence(LocalDate.of(2026, 10, 24), 1), "RE10")
                )
            )
        )
    )

    /**
     * RE11: a café every day, last handled on 31 jul; opening on 24 oct records the 85
     * occurrences from 1 ago, 425.000, in two batches: 1 ago – 19 sep (50) and 20 sep –
     * 24 oct (35). The catch-up step lists the batches.
     */
    val LongGap = Scenario(
        id = "cafe-diario",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 7, 31),
        title = "Programados · Tres meses sin abrir",
        summary = "RE11: un café diario; al abrir el 24 oct se registran 85 en lotes de 50 y " +
            "35: 425.000.",
        steps = listOf(
            Category("comida", "Comida", "restaurant", 500_000),
            Schedule("cafe", Scheduled.Payment("comida", 5_000, "Café"), Every.Days(1)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 24)),
            CatchUp,
            Verify(
                listOf(
                    Check(YearMonth.of(2026, 8), Figure.Spent, 155_000, "a mano, RE11: 31 × 5.000"),
                    Check(Sep, Figure.Spent, 150_000, "a mano, RE11: 30 × 5.000"),
                    Check(Oct, Figure.Spent, 120_000, "a mano, RE11: 24 × 5.000")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "cafe",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 10, 24),
                        // Every day from 1 ago to 24 oct.
                        (0L until 85L).map {
                            Recorded(LocalDate.of(2026, 8, 1).plusDays(it), 5_000)
                        },
                        "RE11: 85 del 1 ago al 24 oct, 425.000"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("cafe", Occurrence(LocalDate.of(2026, 10, 24), 1), "RE11")
                )
            )
        )
    )

    /**
     * RE09, then RE01: monthly on the 5th ending 5 dic records 5 nov and 5 dic and nothing
     * after; monthly on the 31st from 1 ene 2027 keeps its anchor through February and
     * April. The amounts are the scenario's own; the examples set only the dates.
     */
    val MonthEnds = Scenario(
        id = "dia-31",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 22),
        title = "Programados · Día 31 y fecha final",
        summary = "RE09: hasta el 5 dic, nada después. RE01: el 31 vuelve tras el 28 feb y el " +
            "30 abr.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 1_000_000),
            Schedule(
                "gas",
                Scheduled.Payment("hogar", 40_000, "Gas"),
                Every.Month(5),
                end = LocalDate.of(2026, 12, 5)
            ),
            CatchUp,
            AdvanceTo(LocalDate.of(2027, 1, 1)),
            CatchUp,
            Schedule("tarjeta", Scheduled.Payment("hogar", 200_000, "Tarjeta"), Every.Month(31)),
            CatchUp,
            AdvanceTo(LocalDate.of(2027, 5, 31)),
            CatchUp,
            Verify(
                emptyList(),
                occurrences = listOf(
                    OccurrenceCheck(
                        "gas",
                        LocalDate.of(2026, 10, 22),
                        LocalDate.of(2027, 5, 31),
                        listOf(
                            Recorded(LocalDate.of(2026, 11, 5), 40_000),
                            Recorded(LocalDate.of(2026, 12, 5), 40_000)
                        ),
                        "RE09"
                    ),
                    OccurrenceCheck(
                        "tarjeta",
                        LocalDate.of(2027, 1, 1),
                        LocalDate.of(2027, 5, 31),
                        listOf(
                            LocalDate.of(2027, 1, 31),
                            LocalDate.of(2027, 2, 28),
                            LocalDate.of(2027, 3, 31),
                            LocalDate.of(2027, 4, 30),
                            LocalDate.of(2027, 5, 31)
                        ).map { Recorded(it, 200_000) },
                        "RE01"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("gas", Occurrence(LocalDate.of(2026, 12, 5), 1), "RE09"),
                    ScheduleCheck("tarjeta", Occurrence(LocalDate.of(2027, 5, 31), 1), "RE01")
                )
            )
        )
    )

    /**
     * RE05, RE06 and RE07: a salary twice a month on the 15th and 30th, a payment every 15
     * days from 15 oct, which is not the same, and one on the 30th and 31st, which both
     * fall on 28 feb 2027 as slots 1 and 2.
     */
    val TwiceMonthly = Scenario(
        id = "quincena",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Dos veces al mes",
        summary = "RE05: 15 y 30, el 30 de febrero es el 28. RE07: cada 15 días no es lo " +
            "mismo. RE06: 30 y 31 caen ambos el 28 feb.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 1_000_000),
            Schedule(
                "quincena",
                Scheduled.Income("salary", 1_000_000, "Quincena"),
                Every.TwiceAMonth(15, 30)
            ),
            CatchUp,
            Schedule(
                "cuota",
                Scheduled.Payment("hogar", 50_000, "Cuota"),
                Every.Days(15),
                start = LocalDate.of(2026, 10, 15)
            ),
            CatchUp,
            Schedule(
                "cierre",
                Scheduled.Payment("hogar", 10_000, "Cierre de mes"),
                Every.TwiceAMonth(30, 31)
            ),
            CatchUp,
            AdvanceTo(LocalDate.of(2027, 2, 28)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Income, 2_000_000, "a mano, RE05: 15 y 30 oct × 1.000.000")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "quincena",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2027, 2, 28),
                        listOf(
                            Recorded(LocalDate.of(2026, 10, 15), 1_000_000),
                            Recorded(LocalDate.of(2026, 10, 30), 1_000_000, slot = 2),
                            Recorded(LocalDate.of(2026, 11, 15), 1_000_000),
                            Recorded(LocalDate.of(2026, 11, 30), 1_000_000, slot = 2),
                            Recorded(LocalDate.of(2026, 12, 15), 1_000_000),
                            Recorded(LocalDate.of(2026, 12, 30), 1_000_000, slot = 2),
                            Recorded(LocalDate.of(2027, 1, 15), 1_000_000),
                            Recorded(LocalDate.of(2027, 1, 30), 1_000_000, slot = 2),
                            Recorded(LocalDate.of(2027, 2, 15), 1_000_000),
                            Recorded(LocalDate.of(2027, 2, 28), 1_000_000, slot = 2)
                        ),
                        "RE05"
                    ),
                    OccurrenceCheck(
                        "cuota",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2027, 1, 13),
                        listOf(
                            LocalDate.of(2026, 10, 15),
                            LocalDate.of(2026, 10, 30),
                            LocalDate.of(2026, 11, 14),
                            LocalDate.of(2026, 11, 29),
                            LocalDate.of(2026, 12, 14),
                            LocalDate.of(2026, 12, 29),
                            LocalDate.of(2027, 1, 13)
                        ).map { Recorded(it, 50_000) },
                        "RE07"
                    ),
                    OccurrenceCheck(
                        "cierre",
                        LocalDate.of(2027, 2, 1),
                        LocalDate.of(2027, 2, 28),
                        listOf(
                            Recorded(LocalDate.of(2027, 2, 28), 10_000, slot = 1),
                            Recorded(LocalDate.of(2027, 2, 28), 10_000, slot = 2)
                        ),
                        "RE06"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("quincena", Occurrence(LocalDate.of(2027, 2, 28), 2), "RE05"),
                    ScheduleCheck("cierre", Occurrence(LocalDate.of(2027, 2, 28), 2), "RE06")
                )
            )
        )
    )

    /**
     * RE21 and RE22: rent on the 5th paused on 3 oct. Resumed on 5 dic, the resume day
     * counts: 5 oct and 5 nov are skipped, 5 dic is recorded. The same paused rent resumed
     * on 6 dic skips 5 dic too and records next on 5 ene 2027.
     */
    val PauseAndResume = Scenario(
        id = "pausa",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Pausa y reanudación",
        summary = "RE21: reanudado el 5 dic, se registra el 5 dic. RE22: reanudado el 6 dic, " +
            "el siguiente es el 5 ene.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 3_000_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 950_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            Schedule("bodega", Scheduled.Payment("hogar", 950_000, "Bodega"), Every.Month(5)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 3)),
            CatchUp,
            PauseSchedule("arriendo", 1),
            PauseSchedule("bodega", 1),
            Verify(
                emptyList(),
                schedules = listOf(
                    ScheduleCheck("arriendo", null, "RE21", LocalDate.of(2026, 10, 3)),
                    ScheduleCheck("bodega", null, "RE22", LocalDate.of(2026, 10, 3))
                )
            ),
            AdvanceTo(LocalDate.of(2026, 12, 5)),
            CatchUp,
            ResumeSchedule("arriendo", 2),
            CatchUp,
            Verify(
                emptyList(),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 12, 5),
                        listOf(Recorded(LocalDate.of(2026, 12, 5), 950_000)),
                        "RE21: 5 oct y 5 nov saltados"
                    ),
                    OccurrenceCheck(
                        "bodega",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 12, 5),
                        emptyList(),
                        "RE22: aún en pausa"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("arriendo", Occurrence(LocalDate.of(2026, 12, 5), 1), "RE21")
                )
            ),
            AdvanceTo(LocalDate.of(2026, 12, 6)),
            CatchUp,
            ResumeSchedule("bodega", 2),
            CatchUp,
            AdvanceTo(LocalDate.of(2027, 1, 5)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Spent, 0, "a mano: 5 oct saltado en ambos (RE21, RE22)"),
                    Check(YearMonth.of(2026, 11), Figure.Spent, 0, "a mano: 5 nov saltado (RE21)"),
                    Check(YearMonth.of(2026, 12), Figure.Spent, 950_000, "a mano: solo RE21")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2027, 1, 5),
                        listOf(
                            Recorded(LocalDate.of(2026, 12, 5), 950_000),
                            Recorded(LocalDate.of(2027, 1, 5), 950_000)
                        ),
                        "RE21"
                    ),
                    OccurrenceCheck(
                        "bodega",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2027, 1, 5),
                        listOf(Recorded(LocalDate.of(2027, 1, 5), 950_000)),
                        "RE22: 5 oct, 5 nov y 5 dic saltados"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("bodega", Occurrence(LocalDate.of(2027, 1, 5), 1), "RE22")
                )
            )
        )
    )

    /**
     * RE17–RE20, each changed on 10 or 20 oct for the occurrences after that day: a new
     * amount, a later day, an earlier day, and every 15 days becoming every 10. A new rule
     * that would start the same day is refused (rule 7).
     */
    val Changes = Scenario(
        id = "cambio-desde-manana",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Cambios desde mañana",
        summary = "RE17–RE20: lo registrado se queda con sus términos; lo nuevo empieza " +
            "mañana.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 3_000_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 950_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            Schedule(
                "parqueadero",
                Scheduled.Payment("hogar", 100_000, "Parqueadero"),
                Every.Month(5)
            ),
            CatchUp,
            Schedule(
                "administracion",
                Scheduled.Payment("hogar", 200_000, "Administración"),
                Every.Month(25)
            ),
            CatchUp,
            Schedule(
                "cuota",
                Scheduled.Payment("hogar", 50_000, "Cuota"),
                Every.Days(15),
                start = LocalDate.of(2026, 10, 15)
            ),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 10)),
            CatchUp,
            ChangeSchedule("arriendo", 1, pesos = 1_000_000),
            ChangeSchedule(
                "parqueadero",
                1,
                every = Every.Month(25),
                start = LocalDate.of(2026, 10, 11)
            ),
            ChangeSchedule(
                "administracion",
                1,
                every = Every.Month(5),
                start = LocalDate.of(2026, 10, 11)
            ),
            AdvanceTo(LocalDate.of(2026, 10, 20)),
            CatchUp,
            ChangeSchedule(
                "cuota",
                1,
                every = Every.Days(10),
                start = LocalDate.of(2026, 10, 20),
                expect = Expect.Rejected("StartTooEarly")
            ),
            ChangeSchedule(
                "cuota",
                1,
                every = Every.Days(10),
                start = LocalDate.of(2026, 10, 25)
            ),
            AdvanceTo(LocalDate.of(2026, 11, 14)),
            CatchUp,
            Verify(
                emptyList(),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 14),
                        listOf(
                            Recorded(LocalDate.of(2026, 10, 5), 950_000),
                            Recorded(LocalDate.of(2026, 11, 5), 1_000_000)
                        ),
                        "RE17"
                    ),
                    OccurrenceCheck(
                        "parqueadero",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 14),
                        listOf(
                            Recorded(LocalDate.of(2026, 10, 5), 100_000),
                            Recorded(LocalDate.of(2026, 10, 25), 100_000)
                        ),
                        "RE18"
                    ),
                    OccurrenceCheck(
                        "administracion",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 14),
                        listOf(Recorded(LocalDate.of(2026, 11, 5), 200_000)),
                        "RE19"
                    ),
                    OccurrenceCheck(
                        "cuota",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 14),
                        listOf(
                            LocalDate.of(2026, 10, 15),
                            LocalDate.of(2026, 10, 25),
                            LocalDate.of(2026, 11, 4),
                            LocalDate.of(2026, 11, 14)
                        ).map { Recorded(it, 50_000) },
                        "RE20: el 30 oct no se registra"
                    )
                )
            )
        )
    )

    /**
     * RE23: archiving Deporte on 12 oct pauses its gym payment, so 15 oct is not recorded
     * and it cannot resume while archived. Unarchived on 20 oct it stays paused; resumed
     * the same day, the next is 15 nov.
     */
    val ArchivePauses = Scenario(
        id = "archivo-pausa",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Archivar pausa",
        summary = "RE23: archivada el 12 oct, el gimnasio del 15 no se registra; desarchivada " +
            "sigue en pausa; reanudada, el siguiente es el 15 nov.",
        steps = listOf(
            Category("deporte", "Deporte", "fitness_center", 200_000),
            Schedule(
                "gimnasio",
                Scheduled.Payment("deporte", 99_900, "Gimnasio"),
                Every.Month(15)
            ),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 12)),
            CatchUp,
            Archive("deporte", version = 1),
            Verify(
                emptyList(),
                schedules = listOf(
                    ScheduleCheck("gimnasio", null, "RE23", LocalDate.of(2026, 10, 12))
                )
            ),
            ResumeSchedule("gimnasio", 2, Expect.Rejected("CategoryArchived")),
            AdvanceTo(LocalDate.of(2026, 10, 15)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 20)),
            CatchUp,
            Unarchive("deporte", version = 2),
            Verify(
                emptyList(),
                occurrences = listOf(
                    OccurrenceCheck(
                        "gimnasio",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 20),
                        emptyList(),
                        "RE23: el 15 oct no se registra"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck(
                        "gimnasio",
                        null,
                        "RE23: desarchivada, sigue en pausa",
                        LocalDate.of(2026, 10, 12)
                    )
                )
            ),
            ResumeSchedule("gimnasio", 2),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 11, 15)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Spent, 0, "a mano, RE23: nada en octubre"),
                    Check(YearMonth.of(2026, 11), Figure.Spent, 99_900, "a mano, RE23: 15 nov")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "gimnasio",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 15),
                        listOf(Recorded(LocalDate.of(2026, 11, 15), 99_900)),
                        "RE23"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("gimnasio", Occurrence(LocalDate.of(2026, 11, 15), 1), "RE23")
                )
            )
        )
    )

    /** RE24: deleting the rent schedule on 10 oct keeps its 5 oct entry; nothing more comes. */
    val DeleteKeepsEntries = Scenario(
        id = "borrar-programado",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Borrar el programado",
        summary = "RE24: el arriendo del 5 oct se queda como un gasto; el 5 nov no llega nada.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 1_500_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 950_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 5)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 10)),
            CatchUp,
            DeleteSchedule("arriendo", 1),
            AdvanceTo(LocalDate.of(2026, 11, 5)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Spent, 950_000, "RE24: el 5 oct se queda"),
                    Check(YearMonth.of(2026, 11), Figure.Spent, 0, "RE24: nada más")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 5),
                        listOf(Recorded(LocalDate.of(2026, 10, 5), 950_000)),
                        "RE24"
                    )
                ),
                schedules = listOf(ScheduleCheck("arriendo", null, "RE24", present = false))
            )
        )
    )

    /**
     * RE16 and RE15: the rent entered by hand beside the recorded one stays apart; the
     * recorded one, deleted on 6 oct, is not recorded again on 7 oct, and 5 nov comes as
     * usual.
     */
    val DeletedOccurrence = Scenario(
        id = "ocurrencia-borrada",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Una ocurrencia borrada",
        summary = "RE16: el arriendo a mano no se junta con el registrado. RE15: borrado el 6 " +
            "oct, no vuelve el 7; el 5 nov llega.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 3_000_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 950_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 5)),
            CatchUp,
            Expense("hogar", 950_000, "Arriendo"),
            Verify(
                listOf(Check(Oct, Figure.Spent, 1_900_000, "RE16")),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 5),
                        listOf(Recorded(LocalDate.of(2026, 10, 5), 950_000)),
                        "RE16: la de a mano no es una ocurrencia"
                    )
                )
            ),
            AdvanceTo(LocalDate.of(2026, 10, 6)),
            CatchUp,
            DeleteOccurrence("arriendo", LocalDate.of(2026, 10, 5)),
            AdvanceTo(LocalDate.of(2026, 10, 7)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Spent, 950_000, "a mano, RE15 y RE16: queda la de a mano")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 7),
                        emptyList(),
                        "RE15: no se registra de nuevo"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck(
                        "arriendo",
                        Occurrence(LocalDate.of(2026, 10, 5), 1),
                        "RE15: su origen sigue atendido"
                    )
                )
            ),
            AdvanceTo(LocalDate.of(2026, 11, 5)),
            CatchUp,
            Verify(
                emptyList(),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 11, 5),
                        listOf(Recorded(LocalDate.of(2026, 11, 5), 950_000)),
                        "RE15: el 5 nov llega"
                    )
                ),
                schedules = listOf(
                    ScheduleCheck("arriendo", Occurrence(LocalDate.of(2026, 11, 5), 1), "RE15")
                )
            )
        )
    )

    /**
     * RE25–RE29 and RE26 on Recibo (ADR 0012 BG-E02, E03, E06, E11): Hogar's 1.000.000 with
     * rent of 200.000 due on the 28th, seen on jue 22 oct with 10 days left. Spending moves
     * the margin from Al día to Sin margen, Revisar and past the limit; on the 28th catch-up
     * records the rent, nothing is set aside any more and the headline stays where it was.
     */
    val Reservations = Scenario(
        id = "pagos-previstos",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Pagos previstos",
        summary = "RE25–RE29: el arriendo del 28 se separa de lo que queda; Sin margen en 0, " +
            "Revisar cuando falta y al pasar el límite. RE26: al registrarse nada cambia.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 1_000_000),
            Expense("hogar", 400_000),
            AdvanceTo(LocalDate.of(2026, 10, 22)),
            Schedule("arriendo", Scheduled.Payment("hogar", 200_000, "Arriendo"), Every.Month(28)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Remaining, 600_000, "RE25, BG-E02"),
                    Check(Oct, Figure.Reserved, 200_000, "RE25"),
                    Check(Oct, Figure.Margin, 400_000, "RE25: 600.000 − 200.000"),
                    Check(Oct, Figure.PerDay, 40_000, "RE25: 400.000 / 10 días")
                )
            ),
            Expense("hogar", 400_000),
            Verify(
                listOf(
                    Check(Oct, Figure.Margin, 0, "RE27, BG-E11: Sin margen"),
                    Check(Oct, Figure.PerDay, 0, "RE27")
                )
            ),
            Expense("hogar", 50_000),
            Verify(
                listOf(
                    Check(Oct, Figure.Remaining, 150_000, "a mano: 1.000.000 − 850.000"),
                    Check(Oct, Figure.Margin, -50_000, "RE28, BG-E06: faltan 50.000"),
                    Check(Oct, Figure.PerDay, 0, "RE28")
                )
            ),
            Expense("hogar", 230_000),
            Verify(
                listOf(
                    Check(Oct, Figure.Remaining, -80_000, "RE29: pasaste tu límite por 80.000"),
                    Check(Oct, Figure.Reserved, 200_000, "RE29"),
                    Check(Oct, Figure.Margin, -280_000, "RE29: faltan 280.000")
                )
            ),
            AdvanceTo(LocalDate.of(2026, 10, 28)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Spent, 1_280_000, "a mano: 1.080.000 + el arriendo"),
                    Check(Oct, Figure.Reserved, 0, "RE26: registrado, ya no se separa"),
                    Check(Oct, Figure.Margin, -280_000, "RE26: el titular no se mueve")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "arriendo",
                        LocalDate.of(2026, 10, 22),
                        LocalDate.of(2026, 10, 28),
                        listOf(Recorded(LocalDate.of(2026, 10, 28), 200_000)),
                        "RE26"
                    )
                )
            )
        )
    )

    /**
     * RE35–RE37 (ADR 0012 BG-E08, BG-E09): 2.000.000 recorded, budgets of 2.800.000 and a
     * salary of 1.000.000 expected on vie 30 oct. The plan shows 3.000.000 and 200.000
     * unassigned while Recibo does not move; on the 30th catch-up records the salary and
     * the plan goes, with nothing counted twice.
     */
    val IncomePlan = Scenario(
        id = "plan-del-mes",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = LocalDate.of(2026, 10, 1),
        title = "Programados · Plan del mes",
        summary = "RE35–RE37: el salario del 30 se espera: planeado 3.000.000, sin asignar en " +
            "el plan 200.000, Recibo igual. RE36: registrado el 30, el plan se va.",
        steps = listOf(
            Category("hogar", "Hogar", "home", 2_800_000),
            Income(2_000_000, "salary"),
            AdvanceTo(LocalDate.of(2026, 10, 22)),
            Schedule("salario", Scheduled.Income("salary", 1_000_000, "Salario"), Every.Month(30)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Income, 2_000_000, "RE35: Anotado"),
                    Check(Oct, Figure.Expected, 1_000_000, "RE35: Esperado"),
                    Check(Oct, Figure.Planned, 3_000_000, "RE35: Planeado"),
                    Check(Oct, Figure.PlanUnassigned, 200_000, "RE35, BG-E08"),
                    Check(Oct, Figure.Unallocated, -800_000, "RE35: sin asignar anotado"),
                    Check(Oct, Figure.Remaining, 2_800_000, "RE37: Recibo no cambia por F"),
                    Check(Oct, Figure.PerDay, 280_000, "RE37: 2.800.000 / 10 días")
                )
            ),
            AdvanceTo(LocalDate.of(2026, 10, 30)),
            CatchUp,
            Verify(
                listOf(
                    Check(Oct, Figure.Income, 3_000_000, "RE36, BG-E09"),
                    Check(Oct, Figure.Expected, 0, "RE36: el plan se va"),
                    Check(Oct, Figure.Unallocated, 200_000, "RE36: sin doble ingreso")
                ),
                occurrences = listOf(
                    OccurrenceCheck(
                        "salario",
                        LocalDate.of(2026, 10, 22),
                        LocalDate.of(2026, 10, 30),
                        listOf(Recorded(LocalDate.of(2026, 10, 30), 1_000_000)),
                        "RE36"
                    )
                )
            )
        )
    )

    /**
     * IN02–IN07 and IN17: the four investments of the v2 page built over 18 months, the
     * financial clock moving forward between movements. Fondo's two aportes carry the
     * value of 3 may 2025 forward (rule 5); a double tap writes nothing new; each figure
     * then matches the page, a Cripto without a value stays out of the total, and with
     * Parqueadero and CDT deleted only Fondo and Ecopetrol remain.
     */
    val InvestmentPage = Scenario(
        id = "inversiones-pagina",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2025, 5, 3),
        title = "Inversiones · La página",
        summary = "IN02–IN06: Parqueadero +7,5 %, CDT +7,5 %, Fondo −4,3 %, Ecopetrol +7,6 %; " +
            "total 38.038.000, +5,6 %. IN17: Cripto sin valor no cuenta. IN07: −0,3 %.",
        steps = listOf(
            Investment("fondo", "Fondo de inversión", "insights", Currency.COP, 3_000_000),
            AdvanceTo(day(2025, 10, 1)),
            Investment("cdt", "CDT Banco", "account_balance", Currency.COP, 10_000_000),
            AdvanceTo(day(2025, 11, 3)),
            Movement("fondo", Move.Aporte, 3_000_000, 1),
            Repeat(4),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "fondo",
                        6_000_000,
                        Worth(6_000_000, day(2025, 5, 3), 0, Neutral),
                        "Regla 5 a mano: 3.000.000 del 3 may 2025 + el aporte de 3.000.000, " +
                            "valor del 3 may; G 0, 0,0 % sin sello (IN15); repetido sin escribir",
                        since = May2025
                    )
                )
            ),
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
            AdvanceTo(day(2026, 10, 9)),
            Movement("ecopetrol", Move.Valor, 3_050_000, 7),
            AdvanceTo(day(2026, 10, 14)),
            Movement("fondo", Move.Valor, 5_242_000, 3),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "parqueadero",
                        18_000_000,
                        Worth(19_000_000, day(2026, 6, 12), 1_350_000, gana(75)),
                        "IN02",
                        dividends = 350_000
                    ),
                    InvestmentCheck(
                        "cdt",
                        10_000_000,
                        Worth(10_746_000, day(2026, 9, 30), 746_000, gana(75)),
                        "IN03"
                    ),
                    InvestmentCheck(
                        "fondo",
                        6_000_000,
                        Worth(5_242_000, day(2026, 10, 14), -258_000, pierde(43)),
                        "IN04",
                        withdrawn = 500_000,
                        since = May2025
                    ),
                    InvestmentCheck(
                        "ecopetrol",
                        3_000_000,
                        Worth(3_050_000, day(2026, 10, 9), 228_000, gana(76)),
                        "IN05; sus 9 movimientos (IN23)",
                        dividends = 178_000,
                        movements = 9
                    )
                ),
                portfolio = PageTotal
            ),
            AdvanceTo(day(2026, 10, 20)),
            Investment("cripto", "Cripto", "shopping_cart", Currency.COP, 2_000_000),
            DeleteMovement(36, 1, firstValue = true),
            Verify(
                emptyList(),
                investments = listOf(InvestmentCheck("cripto", 2_000_000, null, "IN17")),
                portfolio = PageTotal.copy(source = "IN17: el total sigue como IN06")
            ),
            DeleteInvestment("parqueadero", 3),
            DeleteInvestment("cdt", 2),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck.gone("parqueadero", "IN07: borrada"),
                    InvestmentCheck.gone("cdt", "IN07: borrada")
                ),
                portfolio = PortfolioCheck.Total(
                    value = 8_292_000,
                    contributed = 9_000_000,
                    withdrawn = 500_000,
                    dividends = 178_000,
                    gain = -30_000,
                    yield = pierde(3),
                    since = May2025,
                    valuedFrom = day(2026, 10, 9),
                    valuedTo = day(2026, 10, 14),
                    rate = null,
                    source = "IN07; fechas de valor a mano (Ecopetrol y Fondo)"
                )
            )
        )
    )

    /**
     * IN08–IN16 and IN26: a value carried forward. An aporte after the 1 oct value adds to
     * it, a retiro takes from it, and both keep that value's date; a retiro above it is
     * refused and exactly it is accepted; a later value replaces it; an aporte dated
     * before it is already in it; a dividend never changes it. Recording order decides
     * on one day; a gain of 0 has no stamp and a gain of −1 is a loss.
     */
    val CarriedForward = Scenario(
        id = "valor-arrastrado",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2026, 9, 1),
        title = "Inversiones · Valor arrastrado",
        summary = "IN08 6.000.000 del 1 oct, +20,0 %; IN09 4.000.000; IN10 4.000.001 no, " +
            "4.000.000 sí; IN11 +16,0 %; IN14 +17,0 %; IN12 sigue 3.800.000; IN13; IN15; IN16.",
        steps = listOf(
            Investment("ahorro", "Fondo de ahorro", "insights", Currency.COP, 4_000_000),
            AdvanceTo(day(2026, 10, 1)),
            Movement("ahorro", Move.Valor, 5_000_000, 1),
            AdvanceTo(day(2026, 10, 10)),
            Movement("ahorro", Move.Aporte, 1_000_000, 2),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "ahorro",
                        5_000_000,
                        Worth(6_000_000, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN08"
                    )
                )
            ),
            AdvanceTo(day(2026, 10, 12)),
            Movement("ahorro", Move.Retiro, 2_000_000, 3),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "ahorro",
                        5_000_000,
                        Worth(4_000_000, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN09",
                        withdrawn = 2_000_000
                    )
                )
            ),
            Movement("ahorro", Move.Retiro, 4_000_001, 4, expect = Expect.Rejected("AboveValue")),
            Movement("ahorro", Move.Retiro, 4_000_000, 4),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "ahorro",
                        5_000_000,
                        Worth(0, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN10: 4.000.000 se acepta, V 0; G a mano 0 + 6.000.000 − 5.000.000",
                        withdrawn = 6_000_000
                    )
                )
            ),
            // Back to IN09 for the rest.
            DeleteMovement(10, 5),
            AdvanceTo(day(2026, 10, 14)),
            Movement("ahorro", Move.Valor, 3_800_000, 6),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "ahorro",
                        5_000_000,
                        Worth(3_800_000, day(2026, 10, 14), 800_000, gana(160)),
                        "IN11",
                        withdrawn = 2_000_000
                    )
                )
            ),
            Movement("ahorro", Move.Dividendo, 50_000, 7),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "ahorro",
                        5_000_000,
                        Worth(3_800_000, day(2026, 10, 14), 850_000, gana(170)),
                        "IN14",
                        withdrawn = 2_000_000,
                        dividends = 50_000
                    )
                )
            ),
            AdvanceTo(day(2026, 10, 20)),
            Movement("ahorro", Move.Aporte, 300_000, 8, on = day(2026, 10, 5)),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "ahorro",
                        5_300_000,
                        Worth(3_800_000, day(2026, 10, 14), 550_000, gana(104)),
                        "IN12 tras el dividendo de IN14: V sigue en 3.800.000; G a mano " +
                            "3.800.000 + 2.000.000 + 50.000 − 5.300.000 = 550.000, 10,38 %",
                        withdrawn = 2_000_000,
                        dividends = 50_000
                    )
                )
            ),
            Movement("ahorro", Move.Aporte, 1_000_000_000_000, 9, expect = Expect.InvalidInput),
            Investment("orden-a", "Orden A", "insights", Currency.COP, 1_000_000),
            Movement("orden-a", Move.Valor, 4_000_000, 1),
            Movement("orden-a", Move.Aporte, 500_000, 2),
            Investment("orden-b", "Orden B", "insights", Currency.COP, 1_000_000),
            Movement("orden-b", Move.Aporte, 500_000, 1),
            Movement("orden-b", Move.Valor, 4_000_000, 2),
            Investment("par", "Par", "school", Currency.COP, 1_000_000),
            Investment("casi", "Casi", "school", Currency.COP, 3_000_000, value = 2_999_999),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "orden-a",
                        1_500_000,
                        Worth(4_500_000, day(2026, 10, 20), 3_000_000, gana(2_000)),
                        "IN13: valor y luego aporte; G a mano 4.500.000 − 1.500.000, 200,0 %"
                    ),
                    InvestmentCheck(
                        "orden-b",
                        1_500_000,
                        Worth(4_000_000, day(2026, 10, 20), 2_500_000, gana(1_667)),
                        "IN13: aporte y luego valor; G a mano 4.000.000 − 1.500.000, 166,67 %"
                    ),
                    InvestmentCheck(
                        "par",
                        1_000_000,
                        Worth(1_000_000, day(2026, 10, 20), 0, Neutral),
                        "IN15"
                    ),
                    InvestmentCheck(
                        "casi",
                        3_000_000,
                        Worth(2_999_999, day(2026, 10, 20), -1, pierde(0)),
                        "IN16"
                    )
                )
            )
        )
    )

    /**
     * IN27–IN34: the four of the page, entered with past dates on 20 oct, then Acciones
     * EE. UU. in dollars: refused with no rate (IN33), saved with 4.150,25. The overview
     * converts once at the current rate; a new rate changes it, a later one on the same
     * day replaces it, an earlier-dated one only joins the history.
     */
    val Dollars = Scenario(
        id = "dolares",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2026, 10, 20),
        title = "Inversiones · Dólares",
        summary = "IN33 sin tasa no; IN27 +5,1 % en USD; IN28 total 42.397.838 a 4.150,25; " +
            "IN29 a 4.000,00; IN30 4.010,00 el mismo día; IN31 una tasa anterior no cambia.",
        steps = listOf(
            Investment(
                "parqueadero",
                "Parqueadero",
                "home",
                Currency.COP,
                18_000_000,
                on = day(2026, 1, 15)
            ),
            Movement("parqueadero", Move.Dividendo, 350_000, 1, on = day(2026, 4, 1)),
            Movement("parqueadero", Move.Valor, 19_000_000, 2, on = day(2026, 6, 12)),
            Investment(
                "cdt",
                "CDT Banco",
                "account_balance",
                Currency.COP,
                10_000_000,
                on = day(2025, 10, 1)
            ),
            Movement("cdt", Move.Valor, 10_746_000, 1, on = day(2026, 9, 30)),
            Investment(
                "fondo",
                "Fondo de inversión",
                "insights",
                Currency.COP,
                3_000_000,
                on = day(2025, 5, 3)
            ),
            Movement("fondo", Move.Aporte, 3_000_000, 1, on = day(2025, 11, 3)),
            Movement("fondo", Move.Retiro, 500_000, 2, on = day(2026, 8, 3)),
            Movement("fondo", Move.Valor, 5_242_000, 3, on = day(2026, 10, 14)),
            Investment(
                "ecopetrol",
                "Ecopetrol",
                "trending_up",
                Currency.COP,
                3_000_000,
                on = day(2026, 2, 10)
            ),
            Movement("ecopetrol", Move.Dividendo, 89_000, 1, on = day(2026, 4, 15)),
            Movement("ecopetrol", Move.Dividendo, 89_000, 2, on = day(2026, 7, 15)),
            Movement("ecopetrol", Move.Valor, 3_050_000, 3, on = day(2026, 10, 9)),
            Verify(
                emptyList(),
                portfolio = PageTotal.copy(source = "IN06, anotado con fechas pasadas")
            ),
            Investment(
                "acciones",
                "Acciones EE. UU.",
                "trending_up",
                Currency.USD,
                100_000,
                value = 105_050,
                expect = Expect.Rejected("RateRequired")
            ),
            Investment(
                "acciones",
                "Acciones EE. UU.",
                "trending_up",
                Currency.USD,
                100_000,
                value = 105_050,
                rate = Rate(415_025)
            ),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "acciones",
                        100_000,
                        Worth(105_050, day(2026, 10, 20), 5_050, gana(51)),
                        "IN27"
                    )
                ),
                portfolio = PortfolioCheck.Total(
                    value = 42_397_838,
                    contributed = 41_150_250,
                    withdrawn = 500_000,
                    dividends = 528_000,
                    gain = 2_275_588,
                    yield = gana(55),
                    since = May2025,
                    valuedFrom = day(2026, 6, 12),
                    valuedTo = day(2026, 10, 20),
                    rate = RateCheck(415_025, day(2026, 10, 20)),
                    source = "IN28"
                )
            ),
            SetRate(Rate(99), Expect.InvalidInput),
            SetRate(Rate(10_000_001), Expect.InvalidInput),
            SetRate(Rate(400_000, on = day(2026, 10, 21)), Expect.Rejected("FutureDate")),
            Movement("acciones", Move.Aporte, 1_000_000_000_000, 1, expect = Expect.InvalidInput),
            AdvanceTo(day(2026, 10, 24)),
            SetRate(Rate(400_000)),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "acciones",
                        100_000,
                        Worth(105_050, day(2026, 10, 20), 5_050, gana(51)),
                        "IN29: su rendimiento sigue en USD"
                    )
                ),
                portfolio = PortfolioCheck.Total(
                    value = 42_240_000,
                    contributed = 41_000_000,
                    withdrawn = 500_000,
                    dividends = 528_000,
                    gain = 2_268_000,
                    yield = gana(55),
                    since = May2025,
                    valuedFrom = day(2026, 6, 12),
                    valuedTo = day(2026, 10, 20),
                    rate = RateCheck(400_000, day(2026, 10, 24)),
                    source = "IN29: V 4.202.000 y C 4.000.000 convertidos; total a mano " +
                        "38.038.000 + 4.202.000, 2.268.000 de 41.000.000, 5,53 %"
                )
            ),
            SetRate(Rate(401_000)),
            Verify(
                emptyList(),
                portfolio = PortfolioCheck.Total(
                    value = 42_250_505,
                    contributed = 41_010_000,
                    withdrawn = 500_000,
                    dividends = 528_000,
                    gain = 2_268_505,
                    yield = gana(55),
                    since = May2025,
                    valuedFrom = day(2026, 6, 12),
                    valuedTo = day(2026, 10, 20),
                    rate = RateCheck(401_000, day(2026, 10, 24)),
                    source = "IN30: V 4.212.505 y C 4.010.000; total a mano " +
                        "38.038.000 + 4.212.505, 2.268.505 de 41.010.000, 5,53 %"
                )
            ),
            SetRate(Rate(390_000, on = day(2026, 10, 22))),
            Verify(
                emptyList(),
                portfolio = PortfolioCheck.Total(
                    value = 42_250_505,
                    contributed = 41_010_000,
                    withdrawn = 500_000,
                    dividends = 528_000,
                    gain = 2_268_505,
                    yield = gana(55),
                    since = May2025,
                    valuedFrom = day(2026, 6, 12),
                    valuedTo = day(2026, 10, 20),
                    rate = RateCheck(401_000, day(2026, 10, 24)),
                    source = "IN31: 3.900,00 del 22 oct entra al historial; sigue 4.010,00"
                )
            )
        )
    )

    /**
     * IN19–IN25: corrections replayed before they are saved. Deleting an aporte a later
     * retiro needs, or raising that retiro above the value, is refused; deleting the
     * retiro and Deshacer put IN09 back; the only aporte cannot go; nothing is dated
     * before the first aporte or in the future. Deshacer comes late, so the corpus's cut
     * and resume never falls between it and its deletion.
     */
    val Corrections = Scenario(
        id = "correcciones",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2026, 9, 1),
        title = "Inversiones · Correcciones",
        summary = "IN22 el único aporte no se borra; IN25; IN24; IN19 y IN20 rechazados; " +
            "IN21 borrar el retiro y Deshacer; una corrección y una inversión borrada.",
        steps = listOf(
            Investment("a", "Inversión A", "insights", Currency.COP, 4_000_000),
            Investment("b", "Inversión B", "insights", Currency.COP, 4_000_000),
            Investment("c", "Inversión C", "school", Currency.COP, 2_000_000),
            DeleteMovement(2, 1, expect = Expect.Rejected("LastContribution")),
            Movement(
                "c",
                Move.Valor,
                1_900_000,
                1,
                on = day(2026, 8, 31),
                expect = Expect.Rejected("BeforeFirstContribution")
            ),
            Movement(
                "c",
                Move.Aporte,
                100_000,
                1,
                on = day(2026, 9, 2),
                expect = Expect.Rejected("FutureDate")
            ),
            AdvanceTo(day(2026, 10, 1)),
            Movement("a", Move.Valor, 5_000_000, 1),
            Movement("b", Move.Valor, 5_000_000, 1),
            AdvanceTo(day(2026, 10, 10)),
            Movement("a", Move.Aporte, 1_000_000, 2),
            Movement("b", Move.Aporte, 1_000_000, 2),
            AdvanceTo(day(2026, 10, 12)),
            Movement("a", Move.Retiro, 6_000_000, 3),
            Movement("b", Move.Retiro, 2_000_000, 3),
            DeleteMovement(10, 4, expect = Expect.Rejected("AboveValue")),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "a",
                        5_000_000,
                        Worth(0, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN19: nada cambia; a mano 0 + 6.000.000 − 5.000.000",
                        withdrawn = 6_000_000
                    ),
                    InvestmentCheck(
                        "b",
                        5_000_000,
                        Worth(4_000_000, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN09",
                        withdrawn = 2_000_000
                    )
                )
            ),
            EditMovement(14, 7_000_000, 4, expect = Expect.Rejected("AboveValue")),
            DeleteMovement(14, 4),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "b",
                        5_000_000,
                        Worth(6_000_000, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN21"
                    )
                )
            ),
            UndoMovementDelete(18),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "b",
                        5_000_000,
                        Worth(4_000_000, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN21: Deshacer devuelve IN09",
                        withdrawn = 2_000_000
                    )
                )
            ),
            EditMovement(14, 1_500_000, 6),
            DeleteMovement(13, 4),
            DeleteMovement(10, 5),
            DeleteInvestment("c", 1),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "a",
                        4_000_000,
                        Worth(5_000_000, day(2026, 10, 1), 1_000_000, gana(250)),
                        "IN19: borrado primero el retiro, luego el aporte; a mano " +
                            "5.000.000 − 4.000.000, 25,0 %"
                    ),
                    InvestmentCheck(
                        "b",
                        5_000_000,
                        Worth(4_500_000, day(2026, 10, 1), 1_000_000, gana(200)),
                        "IN20: el retiro corregido a 1.500.000; a mano 6.000.000 − 1.500.000",
                        withdrawn = 1_500_000
                    ),
                    InvestmentCheck.gone("c", "IN23: borrada con sus movimientos, sin deshacer")
                )
            )
        )
    )

    /**
     * IN17, IN18 and IN35: with every value deleted an investment has no value and is
     * left out of the overview, which has no total at all; a retiro waits for a value.
     */
    val NoValue = Scenario(
        id = "sin-valor",
        version = 1,
        seed = 1,
        zone = Bogota,
        start = day(2026, 10, 20),
        title = "Inversiones · Sin valor",
        summary = "IN35: sin valores no hay total ni tasa; IN18: el retiro espera un valor y " +
            "con uno se acepta; −10,0 %.",
        steps = listOf(
            Investment("cripto", "Cripto", "shopping_cart", Currency.COP, 2_000_000),
            Investment(
                "acciones",
                "Acciones EE. UU.",
                "trending_up",
                Currency.USD,
                100_000,
                rate = Rate(415_025)
            ),
            DeleteMovement(0, 1, firstValue = true),
            Movement("cripto", Move.Retiro, 100_000, 2, expect = Expect.Rejected("NoValue")),
            DeleteMovement(1, 1, firstValue = true),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck("cripto", 2_000_000, null, "IN17: Sin valor · aportaste"),
                    InvestmentCheck("acciones", 100_000, null, "IN35: Sin valor, en USD")
                ),
                portfolio = PortfolioCheck.NoValues("IN35")
            ),
            Movement("cripto", Move.Valor, 1_800_000, 2),
            Movement("cripto", Move.Retiro, 100_000, 3),
            Verify(
                emptyList(),
                investments = listOf(
                    InvestmentCheck(
                        "cripto",
                        2_000_000,
                        Worth(1_700_000, day(2026, 10, 20), -200_000, pierde(100)),
                        "IN18: con un valor el retiro se acepta; a mano " +
                            "1.800.000 − 100.000, 1.700.000 + 100.000 − 2.000.000",
                        withdrawn = 100_000
                    )
                ),
                portfolio = PortfolioCheck.Total(
                    value = 1_700_000,
                    contributed = 2_000_000,
                    withdrawn = 100_000,
                    dividends = 0,
                    gain = -200_000,
                    yield = pierde(100),
                    since = YearMonth.of(2026, 10),
                    valuedFrom = day(2026, 10, 20),
                    valuedTo = day(2026, 10, 20),
                    rate = null,
                    source = "IN18 y regla 13: solo Cripto; sin dólar con valor, sin tasa"
                )
            )
        )
    )

    val all: List<Scenario> = listOf(
        CategoryDetail,
        G01,
        MonthAndYearEnds,
        SixMonths,
        Edges,
        LeapYear,
        SkippedMonths,
        PastLimits,
        Archived,
        IncomeAllocation,
        GoalStamps,
        GoalWithdrawals,
        GoalTargets,
        GoalCompletion,
        GoalTooLate,
        WeekAway,
        LongGap,
        MonthEnds,
        TwiceMonthly,
        PauseAndResume,
        Changes,
        ArchivePauses,
        DeleteKeepsEntries,
        DeletedOccurrence,
        Reservations,
        IncomePlan,
        InvestmentPage,
        CarriedForward,
        Dollars,
        Corrections,
        NoValue
    )

    fun find(key: String?): Scenario? = all.firstOrNull { it.key == key }
}
