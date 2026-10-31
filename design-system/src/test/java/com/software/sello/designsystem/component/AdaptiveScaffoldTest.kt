package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    /** One window dp is this many pixels, so even an 840dp window fits the test screen. */
    private val scale = 1f
    private val topInset = 24
    private val bottomInset = 30

    private val selections = mutableListOf<String>()
    private var docked = 0
    private var selected = "recibo"

    private fun show(
        width: Int,
        height: Int,
        withSecondPane: Boolean = true,
        insets: WindowInsets = WindowInsets(top = topInset, bottom = bottomInset),
        withTabs: Boolean = true
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("window")) {
                        SelloScaffold(
                            title = "Octubre 2026",
                            navigation = SelloNavigation(
                                listOf(
                                    SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                                    SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                                ),
                                selected
                            ) { selections += it }.takeIf { withTabs },
                            dock = SelloDock("Anotar un gasto", "Anotar", { docked++ }),
                            windowInsets = insets,
                            secondaryPane = if (withSecondPane) {
                                { padding -> Pane("second", padding) }
                            } else {
                                null
                            }
                        ) { padding -> Pane("first", padding) }
                    }
                }
            }
        }
    }

    @Composable
    private fun Pane(name: String, padding: PaddingValues) {
        Column(
            Modifier
                .fillMaxSize()
                .testTag("$name:scroll")
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            Spacer(Modifier.height(1200.dp))
            Box(Modifier.fillMaxWidth().height(40.dp).testTag("$name:last"))
        }
    }

    private fun bounds(tag: String): Rect =
        rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun exists(tag: String) = rule.onAllNodes(hasTestTag(tag))
        .fetchSemanticsNodes().isNotEmpty()

    private fun near(expected: Float, actual: Float) =
        assertTrue("expected about $expected, was $actual", abs(expected - actual) <= 1.5f)

    @Test
    fun anUprightPhoneStacksContentDockAndTabBarWithoutOverlap() {
        show(360, 700)
        assertTrue(exists(SCAFFOLD_BAR_TAG) && exists(SCAFFOLD_DOCK_TAG))
        assertTrue(!exists(SCAFFOLD_RAIL_TAG) && !exists(SCAFFOLD_SECONDARY_TAG))
        val window = bounds("window")
        val content = bounds(SCAFFOLD_PRIMARY_TAG)
        val dock = bounds(SCAFFOLD_DOCK_TAG)
        val bar = bounds(SCAFFOLD_BAR_TAG)
        assertTrue("content ends before the dock", content.bottom <= dock.top)
        assertTrue("dock ends before the tab bar", dock.bottom <= bar.top)
        near(window.bottom, bar.bottom)
        // 64 for the bar plus the system inset it keeps clear.
        near(64f + bottomInset, bar.height)
        assertTrue(
            "top bar starts below the status bar",
            content.top >= window.top + topInset + 50f
        )

        // The last thing in the list can be brought fully above the dock.
        rule.onNodeWithTag("first:scroll").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 10_000f)
        }
        assertTrue(bounds("first:last").bottom <= dock.top)
    }

    @Test
    fun navigationAndDockOnlyReportWhatWasPressed() {
        show(360, 700)
        rule.onNodeWithText("Recibo").assertIsSelected()
        rule.onNodeWithText("Resumen").assertIsNotSelected()
            .performSemanticsAction(SemanticsActions.OnClick)
        // The scaffold keeps no selection of its own: nothing changes until the app says so.
        rule.onNodeWithText("Recibo").assertIsSelected()
        rule.onNodeWithText("Recibo").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("resumen", "recibo"), selections)

        rule.onNodeWithContentDescription("Anotar un gasto")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, docked)
    }

    @Test
    fun aShortWindowUsesARailAndTwoEvenPanesThatClearTheSystemBar() {
        show(760, 400)
        assertTrue(exists(SCAFFOLD_RAIL_TAG) && exists(SCAFFOLD_SECONDARY_TAG))
        assertTrue(!exists(SCAFFOLD_BAR_TAG) && !exists(SCAFFOLD_DOCK_TAG))
        val rail = bounds(SCAFFOLD_RAIL_TAG)
        val first = bounds(SCAFFOLD_PRIMARY_TAG)
        val second = bounds(SCAFFOLD_SECONDARY_TAG)
        near(rail.right, first.left)
        near(first.right, second.left)
        near(first.width, second.width)
        near(bounds("window").right, second.right)

        // The rail's "+" replaces the dock, and both panes scroll on their own.
        rule.onNodeWithContentDescription("Anotar un gasto")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, docked)
        rule.onNodeWithText("Resumen").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("resumen"), selections)
        for (pane in listOf("first", "second")) {
            // Scrolled to its very end, the pane's last item sits above the system bar.
            rule.onNodeWithTag("$pane:scroll").performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, 10_000f)
            }
            val last = bounds("$pane:last")
            assertTrue(
                "$pane clears the system bar",
                last.bottom <= bounds("window").bottom - bottomInset
            )
        }
    }

    @Test
    fun aSystemBarOrCutoutAtTheSideCoversNeitherPaneNorTheDock() {
        // A phone on its side: a cutout at one edge, the system's buttons at the other.
        show(760, 400, insets = WindowInsets(left = 20, top = 24, right = 48), withTabs = false)

        val window = bounds("window")
        assertTrue(!exists(SCAFFOLD_RAIL_TAG) && exists(SCAFFOLD_DOCK_TAG))
        for (tag in listOf(SCAFFOLD_PRIMARY_TAG, SCAFFOLD_SECONDARY_TAG, SCAFFOLD_DOCK_TAG)) {
            val part = bounds(tag)
            assertTrue("$tag starts at ${part.left}", part.left >= window.left + 20f)
            assertTrue("$tag ends at ${part.right}", part.right <= window.right - 48f)
        }
    }

    @Test
    fun besideARailTheFarEdgeIsStillKeptClearOfTheSystemBar() {
        show(760, 400, insets = WindowInsets(left = 20, top = 24, right = 48))

        val window = bounds("window")
        assertTrue(bounds(SCAFFOLD_RAIL_TAG).left >= window.left)
        assertTrue(bounds(SCAFFOLD_SECONDARY_TAG).right <= window.right - 48f)
    }

    @Test
    fun aMediumWindowKeepsTheTabBarAndCentresOneColumnOf560() {
        show(700, 900)
        assertTrue(exists(SCAFFOLD_BAR_TAG) && exists(SCAFFOLD_DOCK_TAG))
        assertTrue(!exists(SCAFFOLD_RAIL_TAG) && !exists(SCAFFOLD_SECONDARY_TAG))
        val window = bounds("window")
        val content = bounds(SCAFFOLD_PRIMARY_TAG)
        near(560f, content.width)
        near(content.left - window.left, window.right - content.right)
    }

    @Test
    fun anExpandedWindowUsesARailA400ListPaneAndTheRestForDetail() {
        show(900, 700)
        assertTrue(exists(SCAFFOLD_RAIL_TAG) && !exists(SCAFFOLD_BAR_TAG))
        val rail = bounds(SCAFFOLD_RAIL_TAG)
        val first = bounds(SCAFFOLD_PRIMARY_TAG)
        val second = bounds(SCAFFOLD_SECONDARY_TAG)
        near(80f, rail.width)
        near(700f, rail.height)
        near(400f, first.width)
        near(900f - 80f - 400f, second.width)
    }

    @Test
    fun withoutADetailPaneAWideWindowStillShowsOneColumn() {
        show(900, 700, withSecondPane = false)
        assertTrue(exists(SCAFFOLD_RAIL_TAG) && !exists(SCAFFOLD_SECONDARY_TAG))
        near(900f - 80f, bounds(SCAFFOLD_PRIMARY_TAG).width)
    }

    @Test
    fun theContentWritesInTheThemeInkSoPlainTextShowsInTheDark() {
        var expected = Color.Unspecified
        var actual = Color.Unspecified
        rule.setContent {
            SelloTheme(darkTheme = true, reducedMotion = true) {
                expected = SelloTheme.colors.ink
                SelloScaffold(title = "Aportar") { actual = LocalContentColor.current }
            }
        }
        rule.waitForIdle()
        assertEquals(expected, actual)
    }
}
