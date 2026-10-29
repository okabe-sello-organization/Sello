package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.software.sello.R
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.presentation.corrections.CorrectionAnnouncementsViewModel
import com.software.sello.presentation.corrections.CorrectionsViewModel

/**
 * The root of the app's interface: it owns navigation and the frame's lifecycle
 * effects, and hands [ShellScreen] plain state and actions.
 */
@Composable
fun SelloAppRoot(viewModels: ViewModelProvider.Factory) {
    val shell: ShellViewModel = viewModel(factory = viewModels)
    val shellState = shell.state.collectAsStateWithLifecycle()
    val state by shellState
    // Stop and start, not pause and resume: a dialog over the app is not time away.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { shell.onAction(ShellAction.EnteredBackground) }
    val corrections: CorrectionsViewModel = viewModel(factory = viewModels)
    val noticeState = corrections.notice.collectAsStateWithLifecycle()
    val announcements: CorrectionAnnouncementsViewModel = viewModel(factory = viewModels)
    val announcementsState = announcements.state.collectAsStateWithLifecycle()
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
        entry?.destination?.hasRoute<InversionesRoute>() == true -> ShellTab.Inversiones
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
            SelloDestination(ShellTab.Metas.id, stringResource(R.string.tab_metas), SelloIcon.Flag),
            SelloDestination(
                ShellTab.Inversiones.id,
                stringResource(R.string.tab_inversiones),
                SelloIcon.AccountBalance
            )
        ),
        selectedTab.id
    ) { id ->
        if (id != selectedTab.id) {
            val route: Any = when (id) {
                ShellTab.Resumen.id -> ResumenRoute
                ShellTab.Metas.id -> MetasRoute
                ShellTab.Inversiones.id -> InversionesRoute
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
        reciboDestinations(
            navigation,
            viewModels,
            shellState,
            shell::onAction,
            tabs,
            noticeState,
            corrections::undo,
            corrections::lands,
            announcementsState,
            announcements
        )
        resumenDestinations(
            navigation,
            viewModels,
            shellState,
            shell::onAction,
            tabs,
            noticeState,
            corrections::undo,
            announcementsState,
            announcements
        )
        goalDestinations(
            navigation,
            viewModels,
            shellState,
            shell::onAction,
            tabs,
            noticeState,
            corrections::undo,
            corrections::lands
        )
        investmentDestinations(
            navigation,
            viewModels,
            shellState,
            shell::onAction,
            tabs,
            noticeState,
            corrections::undo
        )
        settingsDestinations(navigation, viewModels, shellState, shell::onAction)
        backupDestinations(navigation, viewModels, shellState, shell::onAction)
        resetDestinations(navigation, viewModels, shellState, shell::onAction)
        recurrenceDestinations(
            navigation,
            viewModels,
            shellState,
            shell::onAction,
            noticeState,
            corrections::undo
        )
        incomeDestinations(
            navigation,
            viewModels,
            shellState,
            shell::onAction,
            noticeState,
            corrections::undo,
            announcementsState,
            announcements
        )
        categoryDestinations(navigation, viewModels, shellState, shell::onAction)
        expenseDestinations(navigation, viewModels, announcementsState, announcements)
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
