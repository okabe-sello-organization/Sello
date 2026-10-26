package com.software.sello.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.await
import com.software.sello.awaitTag
import com.software.sello.composition.SelloApplication
import com.software.sello.designsystem.component.SCAFFOLD_PRIMARY_TAG
import com.software.sello.designsystem.component.SCAFFOLD_SECONDARY_TAG
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.recibo.CATEGORY_DETAIL_HEADER_TAG
import com.software.sello.feature.recibo.CATEGORY_DETAIL_TAG
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.feature.recibo.reciboCategoryTag
import com.software.sello.readyViewModels
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real root, with the app's own view models and database, in a window as wide as
 * a tablet. One dp is drawn as one pixel so that 900dp fits the test device's screen.
 */
@RunWith(AndroidJUnit4::class)
class ReciboListDetailWideTest {
    @Before
    fun twoCategories() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        TestCommands.createCategory("Mercado", 300_000)
        TestCommands.createCategory("Café", 50_000)
        // The one used most comes first, and is the one shown before any is chosen.
        TestCommands.createExpense("Café", TestCommands.today, 4_000, "Tinto")
    }

    @After
    fun leaveNothingBehind() = TestData.reset()

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun inDetail(text: String) =
        hasText(text) and hasAnyAncestor(hasTestTag(CATEGORY_DETAIL_TAG))

    @Test
    fun reciboAndACategorysDetailAreSideBySideAndChoosingAnotherChangesOnlyTheDetail() {
        val viewModels = (rule.activity.application as SelloApplication).readyViewModels()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(900.dp, 700.dp)) { SelloAppRoot(viewModels) }
                }
            }
        }
        val coffee = reciboCategoryTag(TestCommands.categoryId("Café"))
        val market = reciboCategoryTag(TestCommands.categoryId("Mercado"))

        // The window may be wider than the device's screen, so: present, not "on screen".
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.awaitTag(CATEGORY_DETAIL_HEADER_TAG)
        rule.await(inDetail("Café"))
        rule.await(inDetail("Tinto"))
        rule.onNodeWithTag(coffee).assertIsSelected()
        rule.onNodeWithTag(market).assertIsNotSelected()
        val recibo = rule.onNodeWithTag(SCAFFOLD_PRIMARY_TAG).getUnclippedBoundsInRoot()
        val detail = rule.onNodeWithTag(SCAFFOLD_SECONDARY_TAG).getUnclippedBoundsInRoot()
        assertTrue("recibo $recibo, detail $detail", recibo.right <= detail.left)
        // Beside Recibo there is nothing to go back from.
        rule.onNodeWithTag(DETAIL_BACK_TAG).assertDoesNotExist()
        // The categories card keeps its add action in the header beside the detail.
        rule.onNode(
            hasText("Nueva categoría") and
                hasAnyAncestor(hasTestTag(SCAFFOLD_PRIMARY_TAG)) and
                hasClickAction()
        ).assertExists()

        rule.onNodeWithTag(market).performSemanticsAction(SemanticsActions.OnClick)

        rule.await(inDetail("Mercado"))
        rule.onNodeWithTag(market).assertIsSelected()
        rule.onNodeWithTag(coffee).assertIsNotSelected()
        rule.onNodeWithTag(RECIBO_SUMMARY_TAG).assertExists()
        rule.onNodeWithTag(DETAIL_BACK_TAG).assertDoesNotExist()
        rule.onNode(inDetail("Tinto")).assertDoesNotExist()
    }
}
