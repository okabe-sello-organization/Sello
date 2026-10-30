package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The notice with its one action, and a line that can be pressed, swiped away or asked for a menu. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CorrectionControlsTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val done = mutableListOf<String>()

    // --- The notice ---

    @Test
    fun theNoticeSaysItsMessageAndItsActionReportsEachPressOnce() {
        rule.inEveryScheme(
            reset = { done.clear() },
            content = {
                SelloNotice(
                    "Gasto borrado",
                    actionLabel = "Deshacer",
                    onAction = { done += "undo" },
                    spoken = "Gasto borrado. Puedes deshacerlo durante 6 segundos."
                )
            }
        ) {
            rule.onNodeWithText("Gasto borrado").assertIsDisplayed()
            rule.onNodeWithText("Deshacer").assert(isButton).performClick()
            assertEquals(it, listOf("undo"), done)
            val height = rule.onNodeWithText("Deshacer").getUnclippedBoundsInRoot().height
            assertTrue("$it: the action is $height tall", height >= 48.dp)
        }
    }

    @Test
    fun whatIsReadAloudCanSayMoreThanWhatIsShownAndIsAnnouncedByItself() {
        rule.setContent {
            SelloTheme {
                SelloNotice(
                    "Gasto borrado",
                    actionLabel = "Deshacer",
                    onAction = {},
                    spoken = "Gasto borrado. Puedes deshacerlo durante 6 segundos."
                )
            }
        }

        val said = rule.onNodeWithText("Gasto borrado").fetchSemanticsNode().config
        assertEquals(
            listOf("Gasto borrado. Puedes deshacerlo durante 6 segundos."),
            said.getOrNull(SemanticsProperties.ContentDescription)
        )
        assertTrue(said.getOrNull(SemanticsProperties.LiveRegion) != null)
    }

    @Test
    fun aBusyNoticeKeepsItsActionInViewButItCannotBePressed() {
        rule.setContent {
            SelloTheme {
                SelloNotice("Gasto borrado", actionLabel = "Deshacer", onAction = {
                    done += "undo"
                }, busy = true)
            }
        }

        rule.onNodeWithText("Deshacer").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText("Deshacer").performClick()
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun aNoticeWithNothingToDoHasNoButton() {
        rule.setContent { SelloTheme { SelloNotice("Gasto actualizado") } }

        rule.onNodeWithText("Gasto actualizado").assertIsDisplayed()
        rule.onAllNodes(isButton).assertCountEquals(0)
    }

    @Test
    fun theScaffoldShowsTheNoticeOverTheContentAndAboveTheDock() {
        rule.setContent {
            SelloTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    SelloScaffold(
                        title = "Octubre 2026",
                        dock = SelloDock("Anotar un gasto", "Anotar", onClick = {}),
                        notice = {
                            SelloNotice("Gasto borrado", Modifier.testTag("notice"), "Deshacer", {})
                        }
                    ) { Box(Modifier.fillMaxSize().testTag("content")) { Text("Recibo") } }
                }
            }
        }

        val notice = rule.onNodeWithTag("notice").assertIsDisplayed().getUnclippedBoundsInRoot()
        val dock = rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).getUnclippedBoundsInRoot()
        val content = rule.onNodeWithTag("content").getUnclippedBoundsInRoot()
        assertTrue(
            "notice ends at ${notice.bottom}, dock starts at ${dock.top}",
            notice.bottom <= dock.top
        )
        // Over the content, which still reaches down behind it.
        assertTrue(notice.top >= content.top && notice.bottom <= content.bottom)
        assertTrue(content.bottom <= dock.top)
    }
}
