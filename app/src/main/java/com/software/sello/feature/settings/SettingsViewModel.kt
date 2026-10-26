package com.software.sello.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coordinates the appearance, Recibo-order and Inversiones controls, how many payments and
 * incomes repeat and the current USD rate. The backup card reads the backup's own state.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val appearancePreferences: AppearancePreferences,
    private val orderingPreferences: CategoryOrderingPreferences,
    private val investmentPreferences: InvestmentPreferences,
    schedules: ScheduleReads,
    investments: InvestmentReads,
    money: MoneyFormatter
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val busy = MutableStateFlow(false)
    private val writeFailure = MutableStateFlow<PreferenceFailure?>(null)

    private val reads: Flow<SettingsRead> = retry.flatMapLatest {
        merge(
            appearancePreferences.observeAppearance().map(SettingsRead::Appearance),
            orderingPreferences.observeCategoryOrdering().map(SettingsRead::Ordering),
            investmentPreferences.observeInvestments().map(SettingsRead::Investments)
        )
    }

    private val readState = reads.runningFold(SettingsState(loading = true)) { old, read ->
        when (read) {
            is SettingsRead.Appearance -> when (val value = read.value) {
                is PreferenceRead.Ready -> old.copy(
                    appearance = value.value,
                    appearanceLoaded = true,
                    appearanceFailure = null
                )

                is PreferenceRead.Failed -> old.copy(
                    appearance = value.lastGood ?: old.appearance,
                    appearanceLoaded = true,
                    appearanceFailure = value.failure
                )
            }

            is SettingsRead.Ordering -> when (val value = read.value) {
                is PreferenceRead.Ready -> old.copy(
                    categoryOrdering = value.value,
                    orderingLoaded = true,
                    orderingFailure = null
                )

                is PreferenceRead.Failed -> old.copy(
                    categoryOrdering = value.lastGood ?: old.categoryOrdering,
                    orderingLoaded = true,
                    orderingFailure = value.failure
                )
            }

            is SettingsRead.Investments -> when (val value = read.value) {
                is PreferenceRead.Ready -> old.copy(
                    investments = value.value,
                    investmentsLoaded = true,
                    investmentsFailure = null
                )

                is PreferenceRead.Failed -> old.copy(
                    investments = value.lastGood ?: old.investments,
                    investmentsLoaded = true,
                    investmentsFailure = value.failure
                )
            }
        }.let {
            it.copy(loading = !it.appearanceLoaded || !it.orderingLoaded || !it.investmentsLoaded)
        }
    }

    private val scheduleCount = retry.flatMapLatest { schedules.observe() }.map { read ->
        when (read) {
            ScheduleBookState.Loading -> ScheduleCountLine.Loading

            is ScheduleBookState.Failed -> ScheduleCountLine.Unreadable

            is ScheduleBookState.Ready -> {
                val all = read.book.schedules.map { it.schedule.entry }
                ScheduleCountLine.Read(
                    payments = all.count { it is ScheduledEntry.Payment },
                    incomes = all.count { it is ScheduledEntry.Income }
                )
            }
        }
    }

    private val rateLine = retry.flatMapLatest { investments.observeBoard() }.map { read ->
        when (read) {
            InvestmentBoardState.Loading -> RateLine.Loading

            is InvestmentBoardState.Failed -> RateLine.Unreadable

            is InvestmentBoardState.Ready -> read.board.rates.current?.let {
                RateLine.Read(money.decimal(it.rate.hundredths, RATE_SCALE), it.date)
            } ?: RateLine.None
        }
    }

    val state = merge(
        readState.map { read ->
            { old: SettingsState ->
                read.copy(
                    busy = old.busy,
                    writeFailure = old.writeFailure,
                    schedules = old.schedules,
                    rate = old.rate
                )
            }
        },
        scheduleCount.map { value -> { old: SettingsState -> old.copy(schedules = value) } },
        rateLine.map { value -> { old: SettingsState -> old.copy(rate = value) } },
        busy.map { value -> { old: SettingsState -> old.copy(busy = value) } },
        writeFailure.map { value -> { old: SettingsState -> old.copy(writeFailure = value) } }
    ).runningFold(SettingsState(loading = true)) { old, change -> change(old) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState(loading = true))

    fun onAction(action: SettingsAction) {
        if (action == SettingsAction.Retry) {
            writeFailure.value = null
            retry.update { it + 1 }
            return
        }
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            writeFailure.value = null
            val result = when (action) {
                is SettingsAction.ThemeChosen -> appearancePreferences.setTheme(action.theme)

                is SettingsAction.InkChosen -> appearancePreferences.setInk(action.ink)

                is SettingsAction.OrderChosen -> orderingPreferences.setCategoryOrder(action.order)

                is SettingsAction.DirectionChosen ->
                    orderingPreferences.setCategoryOrderDirection(action.direction)

                is SettingsAction.NewInvestmentCurrencyChosen ->
                    investmentPreferences.setNewInvestmentCurrency(action.currency)

                is SettingsAction.InvestmentOrderChosen ->
                    investmentPreferences.setInvestmentOrder(action.order)

                SettingsAction.Retry -> error("Retry is handled before launching")
            }
            busy.value = false
            if (result is PreferenceWrite.Failed) writeFailure.value = result.failure
        }
    }
}

