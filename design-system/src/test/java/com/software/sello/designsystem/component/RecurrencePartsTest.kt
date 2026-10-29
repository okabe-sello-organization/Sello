package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.designsystem.icon.SelloIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The parts of a schedule: Se repite's sheet, the list's rows, the choice and the notice. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecurrencePartsTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val isCheckbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun days(): List<DpRect> = rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        .map { node ->
            with(rule.density) {
                val box = node.boundsInRoot
                DpRect(box.left.toDp(), box.top.toDp(), box.right.toDp(), box.bottom.toDp())
            }
        }

    private fun assertTargets(scheme: String, boxes: List<DpRect>) = boxes.forEach {
        assertTrue("$scheme $it", it.width >= 48.dp && it.height >= 48.dp)
    }

    @Test
    fun everyDayIsOneToggleReadAsItsDayWithWhetherItIsChosen() {
        val toggled = mutableListOf<Int>()
        rule.inEveryScheme(reset = { toggled.clear() }, content = {
            DayGrid(setOf(5, 30), { toggled += it }, { "Día $it" })
        }) { scheme ->
            assertEquals(scheme, 31, rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
            rule.onNodeWithContentDescription("Día 5").assert(isCheckbox).assertIsOn()
            rule.onNodeWithContentDescription("Día 30").assertIsOn()
            rule.onNodeWithContentDescription("Día 6").assertIsOff().performClick()
            rule.onNodeWithContentDescription("Día 5").performClick()
            assertEquals(scheme, listOf(6, 5), toggled)
            // The number is not read a second time after the description.
            val day = rule.onNodeWithContentDescription("Día 31").fetchSemanticsNode()
            assertEquals(scheme, null, day.config.getOrNull(SemanticsProperties.Text))
            val boxes = days()
            assertTargets(scheme, boxes)
            // Rows of seven where seven 48dp targets fit; fewer on a narrower screen.
            val width = boxes.maxOf { it.right } - boxes.minOf { it.left }
            val fits = minOf(7, (width.value / 48f).toInt())
            assertEquals(scheme, fits, boxes.map { it.left }.distinct().size)
        }
    }

    @Test
    fun aDayGridTurnedOffIgnoresTaps() {
        var toggles = 0
        rule.inEveryScheme(reset = { toggles = 0 }, content = {
            DayGrid(setOf(5), { toggles++ }, { "Día $it" }, enabled = false)
        }) { scheme ->
            rule.onNodeWithContentDescription("Día 6").assertIsNotEnabled().performClick()
            assertEquals(scheme, 0, toggles)
        }
    }

    @Test
    fun atTwiceTheFontSizeTheDaysTakeFourColumnsWithinANarrowScreen() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            DayGrid(setOf(15, 30), {}, { "Día $it" })
        }) { scheme ->
            val boxes = days()
            assertEquals(scheme, 31, boxes.size)
            assertEquals(scheme, 4, boxes.map { it.left }.distinct().size)
            assertTrue(scheme, boxes.all { it.right <= 320.dp })
            assertTargets(scheme, boxes)
        }
    }

    @Test
    fun whereSevenTargetsDoNotFitTheRowHoldsFewerAndKeepsThemAt48dp() {
        rule.inEveryScheme(width = 320.dp, content = {
            DayGrid(setOf(1), {}, { "Día $it" })
        }) { scheme ->
            val boxes = days()
            assertEquals(scheme, 6, boxes.map { it.left }.distinct().size)
            assertTargets(scheme, boxes)
        }
    }

    @Test
    fun theStepperReadsItsValueWithTheUnitAndStopsAtItsBounds() {
        var value by mutableIntStateOf(1)
        var increases = 0
        var decreases = 0
        rule.inEveryScheme(reset = {
            value = 1
            increases = 0
            decreases = 0
        }, content = {
            IntervalStepper(
                value = value,
                unit = if (value == 1) "semana" else "semanas",
                onDecrease = { decreases++ },
                onIncrease = {
                    increases++
                    value++
                },
                decreaseLabel = "Menos",
                increaseLabel = "Más",
                canDecrease = value > 1,
                canIncrease = value < 52
            )
        }) { scheme ->
            rule.onNodeWithContentDescription("Menos").assert(isButton).assertIsNotEnabled()
                .performClick()
            assertEquals(scheme, 0, decreases)
            val read = rule.onNodeWithContentDescription("1 semana").fetchSemanticsNode()
            assertEquals(
                scheme,
                LiveRegionMode.Polite,
                read.config.getOrNull(SemanticsProperties.LiveRegion)
            )
            rule.onNodeWithContentDescription("Más").assert(isButton).assertIsEnabled()
                .performClick()
            assertEquals(scheme, 1, increases)
            rule.onNodeWithContentDescription("2 semanas").assertIsDisplayed()
            rule.onNodeWithContentDescription("Menos").assertIsEnabled()
            listOf("Menos", "Más").forEach {
                val box = rule.onNodeWithContentDescription(it).getUnclippedBoundsInRoot()
                assertTrue("$scheme $it", box.width >= 48.dp && box.height >= 48.dp)
            }
        }
    }

    @Test
    fun theTopStepIsOff() {
        var increases = 0
        rule.inEveryScheme(reset = { increases = 0 }, content = {
            IntervalStepper(52, "semanas", {}, { increases++ }, "Menos", "Más", canIncrease = false)
        }) { scheme ->
            rule.onNodeWithContentDescription("Más").assertIsNotEnabled().performClick()
            assertEquals(scheme, 0, increases)
            rule.onNodeWithContentDescription("Menos").assertIsEnabled()
        }
    }

    private val busSpoken = "Bus, 20.000 pesos, cada semana, los sábados, en pausa desde lun 12 oct"

    @Test
    fun aScheduleRowIsOneButtonReadAsOneSentence() {
        var opened = 0
        rule.inEveryScheme(reset = { opened = 0 }, content = {
            ScheduleRow(
                name = "Bus",
                caption = "Transporte · cada semana, los sábados · en pausa desde lun 12 oct",
                amount = pesos("20.000"),
                icon = SelloIcon.DirectionsBus,
                onClick = { opened++ },
                spoken = busSpoken,
                pausedLabel = "En pausa",
                dimmed = true,
                modifier = Modifier.testTag("row")
            )
        }) { scheme ->
            val row = rule.onNodeWithContentDescription(busSpoken).assert(isButton)
                .assertIsDisplayed()
            // Name, caption, stamp and amount are not read again one by one.
            val said = row.fetchSemanticsNode().config
            assertEquals(
                scheme,
                listOf(busSpoken),
                said.getOrNull(SemanticsProperties.ContentDescription)
            )
            assertEquals(scheme, null, said.getOrNull(SemanticsProperties.Text))
            assertTrue(scheme, row.fetchSemanticsNode().children.isEmpty())
            assertEquals(
                scheme,
                1,
                rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                    .fetchSemanticsNodes().size
            )
            rule.onNodeWithContentDescription("En pausa", useUnmergedTree = true)
                .assertIsDisplayed()
            row.performClick()
            assertEquals(scheme, 1, opened)
            assertTrue(
                scheme,
                rule.onNodeWithTag("row").getUnclippedBoundsInRoot().height >= 60.dp
            )
        }
    }

    @Test
    fun atTwiceTheFontSizeARowFitsANarrowScreenWithTheAmountUnderTheName() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            ScheduleRow(
                name = "Servicios públicos",
                caption = "Hogar · el 28 de cada mes · próximo mié 28 oct",
                amount = pesos("200.000"),
                icon = SelloIcon.Home,
                onClick = {},
                spoken = "Servicios públicos, 200.000 pesos, el 28 de cada mes",
                soon = true,
                pausedLabel = "En pausa",
                modifier = Modifier.testTag("row")
            )
        }) { scheme ->
            val row = rule.onNodeWithTag("row").getUnclippedBoundsInRoot()
            assertTrue("$scheme ${row.right}", row.right <= 320.dp)
            val name = rule.onNodeWithText("Servicios públicos", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val amount = rule.onNodeWithText("200.000", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            assertTrue("$scheme $name $amount", amount.top >= name.bottom)
            assertTrue("$scheme ${amount.right}", amount.right <= 320.dp)
        }
    }

    private class Answers {
        var first = 0
        var second = 0
        var back = 0

        fun said() = Triple(first, second, back)
    }

    @Test
    fun eachAnswerAndTheWayBackDoOnlyTheirOwnThing() {
        val answers = Answers()
        rule.inEveryScheme(reset = {
            answers.first = 0
            answers.second = 0
            answers.back = 0
        }, content = {
            ChoiceSlip(
                title = "¿Cambiar también los siguientes?",
                message = AnnotatedString("Este gasto lo anotó Arriendo."),
                firstLabel = "Solo este",
                onFirst = { answers.first++ },
                secondLabel = "Este y los siguientes",
                onSecond = { answers.second++ },
                backLabel = "Volver a editar",
                onBack = { answers.back++ }
            )
        }) { scheme ->
            rule.onNode(isHeading()).assertIsDisplayed()
            rule.onNodeWithText("Solo este").assert(isButton).performClick()
            assertEquals(scheme, Triple(1, 0, 0), answers.said())
            rule.onNodeWithText("Este y los siguientes").assert(isButton).performClick()
            assertEquals(scheme, Triple(1, 1, 0), answers.said())
            rule.onNodeWithText("Volver a editar").assert(isButton).performClick()
            assertEquals(scheme, Triple(1, 1, 1), answers.said())
            listOf("Solo este", "Este y los siguientes", "Volver a editar").forEach {
                assertTrue(
                    "$scheme $it",
                    rule.onNodeWithText(it).getUnclippedBoundsInRoot().height >= 46.dp
                )
            }
        }
    }

    @Test
    fun aBusyChoiceCannotBeAnsweredAgainOrLeft() {
        val answers = Answers()
        var busy by mutableStateOf(false)
        rule.setContent {
            com.software.sello.designsystem.theme.SelloTheme {
                ChoiceSlip(
                    "¿Cambiar también los siguientes?",
                    AnnotatedString("Este gasto lo anotó Arriendo."),
                    "Solo este",
                    {
                        answers.first++
                        busy = true
                    },
                    "Este y los siguientes",
                    { answers.second++ },
                    "Volver a editar",
                    { answers.back++ },
                    busy = busy
                )
            }
        }
        rule.onNodeWithText("Solo este").performClick()
        rule.onNodeWithText("Solo este").assertIsNotEnabled().performClick()
        rule.onNodeWithText("Este y los siguientes").assertIsNotEnabled().performClick()
        rule.onNodeWithText("Volver a editar").assertIsNotEnabled().performClick()
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(Triple(1, 0, 0), answers.said())
    }

    @Test
    fun atTwiceTheFontSizeTheAnswersStandOneAboveTheOther() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            ChoiceSlip(
                "¿Cambiar también los siguientes?",
                AnnotatedString("Este gasto lo anotó Arriendo."),
                "Solo este",
                {},
                "Este y los siguientes",
                {},
                "Volver a editar",
                {}
            )
        }) { scheme ->
            val first = rule.onNodeWithText("Solo este").getUnclippedBoundsInRoot()
            val second = rule.onNodeWithText("Este y los siguientes").getUnclippedBoundsInRoot()
            assertTrue("$scheme $first $second", second.top >= first.bottom)
            assertEquals(scheme, first.left, second.left)
            val screen = InstrumentationRegistry.getInstrumentation().targetContext.resources
                .configuration.screenWidthDp.dp
            assertTrue("$scheme $second", second.right <= screen)
        }
    }

    @Test
    fun atTwiceTheFontSizeTheSheetActionsStandOneAboveTheOther() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            RepeatSheetActions("Quitar", {}, "Listo", {})
        }) { scheme ->
            val dismiss = rule.onNodeWithText("Quitar").getUnclippedBoundsInRoot()
            val confirm = rule.onNodeWithText("Listo").getUnclippedBoundsInRoot()
            assertTrue("$scheme $dismiss $confirm", confirm.top >= dismiss.bottom)
            assertTrue(scheme, confirm.right <= 320.dp && dismiss.right <= 320.dp)
        }
    }

    @Test
    fun theSheetActionsShareARowAThirdAndTwoThirds() {
        var quit = 0
        var done = 0
        rule.inEveryScheme(reset = {
            quit = 0
            done = 0
        }, content = {
            RepeatSheetActions("Quitar", { quit++ }, "Listo", { done++ })
        }) { scheme ->
            val dismiss = rule.onNodeWithText("Quitar").getUnclippedBoundsInRoot()
            val confirm = rule.onNodeWithText("Listo").getUnclippedBoundsInRoot()
            assertEquals(scheme, dismiss.top, confirm.top)
            assertTrue("$scheme $dismiss $confirm", confirm.width > dismiss.width * 1.8f)
            rule.onNodeWithText("Quitar").performClick()
            rule.onNodeWithText("Listo").performClick()
            assertEquals(scheme, 1 to 1, quit to done)
        }
    }

    private var dismissed = 0

    private fun showSheet(fontScale: Float = 1f, height: Float = 640f) {
        dismissed = 0
        rule.inEveryScheme(fontScale = fontScale, reset = { dismissed = 0 }, content = {
            Box(Modifier.height(height.dp)) {
                RepeatSheet("Se repite", onDismiss = { dismissed++ }) {
                    DayGrid(setOf(5), {}, { "Día $it" })
                    IntervalStepper(1, "semana", {}, {}, "Menos", "Más")
                    RepeatNext("Próximo: lun 5 nov. Se anota solo en su fecha.")
                    RepeatSheetActions("Quitar", {}, "Listo", {})
                }
            }
        }) { scheme -> check(scheme) }
    }

    private var check: (String) -> Unit = {}

    @Test
    fun theSheetTitleIsAHeadingAndATapOnTheScrimClosesIt() {
        check = { scheme ->
            rule.onNode(isHeading()).assertIsDisplayed()
            assertEquals(scheme, "Se repite", headingText())
            // A tap on the paper leaves it open; one above it, on the scrim, closes it.
            rule.onNodeWithText("Se repite").performClick()
            assertEquals(scheme, 0, dismissed)
            rule.onRoot().performTouchInput { click(Offset(centerX, 4f)) }
            assertEquals(scheme, 1, dismissed)
        }
        showSheet()
    }

    private fun headingText() = rule.onNode(isHeading()).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    @Test
    fun atTwiceTheFontSizeTheSheetScrollsToItsLastAction() {
        check = { scheme ->
            assertTrue(
                scheme,
                rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty()
            )
            rule.onNodeWithText("Listo").performScrollTo().assertIsDisplayed().performClick()
            rule.onNode(isHeading()).assertExists()
            assertEquals(scheme, 0, dismissed)
        }
        showSheet(fontScale = 2f, height = 560f)
    }

    @Test
    fun aNoticeWithAnIconKeepsItsMessageAndActionAndDrawsNoCheck() {
        var seen = 0
        rule.inEveryScheme(reset = { seen = 0 }, content = {
            SelloNotice(
                "Sello anotó 4 pagos fijos",
                actionLabel = "Ver",
                onAction = { seen++ },
                confirmation = true,
                icon = SelloIcon.Autorenew
            )
        }) { scheme ->
            rule.onNodeWithText("Sello anotó 4 pagos fijos").assertIsDisplayed()
            rule.onNodeWithText("Ver").assert(isButton).performClick()
            assertEquals(scheme, 1, seen)
            val checks = rule.onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SELLO_NOTICE_CHECK_TAG),
                useUnmergedTree = true
            )
            assertFalse(scheme, checks.fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun aRowCarriesItsDayInAPillAndARecordedOneStaysOneButton() {
        var opened = 0
        rule.inEveryScheme(reset = { opened = 0 }, content = {
            Column {
                ScheduleRow(
                    name = "Aseo carro",
                    caption = "el 4 de cada mes",
                    amount = pesos("30.000"),
                    icon = SelloIcon.DirectionsCar,
                    onClick = {},
                    spoken = "Aseo carro, 30.000 pesos, el 4 de cada mes, Mañana, jue 4 feb",
                    pill = "Mañana, jue 4 feb"
                )
                ScheduleRow(
                    name = "Arriendo",
                    caption = "el 1 de cada mes · anotado lun 1 feb",
                    amount = pesos("1.200.000"),
                    icon = SelloIcon.Home,
                    onClick = { opened++ },
                    spoken = "Arriendo, 1.200.000 pesos, anotado lun 1 feb, próximo lun 1 mar",
                    pill = "próximo lun 1 mar",
                    recorded = true,
                    last = true
                )
            }
        }) { scheme ->
            rule.onNodeWithText("Mañana, jue 4 feb", useUnmergedTree = true).assertExists()
            rule.onNodeWithText("próximo lun 1 mar", useUnmergedTree = true).assertExists()
            rule.onNodeWithContentDescription(
                "Arriendo, 1.200.000 pesos, anotado lun 1 feb, próximo lun 1 mar"
            ).assert(isButton).performClick()
            assertEquals(scheme, 1, opened)
        }
    }
}
