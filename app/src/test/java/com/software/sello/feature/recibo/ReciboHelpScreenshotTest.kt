package com.software.sello.feature.recibo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.presentation.carry.carryMonthOf
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Recibo's summary card with its **?** and underlined words, and one explanation open over
 * it (SELLO-207), kept as committed images (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReciboHelpScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    /** Comida carries from ene: the card shows por día, base and sobrante (SC13). */
    private val carrying = ReciboRenderModel(testMoney()).recibo(
        ReadState.Ready(
            carryMonthOf(
                "2027-02-22",
                listOf(categoryIn(1, "Comida", 100_000), categoryIn(2, "Transporte", 50_000)),
                listOf(
                    storedExpense(100, 1, "2027-01-10", 20_000),
                    storedExpense(101, 1, "2027-02-05", 30_000)
                ),
                mapOf(1 to YearMonth.of(2027, 1))
            )
        )
    ) as ReciboState.Month

    private fun capture(name: String, state: ReciboState, look: Look) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                ReciboScreen(state, {}, PaddingValues(12.dp))
            }
        }
        rule.captureStill("src/test/screenshots/recibo-help-$name-${look.label}.png")
    }

    @Test fun cardLight() = capture("card", carrying, Look())

    @Test fun cardDark() = capture("card", carrying, Look(dark = true))

    @Test
    fun sheetLight() = capture("sheet", carrying.copy(help = ReciboHelp.Summary), Look())

    @Test
    fun sheetDark() = capture("sheet", carrying.copy(help = ReciboHelp.Summary), Look(dark = true))
}
