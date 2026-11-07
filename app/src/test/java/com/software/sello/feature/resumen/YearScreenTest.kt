package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.YearRecords
import com.software.sello.domain.port.ReadState
import com.software.sello.showSello
import com.software.sello.testing.testDateLabels
import com.software.sello.testing.testMoney
import com.software.sello.testing.testMonthNames
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YearScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val render = YearRenderModel(testMoney(), testMonthNames(), testDateLabels())
    private val ready = render.render(2026, ReadState.Ready(yearSnapshot())) as YearState.Ready
    private val state = mutableStateOf<YearState>(ready)
    private val actions = mutableListOf<YearAction>()

    private fun show() = rule.showSello {
        YearScreen(state.value, { actions += it }, PaddingValues())
    }

    @Test
    fun theHeroComparesTheSameDaysOfTheYearBefore() {
        show()
        rule.onNodeWithText("¿Cómo va tu año?").assertIsDisplayed()
        rule.onNodeWithText("1.120.000 menos que en 2025").assertIsDisplayed()
        rule.onNodeWithText("↓ 7,3 % al mismo día del año").assertIsDisplayed()
        rule.onNodeWithText("Del 1 de enero al 6 de noviembre de cada año").assertIsDisplayed()
    }

    @Test
    fun aYearWithoutTheYearBeforeShowsWhatWasSpentAlone() {
        state.value = render.render(2026, ReadState.Ready(yearSnapshot(comparison = false)))
        show()
        rule.onNodeWithText("gastados del 1 de enero al 6 de noviembre").assertIsDisplayed()
        rule.onNodeWithText("menos que en", substring = true).assertDoesNotExist()
    }

    @Test
    fun mesAMesCountsVerdictsAndSelectsAColumn() {
        show()
        rule.onNodeWithTag("year.marks").performScrollTo()
            .assertTextContains("8 meses cumplidos · 2 excedidos", substring = true)
        rule.onNodeWithText("noviembre en curso").assertExists()
        rule.onNodeWithContentDescription("marzo de 2026, 1.840.000 pesos, excedido").performClick()
        rule.runOnIdle { assertEquals(YearAction.SelectMonth("2026-03"), actions.last()) }
        rule.runOnIdle { state.value = ready.copy(selectedMonth = "2026-03") }
        rule.onNodeWithTag("year.months.selected").assertIsDisplayed()
        rule.onNodeWithText("Marzo de 2026").assertIsDisplayed()
    }

    @Test
    fun loQueEntroAndTheTopFiveCategories() {
        show()
        rule.onNodeWithText("Entró en 2026").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("17.270.000 pesos").assertExists()
        rule.onNodeWithText("46 %").assertExists()
        rule.onNodeWithText("Salud").assertDoesNotExist()
        rule.onNodeWithTag("year.categories.all").performScrollTo()
            .assertTextContains("Ver las 9 categorías").performClick()
        rule.runOnIdle { assertEquals(YearAction.ShowAllCategories, actions.last()) }
        rule.runOnIdle { state.value = ready.copy(allCategories = true) }
        rule.onNodeWithText("Salud").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("year.categories.all").assertDoesNotExist()
    }

    @Test
    fun movesOnlyWithinTheYearsAllowed() {
        show()
        rule.onNodeWithTag("year.previous").assertIsEnabled().performClick()
        rule.onNodeWithTag("year.next").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(listOf<YearAction>(YearAction.Previous), actions) }
    }

    @Test
    fun loadingFailedStaleAndEmptyAreDistinct() {
        state.value = YearState.Loading(2026)
        show()
        rule.onNodeWithTag("year.loading").assertIsDisplayed()
        rule.onNodeWithTag("year.shown").assertTextContains("2026")
        rule.runOnIdle { state.value = YearState.Failed(2026) }
        rule.onNodeWithTag("year.failed").assertIsDisplayed()
        rule.onNodeWithTag("year.hero").assertDoesNotExist()
        rule.runOnIdle { state.value = ready.copy(stale = true) }
        rule.onNodeWithTag("year.stale").assertIsDisplayed()
        rule.onNodeWithTag("year.hero").assertExists()
        rule.runOnIdle {
            state.value = render.render(2026, ReadState.Ready(yearSnapshot(empty = true)))
        }
        rule.onNodeWithTag("year.empty").assertIsDisplayed()
        rule.onNodeWithText("Nada anotado en 2026").assertExists()
        rule.onNodeWithTag("year.hero").assertDoesNotExist()
    }

    @Test
    fun theYearsRecordsFromFinishedMonths() {
        show()
        rule.onNodeWithTag("year.records").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Récords del año").assertExists()
        rule.onNodeWithTag("year.records.dearest")
            .assertTextContains("Mes más caro").assertTextContains("Marzo")
            .assertContentDescriptionContains("1.840.000 pesos")
        rule.onNodeWithTag("year.records.calmest")
            .assertTextContains("Mes más tranquilo").assertTextContains("Agosto")
        rule.onNodeWithText("Matrícula · mar 3 feb").assertExists()
        rule.onNodeWithTag("year.records.most_over")
            .assertTextContains("Entretenimiento").assertTextContains("4 meses")
    }

    @Test
    fun aBiggestExpenseWithoutANoteShowsItsCategory() {
        state.value = render.render(
            2026,
            ReadState.Ready(yearSnapshot(records = mockRecords(note = null, mostOver = false)))
        )
        show()
        rule.onNodeWithTag("year.records").performScrollTo()
        rule.onNodeWithText("Hogar · mar 3 feb").assertExists()
        rule.onNodeWithTag("year.records.most_over").assertDoesNotExist()
    }

    @Test
    fun withFewerThanTwoFinishedMonthsTheRecordsComeLater() {
        state.value = render.render(
            2026,
            ReadState.Ready(yearSnapshot(records = YearRecords.TooFew))
        )
        show()
        rule.onNodeWithTag("year.records.too_few").performScrollTo()
            .assertTextContains("Con un mes más verás tus récords.")
        rule.onNodeWithTag("year.records.dearest").assertDoesNotExist()
    }
}
