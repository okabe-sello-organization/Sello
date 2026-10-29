package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.DecimalDraft
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.EditInvestmentMovement
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Anotar movimiento: an Aporte, Valor de hoy, Retiro or Dividendo o renta, and the
 * correction or deletion of a movement already recorded
 * (docs/testing/investment-examples.md). The investments come from the board as it
 * changes; the draft, an open note and the operation in flight live in [saved]. Before
 * anything is sent the movement is checked against the investment's whole history: a
 * retiro above the value on its date or with no value, a movement before the first
 * aporte, or one that would leave a later retiro short waits with the reason. A save
 * whose outcome is not known is asked about with its own identifier before anything
 * else is sent. Pesos are typed with 000, dollars with the comma and exact cents.
 */
class InvestmentMovementViewModel(
    private val saved: SavedStateHandle,
    reads: InvestmentReads,
    private val commands: InvestmentCommands,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: ExpenseCorrections
) : ViewModel() {
    private data class Draft(
        val kind: InvestmentMovementKind,
        val investmentId: String?,
        val pesos: AmountDraft,
        val dollars: DecimalDraft,
        val date: LocalDate,
        val note: String
    )

    private data class Progress(
        val busy: Boolean = false,
        val error: InvestmentMovementError? = null,
        val closed: Boolean = false,
        val confirmDiscard: Boolean = false,
        val datePickerOpen: Boolean = false,
        val limitPulse: Int = 0,
        val atLimit: Boolean = false,
        /** The later retiro a deletion was refused for, and what it would have been worth. */
        val deleteBlockedBy: Pair<InvestmentMovementId, Money?>? = null,
        /** The earliest aporte the command said the movement is dated before. */
        val firstContribution: LocalDate? = null
    )

    /** What the history says about the movement as it would be recorded. */
    private data class Check(
        val over: Money? = null,
        val noValue: Boolean = false,
        val blockedBy: BlockingRetiro? = null,
        val firstContribution: LocalDate? = null
    )

    private val movementId: String? = saved[KEY_MOVEMENT]
    private val editing = movementId != null

    private val draft = MutableStateFlow(
        Draft(
            kind = saved.get<String>(KIND)?.let(::kindOf)
                ?: saved.get<String>(KEY_KIND)?.let(::kindOf)
                ?: InvestmentMovementKind.Contribution,
            investmentId = saved[INVESTMENT] ?: saved[KEY_INVESTMENT],
            pesos =
                saved.get<String>(PESOS)?.let {
                    if (it ==
                        ZERO
                    ) {
                        ZeroPesos
                    } else {
                        AmountDraft.paste(it)
                    }
                }
                    ?: AmountDraft.Empty,
            dollars = saved.get<String>(WHOLE)?.let {
                DecimalDraft(
                    MAX_WHOLE,
                    it,
                    saved.get<Boolean>(COMMA) ?: false,
                    saved.get<String>(CENTS).orEmpty()
                )
            } ?: DecimalDraft(MAX_WHOLE),
            date = saved.get<String>(DATE)?.let(LocalDate::parse) ?: clock.today.value.date,
            note = saved[NOTE] ?: ""
        )
    )
    private val progress = MutableStateFlow(Progress())
    private val board = reads.observeBoard()
        .stateIn(viewModelScope, SharingStarted.Eagerly, InvestmentBoardState.Loading)
    private val composer = combine(
        saved.getStateFlow(COMPOSER, false),
        saved.getStateFlow(COMPOSER_TEXT, "")
    ) { open, text -> text.takeIf { open } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, composerNow())

    private fun composerNow(): String? = if (saved.get<Boolean>(COMPOSER) ==
        true
    ) {
        saved.get<String>(COMPOSER_TEXT).orEmpty()
    } else {
        null
    }

    val state: StateFlow<InvestmentMovementState> =
        combine(draft, progress, board, clock.today, composer) {
                draft,
                progress,
                board,
                today,
                writing
            ->
            render(draft, progress, board, today.date, writing)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(draft.value, progress.value, board.value, clock.today.value.date, composerNow())
        )

    init {
        viewModelScope.launch {
            board.collect { state ->
                val current = readable(state) ?: return@collect
                if (editing && saved.get<Boolean>(DRAFTED) != true) {
                    original(current)?.let { (summary, movement) ->
                        saved[DRAFTED] = true
                        val units = movement.amount.money.minorUnits
                        val usd = summary.history.investment.currency == Currency.USD
                        change(
                            draft.value.copy(
                                kind = movement.kind,
                                pesos = if (usd) {
                                    AmountDraft.Empty
                                } else if (units == 0L) {
                                    ZeroPesos
                                } else {
                                    AmountDraft.Digits(units.toString())
                                },
                                dollars = if (usd) {
                                    DecimalDraft.of(units, MAX_WHOLE)
                                } else {
                                    DecimalDraft(MAX_WHOLE)
                                },
                                date = movement.date,
                                note = movement.note?.value.orEmpty()
                            )
                        )
                    }
                }
            }
        }
        if (saved.get<String>(OPERATION) != null) viewModelScope.launch { recover() }
    }

    fun onAction(action: InvestmentMovementAction) {
        val now = state.value
        val editable = now.editable && !now.loading && !now.loadFailed
        when (action) {
            is InvestmentMovementAction.Kind ->
                if (editable && !editing) change(draft.value.copy(kind = action.kind))

            InvestmentMovementAction.RecordValueFirst -> if (editable && now.offersValue) {
                change(clearedAmount(draft.value).copy(kind = InvestmentMovementKind.Valuation))
            }

            is InvestmentMovementAction.Digit -> if (editable) {
                if (now.usd) {
                    dollars(
                        draft.value.dollars.digit(action.digit)
                    )
                } else {
                    pesos(pesoDigit(draft.value, action.digit))
                }
            }

            InvestmentMovementAction.TripleZero ->
                if (editable && !now.usd) pesos(draft.value.pesos.tripleZero())

            InvestmentMovementAction.Comma ->
                if (editable && now.usd) dollars(draft.value.dollars.comma())

            InvestmentMovementAction.Backspace -> if (editable) {
                val current = draft.value
                change(
                    if (now.usd) {
                        current.copy(dollars = current.dollars.backspace())
                    } else {
                        current.copy(pesos = current.pesos.backspace())
                    }
                )
            }

            InvestmentMovementAction.ClearAmount -> if (editable) change(clearedAmount(draft.value))

            is InvestmentMovementAction.Paste ->
                if (editable &&
                    !now.usd
                ) {
                    change(draft.value.copy(pesos = AmountDraft.paste(action.text)))
                }

            is InvestmentMovementAction.InvestmentPicked ->
                if (editable && !editing && now.investments.any { it.id == action.id }) {
                    val picked = now.investments.first { it.id == action.id }
                    // An amount typed in another currency is not carried over.
                    val next = if (picked.usd !=
                        now.usd
                    ) {
                        clearedAmount(draft.value)
                    } else {
                        draft.value
                    }
                    change(next.copy(investmentId = action.id))
                }

            InvestmentMovementAction.OpenDatePicker ->
                if (editable) progress.update { it.copy(datePickerOpen = true) }

            InvestmentMovementAction.CloseDatePicker ->
                progress.update { it.copy(datePickerOpen = false) }

            is InvestmentMovementAction.DatePicked -> {
                if (editable && InvestmentPolicy.acceptsDate(action.date, now.today)) {
                    change(draft.value.copy(date = action.date))
                }
                progress.update { it.copy(datePickerOpen = false) }
            }

            InvestmentMovementAction.OpenComposer -> if (editable) {
                saved[COMPOSER_TEXT] = draft.value.note
                saved[COMPOSER] = true
            }

            is InvestmentMovementAction.ComposerChanged ->
                if (saved.get<Boolean>(COMPOSER) == true) saved[COMPOSER_TEXT] = action.text

            // Over the limit nothing is taken: the composer stays open with the text in it.
            InvestmentMovementAction.ComposerDone -> composer.value?.let { text ->
                if (editable && noteProblem(text) == null) {
                    change(draft.value.copy(note = text))
                    closeComposer()
                }
            }

            InvestmentMovementAction.ComposerCancelled -> closeComposer()

            InvestmentMovementAction.Submit -> if (now.canSubmit) submit()

            InvestmentMovementAction.Retry -> if (!now.busy) {
                if (saved.get<String>(OPERATION) != null) {
                    viewModelScope.launch { recover() }
                } else {
                    progress.update { it.copy(error = null) }
                }
            }

            InvestmentMovementAction.Delete -> if (now.canDelete) delete()

            InvestmentMovementAction.Back -> when {
                now.busy || saved.get<String>(OPERATION) != null -> Unit
                typed(now) -> progress.update { it.copy(confirmDiscard = true) }
                else -> progress.update { it.copy(closed = true) }
            }

            InvestmentMovementAction.DiscardConfirmed ->
                progress.update { it.copy(confirmDiscard = false, closed = true) }

            InvestmentMovementAction.KeepEditing ->
                progress.update { it.copy(confirmDiscard = false) }
        }
    }

    private fun clearedAmount(current: Draft) =
        current.copy(pesos = AmountDraft.Empty, dollars = DecimalDraft(MAX_WHOLE))

    private fun change(next: Draft) {
        val current = draft.value
        if (next.pesos != current.pesos || next.dollars != current.dollars) {
            progress.update { it.copy(atLimit = false) }
        }
        saved[KIND] = next.kind.key
        saved[INVESTMENT] = next.investmentId
        saved[PESOS] = when (val amount = next.pesos) {
            AmountDraft.Empty -> null
            is AmountDraft.Digits -> amount.digits
            is AmountDraft.Rejected -> amount.original
        }
        saved[WHOLE] = next.dollars.whole
        saved[COMMA] = next.dollars.comma
        saved[CENTS] = next.dollars.cents
        saved[DATE] = next.date.toString()
        saved[NOTE] = next.note
        progress.update {
            it.copy(
                deleteBlockedBy = null,
                firstContribution = null,
                error = it.error.takeUnless { error -> error in Clearable }
            )
        }
        draft.value = next
    }

    /**
     * A digit typed in pesos. A recorded value may be 0 (rule 2), which the expense pad
     * never takes: a 0 typed first on Valor de hoy is that value, and the next digit
     * replaces it.
     */
    private fun pesoDigit(current: Draft, digit: Int): AmountDraft.Change {
        val pesos = current.pesos
        return when {
            current.kind == InvestmentMovementKind.Valuation && digit == 0 &&
                (pesos == AmountDraft.Empty || pesos == ZeroPesos) -> AmountDraft.Change(ZeroPesos)

            pesos == ZeroPesos -> AmountDraft.Empty.digit(digit)

            else -> pesos.digit(digit)
        }
    }

    private fun pesos(change: AmountDraft.Change) {
        change(draft.value.copy(pesos = change.draft))
        if (change.limitReached) pulse()
    }

    private fun dollars(change: DecimalDraft.Change) {
        change(draft.value.copy(dollars = change.draft))
        if (change.limitReached) pulse()
    }

    private fun pulse() = progress.update {
        it.copy(limitPulse = it.limitPulse + 1, atLimit = true)
    }

    private fun closeComposer() {
        saved[COMPOSER] = false
        saved[COMPOSER_TEXT] = null
    }

    private fun noteProblem(text: String): InvestmentNoteProblem? =
        when ((Note.of(text) as? Outcome.Failure)?.error) {
            null, TextError.Required -> null
            is TextError.TooLong -> InvestmentNoteProblem.TooLong
            TextError.ControlCharacter -> InvestmentNoteProblem.NotAllowed
        }

    private fun typed(state: InvestmentMovementState): Boolean = if (editing) {
        state.changed
    } else {
        val current = draft.value
        current.pesos != AmountDraft.Empty || !current.dollars.empty || current.note.isNotBlank()
    }

    /** The board to work from: the last one read, also after a failed refresh. */
    private fun readable(state: InvestmentBoardState): InvestmentBoard? = when (state) {
        InvestmentBoardState.Loading -> null
        is InvestmentBoardState.Ready -> state.board
        is InvestmentBoardState.Failed -> state.lastGood
    }

    private fun summary(board: InvestmentBoard?, id: String?): InvestmentSummary? =
        board?.investments?.firstOrNull { it.history.investment.id.value == id }

    /** The investment and the movement being corrected, as read. */
    private fun original(board: InvestmentBoard): Pair<InvestmentSummary, InvestmentMovement>? {
        val summary = summary(board, draft.value.investmentId) ?: return null
        val movement = summary.history.movements.firstOrNull { it.id.value == movementId }
            ?: return null
        return summary to movement
    }

    /** The investments largest value first, as the overview orders them; no value last. */
    private fun ordered(board: InvestmentBoard): List<InvestmentSummary> {
        val overview = (board.overview as? Outcome.Success)?.value
            ?: return board.investments.sortedBy { it.figures.value == null }
        val ids = overview.items.map { it.history.investment.id }
        return board.investments.sortedBy { summary ->
            ids.indexOf(summary.history.investment.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }

    /** The amount typed, in [currency], if it is one an investment may hold for [kind]. */
    private fun amount(draft: Draft, currency: Currency): InvestmentAmount? {
        val money = when (currency) {
            Currency.USD -> draft.dollars.hundredths()?.let { Money(it, Currency.USD) }
            else -> (draft.pesos as? AmountDraft.Digits)?.digits?.toLongOrNull()?.let(Money::cop)
        } ?: return null
        val amount = if (draft.kind == InvestmentMovementKind.Valuation) {
            InvestmentAmount.ofValue(money)
        } else {
            InvestmentAmount.of(money)
        }
        return (amount as? Outcome.Success)?.value
    }

    /** The draft as the pad wrote it, "+USD 250,5", read aloud as the exact amount. */
    private fun shown(
        draft: Draft,
        currency: Currency,
        amount: InvestmentAmount?
    ): MoneyTextValue? {
        amount ?: return null
        val written = money.format(amount.money)
        val digits = if (currency == Currency.USD) {
            val dollars = draft.dollars
            val whole = money.decimal(dollars.whole.ifEmpty { "0" }.toLong(), 0)
            if (dollars.comma) "$whole,${dollars.cents}" else whole
        } else {
            written.digits
        }
        return written.copy(digits = digits, sign = MoneySign.None)
    }

    /** The movement as it would be recorded, to check it against the history. */
    private fun candidate(
        draft: Draft,
        amount: InvestmentAmount,
        original: InvestmentMovement?
    ): InvestmentMovement {
        val note = (Note.of(draft.note) as? Outcome.Success)?.value
        return original?.copy(amount = amount, date = draft.date, note = note)
            ?: InvestmentMovement(
                CANDIDATE,
                draft.kind,
                amount,
                draft.date,
                Long.MAX_VALUE,
                note,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            )
    }

    private fun retiroOf(history: InvestmentHistory, id: InvestmentMovementId, available: Money?) =
        history.movements.firstOrNull { it.id == id }?.let {
            BlockingRetiro(
                money.format(it.amount.money),
                it.date,
                available?.let { value -> money.format(value) }
            )
        }

    private fun check(history: InvestmentHistory, movement: InvestmentMovement): Check {
        val ours = { id: InvestmentMovementId -> id == movement.id }
        return when (
            val error = (
                InvestmentPolicy.figures(
                    InvestmentPolicy.with(history, movement)
                ) as? Outcome.Failure
                )?.error
        ) {
            is InvestmentHistoryError.WithdrawalAboveValue -> if (ours(error.withdrawal)) {
                Check(over = error.available)
            } else {
                Check(blockedBy = retiroOf(history, error.withdrawal, error.available))
            }

            is InvestmentHistoryError.WithdrawalWithoutValue -> if (ours(error.withdrawal)) {
                Check(noValue = true)
            } else {
                Check(blockedBy = retiroOf(history, error.withdrawal, null))
            }

            is InvestmentHistoryError.BeforeFirstContribution ->
                Check(firstContribution = error.firstContribution)

            else -> Check()
        }
    }

    /** The fact under the chips, about the investment as recorded, or the retiro's date. */
    private fun fact(
        summary: InvestmentSummary,
        kind: InvestmentMovementKind,
        date: LocalDate,
        original: InvestmentMovement?
    ): InvestmentMovementFact? {
        val figures = summary.figures
        val name = summary.history.investment.name.value
        return when (kind) {
            InvestmentMovementKind.Contribution ->
                InvestmentMovementFact.Contributed(money.format(figures.contributed), name)

            InvestmentMovementKind.Dividend ->
                InvestmentMovementFact.Dividends(money.format(figures.dividends), name)

            InvestmentMovementKind.Valuation -> figures.value?.let {
                InvestmentMovementFact.LastValue(money.format(it.amount), it.valuedOn)
            } ?: InvestmentMovementFact.FirstValue

            InvestmentMovementKind.Withdrawal -> {
                // A correction is told what the investment is worth without itself.
                val history = original?.let { InvestmentPolicy.without(summary.history, it) }
                    ?: summary.history
                val upTo = history.copy(
                    movements = history.movements.filter {
                        !it.date.isAfter(date)
                    }
                )
                when (val worth = InvestmentPolicy.figures(upTo)) {
                    is Outcome.Success -> worth.value.value?.let {
                        InvestmentMovementFact.Worth(money.format(it.amount), it.valuedOn)
                    } ?: InvestmentMovementFact.NoValue

                    is Outcome.Failure -> null
                }
            }
        }
    }

    private fun render(
        draft: Draft,
        progress: Progress,
        board: InvestmentBoardState,
        today: LocalDate,
        writing: String?
    ): InvestmentMovementState {
        val read = readable(board)
        val offered = when {
            read == null -> emptyList()
            editing -> listOfNotNull(summary(read, draft.investmentId))
            else -> ordered(read)
        }
        val summary = summary(read, draft.investmentId)
        val original = read?.let(::original)?.second
        val currency = summary?.history?.investment?.currency ?: Currency.COP
        val amount = amount(draft, currency)
        val check = if (summary != null && amount != null) {
            check(summary.history, candidate(draft, amount, original))
        } else {
            Check()
        }
        val note = (Note.of(draft.note) as? Outcome.Success)?.value
        val fact = summary?.let { fact(it, draft.kind, draft.date, original) }
        val deleteBlocked = progress.deleteBlockedBy?.let { (id, available) ->
            summary?.let { retiroOf(it.history, id, available) }
        }
        return InvestmentMovementState(
            loading = board is InvestmentBoardState.Loading || (
                editing && read != null && original == null && saved.get<Boolean>(DRAFTED) != true
                ),
            loadFailed = board is InvestmentBoardState.Failed && read == null,
            editing = editing,
            kind = draft.kind,
            amount = shown(draft, currency, amount),
            amountText = (draft.pesos as? AmountDraft.Rejected)?.original
                ?.takeIf { currency == Currency.COP },
            limitPulse = progress.limitPulse,
            atLimit = progress.atLimit,
            investments = offered.map {
                InvestmentChoice(
                    id = it.history.investment.id.value,
                    name = it.history.investment.name.value,
                    iconKey = it.history.investment.icon.value,
                    usd = it.history.investment.currency == Currency.USD
                )
            },
            investmentId = summary?.history?.investment?.id?.value,
            fact = fact,
            overValue = check.over?.let { money.format(it) },
            noValue = check.noValue || fact == InvestmentMovementFact.NoValue,
            blockedBy = check.blockedBy ?: deleteBlocked,
            firstContribution = check.firstContribution ?: progress.firstContribution,
            date = draft.date,
            today = today,
            datePickerOpen = progress.datePickerOpen,
            note = draft.note,
            composerOpen = writing != null && !progress.closed,
            composerDraft = writing.orEmpty(),
            composerProblem = writing?.let(::noteProblem),
            composerLength = writing?.trim()?.let { it.codePointCount(0, it.length) } ?: 0,
            changed = original != null && (
                amount != original.amount || draft.date != original.date || note != original.note
                ),
            busy = progress.busy,
            error = progress.error,
            confirmDiscard = progress.confirmDiscard,
            closed = progress.closed
        )
    }

    private fun operation(step: String): OperationId {
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[OPERATION] = operation.value
        saved[STEP] = step
        return operation
    }

    private fun submit() {
        val read = readable(board.value) ?: return
        val current = draft.value
        val summary = summary(read, current.investmentId) ?: return
        val investment = summary.history.investment
        val amount = amount(current, investment.currency) ?: return
        val note = (Note.of(current.note) as? Outcome.Success)?.value
        val original = original(read)?.second
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            if (original != null) {
                val operation = operation(STEP_EDIT)
                settle(
                    STEP_EDIT,
                    commands.submit(
                        EditInvestmentMovement(
                            operation,
                            read.generation,
                            investment.id,
                            investment.version,
                            original.id,
                            original.version,
                            amount,
                            current.date,
                            note
                        )
                    )
                )
            } else {
                val operation = operation(current.kind.key)
                settle(
                    current.kind.key,
                    commands.submit(
                        RecordInvestmentMovement(
                            operation,
                            read.generation,
                            investment.id,
                            investment.version,
                            current.kind,
                            amount,
                            current.date,
                            note
                        )
                    )
                )
            }
        }
    }

    private fun delete() {
        val read = readable(board.value) ?: return
        val (summary, movement) = original(read) ?: return
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val operation = operation(STEP_DELETE)
            settle(
                STEP_DELETE,
                corrections.deleteInvestmentMovement(
                    DeleteInvestmentMovement(
                        operation,
                        read.generation,
                        summary.history.investment.id,
                        summary.history.investment.version,
                        movement.id,
                        movement.version
                    )
                )
            )
        }
    }

    private suspend fun settle(step: String, outcome: InvestmentCommandOutcome) {
        when (outcome) {
            // A deletion was already offered to undo by [corrections].
            is CommandOutcome.Committed ->
                committed(step, outcome.value.receipt, announced = true)

            is CommandOutcome.OutcomeUnknown -> recover()

            is CommandOutcome.Rejected -> {
                // Certain: nothing was written, so this attempt is over.
                saved[OPERATION] = null
                rejected(step, outcome.reason)
            }
        }
    }

    private fun rejected(step: String, reason: CommandRejection<InvestmentRejection>) {
        val deleting = step == STEP_DELETE
        val error = when (reason) {
            is CommandRejection.StaleGeneration -> InvestmentMovementError.DataChanged

            is CommandRejection.VersionConflict -> InvestmentMovementError.Changed

            CommandRejection.OperationConflict, is CommandRejection.StorageFailed -> null

            is CommandRejection.Specific -> {
                val specific = reason.reason
                when (specific) {
                    // A deletion names the retiro it would leave short. A new movement's own
                    // id is not known here: the investment changed, and the board read again
                    // says how.
                    is InvestmentRejection.AboveValue -> if (deleting) {
                        return blocked(specific.withdrawal, specific.available)
                    }

                    is InvestmentRejection.NoValue ->
                        if (deleting) return blocked(specific.withdrawal, null)

                    is InvestmentRejection.BeforeFirstContribution -> {
                        progress.update {
                            it.copy(busy = false, firstContribution = specific.firstContribution)
                        }
                        return
                    }

                    else -> Unit
                }
                when (specific) {
                    is InvestmentRejection.FutureDate, InvestmentRejection.InvalidDate ->
                        InvestmentMovementError.FutureDate

                    InvestmentRejection.InvestmentMissing, InvestmentRejection.MovementMissing ->
                        InvestmentMovementError.Gone

                    is InvestmentRejection.AboveValue, is InvestmentRejection.NoValue ->
                        InvestmentMovementError.Changed

                    InvestmentRejection.CurrencyMismatch -> InvestmentMovementError.CurrencyMismatch

                    InvestmentRejection.Unchanged -> InvestmentMovementError.Unchanged

                    InvestmentRejection.LastContribution -> InvestmentMovementError.LastContribution

                    else -> null
                }
            }
        } ?: if (deleting) {
            InvestmentMovementError.NotDeleted
        } else {
            InvestmentMovementError.NotSaved
        }
        progress.update { it.copy(busy = false, error = error) }
    }

    private fun blocked(withdrawal: InvestmentMovementId, available: Money?) =
        progress.update { it.copy(busy = false, deleteBlockedBy = withdrawal to available) }

    /** Asks how the saved operation ended, with its own identifier. Only a receipt counts. */
    private suspend fun recover() {
        val operation =
            (saved.get<String>(OPERATION)?.let(OperationId::of) as? Outcome.Success)?.value
                ?: return
        val step = saved[STEP] ?: InvestmentMovementKind.Contribution.key
        progress.update { it.copy(busy = true, error = null) }
        when (val found = commands.find(operation)) {
            is Outcome.Failure -> progress.update {
                it.copy(busy = false, error = InvestmentMovementError.Unknown)
            }

            is Outcome.Success -> {
                val committed = found.value
                if (committed != null) {
                    committed(step, committed.receipt, announced = false)
                } else {
                    saved[OPERATION] = null
                    progress.update {
                        it.copy(
                            busy = false,
                            error = if (step == STEP_DELETE) {
                                InvestmentMovementError.NotDeleted
                            } else {
                                InvestmentMovementError.NotSaved
                            }
                        )
                    }
                }
            }
        }
    }

    private fun committed(step: String, receipt: OperationReceipt, announced: Boolean) {
        saved[OPERATION] = null
        when (step) {
            STEP_DELETE -> if (!announced) corrections.investmentMovementDeleted(receipt)

            STEP_EDIT -> corrections.investmentMovementUpdated()

            else -> {
                val current = draft.value
                val currency = summary(readable(board.value), current.investmentId)
                    ?.history?.investment?.currency
                val amount = currency?.let { amount(current, it) }
                corrections.investmentMovementRecorded(
                    CorrectionNotice.InvestmentMovementRecorded(
                        kind = kindOf(step) ?: current.kind,
                        amount = amount?.let { money.format(it.money) }
                    )
                )
            }
        }
        progress.update { it.copy(busy = false, error = null, closed = true) }
    }

    companion object {
        /** A value of 0 pesos, as typed on Valor de hoy (rule 2). */
        private val ZeroPesos = AmountDraft.Digits("0")
        private const val ZERO = "0"

        /** Route arguments: the investment chosen, the movement's kind, the movement to correct. */
        const val KEY_INVESTMENT = "investmentId"
        const val KEY_KIND = "kind"
        const val KEY_MOVEMENT = "movementId"

        /** [KEY_KIND]'s values. */
        const val KIND_CONTRIBUTION = "contribution"
        const val KIND_VALUATION = "valuation"
        const val KIND_WITHDRAWAL = "withdrawal"
        const val KIND_DIVIDEND = "dividend"

        /** USD 9.999.999.999,99 is ten whole digits (rule 2, IN34). */
        private const val MAX_WHOLE = 10

        /** Stands for a new movement while it is checked; never sent. */
        private val CANDIDATE =
            (
                InvestmentMovementId.of(
                    "00000000-0000-4000-8000-ffffffffffff"
                ) as Outcome.Success
                ).value

        /** Errors a change to the draft answers: the next attempt may differ. */
        private val Clearable = setOf(
            InvestmentMovementError.NotSaved,
            InvestmentMovementError.NotDeleted,
            InvestmentMovementError.FutureDate,
            InvestmentMovementError.Unchanged,
            InvestmentMovementError.CurrencyMismatch,
            InvestmentMovementError.LastContribution
        )

        private val InvestmentMovementKind.key: String
            get() = when (this) {
                InvestmentMovementKind.Contribution -> KIND_CONTRIBUTION
                InvestmentMovementKind.Valuation -> KIND_VALUATION
                InvestmentMovementKind.Withdrawal -> KIND_WITHDRAWAL
                InvestmentMovementKind.Dividend -> KIND_DIVIDEND
            }

        private fun kindOf(key: String): InvestmentMovementKind? =
            InvestmentMovementKind.entries.firstOrNull { it.key == key }

        private const val KIND = "investment.movement.kind"
        private const val INVESTMENT = "investment.movement.investment"
        private const val PESOS = "investment.movement.pesos"
        private const val WHOLE = "investment.movement.whole"
        private const val COMMA = "investment.movement.comma"
        private const val CENTS = "investment.movement.cents"
        private const val DATE = "investment.movement.date"
        private const val NOTE = "investment.movement.note"
        private const val DRAFTED = "investment.movement.drafted"
        private const val COMPOSER = "investment.movement.composer"
        private const val COMPOSER_TEXT = "investment.movement.composer.text"
        private const val OPERATION = "investment.movement.operation"
        private const val STEP = "investment.movement.step"
        private const val STEP_EDIT = "edit"
        private const val STEP_DELETE = "delete"
    }
}
