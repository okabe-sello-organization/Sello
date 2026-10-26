package com.software.sello.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonPair
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.InkSwatches
import com.software.sello.designsystem.component.OptionSlip
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SettingRow
import com.software.sello.designsystem.component.SettingTone
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.feature.backup.BackupAction
import com.software.sello.feature.backup.BackupProblems
import com.software.sello.feature.backup.BackupSavePicker
import com.software.sello.feature.backup.BackupState
import com.software.sello.feature.backup.BackupViewModel
import com.software.sello.feature.backup.RestoreAction
import com.software.sello.feature.backup.RestoreFilePicker
import com.software.sello.feature.backup.RestoreStep
import com.software.sello.feature.backup.RestoreViewModel
import com.software.sello.feature.backup.backupDisclosure
import com.software.sello.presentation.date.dateLabels

@Composable
fun SettingsRoot(
    viewModels: ViewModelProvider.Factory,
    contentPadding: PaddingValues,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenRate: () -> Unit
) {
    val model: SettingsViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    // The card saves and restores through the same sessions as Respaldo.
    val backupModel: BackupViewModel = viewModel(factory = viewModels)
    val backup by backupModel.state.collectAsStateWithLifecycle()
    val restoreModel: RestoreViewModel = viewModel(factory = viewModels)
    val restore by restoreModel.state.collectAsStateWithLifecycle()
    BackupSavePicker(backup, backupModel::onAction)
    // A chosen file is checked, previewed and confirmed on Respaldo, never here.
    RestoreFilePicker(restore, restoreModel::onAction, onChosen = onOpenBackup)
    SettingsScreen(
        state = state,
        onAction = model::onAction,
        backup = backup,
        onBackupAction = backupModel::onAction,
        restoreEnabled = !restore.pickerRequest,
        onRestore = {
            // A restore already under way is Respaldo's to show.
            if (restore.step == RestoreStep.Idle) {
                restoreModel.onAction(RestoreAction.Choose)
            } else {
                onOpenBackup()
            }
        },
        contentPadding = contentPadding,
        onOpenBackup = onOpenBackup,
        onOpenReset = onOpenReset,
        onOpenSchedules = onOpenSchedules,
        onOpenRate = onOpenRate
    )
}

/** Which choice is open over Configuración, kept across recreation. */
private enum class SettingsPicker { Order, Direction }

/**
 * Configuración: Tus datos and its backup card first, then Lo que se repite, Apariencia,
 * Recibo and Inversiones. Every control is a real setting, never a placeholder.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    backup: BackupState,
    onBackupAction: (BackupAction) -> Unit,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
    contentPadding: PaddingValues,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var disclosing by rememberSaveable { mutableStateOf(false) }
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
        BackupProblems(backup) { disclosing = true }
        DataControls(
            backup = backup,
            onBackupAction = onBackupAction,
            onCreate = { disclosing = true },
            restoreEnabled = restoreEnabled,
            onRestore = onRestore,
            onOpenBackup = onOpenBackup,
            onOpenReset = onOpenReset
        )
        RecurringControls(state, onOpenSchedules)
        AppearanceControls(state, onAction)
        CategoryOrderingControls(state) { picking = it }
        InvestmentControls(state, onOpenRate)
        Text(
            text = stringResource(R.string.settings_footer),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = SelloTheme.spacing.sm)
        )
    }
    if (disclosing) {
        ConfirmSlip(
            title = stringResource(R.string.backup_disclosure_title),
            confirmLabel = stringResource(R.string.settings_backup_choose),
            onConfirm = {
                disclosing = false
                onBackupAction(BackupAction.Create)
            },
            onDismiss = { disclosing = false },
            destructive = false,
            modifier = Modifier.testTag(SETTINGS_DISCLOSURE_TAG)
        ) {
            BulletList(backupDisclosure())
        }
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

        null -> Unit
    }
}

@Composable
private fun DataControls(
    backup: BackupState,
    onBackupAction: (BackupAction) -> Unit,
    onCreate: () -> Unit,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenReset: () -> Unit
) {
    if (backup.loading) {
        SlipSkeleton(Modifier.testTag(SETTINGS_DATA_TAG))
        return
    }
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_DATA_TAG)) {
        SectionTitle(R.string.settings_data)
        BackupCard(backup, onBackupAction, onCreate, restoreEnabled, onRestore, onOpenBackup)
        SettingRow(
            title = stringResource(R.string.settings_reset),
            icon = SelloIcon.DeleteForever,
            caption = stringResource(R.string.settings_reset_caption),
            onClick = onOpenReset,
            tone = SettingTone.Loss,
            divider = false,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag(SETTINGS_RESET_TAG)
        )
    }
}

/**
 * When the last backup was made, how old and how big it is, a stamp for whether a new one
 * is due, and the way to make one or restore one.
 */
