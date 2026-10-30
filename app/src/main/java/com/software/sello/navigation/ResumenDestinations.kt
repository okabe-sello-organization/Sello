package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.software.sello.R
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.feature.recibo.CategoryDetailAction
import com.software.sello.feature.recibo.CategoryDetailScreen
import com.software.sello.feature.recibo.CategoryDetailState
import com.software.sello.feature.recibo.CategoryDetailViewModel
import com.software.sello.feature.resumen.ResumenRoot
import com.software.sello.presentation.corrections.CorrectionAnnouncementState
import com.software.sello.presentation.corrections.CorrectionAnnouncementsViewModel
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames

/** Resumen and the detail of one of its categories. */
internal fun NavGraphBuilder.resumenDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit,
    tabs: SelloNavigation,
    noticeState: State<CorrectionNotice?>,
    onUndo: () -> Unit,
    announcementsState: State<CorrectionAnnouncementState>,
    announcements: CorrectionAnnouncementsViewModel
) {
    composable<ResumenRoute> {
        val state by shellState
        val notice by noticeState
        ShellScreen(
            state,
            monthNames(),
            onShell,
            navigation = tabs,
            // Resumen reads the month: no Anotar here (owner, 2026-10-26).
            entryDock = false,
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            ResumenRoot(viewModels, {
                navigation.navigate(IncomeHistoryRoute)
            }, { id -> navigation.navigate(ResumenCategoryRoute(id)) }, {
                navigation.navigate(ExpenseEntryRoute())
            }, padding, { navigation.navigate(SchedulesRoute()) }) { id ->
                navigation.navigate(CategoryEditRoute(id, state.selectedMonth.toString()))
            }
        }
        BackHandler(enabled = !state.monthPickerOpen) { tabs.onSelect("recibo") }
    }
    composable<ResumenCategoryRoute> { entry ->
        val state by shellState
        val notice by noticeState
        val announcementState by announcementsState
        val id = entry.toRoute<ResumenCategoryRoute>().categoryId
        val detail: CategoryDetailViewModel = viewModel(factory = viewModels)
        val detailState by detail.state.collectAsStateWithLifecycle()
        LaunchedEffect(id) { detail.onAction(CategoryDetailAction.Show(id)) }
        ShellScreen(
            state,
            monthNames(),
            onShell,
            onBack = { navigation.popBackStack() },
            screenTitle = (detailState as? CategoryDetailState.Ready)?.category?.name
                ?: stringResource(R.string.category_detail_title_fallback),
            showMonthAsSubtitle = true,
            dock = categoryDock(archived(detailOf(id, detailState))) {
                navigation.navigate(ExpenseEntryRoute(id))
            },
            actions = {
                CategoryEditAction(detailState is CategoryDetailState.Ready) {
                    navigation.navigate(CategoryEditRoute(id, state.selectedMonth.toString()))
                }
            },
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            CategoryDetailScreen(
                state = detailOf(id, detailState), months = monthNames(), dates = dateLabels(),
                showName = false, onAction = detail::onAction,
                onRecordHere = { navigation.navigate(ExpenseEntryRoute(id)) },
                onShowAllExpenses = { navigation.navigate(CategoryExpensesRoute(id)) },
                onRecordSeveral = { navigation.navigate(ExpenseBulkRoute(categoryId = id)) },
                onSetMonthLimit = { categoryId, month ->
                    navigation.navigate(CategoryEditRoute(categoryId, month.toString()))
                },
                contentPadding = padding,
                onEditExpense = { navigation.navigate(ExpenseEditRoute(it)) },
                onEditCategory = {
                    navigation.navigate(CategoryEditRoute(id, state.selectedMonth.toString()))
                },
                showPastLimitAnnouncement = !announcementState.loading &&
                    !announcementState.preference.pastLimitSeen,
                onDismissPastLimitAnnouncement = if (announcementState.failure == null) {
                    announcements::dismissPastLimit
                } else {
                    announcements::retry
                },
                announcementBusy = announcementState.busy,
                announcementFailed = announcementState.failure != null
            )
        }
    }
}
