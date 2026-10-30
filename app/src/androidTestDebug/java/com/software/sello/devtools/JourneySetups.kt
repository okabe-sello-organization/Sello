package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.AdvanceTo
import com.software.sello.devtools.ScenarioStep.CatchUp
import com.software.sello.devtools.ScenarioStep.Category
import com.software.sello.devtools.ScenarioStep.Expense
import com.software.sello.devtools.ScenarioStep.Schedule
import java.time.LocalDate
import java.time.ZoneId

/**
 * The data some device journeys start from, written through the app's own commands and
 * catch-up as the sandbox loads a history. They are tests' own, never offered in the tools.
 */
object JourneySetups {
    private val bogota = ZoneId.of("America/Bogota")

    /** Four categories and October's expenses: 730.000 of 650.000, 80.000 over (AJ04). */
    val OverspentOctober = Scenario(
        id = "prueba-octubre-excedido",
        version = 1,
        seed = 1,
        zone = bogota,
        start = LocalDate.of(2026, 10, 2),
        today = LocalDate.of(2026, 10, 2),
        title = "Octubre excedido",
        summary = "",
        steps = listOf(
            Category("cafe", "Café", "local_cafe", 50_000),
            Category("comida", "Comida", "restaurant", 200_000),
            Category("hogar", "Hogar", "home", 400_000),
            Category("salud", "Salud", "medical_services", null),
            Expense("cafe", 90_000),
            Expense("comida", 230_000),
            Expense("hogar", 400_000),
            Expense("salud", 10_000)
        )
    )

    /**
     * ADR 0015's category detail: Alimentación 200.000, 20.000 in August, none in
     * September, and 100.000 in October up to the 22nd in weekly steps.
     */
    val CategoryDetail = Scenario(
        id = "prueba-detalle-categoria",
        version = 1,
        seed = 1,
        zone = bogota,
        start = LocalDate.of(2026, 8, 1),
        today = LocalDate.of(2026, 10, 22),
        title = "Detalle de categoría",
        summary = "",
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
            Expense("comida", 5_000, "Café")
        )
    )

    /**
     * RE10: rent on the 5th and a weekly bus from 26 September; Sello opened on 3 October
     * ([UP_TO_THIRD_OCTOBER] steps), then not again until 24 October.
     */
    val WeekAway = Scenario(
        id = "prueba-semana-fuera",
        version = 1,
        seed = 1,
        zone = bogota,
        start = LocalDate.of(2026, 9, 26),
        today = LocalDate.of(2026, 10, 24),
        title = "Una semana fuera",
        summary = "",
        steps = listOf(
            Category("hogar", "Hogar", "home", 1_500_000),
            Category("transporte", "Transporte", "directions_bus", 200_000),
            Schedule("arriendo", Scheduled.Payment("hogar", 950_000, "Arriendo"), Every.Month(5)),
            CatchUp,
            Schedule("bus", Scheduled.Payment("transporte", 20_000, "Bus"), Every.Weeks(1)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 3)),
            CatchUp,
            AdvanceTo(LocalDate.of(2026, 10, 24)),
            CatchUp
        )
    )

    /** The steps of [WeekAway] up to the catch-up of 3 October. */
    const val UP_TO_THIRD_OCTOBER = 8

    val all = listOf(OverspentOctober, CategoryDetail, WeekAway)

    /** A graph at [setup]'s start, ready to load it. */
    fun graph(setup: Scenario) = SandboxGraph(
        SandboxSession(
            active = true,
            date = setup.start,
            zone = setup.zone,
            scenario = setup.key
        ),
        all
    )
}
