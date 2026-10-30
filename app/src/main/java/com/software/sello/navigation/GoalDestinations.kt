package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.software.sello.R
import com.software.sello.designsystem.component.BandChip
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.feature.goals.CompletionState
import com.software.sello.feature.goals.GoalDetailAction
import com.software.sello.feature.goals.GoalDetailScreen
import com.software.sello.feature.goals.GoalDetailState
import com.software.sello.feature.goals.GoalDetailViewModel
import com.software.sello.feature.goals.GoalEditorResult
import com.software.sello.feature.goals.GoalEditorRoot
import com.software.sello.feature.goals.GoalMovementRoot
import com.software.sello.feature.goals.GoalPin
import com.software.sello.feature.goals.LogradaMoment
import com.software.sello.feature.goals.METAS_CREATE_TAG
import com.software.sello.feature.goals.MetasAction
import com.software.sello.feature.goals.MetasScreen
import com.software.sello.feature.goals.MetasState
import com.software.sello.feature.goals.MetasViewModel
import com.software.sello.feature.reset.back
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.corrections.celebration
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames

/**
 * The Metas tab: titled "Metas", never a month. Its own actions are offered only once
 * their screens exist: [onCreate] for **+ Nueva meta** on the band and the empty state's
 * **Crear una meta**, [onContribute] for **Aportar a una meta**. Recording an expense is
 * not offered here, so without them the tab has no dock.
 */
@Composable
internal fun MetasDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    tabs: SelloNavigation,
    onOpenGoal: (String) -> Unit,
    onCreate: (() -> Unit)? = null,
    onContribute: (() -> Unit)? = null,
    notice: (@Composable () -> Unit)? = null
) {
    val model: MetasViewModel = viewModel(factory = viewModels)
    val metas by model.state.collectAsStateWithLifecycle()
    val names = monthNames()
    val ready = metas as? MetasState.Ready
    ShellScreen(
        state = state,
        names = names,
        onAction = onShell,
        navigation = tabs,
        titleOnly = stringResource(R.string.metas_title),
        notice = notice,
        // + Nueva meta beside the title once there are goals; with none, the dock creates one.
        titleChip = onCreate?.takeIf { ready != null && !ready.empty }?.let { create ->
            { compact ->
                BandChip(
                    stringResource(R.string.metas_create),
                    create,
                    Modifier.testTag(METAS_CREATE_TAG),
                    compact = compact
                )
            }
        },
        entryDock = false,
        dock = when {
            ready == null -> null

            ready.empty -> onCreate?.let {
                goalDock(stringResource(R.string.metas_dock_create), it)
            }

            else -> onContribute?.let {
                goalDock(stringResource(R.string.metas_dock_contribute), it)
            }
        }
    ) { padding ->
        MetasScreen(
            state = metas,
            onAction = { action ->
                if (action is MetasAction.Open) onOpenGoal(action.id)
                model.onAction(action)
            },
            dates = dateLabels(),
            padding = padding
        )
    }
    LogradaMoment(
        ready?.completion ?: CompletionState.Idle,
        onDone = { model.onAction(MetasAction.CelebrationDone) },
        onView = {
            val plate = (ready?.completion as? CompletionState.Celebrating)?.plate
            model.onAction(MetasAction.CelebrationDone)
            plate?.goalId?.takeIf { it.isNotEmpty() }?.let(onOpenGoal)
        }
    )
    BackHandler(enabled = !state.monthPickerOpen) { tabs.onSelect(ShellTab.Recibo.id) }
}

/**
 * One goal, titled with its name and never a month. The star on the band chooses it for
 * Recibo. [onEdit], [onContribute] and [onWithdraw] are offered only once their screens
 * exist, and never on a completed goal, which can only be deleted. A deleted goal
 * leaves through [onBack].
 */
@Composable
internal fun GoalDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onContribute: (() -> Unit)? = null,
    onWithdraw: (() -> Unit)? = null,
    onEditMovement: ((String) -> Unit)? = null,
    notice: (@Composable () -> Unit)? = null,
    celebration: GoalCelebration? = null
) {
    val model: GoalDetailViewModel = viewModel(factory = viewModels)
    val detail by model.state.collectAsStateWithLifecycle()
    val deleted by model.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val ready = detail as? GoalDetailState.Ready
    val active = ready != null && ready.goal.completedOn == null
    ShellScreen(
        state = state,
        names = monthNames(),
        onAction = onShell,
        navigation = null,
        onBack = onBack,
        titleOnly = ready?.goal?.name ?: stringResource(R.string.goal_title_fallback),
        notice = notice,
        band = if (active) {
            { GoalPin(ready!!, model::onAction) }
        } else {
            null
        },
        actions = {
            if (active && onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag(GOAL_EDIT_TAG)) {
                    Icon(SelloIcon.EditNote.painter(), stringResource(R.string.goal_edit))
                }
            }
        },
        entryDock = false,
        dock = onContribute?.takeIf { active }?.let {
            goalDock(stringResource(R.string.goal_dock_contribute), it)
        }
    ) { padding ->
        GoalDetailScreen(
            state = detail,
            onAction = model::onAction,
            dates = dateLabels(),
            months = monthNames(),
            padding = padding,
            onWithdraw = onWithdraw,
            onEditMovement = onEditMovement.takeIf { active },
            landing = celebration?.earned.orEmpty().toSet(),
            relighting = celebration?.relit.orEmpty().toSet()
        )
    }
    LogradaMoment(ready?.completion ?: CompletionState.Idle, {
        model.onAction(GoalDetailAction.CelebrationDone)
    })
}

/** Metas' own action: green with the flag, so it cannot be taken for Anotar. */
private fun goalDock(label: String, onClick: () -> Unit) =
    SelloDock(label, label, onClick, tone = ButtonTone.Gain, icon = SelloIcon.Flag)

const val GOAL_EDIT_TAG = "goal.edit"

/** Metas, one goal, its editor and its movements. */
internal fun NavGraphBuilder.goalDestinations(
    navigation: NavHostController,
    viewModels: ViewModelProvider.Factory,
    shellState: State<ShellState>,
    onShell: (ShellAction) -> Unit,
    tabs: SelloNavigation,
    noticeState: State<CorrectionNotice?>,
    onUndo: () -> Unit,
    lands: (GoalCelebration) -> Boolean
) {
    composable<MetasRoute> {
        val state by shellState
        val notice by noticeState
        MetasDestination(
            state = state,
            viewModels = viewModels,
            onShell = onShell,
            tabs = tabs,
            onOpenGoal = { id ->
                navigation.navigate(GoalRoute(id)) { launchSingleTop = true }
            },
            onCreate = { navigation.navigate(GoalEditorRoute()) { launchSingleTop = true } },
            onContribute = {
                navigation.navigate(GoalMovementRoute()) { launchSingleTop = true }
            },
            notice = notice?.let {
                { StampNoticeBar(it, onUndo, lands) }
            }
        )
    }
    composable<GoalRoute> { entry ->
        val state by shellState
        val notice by noticeState
        val id = entry.toRoute<GoalRoute>().goalId
        GoalDestination(
            state = state,
            viewModels = viewModels,
            onShell = onShell,
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
            notice = notice?.let { { CorrectionNoticeBar(it, onUndo) } },
            celebration = remember(notice) {
                notice?.celebration?.takeIf { it.goalId == id && lands(it) }
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
}
