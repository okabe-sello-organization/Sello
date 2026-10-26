package com.software.sello.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.InkSwatches
import com.software.sello.designsystem.component.OptionSlip
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SettingRow
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.ThemePreference
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.investments.label
import com.software.sello.presentation.investments.newInvestmentLabel

/**
 * [tusDatos] is the backup section, given by navigation: it belongs to Respaldo's
 * feature and saves and restores through Respaldo's sessions.
 */
@Composable
fun SettingsRoot(
    viewModels: ViewModelProvider.Factory,
    contentPadding: PaddingValues,
    onOpenSchedules: () -> Unit,
    onOpenRate: () -> Unit,
    tusDatos: @Composable () -> Unit
) {
    val model: SettingsViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onAction = model::onAction,
        contentPadding = contentPadding,
        onOpenSchedules = onOpenSchedules,
        onOpenRate = onOpenRate,
        tusDatos = tusDatos
    )
}

/** Which choice is open over Configuración, kept across recreation. */
private enum class SettingsPicker { Order, Direction, NewInvestmentCurrency, InvestmentOrder }

/** The currencies a new investment may start in, in the order offered. */
private val newInvestmentCurrencies = listOf(Currency.COP, Currency.USD)

/**
 * Configuración: Tus datos and its backup card first, then Lo que se repite, Apariencia,
 * Recibo and Inversiones. Every control is a real setting, never a placeholder.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    contentPadding: PaddingValues,
    onOpenSchedules: () -> Unit,
    onOpenRate: () -> Unit,
    modifier: Modifier = Modifier,
    tusDatos: @Composable () -> Unit
) {
    var picking by rememberSaveable { mutableStateOf<SettingsPicker?>(null) }
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .testTag(SETTINGS_TAG)
    ) {
        if (state.loading) {
            SlipSkeleton(Modifier.testTag(SETTINGS_LOADING_TAG))
            return@Column
        }
        if (state.failure != null) {
            ErrorSlip(
                message = stringResource(R.string.settings_preferences_failed),
                onRetry = { onAction(SettingsAction.Retry) },
                modifier = Modifier.testTag(SETTINGS_ERROR_TAG)
            )
        }
        Box(Modifier.testTag(SETTINGS_DATA_TAG)) { tusDatos() }
        RecurringControls(state, onOpenSchedules)
        AppearanceControls(state, onAction)
        CategoryOrderingControls(state) { picking = it }
        InvestmentControls(state, onOpenRate) { picking = it }
        Text(
            text = stringResource(R.string.settings_footer),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = SelloTheme.spacing.sm)
        )
    }
    when (picking) {
        SettingsPicker.Order -> OptionSlip(
            title = stringResource(R.string.settings_order),
            options = CategoryOrder.entries.map { stringResource(it.label()) },
            selectedIndex = CategoryOrder.entries.indexOf(state.categoryOrdering.order),
            onSelect = { index ->
                picking = null
                onAction(SettingsAction.OrderChosen(CategoryOrder.entries[index]))
            },
            onDismiss = { picking = null },
            modifier = Modifier.testTag(SETTINGS_ORDER_TAG)
        )

        SettingsPicker.Direction -> OptionSlip(
            title = stringResource(R.string.settings_direction),
            options = CategoryOrderDirection.entries.map { stringResource(it.label()) },
            selectedIndex = CategoryOrderDirection.entries.indexOf(
                state.categoryOrdering.direction
            ),
            onSelect = { index ->
                picking = null
                onAction(SettingsAction.DirectionChosen(CategoryOrderDirection.entries[index]))
            },
            onDismiss = { picking = null },
            modifier = Modifier.testTag(SETTINGS_DIRECTION_TAG)
        )

        SettingsPicker.NewInvestmentCurrency -> OptionSlip(
            title = stringResource(R.string.settings_investment_currency),
            options = newInvestmentCurrencies.map { stringResource(it.newInvestmentLabel()) },
            selectedIndex = newInvestmentCurrencies.indexOf(state.investments.newCurrency),
            onSelect = { index ->
                picking = null
                onAction(SettingsAction.NewInvestmentCurrencyChosen(newInvestmentCurrencies[index]))
            },
            onDismiss = { picking = null },
            modifier = Modifier.testTag(SETTINGS_INVESTMENT_CURRENCY_TAG)
        )

        SettingsPicker.InvestmentOrder -> OptionSlip(
            title = stringResource(R.string.settings_investment_order),
            options = InvestmentOrder.entries.map { stringResource(it.label()) },
            selectedIndex = InvestmentOrder.entries.indexOf(state.investments.order),
            onSelect = { index ->
                picking = null
                onAction(SettingsAction.InvestmentOrderChosen(InvestmentOrder.entries[index]))
            },
            onDismiss = { picking = null },
            modifier = Modifier.testTag(SETTINGS_INVESTMENT_ORDER_TAG)
        )

        null -> Unit
    }
}

@Composable
private fun RecurringControls(state: SettingsState, onOpenSchedules: () -> Unit) {
    val count = when (val read = state.schedules) {
        ScheduleCountLine.Loading -> ""

        ScheduleCountLine.Unreadable -> stringResource(R.string.settings_schedules_unreadable)

        is ScheduleCountLine.Read -> stringResource(
            R.string.settings_schedules_count,
            pluralStringResource(R.plurals.settings_payments, read.payments, read.payments),
            pluralStringResource(R.plurals.settings_incomes, read.incomes, read.incomes)
        )
    }
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_RECURRING_TAG)) {
        SectionTitle(R.string.settings_recurring)
        SettingRow(
            title = stringResource(R.string.schedules_title),
            icon = SelloIcon.Autorenew,
            value = count,
            onClick = onOpenSchedules,
            divider = false,
            modifier = Modifier.testTag(SETTINGS_SCHEDULES_TAG)
        )
    }
}

@Composable
private fun AppearanceControls(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_APPEARANCE_TAG)) {
        SectionTitle(R.string.settings_appearance)
        SettingRow(
            title = stringResource(R.string.settings_theme),
            icon = SelloIcon.Contrast,
            divider = false
        )
        SegmentedSwitch(
            options = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark)
            ),
            selectedIndex = ThemePreference.entries.indexOf(state.appearance.theme),
            onSelect = { index ->
                onAction(SettingsAction.ThemeChosen(ThemePreference.entries[index]))
            },
            enabled = !state.busy,
            modifier = Modifier.testTag(SETTINGS_THEME_TAG)
        )
        SettingRow(
            title = stringResource(R.string.settings_ink),
            icon = SelloIcon.InkPen,
            caption = stringResource(R.string.settings_ink_caption),
            divider = false,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
        InkSwatches(
            inks = InkPreference.entries.map { ink ->
                ink.asSelloInk() to stringResource(
                    if (ink == InkPreference.Cobalto) {
                        R.string.settings_ink_cobalto
                    } else {
                        R.string.settings_ink_violeta
                    }
                )
            },
            selected = state.appearance.ink.asSelloInk(),
            onSelect = { ink ->
                val chosen = InkPreference.entries.first { it.asSelloInk() == ink }
                onAction(SettingsAction.InkChosen(chosen))
            },
            enabled = !state.busy,
            modifier = Modifier.padding(start = 26.dp).testTag(SETTINGS_INK_TAG)
        )
    }
}

@Composable
private fun CategoryOrderingControls(state: SettingsState, onPick: (SettingsPicker) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_ORDERING_TAG)) {
        SectionTitle(R.string.settings_recibo)
        SettingRow(
            title = stringResource(R.string.settings_order),
            icon = SelloIcon.Sort,
            caption = stringResource(R.string.settings_order_caption),
            value = stringResource(state.categoryOrdering.order.label()),
            onClick = { onPick(SettingsPicker.Order) },
            divider = false,
            enabled = !state.busy,
            modifier = Modifier.testTag(SETTINGS_ORDER_ROW_TAG)
        )
        SettingRow(
            title = stringResource(R.string.settings_direction),
            icon = SelloIcon.SwapVert,
            value = stringResource(state.categoryOrdering.direction.label()),
            onClick = { onPick(SettingsPicker.Direction) },
            enabled = !state.busy,
            modifier = Modifier.testTag(SETTINGS_DIRECTION_ROW_TAG)
        )
    }
}

@Composable
private fun InvestmentControls(
    state: SettingsState,
    onOpenRate: () -> Unit,
    onPick: (SettingsPicker) -> Unit
) {
    val rate = state.rate
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_INVESTMENTS_TAG)) {
        SectionTitle(R.string.settings_investments)
        SettingRow(
            title = stringResource(R.string.settings_investment_currency),
            icon = SelloIcon.Payments,
            value = stringResource(state.investments.newCurrency.newInvestmentLabel()),
            onClick = { onPick(SettingsPicker.NewInvestmentCurrency) },
            divider = false,
            enabled = !state.busy,
            modifier = Modifier.testTag(SETTINGS_INVESTMENT_CURRENCY_ROW_TAG)
        )
        SettingRow(
            title = stringResource(R.string.settings_investment_order),
            icon = SelloIcon.Sort,
            value = stringResource(state.investments.order.label()),
            onClick = { onPick(SettingsPicker.InvestmentOrder) },
            enabled = !state.busy,
            modifier = Modifier.testTag(SETTINGS_INVESTMENT_ORDER_ROW_TAG)
        )
        SettingRow(
            title = stringResource(R.string.usd_rate_title),
            icon = SelloIcon.CurrencyExchange,
            caption = when (rate) {
                RateLine.Loading -> null

                RateLine.Unreadable -> stringResource(R.string.settings_rate_unreadable)

                RateLine.None -> stringResource(R.string.settings_rate_none)

                is RateLine.Read -> stringResource(
                    R.string.settings_rate_date,
                    dateLabels().shortDay(rate.date, rate.date)
                )
            },
            value = (rate as? RateLine.Read)?.rate,
            onClick = onOpenRate,
            modifier = Modifier.testTag(SETTINGS_RATE_TAG)
        )
    }
}

@Composable
private fun SectionTitle(resource: Int) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = SelloTheme.spacing.sm).semantics { heading() }
    )
}

private fun InkPreference.asSelloInk() = when (this) {
    InkPreference.Cobalto -> SelloInk.Cobalto
    InkPreference.Violeta -> SelloInk.Violeta
}

private fun CategoryOrder.label() = when (this) {
    CategoryOrder.MostUsed -> R.string.settings_order_most_used
    CategoryOrder.Alphabetical -> R.string.settings_order_alphabetical
    CategoryOrder.AmountSpent -> R.string.settings_order_amount_spent
    CategoryOrder.Limit -> R.string.settings_order_limit
    CategoryOrder.LatestExpenseAdded -> R.string.settings_order_latest_expense_added
}

private fun CategoryOrderDirection.label() = when (this) {
    CategoryOrderDirection.Descending -> R.string.settings_direction_descending
    CategoryOrderDirection.Ascending -> R.string.settings_direction_ascending
}

const val SETTINGS_TAG = "settings"
const val SETTINGS_LOADING_TAG = "settings:loading"
const val SETTINGS_ERROR_TAG = "settings:error"
const val SETTINGS_APPEARANCE_TAG = "settings:appearance"
const val SETTINGS_THEME_TAG = "settings:theme"
const val SETTINGS_INK_TAG = "settings:ink"
const val SETTINGS_ORDERING_TAG = "settings:ordering"

/** The order's option slip, open after tapping [SETTINGS_ORDER_ROW_TAG]. */
const val SETTINGS_ORDER_TAG = "settings:order"
const val SETTINGS_ORDER_ROW_TAG = "settings:order:row"

/** The direction's option slip, open after tapping [SETTINGS_DIRECTION_ROW_TAG]. */
const val SETTINGS_DIRECTION_TAG = "settings:direction"
const val SETTINGS_DIRECTION_ROW_TAG = "settings:direction:row"
const val SETTINGS_DATA_TAG = "settings:data"

const val SETTINGS_RECURRING_TAG = "settings:recurring"
const val SETTINGS_SCHEDULES_TAG = "settings:schedules"
const val SETTINGS_INVESTMENTS_TAG = "settings:investments"
const val SETTINGS_RATE_TAG = "settings:rate"

/** Moneda al crear's option slip, open after tapping its row. */
const val SETTINGS_INVESTMENT_CURRENCY_TAG = "settings:investment:currency"
const val SETTINGS_INVESTMENT_CURRENCY_ROW_TAG = "settings:investment:currency:row"

/** Orden de la lista's option slip, open after tapping its row. */
const val SETTINGS_INVESTMENT_ORDER_TAG = "settings:investment:order"
const val SETTINGS_INVESTMENT_ORDER_ROW_TAG = "settings:investment:order:row"
