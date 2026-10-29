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
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.DeleteInvestment
import com.software.sello.domain.port.EditInvestment
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

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
    preferences: InvestmentPreferences,
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

    /** A figure typed with the comma: dollars or the rate. */
    @Serializable
    private data class Decimal(val whole: String, val comma: Boolean, val cents: String)

    /** What was typed and chosen, kept under one key. Pesos are digits, "0" only for Otro valor. */
    @Serializable
    private data class Draft(
        val name: String = "",
        val description: String = "",
        val icon: String? = null,
        val currency: String? = null,
        val sameValue: Boolean? = null,
        val copFirst: String? = null,
        val copValue: String? = null,
        val usdFirst: Decimal? = null,
        val usdValue: Decimal? = null,
        val rate: Decimal? = null,
        val date: String? = null,
        val rateDate: String? = null,
        val pad: String? = null
    )

    private val investmentId: String? = saved[KEY_INVESTMENT]
    private val stash = SavedDraft(saved, KEY_DRAFT, Draft.serializer())
    private var form = stash.read() ?: Draft()
    private val submission = Submission(saved, KEY_OP, ids)

    /** Bumped whenever [saved] changes, so the state is drawn again. */
    private val drafts = MutableStateFlow(0)
    private val progress = MutableStateFlow(
        Progress(result = saved.get<String>(KEY_RESULT)?.let(::result))
    )
    private val board = reads.observeBoard()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadState.Loading)

    /**
     * The currency a new investment starts in, chosen in Configuración, until another is
     * picked here. Pesos while it is read, or when it cannot be: it moves no money.
     */
    private val starting = preferences.observeInvestments().map { read ->
        when (read) {
            is PreferenceRead.Ready -> read.value.newCurrency
            is PreferenceRead.Failed -> read.lastGood?.newCurrency ?: Currency.COP
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Currency.COP)

    val state: StateFlow<InvestmentEditorState> =
        combine(drafts, progress, board, clock.today, starting) {
                _,
                progress,
                board,
                today,
                starting
            ->
            render(progress, board, today.date, starting)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(progress.value, board.value, clock.today.value.date, starting.value)
        )

    init {
        viewModelScope.launch { board.collect(::read) }
        if (submission.pending) viewModelScope.launch { recover() }
    }

    fun onAction(action: InvestmentEditorAction) {
        val now = state.value
        val editable = now.editable
        when (action) {
            is InvestmentEditorAction.NameChanged -> if (editable) {
                edit(
                    form.copy(name = action.text)
                )
            }

            is InvestmentEditorAction.DescriptionChanged ->
                if (editable) edit(form.copy(description = action.text))

            is InvestmentEditorAction.IconPicked ->
                if (editable &&
                    action.key in InvestmentIcons.keys
                ) {
                    edit(form.copy(icon = action.key))
                }

            is InvestmentEditorAction.CurrencyPicked ->
                if (editable && !now.editing && action.currency in InvestmentAmount.currencies) {
                    edit(form.copy(currency = action.currency.code))
                }

            is InvestmentEditorAction.SameValuePicked -> if (editable && !now.editing) {
                val pad = when {
                    !action.same -> InvestmentField.Value.name
                    now.padField == InvestmentField.Value -> null
                    else -> form.pad
                }
                edit(form.copy(sameValue = action.same, pad = pad))
            }

            is InvestmentEditorAction.TypeInto -> if (editable && !now.editing) {
                edit(form.copy(pad = action.field.name))
            }

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
                if (editable && field != null &&
                    InvestmentPolicy.acceptsDate(action.date, now.today)
                ) {
                    val date = action.date.toString()
                    store(
                        if (field == InvestmentField.Rate) {
                            form.copy(rateDate = date)
                        } else {
                            form.copy(date = date)
                        }
                    )
                }
                set(KEY_PICKER, null)
            }

            InvestmentEditorAction.Submit -> if (now.canSubmit && !submission.pending) {
                if (now.editing && !now.changed) {
                    progress.update { it.copy(saveError = InvestmentSaveError.Unchanged) }
                } else {
                    submit(now)
                }
            }

            InvestmentEditorAction.Retry -> when {
                now.busy -> Unit
                submission.pending -> viewModelScope.launch { recover() }
                else -> progress.update { it.copy(saveError = null) }
            }

            // Never closed while a save may be on its way: its outcome comes first.
            InvestmentEditorAction.Back -> when {
                now.busy || submission.pending -> Unit
                now.confirmDelete -> set(KEY_CONFIRM, false)
                dirty(now) -> progress.update { it.copy(confirmDiscard = true) }
                else -> finish(InvestmentEditorResult.Discarded)
            }

            InvestmentEditorAction.DiscardConfirmed ->
                if (!now.busy && !submission.pending) {
                    finish(InvestmentEditorResult.Discarded)
                }

            InvestmentEditorAction.KeepEditing ->
                progress.update { it.copy(confirmDiscard = false) }

            InvestmentEditorAction.AskToDelete ->
                if (now.editing && editable && stored() != null) set(KEY_CONFIRM, true)

            InvestmentEditorAction.CancelDelete -> if (!now.busy) set(KEY_CONFIRM, false)

            InvestmentEditorAction.Delete ->
                if (now.confirmDelete && !now.busy && !submission.pending) delete()
        }
    }

    private fun set(key: String, value: Any?) {
        saved[key] = value
        changed()
    }

    private fun store(next: Draft) {
        form = next
        stash.write(next)
    }

    private fun edit(next: Draft) {
        store(next)
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

    private fun readable(state: InvestmentBoardState = board.value): InvestmentBoard? =
        when (state) {
            ReadState.Loading -> null
            is ReadState.Ready -> state.value
            is ReadState.Failed -> state.lastGood
        }

    private fun stored(read: InvestmentBoard? = readable()): InvestmentSummary? =
        investmentId?.let { id ->
            read?.investments?.firstOrNull {
                it.history.investment.id.value ==
                    id
            }
        }

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
                val idle = !submission.pending && !progress.value.busy
                if (idle && progress.value.result == null) finish(InvestmentEditorResult.Discarded)
                return
            }
            if (saved.get<Boolean>(KEY_DRAFTED) != true) {
                val investment = summary.history.investment
                store(
                    form.copy(
                        name = investment.name.value,
                        description = investment.description?.value.orEmpty(),
                        icon = investment.icon.value
                    )
                )
                saved[KEY_VERSION] = investment.version
                saved[KEY_DRAFTED] = true
            }
        }
        drafts.update { it + 1 }
    }

    // ── The pad ──────────────────────────────────────────────────────────────────────

    /**
     * The figure the pad, always under a new investment, types into: the one last chosen
     * while it is on the form, otherwise Primer aporte. Editing has no figures and no pad.
     */
    private fun padField(
        editing: Boolean,
        sameValue: Boolean,
        rateNeeded: Boolean
    ): InvestmentField? {
        if (editing) return null
        val chosen = form.pad?.let(InvestmentField::valueOf)
        return when (chosen) {
            InvestmentField.Value -> chosen.takeUnless { sameValue }
            InvestmentField.Rate -> chosen.takeIf { rateNeeded }
            else -> chosen
        } ?: InvestmentField.First
    }

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
        val current = cop(field)?.takeUnless { it == "0" }
        if (current == null && keys.all { it == '0' }) {
            edit(withCop(field, if (field == InvestmentField.Value) "0" else null))
            return
        }
        val draft = current?.let(AmountDraft::Digits) ?: AmountDraft.Empty
        val change = if (keys == "000") draft.tripleZero() else draft.digit(keys.toInt())
        if (change.limitReached) {
            progress.update { it.copy(pulse = it.pulse + 1, atLimit = field) }
        } else {
            edit(withCop(field, (change.draft as? AmountDraft.Digits)?.digits))
        }
    }

    private fun backspace(now: InvestmentEditorState) {
        val field = now.padField ?: return
        if (decimal(field, now.currency)) {
            storeDecimal(field, decimalDraft(field).backspace())
            changed()
        } else {
            val digits = cop(field)
            edit(withCop(field, digits?.takeIf { it.length > 1 && it != "0" }?.dropLast(1)))
        }
    }

    private fun clear(now: InvestmentEditorState) {
        val field = now.padField ?: return
        if (decimal(field, now.currency)) {
            storeDecimal(field, DecimalDraft(maxWhole(field)))
            changed()
        } else {
            edit(withCop(field, null))
        }
    }

    /** The pesos typed for [field]: Primer aporte, or Otro valor for the rate too. */
    private fun cop(field: InvestmentField): String? =
        if (field == InvestmentField.First) form.copFirst else form.copValue

    private fun withCop(field: InvestmentField, digits: String?): Draft =
        if (field == InvestmentField.First) {
            form.copy(copFirst = digits)
        } else {
            form.copy(copValue = digits)
        }

    /** 100.000,00 pesos is six whole digits; USD 9.999.999.999,99 is ten (rules 2 and 11). */
    private fun maxWhole(field: InvestmentField) =
        if (field == InvestmentField.Rate) RATE_WHOLE_DIGITS else USD_WHOLE_DIGITS

    private fun decimalDraft(field: InvestmentField): DecimalDraft {
        val typed = when (field) {
            InvestmentField.First -> form.usdFirst
            InvestmentField.Value -> form.usdValue
            InvestmentField.Rate -> form.rate
        } ?: return DecimalDraft(maxWhole(field))
        return DecimalDraft(maxWhole(field), typed.whole, typed.comma, typed.cents)
    }

    private fun storeDecimal(field: InvestmentField, draft: DecimalDraft) {
        val typed = Decimal(draft.whole, draft.comma, draft.cents)
        store(
            when (field) {
                InvestmentField.First -> form.copy(usdFirst = typed)
                InvestmentField.Value -> form.copy(usdValue = typed)
                InvestmentField.Rate -> form.copy(rate = typed)
            }
        )
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
            cop(field)?.let { Money.cop(it.toLong()) }
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

    private fun name(): String = form.name

    private fun description(): String = form.description

    private fun chosenCurrency(summary: InvestmentSummary?, starting: Currency): Currency =
        summary?.history?.investment?.currency
            ?: form.currency?.let { code ->
                Currency.entries.first {
                    it.code ==
                        code
                }
            }
            ?: starting

    private fun date(typed: String?, today: LocalDate): LocalDate =
        typed?.let(LocalDate::parse) ?: today

    private fun rate(): UsdRate? = decimalDraft(InvestmentField.Rate).hundredths()
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
            form.icon != investment.icon.value
    }

    private fun dirty(state: InvestmentEditorState): Boolean = if (state.editing) {
        state.changed
    } else {
        name().isNotBlank() || description().isNotBlank() || state.iconKey != null ||
            state.currency != starting.value || state.firstContribution != null ||
            state.otherValue != null || state.rate != null
    }

    private fun render(
        progress: Progress,
        board: InvestmentBoardState,
        today: LocalDate,
        starting: Currency
    ): InvestmentEditorState {
        val read = readable(board)
        val summary = stored(read)
        val editing = investmentId != null
        val currency = chosenCurrency(summary, starting)
        val first = figure(InvestmentField.First, currency)
        val other = figure(InvestmentField.Value, currency)
        val rateDraft = decimalDraft(InvestmentField.Rate)
        val hundredths = rateDraft.hundredths()
        val text = name()
        val about = description()
        val rateNeeded = !editing && currency == Currency.USD && read != null &&
            read.rates.current == null
        val sameValue = form.sameValue ?: true
        return InvestmentEditorState(
            loading = (read == null && board !is ReadState.Failed) ||
                (editing && read != null && saved.get<Boolean>(KEY_DRAFTED) != true),
            loadFailed = board is ReadState.Failed && read == null,
            editing = editing,
            name = text,
            nameLength = length(text),
            nameError = textError((InvestmentName.of(text) as? Outcome.Failure)?.error),
            description = about,
            descriptionLength = length(about),
            descriptionError =
                textError((InvestmentDescription.of(about) as? Outcome.Failure)?.error),
            iconKey = form.icon,
            currency = currency,
            firstContribution = shown(InvestmentField.First, currency),
            firstReady = first != null && InvestmentAmount.of(first) is Outcome.Success,
            date = date(form.date, today),
            today = today,
            sameValue = sameValue,
            otherValue = shown(InvestmentField.Value, currency),
            valueReady = other != null && InvestmentAmount.ofValue(other) is Outcome.Success,
            rateNeeded = rateNeeded,
            rate = decimalText(rateDraft),
            rateProblem = when {
                hundredths == null -> null
                hundredths < UsdRate.MIN_HUNDREDTHS -> RateProblem.BelowMinimum
                hundredths > UsdRate.MAX_HUNDREDTHS -> RateProblem.AboveMaximum
                else -> null
            },
            rateReady = rate() != null,
            rateDate = date(form.rateDate, today),
            currentRate = read?.rates?.current?.let {
                RateModel(money.decimal(it.rate.hundredths, Currency.USD.scale), it.date)
            },
            padField = padField(editing, sameValue, rateNeeded),
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
        val description = (
            InvestmentDescription.of(
                description()
            ) as? Outcome.Success ?: return
            ).value
        val icon = form.icon?.let { (IconKey.of(it) as? Outcome.Success)?.value }
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

    private fun send(step: String, command: (OperationId) -> InvestmentCommand) {
        progress.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch {
            settle(step, submission.send(commands::find, step) { commands.submit(command(it)) })
        }
    }

    private fun settle(step: String, settled: Settled<CommittedInvestment, InvestmentRejection>?) {
        when (settled) {
            // Something is still in flight and will settle; or nothing was left to ask about.
            null -> if (!submission.pending) progress.update { it.copy(busy = false) }

            is Settled.Committed -> committed(step, settled.value.investmentId)

            is Settled.Unknown -> progress.update {
                it.copy(busy = false, saveError = InvestmentSaveError.Unknown)
            }

            Settled.NotCommitted -> notSaved(InvestmentSaveError.NotSaved)

            is Settled.Rejected -> notSaved(
                when (val reason = settled.reason) {
                    is CommandRejection.StaleGeneration,
                    is CommandRejection.VersionConflict,
                    CommandRejection.OperationConflict -> InvestmentSaveError.Changed

                    is CommandRejection.StorageFailed -> InvestmentSaveError.NotSaved

                    is CommandRejection.Specific -> when (reason.reason) {
                        InvestmentRejection.InvestmentMissing -> InvestmentSaveError.Changed

                        is InvestmentRejection.FutureDate -> InvestmentSaveError.FutureDate

                        InvestmentRejection.InvalidAmount,
                        InvestmentRejection.InvalidDate,
                        InvestmentRejection.CurrencyMismatch,
                        InvestmentRejection.Unrepresentable -> InvestmentSaveError.Invalid

                        InvestmentRejection.RateRequired -> InvestmentSaveError.RateRequired

                        InvestmentRejection.Unchanged -> InvestmentSaveError.Unchanged

                        else -> InvestmentSaveError.NotSaved
                    }
                }
            )
        }
    }

    /**
     * The saved operation after a restart or a Comprobar, asked about with its own
     * identifier before anything else. Only a receipt counts as saved.
     */
    private suspend fun recover() {
        if (!submission.pending) return
        val step = submission.step ?: STEP_CREATE
        progress.update { it.copy(busy = true, saveError = null) }
        settle(step, submission.recover(commands::find))
    }

    private fun committed(step: String, id: InvestmentId?) {
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

        private const val KEY_DRAFT = "investment.editor.draft"
        private const val KEY_PICKER = "investment.editor.datePicker"
        private const val KEY_DRAFTED = "investment.editor.drafted"
        private const val KEY_GENERATION = "investment.editor.generation"
        private const val KEY_VERSION = "investment.editor.version"
        private const val KEY_CONFIRM = "investment.editor.confirmDelete"
        private const val KEY_OP = "investment.editor.operation"
        private const val KEY_RESULT = "investment.editor.result"
        private const val STEP_CREATE = "create"
        private const val STEP_EDIT = "edit"
        private const val STEP_DELETE = "delete"
    }
}
