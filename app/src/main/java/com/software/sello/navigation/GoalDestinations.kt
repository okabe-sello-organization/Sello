package com.software.sello.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
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
import com.software.sello.feature.goals.GoalPin
import com.software.sello.feature.goals.LogradaMoment
import com.software.sello.feature.goals.MetasAction
import com.software.sello.feature.goals.MetasBandTotal
import com.software.sello.feature.goals.MetasScreen
import com.software.sello.feature.goals.MetasState
import com.software.sello.feature.goals.MetasViewModel
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames

/**
 * The Metas tab: titled "Metas", never a month. Its own actions are offered only once
 * their screens exist: [onCreate] for the top **+** and the empty state's **Crear una
 * meta**, [onContribute] for **Aportar a una meta**. Recording an expense is not offered
 * here, so without them the tab has no dock.
 */
@Composable
internal fun MetasDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    tabs: SelloNavigation,
    onOpenSettings: () -> Unit,
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
        band = ready?.band?.let { band -> { MetasBandTotal(band, names) } },
        actions = {
            if (onCreate != null) {
                IconButton(onClick = onCreate, modifier = Modifier.testTag(METAS_CREATE_TAG)) {
                    Icon(SelloIcon.Add.painter(), stringResource(R.string.metas_create))
                }
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(SETTINGS_ACTION_TAG)) {
                Icon(SelloIcon.Settings.painter(), stringResource(R.string.settings_open))
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

const val METAS_CREATE_TAG = "metas.create"
const val GOAL_EDIT_TAG = "goal.edit"
