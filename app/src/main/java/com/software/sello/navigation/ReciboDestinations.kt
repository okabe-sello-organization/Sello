package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.software.sello.R
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.feature.goals.CompletionState
import com.software.sello.feature.goals.LogradaMoment
import com.software.sello.feature.goals.ReciboGoal
import com.software.sello.feature.goals.ReciboGoalAction
import com.software.sello.feature.goals.ReciboGoalState
import com.software.sello.feature.goals.ReciboGoalViewModel
import com.software.sello.feature.recibo.AdjustBudgetRoot
import com.software.sello.feature.recibo.CATEGORY_DETAIL_EDIT_TAG
import com.software.sello.feature.recibo.CategoryDetailAction
import com.software.sello.feature.recibo.CategoryDetailScreen
import com.software.sello.feature.recibo.CategoryDetailState
import com.software.sello.feature.recibo.CategoryDetailViewModel
import com.software.sello.feature.recibo.CategoryExpensesAction
import com.software.sello.feature.recibo.CategoryExpensesScreen
import com.software.sello.feature.recibo.CategoryExpensesState
import com.software.sello.feature.recibo.CategoryExpensesViewModel
import com.software.sello.feature.recibo.ReciboAction
import com.software.sello.feature.recibo.ReciboScreen
import com.software.sello.feature.recibo.ReciboState
import com.software.sello.feature.recibo.ReciboViewModel
import com.software.sello.feature.recibo.UpcomingPaymentsRoot
import com.software.sello.feature.recurrence.CatchUpNoticeBar
import com.software.sello.feature.recurrence.CatchUpNoticeViewModel
import com.software.sello.presentation.corrections.CorrectionAnnouncementState
import com.software.sello.presentation.corrections.CorrectionAnnouncementsViewModel
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import java.time.YearMonth

/**
 * Recibo, the screens opened from it (every expense of a category, the budget adjustment, the
 * upcoming payments) and the notice they share.
 */
internal fun NavGraphBuilder.reciboDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit,
    tabs: SelloNavigation,
    noticeState: State<CorrectionNotice?>,
    onUndo: () -> Unit,
    lands: (GoalCelebration) -> Boolean,
    announcementsState: State<CorrectionAnnouncementState>,
    announcements: CorrectionAnnouncementsViewModel
) {
    composable<ReciboRoute> {
        val state by shellState
        val notice by noticeState
        val announcementState by announcementsState
        ReciboDestination(
            state = state,
            viewModels = viewModels,
            onShell = onShell,
            notice = notice,
            onUndo = onUndo,
            lands = lands,
            onOpenGoal = { id ->
                navigation.navigate(GoalRoute(id)) { launchSingleTop = true }
            },
            onContributeGoal = { id ->
                navigation.navigate(GoalMovementRoute(id)) { launchSingleTop = true }
            },
            announcements = announcementState,
            onDismissPastLimit = if (announcementState.failure == null) {
                announcements::dismissPastLimit
            } else {
                announcements::retry
            },
            announcementFailed = announcementState.failure != null,
            tabs = tabs,
            onOpenSettings = { navigation.navigate(SettingsRoute) { launchSingleTop = true } },
            onEditExpense = { id ->
                navigation.navigate(ExpenseEditRoute(id)) { launchSingleTop = true }
            },
            // Opened from the month on screen: that is the month whose limit it edits.
            onShowAllExpenses = { id -> navigation.navigate(CategoryExpensesRoute(id)) },
            onRecordSeveral = { id -> navigation.navigate(BulkEntryRoute(categoryId = id)) },
            onOpenRecorded = { navigation.navigate(CatchUpRoute) { launchSingleTop = true } },
            onOpenReserved = {
                navigation.navigate(UpcomingPaymentsRoute) { launchSingleTop = true }
            },
            onAdjustBudget = {
                navigation.navigate(AdjustBudgetRoute(state.selectedMonth.toString())) {
                    launchSingleTop = true
                }
            },
            // Resumen shows what the shared selection says, so pick the month first.
            onOpenClosedMonth = { month ->
                onShell(ShellAction.PickMonth(month))
                tabs.onSelect(ShellTab.Resumen.id)
            },
            onEditCategory = { id ->
                navigation.navigate(CategoryEditRoute(id, state.selectedMonth.toString())) {
                    launchSingleTop = true
                }
            },
            onSetMonthLimit = { id, month ->
                navigation.navigate(CategoryEditRoute(id, month.toString())) {
                    launchSingleTop = true
                }
            }
        ) { navigation.navigate(CategoryEditorRoute()) }
    }
    composable<CategoryExpensesRoute> { entry ->
        val state by shellState
        val notice by noticeState
        val id = entry.toRoute<CategoryExpensesRoute>().categoryId
        val model: CategoryExpensesViewModel = viewModel(factory = viewModels)
        val expenses by model.state.collectAsStateWithLifecycle()
        val query by model.query.collectAsStateWithLifecycle()
        val order by model.order.collectAsStateWithLifecycle()
        LaunchedEffect(id) { model.onAction(CategoryExpensesAction.Show(id)) }
        ShellScreen(
            state,
            monthNames(),
            onShell,
            onBack = { navigation.popBackStack() },
            screenTitle = (expenses as? CategoryExpensesState.Ready)?.categoryName
                ?: stringResource(R.string.category_expenses_title),
            showMonthAsSubtitle = true,
            dock = SelloDock(
                label = stringResource(R.string.dock_entry_label),
                actionLabel = stringResource(R.string.dock_entry_action),
                onClick = { navigation.navigate(ExpenseEntryRoute(id)) }
            ),
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            CategoryExpensesScreen(
                expenses,
                query,
                order,
                model::onAction,
                { navigation.navigate(ExpenseEditRoute(it)) },
                padding
            )
        }
    }
    composable<UpcomingPaymentsRoute> {
        val state by shellState
        val notice by noticeState
        ShellScreen(
            state,
            monthNames(),
            onShell,
            navigation = null,
            onBack = { navigation.popBackStack() },
            screenTitle = stringResource(R.string.upcoming_screen_title),
            pastMonthRibbon = false,
            entryDock = false,
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            UpcomingPaymentsRoot(
                viewModels,
                padding,
                onOpenSchedule = { id ->
                    navigation.navigate(ScheduleRoute(id)) { launchSingleTop = true }
                },
                onAllSchedules = {
                    navigation.navigate(SchedulesRoute()) { launchSingleTop = true }
                }
            )
        }
    }
    composable<AdjustBudgetRoute> { entry ->
        val state by shellState
        val notice by noticeState
        val month = entry.toRoute<AdjustBudgetRoute>().month
        ShellScreen(
            state,
            monthNames(),
            onShell,
            navigation = null,
            onBack = { navigation.popBackStack() },
            screenTitle = stringResource(R.string.adjust_title),
            pastMonthRibbon = false,
            entryDock = false,
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } }
        ) { padding ->
            AdjustBudgetRoot(
                viewModels,
                padding,
                onOpenReserved = {
                    navigation.navigate(UpcomingPaymentsRoute) { launchSingleTop = true }
                },
                // An archived category is unarchived from its editor, for this month.
                onEditCategory = { id ->
                    navigation.navigate(CategoryEditRoute(id, month)) { launchSingleTop = true }
                }
            )
        }
    }
}

