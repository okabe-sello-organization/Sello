package com.software.sello.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Lo que más se repite opens Todos los gastos for a note (SELLO-195): the note must arrive whole. */
@RunWith(AndroidJUnit4::class)
class CategoryExpensesRouteTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var navigation: NavHostController
    private var arrived: CategoryExpensesRoute? = null

    private fun open(route: CategoryExpensesRoute) {
        rule.setContent {
            navigation = rememberNavController()
            NavHost(navigation, startDestination = ReciboRoute) {
                composable<ReciboRoute> { Text("Recibo") }
                composable<CategoryExpensesRoute> { entry ->
                    arrived = entry.toRoute<CategoryExpensesRoute>()
                    Text("Todos los gastos")
                }
            }
        }
        rule.runOnIdle { navigation.navigate(route) }
        rule.waitForIdle()
    }

    @Test
    fun aNoteWithReservedCharactersReachesTheSearchFieldIntact() {
        val note = "50% & café/pan #1 ? sí"

        open(CategoryExpensesRoute("category", note))

        assertEquals(CategoryExpensesRoute("category", note), arrived)
    }

    @Test
    fun theWholeListOpensWithNoSearch() {
        open(CategoryExpensesRoute("category"))

        assertEquals("", arrived?.search)
    }
}
