package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Category detail's nudges under its header (SELLO-212): their words, ✕ and place. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryDetailNudgeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val food = ReciboCategory(
        id = "a",
        name = "Alimentación",
        iconKey = "restaurant",
        archived = false,
        figure = BudgetFigure(ReciboHeadline.Remaining, plain("480.000"), ReciboStamp.OnTrack),
        cellAmount = plain("480.000"),
        spent = plain("20.000"),
        limit = plain("500.000"),
        fraction = 0.04f,
        over = false,
        expenseCount = 1
    )

    private fun ready(nudge: DetailNudge) = CategoryDetailState.Ready(
        category = food,
        month = YearMonth.of(2026, 11),
        today = LocalDate.of(2026, 11, 8),
        stale = false,
        canRecord = true,
        expenses = ExpenseListState.Rows(
            listOf(
                ExpenseRow(
                    "1",
                    LocalDate.of(2026, 11, 3),
                    "Rappi, almuerzo",
                    plain("20.000"),
                    false
                )
            ),
            more = false
        ),
        nudge = nudge
    )

    private val actions = mutableListOf<CategoryDetailAction>()
    private var edits = 0

    private fun show(nudge: DetailNudge, look: Look = Look()) = rule.showSello(look) {
        CategoryDetailScreen(
            state = ready(nudge),
            months = monthNames(),
            dates = dateLabels(),
            showName = false,
            onAction = { actions += it },
            onRecordHere = {},
            contentPadding = PaddingValues(12.dp),
            onEditCategory = { edits++ }
        )
    }

    private fun shot(name: String, nudge: DetailNudge) {
        val look = Look()
        show(nudge, look)
        rule.captureStill("src/test/screenshots/category-detail-nudge-$name-${look.label}.png")
    }

    private val carry = DetailNudge.Carry(YearMonth.of(2026, 10), surplus = true)

    @Test fun carryLight() = shot("carry", carry)

    @Test fun fixedPaymentLight() = shot("fixed", DetailNudge.FixedPayment)

    @Test fun deletingLight() = shot("deleting", DetailNudge.Deleting(6))

    @Test
    fun deletingSaysUndoLastsSixSecondsAndItsCrossClosesIt() {
        show(DetailNudge.Deleting(6))

        rule.onNodeWithText(
            "Si borras un gasto por error, puedes deshacerlo durante 6 segundos."
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription("Cerrar el aviso").performClick()

        assertEquals(listOf<CategoryDetailAction>(CategoryDetailAction.CloseNudge), actions)
    }

    @Test
    fun arrastreSaysWhatWasLeftAndItsTextOpensEditarCategoria() {
        show(carry)

        rule.onNodeWithText(
            "En octubre te sobró. En Editar categoría puedes hacer que lo que sobre pase al " +
                "mes siguiente."
        ).assertIsDisplayed()
        rule.onNodeWithTag(CATEGORY_DETAIL_NUDGE_TAG).performClick()

        assertEquals(1, edits)
    }

    @Test
    fun arrastreAfterAMonthOverItsLimitSaysWhatWasMissing() {
        show(DetailNudge.Carry(YearMonth.of(2026, 10), surplus = false))

        rule.onNodeWithText(
            "En octubre te pasaste. En Editar categoría puedes hacer que lo que falte se " +
                "descuente del mes siguiente."
        ).assertIsDisplayed()
    }

    @Test
    fun aPagoFijoPointsAtRepetirEsteGasto() {
        show(DetailNudge.FixedPayment)

        rule.onNodeWithText(
            "Anotas el mismo gasto cada mes: puede ser un pago fijo. Al anotarlo, toca " +
                "Repetir este gasto."
        ).assertIsDisplayed()
    }

    @Test
    fun theRowSitsUnderTheHeaderAndAboveTheExpenses() {
        show(DetailNudge.Deleting(6))

        val nudge = rule.onNodeWithTag(CATEGORY_DETAIL_NUDGE_TAG).fetchSemanticsNode().boundsInRoot
        val list = rule.onNodeWithTag(CATEGORY_DETAIL_LIST_TAG).fetchSemanticsNode().boundsInRoot
        assert(nudge.bottom <= list.top) { "the nudge $nudge is not above the list $list" }
    }
}