sealed interface SettingsAction {
    data class ThemeChosen(val theme: ThemePreference) : SettingsAction
    data class InkChosen(val ink: InkPreference) : SettingsAction
    data class OrderChosen(val order: CategoryOrder) : SettingsAction
    data class DirectionChosen(val direction: CategoryOrderDirection) : SettingsAction
    data class NewInvestmentCurrencyChosen(val currency: Currency) : SettingsAction
    data class InvestmentOrderChosen(val order: InvestmentOrder) : SettingsAction
    data object Retry : SettingsAction
}

data class SettingsState(
    val appearance: AppearancePreference = AppearancePreference(),
    val categoryOrdering: CategoryOrderingPreference = CategoryOrderingPreference(),
    val investments: InvestmentPreference = InvestmentPreference(),
    val loading: Boolean,
    val appearanceLoaded: Boolean = false,
    val orderingLoaded: Boolean = false,
    val investmentsLoaded: Boolean = false,
    val appearanceFailure: PreferenceFailure? = null,
    val orderingFailure: PreferenceFailure? = null,
    val investmentsFailure: PreferenceFailure? = null,
    val writeFailure: PreferenceFailure? = null,
    val busy: Boolean = false,
    val schedules: ScheduleCountLine = ScheduleCountLine.Loading,
    val rate: RateLine = RateLine.Loading
) {
    val failure: PreferenceFailure?
        get() = writeFailure ?: appearanceFailure ?: orderingFailure ?: investmentsFailure
}

/** The rate is written to the centavo: "4.150,25" (investment rule 11). */
private const val RATE_SCALE = 2

/** The Dólar row: the current rate and its date, none yet, or unreadable. */
sealed interface RateLine {
    data object Loading : RateLine

    data object Unreadable : RateLine

    data object None : RateLine

    data class Read(val rate: String, val date: LocalDate) : RateLine
}

/** The Pagos e ingresos fijos row: how many of each, when that can be read. */
sealed interface ScheduleCountLine {
    data object Loading : ScheduleCountLine

    data object Unreadable : ScheduleCountLine

    data class Read(val payments: Int, val incomes: Int) : ScheduleCountLine
}

private sealed interface SettingsRead {
    data class Appearance(val value: PreferenceRead<AppearancePreference>) : SettingsRead
    data class Ordering(val value: PreferenceRead<CategoryOrderingPreference>) : SettingsRead
    data class Investments(val value: PreferenceRead<InvestmentPreference>) : SettingsRead
}
