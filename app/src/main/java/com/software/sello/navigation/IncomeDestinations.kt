package com.software.sello.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.software.sello.R
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.feature.income.IncomeFormRoot
import com.software.sello.feature.income.IncomeHistoryScreen
import com.software.sello.feature.income.IncomeHistoryViewModel
import com.software.sello.presentation.corrections.CorrectionAnnouncementState
import com.software.sello.presentation.corrections.CorrectionAnnouncementsViewModel
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.month.monthNames

/** Income history, recording an income and correcting one. */
internal fun NavGraphBuilder.incomeDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit,
    noticeState: State<CorrectionNotice?>,
    onUndo: () -> Unit,
    announcementsState: State<CorrectionAnnouncementState>,
    announcements: CorrectionAnnouncementsViewModel
) {
    composable<IncomeHistoryRoute> {
        val state by shellState
        val notice by noticeState
        val model: IncomeHistoryViewModel = viewModel(factory = viewModels)
        val history by model.state.collectAsStateWithLifecycle()
        val today by model.today.collectAsStateWithLifecycle()
        val split by model.split.collectAsStateWithLifecycle()
        val plan by model.plan.collectAsStateWithLifecycle()
        val assign by model.assign.collectAsStateWithLifecycle()
        ShellScreen(
            state,
            monthNames(),
            onShell,
            navigation = null,
            onBack = { navigation.popBackStack() },
            screenTitle = stringResource(R.string.income_history_title),
            titleLabel = incomeHistoryTitle(state, monthNames()),
            // Here the dock records income, as in v2.
            dock = SelloDock(
                label = stringResource(R.string.dock_income_label),
                actionLabel = stringResource(R.string.income_save),
                onClick = { navigation.navigate(IncomeEntryRoute()) }
            ),
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            IncomeHistoryScreen(
                history,
                split,
                plan,
                assign,
                today,
                model::onAction,
                { navigation.navigate(IncomeEntryRoute()) },
                {
                    navigation.navigate(CategoryEditorRoute(limit = it)) { launchSingleTop = true }
                },
                { navigation.navigate(IncomeEditRoute(it)) },
                padding
            )
        }
    }
    composable<IncomeEntryRoute> { entry ->
        val announcementState by announcementsState
        EntryDestination(
            viewModels,
            startsWithIncome = true,
            result = entry.savedStateHandle,
            onCreateCategory = { navigation.navigate(CategoryEditorRoute()) },
            announcements = announcementState,
            onDismissPastExpense = if (announcementState.failure == null) {
                announcements::dismissPastExpense
            } else {
                announcements::retry
            },
            announcementFailed = announcementState.failure != null,
            onSeveral = { several ->
                navigation.navigate(several.route()) {
                    popUpTo<IncomeEntryRoute> { inclusive = true }
                }
            }
        ) { navigation.popBackStack() }
    }
    composable<IncomeEditRoute> {
        IncomeFormRoot(viewModels, onClosed = { navigation.popBackStack() })
    }
}

/** "Ingresos de octubre", with the year when it is not the current one's. */
@Composable
internal fun incomeHistoryTitle(state: ShellState, names: MonthNames): String {
    val month = state.selectedMonth
    val named = if (month.year == state.currentMonth.year) {
        names.inSentence(month)
    } else {
        stringResource(R.string.month_ribbon_month_of_year, names.inSentence(month), month.year)
    }
    return stringResource(R.string.income_history_title_month, named)
}
