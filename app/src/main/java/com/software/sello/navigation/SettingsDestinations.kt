package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.software.sello.R
import com.software.sello.feature.backup.TusDatosRoot
import com.software.sello.feature.settings.SettingsRoot
import com.software.sello.presentation.month.monthNames

/** Configuración. */
internal fun NavGraphBuilder.settingsDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit
) {
    composable<SettingsRoute> {
        val state by shellState
        ShellScreen(
            state = state,
            names = monthNames(),
            onAction = onShell,
            navigation = null,
            onBack = { navigation.popBackStack() },
            screenTitle = stringResource(R.string.settings_title),
            // Configuración shows nothing of the month.
            pastMonthRibbon = false
        ) { padding ->
            SettingsRoot(
                viewModels,
                padding,
                onOpenSchedules = {
                    navigation.navigate(SchedulesRoute()) { launchSingleTop = true }
                },
                onOpenRate = { navigation.navigate(UsdRateRoute) { launchSingleTop = true } }
            ) {
                TusDatosRoot(
                    viewModels,
                    onOpenBackup = {
                        navigation.navigate(BackupRoute) { launchSingleTop = true }
                    },
                    onOpenReset = { navigation.navigate(ResetRoute) { launchSingleTop = true } }
                )
            }
        }
        BackHandler(enabled = !state.monthPickerOpen) { navigation.popBackStack() }
    }
}
