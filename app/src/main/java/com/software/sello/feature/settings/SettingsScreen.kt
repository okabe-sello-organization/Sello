package com.software.sello.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.presentation.date.dateLabels

@Composable
fun SettingsRoot(
    viewModels: ViewModelProvider.Factory,
    contentPadding: PaddingValues,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit,
    onOpenSchedules: () -> Unit
) {
    val model: SettingsViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state,
        model::onAction,
        contentPadding,
        onOpenBackup,
        onOpenReset,
        onOpenSchedules
    )
}

/** Persisted, nonfinancial preferences. Every control is a real setting, never a placeholder. */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    contentPadding: PaddingValues,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit,
    onOpenSchedules: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        AppearanceControls(state, onAction)
        CategoryOrderingControls(state, onAction)
        DataControls(state, onOpenBackup, onOpenReset, onOpenSchedules)
    }
}

@Composable
private fun DataControls(
    state: SettingsState,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit,
    onOpenSchedules: () -> Unit
) {
    val value = when (val backup = state.backup) {
        BackupLine.Loading -> ""

        BackupLine.Unreadable -> stringResource(R.string.settings_backup_unreadable)

        is BackupLine.Read -> backup.lastAt?.let {
            dateLabels().moment(it, backup.zone, stringResource(R.string.backup_moment))
        } ?: stringResource(R.string.settings_backup_never)
    }
    val schedules = when (val count = state.schedules) {
        ScheduleCountLine.Loading -> ""

        ScheduleCountLine.Unreadable -> stringResource(R.string.settings_schedules_unreadable)

        is ScheduleCountLine.Read -> stringResource(
            R.string.settings_schedules_count,
            pluralStringResource(R.plurals.settings_payments, count.payments, count.payments),
            pluralStringResource(R.plurals.settings_incomes, count.incomes, count.incomes)
        )
    }
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_DATA_TAG)) {
        SectionTitle(R.string.settings_data)
        LeaderLine(
            label = stringResource(R.string.schedules_title),
            value = "",
            caption = schedules,
            icon = SelloIcon.Autorenew,
            onClick = onOpenSchedules,
            modifier = Modifier.testTag(SETTINGS_SCHEDULES_TAG)
        )
        LeaderLine(
            label = stringResource(R.string.settings_backup),
            value = value,
            onClick = onOpenBackup,
            modifier = Modifier.testTag(SETTINGS_BACKUP_TAG)
        )
        Text(
            text = stringResource(R.string.settings_backup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft
        )
        LeaderLine(
            label = stringResource(R.string.settings_reset),
            value = "",
            caption = stringResource(R.string.settings_reset_caption),
            onClick = onOpenReset,
            modifier = Modifier.testTag(SETTINGS_RESET_TAG)
        )
    }
}

@Composable
private fun AppearanceControls(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_APPEARANCE_TAG)) {
        SectionTitle(R.string.settings_appearance)
        ControlLabel(R.string.settings_theme)
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
        ControlLabel(R.string.settings_ink)
        ChipFlow(Modifier.testTag(SETTINGS_INK_TAG)) {
            InkPreference.entries.forEach { ink ->
                SelloChip(
                    label = stringResource(
                        if (ink == InkPreference.Cobalto) {
                            R.string.settings_ink_cobalto
                        } else {
                            R.string.settings_ink_violeta
                        }
                    ),
                    selected = state.appearance.ink == ink,
                    onClick = { onAction(SettingsAction.InkChosen(ink)) },
                    enabled = !state.busy
                )
            }
        }
    }
}

@Composable
private fun CategoryOrderingControls(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_ORDERING_TAG)) {
        SectionTitle(R.string.settings_recibo)
        Text(
            text = stringResource(R.string.settings_recibo_body),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft
        )
        ControlLabel(R.string.settings_order)
        ChipFlow(Modifier.testTag(SETTINGS_ORDER_TAG)) {
            CategoryOrder.entries.forEach { order ->
                SelloChip(
                    label = stringResource(order.label()),
                    selected = state.categoryOrdering.order == order,
                    onClick = { onAction(SettingsAction.OrderChosen(order)) },
                    enabled = !state.busy
                )
            }
        }
        ControlLabel(R.string.settings_direction)
        SegmentedSwitch(
            options = listOf(
                stringResource(R.string.settings_direction_descending),
                stringResource(R.string.settings_direction_ascending)
            ),
            selectedIndex = CategoryOrderDirection.entries.indexOf(
                state.categoryOrdering.direction
            ),
            onSelect = { index ->
                onAction(SettingsAction.DirectionChosen(CategoryOrderDirection.entries[index]))
            },
            enabled = !state.busy,
            modifier = Modifier.testTag(SETTINGS_DIRECTION_TAG)
        )
    }
}

@Composable
private fun SectionTitle(resource: Int) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.headlineSmall
    )
}

@Composable
private fun ControlLabel(resource: Int) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.labelLarge,
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(top = SelloTheme.spacing.lg)
    )
}

private fun CategoryOrder.label() = when (this) {
    CategoryOrder.MostUsed -> R.string.settings_order_most_used
    CategoryOrder.Alphabetical -> R.string.settings_order_alphabetical
    CategoryOrder.AmountSpent -> R.string.settings_order_amount_spent
    CategoryOrder.Limit -> R.string.settings_order_limit
    CategoryOrder.LatestExpenseAdded -> R.string.settings_order_latest_expense_added
}

const val SETTINGS_TAG = "settings"
const val SETTINGS_LOADING_TAG = "settings:loading"
const val SETTINGS_ERROR_TAG = "settings:error"
const val SETTINGS_APPEARANCE_TAG = "settings:appearance"
const val SETTINGS_THEME_TAG = "settings:theme"
const val SETTINGS_INK_TAG = "settings:ink"
const val SETTINGS_ORDERING_TAG = "settings:ordering"
const val SETTINGS_ORDER_TAG = "settings:order"
const val SETTINGS_DIRECTION_TAG = "settings:direction"
const val SETTINGS_DATA_TAG = "settings:data"
const val SETTINGS_BACKUP_TAG = "settings:backup"
const val SETTINGS_RESET_TAG = "settings:reset"

const val SETTINGS_SCHEDULES_TAG = "settings:schedules"
