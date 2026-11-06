package com.software.sello.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelloIconButtonTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aSmallIconStillHasA48dpTargetAndSaysItsLabel() {
        var clicks = 0
        rule.inEveryScheme(
            reset = { clicks = 0 },
            content = {
                SelloIconButton(
                    SelloIcon.Close,
                    "Cerrar",
                    { clicks++ },
                    Modifier.testTag("close"),
                    iconSize = 16.dp
                )
            }
        ) { scheme ->
            val node = rule.onNodeWithTag("close")
            node.assertContentDescriptionEquals("Cerrar").assertIsEnabled()
            val semantics = node.fetchSemanticsNode()
            with(semantics.layoutInfo.density) {
                assertTrue(scheme, semantics.touchBoundsInRoot.width.toDp() >= 48.dp)
                assertTrue(scheme, semantics.touchBoundsInRoot.height.toDp() >= 48.dp)
            }
            node.performClick()
            assertEquals(scheme, 1, clicks)
        }
    }

    @Test
    fun aDisabledButtonIgnoresPresses() {
        var clicks = 0
        rule.setContent {
            SelloTheme {
                SelloIconButton(
                    SelloIcon.ArrowBack,
                    "Volver",
                    { clicks++ },
                    Modifier.testTag("back"),
                    enabled = false
                )
            }
        }
        rule.onNodeWithTag("back").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun theIconTakesTheContentColourUnlessGivenATint() {
        val band = Color(0xFF1D3FBF)
        val loss = Color(0xFFB3261E)
        rule.setContent {
            SelloTheme {
                CompositionLocalProvider(LocalContentColor provides band) {
                    Column {
                        Box(Modifier.background(Color.White)) {
                            SelloIconButton(SelloIcon.Close, "a", {}, Modifier.testTag("theme"))
                        }
                        Box(Modifier.background(Color.White)) {
                            SelloIconButton(
                                SelloIcon.Close,
                                "b",
                                {},
                                Modifier.testTag("tinted"),
                                tint = loss
                            )
                        }
                        Box(Modifier.background(Color.White)) {
                            SelloIconButton(
                                SelloIcon.Close,
                                "c",
                                {},
                                Modifier.testTag("disabled"),
                                enabled = false
                            )
                        }
                    }
                }
            }
        }
        val theme = rule.onNodeWithTag("theme").drawnBitmap()
        assertTrue(theme.has(band))
        val tinted = rule.onNodeWithTag("tinted").drawnBitmap()
        assertTrue(tinted.has(loss))
        assertFalse(tinted.has(band))
        // Faded to the disabled alpha over white: never the full ink.
        val disabled = rule.onNodeWithTag("disabled").drawnBitmap()
        assertFalse(disabled.has(band))
        assertTrue(disabled.has(Color.White))
    }

    private fun Bitmap.has(color: Color): Boolean {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels.any { it == color.toArgb() }
    }
}
