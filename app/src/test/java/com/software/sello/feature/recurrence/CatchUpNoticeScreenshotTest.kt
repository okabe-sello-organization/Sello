package com.software.sello.feature.recurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the catch-up notice looks like, kept as committed images (ADR 0022): the bar and
 * the list of what Sello recorded, in three looks each.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatchUpNoticeScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val recorded = CatchUpNoticeState.Ready(
        listOf(
            RecordedItem(
                "a", false, "Arriendo", "Hogar", "home", null, null, LocalDate.of(2026, 10, 5),
                pesos("950.000")
            ),
            RecordedItem(
                "f", true, null, null, null, "other", "Diseño", LocalDate.of(2026, 10, 15),
                pesos("250.000")
            ),
            RecordedItem(
                "g", false, null, "Gimnasio", "home", null, null, LocalDate.of(2026, 10, 15),
                pesos("99.900")
            )
        ),
        LocalDate.of(2026, 10, 24)
    )

    private fun bar(look: Look) {
        rule.showSello(look) {
            Column { CatchUpNoticeBar(recorded, onTimeout = {}, onView = {}) }
        }
        rule.captureStill("src/test/screenshots/catch-up-bar-${look.label}.png")
    }

    private fun list(look: Look) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                CatchUpRecordedScreen(
                    recorded,
                    PaddingValues(12.dp),
                    onRetry = {},
                    onEdit = { _, _ -> }
                )
            }
        }
        rule.captureStill("src/test/screenshots/catch-up-recorded-${look.label}.png")
    }

    @Test
    fun barCobaltoLight() = bar(Look())

    @Test
    fun barCobaltoDark() = bar(Look(dark = true))

    @Test
    fun barVioletaLight() = bar(Look(ink = SelloInk.Violeta))

    @Test
    fun listCobaltoLight() = list(Look())

    @Test
    fun listCobaltoDark() = list(Look(dark = true))

    @Test
    fun listVioletaLight() = list(Look(ink = SelloInk.Violeta))
}
