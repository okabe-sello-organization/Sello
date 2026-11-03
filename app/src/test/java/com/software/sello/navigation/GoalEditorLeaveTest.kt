package com.software.sello.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.feature.goals.GoalEditorResult
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Where Editar meta leaves to: a deleted goal closes its page too, wherever it was opened. */
@RunWith(AndroidJUnit4::class)
class GoalEditorLeaveTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var navigation: NavHostController

    private fun show() = rule.setContent {
        navigation = rememberNavController()
        NavHost(navigation, startDestination = ReciboRoute) {
            composable<ReciboRoute> { Text("Recibo") }
            composable<MetasRoute> { Text("Metas") }
            composable<GoalRoute> { Text("Meta") }
            composable<GoalEditorRoute> { Text("Editar meta") }
        }
    }

    private fun open(vararg routes: Any) = rule.runOnIdle {
        routes.forEach { navigation.navigate(it) }
    }

    @Test
    fun deletingAGoalOpenedFromRecibosPillClosesTheEditorAndTheGoalAndShowsRecibo() {
        show()
        open(GoalRoute("g"), GoalEditorRoute("g"))

        rule.runOnIdle { navigation.leaveGoalEditor(GoalEditorResult.Deleted) }
        // The result is seen again, for instance after a rotation: nothing more closes.
        rule.runOnIdle { navigation.leaveGoalEditor(GoalEditorResult.Deleted) }

        rule.runOnIdle {
            assertTrue(navigation.currentDestination!!.hasRoute<ReciboRoute>())
            val stack = navigation.currentBackStack.value
            assertTrue(stack.none { it.destination.hasRoute<GoalRoute>() })
        }
    }

    @Test
    fun deletingAGoalOpenedFromMetasShowsMetas() {
        show()
        open(MetasRoute, GoalRoute("g"), GoalEditorRoute("g"))

        rule.runOnIdle { navigation.leaveGoalEditor(GoalEditorResult.Deleted) }

        rule.runOnIdle {
            assertTrue(navigation.currentDestination!!.hasRoute<MetasRoute>())
        }
    }

    @Test
    fun aSavedEditGoesBackToTheGoal() {
        show()
        open(GoalRoute("g"), GoalEditorRoute("g"))

        rule.runOnIdle { navigation.leaveGoalEditor(GoalEditorResult.Updated) }

        rule.runOnIdle {
            assertTrue(navigation.currentDestination!!.hasRoute<GoalRoute>())
        }
    }
}
