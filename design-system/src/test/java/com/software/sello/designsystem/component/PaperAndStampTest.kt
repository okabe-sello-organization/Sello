package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaperAndStampTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun stampsAnnounceTheOrdinaryWordWhateverTheirInkOrSize() {
        rule.inEveryScheme(
            content = {
                Column {
                    Stamp("Al día", StampInk.Good, Modifier.testTag("small"))
                    Stamp("Pasado", StampInk.Over, Modifier.testTag("large"), StampSize.Large)
                    Stamp(
                        "Lograda",
                        StampInk.Done,
                        Modifier.testTag("hero"),
                        StampSize.Hero,
                        land = true
                    )
                }
            }
        ) {
            rule.onNodeWithTag("small").assertContentDescriptionEquals("Al día")
            rule.onNodeWithTag("large").assertContentDescriptionEquals("Pasado")
            rule.onNodeWithTag("hero").assertContentDescriptionEquals("Lograda")
            // A stamp is a verdict, never a control.
            rule.onNodeWithTag("hero").assertHasNoClickAction()
        }
    }

    @Test
    fun aLandingStampIsFullyReadableWhileItComesDown() {
        rule.mainClock.autoAdvance = false
        rule.inEveryScheme(
            reducedMotion = false,
            content = { Stamp("Recibido", StampInk.Good, Modifier.testTag("stamp"), land = true) }
        ) {
            rule.onNodeWithTag("stamp").assertContentDescriptionEquals("Recibido")
        }
    }

    @Test
    fun aClickableSlipIsOneButtonThatReportsEachPress() {
        var presses = 0
        rule.inEveryScheme(
            reset = { presses = 0 },
            content = {
                Slip(modifier = Modifier.testTag("slip"), onClick = { presses++ }) {
                    Text("Te quedan")
                    MoneyText(MoneyTextValue(MoneySign.None, "", "937.200", "937.200 pesos"))
                }
            }
        ) {
            rule.onNodeWithTag("slip").assert(isButton).assertHasClickAction()
            rule.onNodeWithTag("slip").assertTextContains("Te quedan")
            rule.onNodeWithTag("slip").assertContentDescriptionEquals("937.200 pesos")
            rule.onNodeWithTag("slip").performClick()
            assertEquals(it, 1, presses)
        }
    }

    @Test
    fun leaderAndAmountSitOnTheLabelsFirstLineNotTheMiddleOfTheRow() = leaderOnLabel()

    // v1.1 `.ln`: the row aligns on its first baseline, so the amount shares the label's
    // line and the dots run 5 px above it (`.ld`: 2 px border raised 4 px), however tall
    // the date under the label makes the row.
    private fun leaderOnLabel() {
        rule.inEveryScheme(
            width = 360.dp,
            content = {
                Slip {
                    LeaderLine("Rappi, almuerzo", Modifier.testTag("dated"), caption = "mié 21") {
                        MoneyText(MoneyTextValue(MoneySign.None, "", "20.600", "20.600 pesos"))
                    }
                    LeaderLine("Fecha", "jue 22 oct", Modifier.testTag("plain"))
                }
            }
        ) {
            rule.assertLeaderOnLabel(it, "dated", "Rappi, almuerzo") {
                hasContentDescription("20.600 pesos")
            }
            rule.assertLeaderOnLabel(it, "plain", "Fecha") {
                hasText("jue 22 oct")
            }
        }
    }
}

private fun ComposeContentTestRule.assertLeaderOnLabel(
    scheme: String,
    row: String,
    label: String,
    value: () -> SemanticsMatcher
) {
    fun inRow(matcher: SemanticsMatcher) =
        onNode(matcher and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)

    val labelBaseline = inRow(hasText(label)).baselineInRoot()

    val valueBaseline = inRow(value()).baselineInRoot()

    val dots = inRow(hasTestTag(LEADER_DOTS_TAG)).getUnclippedBoundsInRoot()

    val dotsCentre = (dots.top + dots.bottom) / 2

    assertNear("$scheme $row amount baseline", labelBaseline, valueBaseline)
    assertNear("$scheme $row dots", labelBaseline - 5.dp, dotsCentre)
}

private fun SemanticsNodeInteraction.baselineInRoot(): Dp =
    getUnclippedBoundsInRoot().top + getAlignmentLinePosition(FirstBaseline)

private fun assertNear(what: String, expected: Dp, actual: Dp) =
    assertTrue("$what at $actual, expected $expected", abs((actual - expected).value) <= 1f)
