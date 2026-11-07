package com.software.sello.feature.resumen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.domain.model.Money
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.month.monthNames
import com.software.sello.revealed
import com.software.sello.showSello
import com.software.sello.testing.testMoney
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

    private fun pesos(amount: Long) = money.format(Money.cop(amount))

    /** The mock's October up to the 28th: 2.480.000 spent, nothing to compare in the hero. */
    private val october = resumenExample(money).copy(
        hero = ResumenHero(true, "octubre", pesos(2_480_000), 28, null)
    )

    /** 312.000 in 38 small expenses, 13 % of 2.480.000; September's same days: 274.000. */
    private val shown = ResumenSmallExpenses.Shown(
        line = "20.000",
        total = pesos(312_000),
        count = 38,
        sharePercent = 13,
        previousName = "septiembre",
        bars = listOf(
            ComparisonBar("Octubre", pesos(312_000), 1f, current = true),
            ComparisonBar("Septiembre", pesos(274_000), 274f / 312f, current = false)
        ),
        throughDay = 28,
        previousThroughDay = 28,
        categories = listOf(
            ResumenSmallCategory("cafe", "Café", "local_cafe", 14, pesos(112_000)),
            ResumenSmallCategory("bus", "Transporte", "directions_bus", 17, pesos(88_500)),
            ResumenSmallCategory("food", "Alimentación", "restaurant", 5, pesos(61_000))
        )
    )

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
        shot("small-older", shown.copy(olderLine = true), Look(), RESUMEN_SMALL_OLDER_TAG)

    @Test
    fun smallUnreadableLight() = shot(
        "small-unreadable",
        ResumenSmallExpenses.Unreadable,
        Look(),
        RESUMEN_SMALL_UNREADABLE_TAG
    )
}
