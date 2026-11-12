package com.software.sello.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InkColour
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TuyaColour
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.SmallExpensePreferences
import com.software.sello.platform.PhoneColor
import com.software.sello.platform.SystemColor
import com.software.sello.presentation.appearance.colour
import com.software.sello.presentation.appearance.startingColour
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coordinates the appearance, Recibo-order, Gastos hormiga and Inversiones controls, how many payments and
 * incomes repeat and the current USD rate. [systemColor] is the phone's colour, for Sistema. The backup card reads the backup's own state.
 * The colour being made in Tu tinta is kept in [handle] while the sheet is open, so it survives
 * rotation and process death; nothing is saved until Usar esta tinta.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val handle: SavedStateHandle,
    private val appearancePreferences: AppearancePreferences,
    private val orderingPreferences: CategoryOrderingPreferences,
    private val investmentPreferences: InvestmentPreferences,
    smallExpensePreferences: SmallExpensePreferences,
    schedules: ScheduleReads,
    investments: InvestmentReads,
    money: MoneyFormatter,
    systemColor: SystemColor
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val busy = MutableStateFlow(false)
    private val writeFailure = MutableStateFlow<PreferenceFailure?>(null)
    private val tuyaSaving = MutableStateFlow(false)
    private val tuyaFailed = MutableStateFlow(false)

    private val tuyaKept = combine(
        handle.getStateFlow<Int?>(KEY_TUYA_COLOUR, null),
        handle.getStateFlow<FloatArray?>(KEY_TUYA_PICK, null),
        handle.getStateFlow<String?>(KEY_TUYA_CODE, null),
        handle.getStateFlow<Int?>(KEY_TUYA_BEFORE, null),
        ::keptDraft
    )

    private val tuya = combine(tuyaKept, tuyaSaving, tuyaFailed) { kept, saving, failed ->
        kept?.copy(saving = saving, failed = failed)
    }

    private val reads: Flow<SettingsRead> = retry.flatMapLatest {
        merge(
            appearancePreferences.observeAppearance().map(SettingsRead::Appearance),
            orderingPreferences.observeCategoryOrdering().map(SettingsRead::Ordering),
            investmentPreferences.observeInvestments().map(SettingsRead::Investments),
            smallExpensePreferences.observeSmallExpenseLine().map(SettingsRead::SmallExpenses)
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

            is SettingsRead.SmallExpenses -> when (val value = read.value) {
                is PreferenceRead.Ready -> old.copy(
                    smallExpenseLine = money.format(value.value.money).digits,
                    smallExpensesLoaded = true,
                    smallExpensesFailure = null
                )

                // Never a default in its place: the row shows no value until it reads.
                is PreferenceRead.Failed -> old.copy(
                    smallExpenseLine = value.lastGood?.let { money.format(it.money).digits }
                        ?: old.smallExpenseLine,
                    smallExpensesLoaded = true,
                    smallExpensesFailure = value.failure
                )
            }
        }.let {
            it.copy(
                loading = !it.appearanceLoaded || !it.orderingLoaded ||
                    !it.investmentsLoaded || !it.smallExpensesLoaded
            )
        }
    }

    private val scheduleCount = retry.flatMapLatest { schedules.observe() }.map { read ->
        when (read) {
            ReadState.Loading -> ScheduleCountLine.Loading

            is ReadState.Failed -> ScheduleCountLine.Unreadable

            is ReadState.Ready -> {
                val all = read.value.schedules.map { it.schedule.entry }
                ScheduleCountLine.Read(
                    payments = all.count { it is ScheduledEntry.Payment },
                    incomes = all.count { it is ScheduledEntry.Income }
                )
            }
        }
    }

    private val rateLine = retry.flatMapLatest { investments.observeBoard() }.map { read ->
        when (read) {
            ReadState.Loading -> RateLine.Loading

            is ReadState.Failed -> RateLine.Unreadable

            is ReadState.Ready -> read.value.rates.current?.let {
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
                    rate = old.rate,
                    tuya = old.tuya,
                    phone = old.phone
                )
            }
        },
        scheduleCount.map { value -> { old: SettingsState -> old.copy(schedules = value) } },
        rateLine.map { value -> { old: SettingsState -> old.copy(rate = value) } },
        busy.map { value -> { old: SettingsState -> old.copy(busy = value) } },
        writeFailure.map { value -> { old: SettingsState -> old.copy(writeFailure = value) } },
        tuya.map { value -> { old: SettingsState -> old.copy(tuya = value) } },
        systemColor.color.map { value -> { old: SettingsState -> old.copy(phone = value) } }
    ).runningFold(SettingsState(loading = true)) { old, change -> change(old) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState(loading = true))

    fun onAction(action: SettingsAction) {
        if (action == SettingsAction.Retry) {
            writeFailure.value = null
            retry.update { it + 1 }
            return
        }
        if (action is SettingsAction.Tuya) return onTuya(action)
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

                SettingsAction.Retry, is SettingsAction.Tuya ->
                    error("Handled before launching")
            }
            busy.value = false
            if (result is PreferenceWrite.Failed) writeFailure.value = result.failure
        }
    }

    /** Tu tinta: nothing is written until [SettingsAction.Tuya.Use]; one save at a time. */
    private fun onTuya(action: SettingsAction.Tuya) {
        if (tuyaSaving.value) return
        if (action == SettingsAction.Tuya.Open) return open()
        // Every other action needs the sheet open, as this screen wrote it.
        val draft = openDraft() ?: return
        when (action) {
            SettingsAction.Tuya.Open -> Unit

            is SettingsAction.Tuya.SquareMoved -> move(
                draft.pick.copy(saturation = action.saturation, value = action.value)
            )

            is SettingsAction.Tuya.BarMoved -> move(draft.pick.copy(hue = action.hue))

            is SettingsAction.Tuya.CodeTyped -> {
                // Kept as typed, so the field shows it; only a whole code moves the colour.
                val typed = action.text.take(CODE_LENGTH)
                handle[KEY_TUYA_CODE] = typed
                val rgb = colourOfCode(typed) ?: return
                tuyaFailed.value = false
                handle[KEY_TUYA_PICK] = TuyaPick.of(rgb, draft.pick.hue).toArray()
                handle[KEY_TUYA_COLOUR] = rgb
            }

            SettingsAction.Tuya.CodeLeft -> handle[KEY_TUYA_CODE] = codeOf(draft.colour.rgb)

            SettingsAction.Tuya.BeforeTapped -> {
                tuyaFailed.value = false
                val rgb = draft.before.rgb
                handle[KEY_TUYA_PICK] = TuyaPick.of(rgb, draft.pick.hue).toArray()
                handle[KEY_TUYA_CODE] = codeOf(rgb)
                handle[KEY_TUYA_COLOUR] = rgb
            }

            SettingsAction.Tuya.Cancel -> close()

            SettingsAction.Tuya.Use -> {
                if (busy.value) return
                // Nothing can be typed while it saves, so the field shows the colour saved.
                handle[KEY_TUYA_CODE] = codeOf(draft.colour.rgb)
                tuyaSaving.value = true
                busy.value = true
                tuyaFailed.value = false
                viewModelScope.launch {
                    val result = try {
                        appearancePreferences.setInk(
                            InkPreference.Tuya(TuyaColour.Exact(draft.colour))
                        )
                    } finally {
                        busy.value = false
                        tuyaSaving.value = false
                    }
                    if (result is PreferenceWrite.Saved) close() else tuyaFailed.value = true
                }
            }
        }
    }

    /**
     * Opens on Tuya's kept colour, else its kept hue's colour, else the ink in use's; Antes is
     * the ink in use. The colour goes last: it is what opens the sheet.
     */
    private fun open() {
        tuyaFailed.value = false
        val (start, before) = state.value.run {
            appearance.startingColour(phone) to appearance.ink.colour(phone)
        }
        handle[KEY_TUYA_BEFORE] = before
        handle[KEY_TUYA_PICK] = TuyaPick.of(start, 0f).toArray()
        handle[KEY_TUYA_CODE] = codeOf(start)
        handle[KEY_TUYA_COLOUR] = start
    }

    /** The square or the bar moved to [pick]; a place they can't stand changes nothing. */
    private fun move(pick: TuyaPick) {
        if (!pick.isValid) return
        tuyaFailed.value = false
        val rgb = pick.rgb()
        handle[KEY_TUYA_PICK] = pick.toArray()
        handle[KEY_TUYA_CODE] = codeOf(rgb)
        handle[KEY_TUYA_COLOUR] = rgb
    }

    private fun close() {
        tuyaFailed.value = false
        // Not remove(): that would cut the sheet's flow off from later changes.
        handle[KEY_TUYA_COLOUR] = null
    }

    private fun openDraft(): TuyaDraft? = keptDraft(
        handle[KEY_TUYA_COLOUR],
        handle[KEY_TUYA_PICK],
        handle[KEY_TUYA_CODE],
        handle[KEY_TUYA_BEFORE]
    )

    private companion object {
        const val KEY_TUYA_COLOUR = "settings.tuya.colour"
        const val KEY_TUYA_PICK = "settings.tuya.pick"
        const val KEY_TUYA_CODE = "settings.tuya.code"
        const val KEY_TUYA_BEFORE = "settings.tuya.before"
    }
}

