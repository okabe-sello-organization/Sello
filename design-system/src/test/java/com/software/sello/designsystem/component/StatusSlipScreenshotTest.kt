package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Each kind of [StatusSlip] in Claro and Oscuro, 412 dp wide, plus a busy action, so the
 * owner can approve the six kinds side by side. Synthetic words only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusSlipScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        rule.setContent {
            SelloTheme(darkTheme = dark, reducedMotion = true) {
                Box(
                    Modifier.requiredWidth(412.dp).background(SelloTheme.colors.desk)
                        .testTag("stage")
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { content() }
                }
            }
        }
        listOf(false, true).forEach { next ->
            dark = next
            rule.waitForIdle()
            val theme = if (next) "oscuro" else "claro"
            rule.onNodeWithTag("stage").drawnBitmap()
                .captureBaseline("src/test/screenshots/status-slip-$name-$theme.png")
        }
    }

    @Test
    fun empty() = capture("empty") {
        StatusSlip(
            kind = StatusKind.Empty(SelloIcon.ReceiptLong),
            title = "Aún no hay gastos",
            message = "Anota el primero y aquí verás tu mes.",
            action = StatusAction(label = "Anotar gasto") {}
        )
    }

    @Test
    fun loadFailed() = capture("load-failed") {
        StatusSlip(
            kind = StatusKind.LoadFailed,
            message = "No pudimos leer tus gastos. Tus registros siguen guardados.",
            action = StatusAction {}
        )
    }

    @Test
    fun older() = capture("older") {
        StatusSlip(
            kind = StatusKind.Older,
            message = "No pudimos actualizar. Esto es lo último que se leyó y puede no " +
                "estar al día.",
            action = StatusAction {}
        )
    }

    @Test
    fun actionFailed() = capture("action-failed") {
        StatusSlip(
            kind = StatusKind.ActionFailed,
            title = "No se guardó el respaldo",
            message = "No hay espacio suficiente en el teléfono. Libera espacio e " +
                "inténtalo de nuevo.",
            action = StatusAction {}
        )
    }

    @Test
    fun unknown() = capture("unknown") {
        StatusSlip(
            kind = StatusKind.Unknown,
            title = "No sabemos si se guardó",
            message = "El respaldo se interrumpió antes de confirmarse. Comprueba si quedó " +
                "guardado antes de hacer otro.",
            action = StatusAction {}
        )
    }

    @Test
    fun gone() = capture("gone") {
        StatusSlip(
            kind = StatusKind.Gone,
            message = "Esta categoría se borró mientras la veías.",
            action = StatusAction {}
        )
    }

    /** The action while it runs: a spinner in place, the label kept. */
    @Test
    fun busy() = capture("busy") {
        StatusSlip(
            kind = StatusKind.LoadFailed,
            message = "No pudimos leer tus gastos. Tus registros siguen guardados.",
            action = StatusAction(busy = true) {}
        )
        StatusSlip(
            kind = StatusKind.Unknown,
            title = "No sabemos si se guardó",
            message = "Comprueba si quedó guardado antes de hacer otro.",
            action = StatusAction(busy = true) {}
        )
    }
}
