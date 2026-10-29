package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Recibo's reserved states: the footnote that opens the payments, and Revisar's warn ink. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReservedPartsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theFootnoteIsOneButtonAt48dpThatSaysItsSentenceAndOpensWhatItNames() {
        var opened = 0
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            Footnote(
                AnnotatedString("Incluye 200.000 separado para 1 pago previsto"),
                { opened++ },
                Modifier.testTag("footnote")
            )
        }) { scheme ->
            val node = rule.onNodeWithTag("footnote").fetchSemanticsNode()
            assertNotNull(scheme, node.config.getOrNull(SemanticsActions.OnClick))
            assertTrue(
                scheme,
                rule.onNodeWithTag("footnote").getUnclippedBoundsInRoot().height >= 48.dp
            )
            val said = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString {
                it.text
            }
            assertTrue("$scheme: $said", "Incluye 200.000 separado para 1 pago previsto" in said)
            rule.onNodeWithTag("footnote").performClick()
        }
        assertEquals(2, opened)
    }

    @Test
    fun revisarIsStampedInTheWarnInkInEveryScheme() {
        var warn by mutableStateOf(Color.Unspecified)
        rule.inEveryScheme(content = {
            warn = SelloTheme.colors.semantic.warn
            Box(Modifier.testTag("stamp")) { Stamp("Revisar", StampInk.Warn) }
        }) { scheme ->
            val pixels = rule.onNodeWithTag("stamp").captureToImage().toPixelMap()
            var inked = 0
            for (x in 0 until pixels.width) {
                for (y in 0 until pixels.height) {
                    val pixel = pixels[x, y]
                    if (abs(
                            pixel.red - warn.red
                        ) < 0.06f && abs(pixel.green - warn.green) < 0.06f &&
                        abs(pixel.blue - warn.blue) < 0.06f
                    ) {
                        inked++
                    }
                }
            }
            assertTrue("$scheme: $inked warn pixels", inked > 50)
        }
    }
}