/**
 * The open sheet as kept in the handle, or null when it is closed. Only what this screen
 * wrote opens it: a colour, Antes or a place out of range, or a missing piece, is no sheet.
 */
private fun keptDraft(colour: Int?, pick: FloatArray?, code: String?, before: Int?): TuyaDraft? {
    val chosen = colour?.toLong()?.let(InkColour::of) ?: return null
    val antes = before?.toLong()?.let(InkColour::of) ?: return null
    val place = pick?.takeIf { it.size == PICK_SIZE }
        ?.let { TuyaPick(it[0], it[1], it[2]) }
        ?.takeIf { it.isValid } ?: return null
    if (code == null || code.length > CODE_LENGTH) return null
    return TuyaDraft(colour = chosen, pick = place, code = code, before = antes)
}

private fun TuyaPick.toArray() = floatArrayOf(hue, saturation, value)

private const val PICK_SIZE = 3

sealed interface SettingsAction {
    data class ThemeChosen(val theme: ThemePreference) : SettingsAction
    data class InkChosen(val ink: InkPreference) : SettingsAction
    data class OrderChosen(val order: CategoryOrder) : SettingsAction
    data class DirectionChosen(val direction: CategoryOrderDirection) : SettingsAction
    data class NewInvestmentCurrencyChosen(val currency: Currency) : SettingsAction
    data class InvestmentOrderChosen(val order: InvestmentOrder) : SettingsAction
    data object Retry : SettingsAction

