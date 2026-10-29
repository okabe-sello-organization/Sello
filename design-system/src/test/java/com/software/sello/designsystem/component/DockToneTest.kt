package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Metas' dock is green with a flag, so it cannot be taken for Anotar (v2 Metas): the
 * same tone on the full-width dock and on the rail's button, and brand by default.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A screen larger than the 360x700 stage, so it is drawn whole and its pixels can be read.
@Config(qualifiers = "w800dp-h800dp-mdpi")
class DockToneTest {
    @get:Rule
    val rule = createComposeRule()

    private var docked = 0
    private var brand = Color.Unspecified
    private var gain = Color.Unspecified
    private var ink = Color.Unspecified

    private fun show(dock: SelloDock, width: Int = 360, height: Int = 700) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                SelloTheme(reducedMotion = true) {
                    brand = SelloTheme.colors.brand
                    gain = SelloTheme.colors.semantic.gain
                    ink = SelloTheme.colors.ink
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        SelloScaffold(
                            title = "Metas",
                            navigation = SelloNavigation(
                                listOf(
                                    SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                                    SelloDestination("metas", "Metas", SelloIcon.Flag)
                                ),
                                "metas"
                            ) {},
                            dock = dock,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) { Text("contenido") }
                        }
                    }
                }
            }
        }
    }

    private val metas get() = SelloDock(
        label = "Aportar a una meta",
        actionLabel = "Aportar a una meta",
        onClick = { docked++ },
        tone = ButtonTone.Gain,
        icon = SelloIcon.Flag
    )

    /** The colour a little inside the leading edge, clear of the icon and the label. */
    private fun fill(description: String): Color {
        val pixels = rule.onNodeWithContentDescription(description).captureToImage().toPixelMap()
        return pixels[pixels.width / 12, pixels.height / 2]
    }

    private fun assertSame(expected: Color, actual: Color) = assertTrue(
        "expected $expected, was $actual",
        abs(expected.red - actual.red) < 0.02f &&
            abs(expected.green - actual.green) < 0.02f &&
            abs(expected.blue - actual.blue) < 0.02f
    )

    @Test
    fun theDockTakesTheGainToneItIsGiven() {
        show(metas)

        assertSame(gain, fill("Aportar a una meta"))
        rule.onNodeWithContentDescription("Aportar a una meta")
            .assert(hasText("Aportar a una meta"))
            .performClick()
        assertEquals(1, docked)
    }

    @Test
    fun theRailsButtonTakesTheSameTone() {
        // A short window has a rail; it still fits the 320×640 test screen whole.
        show(metas, width = 320, height = 400)
        rule.onNodeWithTag(SCAFFOLD_RAIL_TAG).assertExists()

        // Before pressing it: a press leaves a darker ripple behind.
        assertSame(gain, fill("Aportar a una meta"))
        rule.onNodeWithContentDescription("Aportar a una meta").performClick()
        assertEquals(1, docked)
    }

    @Test
    fun withoutAToneTheDockIsStillBrand() {
        show(SelloDock("Anotar un gasto", "Anotar", { docked++ }))

        assertSame(brand, fill("Anotar un gasto"))
    }

    @Test
    fun inversionesDockIsInkSoItIsNeverTakenForAnotarOrMetas() {
        show(
            SelloDock(
                "Anotar movimiento",
                "Anotar movimiento",
                { docked++ },
                tone = ButtonTone.Ink,
                icon = SelloIcon.Add
            )
        )

        assertSame(ink, fill("Anotar movimiento"))
        rule.onNodeWithContentDescription("Anotar movimiento").performClick()
        assertEquals(1, docked)
    }
}
