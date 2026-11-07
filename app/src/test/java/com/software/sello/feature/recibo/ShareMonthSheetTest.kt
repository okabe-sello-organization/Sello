package com.software.sello.feature.recibo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.revealed
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareMonthSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)
    private val actions = mutableListOf<ShareMonthAction>()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val image = MonthShareImage(
        month = october,
        asOf = LocalDate.of(2026, 10, 28),
        closed = false,
        label = ReciboAnswerLabel.Remaining,
        amount = plain("63.100"),
        stamp = ReciboStamp.OnTrack,
        spent = plain("2.416.900"),
        limit = plain("2.480.000"),
        categories = listOf(
            MonthShareCategory("Arte", "redeem", plain("80.000"), null, false, null, false)
        )
    )

    private fun show(state: ShareMonthState.Open) =
        rule.showSello { ShareMonthSheet(state, onAction = { actions += it }) }

    @Test
    fun theSheetShowsTheImageAndSaysWhatIsInItBeforeAnythingIsSent() {
        show(ShareMonthState.Open(october, SharePreview.Ready(image)))

        rule.onNodeWithText("Compartir octubre").assertExists()
        rule.onNodeWithTag(SHARE_IMAGE_TAG).assertExists()
        rule.onNodeWithText("al 28 de octubre").assertExists()
        rule.onNodeWithText("Gastado 2.416.900 de 2.480.000").assertExists()
        rule.onNodeWithText(
            "Se comparte solo esta imagen: el mes, lo que queda y cada categoría con su gasto " +
                "y su límite. Sin notas, ingresos, metas ni inversiones."
        ).assertExists()
        assertEquals(emptyList<ShareMonthAction>(), actions)

        rule.onNodeWithTag(SHARE_CONFIRM_TAG).revealed().performClick()
        rule.onNodeWithTag(SHARE_CANCEL_TAG).revealed().performClick()

        assertEquals(listOf(ShareMonthAction.Confirm, ShareMonthAction.Cancel), actions)
    }

    @Test
    fun whileItIsPlacedNothingCanBePressed() {
        show(ShareMonthState.Open(october, SharePreview.Ready(image), ShareStep.Placing))

        rule.onNodeWithTag(SHARE_CANCEL_TAG).revealed().assertIsNotEnabled()
        rule.onNodeWithTag(SHARE_CONFIRM_TAG).revealed().performClick()

        assertEquals(emptyList<ShareMonthAction>(), actions)
    }

    @Test
    fun olderFiguresOfferNoShareButOnlyAReadAgain() {
        show(ShareMonthState.Open(october, SharePreview.Waiting))

        rule.onNodeWithTag(SHARE_WAITING_TAG).assertExists()
        rule.onNodeWithTag(SHARE_CONFIRM_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SHARE_IMAGE_TAG).assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf(ShareMonthAction.Retry), actions)
    }
}
