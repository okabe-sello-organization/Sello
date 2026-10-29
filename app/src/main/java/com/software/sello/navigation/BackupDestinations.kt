package com.software.sello.navigation

import androidx.activity.compose.BackHandler
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
import com.software.sello.feature.backup.BackupRoot
import com.software.sello.feature.backup.RestoreAction
import com.software.sello.feature.backup.RestoreViewModel
import com.software.sello.presentation.month.monthNames

/** Respaldo. */
internal fun NavGraphBuilder.backupDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit
) {
    composable<BackupRoute> {
        val state by shellState
        val restore: RestoreViewModel = viewModel(factory = viewModels)
        ShellScreen(
            state = state,
            names = monthNames(),
            onAction = onShell,
            navigation = null,
            // Once the history is or may be replaced, the arrow restarts as Back does:
            // the screens behind it still show the history from before.
            onBack = {
                if (restore.state.value.replaced) {
                    restore.onAction(RestoreAction.Leave)
                } else {
                    navigation.popBackStack()
                }
            },
            screenTitle = stringResource(R.string.backup_title),
            pastMonthRibbon = false,
            entryDock = false
        ) { padding ->
            BackupRoot(viewModels, padding)
        }
        BackHandler(enabled = !state.monthPickerOpen) { navigation.popBackStack() }
    }
}
