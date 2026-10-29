package com.software.sello.feature.expense

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.date.dateLabels
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the keys and the button are. Typing changes the amount, never the place a
 * finger has to go: whatever the form says, a key stays where it was. On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
class ExpenseEntryGeometryTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<ExpenseEntryAction>()
    private val today = LocalDate.of(2026, 10, 9)
    private val food = EntryCategory("a", "Alimentación", "restaurant")

    private fun shown(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    /** A category is chosen and no amount typed: what a person sees on opening the form. */
    private val empty = ExpenseEntryState(
        loading = false,
        categories = listOf(food),
        categoryId = "a",
        date = today,
        today = today
    )
    private val one = empty.copy(amount = shown("1"), missing = null)

    /** Everything the form can say near the keypad, shortest to longest. */
    private val said = listOf(
        "an amount typed" to one,
        "no category" to one.copy(categoryId = null, missing = Missing.Category),
        "a note to correct" to one.copy(missing = Missing.Note),
        "not saved" to one.copy(error = EntryError.NotSaved),
        "category gone" to
            one.copy(
                error = EntryError.CategoryGone,
                categoryId = null,
                missing = Missing.Category
            ),
        "outcome unknown" to one.copy(error = EntryError.Unknown),
        "empty again" to empty
    )

    private var state by mutableStateOf(empty)

    /** One dp is one pixel on the JVM screen, so a window of any size can be laid out. */
    private fun show(window: DpSize, fontScale: Float = 1f) {
        rule.showSello(Look(fontScale = fontScale, window = window)) {
            ExpenseEntryScreen(state, dateLabels(), { actions += it })
        }
    }

    private val phone = DpSize(360.dp, 760.dp)
    private val sideways = DpSize(760.dp, 360.dp)

    /** Where a node is laid out, whether or not all of it is in view. */
    private fun SemanticsNode.place() = Rect(positionInRoot, size.toSize())

    private fun key(label: String): Rect = rule
        .onNode(hasText(label) and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG)), true)
        .fetchSemanticsNode().place()

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().place()

    private fun places() = listOf("1", "7", "0", "000").associateWith(::key) +
        ("button" to bounds(ENTRY_SUBMIT_TAG))

    private fun assertNothingMovesWhateverIsSaid(
        states: List<Pair<String, ExpenseEntryState>> = said
    ) {
        val before = places()
        for ((what, next) in states) {
            state = next
            rule.waitForIdle()
            assertEquals("with $what", before, places())
        }
    }

    @Test
    fun onAPhoneNoKeyAndNoButtonMovesWhateverTheFormSays() {
        show(phone)

        assertNothingMovesWhateverIsSaid()
    }

    @Test
    fun sidewaysNoKeyAndNoButtonMovesWhateverTheFormSays() {
        show(sideways)

        assertNothingMovesWhateverIsSaid()
    }

    @Test
    fun atTwiceTheFontSizeNoKeyAndNoButtonMovesWhateverTheFormSays() {
        show(phone, fontScale = 2f)

        // At this size "Comprobar si se guardó" takes two lines and its taller button
        // lifts the keys. They are off in that state, so no digit can be mistyped.
        assertNothingMovesWhateverIsSaid(said.filter { (_, state) -> state.editable })
    }

    @Test
    fun aFingerGoingBackToWhereSevenWasStillTypesSeven() {
        show(phone)
        val seven = rule.onNode(
            hasText("7") and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG))
        ).fetchSemanticsNode().boundsInRoot
        // The upper part of the key: the recorded slip put the key above under it.
        val upperPart = Offset(seven.center.x, seven.top + seven.height / 4)

        state = one
        rule.waitForIdle()
        rule.onRoot().performTouchInput { click(upperPart) }

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.Digit(7)), actions)
    }

    @Test
    fun theButtonIsEightDpAboveTheKeypadInEveryLayout() {
        show(phone)
        state = one
        rule.waitForIdle()

        // v2: the action sits on the pad, not under it.
        assertEquals(8f, bounds(ENTRY_KEYPAD_TAG).top - bounds(ENTRY_SUBMIT_TAG).bottom, 0.5f)
    }

    @Test
    fun sidewaysTheButtonIsEightDpAboveTheKeypadToo() {
        show(sideways)
        state = one
        rule.waitForIdle()

        assertEquals(8f, bounds(ENTRY_KEYPAD_TAG).top - bounds(ENTRY_SUBMIT_TAG).bottom, 0.5f)
    }

    @Test
    fun noKeyEverRecordsTheExpense() {
        show(phone)
        state = one
        rule.waitForIdle()
        val keypad = bounds(ENTRY_KEYPAD_TAG)

        // Every key, and the top row of pixels the keypad owns, nearest the button.
        for (label in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0")) {
            val place = key(label).center
            rule.onRoot().performTouchInput { click(place) }
        }
        for (x in listOf(keypad.left + 1, keypad.center.x, keypad.right - 1)) {
            rule.onRoot().performTouchInput { click(Offset(x, keypad.top + 1)) }
        }

        assertTrue("$actions", actions.isNotEmpty())
        assertTrue("$actions", ExpenseEntryAction.Submit !in actions)
    }

    /**
     * Half a phone's screen. The form is two panes here on purpose (SELLO-052): in one
     * column the keypad alone would fill the window.
     */
    @Test
    fun inAShortNarrowWindowTheAmountTheKeysAndTheButtonAreSideBySideAndInView() {
        show(DpSize(393.dp, 400.dp))
        state = one
        rule.waitForIdle()

        val amount = bounds(ENTRY_AMOUNT_TAG)
        val keypad = bounds(ENTRY_KEYPAD_TAG)
        val button = bounds(ENTRY_SUBMIT_TAG)
        // The slip at the left, the keys and the button at the right of it.
        assertTrue("$amount $keypad", amount.right <= keypad.left)
        assertTrue("$amount $button", amount.right <= button.left)
        // All three inside the window's height, the button whole.
        assertTrue("$amount", amount.top >= 0f && amount.bottom <= 400f)
        assertTrue("$button", button.top >= 0f && button.bottom <= 400f)
        assertTrue("$button above $keypad", button.bottom <= keypad.top)
        // Each key is at least the smallest size a finger can be asked to hit.
        val five = rule.onNodeWithText("5").fetchSemanticsNode().touchBoundsInRoot
        assertTrue("$five", five.width >= 48f && five.height >= 48f)
    }
}
