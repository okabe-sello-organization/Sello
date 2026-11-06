package com.software.sello.feature.investments

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Varios movimientos as it is drawn (docs/design/system/features/investments.html). */
@RunWith(AndroidJUnit4::class)
class InvestmentMovementsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

    private fun line(key: String, amount: String?) = MovementLineModel(
        key,
        Contribution,
        amount?.let(::pesos),
        valid = true,
        date = today,
        note = "",
        investmentId = "e",
        investmentName = "Ecopetrol"
    )

    @Test
    fun theDiscardQuestionCountsOnlyTheLinesWithSomethingTyped() {
        // An aporte of 500.000, then Otro renglón: the new line copies the type, nothing typed.
        val state = InvestmentMovementsState(
            loading = false,
            lines = listOf(line("0", "500.000"), line("1", null)),
            highlighted = 1,
            contributed = null,
            today = today,
            confirmDiscard = true
        )
        rule.showSello { InvestmentMovementsScreen(state) {} }

        rule.onNodeWithText("¿Descartar 1 renglón?").assertExists()
    }
}
