package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SpendingDirection
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.showSello
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResumenScreenTest {
    @get:Rule
    val rule = createComposeRule()
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private val state = mutableStateOf<ResumenState>(resumenExample(money))
    private val actions = mutableListOf<ResumenAction>()
    private var incomeClicks = 0
    private var expenseClicks = 0
    private var category: String? = null
    private var adjusted: String? = null
    private var schedulesClicks = 0

    private fun show(
        ink: SelloInk = SelloInk.Cobalto,
        dark: Boolean = false,
        font: Float = 1f,
        width: Dp? = null,
        height: Dp? = null,
        density: Float? = null
    ) {
        rule.showSello(Look(ink = ink, dark = dark, fontScale = font, density = density)) {
            Box(
                if (width != null && height != null) {
                    Modifier.requiredSize(width, height)
                } else {
                    Modifier.fillMaxSize()
                }.testTag("stage")
            ) {
                ResumenScreen(
                    state.value,
                    { actions += it },
                    { incomeClicks++ },
                    { category = it },
                    { expenseClicks++ },
                    PaddingValues(),
                    { adjusted = it },
                    { schedulesClicks++ }
                )
            }
        }
    }

    private fun figures(ink: SelloInk, dark: Boolean) {
        show(ink, dark)
        // Where the income went first, as receipt lines with exact amounts.
        rule.onNodeWithText("¿A dónde fue lo que entró?").assertIsDisplayed()
        rule.onNodeWithText("Entró en octubre").assertIsDisplayed()
        rule.onNodeWithContentDescription("150.000 pesos").assertIsDisplayed()
        rule.onNode(
            hasTestTag("resumen.category.00000000-0000-4000-8000-000000000001") and
                hasAnyAncestor(hasTestTag("resumen.flow"))
        ).assertTextContains("Comida")
        rule.onNodeWithText("Ingresos menos gastos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("120.000 pesos").assertIsDisplayed()
        rule.onNodeWithContentDescription("Ingresos, 2 en octubre").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Pagos e ingresos fijos").assertIsDisplayed()
        // Allocation is the income screen's: Resumen no longer repeats it.
        rule.onNodeWithText("Sin asignar a categorías").assertDoesNotExist()
        // Nothing projected, and the month's status lives on Recibo.
        rule.onNodeWithText("Proyección de gasto (estimada)").assertDoesNotExist()
        rule.onNodeWithText("Gasto real").assertDoesNotExist()
    }

    @Test
    fun cobaltoLight() = figures(SelloInk.Cobalto, false)

    @Test
    fun cobaltoDark() = figures(SelloInk.Cobalto, true)

    @Test
    fun violetaLight() = figures(SelloInk.Violeta, false)

    @Test
    fun violetaDark() = figures(SelloInk.Violeta, true)

    @Test
    fun chartsSelectSourceIdsAndSourceRowsOpenImplementedHistories() {
        show()
        rule.onNodeWithContentDescription("sábados, 42.700 pesos").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(ResumenAction.SelectWeekday("SATURDAY")), actions) }
        rule.runOnIdle { state.value = resumenExample(money).copy(selectedWeekday = "SATURDAY") }
        rule.onNodeWithTag("resumen.weekdays.selected").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Sábados").assertIsDisplayed()
        rule.onNodeWithTag("resumen.history.chart").performScrollTo()
        rule.onNodeWithContentDescription("oct, 30.000 pesos, mes en curso, hasta hoy")
            .performClick()
        rule.runOnIdle { assertEquals(ResumenAction.SelectHistory("2026-10"), actions.last()) }
        rule.onNodeWithTag("resumen.category.00000000-0000-4000-8000-000000000001")
            .performScrollTo().performClick()
        rule.runOnIdle { assertEquals("00000000-0000-4000-8000-000000000001", category) }
        rule.onNodeWithTag("resumen.openIncome").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, incomeClicks) }
    }

    @Test
    fun readFailureIsNeverAnEmptySuccessAndStaleDataHasARetry() {
        state.value = ResumenState.Failed
        show()
        rule.onNodeWithTag("resumen.failed").assertIsDisplayed()
        rule.onNodeWithTag("resumen.empty").assertDoesNotExist()
        rule.onNodeWithTag("resumen.flow").assertDoesNotExist()
        rule.runOnIdle { state.value = resumenExample(money).copy(stale = true) }
        rule.onNodeWithTag("resumen.stale").assertIsDisplayed()
        rule.onNodeWithText("hasta el 3 de octubre.", substring = true).assertExists()
    }

    @Test
    fun theRecordedUntilLineNamesTheDay() {
        show()
        rule.onNodeWithText("Movimientos anotados hasta el 3 de octubre").assertExists()
    }

    @Test
    fun successfulEmptyHistoryOffersOnlyAWorkingExpenseAction() {
        state.value = resumenExample(money).copy(
            empty = true,
            history = emptyList(),
            categories = emptyList()
        )
        show()
        rule.onNodeWithText("Anotar un gasto").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, expenseClicks) }
        rule.onNodeWithTag("resumen.weekdays").assertDoesNotExist()
        rule.onNodeWithTag("resumen.suggestion").assertDoesNotExist()
    }

    @Test
    fun largeFontKeepsExactSelectedAmountsAndControlsReachable() {
        state.value = resumenExample(money).copy(selectedWeekday = "SATURDAY")
        show(font = 2f)
        rule.onNodeWithTag("resumen.weekdays.selected").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("resumen.openIncome").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, incomeClicks) }
    }

    @Test
    fun shortCompactWidthKeepsSummaryAndChartsInOneColumn() {
        show(width = 500.dp, height = 400.dp)

        val summary = rule.onNodeWithTag("resumen.flow").getUnclippedBoundsInRoot()
        val chart = rule.onNodeWithTag("resumen.weekdays").getUnclippedBoundsInRoot()

        assertTrue(chart.top >= summary.bottom)
    }

    @Test
    fun expandedWidthUsesIndependentSideBySidePanes() {
        show(width = 840.dp, height = 900.dp, density = 1f)

        val summary = rule.onNodeWithTag("resumen.flow").getUnclippedBoundsInRoot()
        val chart = rule.onNodeWithTag("resumen.history").getUnclippedBoundsInRoot()

        assertTrue(chart.left > summary.left)
    }

    @Test
    fun largeSignedFiguresAndSelectedChartValuesRemainExact() {
        val huge = money.format(Money.cop(9_007_199_254_740_993))
        val original = resumenExample(money)
        val point = original.history.last().copy(
            chart = original.history.last().chart.copy(
                value = 9_007_199_254_740_993,
                spoken = "oct, ${huge.spoken}"
            ),
            amount = huge,
            detail = huge
        )
        state.value = original.copy(
            income = money.format(Money.cop(0)),
            spent = huge,
            netFlow = money.format(Money.cop(-9_007_199_254_740_993)),
            history = listOf(point),
            selectedHistory = point.chart.id
        )
        show(font = 2f)
        rule.onNodeWithContentDescription("menos 9.007.199.254.740.993 pesos")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("resumen.history.selected").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("resumen.history.chart").performScrollTo()
        rule.onNodeWithContentDescription(
            "oct, 9.007.199.254.740.993 pesos, mes en curso, hasta hoy"
        ).assertIsDisplayed()
    }

    @Test
    fun theSlipsComeInTheV2Order() {
        show(width = 360.dp, height = 3000.dp, density = 1f)

        val tops = listOf(
            "resumen.hero",
            "resumen.flow",
            "resumen.history",
            "resumen.weekdays",
            "resumen.suggestion"
        ).map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }

        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun theMonthInProgressIsMarkedAndExplainedAndTheLimitIsOneLine() {
        show()

        rule.onNodeWithTag("resumen.history.chart").performScrollTo()
        rule.onNodeWithText("oct*", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("oct, 30.000 pesos, mes en curso, hasta hoy")
            .assertExists()
        rule.onNodeWithText("* Mes en curso: lo gastado hasta hoy.").performScrollTo()
            .assertIsDisplayed()
        // Short on the line; exact for a screen reader.
        rule.onNodeWithText("Límite 100 mil").assertExists()
        rule.onNodeWithContentDescription("Límite 100.000 pesos").assertExists()
    }

    @Test
    fun whereTheMoneyGoesIsRankedInsideTheIncomeSlipWithItsTotal() {
        show()

        // One slip: "¿Dónde se va?" has none of its own any more.
        rule.onNodeWithText("¿Dónde se va?").assertDoesNotExist()
        rule.onNodeWithText("Lo gastado en octubre, hasta el 3").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("resumen.category.00000000-0000-4000-8000-000000000001")
            .performScrollTo().assertTextContains("Comida").performClick()
        rule.onNodeWithTag("resumen.spent").performScrollTo()
            .assertTextContains("Gastos").assertContentDescriptionContains("30.000 pesos")

        rule.runOnIdle { assertEquals("00000000-0000-4000-8000-000000000001", category) }
    }

    // --- SELLO-123: where the income went ---

    private fun row(number: Int, name: String, icon: String, pesos: Long, share: Float) =
        ResumenCategory(
            "00000000-0000-4000-8000-%012d".format(number),
            name,
            icon,
            money.format(Money.cop(pesos)),
            share
        )

    private fun spending(
        income: Long,
        spent: Long,
        count: Int = 3,
        next: ResumenNextFixed = ResumenNextFixed.Unknown
    ) = example().copy(
        income = money.format(Money.cop(income)),
        spent = money.format(Money.cop(spent)),
        netFlow = money.format(Money.cop(income - spent)),
        categories = listOf(
            row(1, "Hogar", "home", 200_000, 0.5f),
            row(2, "Comida", "restaurant", 80_000, 0.2f),
            row(3, "Café", "local_cafe", 60_000, 0.15f),
            row(4, "Transporte", "directions_bus", 40_000, 0.1f),
            row(5, "Ocio", "theaters", 20_000, 0.05f)
        ),
        incomeSlip = ResumenIncome(
            count,
            if (income == 0L) 0f else minOf(1f, spent.toFloat() / income),
            spent > income
        ),
        nextFixed = next
    )

    private val today = LocalDate.parse("2026-10-22")

    @Test
    fun theIncomeSlipRanksEveryCategoryAndItsStubOpensBothScreens() {
        state.value = spending(
            2_000_000,
            400_000,
            next = ResumenNextFixed.On(LocalDate.parse("2026-10-28"), today)
        )
        show()

        rule.onNodeWithContentDescription("2.000.000 pesos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("resumen.income.bar").performScrollTo().assertContentDescriptionEquals(
            "Gastos: 400.000 pesos. Ingresos menos gastos: 1.600.000 pesos"
        )
        // Every category, in the slip, as the ranking shows it.
        listOf("Hogar", "Comida", "Café", "Transporte", "Ocio").forEachIndexed { index, name ->
            rule.onNode(
                hasTestTag("resumen.category.00000000-0000-4000-8000-%012d".format(index + 1)) and
                    hasAnyAncestor(hasTestTag("resumen.flow"))
            ).performScrollTo().assertTextContains(name)
        }
        rule.onNodeWithTag("resumen.category.00000000-0000-4000-8000-000000000002").performClick()
        rule.runOnIdle { assertEquals("00000000-0000-4000-8000-000000000002", category) }
        rule.onNodeWithTag("resumen.spent").performScrollTo()
            .assertContentDescriptionContains("400.000 pesos")
        rule.onNodeWithContentDescription("1.600.000 pesos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("resumen.openSchedules").performScrollTo()
            .assertContentDescriptionEquals("Pagos e ingresos fijos, Próximo: 28 oct")
            .performClick()
        rule.runOnIdle { assertEquals(1, schedulesClicks) }
        rule.onNodeWithTag("resumen.openIncome").performScrollTo()
            .assertContentDescriptionEquals("Ingresos, 3 en octubre")
            .performClick()
        rule.runOnIdle { assertEquals(1, incomeClicks) }
    }

    @Test
    fun theFixedHalfSaysTodayTomorrowOrNoneAndNothingWhenUnknown() {
        state.value = spending(2_000_000, 400_000, next = ResumenNextFixed.On(today, today))
        show()
        val fixed = rule.onNodeWithTag("resumen.openSchedules")
        fixed.performScrollTo()
            .assertContentDescriptionEquals("Pagos e ingresos fijos, Próximo: hoy")
        rule.runOnIdle {
            state.value = (state.value as ResumenState.Ready)
                .copy(nextFixed = ResumenNextFixed.On(today.plusDays(1), today))
        }
        fixed.assertContentDescriptionEquals("Pagos e ingresos fijos, Próximo: mañana")
        rule.runOnIdle {
            state.value =
                (state.value as ResumenState.Ready).copy(nextFixed = ResumenNextFixed.None)
        }
        fixed.assertContentDescriptionEquals("Pagos e ingresos fijos, Ninguno activo")
        rule.runOnIdle {
            state.value =
                (state.value as ResumenState.Ready).copy(nextFixed = ResumenNextFixed.Unknown)
        }
        fixed.assertContentDescriptionEquals("Pagos e ingresos fijos")
    }

    @Test
    fun spendingAboveTheIncomeIsSignedAndRedWithTheTwoFactsBehindIt() {
        state.value = spending(1_000_000, 1_120_000, count = 1)
        show()

        rule.onNodeWithContentDescription("menos 120.000 pesos").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Anotaste 1.000.000 de ingresos y 1.120.000 de gastos.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withNoIncomeTheSlipIsTheRankingAndItsTotalWithoutANegativeFigure() {
        state.value = spending(0, 400_000, count = 0)
        show()

        rule.onNodeWithText("Aún no anotas ingresos en octubre.").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("resumen.netFlow").assertDoesNotExist()
        rule.onNodeWithTag("resumen.income.bar").assertDoesNotExist()
        rule.onNodeWithContentDescription("menos 400.000 pesos").assertDoesNotExist()
        rule.onNodeWithTag("resumen.category.00000000-0000-4000-8000-000000000001")
            .performScrollTo().assertTextContains("Hogar")
        rule.onNodeWithTag("resumen.spent").performScrollTo()
            .assertTextContains("Total").assertContentDescriptionContains("400.000 pesos")
        rule.onNodeWithTag("resumen.openIncome").performScrollTo()
            .assertContentDescriptionEquals("Ingresos, Ninguno en octubre")
    }

    // --- The comparison, a normal month, weekdays and the suggestion ---

    private fun example() = resumenExample(money)

    private fun comparison() = example().hero.comparison!!

    @Test
    fun theMonthInProgressLeadsWithTheSameDaysComparison() {
        show()

        rule.onNodeWithText("¿Cómo va este mes?").assertIsDisplayed()
        rule.onNodeWithText("menos que en septiembre").assertIsDisplayed()
        rule.onNodeWithText("↓ 14,3 % al mismo día del mes").assertIsDisplayed()
        rule.onNodeWithText("Del 1 al 3 de cada mes").assertIsDisplayed()
        rule.onNodeWithText("Octubre").assertIsDisplayed()
        rule.onNodeWithText("Septiembre").assertIsDisplayed()
        rule.onNodeWithContentDescription("35.000 pesos").assertIsDisplayed()
    }

    @Test
    fun aShorterMonthBeforeNamesBothRanges() {
        state.value = example().copy(
            hero = example().hero.copy(
                throughDay = 31,
                monthName = "marzo",
                comparison = comparison().copy(
                    previousName = "febrero",
                    throughDay = 31,
                    previousThroughDay = 28
                )
            )
        )
        show()

        rule.onNodeWithText("Del 1 al 31 de marzo · Del 1 al 28 de febrero").assertIsDisplayed()
    }

    @Test
    fun aFinishedMonthComparesWholeMonths() {
        state.value = example().copy(
            hero = example().hero.copy(
                inProgress = false,
                monthName = "septiembre",
                comparison = comparison().copy(
                    previousName = "agosto",
                    direction = SpendingDirection.More,
                    percent = "21,1",
                    throughDay = null,
                    previousThroughDay = null
                )
            )
        )
        show()

        rule.onNodeWithText("¿Cómo cerró septiembre?").assertIsDisplayed()
        rule.onNodeWithText("más que en agosto").assertIsDisplayed()
        rule.onNodeWithText("↑ 21,1 % que agosto completo").assertIsDisplayed()
        rule.onNodeWithText("Meses completos").assertIsDisplayed()
    }

    @Test
    fun againstNothingSpentThereIsNoPercent() {
        state.value = example().copy(
            hero = example().hero.copy(
                comparison = comparison().copy(direction = SpendingDirection.More, percent = null)
            )
        )
        show()

        rule.onNodeWithText("más que en septiembre").assertIsDisplayed()
        rule.onNodeWithText("%", substring = true).assertDoesNotExist()
    }

    @Test
    fun theFirstMonthShowsWhatWasSpentAndWhatComparingNeeds() {
        state.value = example().copy(
            hero = example().hero.copy(comparison = null),
            insufficientHistory = true
        )
        show()

        rule.onNodeWithText("¿Cómo va este mes?").assertIsDisplayed()
        rule.onNodeWithText("gastados del 1 al 3").assertIsDisplayed()
        rule.onNodeWithText("Cuando completes otro mes podrás comparar los mismos días.")
            .assertIsDisplayed()
        rule.onNodeWithText("menos que en septiembre").assertDoesNotExist()
    }

    @Test
    fun aNormalMonthIsGivenAboveTheColumns() {
        show()

        rule.onNodeWithText("Un mes normal").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Promedio de 1 mes completo").performScrollTo().assertIsDisplayed()
        val normal = rule.onNodeWithTag("resumen.normal").getUnclippedBoundsInRoot()
        val chart = rule.onNodeWithTag("resumen.history.chart").getUnclippedBoundsInRoot()
        assertTrue(normal.bottom <= chart.top)
    }

    @Test
    fun theBusiestDayIsTheAnswerAndEveryDayIsExact() {
        show()

        rule.onNodeWithTag(
            "resumen.weekdays.answer"
        ).performScrollTo().assertTextContains("Los sábados")
        rule.onNodeWithText("42.700 en promedio · últimos 6 meses").performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("lunes, 18.200 pesos").assertExists()
    }

    @Test
    fun withLessThanSixMonthsRecordedTheWeekdaysSayWhenTheyStart() {
        state.value = example().copy(weekdays = example().weekdays!!.copy(lastSixMonths = false))
        show()

        rule.onNodeWithText("42.700 en promedio · desde el 1 de julio").performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun withNoBusiestDayThereIsNoAnswer() {
        state.value = example().copy(
            weekdays = example().weekdays!!.copy(busiest = null, busiestAverage = null)
        )
        show()

        rule.onNodeWithTag("resumen.weekdays").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("resumen.weekdays.answer").assertDoesNotExist()
    }

    @Test
    fun theSuggestionSaysWhyOffersTheAdjustmentAndCanBeDismissed() {
        show()

        rule.onNodeWithText(
            "Comida superó su límite en agosto y septiembre: gastaste 74.000 en promedio " +
                "y su límite es 50.000."
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Podrías ajustar su límite para que refleje lo que de verdad gastas.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Ajustar el presupuesto de Comida").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("00000000-0000-4000-8000-000000000001", adjusted) }
        rule.onNodeWithContentDescription("Descartar sugerencia").performClick()
        rule.runOnIdle { assertEquals(ResumenAction.DismissSuggestion, actions.last()) }
    }

    // --- Layout against the v2 design ---

    @Test
    fun slipsUseOnlyTheScaffoldsSideMarginsAsOnRecibo() {
        show(width = 360.dp, height = 3000.dp, density = 1f)

        val hero = rule.onNodeWithTag("resumen.hero").getUnclippedBoundsInRoot()
        val window = rule.onNodeWithTag("stage").getUnclippedBoundsInRoot()

        // PaddingValues() here: nothing is added on either side.
        assertEquals(window.left, hero.left)
        assertEquals(window.right, hero.right)
    }

    @Test
    fun eachComparisonBarIsOneLineWithTheAmountLast() {
        show(width = 360.dp, height = 3000.dp, density = 1f)

        val month = rule.onNodeWithText("Octubre").getUnclippedBoundsInRoot()
        val before = rule.onNodeWithText("Septiembre").getUnclippedBoundsInRoot()
        val amount = rule.onNode(
            hasContentDescription("35.000 pesos") and hasAnyAncestor(hasTestTag("resumen.hero")),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()

        // Same line as its month, at the far right; the next month is below.
        assertTrue(amount.top < before.bottom && amount.bottom > before.top)
        assertTrue(amount.left > before.right + 100.dp)
        assertTrue(before.top >= month.bottom)
    }

    @Test
    fun aRankedAmountIsCentredOnTheNameAndItsBar() {
        show(width = 360.dp, height = 3000.dp, density = 1f)

        val row = rule.onNodeWithTag("resumen.category.00000000-0000-4000-8000-000000000001")
            .getUnclippedBoundsInRoot()
        val amount = rule.onNode(
            hasContentDescription("30.000 pesos") and
                hasAnyAncestor(hasTestTag("resumen.category.00000000-0000-4000-8000-000000000001")),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()

        val rowMiddle = (row.top + row.bottom) / 2
        val amountMiddle = (amount.top + amount.bottom) / 2
        assertTrue(abs((rowMiddle - amountMiddle).value) <= 1f)
    }

    @Test
    fun theHeroSaysWhatTheDifferenceMeansAtTheDesignsSize() {
        show()

        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("menos que en septiembre")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(16.sp, layouts.single().layoutInput.style.fontSize)
    }
}
