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
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.DecimalDraft
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GuidancePreferences
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
 * Varios movimientos (SELLO-134, SELLO-142): aportes, values, retiros and dividends of one
 * or several investments on one receipt, saved with [RecordInvestmentMovements] (SELLO-129,
 * SELLO-140) as one operation, or not at all. Each line has its own investment, type,
 * amount (in that investment's currency), date and note; a new line copies the investment,
 * type and date of the one above. Lines without an amount are left out. Opened from the
 * Inversiones list, no investment is chosen.
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
    private val corrections: Corrections,
    private val guidance: GuidancePreferences
) : ViewModel() {
    /**
     * [usd] is the currency the line is typed in: its investment's as last read, kept so a
     * line whose investment is deleted meanwhile still shows what was typed.
     */
    private data class Line(
        val key: String,
        val investment: String?,
        val usd: Boolean,
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
        val note: String,
        val investment: String?,
        val usd: Boolean
    )

    @Serializable
    private data class Saved(val lines: List<SavedLine>, val highlighted: Int, val nextKey: Long)

    /**
     * A line as a receipt saved before SELLO-142 kept it, with no investment of its own: it
     * restores into the investment the receipt was opened from. The saved draft is read in
     * order, so such a receipt never reads as a [Saved] (its lines are two fields short).
     */
    @Serializable
    private data class SavedLineBefore142(
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
    private data class SavedBefore142(
        val lines: List<SavedLineBefore142>,
        val highlighted: Int,
        val nextKey: Long
    )

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

    /** The investment it was opened from; null from the Inversiones list. */
    private val investmentId: String? = saved[KEY_INVESTMENT]

    private val drafts = SavedDraft(saved, DRAFT, Saved.serializer())
    private val draftsBefore142 = SavedDraft(saved, DRAFT, SavedBefore142.serializer())
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
                    .copy(investment = above.investment, usd = above.usd)
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

            is InvestmentMovementsAction.InvestmentPicked -> {
                val picked = summary(readable(board.value), action.id)
                if (editable && picked != null) {
                    val usd = picked.history.investment.currency == Currency.USD
                    line(current) {
                        // Another currency: what was typed meant other money, so it goes.
                        if (usd == usdOf(it)) {
                            it.copy(investment = action.id, usd = usd)
                        } else {
                            it.copy(
                                investment = action.id,
                                usd = usd,
                                pesos = AmountDraft.Empty,
                                dollars = DecimalDraft(MAX_WHOLE)
                            )
                        }
                    }
                }
            }

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
     * line, of the same investment, type and date, waits for the next amount; otherwise one
     * line, of the investment and type it was opened from, if any. A hint that is not valid
     * is dropped, not shown as an error the person did not make.
     */
    private fun opening(): Draft {
        val today = clock.today.value.date
        val kind = saved.get<String>(KEY_PREFILL_KIND)?.let(::kindOf)
        val note = saved.get<String>(KEY_PREFILL_NOTE)
            ?.takeIf { Note.of(it) is Outcome.Success }.orEmpty()
        val date = saved.get<String>(KEY_PREFILL_DATE)?.let(::parseDate)
            ?.takeIf { InvestmentPolicy.acceptsDate(it, today) } ?: today
        val usd = saved.get<Boolean>(KEY_PREFILL_USD) == true
        fun opened(key: Long) = blank(key, kind, date).copy(investment = investmentId, usd = usd)
        val first = prefilled(opened(0), kind)
        return if (first != null) {
            Draft(
                lines = listOf(first.copy(note = note), opened(1)),
                highlighted = 1,
                nextKey = 2
            )
        } else {
            Draft(listOf(opened(0).copy(note = note)), highlighted = 0, nextKey = 1)
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
        investment = null,
        usd = false,
        kind = kind,
        pesos = AmountDraft.Empty,
        dollars = DecimalDraft(MAX_WHOLE),
        date = date,
        note = ""
    )

    /** The saved receipt, when it is one this screen can hold. */
    private fun restored(): Draft? {
        val stored = drafts.read() ?: draftsBefore142.read()?.let { before ->
            Saved(
                lines = before.lines.map {
                    SavedLine(
                        it.key,
                        it.kind,
                        it.pesos,
                        it.whole,
                        it.comma,
                        it.cents,
                        it.date,
                        it.note,
                        investment = investmentId,
                        usd = it.whole.isNotEmpty() || it.comma
                    )
                },
                highlighted = before.highlighted,
                nextKey = before.nextKey
            )
        } ?: return null
        if (stored.lines.size !in 1..InvestmentMovementsState.MAX_LINES) return null
        if (stored.highlighted !in stored.lines.indices) return null
        return Draft(
            lines = stored.lines.map {
                Line(
                    key = it.key,
                    investment = it.investment,
                    usd = it.usd,
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

    private fun change(changed: Draft) {
        // Each line keeps the currency of its investment as read, for when it is deleted.
        val next = changed.copy(lines = changed.lines.map { it.copy(usd = usdOf(it)) })
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
                        note = it.note,
                        investment = it.investment,
                        usd = it.usd
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

    /**
     * Anything on the receipt worth asking about before it is thrown away; the investment
     * it was opened from is not.
     */
    private fun written(current: Draft) = current.lines.any {
        it.pesos != AmountDraft.Empty || !it.dollars.empty || it.kind != null ||
            it.note.isNotBlank() || (it.investment != null && it.investment != investmentId)
    }

    /** The board to work from: the last one read, also after a failed refresh. */
    private fun readable(state: InvestmentBoardState): InvestmentBoard? = when (state) {
        ReadState.Loading -> null
        is ReadState.Ready -> state.value
        is ReadState.Failed -> state.lastGood
    }

    private fun summary(board: InvestmentBoard?, id: String?): InvestmentSummary? =
        id?.let { board?.investments?.firstOrNull { it.history.investment.id.value == id } }

    /** The line's investment as last read, when there is one. */
    private fun summary(board: InvestmentBoard?, line: Line) = summary(board, line.investment)

    /** [line] is typed in dollars: its investment's currency as read, or as last kept. */
    private fun usdOf(line: Line, board: InvestmentBoard? = readable(this.board.value)) =
        summary(board, line)?.let { it.history.investment.currency == Currency.USD } ?: line.usd

    private fun currencyOf(line: Line, board: InvestmentBoard?) =
        if (usdOf(line, board)) Currency.USD else Currency.COP

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

    /**
     * Aportes − retiros of the lines that may be recorded, each in its own currency; a line
     * with no investment is left out. With COP and USD typed lines it is in pesos at today's
     * [rate] (E19), or each currency's part when there is none; null when a sum does not
     * fit. With nothing typed it is 0 in the currency of the lines' investments, pesos when
     * they mix or none is chosen.
     */
    private fun contributed(
        lines: List<Line>,
        board: InvestmentBoard?,
        rate: UsdRateEntry?
    ): ContributedTotal? {
        val totals = mutableMapOf(
            Currency.COP to ExactTotal.zero(Currency.COP),
            Currency.USD to ExactTotal.zero(Currency.USD)
        )
        // A line with no investment has no currency yet: it counts nowhere until it has one.
        val chosen = lines.filter { it.investment != null }
        chosen.forEach { line ->
            val currency = currencyOf(line, board)
            val amount = amount(line, currency)?.money ?: return@forEach
            val total = totals.getValue(currency)
            totals[currency] = when (line.kind) {
                InvestmentMovementKind.Contribution -> total + amount
                InvestmentMovementKind.Withdrawal -> total - amount
                else -> return@forEach
            }
        }
        // Every typed line decides it, valuations and dividends too: COP and USD lines are a
        // receipt in two currencies even when only one of them has aportes.
        val typed = chosen.filter { typedMoney(it, currencyOf(it, board)) != null }
        val currencies = typed.ifEmpty { chosen }.mapTo(mutableSetOf()) { currencyOf(it, board) }
        val pesos = (totals.getValue(Currency.COP).toMoney() as? Outcome.Success)?.value
            ?: return null
        val dollars = (totals.getValue(Currency.USD).toMoney() as? Outcome.Success)?.value
            ?: return null
        return when {
            Currency.USD !in currencies -> ContributedTotal.One(money.format(pesos))

            Currency.COP !in currencies -> ContributedTotal.One(money.format(dollars))

            rate == null -> ContributedTotal.Parts(money.format(pesos), money.format(dollars))

            else -> InvestmentPolicy.inPesos(pesos, dollars, rate.rate)?.let {
                ContributedTotal.InPesos(
                    money.format(it),
                    money.decimal(rate.rate.hundredths, Currency.USD.scale),
                    rate.date
                )
            }
        }
    }

    private fun render(
        draft: Draft,
        progress: Progress,
        board: InvestmentBoardState,
        today: LocalDate,
        writing: String?
    ): InvestmentMovementsState {
        val read = readable(board)
        fun model(line: Line, highlighted: Boolean, refusal: MovementLineRefusal?) =
            summary(read, line).let { summary ->
                val currency = currencyOf(line, read)
                MovementLineModel(
                    key = line.key,
                    kind = line.kind,
                    amount = shown(line, currency, highlighted),
                    valid = amount(line, currency) != null,
                    date = line.date,
                    note = line.note,
                    refusal = refusal,
                    investmentId = line.investment,
                    investmentName = summary?.history?.investment?.name?.value,
                    usd = currency == Currency.USD,
                    investmentGone = line.investment != null && read != null && summary == null
                )
            }
        return InvestmentMovementsState(
            loading = board is ReadState.Loading,
            loadFailed = board is ReadState.Failed && read == null,
            stale = board is ReadState.Failed && read != null,
            investments = read?.ordered()?.map { it.choice() }.orEmpty(),
            lines = draft.lines.mapIndexed { index, line ->
                model(line, index == draft.highlighted, progress.refusals[line.key])
            },
            highlighted = draft.highlighted,
            contributed = contributed(draft.lines, read, read?.rates?.current),
            today = today,
            datePickerOpen = progress.datePickerOpen,
            composerOpen = writing != null && !progress.closed,
            composerDraft = writing.orEmpty(),
            composerProblem = writing?.let(SavedNoteComposer::problem),
            composerLength = SavedNoteComposer.length(writing),
            removed = progress.removed?.line?.let {
                model(it, highlighted = false, refusal = null)
            },
            busy = progress.busy,
            error = progress.error,
            confirmDiscard = progress.confirmDiscard,
            closed = progress.closed
        )
    }

    private fun submit() {
        val read = readable(board.value) ?: return
        val sending = draft.value.lines.filter { typedMoney(it, currencyOf(it, read)) != null }
        val investments = sending.map { summary(read, it) ?: return }
        val lines = sending.zip(investments) { line, summary ->
            val investment = summary.history.investment
            InvestmentMovementLine(
                investmentId = investment.id,
                kind = line.kind ?: return,
                amount = amount(line, investment.currency) ?: return,
                date = line.date,
                note = (Note.of(line.note) as? Outcome.Success)?.value
            )
        }
        val versions = investments.associate {
            it.history.investment.id to
                it.history.investment.version
        }
        val keys = sending.map { it.key }
        val histories = investments.map { it.history }
        progress.update { it.copy(busy = true, error = null, refusals = emptyMap()) }
        viewModelScope.launch {
            settle(
                submission.send(commands::find, STEP) { operation ->
                    commands.submit(
                        RecordInvestmentMovements(operation, read.generation, versions, lines)
                    )
                },
                keys,
                histories
            )
        }
    }

    /** Asks how the saved operation ended, with its own identifier. Only a receipt counts. */
    private suspend fun recover() {
        if (!submission.pending) return
        progress.update { it.copy(busy = true, error = null) }
        settle(submission.recover(commands::find), emptyList(), emptyList())
    }

    /** [keys] are the lines sent, in order, and [histories] each one's investment as read. */
    private suspend fun settle(
        settled: Settled<CommittedInvestment, InvestmentRejection>?,
        keys: List<String>,
        histories: List<InvestmentHistory>
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

            is Settled.Rejected -> rejected(settled.reason, keys, histories)
        }
    }

    private fun rejected(
        reason: CommandRejection<InvestmentRejection>,
        keys: List<String>,
        histories: List<InvestmentHistory>
    ) {
        val specific = (reason as? CommandRejection.Specific)?.reason
        if (specific is InvestmentRejection.LinesRefused && keys.isNotEmpty()) {
            val refusals = specific.lines.mapNotNull { refused ->
                val key = keys.getOrNull(refused.position) ?: return@mapNotNull null
                key to refusal(refused.reason, histories[refused.position])
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
     * receipt), so the lines sent are counted by what was typed, in either currency. The
     * confirmation names the investment when every line was of one.
     */
    private suspend fun committed() {
        val sent = draft.value.lines.filter { it.pesos != AmountDraft.Empty || !it.dollars.empty }
        val only = sent.map { it.investment }.distinct().singleOrNull()
        corrections.investmentMovementsRecorded(
            count = sent.size,
            investmentName = summary(readable(board.value), only)?.history?.investment?.name?.value
        )
        drafts.clear()
        // Movements recorded close the investments nudge for good, kept before the receipt
        // closes so leaving never cancels it.
        guidance.markSeen(Nudge.Investments)
        progress.update { it.copy(busy = false, error = null, closed = true) }
    }

    companion object {
        /** A value of 0 pesos, as typed on Valor de hoy (rule 2). */
        private val ZeroPesos = AmountDraft.Digits("0")

        /** The errors a change to the receipt answers. */
        private val Answered = setOf(
            InvestmentMovementsError.NotSaved,
            InvestmentMovementsError.Changed,
            InvestmentMovementsError.Gone
        )
        private const val ZERO = "0"

        /** The route argument: the investment it was opened from, absent from the list. */
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
