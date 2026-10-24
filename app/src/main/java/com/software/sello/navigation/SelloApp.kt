package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.software.sello.R
import com.software.sello.designsystem.component.Blotter
import com.software.sello.designsystem.component.MonthBar
import com.software.sello.designsystem.component.MonthSwitcher
import com.software.sello.designsystem.component.PastMonthRibbon
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.TitleBar
import com.software.sello.designsystem.component.selloWindowLayout
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.backup.BackupRoot
import com.software.sello.feature.backup.RestoreAction
import com.software.sello.feature.backup.RestoreViewModel
import com.software.sello.feature.category.CategoryEditorRoot
import com.software.sello.feature.category.CategoryManagementRoot
import com.software.sello.feature.expense.ExpenseEntryRoot
import com.software.sello.feature.goals.CelebrationTicket
import com.software.sello.feature.goals.GoalEditorResult
import com.software.sello.feature.goals.GoalEditorRoot
import com.software.sello.feature.goals.GoalMovementRoot
import com.software.sello.feature.goals.celebrationLine
import com.software.sello.feature.income.IncomeFormRoot
import com.software.sello.feature.income.IncomeHistoryScreen
import com.software.sello.feature.income.IncomeHistoryViewModel
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
import com.software.sello.feature.reset.ResetRoot
import com.software.sello.feature.reset.ResetViewModel
import com.software.sello.feature.reset.back
import com.software.sello.feature.resumen.ResumenRoot
import com.software.sello.feature.settings.SettingsRoot
import com.software.sello.presentation.corrections.CorrectionAnnouncementState
import com.software.sello.presentation.corrections.CorrectionAnnouncementsViewModel
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.CorrectionsViewModel
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.corrections.celebration
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.month.monthNames
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The root of the app's interface: it owns navigation and the frame's lifecycle
 * effects, and hands [ShellScreen] plain state and actions.
 */
