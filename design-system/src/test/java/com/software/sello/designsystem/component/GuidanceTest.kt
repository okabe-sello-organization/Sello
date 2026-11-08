package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The guidance parts: what TalkBack hears, what a tap does and how big they are. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuidanceTest {
    @get:Rule
    val rule = createComposeRule()

    private fun SemanticsNodeInteraction.assertButtonOfAtLeast48dp(scheme: String) {
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val node = fetchSemanticsNode()
        with(node.layoutInfo.density) {
            assertTrue(scheme, node.touchBoundsInRoot.width.toDp() >= 48.dp)
            assertTrue(scheme, node.touchBoundsInRoot.height.toDp() >= 48.dp)
        }
    }

    @Test
    fun cardHelpIsAButtonNamedByItsCallerWithA48dpTarget() {
        var clicks = 0
        rule.inEveryScheme(reset = { clicks = 0 }, content = {
            CardHelp("Cómo leer este resumen", { clicks++ }, Modifier.testTag("help"))
        }) { scheme ->
            val node = rule.onNodeWithTag("help")
            node.assertContentDescriptionEquals("Cómo leer este resumen")
            node.assertButtonOfAtLeast48dp(scheme)
            // The glyph is drawn, not read: TalkBack hears the name only.
            rule.onAllNodes(hasText("?")).assertCountEquals(0)
            node.performClick()
            assertEquals(scheme, 1, clicks)
        }
    }

    @Test
    @Config(qualifiers = "mdpi")
    fun cardHelpIsDrawnTheSameAtEveryFontSize() {
        var fontScale by mutableFloatStateOf(1f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SelloTheme { CardHelp("Cómo leer este resumen", {}, Modifier.testTag("help")) }
            }
        }
        val normal = rule.onNodeWithTag("help").drawnBitmap()
        assertEquals(20, normal.width)
        fontScale = 2f
        rule.waitForIdle()
        // The disc and its glyph are drawn the same, pixel for pixel, at twice the font size.
        assertTrue(normal.sameAs(rule.onNodeWithTag("help").drawnBitmap()))
    }

    @Test
    fun helpTermIsTheWordThenQueSignificaAsOneButton() {
        var clicks = 0
        rule.inEveryScheme(reset = { clicks = 0 }, content = {
            HelpTerm("sobrante", { clicks++ }, Modifier.testTag("term"))
        }) { scheme ->
            val node = rule.onNodeWithTag("term")
            node.assertContentDescriptionEquals("sobrante. Qué significa")
            node.assertButtonOfAtLeast48dp(scheme)
            // Read once: the word is not a second stop of its own.
            rule.onAllNodes(hasText("sobrante")).assertCountEquals(0)
            node.performClick()
            assertEquals(scheme, 1, clicks)
        }
    }

    @Test
    fun helpSheetReadsEachTermWithItsExplanationAndOffersTheGuia() {
        var more = 0
        var dismissed = 0
        rule.inEveryScheme(reset = {
            more = 0
            dismissed = 0
        }, content = {
            Box(Modifier.height(700.dp)) {
                HelpSheet(
                    title = "Cómo leer tu mes",
                    entries = listOf(
                        HelpEntry("Restante", "Límite del mes menos lo gastado."),
                        HelpEntry("Por día", "El restante repartido entre los días que faltan.")
                    ),
                    onDismiss = { dismissed++ },
                    example = "Límite 230.000 − gastado 100.000 = restante 130.000",
                    onMore = { more++ }
                )
            }
        }) { scheme ->
            rule.onNode(isHeading() and hasText("Cómo leer tu mes")).assertExists()
            rule.onNodeWithContentDescription("Restante. Límite del mes menos lo gastado.")
                .assertExists()
            rule.onNodeWithContentDescription(
                "Por día. El restante repartido entre los días que faltan."
            ).assertExists()
            rule.onNodeWithText("Límite 230.000 − gastado 100.000 = restante 130.000")
                .assertExists()
            rule.onNodeWithText("Ver más en la Guía de Sello").performClick()
            assertEquals(scheme, 1, more)
            // The paper keeps it open; the scrim above it closes it.
            rule.onNodeWithText("Cómo leer tu mes").performClick()
            assertEquals(scheme, 0, dismissed)
            rule.onRoot().performTouchInput { click(Offset(centerX, 4f)) }
            assertEquals(scheme, 1, dismissed)
        }
    }

    @Test
    fun helpSheetWithoutExampleOrLinkShowsNeither() {
        rule.setContent {
            SelloTheme {
                HelpSheet("Sobrante", listOf(HelpEntry("Sobrante", "Lo que no gastaste.")), {})
            }
        }
        rule.onAllNodes(hasText("Ver más en la Guía de Sello")).assertCountEquals(0)
        rule.onNodeWithContentDescription("Sobrante. Lo que no gastaste.").assertExists()
    }

    @Test
    fun nudgeRowClosesFollowsItsLinkAndItsPill() {
        var closed = 0
        var never = 0
        var go = 0
        rule.inEveryScheme(reset = {
            closed = 0
            never = 0
            go = 0
        }, content = {
            NudgeRow(
                icon = SelloIcon.Backup,
                text = "Tu último respaldo fue hace 34 días.",
                onClose = { closed++ },
                link = NudgeAction("No volver a mostrar") { never++ },
                pill = NudgeAction("Ir a Respaldo") { go++ }
            )
        }) { scheme ->
            rule.onNodeWithText("Tu último respaldo fue hace 34 días.").assertExists()
            val close = rule.onNodeWithContentDescription("Cerrar")
            close.assertButtonOfAtLeast48dp(scheme)
            close.performClick()
            val link = rule.onNodeWithText("No volver a mostrar")
            link.assertButtonOfAtLeast48dp(scheme)
            link.performClick()
            val pill = rule.onNodeWithText("Ir a Respaldo")
            pill.assertButtonOfAtLeast48dp(scheme)
            pill.performClick()
            assertEquals(scheme, listOf(1, 1, 1), listOf(closed, never, go))
        }
    }

    @Test
    fun nudgeRowWithoutActionsHasNoButtons() {
        rule.setContent {
            SelloTheme { NudgeRow(SelloIcon.Settings, "La Guía de Sello está en Configuración.") }
        }
        rule.onAllNodesWithContentDescription("Cerrar").assertCountEquals(0)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
            .assertCountEquals(0)
    }
}
