package com.software.sello.feature.resumen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.policy.ResumenMonthInput
import com.software.sello.domain.policy.ResumenPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.month.monthNames
import com.software.sello.revealed
import com.software.sello.showSello
import com.software.sello.testing.testDateLabels
import com.software.sello.testing.testMoney
import com.software.sello.testing.testMonthNames
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Gastos hormiga's slip in Resumen (SELLO-194), kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmallExpensesScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val money = testMoney()

    private var next = 100

    private fun spend(category: Int, day: Int, pesos: Long, month: Int = 10) =
        storedExpense(next++, category, "2026-%02d-%02d".format(month, day), pesos)

    /**
     * The mock's October up to the 28th, worked out by the real policy so every slip agrees:
     * Café 14 × 8.000 = 112.000; Transporte 15 × 5.000 + 2 × 6.750 = 88.500; Alimentación
     * 4 × 12.000 + 13.000 = 61.000; Hogar 2 × 20.000 + 10.500 = 50.500: 312.000 in 39 small
     * expenses. With rent of 1.500.000 and a market of 668.000, 2.480.000 spent: 12,58 %,
     * 13 %. September 1 to 28: 28 × 9.000 + 2 × 11.000 = 274.000 small, and 1.800.000 of rent.
     */
    private val october: ResumenState.Ready = run {
        val categories = listOf(
            categoryIn(1, "Café", null, icon = "local_cafe"),
            categoryIn(2, "Transporte", null, icon = "directions_bus"),
            categoryIn(3, "Alimentación", 900_000, icon = "restaurant"),
            categoryIn(4, "Hogar", 1_600_000)
        )
        val expenses = (1..14).map { spend(1, it, 8_000) } +
            (1..15).map { spend(2, it, 5_000) } + (16..17).map { spend(2, it, 6_750) } +
            (1..4).map { spend(3, it * 5, 12_000) } + spend(3, 22, 13_000) +
            (1..2).map { spend(4, it * 3, 20_000) } + spend(4, 9, 10_500) +
            spend(4, 1, 1_500_000) + spend(3, 12, 668_000) +
            (1..28).map { spend(1, it, 9_000, month = 9) } +
            (1..2).map { spend(2, it, 11_000, month = 9) } + spend(4, 1, 1_800_000, month = 9)
        val month = YearMonth.of(2026, 10)
        val snapshot = ResumenPolicy.snapshot(
            month,
            FinancialDay(LocalDate.of(2026, 10, 28), ZoneId.of("America/Bogota")),
            1,
            1,
            ResumenPolicy.monthsEndingIn(month).map { ResumenMonthInput(it, categories, expenses) },
            emptyList()
        ) as Outcome.Success
        ResumenRenderModel(money, testDateLabels(), testMonthNames()).render(
            ReadState.Ready(snapshot.value),
            month,
            PreferenceRead.Ready(SmallExpenseLine.DEFAULT)
        ) as ResumenState.Ready
    }

    private val shown = october.smallExpenses as ResumenSmallExpenses.Shown

    private fun shot(name: String, small: ResumenSmallExpenses, look: Look, reveal: String) {
        rule.showSello(look) {
            ShellScreen(
                octoberShell,
                monthNames(),
                {},
                navigation = appTabs(ShellTab.Resumen),
                entryDock = false
            ) { padding ->
                ResumenScreen(october.copy(smallExpenses = small), {}, {}, {}, {}, padding, {}, {})
            }
        }
        rule.onNodeWithTag(reveal).revealed()
        rule.captureStill("src/test/screenshots/resumen-$name-${look.label}.png")
    }

    @Test fun smallLight() = shot("small", shown, Look(), RESUMEN_SMALL_TAG)

    @Test fun smallDark() = shot("small", shown, Look(dark = true), RESUMEN_SMALL_TAG)

    @Test
    fun smallWithoutMonthBeforeLight() =
        shot("small-no-before", shown.copy(bars = null), Look(), RESUMEN_SMALL_TAG)

    @Test
    fun smallOlderLineLight() =
        shot("small-older", shown.copy(olderLine = true), Look(), RESUMEN_SMALL_TAG)

    @Test
    fun smallUnreadableLight() = shot(
        "small-unreadable",
        ResumenSmallExpenses.Unreadable,
        Look(),
        RESUMEN_SMALL_UNREADABLE_TAG
    )

    @Test
    fun smallUnavailableLight() = shot(
        "small-unavailable",
        ResumenSmallExpenses.Unavailable,
        Look(),
        RESUMEN_SMALL_UNAVAILABLE_TAG
    )
}
