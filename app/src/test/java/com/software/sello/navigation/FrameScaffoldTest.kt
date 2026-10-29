package com.software.sello.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.Blotter
import com.software.sello.designsystem.component.SCAFFOLD_BAND_TAG
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The frame with a v2 top bar and an optional ribbon, one window dp drawn as one pixel.
 * The window can be larger than the screen, so positions are measured from its own top.
 * The slip under the band must still start on the band, as with the old title row, and
 * a ribbon may sit under either.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    private val topInset = 24
    private var opened = 0

    private fun show(withTopBar: Boolean, withRibbon: Boolean, fontScale: Float = 1f) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(360.dp, 700.dp).testTag("window")) {
                        SelloScaffold(
                            title = "Octubre 2026",
                            blotter = Blotter.Short,
                            navigation = SelloNavigation(
                                listOf(
                                    SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                                    SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                                ),
                                "recibo"
                            ) {},
                            dock = SelloDock("Anotar un gasto", "Anotar", {}),
                            windowInsets = WindowInsets(top = topInset),
                            topBar = if (withTopBar) {
                                { Bar() }
                            } else {
                                null
                            },
                            ribbon = if (withRibbon) {
                                { Box(Modifier.fillMaxWidth().height(40.dp).testTag("ribbon")) }
                            } else {
                                null
                            }
                        ) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding).testTag("content")) {
                                Text("contenido")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Bar() {
        MonthBar(
            "Octubre",
            "2026",
            { opened++ },
            Modifier.testTag("bar"),
            description = "Cambiar de mes. Mes actual: Octubre 2026",
            buttonModifier = Modifier.testTag("month")
        )
    }

    private fun bounds(tag: String): Rect =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    private fun near(expected: Float, actual: Float) =
        assertTrue("expected about $expected, was $actual", abs(expected - actual) <= 1.5f)

    @Test
    fun withoutAMonthBarTheTitleRowAndBandAreAsBefore() {
        show(withTopBar = false, withRibbon = false)

        rule.onNodeWithText("Octubre 2026").assertExists()
        // The band reaches 100dp below the status bar; the title row takes 50 of it.
        val top = bounds("window").top
        near(top + topInset + 100f, bounds(SCAFFOLD_BAND_TAG).bottom)
        near(top + topInset + 50f, bounds("content").top)
    }

    @Test
    fun aMonthBarTakesThePlaceOfTheTitleRowBelowTheStatusBar() {
        show(withTopBar = true, withRibbon = false)

        rule.onNodeWithText("Octubre 2026").assertDoesNotExist()
        val bar = bounds("bar")
        near(bounds("window").top + topInset, bar.top)
        near(bar.bottom, bounds("content").top)
    }

    @Test
    fun underAMonthBarTheContentStillStartsFiftyDpOnTheBand() {
        show(withTopBar = true, withRibbon = false)

        near(bounds("content").top + 50f, bounds(SCAFFOLD_BAND_TAG).bottom)
    }

    @Test
    fun aRibbonSitsOnTheBandUnderTheBarAndTheContentStillStartsOnTheBand() {
        show(withTopBar = true, withRibbon = true)

        val bar = bounds("bar")
        val ribbon = bounds("ribbon")
        val content = bounds("content")
        assertTrue("ribbon under the bar", ribbon.top >= bar.bottom - 1f)
        assertTrue("content under the ribbon", content.top >= ribbon.bottom - 1f)
        near(content.top + 50f, bounds(SCAFFOLD_BAND_TAG).bottom)
    }

    @Test
    fun aRibbonSitsOnTheBandUnderTheTitleRowAndTheContentStillStartsOnTheBand() {
        show(withTopBar = false, withRibbon = true)

        val title = rule.onNodeWithText("Octubre 2026").assertExists().fetchSemanticsNode()
            .boundsInRoot
        val ribbon = bounds("ribbon")
        val content = bounds("content")
        assertTrue("ribbon under the title", ribbon.top >= title.bottom - 1f)
        assertTrue("content under the ribbon", content.top >= ribbon.bottom - 1f)
        near(content.top + 50f, bounds(SCAFFOLD_BAND_TAG).bottom)
    }

    @Test
    fun atTwiceTheFontSizeARibbonUnderTheTitleRowStillLeavesTheContentOnTheBand() {
        show(withTopBar = false, withRibbon = true, fontScale = 2f)

        assertTrue(bounds("content").top >= bounds("ribbon").bottom - 1f)
        near(bounds("content").top + 50f, bounds(SCAFFOLD_BAND_TAG).bottom)
    }

    @Test
    fun withoutARibbonThereIsNoRibbonSpace() {
        show(withTopBar = true, withRibbon = false)

        assertTrue(!exists("ribbon"))
        near(bounds("bar").bottom, bounds("content").top)
    }

    @Test
    fun theMonthButtonCarriesTheCallersModifierAndOpensThePickerOnce() {
        show(withTopBar = true, withRibbon = false)

        rule.onNodeWithTag("month").performClick()

        assertEquals(1, opened)
        val month = bounds("month")
        val bar = bounds("bar")
        assertTrue("the button is narrower than the bar", month.width < bar.width - 96f)
    }

    @Test
    fun atTwiceTheFontSizeTheContentStillStartsOnTheBand() {
        show(withTopBar = true, withRibbon = true, fontScale = 2f)

        near(bounds("content").top + 50f, bounds(SCAFFOLD_BAND_TAG).bottom)
    }
}