@Composable
private fun BackupCard(
    backup: BackupState,
    onBackupAction: (BackupAction) -> Unit,
    onCreate: () -> Unit,
    restoreEnabled: Boolean,
    onRestore: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val colors = SelloTheme.colors
    val last = backup.last
    val zone = backup.zone
    val today = backup.today
    // Unknown when the record could not be read: no stamp says it is fine or due.
    val age = if (backup.recordFailed || zone == null || today == null) {
        null
    } else {
        backupAge(last?.at, zone, today)
    }
    Column(Modifier.testTag(SETTINGS_BACKUP_CARD_TAG)) {
        // The stamp sits beside the label when there is room and under it when not.
        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(SelloIcon.Backup.painter(), null, Modifier.size(18.dp), colors.brand)
                Text(
                    stringResource(R.string.settings_backup),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inkSoft
                )
            }
            age?.let { BackupStamp(it, land = backup.justSaved) }
        }
        when {
            age == null -> {
                Text(
                    stringResource(R.string.backup_record_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkSoft,
                    modifier = Modifier.padding(top = SelloTheme.spacing.sm)
                )
                SelloButton(
                    text = stringResource(R.string.backup_retry),
                    onClick = { onBackupAction(BackupAction.RetryRecord) },
                    kind = ButtonKind.Text
                )
            }

            last == null -> BackupFigure(
                stringResource(R.string.settings_backup_never),
                stringResource(R.string.settings_backup_never_caption)
            )

            else -> {
                val day = last.at.atZone(zone).toLocalDate()
                val context = LocalContext.current
                BackupFigure(
                    dateLabels().dayMonthYear(day),
                    stringResource(
                        R.string.settings_backup_age_size,
                        ageText(age),
                        Formatter.formatShortFileSize(context, last.bytes)
                    )
                )
            }
        }
        ButtonPair(
            firstWeight = 1f,
            secondWeight = 1f,
            gap = SelloTheme.spacing.sm,
            modifier = Modifier.padding(top = SelloTheme.spacing.md),
            first = {
                SelloButton(
                    text = stringResource(
                        when {
                            backup.saving -> R.string.backup_saving
                            age == BackupAge.None -> R.string.settings_backup_create_first
                            else -> R.string.settings_backup_create
                        }
                    ),
                    onClick = onCreate,
                    icon = SelloIcon.Backup,
                    enabled = !backup.busy,
                    loading = backup.saving,
                    modifier = Modifier.testTag(SETTINGS_BACKUP_CREATE_TAG)
                )
            },
            second = {
                SelloButton(
                    text = stringResource(R.string.settings_backup_restore),
                    onClick = onRestore,
                    kind = ButtonKind.Outlined,
                    icon = SelloIcon.UploadFile,
                    enabled = restoreEnabled && !backup.saving,
                    modifier = Modifier.testTag(SETTINGS_RESTORE_TAG)
                )
            }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = SelloTheme.spacing.xs)
                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onOpenBackup)
                .testTag(SETTINGS_BACKUP_TAG)
        ) {
            Text(
                stringResource(R.string.settings_backup_contents),
                style = MaterialTheme.typography.labelLarge,
                color = colors.brand
            )
            Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(18.dp), colors.brand)
        }
    }
}

@Composable
private fun BackupStamp(age: BackupAge, land: Boolean) {
    val (text, ink) = when (age) {
        BackupAge.None -> R.string.settings_backup_stamp_none to StampInk.Over
        is BackupAge.Recent -> R.string.backup_saved_stamp to StampInk.Done
        is BackupAge.Old -> R.string.settings_backup_stamp_old to StampInk.Warn
    }
    Stamp(
        text = stringResource(text),
        ink = ink,
        land = land,
        modifier = Modifier.padding(vertical = 4.dp).testTag(SETTINGS_BACKUP_STAMP_TAG)
    )
}

@Composable
private fun BackupFigure(figure: String, caption: String) {
    Text(
        figure,
        style = MaterialTheme.typography.displaySmall,
        color = SelloTheme.colors.ink,
        modifier = Modifier.padding(top = SelloTheme.spacing.sm)
    )
    Text(caption, style = MaterialTheme.typography.bodyMedium, color = SelloTheme.colors.inkSoft)
}

@Composable
private fun ageText(age: BackupAge): String {
    val days = when (age) {
        is BackupAge.Recent -> age.days
        is BackupAge.Old -> age.days
        BackupAge.None -> 0
    }
    return when (days) {
        0L -> stringResource(R.string.settings_backup_today)
        1L -> stringResource(R.string.settings_backup_yesterday)
        else -> pluralStringResource(R.plurals.settings_backup_days, days.toInt(), days.toInt())
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
                onAction(SettingsAction.InkChosen(InkPreference.entries.first { it.asSelloInk() == ink }))
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
private fun InvestmentControls(state: SettingsState, onOpenRate: () -> Unit) {
    val rate = state.rate
    Slip(Modifier.fillMaxWidth().testTag(SETTINGS_INVESTMENTS_TAG)) {
        SectionTitle(R.string.settings_investments)
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
            divider = false,
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
const val SETTINGS_BACKUP_CARD_TAG = "settings:backup:card"
const val SETTINGS_BACKUP_STAMP_TAG = "settings:backup:stamp"
const val SETTINGS_BACKUP_CREATE_TAG = "settings:backup:create"
const val SETTINGS_DISCLOSURE_TAG = "settings:backup:disclosure"
const val SETTINGS_RESTORE_TAG = "settings:restore"

/** "Ver qué contiene": the way into Respaldo. */
const val SETTINGS_BACKUP_TAG = "settings:backup"
const val SETTINGS_RESET_TAG = "settings:reset"
const val SETTINGS_RECURRING_TAG = "settings:recurring"
const val SETTINGS_SCHEDULES_TAG = "settings:schedules"
const val SETTINGS_INVESTMENTS_TAG = "settings:investments"
const val SETTINGS_RATE_TAG = "settings:rate"
