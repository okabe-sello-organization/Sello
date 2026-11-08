package com.software.sello.feature.investments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The investments nudge under an investment's answer (SELLO-213). Synthetic figures. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvestmentNudgeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val id = "00000000-0000-4000-8000-000000000001"
    private val actions = mutableListOf<InvestmentDetailAction>()

    private fun ready(): InvestmentDetailState.Ready {
        val fondo = handInvestment(
            id,
            "Fondo de acciones",
            null,
            "trending_up",
            listOf(
                Triple(Contribution, 2_000_000L, "2026-10-01"),
                Triple(Valuation, 2_000_000L, "2026-10-01")
            )
        )
        return InvestmentsRenderModel(testMoney()).detail(
            ReadState.Ready(investmentsBoard(listOf(fondo))),
            id,
            LocalDate.of(2026, 10, 22)
        ) as InvestmentDetailState.Ready
    }

    private fun show(look: Look = Look()) = rule.showSello(look) {
        // As InvestmentDestinations.kt frames it: the investment's name and Anotar movimiento.
        ShellScreen(
            state = ShellState(
                currentMonth = YearMonth.of(2026, 10),
                selectedMonth = YearMonth.of(2026, 10),
                monthPickerOpen = false,
                pickerYear = 2026,
                pendingEntry = null
            ),
            names = monthNames(),
            onAction = {},
            onBack = {},
            titleOnly = "Fondo de acciones",
            entryDock = false,
            dock = SelloDock(
                "Anotar movimiento",
                "Anotar movimiento",
                {},
                tone = ButtonTone.Ink,
                icon = SelloIcon.Add
            )
        ) { padding ->
            InvestmentDetailScreen(ready().copy(nudge = true), { actions += it }, dateLabels(), padding)
        }
    }

    @Test
    fun nudgeLight() {
        show()
        rule.captureStill("src/test/screenshots/investment-detail-nudge-claro.png")
    }

    @Test
    fun itSaysWhereMovementsGoAndThatTheValueIsWrittenByHand() {
        show()

        rule.onNodeWithText(
            "Lo que pones o sacas va en Anotar movimiento. El valor lo anotas tú cuando cambie."
        ).assertIsDisplayed()
        val answer = rule.onNodeWithTag("investment.answer").fetchSemanticsNode().boundsInRoot
        val nudge = rule.onNodeWithTag(INVESTMENT_NUDGE_TAG).fetchSemanticsNode().boundsInRoot
        assert(nudge.top >= answer.bottom) { "the nudge $nudge is not under the answer $answer" }
        rule.onNodeWithContentDescription("Cerrar el aviso de las inversiones").performClick()

        assertEquals(listOf<InvestmentDetailAction>(InvestmentDetailAction.CloseNudge), actions)
    }

    @Test
    fun withoutItThePageHasNoNudge() {
        rule.showSello(Look()) {
            InvestmentDetailScreen(ready(), {}, dateLabels(), PaddingValues())
        }

        rule.onNodeWithTag(INVESTMENT_NUDGE_TAG).assertDoesNotExist()
    }
}
