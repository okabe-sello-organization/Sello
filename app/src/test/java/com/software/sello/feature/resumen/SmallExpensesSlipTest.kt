package com.software.sello.feature.resumen

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.Money
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gastos hormiga's slip on the JVM (ADR 0022), given state by hand. */
@RunWith(AndroidJUnit4::class)
class SmallExpensesSlipTest {
    @get:Rule
    val rule = createComposeRule()

    private val money = testMoney()

    private fun pesos(amount: Long) = money.format(Money.cop(amount))

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
            ResumenSmallCategory("bus", "Transporte", "directions_bus", 1, pesos(8_500))
        )
    )

    private val inProgress = ResumenHero(true, "octubre", pesos(2_480_000), 28, null)
    private var retried = 0

    private fun show(small: ResumenSmallExpenses, hero: ResumenHero = inProgress) =
        rule.showSello { SmallExpensesSlip(small, hero) { retried++ } }

    @Test
    fun theSlipSaysTheTotalTheShareTheDaysAndWhereTheyGather() {
        show(shown)

        rule.onNodeWithText("Gastos hormiga").assertIsDisplayed()
        rule.onNodeWithText("20.000 o menos").assertIsDisplayed()
            .assertContentDescriptionEquals("20.000 pesos o menos")
        rule.onNodeWithText("en 38 gastos pequeños, del 1 al 28 · 13 % de lo que gastaste")
            .assertIsDisplayed()
        rule.onNodeWithText("Del 1 al 28 de cada mes").assertIsDisplayed()
        rule.onNodeWithText("Donde más se juntan").assertIsDisplayed()
        rule.onNodeWithText("14 gastos").assertIsDisplayed()
        rule.onNodeWithText("1 gasto").assertIsDisplayed()
    }

    @Test
    fun aFinishedMonthSaysItsNameAndWholeMonths() {
        show(
            shown.copy(throughDay = null, previousThroughDay = null),
            inProgress.copy(inProgress = false, throughDay = 31)
        )

        rule.onNodeWithText("en 38 gastos pequeños en octubre · 13 % de lo que gastaste")
            .assertIsDisplayed()
        rule.onNodeWithText("Meses completos").assertIsDisplayed()
    }

    @Test
    fun withoutTheMonthBeforeThereAreNoBars() {
        show(shown.copy(bars = null))

        assertEquals(
            0,
            rule.onAllNodes(hasTestTag(RESUMEN_SMALL_BARS_TAG)).fetchSemanticsNodes().size
        )
    }

    @Test
    fun anOlderLineIsSaidAndReintentarAsksAgain() {
        show(shown.copy(olderLine = true))

        rule.onNodeWithText(
            "No pudimos leer tu línea de gastos hormiga de nuevo. Usamos la última: hasta 20.000."
        ).assertIsDisplayed()
        rule.onNodeWithTag(RESUMEN_SMALL_TAG).assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(1, retried)
    }

    @Test
    fun aLineNeverReadIsSaidInPlaceOfTheSlip() {
        show(ResumenSmallExpenses.Unreadable)

        rule.onNodeWithTag(RESUMEN_SMALL_UNREADABLE_TAG).assertIsDisplayed()
        rule.onNodeWithText("No pudimos leer tus gastos hormiga").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(1, retried)
    }

    @Test
    fun smallExpensesThatCannotBeAddedUpOfferNoReintentar() {
        show(ResumenSmallExpenses.Unavailable)

        rule.onNodeWithText("No pudimos sumar tus gastos hormiga").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasText("Reintentar")).fetchSemanticsNodes().size)
    }
}
