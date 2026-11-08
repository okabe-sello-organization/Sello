package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.presentation.carry.carryMonthOf
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every **?** and underlined word on Recibo, Pagos previstos and category detail opens its
 * own explanation (SELLO-207), and the explanation leads to the Guía.
 */
@RunWith(AndroidJUnit4::class)
class ReciboHelpScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val ene = YearMonth.of(2027, 1)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    /** Comida carries from ene; 22 feb 2027 (docs/testing/signed-carryover-examples.md SC13). */
    private val carrying = ReciboRenderModel(testMoney()).recibo(
        ReadState.Ready(
            carryMonthOf(
                "2027-02-22",
                listOf(categoryIn(1, "Comida", 100_000), categoryIn(2, "Transporte", 50_000)),
                listOf(
                    storedExpense(100, 1, "2027-01-10", 20_000),
                    storedExpense(101, 1, "2027-02-05", 30_000)
                ),
                mapOf(1 to ene)
            )
        )
    ) as ReciboState.Month

    private fun showRecibo(state: ReciboState, actions: MutableList<ReciboAction>) {
        rule.showSello(Look()) {
            ReciboScreen(state, { actions += it }, PaddingValues(12.dp))
        }
    }

    @Test
    fun reciboOpensTheSummaryPerDayBaseAndCarryExplanations() {
        val actions = mutableListOf<ReciboAction>()
        showRecibo(carrying, actions)

        rule.onNodeWithTag(RECIBO_SUMMARY_HELP_TAG).performClick()
        rule.onNodeWithTag(RECIBO_PER_DAY_HELP_TAG).performClick()
        rule.onNodeWithContentDescription("base. Qué significa").performClick()
        rule.onNodeWithContentDescription("sobrante. Qué significa").performClick()

        assertEquals(
            listOf(ReciboHelp.Summary, ReciboHelp.PerDay, ReciboHelp.Base, ReciboHelp.Carry)
                .map { ReciboAction.ShowHelp(it) },
            actions
        )
    }

    @Test
    fun theOpenSheetSaysItsTopicAndLeadsToTheGuide() {
        val actions = mutableListOf<ReciboAction>()
        showRecibo(carrying.copy(help = ReciboHelp.Summary), actions)

        rule.onNodeWithTag(reciboHelpTag(ReciboHelp.Summary)).assertIsDisplayed()
        rule.onNodeWithText("Ver más en la Guía de Sello").performScrollTo().performClick()

        assertEquals(listOf<ReciboAction>(ReciboAction.OpenHelpGuide), actions)
    }

    private val upcoming = UpcomingState.Ready(
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 21),
        total = plain("200.000"),
        payments = listOf(
            UpcomingPayment(
                "1",
                "Arriendo",
                "Casa",
                "home",
                LocalDate.of(2026, 10, 25),
                plain("200.000")
            )
        )
    )

    @Test
    fun pagosPrevistosOpensWhatIsSetAside() {
        val opened = mutableListOf<Boolean>()
        rule.showSello(Look()) {
            UpcomingPaymentsScreen(upcoming, PaddingValues(12.dp), {}, {}, {}, onHelp = {
                opened +=
                    it
            })
        }

        rule.onNodeWithTag(UPCOMING_HELP_TAG).performClick()

        assertEquals(listOf(true), opened)
    }

    @Test
    fun pagosPrevistosShowsItsOpenSheet() {
        rule.showSello(Look()) {
            UpcomingPaymentsScreen(upcoming.copy(help = true), PaddingValues(12.dp), {}, {}, {})
        }

        rule.onNodeWithTag(reciboHelpTag(ReciboHelp.Upcoming)).assertIsDisplayed()
    }

    private val carry = CarryFigures(
        base = plain("150.000"),
        carry = MoneyTextValue(MoneySign.Plus, "", "80.000", "más 80.000 pesos"),
        carryMagnitude = plain("80.000"),
        direction = CarryDirection.Surplus,
        capacity = plain("230.000"),
        spent = plain("100.000"),
        chainStart = YearMonth.of(2026, 9),
        source = YearMonth.of(2026, 9)
    )

    private val detail = CategoryDetailState.Ready(
        category = ReciboCategory(
            id = "a",
            name = "Comida",
            iconKey = "restaurant",
            archived = false,
            figure = BudgetFigure(ReciboHeadline.Remaining, plain("130.000"), ReciboStamp.OnTrack),
            cellAmount = plain("130.000"),
            spent = plain("100.000"),
            limit = plain("230.000"),
            fraction = 0.43f,
            over = false,
            expenseCount = 2,
            carry = carry
        ),
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 24),
        stale = false,
        canRecord = true,
        expenses = ExpenseListState.Empty,
        insights = DetailInsightsState.Ready(
            DetailInsights(
                allowance = ReciboPerDay(plain("18.550"), 7),
                comparisonMonth = null,
                comparison = null,
                comparisonDirection = DetailComparisonDirection.Equal,
                elapsedDays = 24,
                averageExpense = null,
                largest = null,
                weekday = DayOfWeek.MONDAY,
                weeklyAverage = null,
                weeks = emptyList(),
                historicalAverage = plain("120.000"),
                history = listOf(
                    DetailHistoryMonth(
                        YearMonth.of(2026, 8),
                        plain("100.000"),
                        100_000,
                        plain("150.000"),
                        150_000
                    ),
                    DetailHistoryMonth(
                        YearMonth.of(2026, 9),
                        plain("140.000"),
                        140_000,
                        plain("150.000"),
                        150_000
                    )
                )
            )
        ),
        fixed = DetailFixedState.Ready(
            upcoming = listOf(
                DetailUpcomingPayment("1", "Mercado", LocalDate.of(2026, 10, 28), plain("50.000"))
            ),
            later = emptyList()
        )
    )

    @Test
    fun categoryDetailOpensEachOfItsExplanations() {
        val actions = mutableListOf<CategoryDetailAction>()
        rule.showSello(Look()) {
            CategoryDetailScreen(
                state = detail,
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = { actions += it },
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp)
            )
        }

        rule.onNodeWithContentDescription("Límite base. Qué significa").performScrollTo()
            .performClick()
        rule.onNodeWithContentDescription("Sobrante. Qué significa").performScrollTo()
            .performClick()
        rule.onNodeWithTag(CATEGORY_DETAIL_PER_DAY_HELP_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(CATEGORY_DETAIL_FIXED_HELP_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(CATEGORY_DETAIL_HISTORY_HELP_TAG).performScrollTo().performClick()

        assertEquals(
            listOf(
                ReciboHelp.Base,
                ReciboHelp.Carry,
                ReciboHelp.DayAllowance,
                ReciboHelp.FixedPayments,
                ReciboHelp.History
            ).map { CategoryDetailAction.ShowHelp(it) },
            actions
        )
    }

    @Test
    fun anUnderlinedLabelStillNamesItsFigureForAScreenReader() {
        rule.showSello(Look()) {
            CategoryDetailScreen(
                state = detail,
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = {},
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp)
            )
        }

        // The pair and the line are read "label, value"; the underline is its own button.
        listOf(
            listOf("Al día", "18.550 pesos"),
            listOf("Límite base", "150.000 pesos"),
            listOf("Sobrante", "más 80.000 pesos")
        ).forEach { spoken ->
            rule.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, spoken)
            ).assertExists()
        }
    }

    @Test
    fun categoryDetailClosesItsSheetBeforeOpeningTheGuide() {
        val actions = mutableListOf<CategoryDetailAction>()
        var guide = 0
        rule.showSello(Look()) {
            CategoryDetailScreen(
                state = detail.copy(help = ReciboHelp.DayAllowance),
                months = monthNames(),
                dates = dateLabels(),
                showName = false,
                onAction = { actions += it },
                onRecordHere = {},
                contentPadding = PaddingValues(12.dp),
                onOpenGuide = { guide++ }
            )
        }

        rule.onNodeWithText("Ver más en la Guía de Sello").performScrollTo().performClick()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.CloseHelp), actions)
        assertEquals(1, guide)
    }
}
