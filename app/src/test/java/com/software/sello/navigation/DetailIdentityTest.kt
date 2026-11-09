package com.software.sello.navigation

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.feature.recibo.BudgetFigure
import com.software.sello.feature.recibo.CategoryDetailState
import com.software.sello.feature.recibo.ExpenseListState
import com.software.sello.feature.recibo.ReciboAnswerLabel
import com.software.sello.feature.recibo.ReciboCategory
import com.software.sello.feature.recibo.ReciboHeadline
import com.software.sello.feature.recibo.ReciboStamp
import com.software.sello.feature.recibo.ReciboState
import com.software.sello.feature.recibo.ReciboSummary
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Whose name is over a category's detail, and whose detail is under it, while the
 * detail of the category just chosen is still being read.
 */
class DetailIdentityTest {
    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val figure = BudgetFigure(ReciboHeadline.Spent, plain("0"), ReciboStamp.NoLimit)

    private fun category(id: String, name: String) = ReciboCategory(
        id = id,
        name = name,
        iconKey = "home",
        archived = false,
        figure = figure,
        cellAmount = plain("0"),
        spent = plain("0"),
        limit = null,
        fraction = null,
        over = false,
        expenseCount = 0
    )

    private fun ready(id: String, name: String) = CategoryDetailState.Ready(
        category = category(id, name),
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 21),
        stale = false,
        canRecord = true,
        expenses = ExpenseListState.Empty
    )

    private val recibo = ReciboState.Month(
        summary = ReciboSummary(ReciboAnswerLabel.Remaining, figure.amount, figure.stamp),
        categories = listOf(category("a", "Alimentación"), category("b", "Café")),
        stale = false
    )

    private val notReady = listOf(
        CategoryDetailState.Closed,
        CategoryDetailState.Loading,
        CategoryDetailState.Failed,
        CategoryDetailState.Gone
    )

    @Test
    fun whileItsDetailIsStillBeingReadACategoryChosenFromReciboAlreadyHasItsName() {
        for (detail in notReady) {
            assertEquals("$detail", "Café", detailTitle("b", detail, recibo))
        }
    }

    @Test
    fun theNameOfTheCategoryLeftBehindIsNeverPutOverTheOneJustChosen() {
        // The detail still holds Alimentación for an instant after Café is chosen.
        val stillTheOther = ready("a", "Alimentación")

        assertEquals("Café", detailTitle("b", stillTheOther, recibo))
        assertNull(detailTitle("b", stillTheOther, ReciboState.Loading))
    }

    @Test
    fun aDetailStillHoldingAnotherCategoryIsNotShownUnderTheOneChosen() {
        val stillTheOther = ready("a", "Alimentación")

        assertEquals(CategoryDetailState.Loading, detailOf("b", stillTheOther))
        assertEquals(stillTheOther, detailOf("a", stillTheOther))
        for (detail in notReady) assertEquals(detail, detailOf("b", detail))
    }

    @Test
    fun onceItsOwnDetailIsReadTheNameComesFromIt() {
        // Read later than Recibo's list: if the two differ, the detail is the newer one.
        val renamed = ready("b", "Cafetería")

        assertEquals("Cafetería", detailTitle("b", renamed, recibo))
        assertEquals("Cafetería", detailTitle("b", renamed, ReciboState.Loading))
    }

    @Test
    fun whenNothingReadSoFarKnowsTheCategoryNoNameIsInvented() {
        for (detail in notReady) {
            assertNull("$detail", detailTitle("b", detail, ReciboState.Loading))
            assertNull("$detail", detailTitle("b", detail, ReciboState.Failed))
            assertNull("$detail", detailTitle("b", detail, ReciboState.FirstRun()))
            assertNull("$detail", detailTitle("zzz", detail, recibo))
        }
        assertNull(detailTitle(null, ready("a", "Alimentación"), recibo))
    }

    @Test
    fun olderFiguresStillNameTheCategoryWhileTheyAreAllThereIs() {
        val stale = recibo.copy(stale = true)

        assertEquals("Alimentación", detailTitle("a", CategoryDetailState.Loading, stale))
    }
}
