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
import com.software.sello.feature.investments.InversionesAction
import com.software.sello.feature.investments.InversionesScreen
import com.software.sello.feature.investments.InversionesState
import com.software.sello.feature.investments.InversionesViewModel
import com.software.sello.feature.investments.InvestmentDetailAction
import com.software.sello.feature.investments.InvestmentDetailScreen
import com.software.sello.feature.investments.InvestmentDetailState
import com.software.sello.feature.investments.InvestmentDetailViewModel
import com.software.sello.feature.investments.UsdRateAction
import com.software.sello.feature.investments.UsdRateScreen
import com.software.sello.feature.investments.UsdRateViewModel
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames

/**
 * The Inversiones tab: titled "Inversiones", never a month (v2 page). The **+** and the
 * empty state's dock add an investment ([onAdd]); otherwise the dock is **Anotar
 * movimiento** in ink ([onMove]), distinct from Anotar. Rows open an investment
 * ([onOpen]); the total's rate line opens Dólar ([onOpenRate]).
 */
@Composable
internal fun InversionesDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    tabs: SelloNavigation,
    onOpenSettings: () -> Unit,
    onOpen: ((String) -> Unit)? = null,
    onOpenRate: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    notice: (@Composable () -> Unit)? = null
) {
    val model: InversionesViewModel = viewModel(factory = viewModels)
    val inversiones by model.state.collectAsStateWithLifecycle()
    val ready = inversiones as? InversionesState.Ready
    ShellScreen(
        state = state,
        names = monthNames(),
        onAction = onShell,
        navigation = tabs,
        titleOnly = stringResource(R.string.inversiones_title),
        notice = notice,
        actions = {
            if (onAdd != null) {
                IconButton(onClick = onAdd, modifier = Modifier.testTag(INVERSIONES_ADD_TAG)) {
                    Icon(SelloIcon.Add.painter(), stringResource(R.string.inversiones_add))
                }
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag(SETTINGS_ACTION_TAG)) {
                Icon(SelloIcon.Settings.painter(), stringResource(R.string.settings_open))
            }
        },
        entryDock = false,
        dock = when {
            ready == null -> null

            ready.empty -> onAdd?.let {
                investmentDock(stringResource(R.string.inversiones_dock_add), it)
            }

            else -> onMove?.let {
                investmentDock(stringResource(R.string.inversiones_dock_move), it)
            }
        }
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

/**
 * One investment, titled with its name, never a month. The pencil edits it ([onEdit]);
 * the dock and **Anotar el valor de hoy** open the movement form with it chosen
 * ([onMove], with the kind); a movement opens its correction ([onEditMovement]). A
 * deleted investment leaves through [onBack].
 */
@Composable
internal fun InvestmentDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMove: (kind: String?) -> Unit,
    onEditMovement: (String) -> Unit,
    notice: (@Composable () -> Unit)? = null
) {
    val model: InvestmentDetailViewModel = viewModel(factory = viewModels)
    val detail by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(detail) { if (detail == InvestmentDetailState.Gone) onBack() }
    val ready = detail as? InvestmentDetailState.Ready
    ShellScreen(
        state = state,
        names = monthNames(),
        onAction = onShell,
        navigation = null,
        onBack = onBack,
        titleOnly = ready?.name ?: stringResource(R.string.inversiones_title),
        notice = notice,
        actions = {
            if (ready != null) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag(INVESTMENT_EDIT_TAG)) {
                    Icon(SelloIcon.EditNote.painter(), stringResource(R.string.investment_edit))
                }
            }
        },
        entryDock = false,
        dock = ready?.let {
            investmentDock(stringResource(R.string.inversiones_dock_move)) { onMove(null) }
        }
    ) { padding ->
        InvestmentDetailScreen(
            state = detail,
            onAction = { action ->
                when (action) {
                    InvestmentDetailAction.Edit -> onEdit()
                    InvestmentDetailAction.RecordValue -> onMove(KIND_VALUATION)
                    is InvestmentDetailAction.OpenMovement -> onEditMovement(action.id)
                    else -> Unit
                }
                model.onAction(action)
            },
            dates = dateLabels(),
            padding = padding
        )
    }
    BackHandler(enabled = !state.monthPickerOpen) { onBack() }
}

/** Inversiones' own action: in ink, so it is never taken for Anotar (brand) or Metas (green). */
private fun investmentDock(label: String, onClick: () -> Unit) =
    SelloDock(label, label, onClick, tone = ButtonTone.Ink, icon = SelloIcon.Add)

const val INVERSIONES_ADD_TAG = "inversiones.add"
const val INVESTMENT_EDIT_TAG = "investment.edit"

/** The movement form's kind for Valor de hoy, as its route carries it. */
const val KIND_VALUATION = "valuation"

/** Dólar, pushed from Configuración or the overview's rate line; no month and no dock. */
@Composable
internal fun UsdRateDestination(
    state: ShellState,
    viewModels: ViewModelProvider.Factory,
    onShell: (ShellAction) -> Unit,
    onBack: () -> Unit,
    onChange: () -> Unit
) {
    val model: UsdRateViewModel = viewModel(factory = viewModels)
    val rate by model.state.collectAsStateWithLifecycle()
    ShellScreen(
        state,
        monthNames(),
        onShell,
        navigation = null,
        onBack = onBack,
        screenTitle = stringResource(R.string.usd_rate_title),
        pastMonthRibbon = false,
        entryDock = false
    ) { padding ->
        UsdRateScreen(
            state = rate,
            onAction = { action ->
                if (action == UsdRateAction.Change) onChange()
                model.onAction(action)
            },
            dates = dateLabels(),
            padding = padding
        )
    }
    BackHandler(enabled = !state.monthPickerOpen) { onBack() }
}
