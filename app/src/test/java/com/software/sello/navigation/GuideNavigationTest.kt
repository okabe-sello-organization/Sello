package com.software.sello.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.guide.GUIDE_TAG
import com.software.sello.feature.guide.GuidePage
import com.software.sello.feature.guide.guidePageTag
import com.software.sello.feature.guide.guideRowTag
import com.software.sello.revealed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Guía de Sello in a real navigation host: opened from Configuración or at a page from
 * any screen, Back returns to where the person was, and the page and its scroll survive
 * the activity being recreated or the process stopped (SELLO-206).
 */
@RunWith(AndroidJUnit4::class)
class GuideNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var navigation: NavHostController

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            SelloTheme(reducedMotion = true) {
                // A short window, so that a page has to scroll.
                Box(Modifier.requiredSize(412.dp, 420.dp)) {
                    navigation = rememberNavController()
                    NavHost(navigation, startDestination = ReciboRoute) {
                        composable<ReciboRoute> { Text("Pantalla de antes") }
                        guideDestinations(navigation, mutableStateOf(octoberShell)) {}
                    }
                }
            }
        }
        if (restoration == null) rule.setContent(content) else restoration.setContent(content)
    }

    private fun back() = rule.onNodeWithContentDescription("Volver").performClick()

    @Test
    fun aHelpSheetOpensThePageAndBackReturnsToItsScreen() {
        show()

        rule.runOnIdle { navigation.openGuide(GuidePage.Backup) }

        rule.onNodeWithTag(guidePageTag(GuidePage.Backup)).assertIsDisplayed()
        rule.onNodeWithText("Solo en este teléfono").assertIsDisplayed()
        back()
        rule.onNodeWithText("Pantalla de antes").assertIsDisplayed()
    }

    @Test
    fun theListOpensAPageAndBackReturnsToTheListThenOut() {
        show()

        rule.runOnIdle { navigation.navigate(GuideRoute) }
        rule.onNodeWithText("Guía de Sello").assertIsDisplayed()
        rule.onNodeWithTag(guideRowTag(GuidePage.Recibo)).revealed().performClick()
        rule.onNodeWithTag(guidePageTag(GuidePage.Recibo)).assertIsDisplayed()
        back()
        rule.onNodeWithTag(GUIDE_TAG).assertIsDisplayed()
        back()
        rule.onNodeWithText("Pantalla de antes").assertIsDisplayed()
    }

    @Test
    fun anIdThisVersionDoesNotKnowShowsTheList() {
        show()

        rule.runOnIdle { navigation.navigate(GuidePageRoute("trucos")) }

        rule.onNodeWithTag(GUIDE_TAG).assertIsDisplayed()
    }

    @Test
    fun thePageAndItsScrollSurviveRecreation() {
        val restoration = StateRestorationTester(rule)
        show(restoration)
        rule.runOnIdle { navigation.openGuide(GuidePage.Recibo) }
        rule.onNodeWithText("Tus categorías").revealed()
        rule.onNodeWithText("Restante del presupuesto").assertIsNotDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag(guidePageTag(GuidePage.Recibo)).assertIsDisplayed()
        rule.onNodeWithText("Tus categorías").assertIsDisplayed()
        rule.onNodeWithText("Restante del presupuesto").assertIsNotDisplayed()
    }
}
