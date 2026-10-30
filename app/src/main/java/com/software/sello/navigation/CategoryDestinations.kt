package com.software.sello.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.software.sello.feature.category.CategoryEditorRoot
import com.software.sello.feature.category.CategoryManagementRoot
import com.software.sello.feature.reset.back

/** Creating a category and editing one. */
internal fun NavGraphBuilder.categoryDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit
) {
    composable<CategoryEditorRoute> { entry ->
        val state by shellState
        val forEntry = entry.toRoute<CategoryEditorRoute>().forEntry
        CategoryEditorRoot(viewModels) { createdId ->
            val onEditor =
                navigation.currentBackStackEntry?.destination?.hasRoute<CategoryEditorRoute>()
            if (onEditor == true) {
                // Only the identifier goes back; whoever opened the form reads the rest.
                if (createdId != null) {
                    navigation.previousBackStackEntry?.savedStateHandle
                        ?.set(CREATED_CATEGORY_RESULT, createdId)
                }
                navigation.popBackStack()
                // The category was created so that an expense could be recorded:
                // carry on to that, with the new category chosen.
                if (createdId != null && forEntry) {
                    val amount = state.pendingEntry?.amount?.money?.minorUnits?.toString()
                    onShell(ShellAction.EntryTaken)
                    navigation.navigate(ExpenseEntryRoute(createdId, amount))
                }
            }
        }
    }
    composable<CategoryEditRoute> {
        CategoryManagementRoot(viewModels) {
            val onEdit =
                navigation.currentBackStackEntry?.destination?.hasRoute<CategoryEditRoute>()
            if (onEdit == true) navigation.popBackStack()
        }
    }
}
