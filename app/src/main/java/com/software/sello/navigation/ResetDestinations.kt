package com.software.sello.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.software.sello.R
import com.software.sello.feature.reset.ResetRoot
import com.software.sello.feature.reset.ResetViewModel
import com.software.sello.feature.reset.back
import com.software.sello.presentation.month.monthNames

/** Borrar todo. */
internal fun NavGraphBuilder.resetDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit
) {
    composable<ResetRoute> {
        val state by shellState
        val reset: ResetViewModel = viewModel(factory = viewModels)
        ShellScreen(
            state = state,
            names = monthNames(),
            onAction = onShell,
            navigation = null,
            onBack = { reset.back { navigation.popBackStack() } },
            screenTitle = stringResource(R.string.reset_title),
            pastMonthRibbon = false,
            entryDock = false
        ) { padding ->
            // Back is the screen's own: once data is erased it restarts instead.
            ResetRoot(viewModels, padding) { navigation.popBackStack() }
        }
    }
}
