package com.software.sello.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.software.sello.R
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.feature.recurrence.CatchUpNoticeViewModel
import com.software.sello.feature.recurrence.CatchUpRecordedRoot
import com.software.sello.feature.recurrence.ScheduleEditorRoot
import com.software.sello.feature.recurrence.ScheduleTab
import com.software.sello.feature.recurrence.SchedulesRoot
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.month.monthNames

/** The schedules, their editor and what catch-up recorded. */
internal fun NavGraphBuilder.recurrenceDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit,
    noticeState: State<CorrectionNotice?>,
    onUndo: () -> Unit
) {
    composable<SchedulesRoute> {
        val state by shellState
        val notice by noticeState
        ShellScreen(
            state,
            monthNames(),
            onShell,
            navigation = null,
            onBack = { navigation.popBackStack() },
            // Always the current month: the title has no month to pick (SELLO-122).
            titleOnly = stringResource(R.string.schedules_title),
            pastMonthRibbon = false,
            entryDock = false,
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            SchedulesRoot(
                viewModels,
                padding,
                onOpen = { id ->
                    navigation.navigate(ScheduleRoute(id)) { launchSingleTop = true }
                },
                onAdd = { tab ->
                    if (tab == ScheduleTab.Incomes) {
                        navigation.navigate(IncomeEntryRoute(repeat = true))
                    } else {
                        navigation.navigate(ExpenseEntryRoute(repeat = true))
                    }
                }
            )
        }
    }
    composable<CatchUpRoute> {
        val state by shellState
        val notice by noticeState
        val model: CatchUpNoticeViewModel = viewModel(factory = viewModels)
        ShellScreen(
            state,
            monthNames(),
            onShell,
            navigation = null,
            onBack = { navigation.popBackStack() },
            screenTitle = stringResource(R.string.catch_up_title),
            pastMonthRibbon = false,
            // Listo dismisses the notice; Back keeps it.
            dock = SelloDock(
                label = stringResource(R.string.catch_up_done_description),
                actionLabel = stringResource(R.string.catch_up_done),
                onClick = model::done,
                icon = SelloIcon.Check
            ),
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            CatchUpRecordedRoot(
                viewModels,
                padding,
                onEdit = { id, income ->
                    navigation.navigate(
                        if (income) IncomeEditRoute(id) else ExpenseEditRoute(id)
                    )
                },
                onClosed = {
                    val onList =
                        navigation.currentBackStackEntry?.destination?.hasRoute<CatchUpRoute>()
                    if (onList == true) navigation.popBackStack()
                }
            )
        }
    }
    composable<ScheduleRoute> {
        ScheduleEditorRoot(viewModels) {
            val onEditor =
                navigation.currentBackStackEntry?.destination?.hasRoute<ScheduleRoute>()
            if (onEditor == true) navigation.popBackStack()
        }
    }
}
