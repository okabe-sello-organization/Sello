package com.software.sello.feature.investments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.SweepLook
import com.software.sello.SweepWindow
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.dateLabels
import com.software.sello.sweepLooks
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dólar and Cambiar la tasa as they are drawn (v2 *Dólares y la tasa*): the rate whole
 * in every theme, size and window; no rate, loading and a failed read each their own
 * state; a rate out of bounds says so and waits.
 */
@RunWith(AndroidJUnit4::class)
class UsdRateScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 25)
    private val ready = UsdRateState.Ready(
        current = RateModel("100.000,00", LocalDate.of(2026, 10, 20)),
        history = listOf(
            RateModel("4.010,00", LocalDate.of(2026, 10, 2)),
            RateModel("3.960,50", LocalDate.of(2025, 9, 15))
        ),
        today = today
    )

    @Test
    fun theRateStaysWholeInEveryThemeSizeAndWindow() {
        var look by mutableStateOf(SweepLook(SelloInk.Cobalto, false, 1f, SweepWindow(320, 640)))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, look.scale)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(
                        Modifier.requiredSize(
                            look.window.width.dp,
                            look.window.height.dp
                        ).testTag("window")
                    ) {
                        UsdRateScreen(ready, {}, dateLabels(), PaddingValues(16.dp))
                    }
                }
            }
        }
        for (each in sweepLooks(listOf(SweepWindow(320, 640), SweepWindow(840, 600)))) {
            look = each
            rule.waitForIdle()
            val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
            val rate = rule.onNodeWithText("100.000,00", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertTrue("$each: $rate", rate.right <= window.right + 0.5.dp)
        }
        rule.onNodeWithContentDescription(
            "1 dólar vale 100.000,00 pesos, tasa del 20 oct",
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithText("3.960,50", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("15 sep 2025", useUnmergedTree = true).assertExists()
    }

    @Test
    fun noRateLoadingAndFailureAreEachTheirOwnState() {
        var state by mutableStateOf<UsdRateState>(UsdRateState.Loading)
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                UsdRateScreen(state, {}, dateLabels(), PaddingValues(16.dp))
            }
        }
        rule.onNodeWithTag("usd.rate.loading").assertExists()
        state = UsdRateState.Failed
        rule.onNodeWithText("No pudimos leer la tasa", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("usd.rate.empty").assertDoesNotExist()
        state = UsdRateState.Ready(null, emptyList(), today)
        rule.onNodeWithText("Todavía no hay tasa", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Anotar la tasa", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aRateOutOfBoundsSaysSoAndTheButtonWaits() {
        var state by mutableStateOf(
            UsdRateEntryState(
                loading = false,
                loadFailed = false,
                rate = "0,99",
                problem = RateProblem.BelowMinimum,
                current = RateModel("4.150,25", LocalDate.of(2026, 10, 20)),
                date = today,
                today = today
            )
        )
        rule.setContent {
            SelloTheme(reducedMotion = true) { UsdRateEntryScreen(state) {} }
        }
        rule.onNodeWithText("La tasa es de 1,00 en adelante", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(USD_RATE_ENTRY_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithContentDescription("Coma decimal").assertExists()
        rule.onNodeWithText(
            "La de hoy es 4.150,25, del 20 oct. La nueva la reemplaza desde su fecha; " +
                "la anterior queda en la lista.",
            useUnmergedTree = true
        ).assertExists()

        state = state.copy(rate = "100.000,01", problem = RateProblem.AboveMaximum)
        rule.onNodeWithText("La tasa llega hasta 100.000,00", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(USD_RATE_ENTRY_SUBMIT_TAG).assertIsNotEnabled()
    }
}
