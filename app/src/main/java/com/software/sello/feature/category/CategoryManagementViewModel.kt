package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryBreakdown
import com.software.sello.domain.model.CarryEnableRefusal
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.policy.CopAmountInput
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.policy.SignedCarryoverPolicy
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CarrySetting
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CategoryRemovalPreviews
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.EditCategory
import com.software.sello.domain.port.LimitEdit
import com.software.sello.domain.port.MonthBudget
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.UnarchiveCategory
import com.software.sello.domain.usecase.CatchUpNow
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.carry.CarryCorrectionText
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.SavedCarry
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Changes an existing category through the real commands: its name, its icon, the
 * limit of the month it was opened from, and whether it is archived. It draws the same
 * form as a new category.
 *
 * The category is read once. What was read, with its version, is kept in [saved]
 * beside the draft, so a draft restored after the process was killed is compared with
 * and sent against the category as it was when the form opened, and is never read over.
 *
 * Guardar sends everything that differs, the carry setting included, as one command
 * with one operation and one receipt (SELLO-188): it is all saved or none of it is. The
 * carry switch and its questions only change the draft; leaving without saving changes
 * nothing. A change is reported as saved only when its receipt says so.
 *
 * Deleting the category (ADR 0024) is [CategoryRemoval]'s, which this form hands the
 * category as it read it, and its own operations through the same [Submission].
 */
