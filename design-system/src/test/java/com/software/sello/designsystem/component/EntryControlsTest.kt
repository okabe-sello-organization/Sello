package com.software.sello.designsystem.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryControlsTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** The area that accepts a touch, which may be larger than what is drawn. */
    private fun tall(tag: String, label: String) {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val height = with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() }
        assertTrue("$label: $tag accepts touches over $height", height >= minTarget)
    }

    /** 48dp, less the fraction of a pixel that rounding to whole pixels can cost. */
    private val minTarget = 47.9.dp

    // ---- Keypad ----

    @Test
    fun keypadReportsEachPressOnceAndNothingElse() {
        val presses = mutableListOf<String>()
        rule.inEveryScheme(
            reset = { presses.clear() },
            content = {
                AmountKeypad(
                    onDigit = { presses += it.toString() },
                    onTripleZero = { presses += "000" },
                    onBackspace = { presses += "back" },
                    onClearAll = { presses += "clear" }
                )
            }
        ) {
            for (key in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0")) {
                rule.onNodeWithText(key).assert(isButton).performClick()
            }
            rule.onNodeWithContentDescription("Borrar").assert(isButton).performClick()
            rule.onNodeWithContentDescription("Borrar")
                .performSemanticsAction(SemanticsActions.OnLongClick)
            assertEquals(
                it,
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", "back", "clear"),
                presses
            )
        }
    }

    @Test
    fun keysKeepAFullTouchTarget() {
        rule.inEveryScheme(content = { AmountKeypad({}, {}, {}, {}) }) {
            for (key in listOf("1", "5", "9", "000", "0")) {
                val height = rule.onNodeWithText(key).getUnclippedBoundsInRoot().height
                assertTrue("$it key $key is $height", height >= minTarget)
            }
            val back = rule.onNodeWithContentDescription("Borrar").getUnclippedBoundsInRoot()
            assertTrue("$it backspace is ${back.height}", back.height >= minTarget)
        }
    }

    @Test
    fun aHardwareKeyboardTypesDeletesAndSubmitsAndTabFollowsReadingOrder() {
        val presses = mutableListOf<String>()
        // A keyboard user is not in touch mode; only then can a button take focus. The
        // mode is supplied here so the test does not depend on what ran before it.
        val keyboardMode = object : InputModeManager {
            override val inputMode = InputMode.Keyboard

            override fun requestInputMode(inputMode: InputMode) = inputMode == InputMode.Keyboard
        }
        rule.setContent {
            CompositionLocalProvider(LocalInputModeManager provides keyboardMode) {
                SelloTheme {
                    AmountKeypad(
                        onDigit = { presses += it.toString() },
                        onTripleZero = { presses += "000" },
                        onBackspace = { presses += "back" },
                        onClearAll = { presses += "clear" },
                        onSubmit = { presses += "submit" }
                    )
                }
            }
        }

        // Tab walks the keys in reading order, starting with the first.
        for (key in listOf("1", "2", "3", "4")) {
            rule.onRoot().performKeyInput { pressKey(Key.Tab) }
            rule.onNodeWithText(key).assertIsFocused()
        }

        rule.onNodeWithText("4").performKeyInput {
            pressKey(Key.Five)
            pressKey(Key.NumPad7)
            pressKey(Key.Backspace)
            pressKey(Key.Enter)
        }
        assertEquals(listOf("5", "7", "back", "submit"), presses)
    }

    @Test
    fun aDisabledKeypadReportsNothing() {
        val presses = mutableListOf<String>()
        rule.setContent {
            SelloTheme {
                AmountKeypad(
                    onDigit = { presses += "digit" },
                    onTripleZero = { presses += "000" },
                    onBackspace = { presses += "back" },
                    onClearAll = { presses += "clear" },
                    enabled = false
                )
            }
        }
        rule.onNodeWithText("7").assertIsNotEnabled().performClick()
        rule.onNodeWithText("000").assertIsNotEnabled().performClick()
        rule.onNodeWithContentDescription("Borrar").assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), presses)
    }

    // ---- Amount display ----

    @Test
    fun anEmptyDraftIsAnnouncedAsNoAmountNotAsZero() {
        rule.inEveryScheme(content = {
            EntryAmountSlip("Gasto en pesos", null, Modifier.testTag("field"))
        }) {
            // The faint placeholder figure is drawn, but it is only ever spoken as no amount.
            rule.onNodeWithText("0").assertContentDescriptionEquals("Sin monto")
            rule.onNodeWithContentDescription("0", substring = true).assertDoesNotExist()
            rule.onNodeWithContentDescription("pesos", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun aRefusedPasteIsShownExactlyAsReceivedWithItsReason() {
        rule.inEveryScheme(
            content = {
                EntryAmountSlip(
                    "Gasto en pesos",
                    amount = null,
                    rejectedText = "1e3",
                    error = "Eso no es un monto"
                )
            }
        ) {
            rule.onNodeWithText("1e3").assertIsDisplayed()
            rule.onNodeWithText("Eso no es un monto").assertIsDisplayed()
            rule.onNodeWithText("13").assertDoesNotExist()
            rule.onNodeWithContentDescription("Sin monto").assertDoesNotExist()
        }
    }

    @Test
    fun pasteHandsOverTheClipboardTextUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val pasted = mutableListOf<String>()
        rule.setContent {
            SelloTheme { EntryAmountSlip("Gasto en pesos", null, onPaste = { pasted += it }) }
        }
        for (text in listOf(" 1e3 ", "-100", "$1.234,50")) {
            rule.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("test", text)) }
            rule.onNodeWithContentDescription("Pegar").assert(isButton).performClick()
            rule.waitForIdle()
        }
        assertEquals(listOf(" 1e3 ", "-100", "$1.234,50"), pasted)
    }

    @Test
    fun theTypedAmountAndAHintAreBothShown() {
        val amount = MoneyTextValue(MoneySign.None, "", "999.999.999.999", "999.999.999.999 pesos")
        rule.inEveryScheme(
            content = {
                EntryAmountSlip(
                    "Gasto en pesos",
                    amount,
                    error = "Máximo 12 dígitos",
                    limitPulse = 1
                )
            }
        ) {
            rule.onNodeWithContentDescription("999.999.999.999 pesos").assertIsDisplayed()
            rule.onNodeWithText("Máximo 12 dígitos").assertIsDisplayed()
        }
    }

    // ---- Chips and pills ----

    @Test
    fun chipsSelectOnceReportTheirStateAndKeepATallHitBox() {
        var selected by mutableIntStateOf(-1)
        var clicks = 0
        rule.inEveryScheme(
            reset = {
                selected = -1
                clicks = 0
            },
            content = {
                ChipFlow {
                    SelloChip("Café", selected == 0, {
                        selected = 0
                        clicks++
                    }, Modifier.testTag("cafe"), SelloIcon.LocalCafe)
                    SelloChip("Transporte", selected == 1, {
                        selected = 1
                        clicks++
                    }, Modifier.testTag("bus"))
                    SelloChip("Archivada", false, {
                        clicks++
                    }, Modifier.testTag("off"), enabled = false)
                }
            }
        ) {
            rule.onNodeWithTag("cafe").assertIsNotSelected().performClick()
            rule.onNodeWithTag("cafe").assertIsSelected()
            rule.onNodeWithTag("bus").assertIsNotSelected()
            rule.onNodeWithTag("off").assertIsNotEnabled().performClick()
            assertEquals(it, 1, clicks)
            tall("cafe", it)
            tall("bus", it)
        }
    }

    @Test
    fun aFieldPillIsAButtonWithAFullTouchTarget() {
        var clicks = 0
        rule.inEveryScheme(
            reset = { clicks = 0 },
            content = {
                FieldPill("Hoy, jue 22", SelloIcon.Event, { clicks++ }, Modifier.testTag("pill"))
            }
        ) {
            rule.onNodeWithTag("pill").assert(isButton).performClick()
            assertEquals(it, 1, clicks)
            tall("pill", it)
        }
    }

    // ---- Text fields ----

    @Test
    fun aFieldPassesOnExactlyWhatWasTypedAndMarksItsError() {
        var value by mutableStateOf("")
        rule.setContent {
            SelloTheme {
                FormField(
                    label = "Nombre",
                    value = value,
                    onValueChange = { value = it },
                    message = if (value.length > 5) FieldMessage("Máximo 5 caracteres") else null,
                    counter = "${value.length}/5"
                )
            }
        }
        val field = rule.onNodeWithContentDescription("Nombre")
        field.performTextInput(" 1e3 ")
        assertEquals("the field must not trim or filter", " 1e3 ", value)
        rule.onNodeWithText("5/5").assertIsDisplayed()
        field.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))

        field.performTextInput("-")
        assertEquals("over the limit is kept, not cut", 6, value.length)
        rule.onNodeWithText("6/5").assertIsDisplayed()
        rule.onNodeWithText("Máximo 5 caracteres").assertIsDisplayed()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Máximo 5 caracteres"))
    }

    @Test
    fun aFocusedFieldAtTheBottomStaysVisibleWhileTyping() {
        var value by mutableStateOf("")
        rule.setContent {
            SelloTheme {
                Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(900.dp))
                    FormField("Nota", value, { value = it })
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
        rule.onNodeWithContentDescription("Nota").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Nota").assertIsFocused()
        rule.onNodeWithContentDescription("Nota").performTextInput("Almuerzo")
        rule.onNodeWithContentDescription("Nota").assertIsDisplayed()
        assertEquals("Almuerzo", value)
    }

    // ---- Band ----

    @Test
    fun theBandRunsEdgeToEdgeDownToTheMiddleOfTheAmount() {
        val band = Color(0xFFFF0000)
        val desk = Color(0xFF00FF00)
        rule.setContent {
            Box(Modifier.fillMaxSize().background(desk).testTag("window")) {
                EntryLayout(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    band = band,
                    header = { Spacer(Modifier.height(40.dp)) },
                    amount = { Spacer(Modifier.fillMaxWidth().height(100.dp).testTag("amount")) },
                    choices = {},
                    action = {},
                    keypad = {}
                )
            }
        }
        val window = rule.onNodeWithTag("window").drawnBitmap()
        val amount = rule.onNodeWithTag("amount").fetchSemanticsNode().boundsInRoot
        val middle = (amount.top + amount.bottom) / 2
        val edges = listOf(1, window.width / 2, window.width - 2)

        for (x in edges) {
            assertEquals("x=$x over the header", band.toArgb(), window.getPixel(x, 2))
            assertEquals(
                "x=$x just above the amount's middle",
                band.toArgb(),
                window.getPixel(x, middle.toInt() - 3)
            )
            assertEquals(
                "x=$x just below the amount's middle",
                desk.toArgb(),
                window.getPixel(x, middle.toInt() + 3)
            )
        }
    }

    @Test
    fun theSegmentedSwitchReportsTheChosenSegment() {
        var selected by mutableIntStateOf(0)
        val picks = mutableListOf<Int>()
        rule.inEveryScheme(
            reset = {
                selected = 0
                picks.clear()
            },
            content = {
                SegmentedSwitch(listOf("Sistema", "Claro", "Oscuro"), selected, {
                    selected = it
                    picks +=
                        it
                })
            }
        ) {
            rule.onNodeWithText("Sistema").assertIsSelected()
            rule.onNodeWithText("Oscuro").assertIsNotSelected().performClick()
            rule.onNodeWithText("Oscuro").assertIsSelected()
            rule.onNodeWithText("Sistema").assertIsNotSelected()
            assertEquals(it, listOf(2), picks)
        }
    }
}
