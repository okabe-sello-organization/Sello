package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The guidance parts in Claro and Oscuro, 412 dp wide, as the E38 mock draws them: a
 * card's **?** and underlined terms, the help sheet, and the nudge rows. Synthetic words
 * and figures only.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h1200dp-mdpi")
class GuidanceScreenshotTest {
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
                .captureBaseline("src/test/screenshots/guidance-$name-$theme.png")
        }
    }

    /** A card's title with its **?**, and terms underlined in its lines. */
    @Test
    fun cardHelpAndTerms() = capture("card-help-and-terms") {
        Slip(pinked = false) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
            ) {
                Text(
                    "Restante del presupuesto",
                    style = MaterialTheme.typography.labelMedium,
                    color = SelloTheme.colors.inkSoft
                )
                CardHelp("Cómo leer tu mes", {})
            }
            MoneyText(pesos("130.000"), style = MoneyStyle.Hero)
            Row(verticalAlignment = Alignment.Bottom) {
                MoneyText(pesos("18.550"), style = MoneyStyle.Card)
                Text(" ")
                HelpTerm(
                    "por día",
                    {},
                    style = MaterialTheme.typography.labelMedium,
                    color = SelloTheme.colors.inkSoft
                )
            }
            Row {
                val small = MaterialTheme.typography.bodySmall
                val soft = SelloTheme.colors.inkSoft
                Text("150.000 ", style = small, color = soft)
                HelpTerm("base", {}, style = small, color = soft)
                Text(" + 80.000 ", style = small, color = soft)
                HelpTerm("sobrante", {}, style = small, color = soft)
            }
        }
    }

    @Test
    fun helpSheet() = capture("help-sheet") {
        Box(Modifier.height(640.dp)) {
            HelpSheet(
                title = "Cómo leer tu mes",
                entries = listOf(
                    HelpEntry(
                        "Restante",
                        "Límite del mes menos lo gastado: lo que aún puedes gastar."
                    ),
                    HelpEntry(
                        "Por día",
                        "El restante repartido entre los días que faltan, incluido hoy."
                    ),
                    HelpEntry(
                        "Límite del mes",
                        "La suma de tus categorías: su base y lo que te sobró o faltó antes."
                    )
                ),
                example = "Límite 230.000 − gastado 100.000 = restante 130.000.\n" +
                    "130.000 entre 7 días ≈ 18.550 por día.",
                onDismiss = {},
                onMore = {}
            )
        }
    }

    @Test
    fun nudgeRows() = capture("nudge-rows") {
        val ink = SelloTheme.colors.ink
        NudgeRow(
            icon = SelloIcon.Settings,
            text = buildAnnotatedString {
                append("¿Dudas? La ")
                withStyle(SpanStyle(color = ink, fontWeight = SelloTheme.type.weight.emphasis)) {
                    append("Guía de Sello")
                }
                append(" está en Configuración.")
            },
            onClose = {}
        )
        NudgeRow(
            icon = SelloIcon.Backup,
            text = buildAnnotatedString {
                append("Tu último respaldo fue hace ")
                withStyle(SpanStyle(color = ink, fontWeight = SelloTheme.type.weight.emphasis)) {
                    append("34 días")
                }
                append(". Tus datos viven solo en este teléfono.")
            },
            link = NudgeAction("No volver a mostrar") {},
            pill = NudgeAction("Ir a Respaldo") {}
        )
        NudgeRow(
            icon = SelloIcon.Info,
            text = "Con Anotar varios guardas muchos gastos de una vez.",
            onClose = {},
            pill = NudgeAction("Probar") {}
        )
    }
}
