package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.CATEGORY_CARRYING_TAG
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.presentation.carry.carryMonthOf
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How Recibo tells a carrying category from the others (docs/design/v2 Arrastre): the ↻
 * badge on its circle and its signed carry under the amount; the month's limit as one sum
 * under the per-day line. Figures are those of docs/testing/signed-carryover-examples.md.
 */
@RunWith(AndroidJUnit4::class)
class ReciboCarryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val render = ReciboRenderModel(testMoney())
    private val ene = YearMonth.of(2027, 1)
    private val feb = YearMonth.of(2027, 2)

    /** SC13: Comida on from ene, Transporte 50.000 off, Ocio unlimited; 22 feb 2027. */
    private val sc13 = carryMonthOf(
        "2027-02-22",
        listOf(
            categoryIn(1, "Comida", 100_000, icon = "restaurant"),
            categoryIn(2, "Transporte", 50_000),
            categoryIn(3, "Ocio", null)
        ),
        listOf(
            storedExpense(100, 1, "2027-01-10", 20_000),
            storedExpense(101, 1, "2027-02-05", 30_000),
            storedExpense(102, 2, "2027-02-06", 60_000),
            storedExpense(103, 3, "2027-02-07", 10_000)
        ),
        mapOf(1 to ene)
    )

    /** SC11 for Comida (ene spent 250.000) with Hogar 400.000 and Transporte 50.000 off. */
    private val sc11 = carryMonthOf(
        "2027-02-22",
        listOf(
            categoryIn(1, "Comida", 100_000, icon = "restaurant"),
            categoryIn(2, "Hogar", 400_000),
            categoryIn(3, "Transporte", 50_000)
        ),
        listOf(
            storedExpense(110, 1, "2027-01-10", 250_000),
            storedExpense(111, 2, "2027-02-06", 380_000),
            storedExpense(112, 3, "2027-02-07", 40_000)
        ),
        mapOf(1 to ene)
    )

    private fun show(snapshot: MonthlySnapshot) {
        rule.showSello(Look()) {
            ReciboScreen(render.recibo(ReadState.Ready(snapshot)), {}, PaddingValues(12.dp))
        }
    }

    private fun inCell(id: Int) = hasAnyAncestor(hasTestTag(reciboCategoryTag(uuid(id))))

    private fun cell(id: Int) = rule.onNodeWithTag(reciboCategoryTag(uuid(id))).performScrollTo()

    @Test
    fun aCarryingCellWearsTheBadgeAndItsSignedCarry() {
        show(sc13)

        cell(1).assertContentDescriptionContains("con un sobrante de 80.000", substring = true)
        rule.onNode(hasTestTag(CATEGORY_CARRYING_TAG) and inCell(1), useUnmergedTree = true)
            .assertExists()
        rule.onNode(hasText("+80.000") and inCell(1), useUnmergedTree = true).assertExists()
        // Off, or without a limit on purpose: no badge and nothing carried.
        listOf(2, 3).forEach { id ->
            cell(id)
            rule.onNode(hasTestTag(CATEGORY_CARRYING_TAG) and inCell(id), useUnmergedTree = true)
                .assertDoesNotExist()
        }
        rule.onNode(hasText("con sobrante") and inCell(1), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun aDeficitIsSignedAndTheMonthSumsItWithoutASentence() {
        show(sc11)

        cell(1).assertContentDescriptionContains("con un faltante de 150.000", substring = true)
        rule.onNode(hasText("−150.000") and inCell(1), useUnmergedTree = true).assertExists()
        // Σ base 550.000 − faltante 150.000 = Límite del mes 400.000; Gastado 420.000.
        rule.onNodeWithTag(RECIBO_CARRY_SUM_CAPTION_TAG)
            .assertContentDescriptionContains("Límite base 550.000", substring = true)
            .assertContentDescriptionContains("menos un faltante de 150.000", substring = true)
        rule.onNode(hasText("Límite del mes") and hasAnyAncestor(hasTestTag(RECIBO_CARRY_SUM_TAG)))
            .assertExists()
        rule.onNodeWithText("Cifras al 22 feb").assertExists()
        // The receipt's own lines and the sentence why it is Excedido are gone from Recibo.
        rule.onNodeWithText("Límite base").assertDoesNotExist()
        rule.onNodeWithText("Faltante").assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_CARRY_WHY_TAG).assertDoesNotExist()
    }

    @Test
    fun aChainsFirstMonthCarriesZeroAndStillWearsTheBadge() {
        show(
            carryMonthOf(
                "2027-02-22",
                listOf(categoryIn(1, "Comida", 100_000, icon = "restaurant")),
                listOf(storedExpense(120, 1, "2027-02-05", 30_000)),
                mapOf(1 to feb)
            )
        )

        cell(1).assertContentDescriptionContains("con un sobrante de 0", substring = true)
        rule.onNode(hasTestTag(CATEGORY_CARRYING_TAG) and inCell(1), useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithTag(RECIBO_CARRY_SUM_CAPTION_TAG)
            .assertContentDescriptionContains("más un sobrante de 0", substring = true)
    }
}