    /** The Tu tinta sheet's actions. */
    sealed interface Tuya : SettingsAction {
        /** Tuya was tapped: the sheet opens on Tuya's colour or the ink in use's. */
        data object Open : Tuya

        /** The colour square, at [saturation] (left to right) and [value] (bottom to top). */
        data class SquareMoved(val saturation: Float, val value: Float) : Tuya

        /** The colour bar, at [hue] degrees, from 0 up to 360. */
        data class BarMoved(val hue: Float) : Tuya

        /** The code field's text; a whole code moves the colour, anything else nothing. */
        data class CodeTyped(val text: String) : Tuya

        /** The code field lost focus: it shows the current colour's code again. */
        data object CodeLeft : Tuya

        /** Antes: back to the ink in use when the sheet opened. */
        data object BeforeTapped : Tuya

        /** Cancelar, Cerrar, back or the scrim: the ink stays as it is. */
        data object Cancel : Tuya

        /** Usar esta tinta, or Reintentar after a save that failed. */
        data object Use : Tuya
    }
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
    /** Gastos hormiga's line as written ("20.000"); null until it is read. */
    val smallExpenseLine: String? = null,
    val smallExpensesLoaded: Boolean = false,
    val smallExpensesFailure: PreferenceFailure? = null,
    val writeFailure: PreferenceFailure? = null,
    val busy: Boolean = false,
    val schedules: ScheduleCountLine = ScheduleCountLine.Loading,
    val rate: RateLine = RateLine.Loading,
    /** The Tu tinta sheet while it is open; null when it is closed. */
    val tuya: TuyaDraft? = null,
    /** The phone's accent colour, for Sistema. */
    val phone: PhoneColor = PhoneColor.None
) {
    val failure: PreferenceFailure?
        get() = writeFailure ?: appearanceFailure ?: orderingFailure ?: investmentsFailure
            ?: smallExpensesFailure
}

/**
 * Tu tinta while it is open: the [colour] being made; where the bar and the square stand
 * ([pick]); the code field's text ([code]), as typed or the colour's; Antes ([before]), the
 * colour of the ink in use when it opened; whether it is being saved; and whether the last
 * save failed, which leaves the app's ink as it was.
 */
data class TuyaDraft(
    val colour: InkColour,
    val pick: TuyaPick,
    val code: String,
    val before: InkColour,
    val saving: Boolean = false,
    val failed: Boolean = false
)

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
    data class SmallExpenses(val value: PreferenceRead<SmallExpenseLine>) : SettingsRead
}
