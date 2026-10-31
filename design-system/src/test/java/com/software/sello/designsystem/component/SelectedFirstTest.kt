package com.software.sello.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectedFirstTest {
    @get:Rule
    val rule = createComposeRule()

    private val choices = listOf("Mercado", "Transporte", "Salud", "Hogar")

    @Test
    fun theChosenOneLeadsAndTheRestKeepTheirOrder() {
        assertEquals(
            listOf("Salud", "Mercado", "Transporte", "Hogar"),
            selectedFirst(choices, "Salud") { it }
        )
    }

    @Test
    fun noChoiceOrAnUnknownOneLeavesTheOrderAlone() {
        assertEquals(choices, selectedFirst(choices, null) { it })
        assertEquals(choices, selectedFirst(choices, "Viajes") { it })
        assertEquals(choices, selectedFirst(choices, "Mercado") { it })
    }

    @Test
    fun aChoiceArrivingBeforeAnyPickMovesToTheFront() {
        var selected by mutableStateOf<String?>(null)
        lateinit var order: SelectedFirstOrder<String>
        rule.setContent { order = rememberSelectedFirst(choices, selected) { it } }
        rule.runOnIdle { assertEquals(choices, order.items) }

        // A restored draft or the entry being corrected arrives after the chips show.
        selected = "Hogar"
        rule.runOnIdle {
            assertEquals(listOf("Hogar", "Mercado", "Transporte", "Salud"), order.items)
        }
    }

    @Test
    fun aPickInTheRowNeverMovesTheChips() {
        var selected by mutableStateOf<String?>("Salud")
        lateinit var order: SelectedFirstOrder<String>
        rule.setContent { order = rememberSelectedFirst(choices, selected) { it } }
        val shown = listOf("Salud", "Mercado", "Transporte", "Hogar")
        rule.runOnIdle { assertEquals(shown, order.items) }

        rule.runOnIdle {
            order.picked()
            selected = "Hogar"
        }
        rule.runOnIdle { assertEquals(shown, order.items) }
    }
}
