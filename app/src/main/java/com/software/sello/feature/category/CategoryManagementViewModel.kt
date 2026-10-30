package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.BudgetLimit
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
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryBudget
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.ChangeCategoryIcon
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.DisableCarry
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.MonthBudget
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import com.software.sello.domain.usecase.CatchUpNow
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.carry.CarryCorrectionText
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
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
 * Each part that changed is its own command and its own receipt, sent one after the
 * other, each against the version the one before left. If one fails, those before it
 * are saved and stay saved; the form says which, keeps the rest of the draft, and the
 * next attempt sends only what is still different. A change is reported as saved only
 * when its receipt says so.
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
    private val catchUp: CatchUpNow
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
        val limit: String = ""
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

    /** Fixed when the form opens, so a draft restored next month is still about this one. */
    private val currentMonth: YearMonth =
        saved.get<String>(KEY_CURRENT)?.let(::month) ?: session.currentMonthNow
    private val targetMonth: YearMonth =
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
            suggestions = LimitFigures(money).suggestions(null),
            targetMonth = targetMonth,
            historical = targetMonth < currentMonth
        )
    )
    val state: StateFlow<CategoryEditorState> = mutable.asStateFlow()

    init {
        saved[KEY_CURRENT] = currentMonth.toString()
        viewModelScope.launch {
            // A restored draft keeps what it was started from; only the rest is read.
            load(keepOriginal = original != null, resetDraft = original == null)
            if (submission.pending) recover()
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
                if (now.editable && !now.limitLocked && !now.unlimited) {
                    setLimit(false, action.text, error = pastedError(action.text))
                }

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

            CategoryEditorAction.Back -> when {
                now.busy -> Unit
                now.changed -> mutable.update { it.copy(confirmDiscard = true) }
                else -> mutable.update { it.copy(result = EditorResult.Discarded) }
            }

            CategoryEditorAction.DiscardConfirmed ->
                mutable.update { it.copy(confirmDiscard = false, result = EditorResult.Discarded) }

            CategoryEditorAction.KeepEditing -> mutable.update { it.copy(confirmDiscard = false) }

            CategoryEditorAction.Archive -> if (now.canArchive) askToArchive()

            CategoryEditorAction.ArchiveDismissed ->
                mutable.update { it.copy(archiveQuestion = null) }

            CategoryEditorAction.ArchiveConfirmed ->
                if (now.canArchive && now.archiveQuestion != null) archive()

            CategoryEditorAction.ReloadKeepingDraft ->
                if (now.saveError == SaveError.Conflict && !now.busy) reload(resetDraft = false)

            CategoryEditorAction.ReloadDiscardingDraft ->
                if (now.saveError == SaveError.Conflict && !now.busy) reload(resetDraft = true)

            CategoryEditorAction.EnableCarry -> if (now.canChangeCarry &&
                now.carry is CarryCard.Off
            ) {
                mutable.update {
                    it.copy(
                        carryProblem = null,
                        carryDone = null,
                        carryQuestion = CarryQuestion.Enable(
                            currentMonth,
                            spentThisMonth?.takeIf { spent ->
                                spent.minorUnits > 0
                            }?.let(money::format)
                        )
                    )
                }
            }

            CategoryEditorAction.DisableCarry -> if (now.canChangeCarry &&
                now.carry is CarryCard.On
            ) {
                dropReason = DropReason.Disable
                confirmedDrop = null
                sendCarry(enable = false)
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

    /** Sends what the question shown names, with what the person confirmed. */
    private fun answerCarry(question: CarryQuestion?) {
        when (question) {
            null -> Unit

            is CarryQuestion.Enable -> sendCarry(enable = true)

            is CarryQuestion.Drop -> {
                confirmedDrop = pendingDrop?.carryIn ?: return
                when (question.reason) {
                    DropReason.Disable -> sendCarry(enable = false)
                    DropReason.Archive -> archive()
                    DropReason.Unlimited -> submit()
                }
            }

            is CarryQuestion.Correction -> submit()
        }
    }

    /**
     * Turns carry on, or off with the balance confirmed, as its own command and receipt
     * (CP28). Only a receipt changes the card; a refusal says why in it.
     */
    private fun sendCarry(enable: Boolean) {
        val stored = original ?: return
        val generation = generation ?: return
        val id = categoryId ?: return
        mutable.update {
            it.copy(busy = true, saveError = null, carryProblem = null, carryDone = null)
        }
        viewModelScope.launch {
            val settled = submission.send(commands::find, STEP_CARRY) { operation ->
                commands.submit(
                    if (enable) {
                        EnableCarry(operation, generation, id, stored.version)
                    } else {
                        DisableCarry(operation, generation, id, stored.version, confirmedDrop)
                    }
                )
            }
            if (!committed(settled)) return@launch
            keep(stored.copy(version = stored.version + 1))
            carryChanged(if (enable) CarryDone.Enabled else CarryDone.Disabled)
        }
    }

    private fun carryReread() {
        viewModelScope.launch { carryChanged(null) }
    }

    /** A carry change has its receipt: the card is read again from the month. */
    private suspend fun carryChanged(done: CarryDone?) {
        pendingDrop = null
        confirmedDrop = null
        dropReason = null
        readCarry()
        render { it.copy(busy = false, carryQuestion = null, carryDone = done) }
    }

    /**
     * The carry card of the month in progress, from the same snapshot every screen reads.
     * Not offered for a month that is over, and never guessed when it cannot be read.
     */
    private suspend fun readCarry() {
        if (targetMonth != currentMonth) return
        val card = when (val read = snapshots.read(currentMonth)) {
            is Outcome.Failure -> CarryCard.Unreadable

            is Outcome.Success -> {
                val line = read.value.categories.firstOrNull { it.category.id == categoryId }
                spentThisMonth = line?.spent
                val carry = line?.budget?.carry
                val since = carry?.chainStart
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
        mutable.update { it.copy(carry = card) }
    }

    /** The command answered with the balance to confirm: shown with its figures (CP10). */
    private fun askDrop(preview: DropPreview) {
        val before = pendingDrop
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
            changedFrom = before?.takeIf { it.carryIn != preview.carryIn }
                ?.let { money.format(it.carryIn, signed = true) }
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

    private fun setLimit(unlimited: Boolean, text: String, error: LimitError? = null) {
        remember(typed.copy(unlimited = unlimited, limit = text))
        render { it.copy(unlimited = unlimited, limitText = text, limitError = error) }
    }

    /** A pasted limit too large to set, unless it is the stored one, which is not being set. */
    private fun pastedError(text: String): LimitError? {
        val stored = (original?.limit as? StoredLimit.Finite)?.pesos
        val pasted = (CopAmountInput.parse(text) as? Outcome.Success)?.value?.minorUnits
        return if (pasted != null && pasted == stored) null else LimitEntry.pasted(text)
    }

    /** Said while typing: a name another category has, however it is written, cannot be saved. */
    private fun takenError(text: String): NameError? {
        val name = (CategoryName.of(text) as? Outcome.Success)?.value ?: return null
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
                limitAmount = figures.amount(after.limitText)
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
        mutable.update { it.copy(saveError = null, savedParts = emptyList()) }
        viewModelScope.launch { load(keepOriginal = false, resetDraft = resetDraft) }
    }

    private fun submit() {
        val state = mutable.value
        val stored = original ?: return
        val generation = generation ?: return
        val id = categoryId ?: return
        val name = when (val name = CategoryName.of(state.name)) {
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
        // Making the limit unlimited while carrying drops the balance (CP11): asked first.
        dropReason = DropReason.Unlimited.takeIf { limit == BudgetLimit.Unlimited }
        mutable.update {
            it.copy(
                busy = true,
                saveError = null,
                savedParts = emptyList(),
                carryProblem = null,
                carryDone = null
            )
        }
        viewModelScope.launch {
            for (part in pending) {
                val version = original?.version ?: return@launch
                val settled = submission.send(commands::find, STEP_EDIT) { operation ->
                    val command: CategoryCommand = when (part) {
                        EditPart.Name -> RenameCategory(operation, generation, id, version, name)

                        EditPart.Icon ->
                            ChangeCategoryIcon(operation, generation, id, version, icon)

                        EditPart.Limit -> if (targetMonth < currentMonth) {
                            SetMonthLimit(
                                operation,
                                generation,
                                id,
                                version,
                                targetMonth,
                                limit!!,
                                confirmation = pendingConfirmation
                            )
                        } else {
                            SetDefaultLimit(
                                operation,
                                generation,
                                id,
                                version,
                                limit!!,
                                confirmedCarryIn = confirmedDrop
                            )
                        }
                    }
                    commands.submit(command)
                }
                if (!committed(settled)) return@launch
                // This part is stored now, at the next version.
                original?.let { before ->
                    keep(
                        when (part) {
                            EditPart.Name -> before.copy(name = name.value)
                            EditPart.Icon -> before.copy(icon = icon.value)
                            EditPart.Limit -> before.copy(limit = draftLimit(state)!!)
                        }.copy(version = version + 1)
                    )
                }
                if (part == EditPart.Limit) {
                    pendingConfirmation = null
                    confirmedDrop = null
                    pendingDrop = null
                }
                render { it.copy(savedParts = it.savedParts + part, carryQuestion = null) }
            }
            finished()
        }
    }

    /** Every change has its receipt. */
    private fun finished() {
        notices.categoryChanged(
            CorrectionNotice.CategorySaved(
                targetMonth.takeIf {
                    it < currentMonth && EditPart.Limit in mutable.value.savedParts
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
            is CategoryRejection.DropUnconfirmed -> return false.also { askDrop(specific.preview) }

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
        mutable.update { it.copy(busy = true, saveError = null) }
        val committed = when (submission.recover(commands::find)) {
            null -> return render { it.copy(busy = false) }

            is Settled.Unknown ->
                return render { it.copy(busy = false, saveError = SaveError.Unknown) }

            is Settled.Committed -> true

            Settled.NotCommitted -> false
        }
        if (step == STEP_CARRY) {
            // Whatever it was, the card says what the month is now.
            if (!load(keepOriginal = false, resetDraft = false)) {
                return render { it.copy(busy = false) }
            }
            return render { it.copy(busy = false, saveError = null) }
        }
        if (committed && step != STEP_EDIT) return archived(unarchived = step == STEP_UNARCHIVE)
        if (!load(keepOriginal = false, resetDraft = false)) return render { it.copy(busy = false) }
        when {
            committed && !mutable.value.changed -> finished()
            committed -> render { it.copy(busy = false) }
            else -> render { it.copy(busy = false, saveError = SaveError.NotSaved) }
        }
    }

    private fun askToArchive() {
        val stored = original ?: return
        viewModelScope.launch {
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
        private const val STEP_CARRY = "carry"
    }
}
