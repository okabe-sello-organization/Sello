package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.navigation.TitledFrame
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Pagos previstos as last read and when nothing was ever read (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpcomingPaymentsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val older = UpcomingState.Ready(
        month = YearMonth.of(2026, 10),
        today = LocalDate.of(2026, 10, 22),
        total = plain("200.000"),
        payments = listOf(
            UpcomingPayment(
                "s1",
                "Suscripciones",
                "Ocio",
                null,
                LocalDate.of(2026, 10, 25),
                plain("120.000")
            ),
            UpcomingPayment(
                "s2",
                "Arriendo",
                "Hogar",
                "home",
                LocalDate.of(2026, 10, 30),
                plain("80.000")
            )
        ),
        stale = true
    )

    private fun capture(state: UpcomingState, file: String, look: Look = Look()) {
        rule.showSello(look) {
            if (state == UpcomingState.Loading) {
                TitledFrame("Pagos previstos") { padding ->
                    UpcomingPaymentsScreen(state, padding, {}, {}, {})
                }
            } else {
                UpcomingPaymentsScreen(state, PaddingValues(12.dp), {}, {}, {})
            }
        }
        rule.captureStill("src/test/screenshots/$file.png")
    }

    @Test
    fun olderClaro() = capture(older, "upcoming-stale-claro")

    @Test
    fun failedClaro() = capture(UpcomingState.Failed, "upcoming-failed-claro")

    @Test
    fun olderOscuro() = capture(older, "upcoming-stale-oscuro", Look(dark = true))

    @Test
    fun failedOscuro() = capture(UpcomingState.Failed, "upcoming-failed-oscuro", Look(dark = true))

    private val ready = older.copy(stale = false)

    @Test fun readyClaro() = capture(ready, "upcoming-ready-claro")

    @Test fun readyOscuro() = capture(ready, "upcoming-ready-oscuro", Look(dark = true))

    @Test fun loadingClaro() = capture(UpcomingState.Loading, "upcoming-loading-claro")

    @Test
    fun loadingOscuro() =
        capture(UpcomingState.Loading, "upcoming-loading-oscuro", Look(dark = true))

    private val empty = UpcomingState.Empty(YearMonth.of(2026, 10))

    @Test fun emptyClaro() = capture(empty, "upcoming-empty-claro")

    @Test fun emptyOscuro() = capture(empty, "upcoming-empty-oscuro", Look(dark = true))
}
