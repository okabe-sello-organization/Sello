package com.software.sello.feature.goals

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.inEveryScheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The goal cards and rows keep their contracts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalContractsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun completedGoalRowCarriesItsStampOnTheRight() {
        rule.inEveryScheme(content = {
            CompletedGoalRow("Vacunas de Luna", "400.000, 30 sep", SelloIcon.Pets, "Lograda", {})
        }) {
            val name = rule.onNodeWithText(
                "Vacunas de Luna",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            val stamp = rule.onNodeWithContentDescription(
                "Lograda",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            assertTrue(stamp.left > name.right)
            assertTrue(stamp.top < name.bottom)
        }
    }

    @Test
    fun activeGoalCardPutsTheDateBesideTheTargetAndSpreadsItsPlaces() {
        val places = listOf("25 %", "50 %", "75 %", "Meta").map {
            MilestoneSpec(it, "$it, por alcanzar", MilestoneState.Empty)
        }
        rule.inEveryScheme(content = {
            GoalCard(
                "Curso de inglés", SelloIcon.School, "15 %",
                MoneyTextValue(MoneySign.None, "", "300.000", "300.000 pesos"),
                "de 2.000.000", "jun 2027", 0.15f, places, {}, Modifier.testTag("card")
            )
        }) {
            val target = rule.onNodeWithText(
                "de 2.000.000",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            val date = rule.onNodeWithText(
                "jun 2027",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            val middle = (date.top + date.bottom) / 2
            assertTrue(middle > target.top && middle < target.bottom)
            val card = rule.onNodeWithTag("card").getUnclippedBoundsInRoot()
            val meta = rule.onNodeWithText(
                "Meta",
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            // The last place reaches the card's trailing padding, and the teeth follow closely.
            assertTrue(card.right - meta.right < 40.dp)
            assertTrue(card.bottom - meta.bottom < 44.dp)
        }
    }
}