/**
 * Recibo and the detail of one of its categories: one screen at a time where there is
 * room for one, side by side where there is room for two. Which category is open is
 * the frame's saved state, so it is the same category after a rotation, a resize or
 * the process being killed. With room for two and none opened, the first one is shown.
 */
@Composable
private fun ReciboDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    notice: CorrectionNotice?,
    onUndo: () -> Unit,
    lands: (GoalCelebration) -> Boolean,
    onOpenGoal: (String) -> Unit,
    onContributeGoal: (String) -> Unit,
    announcements: CorrectionAnnouncementState,
    onDismissPastLimit: () -> Unit,
    announcementFailed: Boolean,
    tabs: SelloNavigation,
    onOpenSettings: () -> Unit,
    onEditExpense: (String) -> Unit,
    onEditCategory: (String) -> Unit,
    onShowAllExpenses: (String) -> Unit,
    onRecordSeveral: (String) -> Unit,
    onOpenRecorded: () -> Unit,
    onOpenReserved: () -> Unit,
    onAdjustBudget: () -> Unit,
    onOpenClosedMonth: (YearMonth) -> Unit,
    onSetMonthLimit: (String, YearMonth) -> Unit,
    onCreateCategory: () -> Unit
) {
    val recibo: ReciboViewModel = viewModel(factory = viewModels)
    val catchUp: CatchUpNoticeViewModel = viewModel(factory = viewModels)
    val catchUpState by catchUp.state.collectAsStateWithLifecycle()
    val detail: CategoryDetailViewModel = viewModel(factory = viewModels)
    val goal: ReciboGoalViewModel = viewModel(factory = viewModels)
    val reciboState by recibo.state.collectAsStateWithLifecycle()
    val goalState by goal.state.collectAsStateWithLifecycle()
    val detailState by detail.state.collectAsStateWithLifecycle()
    val names = monthNames()
    val dates = dateLabels()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = showsDetailBeside(maxWidth, maxHeight)
        val first = (reciboState as? ReciboState.Month)?.categories?.firstOrNull()?.id
        val shownId = state.detailCategoryId ?: first.takeIf { twoPane }
        LaunchedEffect(shownId) { detail.onAction(CategoryDetailAction.Show(shownId)) }
        // The name and the detail of the category chosen, never of the one before it.
        val shownDetail = detailOf(shownId, detailState)
        val title = detailTitle(shownId, detailState, reciboState)
            ?: stringResource(R.string.category_detail_title_fallback)
        ShellScreen(
            state = state,
            names = names,
            onAction = onShell,
            // A correction just made is said first; what catch-up recorded waits under it.
            notice = notice?.let { { StampNoticeBar(it, onUndo, lands) } }
                // It only informs; when its time is up it counts as seen, as Listo does.
                ?: {
                    CatchUpNoticeBar(
                        catchUpState,
                        onTimeout = catchUp::done,
                        onView = onOpenRecorded
                    )
                },
            navigation = tabs,
            actions = {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag(SETTINGS_ACTION_TAG)
                ) {
                    Icon(
                        SelloIcon.Settings.painter(),
                        contentDescription = stringResource(R.string.settings_open)
                    )
                }
            },
            detail = shownId?.let { id ->
                ShellDetail(title, archived = archived(shownDetail), actions = {
                    CategoryEditAction(shownDetail is CategoryDetailState.Ready) {
                        onEditCategory(id)
                    }
                }) { padding ->
                    CategoryDetailScreen(
                        state = shownDetail,
                        months = names,
                        dates = dates,
                        showName = twoPane,
                        onAction = detail::onAction,
                        onRecordHere = { onShell(ShellAction.StartEntryIn(id)) },
                        contentPadding = padding,
                        onEditExpense = onEditExpense,
                        onEditCategory = { onEditCategory(id) },
                        onShowAllExpenses = { onShowAllExpenses(id) },
                        onSetMonthLimit = onSetMonthLimit,
                        onRecordSeveral = { onRecordSeveral(id) },
                        showPastLimitAnnouncement = !announcements.loading &&
                            !announcements.preference.pastLimitSeen,
                        onDismissPastLimitAnnouncement = onDismissPastLimit,
                        announcementBusy = announcements.busy,
                        announcementFailed = announcementFailed
                    )
                }
            }
        ) { padding ->
            ReciboScreen(
                state = reciboState,
                onAction = { action ->
                    when (action) {
                        ReciboAction.CreateCategory -> onCreateCategory()

                        is ReciboAction.OpenCategory -> onShell(ShellAction.OpenCategory(action.id))

                        is ReciboAction.EditCategory -> onEditCategory(action.id)

                        ReciboAction.Retry -> Unit

                        ReciboAction.OpenReserved -> onOpenReserved()

                        ReciboAction.AdjustBudget -> onAdjustBudget()

                        is ReciboAction.OpenInvitation -> onOpenClosedMonth(action.month)

                        is ReciboAction.DismissInvitation -> Unit

                        is ReciboAction.SetMonthLimit ->
                            onSetMonthLimit(action.categoryId, action.month)
                    }
                    recibo.onAction(action)
                },
                contentPadding = padding,
                selectedCategoryId = shownId.takeIf { twoPane },
                goal = { ReciboGoal(goalState, goal::onAction, onOpenGoal, onContributeGoal) }
            )
        }
        val completion = (goalState as? ReciboGoalState.Shown)?.completion ?: CompletionState.Idle
        LogradaMoment(
            completion,
            onDone = { goal.onAction(ReciboGoalAction.CelebrationDone) },
            onView = {
                val plate = (completion as? CompletionState.Celebrating)?.plate
                goal.onAction(ReciboGoalAction.CelebrationDone)
                plate?.goalId?.takeIf { it.isNotEmpty() }?.let(onOpenGoal)
            }
        )
        // Beside Recibo the detail is not a screen to leave: Back does what it does on Recibo.
        val onDetail = state.detailCategoryId != null && !twoPane
        val target = backTarget(state.monthPickerOpen, onDetail, ShellTab.Recibo)
        BackHandler(enabled = target == BackTarget.LeaveDetail) {
            onShell(ShellAction.CloseCategory)
        }
    }
}

internal fun archived(detail: CategoryDetailState): Boolean =
    (detail as? CategoryDetailState.Ready)?.category?.archived == true

@Composable
internal fun CategoryEditAction(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.testTag(CATEGORY_DETAIL_EDIT_TAG)) {
        Icon(SelloIcon.EditNote.painter(), stringResource(R.string.category_detail_edit))
    }
}

const val SETTINGS_ACTION_TAG = "navigation:settings"
