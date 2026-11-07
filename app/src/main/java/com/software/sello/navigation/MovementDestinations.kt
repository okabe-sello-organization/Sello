package com.software.sello.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.software.sello.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.feature.movements.DayByDayScreen
import com.software.sello.feature.movements.DayByDayViewModel
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.month.monthNames

/**
 * Día a día, opened from Recibo's and Resumen's magnifier and Resumen's stub, always on the
 * shared month. A row, of the month or of a search, opens today's editor; its correction
 * and undo come back as a notice, over the same list or results.
 */
internal fun NavGraphBuilder.movementDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit,
    noticeState: State<CorrectionNotice?>,
    onUndo: () -> Unit
) {
    composable<DayByDayRoute> {
        val state by shellState
        val notice by noticeState
        val model: DayByDayViewModel = viewModel(factory = viewModels)
        val days by model.state.collectAsStateWithLifecycle()
        val query by model.query.collectAsStateWithLifecycle()
        val found by model.found.collectAsStateWithLifecycle()
        ShellScreen(
            state,
            monthNames(),
            onShell,
            onBack = { navigation.popBackStack() },
            screenTitle = stringResource(R.string.day_by_day_title),
            showMonthAsSubtitle = true,
            // As in the approved mock: no dock; an empty month offers Anotar gasto itself.
            entryDock = false,
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            DayByDayScreen(
                days,
                query,
                found,
                model::onAction,
                onExpense = {
                    navigation.navigate(ExpenseEditRoute(it)) { launchSingleTop = true }
                },
                onIncome = { navigation.navigate(IncomeEditRoute(it)) { launchSingleTop = true } },
                onRecord = { onShell(ShellAction.StartEntry) },
                padding = padding
            )
        }
    }
}

/** Opens Día a día once, however often the magnifier or the stub is tapped. */
internal fun NavHostController.openDayByDay() = navigate(DayByDayRoute) { launchSingleTop = true }

/** The magnifier on Recibo's and Resumen's bars: it opens Día a día on the shared month. */
@Composable
internal fun DayByDayBarAction(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(DAY_BY_DAY_ACTION_TAG)) {
        Icon(
            SelloIcon.Search.painter(),
            contentDescription = stringResource(R.string.day_by_day_open)
        )
    }
}

const val DAY_BY_DAY_ACTION_TAG = "navigation:dayByDay"
