package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/**
 * The v2 dock and tab bar as the frame draws them, one window dp drawn as one pixel: a
 * full-width "+ Anotar" button read as "Anotar un gasto", and tabs that say which one is
 * selected, whole at twice the font size on the narrowest phone.
 */
@RunWith(AndroidJUnit4::class)
class BottomControlsTest {
    @get:Rule
    val rule = createComposeRule()

    private var docked = 0
    private val selections = mutableListOf<String>()

    private fun show(
        width: Int = 360,
        height: Int = 700,
        fontScale: Float = 1f,
        dock: SelloDock = SelloDock("Anotar un gasto", "Anotar", { docked++ })
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag("window")) {
                        SelloScaffold(
                            title = "Octubre 2026",
                            navigation = SelloNavigation(
                                listOf(
                                    SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                                    SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                                ),
                                "recibo"
                            ) { selections += it },
                            dock = dock,
                            windowInsets = WindowInsets(0, 0, 0, 0)
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

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun theDockIsOneFullWidthAnotarButtonThatSaysWhatItRecords() {
        show()

        val dock = rule.onNodeWithContentDescription("Anotar un gasto")
            .assert(hasText("Anotar"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        dock.performClick()
        assertEquals(1, docked)
        // Edge to edge inside the content margins, and never a small target.
        val shown = dock.fetchSemanticsNode().boundsInRoot
        val window = bounds("window")
        assertTrue("$shown spans $window", shown.width >= window.width - 24f - 1f)
        assertTrue("$shown is at least 48dp", shown.height >= 48f)
        // There is no second, field-like "Anotar un gasto" text beside the button.
        rule.onNodeWithText("Anotar un gasto").assertDoesNotExist()
    }

    @Test
    fun aDockThatCannotBeUsedStaysInPlaceAndSaysWhyInsteadOfOpeningAnything() {
        show(dock = archivedDock)

        val dock = rule.onNodeWithContentDescription(ARCHIVED_DOCK_LABEL)
            .assert(hasText("Archivada"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsNotEnabled()
        dock.performClick()
        assertEquals(0, docked)
        // Still the full-width dock, never a small target.
        val shown = dock.fetchSemanticsNode().boundsInRoot
        val window = bounds("window")
        assertTrue("$shown spans $window", shown.width >= window.width - 24f - 1f)
        assertTrue("$shown is at least 48dp", shown.height >= 48f)
    }

    @Test
    fun onARailTheDockThatCannotBeUsedIsADisabledButtonThatSaysWhy() {
        show(width = 900, height = 400, dock = archivedDock)

        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription(ARCHIVED_DOCK_LABEL)
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, docked)
    }

    private val archivedDock
        get() = SelloDock(
            ARCHIVED_DOCK_LABEL,
            "Archivada",
            { docked++ },
            icon = SelloIcon.Block,
            enabled = false
        )

    @Test
    fun theSelectedTabIsMarkedAndTheOtherIsNotAndTheyOnlyReportPresses() {
        show()

        rule.onNodeWithText("Recibo").assertIsSelected()
        rule.onNodeWithText("Resumen").assertIsNotSelected().performClick()

        assertEquals(listOf("resumen"), selections)
        rule.onNodeWithText("Recibo").assertIsSelected()
    }

    @Test
    fun atTwiceTheFontSizeOnTheNarrowestPhoneBothLabelsAndTheDockAreWhole() {
        show(width = 320, height = 640, fontScale = 2f)

        listOf("Recibo", "Resumen").forEach { label ->
            val node = rule.onNodeWithText(label).fetchSemanticsNode()
            val text = node.config.getOrNull(SemanticsProperties.Text)!!.single().text
            assertEquals(label, text)
            assertTrue("$label fits", node.boundsInRoot.right <= 320f)
            assertTrue("$label target", node.touchBoundsInRoot.height >= 48f)
        }
        val dock = bounds(SCAFFOLD_DOCK_TAG)
        val bar = bounds(SCAFFOLD_BAR_TAG)
        assertTrue("content ends before the dock", bounds("content").bottom <= dock.top)
        assertTrue("dock ends before the tab bar", dock.bottom <= bar.top)
        assertTrue("$bar inside the window", bar.bottom <= 640f + 1f)
        assertTrue(
            rule.onAllNodes(hasTestTag(SCAFFOLD_PRIMARY_TAG)).fetchSemanticsNodes().isNotEmpty()
        )
    }
}

private const val ARCHIVED_DOCK_LABEL = "No se puede anotar: la categoría está archivada"
