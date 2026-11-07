package com.software.sello.feature.movements

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
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

    private fun show(state: DayByDayState) = rule.showSello {
        DayByDayScreen(
            state,
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
        show(dayByDayReady.copy(more = true, pageFailed = true, totalCount = 48))

        rule.onNodeWithTag(movementTag("1")).assertExists()
        rule.onNode(
            hasText("Reintentar") and hasAnyAncestor(hasTestTag("dayByDay.pageFailed"))
        ).performScrollTo().performClick()

        assertEquals(listOf<DayByDayAction>(DayByDayAction.Retry), actions)
    }
}
