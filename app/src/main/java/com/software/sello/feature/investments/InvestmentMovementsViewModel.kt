package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.DecimalDraft
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentMovementLine
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RecordInvestmentMovements
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.movement.SavedNoteComposer
import com.software.sello.presentation.submission.LocalDateSerializer
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import com.software.sello.presentation.submission.typed
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Varios movimientos (SELLO-134): several aportes, values, retiros and dividends of one
 * investment on one receipt, saved with SELLO-129's [RecordInvestmentMovements] as one
 * operation, or not at all. Each line has its own type, amount, date and note; a new
 * line copies the type and date of the one above. Lines without an amount are left out.
 * The lines, the highlighted one and the operation in flight live in [saved], so a
 * restored receipt opens as it was left and only asks about an operation it had sent,
 * never sending it again. A line storage refused is marked with its reason and the
 * draft stays; an investment that changed meanwhile is read again and sent again only
 * when asked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentMovementsViewModel(
    private val saved: SavedStateHandle,
    reads: InvestmentReads,
    private val commands: InvestmentCommands,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: Corrections
) : ViewModel() {
    private data class Line(
        val key: String,
        val kind: InvestmentMovementKind?,
        val pesos: AmountDraft,
        val dollars: DecimalDraft,
        val date: LocalDate,
        val note: String
    )

    private data class Draft(val lines: List<Line>, val highlighted: Int, val nextKey: Long) {
        val current: Line get() = lines[highlighted]
    }

    /** [Line] as text: pesos as typed, dollars digit by digit. */
    @Serializable
    private data class SavedLine(
        val key: String,
        val kind: String?,
        val pesos: String?,
        val whole: String,
        val comma: Boolean,
        val cents: String,
        @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
        val note: String
    )

    @Serializable
    private data class Saved(val lines: List<SavedLine>, val highlighted: Int, val nextKey: Long)

    private data class Removed(val index: Int, val line: Line)

    private data class Progress(
        val busy: Boolean = false,
        val error: InvestmentMovementsError? = null,
        val closed: Boolean = false,
        val confirmDiscard: Boolean = false,
        val datePickerOpen: Boolean = false,
        val refusals: Map<String, MovementLineRefusal> = emptyMap(),
        val removed: Removed? = null
    )

    private val investmentId: String =
        checkNotNull(saved[KEY_INVESTMENT]) { "Varios movimientos needs its investment" }

    private val drafts = SavedDraft(saved, DRAFT, Saved.serializer())
    private val submission = Submission(saved, OPERATION, ids)

    private val draft = MutableStateFlow(restored() ?: opening())
    private val progress = MutableStateFlow(Progress())
    private val attempts = MutableStateFlow(0)
    private val board = attempts.flatMapLatest { reads.observeBoard() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadState.Loading)
    private val composer = SavedNoteComposer(saved, PREFIX, viewModelScope)

    val state: StateFlow<InvestmentMovementsState> =
        combine(draft, progress, board, clock.today, composer.text) {
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
            render(draft.value, progress.value, board.value, clock.today.value.date, composer.now())
        )

    init {
        if (submission.pending) viewModelScope.launch { recover() }
    }

    fun onAction(action: InvestmentMovementsAction) {
        val now = state.value
        val editable = now.editable
        val current = draft.value
        when (action) {
            is InvestmentMovementsAction.Select ->
                if (editable && action.index in current.lines.indices) {
                    change(current.copy(highlighted = action.index))
                }

            InvestmentMovementsAction.AddLine -> if (now.canAdd) {
                val above = current.lines.last()
                val line = blank(current.nextKey, above.kind, above.date)
                change(
                    current.copy(
                        lines = current.lines + line,
                        highlighted = current.lines.size,
                        nextKey = current.nextKey + 1
                    )
                )
            }

            is InvestmentMovementsAction.Remove ->
                if (editable && current.lines.size > 1 && action.index in current.lines.indices) {
                    remove(current, action.index)
                }

            InvestmentMovementsAction.UndoRemove -> {
                val removed = progress.value.removed
                if (editable && removed != null && !now.full) {
                    val at = removed.index.coerceAtMost(current.lines.size)
                    val lines = current.lines.toMutableList().apply { add(at, removed.line) }
                    progress.update { it.copy(removed = null) }
                    change(current.copy(lines = lines, highlighted = at))
                }
            }

            InvestmentMovementsAction.RemovalShown -> progress.update { it.copy(removed = null) }

            is InvestmentMovementsAction.Kind ->
                if (editable) line(current) { it.copy(kind = action.kind) }

            is InvestmentMovementsAction.Digit -> if (editable) {
                if (now.usd) {
                    line(current) { it.copy(dollars = it.dollars.digit(action.digit).draft) }
                } else {
                    line(current) { it.copy(pesos = pesoDigit(it, action.digit)) }
                }
            }

            InvestmentMovementsAction.TripleZero -> if (editable && !now.usd) {
                line(current) {
                    if (it.pesos == ZeroPesos) it else it.copy(pesos = it.pesos.tripleZero().draft)
                }
            }

            InvestmentMovementsAction.Comma ->
                if (editable &&
                    now.usd
                ) {
                    line(current) { it.copy(dollars = it.dollars.comma().draft) }
                }

            InvestmentMovementsAction.Backspace -> if (editable) {
                line(current) {
                    if (now.usd) {
                        it.copy(dollars = it.dollars.backspace())
                    } else {
                        it.copy(pesos = it.pesos.backspace())
                    }
                }
            }

            InvestmentMovementsAction.ClearAmount -> if (editable) {
                line(current) {
                    it.copy(pesos = AmountDraft.Empty, dollars = DecimalDraft(MAX_WHOLE))
                }
            }

            InvestmentMovementsAction.OpenDatePicker ->
                if (editable) progress.update { it.copy(datePickerOpen = true) }

            InvestmentMovementsAction.CloseDatePicker ->
                progress.update { it.copy(datePickerOpen = false) }

            is InvestmentMovementsAction.DatePicked -> {
                if (editable && InvestmentPolicy.acceptsDate(action.date, now.today)) {
                    line(current) { it.copy(date = action.date) }
                }
                progress.update { it.copy(datePickerOpen = false) }
            }

            InvestmentMovementsAction.OpenComposer ->
                if (editable) composer.open(current.current.note)

            is InvestmentMovementsAction.ComposerChanged -> composer.write(action.text)

            // Over the limit nothing is taken: the composer stays open with the text in it.
            InvestmentMovementsAction.ComposerDone -> composer.text.value?.let { text ->
                if (editable && SavedNoteComposer.problem(text) == null) {
                    line(current) { it.copy(note = text) }
                    composer.close()
                }
            }

            InvestmentMovementsAction.ComposerCancelled -> composer.close()

            InvestmentMovementsAction.Submit -> if (now.canSubmit) submit()

            InvestmentMovementsAction.Retry -> if (!now.busy) {
                when {
                    submission.pending -> viewModelScope.launch { recover() }

                    // Reintentar after a failed first read reads the board again.
                    now.loadFailed -> attempts.update { it + 1 }

                    else -> progress.update { it.copy(error = null) }
                }
            }

            InvestmentMovementsAction.Back -> when {
                now.busy || submission.pending -> Unit
                written(current) -> progress.update { it.copy(confirmDiscard = true) }
                else -> progress.update { it.copy(closed = true) }
            }

            InvestmentMovementsAction.DiscardConfirmed -> {
                drafts.clear()
                progress.update { it.copy(confirmDiscard = false, closed = true) }
            }

            InvestmentMovementsAction.KeepEditing ->
                progress.update { it.copy(confirmDiscard = false) }
        }
    }

    /**
     * The first draft: what was typed in Anotar movimiento becomes line one and a second
     * line, of the same type and date, waits for the next amount; otherwise one line, of the
     * type it was opened from, if any. A hint that is not valid is dropped, not shown as an
     * error the person did not make.
     */
    private fun opening(): Draft {
        val today = clock.today.value.date
        val kind = saved.get<String>(KEY_PREFILL_KIND)?.let(::kindOf)
        val note = saved.get<String>(KEY_PREFILL_NOTE)
            ?.takeIf { Note.of(it) is Outcome.Success }.orEmpty()
        val date = saved.get<String>(KEY_PREFILL_DATE)?.let(::parseDate)
            ?.takeIf { InvestmentPolicy.acceptsDate(it, today) } ?: today
        val first = prefilled(blank(0, kind, date), kind)
        return if (first != null) {
            Draft(
                lines = listOf(first.copy(note = note), blank(1, kind, date)),
                highlighted = 1,
                nextKey = 2
            )
        } else {
            Draft(listOf(blank(0, kind, date).copy(note = note)), highlighted = 0, nextKey = 1)
        }
    }

    /**
     * [line] holding the hinted amount, in the currency it was typed in, when it is one an
     * investment may hold for [kind]; null when there is none.
     */
    private fun prefilled(line: Line, kind: InvestmentMovementKind?): Line? {
        val units = saved.get<String>(KEY_PREFILL_AMOUNT)?.toLongOrNull() ?: return null
        val usd = saved.get<Boolean>(KEY_PREFILL_USD) == true
        val typed = Money(units, if (usd) Currency.USD else Currency.COP)
        val amount = if (kind == InvestmentMovementKind.Valuation) {
            InvestmentAmount.ofValue(typed)
        } else {
            InvestmentAmount.of(typed)
        }
        if (amount !is Outcome.Success) return null
        return when {
            usd -> line.copy(dollars = DecimalDraft.of(units, MAX_WHOLE))
            units == 0L -> line.copy(pesos = ZeroPesos)
            else -> line.copy(pesos = AmountDraft.Digits(units.toString()))
        }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun blank(key: Long, kind: InvestmentMovementKind?, date: LocalDate) = Line(
        key = key.toString(),
        kind = kind,
        pesos = AmountDraft.Empty,
        dollars = DecimalDraft(MAX_WHOLE),
        date = date,
        note = ""
    )

    /** The saved receipt, when it is one this screen can hold. */
    private fun restored(): Draft? {
        val stored = drafts.read() ?: return null
        if (stored.lines.size !in 1..InvestmentMovementsState.MAX_LINES) return null
        if (stored.highlighted !in stored.lines.indices) return null
        return Draft(
            lines = stored.lines.map {
                Line(
                    key = it.key,
                    kind = it.kind?.let(::kindOf),
                    pesos = it.pesos?.let { pesos ->
                        if (pesos == ZERO) ZeroPesos else AmountDraft.paste(pesos)
                    } ?: AmountDraft.Empty,
                    dollars = DecimalDraft(MAX_WHOLE, it.whole, it.comma, it.cents),
                    date = it.date,
                    note = it.note
                )
            },
            highlighted = stored.highlighted,
            nextKey = stored.nextKey
        )
    }

    private fun change(next: Draft) {
        drafts.write(
            Saved(
                lines = next.lines.map {
                    SavedLine(
                        key = it.key,
                        kind = it.kind?.key,
                        pesos = it.pesos.typed(),
                        whole = it.dollars.whole,
                        comma = it.dollars.comma,
                        cents = it.dollars.cents,
                        date = it.date,
                        note = it.note
                    )
                },
                highlighted = next.highlighted,
                nextKey = next.nextKey
            )
        )
        val edited = next.lines != draft.value.lines
        // A refusal answers the receipt as it was sent: any change may answer it.
        if (edited) {
            progress.update {
                it.copy(refusals = emptyMap(), error = it.error.takeUnless { e -> e in Answered })
            }
        }
        draft.value = next
    }

    /** Changes the highlighted line. */
    private fun line(current: Draft, edit: (Line) -> Line) {
        val lines = current.lines.toMutableList()
        lines[current.highlighted] = edit(current.current)
        change(current.copy(lines = lines))
    }

    private fun remove(current: Draft, index: Int) {
        val lines = current.lines.toMutableList()
        val line = lines.removeAt(index)
        val highlighted = when {
            index < current.highlighted -> current.highlighted - 1
            else -> current.highlighted.coerceAtMost(lines.lastIndex)
        }
        progress.update { it.copy(removed = Removed(index, line)) }
        change(current.copy(lines = lines, highlighted = highlighted))
    }

    /**
     * A digit typed in pesos. A recorded value may be 0 (rule 2), which the expense pad
     * never takes: a 0 typed first on Valor de hoy is that value, and the next digit
     * replaces it.
     */
    private fun pesoDigit(line: Line, digit: Int): AmountDraft {
        val pesos = line.pesos
        return when {
            line.kind == InvestmentMovementKind.Valuation && digit == 0 &&
                (pesos == AmountDraft.Empty || pesos == ZeroPesos) -> ZeroPesos

            pesos == ZeroPesos -> AmountDraft.Empty.digit(digit).draft

            else -> pesos.digit(digit).draft
        }
    }

    /** Anything on the receipt worth asking about before it is thrown away. */
    private fun written(current: Draft) = current.lines.any {
        it.pesos != AmountDraft.Empty || !it.dollars.empty || it.kind != null ||
            it.note.isNotBlank()
    }

    /** The board to work from: the last one read, also after a failed refresh. */
    private fun readable(state: InvestmentBoardState): InvestmentBoard? = when (state) {
        ReadState.Loading -> null
        is ReadState.Ready -> state.value
        is ReadState.Failed -> state.lastGood
    }

    private fun summary(board: InvestmentBoard?): InvestmentSummary? =
        board?.investments?.firstOrNull { it.history.investment.id.value == investmentId }

    /** What was typed on [line], in [currency], whatever its type; null while nothing is. */
    private fun typedMoney(line: Line, currency: Currency): Money? = when (currency) {
        Currency.USD -> line.dollars.takeUnless { it.empty }?.hundredths()?.let {
            Money(it, Currency.USD)
        }

        else -> (line.pesos as? AmountDraft.Digits)?.digits?.toLongOrNull()?.let(Money::cop)
    }

    /** The amount [line] records, if its type may hold what was typed. */
    private fun amount(line: Line, currency: Currency): InvestmentAmount? {
        val kind = line.kind ?: return null
        val typed = typedMoney(line, currency) ?: return null
        val amount = if (kind == InvestmentMovementKind.Valuation) {
            InvestmentAmount.ofValue(typed)
        } else {
            InvestmentAmount.of(typed)
        }
        return (amount as? Outcome.Success)?.value
    }

    /** The highlighted line as the pad writes it, "250,5"; the others as the app writes money. */
    private fun shown(line: Line, currency: Currency, highlighted: Boolean): MoneyTextValue? {
        val typed = typedMoney(line, currency) ?: return null
        val written = money.format(typed)
        if (currency != Currency.USD || !highlighted) return written
        val dollars = line.dollars
        val whole = money.decimal(dollars.whole.ifEmpty { "0" }.toLong(), 0)
        return written.copy(
            digits = if (dollars.comma) "$whole,${dollars.cents}" else whole,
            sign = MoneySign.None
        )
    }

    /** Aportes − retiros of the lines that may be recorded. */
    private fun contributed(lines: List<Line>, currency: Currency): MoneyTextValue? {
        var total = ExactTotal.zero(currency)
        lines.forEach { line ->
            val amount = amount(line, currency)?.money ?: return@forEach
            total = when (line.kind) {
                InvestmentMovementKind.Contribution -> total + amount
                InvestmentMovementKind.Withdrawal -> total - amount
                else -> total
            }
        }
        return (total.toMoney() as? Outcome.Success)?.value?.let(money::format)
    }

    private fun render(
        draft: Draft,
        progress: Progress,
        board: InvestmentBoardState,
        today: LocalDate,
        writing: String?
    ): InvestmentMovementsState {
        val read = readable(board)
        val summary = summary(read)
        val currency = summary?.history?.investment?.currency ?: Currency.COP
        val lines = draft.lines.mapIndexed { index, line ->
            MovementLineModel(
                key = line.key,
                kind = line.kind,
                amount = shown(line, currency, index == draft.highlighted),
                valid = amount(line, currency) != null,
                date = line.date,
                note = line.note,
                refusal = progress.refusals[line.key]
            )
        }
        return InvestmentMovementsState(
            loading = board is ReadState.Loading,
            loadFailed = board is ReadState.Failed && read == null,
            stale = board is ReadState.Failed && read != null,
            name = summary?.history?.investment?.name?.value.orEmpty(),
            usd = currency == Currency.USD,
            lines = lines,
            highlighted = draft.highlighted,
            contributed = contributed(draft.lines, currency),
            today = today,
            datePickerOpen = progress.datePickerOpen,
            composerOpen = writing != null && !progress.closed,
            composerDraft = writing.orEmpty(),
            composerProblem = writing?.let(SavedNoteComposer::problem),
            composerLength = SavedNoteComposer.length(writing),
            removed = progress.removed?.line?.let { removed ->
                MovementLineModel(
                    key = removed.key,
                    kind = removed.kind,
                    amount = shown(removed, currency, highlighted = false),
                    valid = amount(removed, currency) != null,
                    date = removed.date,
                    note = removed.note
                )
            },
            busy = progress.busy,
            error = progress.error ?: InvestmentMovementsError.Gone.takeIf {
                read != null && summary == null && !progress.closed
            },
            confirmDiscard = progress.confirmDiscard,
            closed = progress.closed
        )
    }

    private fun submit() {
        val read = readable(board.value) ?: return
        val summary = summary(read) ?: return
        val investment = summary.history.investment
        val sending = draft.value.lines.filter { typedMoney(it, investment.currency) != null }
        val lines = sending.map { line ->
            InvestmentMovementLine(
                kind = line.kind ?: return,
                amount = amount(line, investment.currency) ?: return,
                date = line.date,
                note = (Note.of(line.note) as? Outcome.Success)?.value
            )
        }
        val keys = sending.map { it.key }
        progress.update { it.copy(busy = true, error = null, refusals = emptyMap()) }
        viewModelScope.launch {
            settle(
                submission.send(commands::find, STEP) { operation ->
                    commands.submit(
                        RecordInvestmentMovements(
                            operation,
                            read.generation,
                            investment.id,
                            investment.version,
                            lines
                        )
                    )
                },
                keys,
                summary.history
            )
        }
    }

    /** Asks how the saved operation ended, with its own identifier. Only a receipt counts. */
    private suspend fun recover() {
        if (!submission.pending) return
        progress.update { it.copy(busy = true, error = null) }
        settle(submission.recover(commands::find), emptyList(), null)
    }

    private fun settle(
        settled: Settled<CommittedInvestment, InvestmentRejection>?,
        keys: List<String>,
        history: InvestmentHistory?
    ) {
        when (settled) {
            // Something is still in flight and will settle; or nothing was left to ask about.
            null -> if (!submission.pending) progress.update { it.copy(busy = false) }

            is Settled.Committed -> committed()

            is Settled.Unknown -> progress.update {
                it.copy(busy = false, error = InvestmentMovementsError.Unknown)
            }

            Settled.NotCommitted -> progress.update {
                it.copy(busy = false, error = InvestmentMovementsError.NotSaved)
            }

            is Settled.Rejected -> rejected(settled.reason, keys, history)
        }
    }

    private fun rejected(
        reason: CommandRejection<InvestmentRejection>,
        keys: List<String>,
        history: InvestmentHistory?
    ) {
        val specific = (reason as? CommandRejection.Specific)?.reason
        if (specific is InvestmentRejection.LinesRefused && history != null) {
            val refusals = specific.lines.mapNotNull { refused ->
                keys.getOrNull(refused.position)?.let { it to refusal(refused.reason, history) }
            }.toMap()
            val first = draft.value.lines.indexOfFirst { it.key in refusals }
            progress.update {
                it.copy(
                    busy = false,
                    error = InvestmentMovementsError.NotSaved,
                    refusals = refusals
                )
            }
            // The first refused line is the one the pad and chips work on.
            if (first >= 0) change(draft.value.copy(highlighted = first))
            return
        }
        val error = when (reason) {
            is CommandRejection.StaleGeneration -> InvestmentMovementsError.DataChanged

            is CommandRejection.VersionConflict -> InvestmentMovementsError.Changed

            else -> if (specific == InvestmentRejection.InvestmentMissing) {
                InvestmentMovementsError.Gone
            } else {
                InvestmentMovementsError.NotSaved
            }
        }
        progress.update { it.copy(busy = false, error = error) }
    }

    /** A line's refusal in the screen's words; a recorded retiro it leaves short is named. */
    private fun refusal(
        reason: InvestmentRejection,
        history: InvestmentHistory
    ): MovementLineRefusal {
        fun recorded(id: InvestmentMovementId) = history.movements.firstOrNull { it.id == id }
        return when (reason) {
            is InvestmentRejection.AboveValue -> recorded(reason.withdrawal)?.let {
                MovementLineRefusal.LeavesRetiroShort(money.format(it.amount.money), it.date)
            } ?: MovementLineRefusal.OverValue(money.format(reason.available))

            is InvestmentRejection.NoValue -> recorded(reason.withdrawal)?.let {
                MovementLineRefusal.LeavesRetiroShort(money.format(it.amount.money), it.date)
            } ?: MovementLineRefusal.NoValue

            is InvestmentRejection.BeforeFirstContribution ->
                MovementLineRefusal.BeforeFirstContribution(reason.firstContribution)

            is InvestmentRejection.FutureDate, InvestmentRejection.InvalidDate ->
                MovementLineRefusal.FutureDate

            InvestmentRejection.InvalidAmount, InvestmentRejection.CurrencyMismatch ->
                MovementLineRefusal.InvalidAmount

            else -> MovementLineRefusal.Other
        }
    }

    /**
     * The receipt was saved. It may be answered before the board is read (a restored
     * receipt), so the lines sent are counted by what was typed, in either currency.
     */
    private fun committed() {
        val summary = summary(readable(board.value))
        corrections.investmentMovementsRecorded(
            count = draft.value.lines.count { it.pesos != AmountDraft.Empty || !it.dollars.empty },
            investmentName = summary?.history?.investment?.name?.value
        )
        drafts.clear()
        progress.update { it.copy(busy = false, error = null, closed = true) }
    }

    companion object {
        /** A value of 0 pesos, as typed on Valor de hoy (rule 2). */
        private val ZeroPesos = AmountDraft.Digits("0")

        /** The errors a change to the receipt answers. */
        private val Answered =
            setOf(InvestmentMovementsError.NotSaved, InvestmentMovementsError.Changed)
        private const val ZERO = "0"

        /** The route argument: the investment the receipt is for. */
        const val KEY_INVESTMENT = "investmentId"

        /**
         * Route hints: what Anotar movimiento had typed, checked like any input. The amount
         * is in minor units, cents when [KEY_PREFILL_USD] is true.
         */
        const val KEY_PREFILL_KIND = "kind"
        const val KEY_PREFILL_AMOUNT = "amount"
        const val KEY_PREFILL_USD = "usd"
        const val KEY_PREFILL_NOTE = "note"
        const val KEY_PREFILL_DATE = "date"

        /** USD 9.999.999.999,99 is ten whole digits (rule 2, IN34). */
        private const val MAX_WHOLE = 10

        private val InvestmentMovementKind.key: String
            get() = when (this) {
                InvestmentMovementKind.Contribution -> "contribution"
                InvestmentMovementKind.Valuation -> "valuation"
                InvestmentMovementKind.Withdrawal -> "withdrawal"
                InvestmentMovementKind.Dividend -> "dividend"
            }

        private fun kindOf(key: String): InvestmentMovementKind? =
            InvestmentMovementKind.entries.firstOrNull { it.key == key }

        private const val DRAFT = "investment.movements.draft"
        private const val PREFIX = "investment.movements"
        private const val OPERATION = "investment.movements.operation"
        private const val STEP = "record"
    }
}
