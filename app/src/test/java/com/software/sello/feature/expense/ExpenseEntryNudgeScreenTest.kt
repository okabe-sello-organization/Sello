package com.software.sello.feature.expense

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The Anotar varios nudge right under Otro gasto (SELLO-212): its words, ✕ and place. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpenseEntryNudgeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

    /** A new expense being written, with the nudge due. */
    private val ready = ExpenseEntryState(
        loading = false,
        categories = listOf(
            EntryCategory("a", "Alimentación", "restaurant"),
            EntryCategory("b", "Café", "local_cafe")
        ),
        categoryId = "a",
        amount = MoneyTextValue(MoneySign.None, "", "8.000", "8.000 pesos"),
        missing = null,
        date = today,
        today = today,
        severalNudge = true
    )

    private val actions = mutableListOf<ExpenseEntryAction>()

    private fun show(look: Look = Look()) = rule.showSello(look) {
        ExpenseEntryScreen(ready, dateLabels(), { actions += it }, severalOffered = true)
    }

    @Test
    fun severalLight() {
        val look = Look()
        show(look)
        rule.captureStill("src/test/screenshots/expense-entry-nudge-several-${look.label}.png")
    }

    @Test
    fun itSaysWhatOtroGastoDoesAndItsCrossClosesIt() {
        show()

        rule.onNodeWithText("¿Anotas varios seguidos? Con Otro gasto los anotas todos de una vez.")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Cerrar el aviso de Anotar varios").performClick()

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.CloseSeveralNudge), actions)
    }

    @Test
    fun itSitsRightUnderTheSlip() {
        show()

        val slip = rule.onNodeWithTag(ENTRY_SEVERAL_TAG).fetchSemanticsNode().boundsInRoot
        val nudge = rule.onNodeWithTag(ENTRY_SEVERAL_NUDGE_TAG).fetchSemanticsNode().boundsInRoot
        assert(nudge.top >= slip.bottom) { "the nudge $nudge is not under the slip $slip" }
    }
}
