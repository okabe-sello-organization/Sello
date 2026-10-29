package com.software.sello.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.composition.viewModels
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.devtools.SandboxGraph
import com.software.sello.devtools.SandboxSession
import com.software.sello.feature.backup.BACKUP_CARD_CONTENTS_TAG
import com.software.sello.feature.backup.BACKUP_CARD_RESET_TAG
import com.software.sello.feature.backup.BACKUP_CREATE_TAG
import com.software.sello.feature.category.EDITOR_SUBMIT_TAG
import com.software.sello.feature.expense.ENTRY_DELETE_TAG
import com.software.sello.feature.expense.ENTRY_SUBMIT_TAG
import com.software.sello.feature.recibo.CATEGORY_DETAIL_EDIT_TAG
import com.software.sello.feature.recibo.CATEGORY_DETAIL_HEADER_TAG
import com.software.sello.feature.recibo.RECIBO_ADD_CATEGORY_TAG
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.feature.recibo.categoryExpenseTag
import com.software.sello.feature.recibo.reciboCategoryTag
import com.software.sello.feature.reset.RESET_CONTINUE_TAG
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Every daily-use screen of the real app, on its own database and view models, in the
 * windows and themes of the [UI acceptance matrix](docs/testing/ui-acceptance-matrix.md).
 * One dp is drawn as one pixel, so a window can be larger than the test device's
 * screen: what is checked is what exists and where it is laid out, not what is
 * visible. Each screen is checked for:
 * - controls at least 48 by 48 dp to touch;
 * - text that is not cut off, except a category tile's name, which its tile says in full;
 * - amounts spoken in full, in pesos;
 * - its main action reachable inside the window.
 * Every problem on every screen is collected, then reported together. Pull requests
 * sweep two windows ([UiAcceptanceSweepTest]); the nightly run sweeps all five
 * (with [UiAcceptanceSweepNightlyTest]).
 */
abstract class UiAcceptanceSweep(private val window: Window) {
    /** A window of [width] × [height] dp, drawn in one theme. */
    data class Window(
        val name: String,
        val width: Int,
        val height: Int,
        val ink: SelloInk,
        val dark: Boolean
    ) {
        override fun toString() = name
    }

    companion object {
        private val money = Regex("""(?<![\d.])\d{1,3}(\.\d{3})+(?![\d.])""")
        private const val MIN_TARGET = 48f
        private const val WINDOW_TAG = "sweep:window"

        /** A 30th: the month has run into its fifth week, so the weekly chart has five columns. */
        private val FIVE_WEEK_DAY = LocalDate.of(2026, 10, 30)
    }

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val problems = mutableListOf<String>()

    private val sandbox = SandboxGraph(SandboxSession(active = true, date = FIVE_WEEK_DAY))

    @Before
    fun history() {
        // The app's own graph on the sandbox's data with its financial clock on a fixed day,
        // one of the 30th of a month: the weekly chart always has its five columns, the
        // widest it can be, whatever the real date. Audit and elapsed time stay real.
        sandbox.erase()
        sandbox.open()
        TestCommands.use(sandbox.koin)
        assertEquals(FIVE_WEEK_DAY, TestCommands.today)
        TestCommands.showCurrentMonth()
        TestCommands.createCategory("Mercado", 300_000)
        TestCommands.createCategory("Transporte público", 50_000)
        TestCommands.createExpense("Mercado", TestCommands.today, 48_700, "Plaza de mercado")
        // Over its limit, so its status must be said in words.
        TestCommands.createExpense("Transporte público", TestCommands.today, 61_250, "Tarjeta")
        TestCommands.createIncome(TestCommands.today, 4_200_000)
    }

    @After
    fun leaveNothingBehind() {
        TestCommands.useInstalledApp()
        sandbox.erase()
    }

