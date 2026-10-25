package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.feature.investments.InversionesAction
import com.software.sello.feature.investments.InversionesScreen
import com.software.sello.feature.investments.InversionesViewModel
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames

/**
 * The Inversiones tab: titled "Inversiones", never a month (v2 page). Its own actions
 * are offered only once their screens exist: [onOpen] opens an investment, [onOpenRate]
 * the rate. Recording an expense is not offered here, so until Anotar movimiento exists
 * there is no dock. The tab joins the bar with SELLO-110, once investments can be
 * created and recorded: an unfinished tab is never shown (architecture §1).
 */
@Composable
internal fun InversionesDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    tabs: SelloNavigation,
    onOpenSettings: () -> Unit,
    onOpen: ((String) -> Unit)? = null,
    onOpenRate: (() -> Unit)? = null
) {
    val model: InversionesViewModel = viewModel(factory = viewModels)
    val inversiones by model.state.collectAsStateWithLifecycle()
    ShellScreen(
        state = state,
        names = monthNames(),
        onAction = onShell,
        navigation = tabs,
        titleOnly = stringResource(R.string.inversiones_title),
        actions = {
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(SETTINGS_ACTION_TAG)) {
                Icon(SelloIcon.Settings.painter(), stringResource(R.string.settings_open))
            }
        },
        entryDock = false,
        dock = null
    ) { padding ->
        InversionesScreen(
            state = inversiones,
            onAction = { action ->
                if (action is InversionesAction.Open) onOpen?.invoke(action.id)
                model.onAction(action)
            },
            dates = dateLabels(),
            padding = padding,
            onOpenRate = onOpenRate,
            openRows = onOpen != null
        )
    }
    BackHandler(enabled = !state.monthPickerOpen) { tabs.onSelect(ShellTab.Recibo.id) }
}
