package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The contract of [StatusSlip]: its kind, not the caller, decides how it looks. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusSlipTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val empty = StatusKind.Empty(SelloIcon.ReceiptLong)

    /** One slip of [kind] with an action, tagged "slip", its words fixed per kind. */
    @Composable
    private fun Sample(kind: StatusKind, onAction: () -> Unit = {}, busy: Boolean = false) {
        StatusSlip(
            kind = kind,
            message = MESSAGE,
            modifier = Modifier.testTag("slip"),
            title = if (kind is StatusKind.Empty) "Aún no hay gastos" else null,
            action = StatusAction(
                label = if (kind is StatusKind.Empty) "Anotar" else null,
                busy = busy,
                onClick = onAction
            )
        )
    }

    @Test
    fun eachKindDecidesItsIconInkLayoutAndButton() {
        // kind to (layout, icon, icon ink, message ink, button, announced)
        val expected = listOf(
            empty to listOf(
                StatusLayout.Centred,
                SelloIcon.ReceiptLong,
                StatusInk.Brand,
                StatusInk.InkSoft,
                ButtonKind.Outlined,
                false
            ),
            StatusKind.LoadFailed to listOf(
                StatusLayout.Centred,
                SelloIcon.Error,
                StatusInk.Brand,
                StatusInk.InkSoft,
                ButtonKind.Outlined,
                true
            ),
            StatusKind.Older to listOf(
                StatusLayout.Inline,
                null,
                StatusInk.Warn,
                StatusInk.Warn,
                ButtonKind.Text,
                true
            ),
            StatusKind.ActionFailed to listOf(
                StatusLayout.Inline,
                SelloIcon.Error,
                StatusInk.Loss,
                StatusInk.Loss,
                ButtonKind.Outlined,
                true
            ),
            StatusKind.Unknown to listOf(
                StatusLayout.Inline,
                SelloIcon.Info,
                StatusInk.Warn,
                StatusInk.Warn,
                ButtonKind.Outlined,
                true
            ),
            StatusKind.Gone to listOf(
                StatusLayout.Centred,
                SelloIcon.Block,
                StatusInk.Brand,
                StatusInk.InkSoft,
                ButtonKind.Outlined,
                false
            )
        )
        expected.forEach { (kind, want) ->
            val look = kind.look()
            assertEquals(
                "$kind",
                want,
                listOf(
                    look.layout,
                    look.icon,
                    look.iconInk,
                    look.messageInk,
                    look.button,
                    look.announced
                )
            )
        }
        assertEquals(SelloIcon.Insights, StatusKind.Empty(SelloIcon.Insights).look().icon)
    }

    @Test
    fun theMessageIsDrawnInTheKindsInk() {
        val inks = mutableMapOf<String, List<Color>>()
        val kinds = listOf(
            empty,
            StatusKind.LoadFailed,
            StatusKind.Older,
            StatusKind.ActionFailed,
            StatusKind.Unknown,
            StatusKind.Gone
        )
        var kind by mutableStateOf<StatusKind>(empty)
        var colors: List<Color> = emptyList()
        rule.inEveryScheme(
            content = {
                val c = SelloTheme.colors
                colors = listOf(c.brand, c.inkSoft, c.semantic.warn, c.semantic.loss)
                Sample(kind)
            }
        ) { scheme ->
            val byKind = kinds.map { next ->
                kind = next
                rule.waitForIdle()
                messageColor()
            }
            inks[scheme] = byKind
            val (_, inkSoft, warn, loss) = colors
            assertEquals(scheme, listOf(inkSoft, inkSoft, warn, loss, warn, inkSoft), byKind)
            kind = empty
        }
        assertEquals(2, inks.size)
        assertTrue(inks.values.first() != inks.values.last())
    }

    private fun messageColor(): Color {
        val node = rule.onNode(hasText(MESSAGE)).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single().layoutInput.style.color
    }

    @Test
    fun centredKindsCentreTheirWordsUnderTheIconAndInlineKindsStartThem() {
        var kind by mutableStateOf<StatusKind>(empty)
        rule.inEveryScheme(content = { Sample(kind) }) { scheme ->
            listOf(empty, StatusKind.LoadFailed, StatusKind.Gone).forEach { next ->
                kind = next
                rule.waitForIdle()
                val slip = rule.onNodeWithTag("slip").getUnclippedBoundsInRoot()
                val icon = rule.onNode(
                    hasAnyAncestor(hasTestTag("slip")) and
                        hasTestTag(STATUS_ICON_TAG)
                ).getUnclippedBoundsInRoot()
                val words = rule.onNode(hasText(MESSAGE)).getUnclippedBoundsInRoot()
                val middle = (slip.left + slip.right) / 2
                assertTrue(
                    "$scheme $next icon centred",
                    ((icon.left + icon.right) / 2 - middle).value in -1f..1f
                )
                assertTrue(
                    "$scheme $next words centred",
                    ((words.left + words.right) / 2 - middle).value in -1f..1f
                )
                assertTrue("$scheme $next icon above", icon.bottom <= words.top)
            }
            listOf(StatusKind.Older, StatusKind.ActionFailed, StatusKind.Unknown).forEach { next ->
                kind = next
                rule.waitForIdle()
                val slip = rule.onNodeWithTag("slip").getUnclippedBoundsInRoot()
                val words = rule.onNode(hasText(MESSAGE)).getUnclippedBoundsInRoot()
                // The words start near the slip's edge: after its padding, and the icon if any.
                assertTrue("$scheme $next starts inline", words.left - slip.left < 80.dp)
                val icons = rule.onAllNodes(hasTestTag(STATUS_ICON_TAG), useUnmergedTree = true)
                    .fetchSemanticsNodes().size
                assertEquals("$scheme $next icon", if (next == StatusKind.Older) 0 else 1, icons)
            }
            kind = empty
        }
    }

    @Test
    fun theIconIsDecorative() {
        rule.inEveryScheme(content = {
            Column {
                Sample(StatusKind.Gone)
                Sample(StatusKind.Unknown)
            }
        }) {
            assertEquals(
                it,
                0,
                rule.onAllNodes(hasContentDescription("", substring = true))
                    .fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun eachKindHasItsOwnDefaultWords() {
        // kind to (title or null, action label)
        val expected = listOf(
            StatusKind.LoadFailed to ("No se pudo cargar" to "Reintentar"),
            StatusKind.Older to (null to "Reintentar"),
            StatusKind.ActionFailed to (null to "Reintentar"),
            StatusKind.Unknown to (null to "Comprobar"),
            StatusKind.Gone to ("Ya no está" to "Volver")
        )
        var kind by mutableStateOf<StatusKind>(StatusKind.LoadFailed)
        rule.inEveryScheme(content = { Sample(kind) }) { scheme ->
            expected.forEach { (next, words) ->
                kind = next
                rule.waitForIdle()
                val (title, label) = words
                if (title != null) rule.onNodeWithText(title).assertIsDisplayed()
                rule.onNodeWithText(label).assert(isButton)
                val headings = rule.onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
                ).fetchSemanticsNodes().size
                assertEquals("$scheme $next headings", if (title == null) 0 else 1, headings)
            }
            kind = StatusKind.LoadFailed
        }
    }

    @Test
    fun aCallersTitleAndLabelReplaceTheKindsOwn() {
        var checks = 0
        rule.inEveryScheme(reset = { checks = 0 }, content = {
            StatusSlip(
                kind = StatusKind.Unknown,
                message = "Aún no sabemos si se guardó.",
                title = "Respaldo sin confirmar",
                action = StatusAction(label = "Comprobar respaldo") { checks++ }
            )
        }) {
            rule.onNodeWithText("Respaldo sin confirmar").assertIsDisplayed()
            rule.onNodeWithText("Comprobar respaldo").assert(isButton).performClick()
            assertEquals(it, 1, checks)
            assertEquals(it, 0, rule.onAllNodes(hasText("Comprobar")).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun theActionReportsOncePerPressAndIsATouchTarget() {
        val kinds = listOf(
            empty,
            StatusKind.LoadFailed,
            StatusKind.Older,
            StatusKind.ActionFailed,
            StatusKind.Unknown,
            StatusKind.Gone
        )
        var kind by mutableStateOf<StatusKind>(empty)
        var presses = 0
        rule.inEveryScheme(content = { Sample(kind, onAction = { presses++ }) }) { scheme ->
            kinds.forEach { next ->
                kind = next
                presses = 0
                rule.waitForIdle()
                val button = rule.onNode(isButton)
                button.performClick()
                assertEquals("$scheme $next", 1, presses)
                val height = button.getUnclippedBoundsInRoot().height
                assertTrue("$scheme $next is $height tall", height >= 46.dp)
            }
            kind = empty
        }
    }

    @Test
    fun aBusyActionKeepsItsLabelAndIgnoresASecondPress() {
        var presses = 0
        var busy by mutableStateOf(false)
        rule.inEveryScheme(
            reset = {
                presses = 0
                busy = false
            },
            content = { Sample(StatusKind.ActionFailed, onAction = { presses++ }, busy = busy) }
        ) {
            rule.onNodeWithText("Reintentar").performClick()
            busy = true
            rule.waitForIdle()
            rule.onNodeWithText("Reintentar").assertIsDisplayed().assertIsNotEnabled()
                .performClick()
            assertEquals(it, 1, presses)
        }
    }

    @Test
    fun noActionMeansNoButton() {
        rule.inEveryScheme(content = {
            StatusSlip(StatusKind.Gone, MESSAGE, title = "La categoría ya no está")
        }) {
            assertEquals(it, 0, rule.onAllNodes(isButton).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun onlyNewsThatArrivesIsAnnounced() {
        val announced = setOf<StatusKind>(
            StatusKind.LoadFailed,
            StatusKind.Older,
            StatusKind.ActionFailed,
            StatusKind.Unknown
        )
        val kinds = listOf(
            empty,
            StatusKind.LoadFailed,
            StatusKind.Older,
            StatusKind.ActionFailed,
            StatusKind.Unknown,
            StatusKind.Gone
        )
        var kind by mutableStateOf<StatusKind>(empty)
        rule.inEveryScheme(content = { Sample(kind) }) { scheme ->
            kinds.forEach { next ->
                kind = next
                rule.waitForIdle()
                val live = rule.onAllNodes(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion,
                        LiveRegionMode.Polite
                    ) and hasAnyDescendantOrSelfText(MESSAGE),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().size
                if (next in announced) {
                    assertEquals("$scheme $next", 1, live)
                } else {
                    assertEquals("$scheme $next", 0, live)
                }
            }
            kind = empty
        }
    }

    private fun hasAnyDescendantOrSelfText(text: String): SemanticsMatcher =
        hasText(text) or androidx.compose.ui.test.hasAnyDescendant(hasText(text))

    @Test
    fun theKindsRulesOnWordsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) {
            rule.setContent { SelloTheme { StatusSlip(empty, MESSAGE) } }
        }
    }

    @Test
    fun olderFiguresHaveNoTitle() {
        assertThrows(IllegalArgumentException::class.java) {
            rule.setContent {
                SelloTheme { StatusSlip(StatusKind.Older, MESSAGE, title = "Viejo") }
            }
        }
    }

    @Test
    fun anEmptySlipsActionNeedsItsLabel() {
        assertFalse(empty.look().defaultLabel != null)
        assertThrows(IllegalArgumentException::class.java) {
            rule.setContent {
                SelloTheme {
                    StatusSlip(empty, MESSAGE, title = "Nada", action = StatusAction {})
                }
            }
        }
    }

    private companion object {
        const val MESSAGE = "Tus registros siguen guardados."
    }
}