@Composable
fun SelloAppRoot(viewModels: ViewModelProvider.Factory) {
    val shell: ShellViewModel = viewModel(factory = viewModels)
    val state by shell.state.collectAsStateWithLifecycle()
    // Stop and start, not pause and resume: a dialog over the app is not time away.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { shell.onAction(ShellAction.EnteredBackground) }
    val corrections: CorrectionsViewModel = viewModel(factory = viewModels)
    val notice by corrections.notice.collectAsStateWithLifecycle()
    val announcements: CorrectionAnnouncementsViewModel = viewModel(factory = viewModels)
    val announcementState by announcements.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        shell.onAction(ShellAction.ReturnedToForeground)
        corrections.returned()
    }

    val navigation = rememberNavController()
    val entry by navigation.currentBackStackEntryAsState()
    val onDetail = entry != null && navigation.previousBackStackEntry != null
    val selectedTab = when {
        entry?.destination?.hasRoute<ResumenRoute>() == true -> ShellTab.Resumen
        entry?.destination?.hasRoute<MetasRoute>() == true -> ShellTab.Metas
        else -> ShellTab.Recibo
    }
    val tabs = SelloNavigation(
        listOf(
            SelloDestination(
                ShellTab.Recibo.id,
                stringResource(R.string.tab_recibo),
                SelloIcon.ReceiptLong
            ),
            SelloDestination(
                ShellTab.Resumen.id,
                stringResource(R.string.tab_resumen),
                SelloIcon.Insights
            ),
            SelloDestination(ShellTab.Metas.id, stringResource(R.string.tab_metas), SelloIcon.Flag)
        ),
        selectedTab.id
    ) { id ->
        if (id != selectedTab.id) {
            val route: Any = when (id) {
                ShellTab.Resumen.id -> ResumenRoute
                ShellTab.Metas.id -> MetasRoute
                else -> ReciboRoute
            }
            navigation.navigate(route) {
                popUpTo(navigation.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    NavHost(navController = navigation, startDestination = ReciboRoute) {
        composable<ReciboRoute> {
            ReciboDestination(
                state = state,
                viewModels = viewModels,
                onShell = shell::onAction,
                notice = notice,
                onUndo = corrections::undo,
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
                onEditCategory = { id ->
                    navigation.navigate(CategoryEditRoute(id, state.selectedMonth.toString())) {
                        launchSingleTop = true
                    }
                }
            ) { navigation.navigate(CategoryEditorRoute()) }
        }
        composable<ResumenRoute> {
            ShellScreen(
                state,
                monthNames(),
                shell::onAction,
                navigation = tabs,
                notice = notice?.let { { CorrectionNoticeBar(it, corrections::undo) } }
            ) { padding ->
                ResumenRoot(viewModels, {
                    navigation.navigate(IncomeHistoryRoute)
                }, { id -> navigation.navigate(ResumenCategoryRoute(id)) }, {
                    navigation.navigate(ExpenseEntryRoute())
                }, padding) { id ->
                    navigation.navigate(CategoryEditRoute(id, state.selectedMonth.toString()))
                }
            }
            BackHandler(enabled = !state.monthPickerOpen) { tabs.onSelect("recibo") }
        }
        composable<MetasRoute> {
            MetasDestination(
                state = state,
                viewModels = viewModels,
                onShell = shell::onAction,
                tabs = tabs,
                onOpenSettings = { navigation.navigate(SettingsRoute) { launchSingleTop = true } },
                onOpenGoal = { id ->
                    navigation.navigate(GoalRoute(id)) { launchSingleTop = true }
                },
                onCreate = { navigation.navigate(GoalEditorRoute()) { launchSingleTop = true } },
                onContribute = {
                    navigation.navigate(GoalMovementRoute()) { launchSingleTop = true }
                },
                notice = notice?.let {
                    { MetasNoticeBar(it, corrections::undo, corrections::lands) }
                }
            )
        }
        composable<GoalRoute> { entry ->
            val id = entry.toRoute<GoalRoute>().goalId
            GoalDestination(
                state = state,
                viewModels = viewModels,
                onShell = shell::onAction,
                onBack = {
                    val onGoal =
                        navigation.currentBackStackEntry?.destination?.hasRoute<GoalRoute>()
                    if (onGoal == true) navigation.popBackStack()
                },
                onEdit = { navigation.navigate(GoalEditorRoute(id)) { launchSingleTop = true } },
                onContribute = {
                    navigation.navigate(GoalMovementRoute(id)) { launchSingleTop = true }
                },
                onWithdraw = {
                    navigation.navigate(GoalMovementRoute(id, withdrawal = true)) {
                        launchSingleTop = true
                    }
                },
                onEditMovement = { movement ->
                    navigation.navigate(GoalMovementRoute(id, movementId = movement)) {
                        launchSingleTop = true
                    }
                },
                notice = notice?.let { { CorrectionNoticeBar(it, corrections::undo) } },
                celebration = remember(notice) {
                    notice?.celebration?.takeIf { it.goalId == id && corrections.lands(it) }
                }
            )
        }
        composable<GoalEditorRoute> {
            GoalEditorRoot(viewModels) { result ->
                val onEditor =
                    navigation.currentBackStackEntry?.destination?.hasRoute<GoalEditorRoute>()
                if (onEditor == true) {
                    // A deleted goal has no page to go back to: Metas shows the rest.
                    if (result == GoalEditorResult.Deleted) {
                        navigation.popBackStack<MetasRoute>(inclusive = false)
                    } else {
                        navigation.popBackStack()
                    }
                }
            }
        }
        composable<GoalMovementRoute> {
            GoalMovementRoot(viewModels) {
                val onForm =
                    navigation.currentBackStackEntry?.destination?.hasRoute<GoalMovementRoute>()
                if (onForm == true) navigation.popBackStack()
            }
        }
        composable<ResumenCategoryRoute> { entry ->
            val id = entry.toRoute<ResumenCategoryRoute>().categoryId
            val detail: CategoryDetailViewModel = viewModel(factory = viewModels)
            val detailState by detail.state.collectAsStateWithLifecycle()
            LaunchedEffect(id) { detail.onAction(CategoryDetailAction.Show(id)) }
            ShellScreen(
                state,
                monthNames(),
                shell::onAction,
                onBack = { navigation.popBackStack() },
                screenTitle = (detailState as? CategoryDetailState.Ready)?.category?.name
                    ?: stringResource(R.string.category_detail_title_fallback),
                showMonthAsSubtitle = true,
                dock = SelloDock(
                    label = stringResource(R.string.dock_entry_label),
                    actionLabel = stringResource(R.string.dock_entry_action),
                    onClick = { navigation.navigate(ExpenseEntryRoute(id)) }
                ),
                actions = {
                    CategoryEditAction(detailState is CategoryDetailState.Ready) {
                        navigation.navigate(CategoryEditRoute(id, state.selectedMonth.toString()))
                    }
                },
                notice = notice?.let { { CorrectionNoticeBar(it, corrections::undo) } }
            ) { padding ->
                CategoryDetailScreen(
                    state = detailOf(id, detailState), months = monthNames(), dates = dateLabels(),
                    showName = false, onAction = detail::onAction,
                    onRecordHere = { navigation.navigate(ExpenseEntryRoute(id)) },
                    onShowAllExpenses = { navigation.navigate(CategoryExpensesRoute(id)) },
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
        composable<CategoryExpensesRoute> { entry ->
            val id = entry.toRoute<CategoryExpensesRoute>().categoryId
            val model: CategoryExpensesViewModel = viewModel(factory = viewModels)
            val expenses by model.state.collectAsStateWithLifecycle()
            val query by model.query.collectAsStateWithLifecycle()
            val order by model.order.collectAsStateWithLifecycle()
            LaunchedEffect(id) { model.onAction(CategoryExpensesAction.Show(id)) }
            ShellScreen(
                state,
                monthNames(),
                shell::onAction,
                onBack = { navigation.popBackStack() },
                screenTitle = (expenses as? CategoryExpensesState.Ready)?.categoryName
                    ?: stringResource(R.string.category_expenses_title),
                showMonthAsSubtitle = true,
                dock = SelloDock(
                    label = stringResource(R.string.dock_entry_label),
                    actionLabel = stringResource(R.string.dock_entry_action),
                    onClick = { navigation.navigate(ExpenseEntryRoute(id)) }
                ),
                notice = notice?.let { { CorrectionNoticeBar(it, corrections::undo) } }
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
        composable<SettingsRoute> {
            ShellScreen(
                state = state,
                names = monthNames(),
                onAction = shell::onAction,
                navigation = null,
                onBack = { navigation.popBackStack() },
                screenTitle = stringResource(R.string.settings_title),
                // Configuración shows nothing of the month.
                pastMonthRibbon = false
            ) { padding ->
                SettingsRoot(
                    viewModels,
                    padding,
                    onOpenBackup = { navigation.navigate(BackupRoute) { launchSingleTop = true } },
                    onOpenReset = { navigation.navigate(ResetRoute) { launchSingleTop = true } }
                )
            }
            BackHandler(enabled = !state.monthPickerOpen) { navigation.popBackStack() }
        }
        composable<BackupRoute> {
            val restore: RestoreViewModel = viewModel(factory = viewModels)
            ShellScreen(
                state = state,
                names = monthNames(),
                onAction = shell::onAction,
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
        composable<ResetRoute> {
            val reset: ResetViewModel = viewModel(factory = viewModels)
            ShellScreen(
                state = state,
                names = monthNames(),
                onAction = shell::onAction,
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
        composable<IncomeHistoryRoute> {
            val model: IncomeHistoryViewModel = viewModel(factory = viewModels)
            val history by model.state.collectAsStateWithLifecycle()
            val today by model.today.collectAsStateWithLifecycle()
            val split by model.split.collectAsStateWithLifecycle()
            ShellScreen(
                state,
                monthNames(),
                shell::onAction,
                navigation = null,
                onBack = { navigation.popBackStack() },
                screenTitle = stringResource(R.string.income_history_title),
                titleLabel = incomeHistoryTitle(state, monthNames()),
                // Here the dock records income, as in v2.
                dock = SelloDock(
                    label = stringResource(R.string.dock_income_label),
                    actionLabel = stringResource(R.string.income_save),
                    onClick = { navigation.navigate(IncomeEntryRoute) }
                ),
                notice = notice?.let { { CorrectionNoticeBar(it, corrections::undo) } }
            ) { padding ->
                IncomeHistoryScreen(
                    history,
                    split,
                    today,
                    model::onAction,
                    { navigation.navigate(IncomeEntryRoute) },
                    { navigation.navigate(IncomeEditRoute(it)) },
                    padding
                )
            }
        }
        composable<IncomeEntryRoute> { entry ->
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
                announcementFailed = announcementState.failure != null
            ) { navigation.popBackStack() }
        }
        composable<IncomeEditRoute> {
            IncomeFormRoot(viewModels, onClosed = { navigation.popBackStack() })
        }
        composable<CategoryEditorRoute> { entry ->
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
                        shell.onAction(ShellAction.EntryTaken)
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
        composable<ExpenseEntryRoute> { entry ->
            EntryDestination(
                viewModels,
                startsWithIncome = false,
                result = entry.savedStateHandle,
                onCreateCategory = { navigation.navigate(CategoryEditorRoute()) },
                announcements = announcementState,
                onDismissPastExpense = if (announcementState.failure == null) {
                    announcements::dismissPastExpense
                } else {
                    announcements::retry
                },
                announcementFailed = announcementState.failure != null
            ) {
                val onEntry =
                    navigation.currentBackStackEntry?.destination?.hasRoute<ExpenseEntryRoute>()
                if (onEntry == true) navigation.popBackStack()
            }
        }
        // The same form: the route's identifier makes it correct that expense.
        composable<ExpenseEditRoute> {
            ExpenseEntryRoot(
                viewModels = viewModels,
                announcements = announcementState,
                onDismissPastExpense = if (announcementState.failure == null) {
                    announcements::dismissPastExpense
                } else {
                    announcements::retry
                },
                announcementFailed = announcementState.failure != null
            ) {
                val onEdit =
                    navigation.currentBackStackEntry?.destination?.hasRoute<ExpenseEditRoute>()
                if (onEdit == true) navigation.popBackStack()
            }
        }
    }
    // An entry cannot be recorded without a category, so the form to create one opens
    // with that explanation instead of an entry form that could not be completed.
    LaunchedEffect(state.entryNeedsCategory) {
        if (state.entryNeedsCategory) {
            shell.onAction(ShellAction.CategoryPrerequisiteShown)
            navigation.navigate(CategoryEditorRoute(forEntry = true)) { launchSingleTop = true }
        }
    }
    LaunchedEffect(state.entryReady) {
        if (state.entryReady) {
            val request = state.pendingEntry
            shell.onAction(ShellAction.EntryTaken)
            navigation.navigate(
                ExpenseEntryRoute(
                    categoryId = request?.categoryId?.value,
                    amount = request?.amount?.money?.minorUnits?.toString()
                )
            ) { launchSingleTop = true }
        }
    }
    // Declared after the content so that it is asked before the navigation host: an
    // open panel closes first, whatever screen is underneath.
    val target = backTarget(state.monthPickerOpen, onDetail, ShellTab.Recibo)
    BackHandler(enabled = target == BackTarget.ClosePanel) {
        shell.onAction(ShellAction.CloseMonthPicker)
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
    announcements: CorrectionAnnouncementState,
    onDismissPastLimit: () -> Unit,
    announcementFailed: Boolean,
    tabs: SelloNavigation,
    onOpenSettings: () -> Unit,
    onEditExpense: (String) -> Unit,
    onEditCategory: (String) -> Unit,
    onShowAllExpenses: (String) -> Unit,
    onCreateCategory: () -> Unit
) {
    val recibo: ReciboViewModel = viewModel(factory = viewModels)
    val detail: CategoryDetailViewModel = viewModel(factory = viewModels)
    val reciboState by recibo.state.collectAsStateWithLifecycle()
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
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } },
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
                ShellDetail(title, actions = {
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
                    }
                    recibo.onAction(action)
                },
                contentPadding = padding,
                selectedCategoryId = shownId.takeIf { twoPane }
            )
        }
        // Beside Recibo the detail is not a screen to leave: Back does what it does on Recibo.
        val onDetail = state.detailCategoryId != null && !twoPane
        val target = backTarget(state.monthPickerOpen, onDetail, ShellTab.Recibo)
        BackHandler(enabled = target == BackTarget.LeaveDetail) {
            onShell(ShellAction.CloseCategory)
        }
    }
}

/**
 * Whether a detail fits beside what it is the detail of: a window laid out in two
 * columns that is also wide enough for both to be read. A window that is short and
 * narrow, such as half a phone's screen, shows one at a time like any phone.
 */
fun showsDetailBeside(width: Dp, height: Dp): Boolean =
    selloWindowLayout(width, height).twoPane && width >= SideBySideWidth

private val SideBySideWidth = 600.dp

/** The detail of what the content lists: its [title] and how to draw it. */
class ShellDetail(
    val title: String,
    val actions: @Composable RowScope.() -> Unit = {},
    val content: @Composable (PaddingValues) -> Unit
)

@Composable
private fun CategoryEditAction(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.testTag(CATEGORY_DETAIL_EDIT_TAG)) {
        Icon(SelloIcon.EditNote.painter(), stringResource(R.string.category_detail_edit))
    }
}

/**
 * "Estás viendo septiembre" and "Volver a octubre". The year is added to the month
 * viewed only when it is not the current one's: "septiembre de 2025".
 */
@Composable
internal fun PastMonthNotice(
    state: ShellState,
    names: MonthNames,
    onAction: (ShellAction) -> Unit
) {
    val month = state.selectedMonth
    val viewed = if (month.year == state.currentMonth.year) {
        names.inSentence(month)
    } else {
        stringResource(R.string.month_ribbon_month_of_year, names.inSentence(month), month.year)
    }
    val sentence = stringResource(R.string.month_ribbon_viewing, viewed)
    val start = sentence.lastIndexOf(viewed)
    PastMonthRibbon(
        message = buildAnnotatedString {
            append(sentence)
            if (start >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + viewed.length)
            }
        },
        returnLabel = stringResource(
            R.string.month_ribbon_return,
            names.inSentence(state.currentMonth)
        ),
        onReturn = { onAction(ShellAction.ReturnToCurrentMonth) },
        modifier = Modifier.testTag(PAST_MONTH_RIBBON_TAG)
    )
}

/**
 * The frame: the month in the title, the content, and the month picker. With a
 * [detail], a window with room for two columns shows it beside the content; any other
 * window shows it instead of the content once it has been opened, under its own title
 * and with a way back. The picker is
 * a bottom sheet, or a side panel beside the content in a window wide enough for one.
 * There is no tab bar while there is one tab. The dock asks to record an expense, unless [entryDock] is false.
 *
 * A screen not tied to a month, as Metas and a goal are, passes [titleOnly]: its whole
 * title, with no month bar, month switcher or past-month ribbon. Its [band], if any,
 * sits on the band under that title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    state: ShellState,
    names: MonthNames,
    onAction: (ShellAction) -> Unit,
    modifier: Modifier = Modifier,
    detail: ShellDetail? = null,
    notice: (@Composable () -> Unit)? = null,
    navigation: SelloNavigation? = null,
    onBack: (() -> Unit)? = null,
    screenTitle: String? = null,
    showMonthAsSubtitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    pastMonthRibbon: Boolean = true,
    titleLabel: String? = null,
    dock: SelloDock? = null,
    entryDock: Boolean = true,
    titleOnly: String? = null,
    band: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val title = names.title(state.selectedMonth)
    // Over a past month, every screen that shows it says so and offers the way back.
    val ribbon: (@Composable () -> Unit)? = when {
        titleOnly != null -> band

        pastMonthRibbon && state.selectedMonth != state.currentMonth -> {
            { PastMonthNotice(state, names, onAction) }
        }

        else -> null
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layout = selloWindowLayout(maxWidth, maxHeight)
        val sidePanel = layout.sidePanelWidth != null
        val beside = showsDetailBeside(maxWidth, maxHeight)
        val openedId = state.detailCategoryId.takeIf { detail != null && !beside }
        if (openedId != null && detail != null) {
            SelloScaffold(
                title = detail.title,
                subtitle = title,
                actions = detail.actions,
                blotter = Blotter.Short,
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(ShellAction.CloseCategory) },
                        modifier = Modifier.testTag(DETAIL_BACK_TAG)
                    ) {
                        Icon(
                            SelloIcon.ArrowBack.painter(),
                            stringResource(R.string.category_detail_back)
                        )
                    }
                },
                // From a category's own screen, an expense starts in that category.
                dock = SelloDock(
                    label = stringResource(R.string.dock_entry_label),
                    actionLabel = stringResource(R.string.dock_entry_action),
                    onClick = { onAction(ShellAction.StartEntryIn(openedId)) }
                ),
                notice = notice,
                ribbon = ribbon,
                content = detail.content
            )
        } else {
            // A tab gets the v2 month bar; a pushed screen keeps its titled bar and back arrow.
            val tab = screenTitle == null && onBack == null
            val bar = monthBarLabel(state, names)
            SelloScaffold(
                title = titleOnly ?: screenTitle ?: title,
                subtitle = if (showMonthAsSubtitle && titleOnly == null) title else null,
                topBar = if (tab && titleOnly != null) {
                    { TitleBar(titleOnly, actions = actions) }
                } else if (tab) {
                    {
                        MonthBar(
                            month = bar.month,
                            year = bar.year,
                            onClick = { onAction(ShellAction.OpenMonthPicker) },
                            description = bar.spoken,
                            buttonModifier = Modifier.testTag(MONTH_SWITCHER_TAG),
                            actions = actions
                        )
                    }
                } else {
                    null
                },
                ribbon = ribbon,
                navigation = navigation,
                navigationIcon = onBack?.let { back ->
                    {
                        IconButton(onClick = back) {
                            Icon(
                                SelloIcon.ArrowBack.painter(),
                                stringResource(R.string.navigation_back)
                            )
                        }
                    }
                },
                actions = actions,
                titleContent = if (showMonthAsSubtitle || titleOnly != null) {
                    null
                } else {
                    {
                        val label = when {
                            titleLabel != null -> titleLabel
                            screenTitle == null -> title
                            else -> stringResource(R.string.screen_title_month, screenTitle, title)
                        }
                        MonthSwitcher(
                            label = label,
                            onClick = { onAction(ShellAction.OpenMonthPicker) },
                            modifier = Modifier
                                .testTag(MONTH_SWITCHER_TAG)
                                .semantics { contentDescription = bar.spoken }
                        )
                    }
                },
                blotter = Blotter.Short,
                // A screen can offer its own entry, as income history does, or none
                // when its own action is the only one, as Respaldo's is.
                dock = dock ?: if (entryDock) {
                    SelloDock(
                        label = stringResource(R.string.dock_entry_label),
                        actionLabel = stringResource(R.string.dock_entry_action),
                        onClick = { onAction(ShellAction.StartEntry) }
                    )
                } else {
                    null
                },
                notice = notice,
                secondaryPane = if (state.monthPickerOpen && sidePanel) {
                    { padding ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(
                                rememberScrollState()
                            ).padding(padding)
                        ) {
                            Slip(pinked = false) { MonthPicker(state, names, onAction) }
                        }
                    }
                } else {
                    detail?.content.takeIf { beside }
                },
                content = content
            )
        }
        if (state.monthPickerOpen && !sidePanel) {
            ModalBottomSheet(
                onDismissRequest = { onAction(ShellAction.CloseMonthPicker) },
                // Opened whole: a half-open sheet would hide the later months.
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = SelloTheme.colors.paper,
                contentColor = SelloTheme.colors.ink
            ) {
                // Scrolls, so a large font or a short window cannot cut months off.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MonthPicker(state, names, onAction)
                }
            }
        }
    }
}

@Composable
private fun EntryDestination(
    viewModels: ViewModelProvider.Factory,
    startsWithIncome: Boolean,
    onCreateCategory: () -> Unit,
    announcements: CorrectionAnnouncementState,
    onDismissPastExpense: () -> Unit,
    announcementFailed: Boolean,
    result: SavedStateHandle? = null,
    onClosed: () -> Unit
) {
    var income by rememberSaveable { mutableStateOf(startsWithIncome) }
    // What the category editor, opened from Nueva, left for this entry.
    val created by (
        result?.getStateFlow<String?>(CREATED_CATEGORY_RESULT, null) ?: remember {
            MutableStateFlow<String?>(null)
        }
        ).collectAsStateWithLifecycle()
    if (income) {
        IncomeFormRoot(viewModels, onClosed, onExpense = { income = false })
    } else {
        ExpenseEntryRoot(
            viewModels,
            onIncome = { income = true },
            onCreateCategory = onCreateCategory,
            announcements = announcements,
            onDismissPastExpense = onDismissPastExpense,
            announcementFailed = announcementFailed,
            createdCategoryId = created,
            onCreatedCategoryTaken = { result?.set(CREATED_CATEGORY_RESULT, null) },
            onClosed = onClosed
        )
    }
}

const val SETTINGS_ACTION_TAG = "navigation:settings"

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

/** "Gasto anotado: 48.700 en Comida", with as much as could be read back. */
@Composable
private fun expenseRecorded(figure: String?, category: String?): String = when {
    figure == null -> stringResource(R.string.notice_expense_recorded_plain)
    category == null -> stringResource(R.string.notice_expense_recorded_amount, figure)
    else -> stringResource(R.string.notice_expense_recorded, figure, category)
}

/**
 * What is said about the last correction. Only a deletion that can still be undone
 * has something to press; everything else is a statement that goes away by itself.
 */
@Composable
internal fun CorrectionNoticeBar(notice: CorrectionNotice, onUndo: () -> Unit) {
    val tagged = Modifier.testTag(CORRECTION_NOTICE_TAG)
    when (notice) {
        is CorrectionNotice.Deleted -> SelloNotice(
            message = stringResource(
                if (notice.undoFailed) {
                    R.string.correction_undo_failed
                } else {
                    R.string.correction_deleted
                }
            ),
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = stringResource(
                when {
                    notice.undoFailed -> R.string.correction_undo_failed
                    notice.canUndo -> R.string.correction_deleted_spoken
                    else -> R.string.correction_deleted
                }
            )
        )

        CorrectionNotice.Restored ->
            SelloNotice(stringResource(R.string.correction_restored), tagged)

        CorrectionNotice.UndoTooLate ->
            SelloNotice(stringResource(R.string.correction_too_late), tagged)

        CorrectionNotice.UndoBlocked ->
            SelloNotice(stringResource(R.string.correction_blocked), tagged)

        CorrectionNotice.Updated ->
            SelloNotice(stringResource(R.string.correction_updated), tagged)

        is CorrectionNotice.CategorySaved -> SelloNotice(
            message = notice.limitOf?.let {
                stringResource(R.string.category_saved_month, monthNames().inSentence(it))
            } ?: stringResource(R.string.category_saved),
            modifier = tagged
        )

        CorrectionNotice.CategoryArchived ->
            SelloNotice(stringResource(R.string.category_archived_notice), tagged)

        CorrectionNotice.CategoryUnarchived ->
            SelloNotice(stringResource(R.string.category_unarchived_notice), tagged)

        is CorrectionNotice.IncomeDeleted -> SelloNotice(
            message = stringResource(
                if (notice.undoFailed) {
                    R.string.correction_undo_failed
                } else {
                    R.string.income_deleted
                }
            ),
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = stringResource(
                when {
                    notice.undoFailed -> R.string.correction_undo_failed
                    notice.canUndo -> R.string.income_deleted_spoken
                    else -> R.string.income_deleted
                }
            )
        )

        CorrectionNotice.IncomeRestored ->
            SelloNotice(stringResource(R.string.income_restored), tagged)

        CorrectionNotice.IncomeUndoTooLate ->
            SelloNotice(stringResource(R.string.income_too_late), tagged)

        CorrectionNotice.IncomeUndoBlocked ->
            SelloNotice(stringResource(R.string.income_blocked), tagged)

        CorrectionNotice.IncomeUpdated ->
            SelloNotice(stringResource(R.string.income_updated), tagged)

        is CorrectionNotice.Confirmed -> SelloNotice(
            message = notice.message,
            modifier = tagged,
            spoken = notice.spoken,
            confirmation = true
        )

        is CorrectionNotice.ExpenseRecorded -> {
            val category = notice.categoryName?.takeIf { it.isNotBlank() }
            // On screen the figure as it is written; read aloud with its currency.
            SelloNotice(
                message = expenseRecorded(notice.amount?.digits, category),
                modifier = tagged,
                spoken = expenseRecorded(notice.amount?.spoken, category),
                confirmation = true
            )
        }

        is CorrectionNotice.GoalMovementDeleted -> SelloNotice(
            message = stringResource(
                if (notice.undoFailed) {
                    R.string.correction_undo_failed
                } else {
                    R.string.goal_movement_deleted
                }
            ),
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = stringResource(
                when {
                    notice.undoFailed -> R.string.correction_undo_failed
                    notice.canUndo -> R.string.goal_movement_deleted_spoken
                    else -> R.string.goal_movement_deleted
                }
            )
        )

        CorrectionNotice.GoalMovementRestored ->
            SelloNotice(stringResource(R.string.goal_movement_restored), tagged)

        CorrectionNotice.GoalMovementUndoTooLate ->
            SelloNotice(stringResource(R.string.goal_movement_too_late), tagged)

        CorrectionNotice.GoalMovementUndoBlocked ->
            SelloNotice(stringResource(R.string.goal_movement_undo_blocked), tagged)

        CorrectionNotice.GoalMovementUpdated ->
            SelloNotice(stringResource(R.string.goal_movement_updated), tagged)

        is CorrectionNotice.GoalCompleted -> SelloNotice(
            message = if (notice.undoFailed) {
                stringResource(R.string.correction_undo_failed)
            } else {
                stringResource(R.string.goal_completed, notice.goalName)
            },
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = when {
                notice.undoFailed -> stringResource(R.string.correction_undo_failed)
                notice.canUndo -> stringResource(R.string.goal_completed_spoken, notice.goalName)
                else -> stringResource(R.string.goal_completed, notice.goalName)
            },
            confirmation = !notice.undoFailed
        )

        is CorrectionNotice.GoalCompletionUndone ->
            SelloNotice(stringResource(R.string.goal_completion_undone, notice.goalName), tagged)

        CorrectionNotice.GoalCompletionUndoTooLate ->
            SelloNotice(stringResource(R.string.goal_completion_too_late), tagged)

        is CorrectionNotice.GoalMovementRecorded -> {
            val amount = notice.amount
            val (said, plain) = if (notice.withdrawal) {
                R.string.notice_goal_withdrawal to R.string.notice_goal_withdrawal_plain
            } else {
                R.string.notice_goal_contribution to R.string.notice_goal_contribution_plain
            }
            val message = amount?.let { stringResource(said, it.digits) } ?: stringResource(plain)
            val spoken = amount?.let { stringResource(said, it.spoken) } ?: stringResource(plain)
            val celebration = notice.celebration
            val line = celebration?.let { celebrationLine(it) }
            when {
                line != null -> SelloNotice(
                    message = stringResource(R.string.goal_celebration_notice, message, line),
                    modifier = tagged,
                    spoken = stringResource(R.string.goal_celebration_notice, spoken, line),
                    confirmation = true
                )

                else -> SelloNotice(message, tagged, spoken = spoken, confirmation = true)
            }
        }

        is CorrectionNotice.GoalTargetChanged -> {
            val message = stringResource(R.string.notice_goal_target_changed)
            val line = celebrationLine(notice.celebration)
            if (line != null) {
                SelloNotice(
                    message = stringResource(R.string.goal_celebration_notice, message, line),
                    modifier = tagged,
                    confirmation = true
                )
            } else {
                SelloNotice(message, tagged, confirmation = true)
            }
        }

        is CorrectionNotice.IncomeRecorded -> {
            val amount = notice.amount
            SelloNotice(
                message = amount?.let {
                    stringResource(R.string.notice_income_recorded, it.digits)
                } ?: stringResource(R.string.notice_income_recorded_plain),
                modifier = tagged,
                spoken = amount?.let {
                    stringResource(R.string.notice_income_recorded, it.spoken)
                } ?: stringResource(R.string.notice_income_recorded_plain),
                confirmation = true
            )
        }
    }
}

/**
 * The notice on Metas: a stamp a confirmed aporte earned lands on a paper ticket, as the
 * goal's own stamps are not in view there; anything else is said as everywhere.
 */
@Composable
internal fun MetasNoticeBar(
    notice: CorrectionNotice,
    onUndo: () -> Unit,
    lands: (GoalCelebration) -> Boolean
) {
    val celebration = notice.celebration
    if (celebration != null && celebrationLine(celebration) != null) {
        val land = remember(celebration) { lands(celebration) }
        Box(Modifier.testTag(CORRECTION_NOTICE_TAG)) { CelebrationTicket(celebration, land) }
    } else {
        CorrectionNoticeBar(notice, onUndo)
    }
}

/** What the month bar shows and says, made from one month so they cannot disagree. */
@Immutable
internal data class MonthBarLabel(val month: String, val year: String, val spoken: String)

/** "Mes actual" is said only of the month the financial day is in. */
@Composable
internal fun monthBarLabel(state: ShellState, names: MonthNames): MonthBarLabel = MonthBarLabel(
    month = names.name(state.selectedMonth),
    year = state.selectedMonth.year.toString(),
    spoken = stringResource(
        if (state.selectedMonth == state.currentMonth) {
            R.string.month_picker_open_current
        } else {
            R.string.month_picker_open_selected
        },
        names.title(state.selectedMonth)
    )
)

const val MONTH_SWITCHER_TAG = "shell:month-switcher"
const val PAST_MONTH_RIBBON_TAG = "shell:past-month-ribbon"
const val DETAIL_BACK_TAG = "shell:detail-back"
const val CORRECTION_NOTICE_TAG = "shell:correction-notice"
