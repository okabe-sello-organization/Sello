package com.software.sello.feature.goals

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import java.time.LocalDate

/** The approved mock's Aportar varios receipts, given by hand, for the screen and its images. */
internal object GoalBulkSamples {
    val today: LocalDate = LocalDate.of(2026, 10, 22)

    fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    fun signed(sign: MoneySign, digits: String) = MoneyTextValue(sign, "", digits, "$digits pesos")

    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    private const val VIAJE = "g-viaje"
    private const val CELULAR = "g-celular"

    val goals = listOf(
        GoalBulkChoice(VIAJE, "Viaje a la costa", "flight"),
        GoalBulkChoice(CELULAR, "Celular nuevo", "wifi"),
        GoalBulkChoice("g-casa", "Casa propia", "home")
    )

    /** A line of Viaje a la costa. */
    private fun viaje(
        key: String,
        withdrawal: Boolean?,
        amount: MoneyTextValue?,
        date: LocalDate,
        note: String = "",
        available: MoneyTextValue? = null
    ) = GoalBulkLine(
        key,
        VIAJE,
        "Viaje a la costa",
        withdrawal,
        amount,
        date,
        note,
        available
    )

    val opening = GoalBulkState(
        loading = false,
        goalName = "Viaje a la costa",
        goals = goals,
        lines = listOf(viaje("1", null, null, today)),
        total = plain("0"),
        today = today
    )

    /** +100.000 + 80.000 − 50.000 = +130.000, the retiro highlighted. */
    val three = opening.copy(
        lines = listOf(
            viaje("1", false, plain("100.000"), oct(12)),
            viaje("2", false, plain("80.000"), oct(14), "Regalo de mamá"),
            viaje("3", true, plain("50.000"), oct(16), "Arreglo del carro")
        ),
        highlighted = 2,
        total = signed(MoneySign.Plus, "130.000")
    )

    /** 80.000 without a type: out of the total, +100.000 − 50.000 = +50.000. */
    val missing = three.copy(
        lines = listOf(
            three.lines[0],
            viaje("2", null, plain("80.000"), oct(14), "Regalo de mamá"),
            three.lines[2]
        ),
        highlighted = 1,
        total = signed(MoneySign.Plus, "50.000")
    )

    /** A retiro of 2.000.000 when 1.710.000 + 100.000 + 80.000 = 1.890.000 is available. */
    val refused = three.copy(
        lines = listOf(
            three.lines[0],
            three.lines[1],
            viaje("3", true, plain("2.000.000"), oct(16), available = plain("1.890.000"))
        ),
        total = signed(MoneySign.Minus, "1.820.000"),
        error = GoalBulkError.Refused
    )

    val saving = three.copy(busy = true)

    val unknown = three.copy(error = GoalBulkError.Unknown)

    /** 30 aportes of 20.000 = +600.000. */
    val thirty = opening.copy(
        lines = List(30) { viaje("${it + 1}", false, plain("20.000"), oct(1 + it % 7)) },
        highlighted = 29,
        total = signed(MoneySign.Plus, "600.000")
    )

    /** [count] aportes of 20.000, the last highlighted. */
    fun lines(count: Int) = opening.copy(
        lines = List(count) { viaje("${it + 1}", false, plain("20.000"), oct(1 + it)) },
        highlighted = count - 1,
        total = signed(MoneySign.Plus, "20.000")
    )

    /**
     * Opened from Metas: two goals on one receipt, +100.000 and −50.000 to Viaje a la costa,
     * +200.000 to Celular nuevo, so the net is +250.000; the Celular line highlighted.
     */
    val twoGoals = opening.copy(
        goalName = null,
        lines = listOf(
            viaje("1", false, plain("100.000"), oct(12)),
            GoalBulkLine("2", CELULAR, "Celular nuevo", false, plain("200.000"), oct(14)),
            viaje("3", true, plain("50.000"), oct(16), "Arreglo del carro")
        ),
        highlighted = 1,
        total = signed(MoneySign.Plus, "250.000")
    )

    /** From Metas, a line with an amount and no goal: Falta 1 meta, out of the total. */
    val missingGoal = opening.copy(
        goalName = null,
        lines = listOf(GoalBulkLine("1", null, null, false, plain("80.000"), today)),
        total = plain("0")
    )
}