    @Test
    fun everyDailyUseScreenFitsItsWindowAndIsFullyOperable() {
        val viewModels = viewModels(sandbox.koin)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SelloTheme(ink = window.ink, darkTheme = window.dark, reducedMotion = true) {
                    Box(
                        Modifier.requiredSize(window.width.dp, window.height.dp).testTag(WINDOW_TAG)
                    ) {
                        SelloAppRoot(viewModels)
                    }
                }
            }
        }
        val transport = TestCommands.categoryId("Transporte público")

        awaitOrDescribe(hasTestTag(RECIBO_SUMMARY_TAG))
        check("Recibo")

        open(hasTestTag(reciboCategoryTag(transport)))
        awaitOrDescribe(hasTestTag(CATEGORY_DETAIL_HEADER_TAG))
        check("Category detail")
        // The pinned day is what makes this the widest weekly chart, not luck of the calendar.
        rule.onAllNodes(
            hasAnyAncestor(hasTestTag("detail:weeks")) and
                hasContentDescription(": gastado", substring = true)
        ).assertCountEquals(5)

        val expense = TestData.rows("SELECT id FROM expense WHERE note = 'Tarjeta'").single()
        open(hasTestTag(categoryExpenseTag(expense)))
        awaitOrDescribe(hasTestTag(ENTRY_DELETE_TAG))
        check("Expense edit", ENTRY_SUBMIT_TAG, ENTRY_DELETE_TAG)
        back()

        open(hasTestTag(CATEGORY_DETAIL_EDIT_TAG))
        awaitOrDescribe(hasTestTag(EDITOR_SUBMIT_TAG))
        check("Category edit", EDITOR_SUBMIT_TAG)
        // The form opens with the keyboard up for the name; Back first puts it away.
        Espresso.closeSoftKeyboard()
        back()

        open(hasContentDescription("Anotar un gasto"))
        awaitOrDescribe(hasTestTag(ENTRY_SUBMIT_TAG))
        check("Expense entry", ENTRY_SUBMIT_TAG)
        back()

        // On a phone the detail took the screen; beside Recibo there is nothing to leave.
        rule.onAllNodes(hasTestTag(DETAIL_BACK_TAG)).fetchSemanticsNodes().firstOrNull()?.let {
            open(hasTestTag(DETAIL_BACK_TAG))
        }
        open(hasTestTag(RECIBO_ADD_CATEGORY_TAG))
        awaitOrDescribe(hasTestTag(EDITOR_SUBMIT_TAG))
        check("New category", EDITOR_SUBMIT_TAG)
        Espresso.closeSoftKeyboard()
        back()

        open(hasText("Resumen") and hasClickAction())
        awaitOrDescribe(hasTestTag("resumen.flow"))
        check("Resumen")

        open(hasTestTag("resumen.category.$transport"))
        awaitOrDescribe(hasTestTag(CATEGORY_DETAIL_HEADER_TAG))
        check("Resumen category")
        back()

        open(hasTestTag("resumen.openIncome"))
        awaitOrDescribe(hasTestTag("income.hero"))
        check("Income history")

        open(hasContentDescription("Anotar un ingreso") and hasClickAction())
        awaitOrDescribe(hasTestTag("income.submit"))
        check("Income entry", "income.submit")
        back()
        back()

        open(hasText("Recibo") and hasClickAction())
        open(hasTestTag(SETTINGS_ACTION_TAG))
        awaitOrDescribe(hasTestTag(BACKUP_CARD_RESET_TAG))
        check("Configuración", BACKUP_CARD_CONTENTS_TAG, BACKUP_CARD_RESET_TAG)

        open(hasTestTag(BACKUP_CARD_CONTENTS_TAG))
        awaitOrDescribe(hasTestTag(BACKUP_CREATE_TAG))
        check("Respaldo", BACKUP_CREATE_TAG)
        back()

        open(hasTestTag(BACKUP_CARD_RESET_TAG))
        awaitOrDescribe(hasTestTag(RESET_CONTINUE_TAG))
        check("Borrar todos los datos", RESET_CONTINUE_TAG)
        back()

        assertTrue(
            "${window.name}: ${problems.size} problems\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    private fun open(matcher: SemanticsMatcher) {
        awaitOrDescribe(matcher)
        rule.onAllNodes(matcher)[0].performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
    }

    /** Waits for [matcher]; failing that, says what the screen held and what was found. */
    private fun awaitOrDescribe(matcher: SemanticsMatcher) {
        try {
            rule.waitUntil(15_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError(
                "${window.name}: no ${matcher.description}\n" +
                    problems.joinToString("\n") + "\n" + rule.onRoot().printToString(),
                timeout
            )
        }
    }

    private fun back() {
        Espresso.pressBack()
        rule.waitForIdle()
    }

    /** Records every problem on the screen now shown as [screen]; [actions] must be reachable. */
    private fun check(screen: String, vararg actions: String) {
        rule.waitForIdle()
        val window = rule.onNodeWithTag(WINDOW_TAG).fetchSemanticsNode().boundsInRoot
        val merged = rule.onAllNodes(SemanticsMatcher("any") { true }).fetchSemanticsNodes()
        val unmerged = rule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes()

        merged.filter { SemanticsActions.OnClick in it.config && it.isWhole() }.forEach { node ->
            // Compose widens a small control's touch area to 48dp where there is room;
            // the touch bounds include that.
            val touch = node.touchBoundsInRoot
            if (touch.width < MIN_TARGET - 0.5f || touch.height < MIN_TARGET - 0.5f) {
                problems += "$screen: target ${touch.width.toInt()}x${touch.height.toInt()} " +
                    "for ${node.label()}"
            }
        }

        unmerged.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()?.takeIf { it.isCutOff() }?.let { layout ->
                // The v2 design shortens a category tile's name to one line; its tile
                // must still say the whole name to a screen reader.
                val tile = node.categoryTile()
                if (tile == null) {
                    problems += "$screen: text cut off: ${node.label()}"
                } else if (!tile.spoken().contains(layout.layoutInput.text.text)) {
                    problems += "$screen: cut-off name not spoken in full: ${node.label()}"
                }
            }
        }

        // A week label broken in the middle of its word is not "cut off" by line count.
        unmerged.filter { node ->
            generateSequence(node) { it.parent }.any {
                it.config.getOrNull(SemanticsProperties.TestTag) == "detail:weeks"
            }
        }.forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()?.takeIf { it.breaksAWord() }?.let {
                problems += "$screen: word broken across lines: ${node.label()}"
            }
        }

        merged.forEach { node ->
            val shown = node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }.orEmpty()
            val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString(" ").orEmpty()
            money.findAll(shown).forEach { amount ->
                if (!spoken.contains("${amount.value} peso")) {
                    problems +=
                        "$screen: ${amount.value} not spoken in pesos: \"$spoken\" / \"$shown\""
                }
            }
        }

        actions.forEach { tag ->
            val action = rule.onAllNodes(hasTestTag(tag))[0]
            runCatching { action.performScrollTo() }
            val bounds = action.fetchSemanticsNode().boundsInRoot
            if (bounds.top < window.top || bounds.bottom > window.bottom + 0.5f ||
                bounds.left < window.left || bounds.right > window.right + 0.5f
            ) {
                problems += "$screen: $tag outside the window at $bounds"
            }
        }
    }

    /**
     * Not clipped by a list it is scrolled out of. A clipped control is measured where it
     * is whole: in another window of the sweep, or by its own screen's tests.
     */
    private fun SemanticsNode.isWhole(): Boolean {
        val bounds = boundsInRoot
        return bounds.width >= layoutInfo.width - 0.5f && bounds.height >= layoutInfo.height - 0.5f
    }

    /**
     * Stopped at its line limit with text left over, or shortened with an ellipsis.
     * Overflow in width is not used: the layout this action reports can be wider than
     * the text actually drawn.
     */
    private fun TextLayoutResult.isCutOff() = multiParagraph.didExceedMaxLines ||
        (0 until lineCount).any { isLineEllipsized(it) }

    private fun TextLayoutResult.breaksAWord(): Boolean {
        val text = layoutInput.text.text
        return (0 until lineCount - 1).any {
            val end = getLineEnd(it)
            text[end - 1] != ' ' && text[end] != ' '
        }
    }

    private fun SemanticsNode.categoryTile(): SemanticsNode? = generateSequence(this) { it.parent }
        .firstOrNull {
            it.config.getOrNull(SemanticsProperties.TestTag)
                ?.startsWith(reciboCategoryTag("")) == true
        }

    private fun SemanticsNode.spoken(): String =
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()

    private fun SemanticsNode.label(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val description = config.getOrNull(
            SemanticsProperties.ContentDescription
        )?.joinToString(" ")
        val tag = config.getOrNull(SemanticsProperties.TestTag)
        return listOfNotNull(tag, description, text).joinToString(" / ").ifEmpty { "node $id" }
    }
}
