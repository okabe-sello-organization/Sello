package com.software.sello.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.feature.category.CategoryEditorState
import com.software.sello.feature.category.CategoryEditorViewModel
import com.software.sello.testing.testMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A starter chip's route reaches the category form whole (SELLO-210): the name and icon
 * the route carries are the ones the form starts with, through the real saved state.
 */
@RunWith(AndroidJUnit4::class)
class StarterCategoryRouteTest {
    @get:Rule
    val rule = createComposeRule()

    /** Nothing is sent in this test; a submit would fail it. */
    private object NoCommands : CategoryCommands {
        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome =
            error("Nothing is saved by opening the form: $command")

        override suspend fun find(
            operationId: OperationId
        ): Outcome<CommittedCategoryChange?, StorageFailure> = Outcome.Success(null)
    }

    private lateinit var navigation: NavHostController
    private var editor: CategoryEditorViewModel? = null

    private fun open(route: CategoryEditorRoute) {
        val session = MonthSession(HandFinancialClock("2026-10-09"), HandMonotonicClock())
        rule.setContent {
            navigation = rememberNavController()
            NavHost(navigation, startDestination = ReciboRoute) {
                composable<ReciboRoute> { Text("Recibo") }
                composable<CategoryEditorRoute> {
                    editor = viewModel {
                        CategoryEditorViewModel(
                            createSavedStateHandle(),
                            NoCommands,
                            HandCategoryReads(),
                            session,
                            { "00000000-0000-4000-8000-000000000500" },
                            testMoney()
                        )
                    }
                    Text("Nueva categoría")
                }
            }
        }
        rule.runOnIdle { navigation.navigate(route) }
        rule.waitForIdle()
    }

    @Test
    fun aStarterRouteOpensTheFormWithItsNameAndIconAndNoLimit() {
        open(CategoryEditorRoute(name = "Transporte", icon = "directions_bus"))

        val opened: CategoryEditorState = editor!!.state.value
        assertEquals("Transporte" to "directions_bus", opened.name to opened.iconKey)
        assertEquals("", opened.limitText)
        assertFalse(opened.unlimited)
    }
}
