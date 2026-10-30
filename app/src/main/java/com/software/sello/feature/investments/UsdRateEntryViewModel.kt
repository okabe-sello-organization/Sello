package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.policy.DecimalDraft
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.SetUsdRate
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Cambiar la tasa (rule 11): pesos per dollar typed with the comma pad and a date. The
 * draft, the date and the operation in flight live in [saved]. A save whose outcome is
 * not known is asked about with its own identifier before anything else is sent.
 */
class UsdRateEntryViewModel(
    private val saved: SavedStateHandle,
    reads: InvestmentReads,
    private val commands: InvestmentCommands,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter
) : ViewModel() {
    private data class Progress(
        val busy: Boolean = false,
        val error: RateEntryError? = null,
        val closed: Boolean = false,
        val datePickerOpen: Boolean = false,
        val limitPulse: Int = 0,
        val atLimit: Boolean = false
    )

    /** The rate as typed, digit by digit, and the date chosen, kept under one key. */
    @Serializable
    private data class Draft(
        val whole: String? = null,
        val comma: Boolean = false,
        val cents: String = "",
        val date: String? = null
    )

    private val render = InvestmentsRenderModel(money)
    private val drafts = SavedDraft(saved, DRAFT, Draft.serializer())
    private val submission = Submission(saved, OPERATION, ids)
    private val draft = MutableStateFlow(restored())
    private val date = MutableStateFlow(
        drafts.read()?.date?.let(LocalDate::parse) ?: clock.today.value.date
    )
    private val progress = MutableStateFlow(Progress())
    private val board = reads.observeBoard()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadState.Loading)

    val state: StateFlow<UsdRateEntryState> =
        combine(draft, date, progress, board, clock.today) { draft, date, progress, board, today ->
            render(draft, date, progress, board, today.date)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(draft.value, date.value, progress.value, board.value, clock.today.value.date)
        )

    init {
        if (submission.pending) viewModelScope.launch { recover() }
    }

    private fun restored(): DecimalDraft {
        val typed = drafts.read()
        val whole = typed?.whole ?: return DecimalDraft(MAX_WHOLE)
        return DecimalDraft(MAX_WHOLE, whole, typed.comma, typed.cents)
    }

    private fun store(rate: DecimalDraft, date: LocalDate?) {
        drafts.write(
            Draft(
                rate.whole,
                rate.comma,
                rate.cents,
                date?.toString() ?: drafts.read()?.date
            )
        )
    }

    fun onAction(action: UsdRateEntryAction) {
        val now = state.value
        val editable = now.editable && !now.loading && !now.loadFailed
        when (action) {
            is UsdRateEntryAction.Digit -> if (editable) key(draft.value.digit(action.digit))

            UsdRateEntryAction.Comma -> if (editable) key(draft.value.comma())

            UsdRateEntryAction.Backspace -> if (editable) change(draft.value.backspace())

            UsdRateEntryAction.Clear -> if (editable) change(DecimalDraft(MAX_WHOLE))

            UsdRateEntryAction.OpenDatePicker ->
                if (editable) progress.update { it.copy(datePickerOpen = true) }

            UsdRateEntryAction.CloseDatePicker ->
                progress.update { it.copy(datePickerOpen = false) }

            is UsdRateEntryAction.DatePicked -> {
                if (editable && InvestmentPolicy.acceptsDate(action.date, now.today)) {
                    store(draft.value, action.date)
                    date.value = action.date
                }
                progress.update { it.copy(datePickerOpen = false) }
            }

            UsdRateEntryAction.Submit -> if (now.canSubmit) submit()

            UsdRateEntryAction.Retry -> if (!now.busy) {
                if (submission.pending) {
                    viewModelScope.launch { recover() }
                } else {
                    progress.update { it.copy(error = null) }
                }
            }

            // Never closed while a save may be on its way: its outcome comes first.
            UsdRateEntryAction.Back -> if (!now.busy && !submission.pending) {
                progress.update { it.copy(closed = true) }
            }
        }
    }

    private fun key(change: DecimalDraft.Change) {
        change(change.draft)
        if (change.limitReached) {
            progress.update { it.copy(limitPulse = it.limitPulse + 1, atLimit = true) }
        }
    }

    private fun change(next: DecimalDraft) {
        if (next != draft.value) progress.update { it.copy(atLimit = false, error = null) }
        store(next, null)
        draft.value = next
    }

    private fun readable(state: InvestmentBoardState): InvestmentBoard? = when (state) {
        ReadState.Loading -> null
        is ReadState.Ready -> state.value
        is ReadState.Failed -> state.lastGood
    }

    /** The draft as the pad wrote it: "4.180,7". */
    private fun text(draft: DecimalDraft): String? {
        if (draft.empty) return null
        val whole = draft.whole.ifEmpty { "0" }
        val grouped = money.decimal(whole.toLong(), 0)
        return if (draft.comma) "$grouped,${draft.cents}" else grouped
    }

    private fun render(
        draft: DecimalDraft,
        date: LocalDate,
        progress: Progress,
        board: InvestmentBoardState,
        today: LocalDate
    ): UsdRateEntryState {
        val read = readable(board)
        val hundredths = draft.hundredths()
        return UsdRateEntryState(
            loading = board is ReadState.Loading,
            loadFailed = board is ReadState.Failed && read == null,
            rate = text(draft),
            problem = when {
                hundredths == null -> null
                hundredths < UsdRate.MIN_HUNDREDTHS -> RateProblem.BelowMinimum
                hundredths > UsdRate.MAX_HUNDREDTHS -> RateProblem.AboveMaximum
                else -> null
            },
            current = read?.rates?.current?.let { RateModel(render.rate(it.rate), it.date) },
            date = date,
            today = today,
            datePickerOpen = progress.datePickerOpen,
            limitPulse = progress.limitPulse,
            atLimit = progress.atLimit,
            busy = progress.busy,
            error = progress.error,
            closed = progress.closed
        )
    }

    private fun submit() {
        val read = readable(board.value) ?: return
        val hundredths = draft.value.hundredths() ?: return
        val rate = (UsdRate.ofHundredths(hundredths) as? Outcome.Success)?.value ?: return
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            settle(
                submission.send(commands::find) { operation ->
                    commands.submit(SetUsdRate(operation, read.generation, rate, date.value))
                }
            )
        }
    }

    private fun settle(settled: Settled<CommittedInvestment, InvestmentRejection>?) {
        when (settled) {
            // Something is still in flight and will settle; or nothing was left to ask about.
            null -> if (!submission.pending) progress.update { it.copy(busy = false) }

            is Settled.Committed ->
                progress.update { it.copy(busy = false, error = null, closed = true) }

            is Settled.Unknown -> progress.update {
                it.copy(busy = false, error = RateEntryError.Unknown)
            }

            Settled.NotCommitted -> progress.update {
                it.copy(busy = false, error = RateEntryError.NotSaved)
            }

            is Settled.Rejected -> {
                val error = when (val reason = settled.reason) {
                    is CommandRejection.StaleGeneration -> RateEntryError.DataChanged

                    CommandRejection.OperationConflict,
                    is CommandRejection.VersionConflict,
                    is CommandRejection.StorageFailed -> RateEntryError.NotSaved

                    is CommandRejection.Specific -> when (reason.reason) {
                        is InvestmentRejection.FutureDate -> RateEntryError.FutureDate
                        else -> RateEntryError.NotSaved
                    }
                }
                progress.update { it.copy(busy = false, error = error) }
            }
        }
    }

    /** Asks how the saved operation ended, with its own identifier. Only a receipt counts. */
    private suspend fun recover() {
        if (!submission.pending) return
        progress.update { it.copy(busy = true, error = null) }
        settle(submission.recover(commands::find))
    }

    private companion object {
        /** 100.000,00 is six whole digits (rule 11). */
        const val MAX_WHOLE = 6

        const val DRAFT = "usd.rate.draft"
        const val OPERATION = "usd.rate.operation"
    }
}
