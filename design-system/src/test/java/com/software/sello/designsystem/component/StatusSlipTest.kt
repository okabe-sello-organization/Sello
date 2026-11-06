package com.software.sello.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
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

    private val empty = StatusKind.Empty(SelloIcon.ReceiptLong, "Aún no hay gastos")

    private val kinds = listOf(
        empty,
        StatusKind.LoadFailed(),
        StatusKind.Older,
        StatusKind.ActionFailed(),
        StatusKind.Unknown(),
        StatusKind.Gone()
    )

    /** One slip of [kind] with an action labelled [LABEL], tagged "slip". */
    @Composable
    private fun Sample(kind: StatusKind, onAction: () -> Unit = {}, busy: Boolean = false) {
        StatusSlip(
            kind = kind,
            message = MESSAGE,
            modifier = Modifier.testTag("slip"),
            action = StatusAction(label = LABEL, busy = busy, onClick = onAction)
        )
    }

    /** Runs [check] for every kind in [these], in each scheme, with [Sample] on screen. */
    private fun forEachKind(
        these: List<StatusKind> = kinds,
        onAction: () -> Unit = {},
        check: (scheme: String, kind: StatusKind) -> Unit
    ) {
        var kind by mutableStateOf(these.first())
        rule.inEveryScheme(content = { Sample(kind, onAction) }) { scheme ->
            these.forEach { next ->
                kind = next
                rule.waitForIdle()
                check(scheme, next)
            }
            kind = these.first()
        }
    }

    @Test
    fun eachKindDecidesItsIconInkLayoutAndButton() {
        val expected = listOf(
            StatusLook(
                StatusLayout.Centred,
                SelloIcon.ReceiptLong,
                StatusInk.Brand,
                StatusInk.InkSoft,
                ButtonKind.Outlined,
                announced = false,
                defaultTitle = null
            ),
            StatusLook(
                StatusLayout.Centred,
                SelloIcon.Error,
                StatusInk.Brand,
                StatusInk.InkSoft,
                ButtonKind.Outlined,
                announced = true,
                defaultTitle = R.string.sello_error_title
            ),
            StatusLook(
                StatusLayout.Inline,
                null,
                StatusInk.Warn,
                StatusInk.Warn,
                ButtonKind.Text,
                announced = true,
                defaultTitle = null
            ),
            StatusLook(
                StatusLayout.Inline,
                SelloIcon.Error,
                StatusInk.Loss,
                StatusInk.Loss,
                ButtonKind.Outlined,
                announced = true,
                defaultTitle = null
            ),
            StatusLook(
                StatusLayout.Inline,
                SelloIcon.Info,
                StatusInk.Warn,
                StatusInk.Warn,
                ButtonKind.Outlined,
                announced = true,
                defaultTitle = null
            ),
            StatusLook(
                StatusLayout.Centred,
                SelloIcon.Block,
                StatusInk.Brand,
                StatusInk.InkSoft,
                ButtonKind.Outlined,
                announced = true,
                defaultTitle = R.string.sello_status_gone_title
            )
        )
        assertEquals(expected, kinds.map { it.look() })
        assertEquals(SelloIcon.Insights, StatusKind.Empty(SelloIcon.Insights, "Nada").look().icon)
    }

    /** The theme's inks, read inside the composition, in [StatusInk] order. */
    private var inks: Map<StatusInk, Color> = emptyMap()

    @Composable
    private fun ReadInks() {
        val c = SelloTheme.colors
        inks = mapOf(
            StatusInk.Brand to c.brand,
            StatusInk.InkSoft to c.inkSoft,
            StatusInk.Warn to c.semantic.warn,
            StatusInk.Loss to c.semantic.loss
        )
    }

    @Test
    fun theMessageIconAndButtonAreDrawnAsTheKindSays() {
        var kind by mutableStateOf<StatusKind>(empty)
        rule.inEveryScheme(content = {
            ReadInks()
            Sample(kind)
        }) { scheme ->
            kinds.forEach { next ->
                kind = next
                rule.waitForIdle()
                val look = next.look()
                assertEquals("$scheme $next message", inks[look.messageInk], messageColor())
                if (look.icon != null) {
                    val icon = rule.onNode(hasTestTag(STATUS_ICON_TAG), useUnmergedTree = true)
                        .drawnBitmap()
                    assertEquals(
                        "$scheme $next icon",
                        inks.getValue(look.iconInk).toArgb(),
                        commonestInk(icon, paper = background(icon))
                    )
                }
                val button = rule.onNode(isButton).drawnBitmap()
                assertEquals(
                    "$scheme $next button outlined",
                    look.button == ButtonKind.Outlined,
                    hasEdgeIn(button, inks.getValue(StatusInk.Brand))
                )
            }
            kind = empty
        }
    }

    private fun messageColor(): Color {
        val node = rule.onNode(hasText(MESSAGE)).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single().layoutInput.style.color
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    /** The corner pixel: the paper the icon is drawn on. */
    private fun background(bitmap: Bitmap): Int = bitmap.getPixel(0, 0)

    /** The colour most pixels other than [paper] have: a filled icon's ink. */
    private fun commonestInk(bitmap: Bitmap, paper: Int): Int = pixels(bitmap)
        .filter { it != paper }
        .groupingBy { it }
        .eachCount()
        .maxBy { it.value }
        .key

    /**
     * Whether the button's left edge is drawn in [ink] down nearly all its middle half: a
     * border. A text button's first letter covers far fewer rows.
     */
    private fun hasEdgeIn(bitmap: Bitmap, ink: Color): Boolean {
        val want = ink.toArgb()
        val rows = bitmap.height / 4 until bitmap.height * 3 / 4
        val edge = rows.count { y -> (0 until 4).any { x -> near(bitmap.getPixel(x, y), want) } }
        return edge >= rows.count() * 9 / 10
    }

    private fun near(a: Int, b: Int): Boolean = listOf(16, 8, 0).all { shift ->
        abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 40
    }

    @Test
    fun centredKindsCentreTheirWordsUnderTheIconAndInlineKindsStartThem() {
        forEachKind { scheme, kind ->
            val slip = rule.onNodeWithTag("slip").getUnclippedBoundsInRoot()
            val words = rule.onNode(hasText(MESSAGE)).getUnclippedBoundsInRoot()
            if (kind.look().layout == StatusLayout.Centred) {
                val icon = rule.onNode(
                    hasAnyAncestor(hasTestTag("slip")) and hasTestTag(STATUS_ICON_TAG)
                ).getUnclippedBoundsInRoot()
                val middle = (slip.left + slip.right) / 2
                assertTrue(
                    "$scheme $kind icon centred",
                    ((icon.left + icon.right) / 2 - middle).value in -1f..1f
                )
                assertTrue(
                    "$scheme $kind words centred",
                    ((words.left + words.right) / 2 - middle).value in -1f..1f
                )
                assertTrue("$scheme $kind icon above", icon.bottom <= words.top)
            } else {
                // The words start near the slip's edge: after its padding, and the icon if any.
                assertTrue("$scheme $kind starts inline", words.left - slip.left < 80.dp)
                val icons = rule.onAllNodes(hasTestTag(STATUS_ICON_TAG), useUnmergedTree = true)
                    .fetchSemanticsNodes().size
                assertEquals("$scheme $kind icon", if (kind == StatusKind.Older) 0 else 1, icons)
            }
        }
    }

    @Test
    fun theActionsWordsStartWhereTheMessageDoes() {
        forEachKind(
            listOf(StatusKind.Older, StatusKind.ActionFailed(), StatusKind.Unknown())
        ) { scheme, kind ->
            val message = rule.onNode(hasText(MESSAGE)).getUnclippedBoundsInRoot()
            val button = rule.onNode(isButton).getUnclippedBoundsInRoot()
            // A text button's words sit at its edge; an outlined one's edge lines up.
            val gap = (button.left - message.left).value
            assertTrue("$scheme $kind button starts at the message ($gap dp)", gap in -1f..1f)
        }
    }

    @Test
    fun theIconIsDecorative() {
        rule.inEveryScheme(content = {
            Column {
                Sample(StatusKind.Gone())
                Sample(StatusKind.Unknown())
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
    fun aKindWithoutATitleTakesItsOwnOrNone() {
        // kind to the title shown, or null for none
        val expected = mapOf(
            StatusKind.LoadFailed() to "No se pudo cargar",
            StatusKind.Older to null,
            StatusKind.ActionFailed() to null,
            StatusKind.Unknown() to null,
            StatusKind.Gone() to "Ya no está"
        )
        forEachKind(expected.keys.toList()) { scheme, kind ->
            val title = expected.getValue(kind)
            if (title != null) rule.onNodeWithText(title).assertIsDisplayed()
            val headings = rule.onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
            ).fetchSemanticsNodes().size
            assertEquals("$scheme $kind headings", if (title == null) 0 else 1, headings)
        }
    }

    @Test
    fun aKindsTitleIsShownAsItsHeading() {
        forEachKind(
            listOf(
                empty,
                StatusKind.LoadFailed("No pudimos leer tu mes"),
                StatusKind.ActionFailed("No se guardó el respaldo"),
                StatusKind.Unknown("No sabemos si se guardó"),
                StatusKind.Gone("La categoría ya no está")
            )
        ) { scheme, kind ->
            val title = when (kind) {
                is StatusKind.Empty -> "Aún no hay gastos"
                is StatusKind.LoadFailed -> "No pudimos leer tu mes"
                is StatusKind.ActionFailed -> "No se guardó el respaldo"
                is StatusKind.Unknown -> "No sabemos si se guardó"
                else -> "La categoría ya no está"
            }
            rule.onNode(
                hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
            ).assertIsDisplayed()
            assertEquals(
                "$scheme $kind",
                0,
                rule.onAllNodes(hasText("No se pudo cargar")).fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun theDefaultLabelsAreTheKindsUsualWords() {
        val labels = mutableListOf<String>()
        rule.setContent {
            SelloTheme {
                labels += listOf(
                    StatusSlipDefaults.retryLabel,
                    StatusSlipDefaults.checkLabel,
                    StatusSlipDefaults.backLabel
                )
            }
        }
        rule.waitForIdle()
        assertEquals(listOf("Reintentar", "Comprobar", "Volver"), labels.take(3))
    }

    @Test
    fun theActionReportsOncePerPressAndIsATouchTarget() {
        var presses = 0
        forEachKind(onAction = { presses++ }) { scheme, kind ->
            presses = 0
            val button = rule.onNode(isButton).assert(hasText(LABEL))
            button.performClick()
            assertEquals("$scheme $kind", 1, presses)
            val height = button.getUnclippedBoundsInRoot().height
            assertTrue("$scheme $kind is $height tall", height >= 48.dp)
        }
    }

    @Test
    fun aBusyActionSaysSoKeepsItsLabelAndIgnoresASecondPress() {
        var presses = 0
        var busy by mutableStateOf(false)
        rule.inEveryScheme(
            reset = {
                presses = 0
                busy = false
            },
            content = { Sample(StatusKind.ActionFailed(), onAction = { presses++ }, busy = busy) }
        ) {
            val idle = rule.onNode(isButton).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.StateDescription)
            assertEquals(it, null, idle)
            rule.onNodeWithText(LABEL).performClick()
            busy = true
            rule.waitForIdle()
            rule.onNode(isButton)
                .assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "En curso")
                )
            rule.onNodeWithText(LABEL).assertIsDisplayed().assertIsNotEnabled().performClick()
            assertEquals(it, 1, presses)
        }
    }

    @Test
    fun noActionMeansNoButton() {
        rule.inEveryScheme(content = { StatusSlip(StatusKind.Gone(), MESSAGE) }) {
            assertEquals(it, 0, rule.onAllNodes(isButton).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun everyKindButEmptyIsReadAloudWhenItAppears() {
        forEachKind { scheme, kind ->
            val live = rule.onAllNodes(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                ) and
                    (hasText(MESSAGE) or hasAnyDescendant(hasText(MESSAGE))),
                useUnmergedTree = true
            ).fetchSemanticsNodes().size
            assertEquals("$scheme $kind", if (kind is StatusKind.Empty) 0 else 1, live)
        }
    }

    @Test
    fun aStyledMessageKeepsItsWeightButNeverItsOwnColour() {
        var kind by mutableStateOf<StatusKind>(empty)
        val styled = buildAnnotatedString {
            append("Faltan ")
            withStyle(SpanStyle(color = Color.Magenta, fontWeight = FontWeight.Bold)) {
                append("12.000")
            }
            withStyle(SpanStyle(brush = SolidColor(Color.Cyan), fontStyle = FontStyle.Italic)) {
                append(" hoy")
            }
        }
        rule.inEveryScheme(content = {
            ReadInks()
            StatusSlip(kind, styled)
        }) { scheme ->
            kinds.forEach { next ->
                kind = next
                rule.waitForIdle()
                val layout = layoutOf(styled.text)
                val spans = layout.layoutInput.text.spanStyles.map { it.item }
                val ink = inks[next.look().messageInk]
                assertEquals("$scheme $next ink", ink, layout.layoutInput.style.color)
                assertTrue(
                    "$scheme $next spans $spans",
                    spans.all { it.color == Color.Unspecified && it.brush == null }
                )
                assertTrue("$scheme $next weight", spans.any { it.fontWeight == FontWeight.Bold })
                assertTrue("$scheme $next style", spans.any { it.fontStyle == FontStyle.Italic })
            }
            kind = empty
        }
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = rule.onNode(hasText(text)).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    private companion object {
        const val MESSAGE = "Tus registros siguen guardados."
        const val LABEL = "Hacerlo"
    }
}
