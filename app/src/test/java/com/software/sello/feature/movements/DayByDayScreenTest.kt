package com.software.sello.feature.movements

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.port.MovementFilter
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayByDayScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<DayByDayAction>()
    private val opened = mutableListOf<String>()

    private fun show(
        state: DayByDayState,
        query: String = "",
        found: SearchState = SearchState.Off
    ) = rule.showSello {
        DayByDayScreen(
            state,
            query,
            found,
            { actions += it },
            { opened += "gasto $it" },
            { opened += "ingreso $it" },
            { opened += "anotar" },
            PaddingValues()
        )
    }

    @Test
    fun daysAreHeadedTodayOrByWeekdayWithWhatWasSpent() {
        show(dayByDayReady)

        val today = dayTag(LocalDate.of(2026, 10, 6))
        rule.onNode(hasTestTag(today) and hasText("Hoy, mar 6", substring = true)).assertExists()
        rule.onNodeWithTag("$today.spent", useUnmergedTree = true).assertExists()
        rule.onNode(hasTestTag(dayTag(LocalDate.of(2026, 10, 5))) and hasText("Lun 5", true))
            .assertExists()
        // A day of incomes alone has no sum.
        rule.onNodeWithTag("${dayTag(LocalDate.of(2026, 10, 3))}.spent", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun eachRowIsOneButtonSaidAsNoteCategoryAndAmount() {
        show(dayByDayReady)

        assertEquals(
            listOf("Sin nota, Café, 8.000 pesos"),
            rule.onNodeWithTag(movementTag("3")).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)
        )
        assertEquals(
            listOf("Arriendo, Fijo, Hogar, 1.100.000 pesos"),
            rule.onNodeWithTag(movementTag("4")).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)
        )
        assertEquals(
            listOf("Salario, Ingreso · Salario, más 2.500.000 pesos"),
            rule.onNodeWithTag(movementTag("6")).performScrollTo().fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)
        )
    }

    @Test
    fun aRowOpensItsOwnEditor() {
        show(dayByDayReady)

        rule.onNodeWithTag(movementTag("1")).performClick()
        rule.onNodeWithTag(movementTag("6")).performScrollTo().performClick()

        assertEquals(listOf("gasto 1", "ingreso 6"), opened)
    }

    @Test
    fun theChipsFilterTheList() {
        show(dayByDayReady)

        rule.onNodeWithTag("dayByDay.chip.All").assertIsSelected()
        rule.onNodeWithTag("dayByDay.chip.Incomes").performClick()

        assertEquals(listOf<DayByDayAction>(DayByDayAction.Filter(MovementFilter.Incomes)), actions)
    }

    @Test
    fun anEmptyMonthOffersAnotarGasto() {
        show(dayByDayReady.copy(days = emptyList(), totalCount = 0))

        rule.onNodeWithTag("dayByDay.chips").assertDoesNotExist()
        rule.onNodeWithText("Aún no hay movimientos").assertExists()
        rule.onNodeWithText("Anotar gasto").performClick()

        assertEquals(listOf("anotar"), opened)
    }

    @Test
    fun aFailedPageKeepsTheRowsAndRetries() {
        show(dayByDayReady.copy(more = true, pageFailure = DayByDayProblem.Unread, totalCount = 48))

        rule.onNodeWithTag(movementTag("1")).assertExists()
        rule.onNode(
            hasText("Reintentar") and hasAnyAncestor(hasTestTag("dayByDay.pageFailed"))
        ).performScrollTo().performClick()

        assertEquals(listOf<DayByDayAction>(DayByDayAction.Retry), actions)
    }

    @Test
    fun aPageTooLargeIsSaidWithoutAReintentarThatCannotHelp() {
        show(dayByDayReady.copy(pageFailure = DayByDayProblem.TooLarge))

        rule.onNode(
            hasText("Un día suma más", substring = true) and
                hasAnyAncestor(hasTestTag("dayByDay.pageFailed"))
        ).performScrollTo().assertExists()
        rule.onNode(hasText("Reintentar") and hasAnyAncestor(hasTestTag("dayByDay.pageFailed")))
            .assertDoesNotExist()
    }

    @Test
    fun aChipThatFindsNothingIsAnEmptyStatusSlipUnderTheChips() {
        show(
            dayByDayReady.copy(
                filter = MovementFilter.Incomes,
                days = emptyList(),
                totalCount = 0
            )
        )

        rule.onNodeWithTag("dayByDay.chips").assertExists()
        rule.onNode(
            hasText("No hay ingresos en octubre") and hasAnyAncestor(hasTestTag("dayByDay.none"))
        ).assertExists()
        rule.onNode(hasText("Anotar gasto")).assertDoesNotExist()
    }

    @Test
    fun typingSearches() {
        show(dayByDayReady)

        rule.onNodeWithTag(SEARCH_TAG).performTextInput("rappi")

        assertEquals(listOf<DayByDayAction>(DayByDayAction.Search("rappi")), actions)
    }

    @Test
    fun theClearButtonEmptiesTheField() {
        show(dayByDayReady, query = "rappi", found = foundRappi)
        rule.onNodeWithContentDescription("Borrar la búsqueda").performClick()
        assertEquals(listOf<DayByDayAction>(DayByDayAction.Search("")), actions)
    }

    @Test
    fun resultsAreHeadedByMonthWithTheirCountAndSumAndEachRowSaysItsDay() {
        show(dayByDayReady, query = "rappi", found = foundRappi)

        rule.onNodeWithText("En todos los meses").assertExists()
        rule.onNode(hasText("9 gastos", substring = true)).assertExists()
        rule.onNodeWithTag("dayByDay.found.expenses", useUnmergedTree = true)
            .assertTextContains("214.800", substring = true)
        val october = foundMonthTag(YearMonth.of(2026, 10))
        rule.onNodeWithTag(october).assertContentDescriptionEquals(
            "Octubre 2026: 3 encontrados, 61.200 pesos"
        )
        assertEquals(
            "Rappi, almuerzo, Alimentación · sáb 3, 20.600 pesos",
            rule.onNodeWithTag(movementTag("11")).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)?.single()
        )
        // The day list's headings and month count give way to the results.
        rule.onNodeWithTag("dayByDay.count").assertDoesNotExist()
        rule.onNodeWithTag(dayTag(LocalDate.of(2026, 10, 6))).assertDoesNotExist()
    }

    @Test
    fun anAmountSearchSaysSoAndCountsIncomesApart() {
        show(dayByDayReady, query = "18.000", found = foundAmount)

        rule.onNodeWithText("Monto exacto 18.000").assertExists()
        rule.onNode(hasText("14 gastos", substring = true)).assertExists()
        rule.onNode(hasText("1 ingreso", substring = true)).assertExists()
        // Today's result says hoy instead of its weekday.
        assertEquals(
            "Almuerzo, Alimentación · hoy, 18.000 pesos",
            rule.onNodeWithTag(movementTag("1")).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)?.single()
        )
        rule.onNodeWithTag("dayByDay.found.more").performScrollTo().performClick()
        assertEquals(listOf<DayByDayAction>(DayByDayAction.MoreFound), actions)
    }

    @Test
    fun aResultOpensItsOwnEditor() {
        show(dayByDayReady, query = "18.000", found = foundAmount)

        rule.onNodeWithTag(movementTag("24")).performScrollTo().performClick()
        rule.onNodeWithTag(movementTag("23")).performScrollTo().performClick()

        assertEquals(listOf("gasto 24", "ingreso 23"), opened)
    }

    @Test
    fun nothingFoundSaysWhatWasSearchedWithoutAButton() {
        show(
            dayByDayReady,
            query = "plomería",
            found = foundRappi.copy(words = "plomería", expenses = null, months = emptyList())
        )

        val none = hasAnyAncestor(hasTestTag("dayByDay.found.none"))
        rule.onNode(hasText("No encontramos «plomería»") and none).assertExists()
        rule.onNodeWithText("Busca otra palabra o un monto exacto, como 18.000.").assertExists()
        rule.onNode(hasClickAction() and none).assertDoesNotExist()
        // The chips stay, so another can be picked.
        rule.onNodeWithTag("dayByDay.chips").assertExists()
    }

    @Test
    fun aFailedSearchKeepsTheQueryAndOffersReintentar() {
        show(
            dayByDayReady,
            query = "rappi",
            found = SearchState.Failed("rappi", MovementFilter.All, DayByDayProblem.Unread)
        )

        rule.onNodeWithTag(SEARCH_TAG).assertTextEquals("rappi")
        rule.onNode(hasText("Reintentar") and hasAnyAncestor(hasTestTag("dayByDay.found.failed")))
            .performClick()
        assertEquals(listOf<DayByDayAction>(DayByDayAction.Retry), actions)
    }

    @Test
    fun anEmptyMonthStillOffersTheSearch() {
        show(dayByDayReady.copy(days = emptyList(), totalCount = 0))

        rule.onNodeWithTag(SEARCH_TAG).assertExists()
        // Nothing typed: nothing to clear.
        rule.onNodeWithContentDescription("Borrar la búsqueda").assertDoesNotExist()
        rule.onNodeWithText("Buscar en todos tus movimientos").assertExists()
    }
}
