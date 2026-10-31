package com.software.sello.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.GoalPlace
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.corrections.celebration
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The goal's one confirmation after Aportar varios: the count and the net, signed. */
@RunWith(AndroidJUnit4::class)
class GoalMovementsNoticeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(notice: CorrectionNotice) = rule.showSello { CorrectionNoticeBar(notice) {} }

    @Test
    fun aReceiptIsSaidWithItsCountAndNet() {
        show(
            CorrectionNotice.GoalMovementsRecorded(
                3,
                MoneyTextValue(MoneySign.Plus, "", "130.000", "más 130.000 pesos")
            )
        )

        rule.onNodeWithText("3 movimientos anotados: +130.000").assertExists()
    }

    @Test
    fun aNetWithdrawalCarriesItsMinusAndOneLineIsSingular() {
        show(
            CorrectionNotice.GoalMovementsRecorded(
                1,
                MoneyTextValue(MoneySign.Minus, "", "50.000", "menos 50.000 pesos")
            )
        )

        rule.onNodeWithText("1 movimiento anotado: −50.000").assertExists()
    }

    @Test
    fun aNetThatCouldNotBeWorkedOutIsStillSaidToBeSaved() {
        show(CorrectionNotice.GoalMovementsRecorded(2, net = null))

        rule.onNodeWithText("2 movimientos anotados").assertExists()
    }

    @Test
    fun theReceiptsStampsAreWhatTheGoalCelebrates() {
        val stamps =
            GoalCelebration("g", "Viaje a la costa", listOf(GoalPlace.Half), emptyList(), false)

        assertEquals(stamps, CorrectionNotice.GoalMovementsRecorded(2, null, stamps).celebration)
    }
}
