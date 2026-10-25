package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.DecimalDraft
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.DeleteInvestment
import com.software.sello.domain.port.EditInvestment
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Nueva inversión and Editar inversión through the real investment commands (rules 1, 2,
 * 11 and 12). Every draft — the texts, the icon, the currency, each figure the pad built,
 * the dates, the open pad — lives in [saved], so rotation or the process being killed
 * loses none of them, nor an operation in flight. A save whose outcome is not known is
 * asked about with its own identifier before anything else is sent. A dollar investment
 * created while no rate exists carries the typed rate in the same command, one
 * transaction (IN33). Editing sends name, description and icon only, at the version read.
 */
class InvestmentEditorViewModel(
    private val saved: SavedStateHandle,
    reads: InvestmentReads,
    private val commands: InvestmentCommands,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter
) : ViewModel() {
    private data class Progress(
        val busy: Boolean = false,
        val saveError: InvestmentSaveError? = null,
        val confirmDiscard: Boolean = false,
        val pulse: Int = 0,
        val atLimit: InvestmentField? = null,
        val result: InvestmentEditorResult? = null
    )

    private val investmentId: String? = saved[KEY_INVESTMENT]

    /** Bumped whenever [saved] changes, so the state is drawn again. */
    private val drafts = MutableStateFlow(0)
    private val progress = MutableStateFlow(
        Progress(result = saved.get<String>(KEY_RESULT)?.let(::result))
    )
    private val board = reads.observeBoard()
        .stateIn(viewModelScope, SharingStarted.Eagerly, InvestmentBoardState.Loading)

    val state: StateFlow<InvestmentEditorState> =
        combine(drafts, progress, board, clock.today) { _, progress, board, today ->
            render(progress, board, today.date)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(progress.value, board.value, clock.today.value.date)
        )

    init {
        viewModelScope.launch { board.collect(::read) }
        if (saved.get<String>(KEY_OP) != null) viewModelScope.launch { recover() }
    }

    fun onAction(action: InvestmentEditorAction) {
        val now = state.value
        val editable = now.editable
        when (action) {
            is InvestmentEditorAction.NameChanged -> if (editable) set(KEY_NAME, action.text)

            is InvestmentEditorAction.DescriptionChanged ->
                if (editable) set(KEY_DESCRIPTION, action.text)

            is InvestmentEditorAction.IconPicked ->
                if (editable && action.key in InvestmentIcons.keys) set(KEY_ICON, action.key)

            is InvestmentEditorAction.CurrencyPicked ->
                if (editable && !now.editing && action.currency in InvestmentAmount.currencies) {
                    set(KEY_CURRENCY, action.currency.code)
                }

            is InvestmentEditorAction.SameValuePicked -> if (editable && !now.editing) {
                saved[KEY_SAME] = action.same
                when {
                    !action.same -> saved[KEY_PAD] = InvestmentField.Value.name
                    now.padField == InvestmentField.Value -> saved[KEY_PAD] = null
                }
                changed()
            }

            is InvestmentEditorAction.OpenPad -> if (editable && !now.editing) {
                set(KEY_PAD, action.field.name)
            }

            InvestmentEditorAction.ClosePad -> set(KEY_PAD, null)

            is InvestmentEditorAction.Digit -> if (editable) digit(now, action.digit)

            InvestmentEditorAction.TripleZero -> if (editable) tripleZero(now)

            InvestmentEditorAction.Comma -> if (editable) comma(now)

            InvestmentEditorAction.Backspace -> if (editable) backspace(now)

            InvestmentEditorAction.Clear -> if (editable) clear(now)

            is InvestmentEditorAction.OpenDatePicker ->
                if (editable && !now.editing && action.field != InvestmentField.Value) {
                    set(KEY_PICKER, action.field.name)
                }

            InvestmentEditorAction.CloseDatePicker -> set(KEY_PICKER, null)

            is InvestmentEditorAction.DatePicked -> {
                val field = now.datePicker
                if (editable && field != null && InvestmentPolicy.acceptsDate(action.date, now.today)) {
                    saved[if (field == InvestmentField.Rate) KEY_RATE_DATE else KEY_DATE] =
                        action.date.toString()
                }
                set(KEY_PICKER, null)
            }

            InvestmentEditorAction.Submit -> if (now.canSubmit && saved.get<String>(KEY_OP) == null) {
                if (now.editing && !now.changed) {
                    progress.update { it.copy(saveError = InvestmentSaveError.Unchanged) }
                } else {
                    submit(now)
                }
            }

            InvestmentEditorAction.Retry -> when {
                now.busy -> Unit
                saved.get<String>(KEY_OP) != null -> viewModelScope.launch { recover() }
                else -> progress.update { it.copy(saveError = null) }
            }

            // Never closed while a save may be on its way: its outcome comes first.
            InvestmentEditorAction.Back -> when {
                now.busy || saved.get<String>(KEY_OP) != null -> Unit
                now.confirmDelete -> set(KEY_CONFIRM, false)
                dirty(now) -> progress.update { it.copy(confirmDiscard = true) }
                else -> finish(InvestmentEditorResult.Discarded)
            }

            InvestmentEditorAction.DiscardConfirmed ->
                if (!now.busy && saved.get<String>(KEY_OP) == null) {
                    finish(InvestmentEditorResult.Discarded)
                }

            InvestmentEditorAction.KeepEditing ->
                progress.update { it.copy(confirmDiscard = false) }

            InvestmentEditorAction.AskToDelete ->
                if (now.editing && editable && stored() != null) set(KEY_CONFIRM, true)

            InvestmentEditorAction.CancelDelete -> if (!now.busy) set(KEY_CONFIRM, false)

            InvestmentEditorAction.Delete ->
                if (now.confirmDelete && !now.busy && saved.get<String>(KEY_OP) == null) delete()
        }
    }

    private fun set(key: String, value: Any?) {
        saved[key] = value
        changed()
    }

    private fun changed() {
        progress.update {
            it.copy(
                atLimit = null,
                saveError = it.saveError.takeIf { error -> error == InvestmentSaveError.Unknown }
            )
        }
        drafts.update { it + 1 }
    }

    private fun readable(state: InvestmentBoardState = board.value): InvestmentBoard? = when (state) {
        InvestmentBoardState.Loading -> null
        is InvestmentBoardState.Ready -> state.board
        is InvestmentBoardState.Failed -> state.lastGood
    }

    private fun stored(read: InvestmentBoard? = readable()): InvestmentSummary? =
        investmentId?.let { id -> read?.investments?.firstOrNull { it.history.investment.id.value == id } }

    /**
     * Each read: the generation a command must carry and, when editing, the investment
     * the draft starts from. A draft already made is kept as it is.
     */
    private fun read(state: InvestmentBoardState) {
        val read = readable(state) ?: return
        if (saved.get<Long>(KEY_GENERATION) == null) saved[KEY_GENERATION] = read.generation
        if (investmentId != null) {
            val summary = stored(read)
            if (summary == null) {
                val idle = saved.get<String>(KEY_OP) == null && !progress.value.busy
                if (idle && progress.value.result == null) finish(InvestmentEditorResult.Discarded)
                return
            }
            if (saved.get<Boolean>(KEY_DRAFTED) != true) {
                val investment = summary.history.investment
                saved[KEY_NAME] = investment.name.value
                saved[KEY_DESCRIPTION] = investment.description?.value.orEmpty()
                saved[KEY_ICON] = investment.icon.value
                saved[KEY_VERSION] = investment.version
                saved[KEY_DRAFTED] = true
            }
        }
        drafts.update { it + 1 }
    }

    // ── The pad ──────────────────────────────────────────────────────────────────────

    /** Whether [field] is typed with the comma: dollars and the rate (v2 *Dólares y la tasa*). */
    private fun decimal(field: InvestmentField, currency: Currency) =
        field == InvestmentField.Rate || currency == Currency.USD

    private fun digit(now: InvestmentEditorState, digit: Int) {
        val field = now.padField ?: return
        if (decimal(field, now.currency)) {
            decimalPress(field, decimalDraft(field).digit(digit))
        } else {
            copPress(field, digit.toString())
        }
    }

    /** The decimal comma exists only for dollars and the rate; pesos keep 000. */
    private fun comma(now: InvestmentEditorState) {
        val field = now.padField ?: return
        if (decimal(field, now.currency)) decimalPress(field, decimalDraft(field).comma())
    }

    private fun decimalPress(field: InvestmentField, change: DecimalDraft.Change) {
        storeDecimal(field, change.draft)
        changed()
        if (change.limitReached) {
            progress.update { it.copy(pulse = it.pulse + 1, atLimit = field) }
        }
    }

    private fun tripleZero(now: InvestmentEditorState) {
        val field = now.padField ?: return
        if (!decimal(field, now.currency)) copPress(field, "000")
    }

    /**
     * Pesos: digits and 000, at most twelve digits (rule 2, IN26). Only Otro valor may be
     * 0 (an investment that lost everything); a first contribution never starts with 0.
     */
    private fun copPress(field: InvestmentField, keys: String) {
        val key = copKey(field)
        val current = saved.get<String>(key)?.takeUnless { it == "0" }
        if (current == null && keys.all { it == '0' }) {
            set(key, if (field == InvestmentField.Value) "0" else null)
            return
        }
        val draft = current?.let(AmountDraft::Digits) ?: AmountDraft.Empty
        val change = if (keys == "000") draft.tripleZero() else draft.digit(keys.toInt())
        if (change.limitReached) {
            progress.update { it.copy(pulse = it.pulse + 1, atLimit = field) }
        } else {
            set(key, (change.draft as? AmountDraft.Digits)?.digits)
        }
    }

    private fun backspace(now: InvestmentEditorState) {
        val field = now.padField ?: return
        if (decimal(field, now.currency)) {
            storeDecimal(field, decimalDraft(field).backspace())
            changed()
        } else {
            val key = copKey(field)
            val digits = saved.get<String>(key)
            set(key, digits?.takeIf { it.length > 1 && it != "0" }?.dropLast(1))
        }
    }

    private fun clear(now: InvestmentEditorState) {
        val field = now.padField ?: return
        if (decimal(field, now.currency)) {
            storeDecimal(field, DecimalDraft(maxWhole(field)))
            changed()
        } else {
            set(copKey(field), null)
        }
    }

    private fun copKey(field: InvestmentField) =
        if (field == InvestmentField.First) KEY_COP_FIRST else KEY_COP_VALUE

    private fun decimalKey(field: InvestmentField) = when (field) {
        InvestmentField.First -> KEY_USD_FIRST
        InvestmentField.Value -> KEY_USD_VALUE
        InvestmentField.Rate -> KEY_RATE
    }

    /** 100.000,00 pesos is six whole digits; USD 9.999.999.999,99 is ten (rules 2 and 11). */
    private fun maxWhole(field: InvestmentField) =
        if (field == InvestmentField.Rate) RATE_WHOLE_DIGITS else USD_WHOLE_DIGITS

    private fun decimalDraft(field: InvestmentField): DecimalDraft {
        val key = decimalKey(field)
        val whole = saved.get<String>("$key.whole") ?: return DecimalDraft(maxWhole(field))
        return DecimalDraft(
            maxWhole(field),
            whole,
            saved.get<Boolean>("$key.comma") ?: false,
            saved.get<String>("$key.cents").orEmpty()
        )
    }

    private fun storeDecimal(field: InvestmentField, draft: DecimalDraft) {
        val key = decimalKey(field)
        saved["$key.whole"] = draft.whole
        saved["$key.comma"] = draft.comma
        saved["$key.cents"] = draft.cents
    }

    /** The draft as the pad wrote it: "1.000,5". */
    private fun decimalText(draft: DecimalDraft): String? {
        if (draft.empty) return null
        val grouped = money.decimal(draft.whole.ifEmpty { "0" }.toLong(), 0)
        return if (draft.comma) "$grouped,${draft.cents}" else grouped
    }

    /** What the pad built for [field] in [currency], or null before any key. */
    private fun figure(field: InvestmentField, currency: Currency): Money? =
        if (currency == Currency.USD) {
            decimalDraft(field).hundredths()?.let { Money(it, Currency.USD) }
        } else {
            saved.get<String>(copKey(field))?.let { Money.cop(it.toLong()) }
        }

    private fun shown(field: InvestmentField, currency: Currency) =
        figure(field, currency)?.let { amount ->
            val formatted = money.format(amount)
            if (currency == Currency.USD) {
                formatted.copy(digits = decimalText(decimalDraft(field)) ?: formatted.digits)
            } else {
                formatted
            }
        }

    // ── The form ─────────────────────────────────────────────────────────────────────

    private fun name(): String = saved[KEY_NAME] ?: ""

    private fun description(): String = saved[KEY_DESCRIPTION] ?: ""

    private fun chosenCurrency(summary: InvestmentSummary?): Currency =
        summary?.history?.investment?.currency
            ?: saved.get<String>(KEY_CURRENCY)?.let { code -> Currency.entries.first { it.code == code } }
            ?: Currency.COP

    private fun date(key: String, today: LocalDate): LocalDate =
        saved.get<String>(key)?.let(LocalDate::parse) ?: today

    private fun rate(): UsdRate? =
        decimalDraft(InvestmentField.Rate).hundredths()
            ?.let { (UsdRate.ofHundredths(it) as? Outcome.Success)?.value }

    private fun textError(error: TextError?): InvestmentTextError? = when (error) {
        is TextError.TooLong -> InvestmentTextError.TooLong
        TextError.ControlCharacter -> InvestmentTextError.NotAllowed
        TextError.Required, null -> null
    }

    private fun length(text: String): Int = text.trim().let { it.codePointCount(0, it.length) }

    private fun detailsChanged(summary: InvestmentSummary): Boolean {
        val investment = summary.history.investment
        // A description that cannot be saved differs from any stored one.
        val description = when (val typed = InvestmentDescription.of(description())) {
            is Outcome.Success -> typed.value?.value
            is Outcome.Failure -> description()
        }
        return name().trim() != investment.name.value ||
            description != investment.description?.value ||
            saved.get<String>(KEY_ICON) != investment.icon.value
    }

    private fun dirty(state: InvestmentEditorState): Boolean = if (state.editing) {
        state.changed
    } else {
        name().isNotBlank() || description().isNotBlank() || state.iconKey != null ||
            state.currency != Currency.COP || state.firstContribution != null ||
            state.otherValue != null || state.rate != null
    }

    private fun render(
        progress: Progress,
        board: InvestmentBoardState,
        today: LocalDate
    ): InvestmentEditorState {
        val read = readable(board)
        val summary = stored(read)
        val editing = investmentId != null
        val currency = chosenCurrency(summary)
        val first = figure(InvestmentField.First, currency)
        val other = figure(InvestmentField.Value, currency)
        val rateDraft = decimalDraft(InvestmentField.Rate)
        val hundredths = rateDraft.hundredths()
        val text = name()
        val about = description()
        return InvestmentEditorState(
            loading = read == null && board !is InvestmentBoardState.Failed ||
                editing && read != null && saved.get<Boolean>(KEY_DRAFTED) != true,
            loadFailed = board is InvestmentBoardState.Failed && read == null,
            editing = editing,
            name = text,
            nameLength = length(text),
            nameError = textError((InvestmentName.of(text) as? Outcome.Failure)?.error),
            description = about,
            descriptionLength = length(about),
            descriptionError =
                textError((InvestmentDescription.of(about) as? Outcome.Failure)?.error),
            iconKey = saved[KEY_ICON],
            currency = currency,
            firstContribution = shown(InvestmentField.First, currency),
            firstReady = first != null && InvestmentAmount.of(first) is Outcome.Success,
            date = date(KEY_DATE, today),
            today = today,
            sameValue = saved[KEY_SAME] ?: true,
            otherValue = shown(InvestmentField.Value, currency),
            valueReady = other != null && InvestmentAmount.ofValue(other) is Outcome.Success,
            rateNeeded = !editing && currency == Currency.USD && read != null &&
                read.rates.current == null,
            rate = decimalText(rateDraft),
            rateProblem = when {
                hundredths == null -> null
                hundredths < UsdRate.MIN_HUNDREDTHS -> RateProblem.BelowMinimum
                hundredths > UsdRate.MAX_HUNDREDTHS -> RateProblem.AboveMaximum
                else -> null
            },
            rateReady = rate() != null,
            rateDate = date(KEY_RATE_DATE, today),
            currentRate = read?.rates?.current?.let {
                RateModel(money.decimal(it.rate.hundredths, Currency.USD.scale), it.date)
            },
            padField = saved.get<String>(KEY_PAD)?.let(InvestmentField::valueOf),
            pulse = progress.pulse,
            atLimit = progress.atLimit,
            datePicker = saved.get<String>(KEY_PICKER)?.let(InvestmentField::valueOf),
            movementCount = summary?.history?.movements?.size ?: 0,
            busy = progress.busy,
            saveError = progress.saveError,
            confirmDelete = saved[KEY_CONFIRM] ?: false,
            confirmDiscard = progress.confirmDiscard,
            changed = summary != null && detailsChanged(summary),
            result = progress.result
        )
    }

    // ── Commands ─────────────────────────────────────────────────────────────────────

    private fun submit(now: InvestmentEditorState) {
        val read = readable() ?: return
        val name = (InvestmentName.of(name()) as? Outcome.Success)?.value ?: return
        val description = (InvestmentDescription.of(description()) as? Outcome.Success ?: return).value
        val icon = saved.get<String>(KEY_ICON)?.let { (IconKey.of(it) as? Outcome.Success)?.value }
            ?: return
        val generation = saved.get<Long>(KEY_GENERATION) ?: read.generation
        if (now.editing) {
            val summary = stored(read) ?: return
            val version = saved.get<Long>(KEY_VERSION) ?: summary.history.investment.version
            send(STEP_EDIT) { operation ->
                EditInvestment(
                    operation,
                    generation,
                    summary.history.investment.id,
                    version,
                    name,
                    description,
                    icon
                )
            }
            return
        }
        val currency = now.currency
        val first = figure(InvestmentField.First, currency)
            ?.let { (InvestmentAmount.of(it) as? Outcome.Success)?.value } ?: return
        val value = if (now.sameValue) {
            first
        } else {
            figure(InvestmentField.Value, currency)
                ?.let { (InvestmentAmount.ofValue(it) as? Outcome.Success)?.value } ?: return
        }
        val newRate = if (currency == Currency.USD && read.rates.current == null) {
            DatedUsdRate(rate() ?: return, now.rateDate)
        } else {
            null
        }
        send(STEP_CREATE) { operation ->
            CreateInvestment(
                operation,
                generation,
                name,
                description,
                icon,
                currency,
                first,
                value,
                now.date,
                newRate
            )
        }
    }

    private fun delete() {
        val read = readable() ?: return
        val summary = stored(read) ?: return
        val generation = saved.get<Long>(KEY_GENERATION) ?: read.generation
        val version = saved.get<Long>(KEY_VERSION) ?: summary.history.investment.version
        send(STEP_DELETE) { operation ->
            DeleteInvestment(operation, generation, summary.history.investment.id, version)
        }
    }

    /** The operation is saved before the command leaves, so this attempt is recognised whatever happens. */
    private fun send(
        step: String,
        command: (OperationId) -> InvestmentCommand
    ) {
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[KEY_OP] = operation.value
        saved[KEY_STEP] = step
        progress.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch { settle(step, operation, commands.submit(command(operation))) }
    }

    private suspend fun settle(step: String, operation: OperationId, outcome: InvestmentCommandOutcome) {
        when (outcome) {
            is InvestmentCommandOutcome.Committed -> committed(step, outcome.change.investmentId)

            is InvestmentCommandOutcome.OutcomeUnknown -> ask(step, operation)

            is InvestmentCommandOutcome.Rejected -> notSaved(
                when (outcome.reason) {
                    is InvestmentRejection.StaleGeneration,
                    is InvestmentRejection.VersionConflict,
                    InvestmentRejection.InvestmentMissing,
                    InvestmentRejection.OperationConflict -> InvestmentSaveError.Changed

                    is InvestmentRejection.FutureDate -> InvestmentSaveError.FutureDate

                    InvestmentRejection.InvalidAmount,
                    InvestmentRejection.InvalidDate,
                    InvestmentRejection.CurrencyMismatch,
                    InvestmentRejection.Unrepresentable -> InvestmentSaveError.Invalid

                    InvestmentRejection.RateRequired -> InvestmentSaveError.RateRequired

                    InvestmentRejection.Unchanged -> InvestmentSaveError.Unchanged

                    else -> InvestmentSaveError.NotSaved
                }
            )
        }
    }

    /** Asks how [operation] ended, with its own identifier. Only a receipt counts as saved. */
    private suspend fun ask(step: String, operation: OperationId) {
        progress.update { it.copy(busy = true, saveError = null) }
        when (val found = commands.find(operation)) {
            is Outcome.Failure -> progress.update {
                it.copy(busy = false, saveError = InvestmentSaveError.Unknown)
            }

            is Outcome.Success -> found.value?.let { committed(step, it.investmentId) }
                ?: notSaved(InvestmentSaveError.NotSaved)
        }
    }

    /** The saved operation after a restart or a Comprobar, asked about before anything else. */
    private suspend fun recover() {
        val operation = (saved.get<String>(KEY_OP)?.let(OperationId::of) as? Outcome.Success)?.value
        if (operation == null) {
            saved[KEY_OP] = null
            return
        }
        ask(saved.get<String>(KEY_STEP) ?: STEP_CREATE, operation)
    }

    private fun committed(step: String, id: InvestmentId?) {
        saved[KEY_OP] = null
        saved[KEY_STEP] = null
        finish(
            when (step) {
                STEP_CREATE -> id?.let { InvestmentEditorResult.Created(it.value) }
                    ?: InvestmentEditorResult.Discarded

                STEP_DELETE -> InvestmentEditorResult.Deleted

                else -> InvestmentEditorResult.Updated
            }
        )
    }

    /**
     * Certain: nothing was written, so this attempt is over. When the history changed,
     * the form takes the generation and version as they are now and keeps the draft.
     */
    private fun notSaved(error: InvestmentSaveError) {
        saved[KEY_OP] = null
        saved[KEY_STEP] = null
        saved[KEY_CONFIRM] = false
        if (error == InvestmentSaveError.Changed) {
            readable()?.let { read ->
                saved[KEY_GENERATION] = read.generation
                stored(read)?.let { saved[KEY_VERSION] = it.history.investment.version }
            }
        }
        progress.update { it.copy(busy = false, saveError = error) }
        drafts.update { it + 1 }
        if (investmentId != null && stored() == null && readable() != null) {
            finish(InvestmentEditorResult.Discarded)
        }
    }

    private fun finish(result: InvestmentEditorResult) {
        saved[KEY_RESULT] = when (result) {
            is InvestmentEditorResult.Created -> "created:${result.investmentId}"
            InvestmentEditorResult.Updated -> "updated"
            InvestmentEditorResult.Deleted -> "deleted"
            InvestmentEditorResult.Discarded -> "discarded"
        }
        saved[KEY_CONFIRM] = false
        progress.update { it.copy(busy = false, confirmDiscard = false, result = result) }
        drafts.update { it + 1 }
    }

    private fun result(stored: String): InvestmentEditorResult = when {
        stored.startsWith("created:") ->
            InvestmentEditorResult.Created(stored.removePrefix("created:"))

        stored == "updated" -> InvestmentEditorResult.Updated
        stored == "deleted" -> InvestmentEditorResult.Deleted
        else -> InvestmentEditorResult.Discarded
    }

    companion object {
        /** The route argument: the investment to edit, absent for a new one. */
        const val KEY_INVESTMENT = "investmentId"

        private const val USD_WHOLE_DIGITS = 10
        private const val RATE_WHOLE_DIGITS = 6

        private const val KEY_NAME = "investment.editor.name"
        private const val KEY_DESCRIPTION = "investment.editor.description"
        private const val KEY_ICON = "investment.editor.icon"
        private const val KEY_CURRENCY = "investment.editor.currency"
        private const val KEY_SAME = "investment.editor.sameValue"
        private const val KEY_COP_FIRST = "investment.editor.cop.first"
        private const val KEY_COP_VALUE = "investment.editor.cop.value"
        private const val KEY_USD_FIRST = "investment.editor.usd.first"
        private const val KEY_USD_VALUE = "investment.editor.usd.value"
        private const val KEY_RATE = "investment.editor.rate"
        private const val KEY_DATE = "investment.editor.date"
        private const val KEY_RATE_DATE = "investment.editor.rateDate"
        private const val KEY_PAD = "investment.editor.pad"
        private const val KEY_PICKER = "investment.editor.datePicker"
        private const val KEY_DRAFTED = "investment.editor.drafted"
        private const val KEY_GENERATION = "investment.editor.generation"
        private const val KEY_VERSION = "investment.editor.version"
        private const val KEY_CONFIRM = "investment.editor.confirmDelete"
        private const val KEY_OP = "investment.editor.operation"
        private const val KEY_STEP = "investment.editor.step"
        private const val KEY_RESULT = "investment.editor.result"
        private const val STEP_CREATE = "create"
        private const val STEP_EDIT = "edit"
        private const val STEP_DELETE = "delete"
    }
}
