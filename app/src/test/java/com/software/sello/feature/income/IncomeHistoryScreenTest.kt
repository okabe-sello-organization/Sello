package com.software.sello.feature.income

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The month's income as drawn from state given by hand: what came in, where it went, each one. */
@RunWith(AndroidJUnit4::class)
class IncomeHistoryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.of(2026, 10, 22)
    private val actions = mutableListOf<IncomeHistoryAction>()
    private var created = 0
    private val edited = mutableListOf<String>()
    private val assigned = mutableListOf<String?>()

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val salary = IncomeLine("i1", pesos("150.000"), "salary", null, today, "Quincena")

    private fun ready(lines: List<IncomeLine> = listOf(salary), more: Boolean = false) =
        IncomeHistoryState.Ready(october, pesos("150.000"), lines, more)

    private val split = IncomeSplit(
        income = pesos("150.000"),
        allocated = pesos("100.000"),
        unallocated = pesos("50.000"),
        over = false,
        allocatedShare = 2f / 3f
    )

    /** BG-E08: 2.000.000 recorded, a salary of 1.000.000 expected on 30 October. */
    private val plan = IncomePlanView(
        recorded = pesos("2.000.000"),
        expected = pesos("1.000.000"),
        planned = pesos("3.000.000"),
        unassigned = pesos("200.000"),
        over = false,
        items = listOf(
            ExpectedLine(
                "s1.2026-10-30.1",
                "salary",
                null,
                LocalDate.of(2026, 10, 30),
                pesos("1.000.000")
            )
        )
    )

    private fun show(
        state: IncomeHistoryState,
        split: IncomeSplit?,
        plan: IncomePlanState = IncomePlanState.None,
        assign: AssignSuggestion? = null
    ) {
        rule.showSello(Look()) {
            IncomeHistoryScreen(
                state,
                split,
                plan,
                assign,
                today,
                { actions += it },
                { created++ },
                { assigned += it },
                { edited += it },
                PaddingValues(12.dp)
            )
        }
    }

    @Test
    fun theHeroSaysWhatCameInAndWhereItWentInExactAmounts() {
        show(ready(), split)

        rule.onNodeWithText("Entró en octubre").assertIsDisplayed()
        rule.onNodeWithTag("income.split").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(
                    "En presupuestos de categorías: 100.000 pesos. Sin asignar: 50.000 pesos"
                )
            )
        )
        rule.onNodeWithText("En presupuestos de categorías").assertIsDisplayed()
        rule.onNodeWithText("Sin asignar").assertIsDisplayed()
        rule.onNodeWithTag("income.overBasis").assertDoesNotExist()
        // Nothing is expected: no plan. And no suggestion here.
        rule.onNodeWithTag("income.plan").assertDoesNotExist()
        rule.onNodeWithText("Una sugerencia").assertDoesNotExist()
    }

    // --- Asignar a una categoría (SELLO-083) ---

    private val offer = AssignSuggestion(pesos("50.000"), "50000")

    @Test
    fun unassignedIncomeOffersACategoryAndTheActionOpensItWithTheLimit() {
        show(ready(), split, assign = offer)

        rule.onNodeWithTag("income.assign").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Una sugerencia").assertIsDisplayed()
        rule.onNodeWithText("50.000 de lo que entró aún no tiene un presupuesto.")
            .assertIsDisplayed()
        rule.onNodeWithText(
            "Podrías darle un destino: ahorro, ocio o una reserva, con su propia categoría."
        ).assertIsDisplayed()
        // The amount is read in pesos, as everywhere else.
        rule.onNodeWithContentDescription(
            "50.000 pesos de lo que entró aún no tiene un presupuesto."
        ).assertIsDisplayed()
        // The action only asks for the form: nothing is assigned or saved by it.
        assertEquals(emptyList<String?>(), assigned)
        rule.onNodeWithText("Asignar a una categoría ›").performScrollTo().performClick()

        assertEquals(listOf<String?>("50000"), assigned)
        assertEquals(emptyList<IncomeHistoryAction>(), actions)
        assertEquals(0, created)
    }

    @Test
    fun anAmountThatCannotBeALimitStillOffersTheCategoryWithAnEmptyLimit() {
        show(
            ready(),
            split,
            assign = AssignSuggestion(pesos("1.000.000.000.000"), limit = null)
        )

        rule.onNodeWithText("1.000.000.000.000 de lo que entró aún no tiene un presupuesto.")
            .assertIsDisplayed()
        rule.onNodeWithText("Asignar a una categoría ›").performScrollTo().performClick()

        assertEquals(listOf<String?>(null), assigned)
    }

    @Test
    fun withoutAnOfferThereIsNoSuggestion() {
        // No offer: a closed month, nothing or a negative amount unassigned, a failed read.
        show(ready(), split, assign = null)

        rule.onNodeWithTag("income.assign").assertDoesNotExist()
        rule.onNodeWithText("Asignar a una categoría ›").assertDoesNotExist()
    }

    @Test
    fun theSuggestionIsNotDrawnWhileLoading() {
        show(IncomeHistoryState.Loading, split = null, assign = offer)
        rule.onNodeWithTag("income.assign").assertDoesNotExist()
    }

    @Test
    fun aFailedReadDrawsNoSuggestion() {
        show(IncomeHistoryState.Failed, split = null, assign = offer)

        rule.onNodeWithTag("income.assign").assertDoesNotExist()
        rule.onNodeWithText("Asignar a una categoría ›").assertDoesNotExist()
    }

    @Test
    fun theSuggestionHasNoWayToBeDismissedBecauseNothingRemembersIt() {
        show(ready(), split, IncomePlanState.Shown(plan), assign = offer)

        rule.onNodeWithTag("income.assign").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Descartar sugerencia").assertDoesNotExist()
    }

    @Test
    fun moreBudgetedThanCameInIsSignedAndSaysTheTwoFactsBehindIt() {
        show(
            ready(),
            split.copy(
                income = pesos("60.000"),
                unallocated = minus("40.000"),
                over = true,
                allocatedShare = 1f
            )
        )

        rule.onNodeWithContentDescription("menos 40.000 pesos").assertIsDisplayed()
        rule.onNodeWithText("Tus presupuestos suman 100.000; anotaste 60.000.").assertIsDisplayed()
        rule.onNodeWithTag("income.split").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(
                    "En presupuestos de categorías: 100.000 pesos. Sin asignar: menos 40.000 pesos"
                )
            )
        )
    }

    @Test
    fun withoutTheMonthsSummaryOnlyTheTotalIsShownNeverAZeroSplit() {
        show(ready(), split = null)

        rule.onNodeWithText("Entró en octubre").assertIsDisplayed()
        rule.onNodeWithTag("income.split").assertDoesNotExist()
        rule.onNodeWithText("Sin asignar").assertDoesNotExist()
        rule.onNodeWithTag("income.line.i1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun eachIncomeIsOneRowThatOpensItForEditingAndTheListEndsWithItsTotal() {
        show(ready(), split)

        rule.onNodeWithText("Lo que entró").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Hoy, jue 22 oct · Quincena").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Total").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("income.line.i1").performScrollTo().performClick()

        assertEquals(listOf("i1"), edited)
    }

    @Test
    fun moreRowsArePagedAndAFailedPageCanBeAskedForAgain() {
        show(ready(more = true).copy(pageFailed = true), split)

        rule.onNodeWithText("Reintentar").performScrollTo().performClick()
        rule.onNodeWithTag("income.more").performScrollTo().performClick()

        assertEquals(listOf(IncomeHistoryAction.More, IncomeHistoryAction.More), actions)
    }

    @Test
    fun noIncomeIsTheEmptyStateWithAWayToRecordOne() {
        show(ready(lines = emptyList()).copy(total = pesos("0")), split = null)

        rule.onNodeWithText("Aún no anotas ingresos en octubre").assertIsDisplayed()
        rule.onNodeWithText(
            "Sello funciona sin ingresos. Si los anotas, verás cuánto tiene un presupuesto y cuánto no."
        ).assertIsDisplayed()
        rule.onNodeWithTag("income.hero").assertDoesNotExist()
        rule.onNodeWithText("Anotar ingreso").performClick()

        assertEquals(1, created)
    }

    @Test
    fun aFailedReadSaysSoAndNeverShowsZero() {
        show(IncomeHistoryState.Failed, split = null)

        rule.onNodeWithText("No pudimos leer tus ingresos").assertIsDisplayed()
        rule.onNodeWithContentDescription("0 pesos").assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<IncomeHistoryAction>(IncomeHistoryAction.Retry), actions)
    }

    // --- Plan del mes ---

    @Test
    fun bgE08thePlanAddsTheExpectedSalaryApartAndWhatCameInStaysRecordedOnly() {
        show(
            ready().copy(total = pesos("2.000.000")),
            split.copy(income = pesos("2.000.000"), unallocated = minus("800.000")),
            plan = IncomePlanState.Shown(plan)
        )

        // The hero is what was recorded: 2.000.000, still 800.000 short.
        rule.onNodeWithContentDescription("menos 800.000 pesos").assertIsDisplayed()
        rule.onNodeWithTag("income.plan").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("PLAN DEL MES").assertIsDisplayed()
        rule.onNodeWithText("Incluye lo que esperas recibir").assertIsDisplayed()
        listOf(
            "Anotado" to "2.000.000 pesos",
            "Esperado" to "1.000.000 pesos",
            "Planeado" to "3.000.000 pesos",
            "Sin asignar en el plan" to "200.000 pesos"
        ).forEach { (label, amount) ->
            // One line, read as its label and then its amount.
            rule.onNode(says(label) and says(amount)).assertExists()
        }
        rule.onNodeWithContentDescription("Salario esperado el vie 30 oct: 1.000.000 pesos")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Lo esperado se anota solo en su fecha.").performScrollTo()
            .assertIsDisplayed()
        // Expected money is in no total: the list still ends at what was recorded.
        rule.onNodeWithText("Total").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aPlanAboveWhatTheBudgetsLeaveIsSigned() {
        show(
            ready(),
            split,
            plan = IncomePlanState.Shown(
                plan.copy(unassigned = minus("1.300.000"), over = true)
            )
        )

        rule.onNodeWithTag("income.plan.unassigned").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("menos 1.300.000 pesos").assertIsDisplayed()
    }

    @Test
    fun aMonthWithNothingRecordedYetStillShowsWhatIsExpected() {
        show(
            ready(lines = emptyList()).copy(total = pesos("0")),
            split = null,
            plan = IncomePlanState.Shown(plan.copy(recorded = pesos("0")))
        )

        rule.onNodeWithText("Aún no anotas ingresos en octubre").assertIsDisplayed()
        rule.onNodeWithTag("income.plan").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aPlanThatCouldNotBeReadSaysSoAndCanBeReadAgain() {
        show(ready(), split, plan = IncomePlanState.Failed)

        rule.onNodeWithTag("income.plan").assertDoesNotExist()
        rule.onNodeWithText(
            "No pudimos leer lo que esperas recibir. Tus registros siguen guardados."
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("income.plan.failed").performScrollTo()
        rule.onNodeWithText("Reintentar").performScrollTo().performClick()

        assertEquals(listOf<IncomeHistoryAction>(IncomeHistoryAction.Retry), actions)
    }

    /** A node, merged as it is read, whose text or description is [part]. */
    private fun says(part: String) = SemanticsMatcher("says \"$part\"") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == part } ||
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                .any { it == part }
    }
}
