package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentContractsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun customMoneyTypographyKeepsTheReferencesTabularDigitsAndSize() {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.inEveryScheme(content = {
            MoneyText(
                MoneyTextValue(MoneySign.None, "", "612.400", "612.400 pesos"),
                textStyle = MaterialTheme.typography.labelMedium,
                onTextLayout = { layouts += it }
            )
        }) { scheme ->
            rule.onNodeWithContentDescription("612.400 pesos").assertIsDisplayed()
            assertEquals(scheme, "tnum", layouts.last().layoutInput.style.fontFeatureSettings)
            assertEquals(scheme, 12.5.sp, layouts.last().layoutInput.style.fontSize)
        }
    }

    @Test
    fun compactPadHasFour48dpRowsAndClearAllIsAccessible() {
        var cleared = 0
        rule.inEveryScheme(content = {
            AmountKeypad({}, {}, {}, { cleared++ }, Modifier.testTag("pad"), compact = true)
        }) {
            assertEquals(192.dp, rule.onNodeWithTag("pad").getUnclippedBoundsInRoot().height)
            val key = rule.onNodeWithText("1").fetchSemanticsNode()
            val target = with(key.layoutInfo.density) { key.touchBoundsInRoot.height.toDp() }
            assertTrue(target >= 47.9.dp)
            rule.onNodeWithContentDescription(
                "Borrar"
            ).performSemanticsAction(SemanticsActions.OnLongClick) {
                it()
            }
        }
        assertEquals(4, cleared)
    }

    @Test
    fun twelveDigitSignedAmountFitsOnOneLineAt320AndDoubleFont() {
        val results = mutableListOf<TextLayoutResult>()
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            FittedMoneyText(
                MoneyTextValue(
                    MoneySign.Plus,
                    "",
                    "999.999.999.999",
                    "más 999.999.999.999 pesos"
                ),
                entry = true,
                onTextLayout = {
                    results.add(it)
                }
            )
        }) { scheme ->
            rule.onNodeWithContentDescription("más 999.999.999.999 pesos").assertIsDisplayed()
            assertEquals(scheme, 1, results.last().lineCount)
            assertTrue(scheme, !results.last().hasVisualOverflow)
        }
    }

    @Test
    fun anAnswerSlipWithADescriptionIsReadAsThatOneSentenceAndItsDetailOnItsOwn() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            AnswerSlip(
                "Restante del presupuesto",
                MoneyTextValue(MoneySign.Minus, "", "999.999.999.999", "menos 999.999.999.999"),
                "Excedido",
                StampInk.Over,
                description = "Restante del presupuesto: menos 999.999.999.999. Excedido."
            ) { Text("detalle", Modifier.testTag("detail")) }
        }) {
            rule.onNodeWithContentDescription(
                "Restante del presupuesto: menos 999.999.999.999. Excedido."
            ).assertIsDisplayed()
            // Only the sentence: the label and stamp are not read a second time.
            rule.onNodeWithText("Restante del presupuesto").assertDoesNotExist()
            rule.onNodeWithText("Excedido").assertDoesNotExist()
            rule.onNodeWithTag("detail").assertTextContains("detalle")
        }
    }

    @Test
    fun anEntryAmountSlipShowsRefusedPastedTextAsItWasAndOffersPasteOnlyWhenAsked() {
        var paste by mutableStateOf(true)
        rule.inEveryScheme(content = {
            EntryAmountSlip(
                "Gasto en pesos",
                amount = null,
                error = "Eso no es un monto.",
                rejectedText = "1e3",
                onPaste = if (paste) {
                    {}
                } else {
                    null
                }
            )
        }) {
            rule.onNodeWithText("1e3").assertIsDisplayed()
            rule.onNodeWithText("Eso no es un monto.").assertIsDisplayed()
            rule.onNodeWithText("Gasto en pesos").assertDoesNotExist()
            rule.onNodeWithContentDescription("Pegar").assertIsDisplayed()
        }
        paste = false
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Pegar").assertDoesNotExist()
    }

    @Test
    fun anAnswerSlipCanHaveNoStampAndThenShowsOnlyItsLabelAndAmount() {
        rule.inEveryScheme(content = {
            AnswerSlip(
                "Entró en octubre",
                MoneyTextValue(MoneySign.None, "", "150.000", "150.000 pesos"),
                stamp = null,
                ink = StampInk.Neutral
            )
        }) {
            rule.onNodeWithText("Entró en octubre").assertIsDisplayed()
            rule.onNodeWithContentDescription("150.000 pesos").assertIsDisplayed()
            rule.onNodeWithText("Al día").assertDoesNotExist()
        }
    }

    @Test
    fun allFourDestinationsExposeSelectionAndLabelsAtDoubleFont() {
        var selected by mutableStateOf("recibo")
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            SelloNavigationBar(
                SelloNavigation(
                    listOf(
                        SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                        SelloDestination("resumen", "Resumen", SelloIcon.Insights),
                        SelloDestination("metas", "Metas", SelloIcon.Flag),
                        SelloDestination("inversiones", "Inversiones", SelloIcon.AccountBalance)
                    ),
                    selected,
                    {
                        selected =
                            it
                    }
                )
            )
        }) {
            rule.onNodeWithText("Inversiones").assertIsDisplayed().performClick().assertIsSelected()
        }
    }

    @Test
    fun overlengthNoteIsPreservedAndDoneCannotSubmit() {
        var submitted = 0
        val note = "Una nota de prueba que supera el límite y conserva todas las palabras escritas."
        rule.setContent {
            SelloTheme {
                NoteComposer(
                    AnnotatedString("Gasto en Comida"), note, {}, "${note.length}/60",
                    "Opcional", "Máximo 60 caracteres", "Listo", "Cancelar", { submitted++ }, {}
                )
            }
        }
        rule.onNode(hasSetTextAction()).assertTextContains(note)
        rule.onNodeWithContentDescription("Listo").assertIsNotEnabled().performClick()
        assertEquals(0, submitted)
    }

    @Test
    fun aNavigationBarAtTheSideCoversNeitherCancelNorDone() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                SelloTheme {
                    NoteComposer(
                        AnnotatedString("Gasto en Comida"), "Taxi", {}, "4/60",
                        "Opcional", null, "Listo", "Cancelar", {}, {},
                        windowInsets = WindowInsets(left = 40.dp, right = 48.dp)
                    )
                }
            }
        }
        val sheet = rule.onAllNodes(isRoot()).fetchSemanticsNodes().last().boundsInRoot

        val done = rule.onNodeWithContentDescription("Listo").fetchSemanticsNode().boundsInRoot
        val cancel = rule.onNodeWithText("Cancelar").fetchSemanticsNode().boundsInRoot
        for (button in listOf(done, cancel)) {
            assertTrue("$button in $sheet", button.right <= sheet.right - 48f)
        }
        val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("field $field in $sheet", field.left >= sheet.left + 40f)
    }

    @Test
    fun editingAnExistingNoteStartsWithTheCursorAtTheEnd() {
        var note by mutableStateOf("Almuerzo")
        rule.setContent {
            SelloTheme {
                NoteComposer(
                    AnnotatedString("Gasto en Comida"), note, { note = it }, "${note.length}/60",
                    "Opcional", null, "Listo", "Cancelar", {}, {}
                )
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput(" del equipo")
        assertEquals("Almuerzo del equipo", note)
    }

    @Test
    fun longNoteKeepsCancelAndDoneVisibleAtDoubleFont() {
        val note = "Nota sintética que conserva todas sus palabras. ".repeat(8)
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            NoteComposer(
                AnnotatedString("Gasto en Comida"), note, {}, "${note.length}/60",
                "Opcional", "Máximo 60 caracteres", "Listo", "Cancelar", {}, {}
            )
        }) {
            rule.onNode(hasSetTextAction()).assertTextContains(note)
            rule.onNodeWithText("Cancelar").assertIsDisplayed()
            rule.onNodeWithContentDescription("Listo").assertIsDisplayed().assertIsNotEnabled()
        }
    }

    @Test
    fun twoChipRowsAreVisiblyApartAndKeep48dpTargets() {
        rule.inEveryScheme(content = {
            ChipRows(4) { SelloChip("Categoría $it", false, {}) }
        }) {
            // A horizontal grid fills down first: 0 and 1 share the first column.
            val upper = rule.onNodeWithText("Categoría 0").getUnclippedBoundsInRoot()
            val lower = rule.onNodeWithText("Categoría 1").getUnclippedBoundsInRoot()
            assertTrue(lower.top - upper.bottom >= 6.dp)
            val chip = rule.onNodeWithText("Categoría 0").fetchSemanticsNode()
            val target = with(chip.layoutInfo.density) { chip.touchBoundsInRoot.height.toDp() }
            assertTrue(target >= 47.9.dp)
        }
    }

    @Test
    fun chipsInAColumnTakeTheWidestChipsWidth() {
        val names = listOf("Ocio", "Una categoría larga", "Café", "Ropa")
        rule.inEveryScheme(content = {
            ChipRows(names.size) { SelloChip(names[it], false, {}) }
        }) {
            val short = rule.onNodeWithText("Ocio").getUnclippedBoundsInRoot()
            val long = rule.onNodeWithText("Una categoría larga").getUnclippedBoundsInRoot()
            assertEquals(long.left, short.left)
            assertEquals(long.right, short.right)
        }
    }

    @Test
    fun aFewChipsStartAtTheLeadingEdge() {
        rule.inEveryScheme(content = {
            ChipRows(2, Modifier.testTag("rows")) {
                SelloChip(listOf("Salario", "Otro")[it], false, {})
            }
        }) {
            val rows = rule.onNodeWithTag("rows").getUnclippedBoundsInRoot()
            assertEquals(rows.left, rule.onNodeWithText("Salario").getUnclippedBoundsInRoot().left)
        }
    }

    @Test
    fun landscapeEntryKeepsAmountActionAndPadFixedWhileChoicesScroll() {
        rule.inEveryScheme(fontScale = 2f, width = 760.dp, densityScale = 0.4f, content = {
            Box(Modifier.height(400.dp)) {
                EntryLayout(
                    header = { Text("Gasto / Ingreso") },
                    amount = { Text("48.700", Modifier.testTag("amount")) },
                    choices = {
                        repeat(40) { Text("Categoría $it", Modifier.testTag("choice:$it")) }
                    },
                    action = { Text("Anotar gasto", Modifier.height(56.dp).testTag("action")) },
                    keypad = { Text("Sello pad", Modifier.height(192.dp).testTag("pad")) }
                )
            }
        }) {
            val amount = rule.onNodeWithTag("amount")
            val before = amount.getUnclippedBoundsInRoot().top
            val padBefore = rule.onNodeWithTag("pad").getUnclippedBoundsInRoot().top
            rule.onNodeWithTag("choice:39").performScrollTo().assertIsDisplayed()
            amount.assertIsDisplayed()
            assertEquals(before, amount.getUnclippedBoundsInRoot().top)
            assertEquals(padBefore, rule.onNodeWithTag("pad").getUnclippedBoundsInRoot().top)
            rule.onNodeWithTag("action").assertIsDisplayed()
        }
    }

    @Test
    fun aGoalChipCarriesItsPercentageAfterTheNameAndIsReadWithIt() {
        rule.inEveryScheme(content = {
            SelloChip(
                "Viaje a la costa",
                selected = false,
                onClick = {},
                icon = SelloIcon.Flight,
                modifier = Modifier.testTag("chip"),
                trailing = "57 %"
            )
        }) {
            val name = rule.onNodeWithText("Viaje a la costa", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val share = rule.onNodeWithText("57 %", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertTrue("$share after $name", share.left >= name.right)
            rule.onNodeWithTag("chip").assertTextContains("57 %")
        }
    }

    @Test
    fun addChipIsAReachableButtonAtDoubleFont() {
        var added = 0
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            AddChip("Nueva", { added++ })
        }) {
            val chip = rule.onNodeWithText("Nueva")
            chip.assertIsDisplayed().performClick()
            val node = chip.fetchSemanticsNode()
            assertTrue(
                with(node.layoutInfo.density) {
                    node.touchBoundsInRoot.height.toDp()
                } >= 47.9.dp
            )
        }
        assertEquals(4, added)
    }

    @Test
    fun reducedMotionRetainsTheEarnedMilestoneMeaningAfterWithdrawal() {
        rule.inEveryScheme(reducedMotion = true, content = {
            MilestonePlace(
                MilestoneSpec(
                    "50 %",
                    "50 por ciento, sello conservado tras un retiro",
                    MilestoneState.Kept
                ),
                land = true
            )
        }) {
            rule.onNodeWithContentDescription(
                "50 por ciento, sello conservado tras un retiro"
            ).assertIsDisplayed()
        }
    }
}