class CategoryManagementViewModel(
    private val saved: SavedStateHandle,
    private val commands: CategoryCommands,
    private val reads: CategoryReads,
    private val snapshots: MonthlySnapshots,
    private val session: MonthSession,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val notices: Corrections,
    private val catchUp: CatchUpNow,
    previews: CategoryRemovalPreviews
) : ViewModel() {
    /** The limit a month has, or that it has none of its own to show. */
    private sealed interface StoredLimit {
        data object None : StoredLimit

        data object Unlimited : StoredLimit

        data class Finite(val pesos: Long) : StoredLimit
    }

    /** The category as the form last read it from storage. */
    private data class Original(
        val version: Long,
        val name: String,
        val icon: String,
        val archived: Boolean,
        val limit: StoredLimit,
        /** Archived in an earlier month: this month has no budget because of it. */
        val paused: Boolean
    )

    /** What was typed and chosen, kept under one key. */
    @Serializable
    private data class Draft(
        val name: String = "",
        val icon: String = "",
        val unlimited: Boolean = false,
        val limit: String = "",
        /** Carry as the draft wants it: [CARRY_ON], [CARRY_OFF], or "" for as stored. */
        val carry: String = "",
        /** Turning off: the balance the person agreed to forget, in pesos, or "" for none. */
        val drop: String = ""
    )

    /** [Original] as kept in [saved]: the limit as pesos, [UNLIMITED] or none. */
    @Serializable
    private data class Kept(
        val version: Long,
        val name: String,
        val icon: String,
        val archived: Boolean,
        val paused: Boolean,
        val limit: String?
    )

    private val drafts = SavedDraft(saved, KEY_DRAFT, Draft.serializer())
    private val kept = SavedDraft(saved, KEY_ORIGINAL, Kept.serializer())
    private var typed = drafts.read() ?: Draft()
    private val submission = Submission(saved, KEY_OPERATION, ids)

    private val categoryId: CategoryId? =
        (saved.get<String>(KEY_CATEGORY)?.let(CategoryId::of) as? Outcome.Success)?.value

    /**
     * Fixed when the form opens, so a draft restored next month is still about this one;
     * moved on only when a save finds it ended ([monthEnded]) or archiving is asked about
     * after it ended ([followMonth]).
     */
    private var currentMonth: YearMonth =
        saved.get<String>(KEY_CURRENT)?.let(::month) ?: session.currentMonthNow
    private var targetMonth: YearMonth =
        saved.get<String>(KEY_MONTH)?.let(::month)?.takeIf { it <= currentMonth } ?: currentMonth

    private var original: Original? = savedOriginal()
    private var generation: Long? = null
    private val figures = LimitFigures(money)

    /** The month being edited as last read: what the suggestions and the total come from. */
    private var budget: MonthBudget? = null

    /** What other categories are called, as names are compared. */
    private var takenNames: Set<String> = emptySet()

    /** What this month already spent in the category, said when turning carry on. */
    private var spentThisMonth: Money? = null

    /** The month's carry as last read, for the question asked before turning it off. */
    private var carryRead: CarryBreakdown? = null

    /** The drop question was asked by a save's refusal: answering it saves again. */
    private var dropOnSave = false

    /** The balance the last save sent as confirmed, to say when it has moved since. */
    private var sentDrop: Money? = null

    /** The balance a command asked to confirm before dropping it (CP9, CP10). */
    private var pendingDrop: DropPreview? = null

    /** The balance the person confirmed: sent, exactly, with the next attempt. */
    private var confirmedDrop: Money? = null

    /** Which change asked to drop the balance. */
    private var dropReason: DropReason? = null

    /** What came with the list of later months a past limit changes (CP20, SC34). */
    private var pendingConfirmation: CorrectionConfirmation? = null
    private val corrections = CarryCorrectionText(money)

    private val mutable = MutableStateFlow(
        CategoryEditorState(
            editing = true,
            name = typed.name,
            iconKeys = CategoryIcons.keys,
            iconKey = typed.icon,
            unlimited = typed.unlimited,
            limitText = typed.limit,
            limitError = limitReason(typed.unlimited, typed.limit),
            suggestions = LimitFigures(money).suggestions(null),
            targetMonth = targetMonth,
            historical = targetMonth < currentMonth
        )
    )
    val state: StateFlow<CategoryEditorState> = mutable.asStateFlow()

    private val removal = CategoryRemoval(
        saved,
        submission,
        commands,
        previews,
        snapshots,
        money,
        // Deleting applies to today's month, as its preview does: never a month that ended.
        { session.currentMonthNow },
        viewModelScope,
        object : RemovalHost {
            override fun subject(): RemovalSubject? {
                val stored = original ?: return null
                return RemovalSubject(
                    categoryId = categoryId ?: return null,
                    name = stored.name,
                    generation = generation ?: return null,
                    version = stored.version,
                    archived = stored.archived
                )
            }

            override fun show(removal: RemovalState?) {
                mutable.update { it.copy(removal = removal) }
            }

            override fun deleted(notice: CorrectionNotice) {
                notices.categoryChanged(notice)
                mutable.update {
                    it.copy(busy = false, removal = null, result = EditorResult.Deleted)
                }
            }

            override fun failed(error: SaveError) = render { it.copy(saveError = error) }

            override suspend fun reread(): Boolean =
                load(keepOriginal = false, resetDraft = false) && original != null

            override fun askToArchive() = this@CategoryManagementViewModel.askToArchive()
        }
    )

    init {
        saved[KEY_CURRENT] = currentMonth.toString()
        viewModelScope.launch {
            // A restored draft keeps what it was started from; only the rest is read.
            load(keepOriginal = original != null, resetDraft = original == null)
            when {
                submission.pending -> recover()

                // A deletion being asked when the process was killed: asked again, counted anew.
                removal.restorable -> removal.restore()
            }
        }
    }

    fun onAction(action: CategoryEditorAction) {
        val now = mutable.value
        when (action) {
            is CategoryEditorAction.NameChanged -> if (now.editable) {
                remember(typed.copy(name = action.text))
                render { it.copy(name = action.text, nameError = takenError(action.text)) }
            }

            is CategoryEditorAction.IconPicked ->
                if (now.editable && action.key in CategoryIcons.keys) {
                    remember(typed.copy(icon = action.key))
                    render { it.copy(iconKey = action.key) }
                }

            is CategoryEditorAction.UnlimitedChanged ->
                if (now.editable && !now.limitLocked) setLimit(action.unlimited, now.limitText)

            is CategoryEditorAction.LimitChanged ->
                if (now.editable && !now.limitLocked && !now.unlimited) setLimit(false, action.text)

            is CategoryEditorAction.LimitDigit -> if (typesLimit(now)) {
                LimitKeys.press(now.limitText) {
                    it.digit(action.digit)
                }?.let { setLimit(false, it) }
            }

            CategoryEditorAction.LimitTripleZero -> if (typesLimit(now)) {
                LimitKeys.press(now.limitText) { it.tripleZero() }?.let { setLimit(false, it) }
            }

            CategoryEditorAction.LimitBackspace ->
                if (typesLimit(now)) setLimit(false, LimitKeys.backspace(now.limitText))

            CategoryEditorAction.LimitCleared -> if (typesLimit(now)) setLimit(false, "")

            is CategoryEditorAction.SuggestionPicked ->
                if (now.editable && !now.limitLocked) setLimit(false, action.pesos.toString())

            CategoryEditorAction.Submit -> if (now.canSubmit && now.editable) submit()

            CategoryEditorAction.Retry -> when {
                now.busy -> Unit

                submission.pending -> viewModelScope.launch { recover() }

                else -> viewModelScope.launch {
                    load(keepOriginal = original != null, resetDraft = original == null)
                }
            }

            // Borrar categoría starts only on a form that could also archive; the rest
            // answers the slip on screen.
            is RemovalAction ->
                if (action != RemovalAction.Start || now.canDelete) removal.onAction(action)

            CategoryEditorAction.Back -> when {
                now.busy -> Unit
                now.removal != null -> removal.onAction(RemovalAction.Dismiss)
                now.changed -> mutable.update { it.copy(confirmDiscard = true) }
                else -> mutable.update { it.copy(result = EditorResult.Discarded) }
            }

            CategoryEditorAction.DiscardConfirmed ->
                mutable.update { it.copy(confirmDiscard = false, result = EditorResult.Discarded) }

            CategoryEditorAction.KeepEditing -> mutable.update { it.copy(confirmDiscard = false) }

            CategoryEditorAction.Archive -> if (now.canArchive) askToArchive()

            CategoryEditorAction.ArchiveDismissed ->
                mutable.update { it.copy(archiveQuestion = null) }

            CategoryEditorAction.ArchiveConfirmed -> {
                val question = now.archiveQuestion
                when {
                    !now.canArchive || question == null -> Unit

                    // Asked about a month that has ended: asked again about today's.
                    question.currentMonth != session.currentMonthNow -> askToArchive()

                    else -> archive()
                }
            }

            CategoryEditorAction.ReloadKeepingDraft ->
                if (now.saveError == SaveError.Conflict && !now.busy) reload(resetDraft = false)

            CategoryEditorAction.ReloadDiscardingDraft ->
                if (now.saveError == SaveError.Conflict && !now.busy) reload(resetDraft = true)

            CategoryEditorAction.EnableCarry -> if (now.canChangeCarry) {
                when {
                    // Back to what is stored: nothing about carry is left to save.
                    typed.carry == CARRY_OFF -> setCarry("", null)

                    now.carry is CarryCard.Off && typed.carry.isEmpty() -> mutable.update {
                        it.copy(
                            carryProblem = null,
                            carryQuestion = CarryQuestion.Enable(
                                currentMonth,
                                spentThisMonth?.takeIf { spent ->
                                    spent.minorUnits > 0
                                }?.let(money::format)
                            )
                        )
                    }
                }
            }

            CategoryEditorAction.DisableCarry -> if (now.canChangeCarry) {
                when {
                    typed.carry == CARRY_ON -> setCarry("", null)
                    now.carry is CarryCard.On && typed.carry.isEmpty() -> askToTurnOff()
                }
            }

            CategoryEditorAction.CarryConfirmed -> if (!now.busy) answerCarry(now.carryQuestion)

            CategoryEditorAction.CarryDismissed -> if (!now.busy) {
                // Nothing was written: every question is asked before its change.
                pendingDrop = null
                confirmedDrop = null
                dropReason = null
                pendingConfirmation = null
                mutable.update { it.copy(carryQuestion = null) }
            }
        }
    }

    /**
     * What the question shown names, with what the person confirmed. Turning carry on or
     * off only changes the draft; the rest goes on with the save or the archive it asked for.
     */
    private fun answerCarry(question: CarryQuestion?) {
        when (question) {
            null -> Unit

            is CarryQuestion.Enable -> setCarry(CARRY_ON, null)

            is CarryQuestion.Drop -> {
                val balance = pendingDrop?.carryIn ?: return
                pendingDrop = null
                when (question.reason) {
                    DropReason.Disable -> {
                        setCarry(CARRY_OFF, balance)
                        if (dropOnSave) submit()
                    }

                    DropReason.Archive -> {
                        confirmedDrop = balance
                        archive()
                    }

                    DropReason.Unlimited -> {
                        confirmedDrop = balance
                        submit()
                    }
                }
            }

            is CarryQuestion.Correction -> submit()
        }
    }

    /**
     * Turning carry off asks first, with the exact figures, whenever a balance is carried
     * in (CP9, CP10): they come from the month as read, never guessed. Nothing is sent;
     * the save sends the balance confirmed here and storage checks it again.
     */
    private fun askToTurnOff() {
        val carry = carryRead ?: return
        val spent = spentThisMonth ?: return
        dropReason = DropReason.Disable
        dropOnSave = false
        pendingDrop = null
        when (val preview = SignedCarryoverPolicy.dropPreview(currentMonth, carry, spent)) {
            is Outcome.Failure -> render {
                it.copy(carryQuestion = null, carryProblem = CarryProblemText.DropUnavailable)
            }

            is Outcome.Success -> preview.value?.let { askDrop(it, sent = null) }
                ?: setCarry(CARRY_OFF, null)
        }
    }

    /** The draft's carry: [CARRY_ON], [CARRY_OFF] or "" for as stored, with what it forgets. */
    private fun setCarry(carry: String, drop: Money?) {
        remember(typed.copy(carry = carry, drop = drop?.minorUnits?.toString().orEmpty()))
        render { it.copy(carryQuestion = null, carryProblem = null) }
    }

    /** The carry change the draft asks for, only ever for the month in progress. */
    private fun carryChange(): CarrySetting? = when {
        targetMonth != currentMonth -> null
        typed.carry == CARRY_ON -> CarrySetting.On
        typed.carry == CARRY_OFF -> CarrySetting.Off
        else -> null
    }

    /** The balance the draft agreed to forget when turning carry off. */
    private fun draftDrop(): Money? = typed.drop.toLongOrNull()?.let(Money::cop)

    /**
     * Carry is already as the draft asked: the card is read again to say so. Nothing of
     * the save was stored, so with other changes still waiting the form says that too.
     */
    private fun carryReread() {
        val others = original?.let { stored ->
            parts(mutable.value, stored).any { it != EditPart.Carry }
        } ?: false
        viewModelScope.launch {
            pendingDrop = null
            confirmedDrop = null
            dropReason = null
            readCarry()
            render {
                it.copy(
                    busy = false,
                    carryQuestion = null,
                    saveError = SaveError.NotSaved.takeIf { others }
                )
            }
        }
    }

    /**
     * The carry card of the month in progress, from the same snapshot every screen reads.
     * Not offered for a month that is over, and never guessed when it cannot be read.
     */
    private suspend fun readCarry() {
        if (targetMonth != currentMonth) return
        val card = when (val read = snapshots.read(currentMonth)) {
            is Outcome.Failure -> {
                carryRead = null
                CarryCard.Unreadable
            }

            is Outcome.Success -> {
                val line = read.value.categories.firstOrNull { it.category.id == categoryId }
                spentThisMonth = line?.spent
                val carry = line?.budget?.carry
                val since = carry?.chainStart
                carryRead = carry?.takeIf { since != null }
                if (carry == null || since == null) {
                    CarryCard.Off(currentMonth)
                } else {
                    CarryCard.On(
                        since = since,
                        base = money.format(carry.base),
                        carry = money.format(carry.carryIn, signed = true),
                        carryMagnitude = money.magnitude(carry.carryIn),
                        direction = when {
                            carry.carryIn.minorUnits > 0 -> CarryDirection.Surplus
                            carry.carryIn.minorUnits < 0 -> CarryDirection.Deficit
                            else -> CarryDirection.Zero
                        },
                        capacity = money.format(carry.capacity)
                    )
                }
            }
        }
        // A draft that asks for what is stored now has nothing left to save.
        val stored = when (card) {
            is CarryCard.On -> CARRY_ON
            is CarryCard.Off -> CARRY_OFF
            CarryCard.Unreadable -> null
        }
        if (stored != null && typed.carry == stored) remember(typed.copy(carry = "", drop = ""))
        mutable.update { it.copy(carry = card) }
    }

    /**
     * The balance to confirm, shown with its figures (CP10). [sent] is the balance the save
     * just sent as confirmed: when it differs, the question says it moved meanwhile.
     */
    private fun askDrop(preview: DropPreview, sent: Money?) {
        pendingDrop = preview
        confirmedDrop = null
        val figures = DropFigures(
            month = preview.month,
            carry = money.format(preview.carryIn, signed = true),
            base = money.format(preview.base),
            capacityBefore = money.format(preview.capacityBefore),
            capacityAfter = money.format(preview.capacityAfter),
            remainingBefore = money.format(preview.remainingBefore),
            remainingAfter = money.format(preview.remainingAfter),
            changedFrom = sent?.takeIf { it != preview.carryIn }
                ?.let { money.format(it, signed = true) }
        )
        render {
            it.copy(
                busy = false,
                carryQuestion = CarryQuestion.Drop(dropReason ?: DropReason.Disable, figures)
            )
        }
    }

    /** A past month's limit changes later months: the list first (CP20); again if it moved. */
    private fun askCorrection(refusal: CategoryRejection.CorrectionUnconfirmed) {
        val changed = pendingConfirmation != null
        pendingConfirmation = refusal.confirmation
        val names = budget?.categories.orEmpty().associate { it.category.id to it.category }
        val stored = original
        render {
            it.copy(
                busy = false,
                carryQuestion = CarryQuestion.Correction(
                    month = targetMonth,
                    before = (stored?.limit as? StoredLimit.Finite)?.let { limit ->
                        money.format(Money.cop(limit.pesos))
                    },
                    after = (draftLimit(it) as? StoredLimit.Finite)?.let { limit ->
                        money.format(Money.cop(limit.pesos))
                    },
                    list = corrections.list(refusal.correction, names),
                    changed = changed
                )
            )
        }
    }

    /** The pad types the limit only while it can change and "Sin límite" is off. */
    private fun typesLimit(state: CategoryEditorState) =
        state.editable && !state.limitLocked && !state.unlimited

    private fun month(text: String): YearMonth? = runCatching { YearMonth.parse(text) }.getOrNull()

    private fun remember(next: Draft) {
        typed = next
        drafts.write(next)
    }

    private fun setLimit(unlimited: Boolean, text: String) {
        // Carry cannot start on an unlimited month (CP3): a draft turning it on gives way.
        val carry = if (unlimited && typed.carry == CARRY_ON) "" else typed.carry
        remember(typed.copy(unlimited = unlimited, limit = text, carry = carry))
        val reason = limitReason(unlimited, text)
        render { it.copy(unlimited = unlimited, limitText = text, limitError = reason) }
    }

    /**
     * [LimitEntry.reason], except for the stored limit, which is not being set: one stored
     * above the maximum is shown with no reason and left as it is.
     */
    private fun limitReason(unlimited: Boolean, text: String): LimitError? {
        val stored = (original?.limit as? StoredLimit.Finite)?.pesos
        val written = (CopAmountInput.parse(text) as? Outcome.Success)?.value?.minorUnits
        if (written != null && written == stored) return null
        return LimitEntry.reason(unlimited, text)
    }

    /** Said while typing: a name another category has, however it is written, cannot be saved. */
    private fun takenError(text: String): NameError? {
        val name = (CategoryName.fromInput(text, original?.name) as? Outcome.Success)?.value
            ?: return null
        return NameError.Taken.takeIf { name.uniquenessKey in takenNames }
    }

    /** Updates the state and then what follows from it: whether anything differs from storage. */
    private fun render(change: (CategoryEditorState) -> CategoryEditorState) {
        mutable.update { before ->
            val after = change(before)
            val stored = original
            after.copy(
                changed = stored != null && parts(after, stored).isNotEmpty(),
                archived = stored?.archived ?: false,
                // An archived category has no limit from this month onwards to change.
                limitLocked = stored != null && stored.archived && targetMonth == currentMonth,
                limitTotal = figures.total(budget, categoryId, after.unlimited, after.limitText),
                limitAmount = figures.amount(after.limitText),
                carryPending = carryChange()?.let { change ->
                    CarryPending(
                        on = change == CarrySetting.On,
                        month = currentMonth,
                        forgets = draftDrop()?.let { money.format(it, signed = true) }
                    )
                }
            )
        }
    }

    /** The limit the draft asks for, or null when what is typed is not one. */
    private fun draftLimit(state: CategoryEditorState): StoredLimit? = when {
        state.unlimited -> StoredLimit.Unlimited

        state.limitText.isBlank() -> StoredLimit.None

        else -> (CopAmountInput.parse(state.limitText) as? Outcome.Success)?.value
            ?.takeIf { BudgetLimit.finite(it) is Outcome.Success }
            ?.let { StoredLimit.Finite(it.minorUnits) }
    }

    /** What differs from storage, in the order it is saved. */
    private fun parts(state: CategoryEditorState, stored: Original): List<EditPart> = buildList {
        val name = (CategoryName.of(state.name) as? Outcome.Success)?.value?.value
        if (name != stored.name) add(EditPart.Name)
        if (state.iconKey != stored.icon) add(EditPart.Icon)
        val locked = stored.archived && targetMonth == currentMonth
        if (!locked && draftLimit(state) != stored.limit) add(EditPart.Limit)
        if (carryChange() != null) add(EditPart.Carry)
    }

    private fun savedOriginal(): Original? {
        val stored = kept.read() ?: return null
        return Original(
            version = stored.version,
            name = stored.name,
            icon = stored.icon,
            archived = stored.archived,
            limit = when (val limit = stored.limit) {
                null -> StoredLimit.None
                UNLIMITED -> StoredLimit.Unlimited
                else -> StoredLimit.Finite(limit.toLongOrNull() ?: return null)
            },
            paused = stored.paused
        )
    }

    private fun keep(stored: Original) {
        original = stored
        kept.write(
            Kept(
                version = stored.version,
                name = stored.name,
                icon = stored.icon,
                archived = stored.archived,
                paused = stored.paused,
                limit = when (val limit = stored.limit) {
                    StoredLimit.None -> null
                    StoredLimit.Unlimited -> UNLIMITED
                    is StoredLimit.Finite -> limit.pesos.toString()
                }
            )
        )
    }

    private fun CategoryBudget.asOriginal() = Original(
        version = category.version,
        name = category.name.value,
        icon = category.icon.value,
        archived = category.archived,
        limit = when (val state = state) {
            is MonthBudgetState.Limited -> when (val limit = state.limit) {
                is BudgetLimit.Finite -> StoredLimit.Finite(limit.amount.minorUnits)
                BudgetLimit.Unlimited -> StoredLimit.Unlimited
            }

            MonthBudgetState.Paused, MonthBudgetState.Unconfigured -> StoredLimit.None
        },
        paused = state == MonthBudgetState.Paused
    )

    private fun draftFrom(stored: Original) {
        val unlimited = stored.limit == StoredLimit.Unlimited
        val text = (stored.limit as? StoredLimit.Finite)?.pesos?.toString().orEmpty()
        remember(Draft(stored.name, stored.icon, unlimited, text))
        pendingDrop = null
        confirmedDrop = null
        mutable.update {
            it.copy(
                name = stored.name,
                iconKey = stored.icon,
                unlimited = unlimited,
                limitText = text,
                nameError = null,
                limitError = null
            )
        }
    }

    /**
     * Reads the generation, the other categories' names and, unless [keepOriginal],
     * the category itself as it is in the month being edited. With [resetDraft] the
     * draft starts again from what was read. Returns false when nothing could be read.
     */
    private suspend fun load(keepOriginal: Boolean, resetDraft: Boolean): Boolean {
        mutable.update { it.copy(loading = true, loadFailed = false) }
        val read = when (val read = reads.monthBudget(targetMonth)) {
            is Outcome.Failure -> {
                mutable.update { it.copy(loading = false, loadFailed = true) }
                return false
            }

            is Outcome.Success -> read.value
        }
        generation = read.generation
        budget = read
        val others = read.categories.filter { it.category.id != categoryId }
        mutable.update {
            it.copy(
                suggestions = figures.suggestions(read),
                usedIconKeys = others.map { other -> other.category.icon.value }.toSet()
            )
        }
        takenNames = others.map { it.category.name.uniquenessKey }.toSet()
        val found = read.categories.firstOrNull { it.category.id == categoryId }
        if (!keepOriginal) {
            if (found == null) {
                mutable.update { it.copy(loading = false, saveError = SaveError.CategoryGone) }
                return true
            }
            keep(found.asOriginal())
        }
        if (resetDraft) original?.let(::draftFrom)
        readCarry()
        render { it.copy(loading = false, nameError = takenError(it.name)) }
        return true
    }

    private fun reload(resetDraft: Boolean) {
        mutable.update { it.copy(saveError = null) }
        viewModelScope.launch { load(keepOriginal = false, resetDraft = resetDraft) }
    }

    private fun submit() {
        val state = mutable.value
        val stored = original ?: return
        val generation = generation ?: return
        val id = categoryId ?: return
        val name = when (val name = CategoryName.fromInput(state.name, stored.name)) {
            is Outcome.Success -> name.value

            is Outcome.Failure -> return mutable.update {
                it.copy(
                    nameError = when (name.error) {
                        TextError.Required -> NameError.Required
                        is TextError.TooLong -> NameError.TooLong
                        TextError.ControlCharacter -> NameError.NotAllowed
                    }
                )
            }
        }
        val pending = parts(state, stored)
        val limit = if (EditPart.Limit in pending) {
            when (val asked = draftLimit(state)) {
                null -> return mutable.update { it.copy(limitError = LimitError.NotAnAmount) }

                // A month that has a limit cannot be left without one.
                StoredLimit.None -> return mutable.update {
                    it.copy(limitError = LimitError.Required)
                }

                StoredLimit.Unlimited -> BudgetLimit.Unlimited

                // Only a changed limit is set: one stored above the maximum still reads.
                is StoredLimit.Finite -> when (val entered = LimitEntry.limit(state.limitText)) {
                    is Outcome.Success -> entered.value

                    is Outcome.Failure ->
                        return mutable.update { it.copy(limitError = entered.error) }
                }
            }
        } else {
            null
        }
        val icon = (IconKey.of(state.iconKey) as? Outcome.Success)?.value ?: return
        val carry = carryChange()
        if ((EditPart.Limit in pending || carry != null) && monthEnded(carry = carry != null)) {
            return
        }
        // Turning off, or making the limit unlimited while carrying, drops the balance
        // (CP9–CP11): the draft's confirmed balance goes with it, or the save asks first.
        dropReason = when {
            carry == CarrySetting.Off -> DropReason.Disable
            limit == BudgetLimit.Unlimited -> DropReason.Unlimited
            else -> null
        }
        val confirmed = if (carry == CarrySetting.Off) draftDrop() else confirmedDrop
        val edit = { operation: OperationId ->
            EditCategory(
                operationId = operation,
                generation = generation,
                categoryId = id,
                expectedVersion = stored.version,
                month = currentMonth,
                name = name.takeIf { EditPart.Name in pending },
                icon = icon.takeIf { EditPart.Icon in pending },
                limit = limit?.let {
                    if (targetMonth < currentMonth) {
                        LimitEdit.OfMonth(targetMonth, it)
                    } else {
                        LimitEdit.FromThisMonth(it)
                    }
                },
                carry = carry,
                confirmedCarryIn = confirmed,
                confirmation = pendingConfirmation
            )
        }
        mutable.update { it.copy(busy = true, saveError = null, carryProblem = null) }
        viewModelScope.launch {
            sentDrop = confirmed
            val settled = submission.send(commands::find, STEP_EDIT) { operation ->
                commands.submit(edit(operation))
            }
            if (!committed(settled)) return@launch
            // Every part is stored now, at the next version.
            keep(
                stored.copy(
                    name = name.value,
                    icon = icon.value,
                    limit = if (EditPart.Limit in pending) draftLimit(state)!! else stored.limit,
                    version = stored.version + 1
                )
            )
            pendingConfirmation = null
            confirmedDrop = null
            pendingDrop = null
            remember(typed.copy(carry = "", drop = ""))
            finished(carry, limitSaved = EditPart.Limit in pending)
        }
    }

    /**
     * Whether the month this form shows as in progress has ended. A limit from it onwards
     * and turning carry on are applied to today's month, so neither is sent then: the
     * form says so and reads the month now in progress, keeping what was typed. A
     * finished month's own limit names its month and is not affected.
     */
    private fun monthEnded(carry: Boolean): Boolean {
        val ended = currentMonth
        val now = session.currentMonthNow
        if (targetMonth != ended || now == ended) return false
        moveTo(now)
        render { it.copy(saveError = SaveError.MonthEnded, monthEnded = MonthEnded(ended, carry)) }
        viewModelScope.launch { load(keepOriginal = false, resetDraft = false) }
        return true
    }

    /**
     * Before archiving is asked about: once the month this form shows as in progress has
     * ended, the form moves on to today's month and reads it, since archiving applies to
     * today's month. False when it could not be read; the form then says so.
     */
    private suspend fun followMonth(): Boolean {
        val now = session.currentMonthNow
        if (now == currentMonth) return true
        if (targetMonth != currentMonth) {
            // A finished month's form stays on its month; only "today" moves on.
            currentMonth = now
            saved[KEY_CURRENT] = now.toString()
            return true
        }
        moveTo(now)
        return load(keepOriginal = false, resetDraft = false)
    }

    /** The form shows [now] as the month in progress; what was asked about another is gone. */
    private fun moveTo(now: YearMonth) {
        currentMonth = now
        targetMonth = now
        saved[KEY_CURRENT] = now.toString()
        saved[KEY_MONTH] = now.toString()
        pendingDrop = null
        confirmedDrop = null
        dropReason = null
        pendingConfirmation = null
        // A carry change was asked about the month that ended ("empieza en 0 en …"), so it
        // is not carried over to the next one: it is chosen again there.
        remember(typed.copy(carry = "", drop = ""))
        render {
            it.copy(
                busy = false,
                targetMonth = now,
                historical = false,
                carry = null,
                carryQuestion = null,
                carryProblem = null
            )
        }
    }

    /**
     * The save has its receipt. [limitSaved] says it set the limit, and [carry] whether it
     * turned carry on or off: the notice says so.
     */
    private fun finished(carry: CarrySetting?, limitSaved: Boolean) {
        notices.categoryChanged(
            CorrectionNotice.CategorySaved(
                limitOf = targetMonth.takeIf { limitSaved && it < currentMonth },
                carry = carry?.let {
                    SavedCarry(original?.name.orEmpty(), it == CarrySetting.On, currentMonth)
                }
            )
        )
        mutable.update { it.copy(busy = false, saveError = null, result = EditorResult.Updated) }
    }

    /**
     * Whether [settled] is a receipt. If not, the form is left saying why and nothing
     * further is sent. An outcome that is not known was asked about, never sent again.
     */
    private fun committed(settled: Settled<CommittedCategoryChange, CategoryRejection>?): Boolean {
        val rejection = when (settled) {
            // Something is still in flight and will settle the form.
            null -> return false

            is Settled.Committed -> return true

            is Settled.Unknown -> {
                render { it.copy(busy = false, saveError = SaveError.Unknown) }
                return false
            }

            Settled.NotCommitted -> null

            is Settled.Rejected -> settled.reason
        }
        // Certain: this change was not written, so this attempt is over.
        if (rejection == Specific(CategoryRejection.NameTaken)) {
            render { it.copy(busy = false, nameError = NameError.Taken) }
            return false
        }
        val carryProblem = when (val specific = (rejection as? Specific)?.reason) {
            // The balance moved since it was confirmed, or the save drops one not asked about.
            is CategoryRejection.DropUnconfirmed -> return false.also {
                dropOnSave = true
                askDrop(specific.preview, sent = sentDrop)
            }

            // Storage is already in another month: the form follows it and says so.
            is CategoryRejection.MonthMoved -> return false.also {
                if (!monthEnded(carry = carryChange() != null)) {
                    render { it.copy(busy = false, saveError = SaveError.NotSaved) }
                }
            }

            is CategoryRejection.CorrectionUnconfirmed -> return false.also {
                askCorrection(specific)
            }

            // Carry is already as asked: the card is read again to say so.
            CategoryRejection.NotCarrying -> return false.also { carryReread() }

            is CategoryRejection.CarryRefused -> when (specific.reason) {
                CarryEnableRefusal.AlreadyOn -> return false.also { carryReread() }
                CarryEnableRefusal.Archived -> CarryProblemText.Archived
                CarryEnableRefusal.UnlimitedThisMonth -> CarryProblemText.UnlimitedThisMonth
                CarryEnableRefusal.NoLimitThisMonth -> CarryProblemText.NoLimitThisMonth
                CarryEnableRefusal.DefaultAfterNotFinite -> CarryProblemText.DefaultAfterNotFinite
                is CarryEnableRefusal.BaseAboveLimit -> CarryProblemText.BaseAboveLimit
            }

            is CategoryRejection.ChainBaseAboveLimit -> CarryProblemText.LimitAboveCap

            is CategoryRejection.ChainMonthUnlimited -> CarryProblemText.MonthUnlimited

            is CategoryRejection.CarryUnavailable ->
                if (dropReason == null) {
                    CarryProblemText.Unavailable
                } else {
                    CarryProblemText.DropUnavailable
                }

            else -> null
        }
        if (carryProblem != null) {
            pendingDrop = null
            confirmedDrop = null
            render { it.copy(busy = false, carryQuestion = null, carryProblem = carryProblem) }
            return false
        }
        val error = when (rejection) {
            Specific(CategoryRejection.CategoryMissing) -> SaveError.CategoryGone

            is CommandRejection.StaleGeneration -> SaveError.DataChanged

            // Each of these says the category is no longer what this form read.
            is CommandRejection.VersionConflict,
            Specific(CategoryRejection.CategoryArchived),
            Specific(CategoryRejection.AlreadyArchived),
            Specific(CategoryRejection.NotArchived) -> SaveError.Conflict

            else -> SaveError.NotSaved
        }
        render { it.copy(busy = false, saveError = error) }
        return false
    }

    /**
     * Finds out how the saved operation ended, then reads the category again: whatever
     * the draft still has that storage does not is what remains to be saved.
     */
    private suspend fun recover() {
        if (!submission.pending) return
        val step = submission.step
        if (step == CategoryRemoval.STEP_DELETE) return removal.recover()
        mutable.update { it.copy(busy = true, saveError = null) }
        val committed = when (submission.recover(commands::find)) {
            null -> return render { it.copy(busy = false) }

            is Settled.Unknown ->
                return render { it.copy(busy = false, saveError = SaveError.Unknown) }

            is Settled.Committed -> true

            Settled.NotCommitted -> false
        }
        if (committed && step != STEP_EDIT) return archived(unarchived = step == STEP_UNARCHIVE)
        // What the save asked for, before reading again settles the draft against storage.
        val carry = carryChange()
        val limitSaved = mutable.value.let { state ->
            original?.let { EditPart.Limit in parts(state, it) } ?: false
        }
        if (!load(keepOriginal = false, resetDraft = false)) return render { it.copy(busy = false) }
        when {
            committed && !mutable.value.changed -> finished(carry, limitSaved)
            committed -> render { it.copy(busy = false) }
            else -> render { it.copy(busy = false, saveError = SaveError.NotSaved) }
        }
    }

    private fun askToArchive() {
        viewModelScope.launch {
            mutable.update { it.copy(archiveQuestion = null) }
            if (!followMonth() || !mutable.value.canArchive) return@launch
            val stored = original ?: return@launch
            // Counted from the month as it is shown everywhere else. If it cannot be
            // read the question is asked without a number, never with a made-up one.
            val count = (snapshots.read(currentMonth) as? Outcome.Success)?.value?.categories
                ?.firstOrNull { it.category.id == categoryId }?.expenseCount
            mutable.update {
                it.copy(
                    archiveQuestion = ArchiveQuestion(
                        unarchive = stored.archived,
                        expensesThisMonth = count,
                        currentMonth = currentMonth,
                        resumesBudget = stored.archived && stored.paused,
                        discardsChanges = it.changed
                    )
                )
            }
        }
    }

    private fun archive() {
        val stored = original ?: return
        val generation = generation ?: return
        val id = categoryId ?: return
        mutable.update {
            it.copy(busy = true, saveError = null, archiveQuestion = null, carryProblem = null)
        }
        dropReason = DropReason.Archive.takeUnless { stored.archived }
        viewModelScope.launch {
            if (stored.archived) {
                val settled = submission.send(commands::find, STEP_UNARCHIVE) { operation ->
                    commands.submit(UnarchiveCategory(operation, generation, id, stored.version))
                }
                if (committed(settled)) archived(unarchived = true)
                return@launch
            }
            val archive: suspend (OperationId) -> CategoryCommandOutcome = { operation ->
                commands.submit(
                    ArchiveCategory(operation, generation, id, stored.version, confirmedDrop)
                )
            }
            var settled = submission.send(commands::find, STEP_ARCHIVE, archive)
            if (settled.waitsForPayments()) {
                // A fixed payment of the category is due and not recorded yet (rule 9):
                // Sello records what is due, then archives under a new operation, since
                // the refused one wrote nothing.
                if (catchUp.run().stopped != null) {
                    render { it.copy(busy = false, saveError = SaveError.PaymentsPending) }
                    return@launch
                }
                settled = submission.send(commands::find, STEP_ARCHIVE, archive)
                if (settled.waitsForPayments()) {
                    render { it.copy(busy = false, saveError = SaveError.PaymentsPending) }
                    return@launch
                }
            }
            if (committed(settled)) archived(unarchived = false)
        }
    }

    private fun Settled<*, CategoryRejection>?.waitsForPayments() =
        this is Settled.Rejected && reason == Specific(CategoryRejection.SchedulesPending)

    private fun archived(unarchived: Boolean) {
        notices.categoryChanged(
            if (unarchived) {
                CorrectionNotice.CategoryUnarchived
            } else {
                CorrectionNotice.CategoryArchived
            }
        )
        mutable.update {
            it.copy(
                busy = false,
                saveError = null,
                result = if (unarchived) EditorResult.Unarchived else EditorResult.Archived
            )
        }
    }

    companion object {
        /** Route arguments: the category to edit and the month it was opened from. */
        const val KEY_CATEGORY = "categoryId"
        const val KEY_MONTH = "month"

        private const val KEY_CURRENT = "category.edit.currentMonth"
        private const val KEY_DRAFT = "category.edit.draft"
        private const val KEY_ORIGINAL = "category.edit.original"
        private const val KEY_OPERATION = "category.edit.operation"
        private const val UNLIMITED = "unlimited"
        private const val STEP_EDIT = "edit"
        private const val STEP_ARCHIVE = "archive"
        private const val STEP_UNARCHIVE = "unarchive"

        private const val CARRY_ON = "on"
        private const val CARRY_OFF = "off"
    }
}
