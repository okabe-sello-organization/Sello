package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Pagos previstos as last read: the list stays, with the notice and its retry. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpcomingPaymentsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun show(state: UpcomingState, onRetry: () -> Unit = {}) = rule.showSello {
        UpcomingPaymentsScreen(state, PaddingValues(12.dp), onRetry, {}, {})
    }

    @Test
    fun olderPaymentsStayListedWithTheNoticeAndItsRetry() {
        var retried = 0
        show(
            UpcomingState.Ready(
                month = YearMonth.of(2026, 10),
                today = LocalDate.of(2026, 10, 22),
                total = plain("120.000"),
                payments = listOf(
                    UpcomingPayment(
                        scheduleId = "s1",
                        name = "Suscripciones",
                        categoryName = "Ocio",
                        iconKey = null,
                        date = LocalDate.of(2026, 10, 25),
                        amount = plain("120.000")
                    )
                ),
                stale = true
            ),
            onRetry = { retried++ }
        )

        rule.onNodeWithTag(UPCOMING_STALE_TAG).assertExists()
        rule.onNodeWithTag("upcoming.s1.2026-10-25").assertExists()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(1, retried)
    }

    @Test
    fun nothingReservedAsLastReadKeepsItsNotice() {
        show(UpcomingState.Empty(YearMonth.of(2026, 10), stale = true))

        rule.onNodeWithTag(UPCOMING_STALE_TAG).assertExists()
    }

    @Test
    fun aFreshListHasNoNotice() {
        show(UpcomingState.Empty(YearMonth.of(2026, 10)))

        rule.onNodeWithTag(UPCOMING_STALE_TAG).assertDoesNotExist()
    }
}
