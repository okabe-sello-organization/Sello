package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.Aporte
import com.software.sello.devtools.ScenarioStep.Archive
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.ChooseForRecibo
import com.software.sello.devtools.ScenarioStep.DeleteExpense
import com.software.sello.devtools.ScenarioStep.EditExpense
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.Goal
import com.software.sello.devtools.ScenarioStep.GoalTarget
import com.software.sello.devtools.ScenarioStep.Income
import com.software.sello.devtools.ScenarioStep.MarkLograda
import com.software.sello.devtools.ScenarioStep.Repeat
import com.software.sello.devtools.ScenarioStep.Retiro
import com.software.sello.devtools.ScenarioStep.SetDefaultLimit
import com.software.sello.devtools.ScenarioStep.SetMonthLimit
import com.software.sello.devtools.ScenarioStep.UndoDelete
import com.software.sello.devtools.ScenarioStep.UndoLograda
import com.software.sello.devtools.ScenarioStep.Verify
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
        GoalTooLate
    )

    fun find(key: String?): Scenario? = all.firstOrNull { it.key == key }
}
