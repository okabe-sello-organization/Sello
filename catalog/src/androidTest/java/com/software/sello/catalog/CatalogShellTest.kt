package com.software.sello.catalog

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.catalog.foundations.tokenTag
import com.software.sello.catalog.foundations.typeTag
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogShellTest {
    @get:Rule
    val rule = createAndroidComposeRule<CatalogActivity>()

    private fun choose(control: String, option: String) {
        rule.onNodeWithTag(controlTag(control, option)).performScrollTo().performClick()
    }

    private fun open(id: String) {
        rule.onNodeWithTag(exampleLinkTag(id)).performScrollTo().performClick()
        rule.onNodeWithTag(exampleTag(id)).assertIsDisplayed()
    }

    private fun token(name: String) = rule.onNodeWithTag(tokenTag(name)).performScrollTo()

    @Test
    fun homeListsEveryExampleUnderItsComponentType() {
        rule.onNodeWithTag(groupTag(CatalogGroup.Foundations)).assertIsDisplayed()
        rule.onNodeWithText("Foundations").assertIsDisplayed()
        catalogExamples.forEach { rule.onNodeWithTag(exampleLinkTag(it.id)).assertExists() }
        // Every component type now has examples, and each link sits inside its own group.
        CatalogGroup.entries.forEach { group ->
            val links = catalogExamples.filter { it.group == group }
            assertTrue(group.id, links.isNotEmpty())
            links.forEach {
                rule.onNode(
                    hasTestTag(exampleLinkTag(it.id)) and
                        hasAnyAncestor(hasTestTag(groupTag(group)))
                )
                    .assertExists()
            }
        }
    }

    @Test
    fun everyExampleOpensByItsIdAndBackReturnsHome() {
        catalogExamples.forEach { example ->
            open(example.id)
            rule.onNodeWithTag(BACK_TAG).performClick()
            rule.onNodeWithTag(exampleTag(example.id)).assertDoesNotExist()
        }
        open("foundations.icons")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag(exampleLinkTag("foundations.icons")).assertExists()
    }

    @Test
    fun theScaffoldExampleShowsMetasGreenDockOnRequest() {
        open("navigation.scaffold")
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Anotar"))

        rule.onNodeWithTag("scaffold:metas-dock").performScrollTo().performClick()

        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Aportar a una meta"))
    }

    @Test
    fun backReturnsToTheSameSpotInTheList() {
        val link = rule.onNodeWithTag(exampleLinkTag("inputs.entry")).performScrollTo()
        val before = link.getUnclippedBoundsInRoot().top
        link.performClick()
        rule.onNodeWithTag(BACK_TAG).performClick()
        assertEquals(
            before,
            rule.onNodeWithTag(exampleLinkTag("inputs.entry")).getUnclippedBoundsInRoot().top
        )
    }

    @Test
    fun switchingInkChangesBrandButNotGainOrLoss() {
        rule.onNodeWithTag(CONTROLS_TOGGLE_TAG).performClick()
        choose("mode", "Light")
        open("foundations.colors")

        token("brand").assertTextEquals("#1F3BE0")
        token("gain").assertTextEquals("#0A6B4A")
        token("loss").assertTextEquals("#B81E2A")

        choose("ink", "Violeta")
        token("brand").assertTextEquals("#5B2BD6")
        token("desk").assertTextEquals("#E2DAF6")
        token("gain").assertTextEquals("#0A6B4A")
        token("loss").assertTextEquals("#B81E2A")
    }

    @Test
    fun modeControlSelectsLightOrDarkForEitherInk() {
        rule.onNodeWithTag(CONTROLS_TOGGLE_TAG).performClick()
        open("foundations.colors")

        choose("mode", "Dark")
        token("brand").assertTextEquals("#8FA6FF")
        token("paper").assertTextEquals("#151C36")
        token("gain").assertTextEquals("#4FD6A0")

        choose("ink", "Violeta")
        token("brand").assertTextEquals("#B79CFF")
        token("paper").assertTextEquals("#1A1433")
        token("gain").assertTextEquals("#4FD6A0")

        choose("mode", "Light")
        token("brand").assertTextEquals("#5B2BD6")
    }

    @Test
    fun fontControlScalesExampleText() {
        rule.onNodeWithTag(CONTROLS_TOGGLE_TAG).performClick()
        open("foundations.typography")
        val sample = rule.onNodeWithTag(typeTag("labelSmall"))
        val normal = sample.performScrollTo().getUnclippedBoundsInRoot().height

        choose("font", "Largest")
        val largest = sample.performScrollTo().getUnclippedBoundsInRoot().height

        assertTrue("$normal -> $largest", largest > normal * 1.8f)
    }

    @Test
    fun windowControlSetsTheStageWidth() {
        rule.onNodeWithTag(CONTROLS_TOGGLE_TAG).performClick()
        val stage = rule.onNodeWithTag(STAGE_TAG)
        val device = stage.getUnclippedBoundsInRoot().width

        choose("window", "Compact")
        assertEquals(360.dp, stage.getUnclippedBoundsInRoot().width)

        choose("window", "Expanded")
        assertEquals(840.dp, stage.getUnclippedBoundsInRoot().width)

        choose("window", "Device")
        assertEquals(device, stage.getUnclippedBoundsInRoot().width)
    }

    @Test
    fun choicesAndOpenExampleSurviveRecreation() {
        rule.onNodeWithTag(CONTROLS_TOGGLE_TAG).performClick()
        choose("ink", "Violeta")
        choose("mode", "Dark")
        open("foundations.colors")

        rule.activityRule.scenario.recreate()

        rule.onNodeWithTag(exampleTag("foundations.colors")).assertIsDisplayed()
        rule.onNodeWithTag(controlTag("ink", "Violeta")).assertIsSelected()
        token("brand").assertTextEquals("#B79CFF")
    }
}
