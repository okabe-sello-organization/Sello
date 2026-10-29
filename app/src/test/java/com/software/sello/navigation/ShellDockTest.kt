package com.software.sello.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dock records an expense on every screen, unless a screen offers its own entry, as
 * income history does with "Anotar ingreso". Its title can be a whole phrase too.
 * On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
class ShellDockTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<ShellAction>()
    private var incomes = 0
    private val october = YearMonth.of(2026, 10)
    private val state = ShellState(october, october, false, 2026, null)

    @Test
    fun byDefaultTheDockStartsAnExpense() {
        rule.showSello {
            ShellScreen(state, monthNames(), {
                actions += it
            }, onBack = {}, screenTitle = "X") {
                Text("contenido")
            }
        }

        rule.onNodeWithContentDescription("Anotar un gasto").performClick()

        assertEquals(listOf<ShellAction>(ShellAction.StartEntry), actions)
    }

    @Test
    fun incomeHistoryOffersItsOwnEntryAndTitleAndStartsNoExpense() {
        rule.showSello {
            ShellScreen(
                state,
                monthNames(),
                { actions += it },
                onBack = {},
                screenTitle = "Ingresos",
                titleLabel = "Ingresos de octubre",
                dock = SelloDock("Anotar un ingreso", "Anotar ingreso", { incomes++ })
            ) { Text("contenido") }
        }

        rule.onNodeWithText("Ingresos de octubre").assertIsDisplayed()
        rule.onNodeWithContentDescription("Anotar un gasto").assertDoesNotExist()
        rule.onNodeWithContentDescription("Anotar un ingreso").performClick()

        assertEquals(1, incomes)
        assertEquals(emptyList<ShellAction>(), actions)
    }
}
