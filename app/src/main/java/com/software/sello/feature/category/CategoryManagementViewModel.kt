package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
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
import com.software.sello.domain.port.MonthBudget
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import com.software.sello.domain.usecase.CatchUpNow
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    money: MoneyFormatter,
    private val notices: ExpenseCorrections,
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

    private val mutable = MutableStateFlow(
        CategoryEditorState(
            editing = true,
            name = saved[KEY_NAME] ?: "",
            iconKeys = CategoryIcons.keys,
            iconKey = saved[KEY_ICON] ?: "",
            unlimited = saved[KEY_UNLIMITED] ?: false,
            limitText = saved[KEY_LIMIT] ?: "",
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
            if (saved.get<String>(KEY_OPERATION) != null) recover()
        }
    }

    fun onAction(action: CategoryEditorAction) {
        val now = mutable.value
        when (action) {
            is CategoryEditorAction.NameChanged -> if (now.editable) {
                saved[KEY_NAME] = action.text
                render { it.copy(name = action.text, nameError = takenError(action.text)) }
            }

            is CategoryEditorAction.IconPicked ->
                if (now.editable && action.key in CategoryIcons.keys) {
                    saved[KEY_ICON] = action.key
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

                saved.get<String>(KEY_OPERATION) != null -> viewModelScope.launch { recover() }

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
        }
    }

    /** The pad types the limit only while it can change and "Sin límite" is off. */
    private fun typesLimit(state: CategoryEditorState) =
        state.editable && !state.limitLocked && !state.unlimited

    private fun month(text: String): YearMonth? = runCatching { YearMonth.parse(text) }.getOrNull()

    private fun setLimit(unlimited: Boolean, text: String) {
        saved[KEY_UNLIMITED] = unlimited
        saved[KEY_LIMIT] = text
        render { it.copy(unlimited = unlimited, limitText = text, limitError = null) }
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
        val version = saved.get<Long>(KEY_ORIGINAL_VERSION) ?: return null
        return Original(
            version = version,
            name = saved.get<String>(KEY_ORIGINAL_NAME) ?: return null,
            icon = saved.get<String>(KEY_ORIGINAL_ICON) ?: return null,
            archived = saved.get<Boolean>(KEY_ORIGINAL_ARCHIVED) ?: return null,
            limit = when (val limit = saved.get<String>(KEY_ORIGINAL_LIMIT)) {
                null -> StoredLimit.None
                UNLIMITED -> StoredLimit.Unlimited
                else -> StoredLimit.Finite(limit.toLongOrNull() ?: return null)
            },
            paused = saved.get<Boolean>(KEY_ORIGINAL_PAUSED) ?: false
        )
    }

    private fun keep(stored: Original) {
        original = stored
        saved[KEY_ORIGINAL_VERSION] = stored.version
        saved[KEY_ORIGINAL_NAME] = stored.name
        saved[KEY_ORIGINAL_ICON] = stored.icon
        saved[KEY_ORIGINAL_ARCHIVED] = stored.archived
        saved[KEY_ORIGINAL_PAUSED] = stored.paused
        saved[KEY_ORIGINAL_LIMIT] = when (val limit = stored.limit) {
            StoredLimit.None -> null
            StoredLimit.Unlimited -> UNLIMITED
            is StoredLimit.Finite -> limit.pesos.toString()
        }
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
        saved[KEY_NAME] = stored.name
        saved[KEY_ICON] = stored.icon
        saved[KEY_UNLIMITED] = unlimited
        saved[KEY_LIMIT] = text
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
        render { it.copy(loading = false, nameError = takenError(it.name)) }
        return true
    }

    private fun reload(resetDraft: Boolean) {
        mutable.update { it.copy(saveError = null, savedParts = emptyList()) }
        viewModelScope.launch { load(keepOriginal = false, resetDraft = resetDraft) }
    }

    private fun operation(step: String): OperationId {
        // The identifier is saved before the command leaves, so whatever happens next
        // this attempt can be recognised and never made twice.
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[KEY_OPERATION] = operation.value
        saved[KEY_STEP] = step
        return operation
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

                is StoredLimit.Finite ->
                    (BudgetLimit.finite(Money.cop(asked.pesos)) as Outcome.Success).value
            }
        } else {
            null
        }
        val icon = (IconKey.of(state.iconKey) as? Outcome.Success)?.value ?: return
        mutable.update { it.copy(busy = true, saveError = null, savedParts = emptyList()) }
        viewModelScope.launch {
            for (part in pending) {
                val version = original?.version ?: return@launch
                val operation = operation(STEP_EDIT)
                val command: CategoryCommand = when (part) {
                    EditPart.Name -> RenameCategory(operation, generation, id, version, name)

                    EditPart.Icon -> ChangeCategoryIcon(operation, generation, id, version, icon)

                    EditPart.Limit -> if (targetMonth < currentMonth) {
                        SetMonthLimit(operation, generation, id, version, targetMonth, limit!!)
                    } else {
                        SetDefaultLimit(operation, generation, id, version, limit!!)
                    }
                }
                if (!committed(commands.submit(command), operation)) return@launch
                saved[KEY_OPERATION] = null
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
                render { it.copy(savedParts = it.savedParts + part) }
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
     * Whether [outcome] is a receipt. If not, the form is left saying why and nothing
     * further is sent. An outcome that is not known is asked about, never sent again.
     */
    private suspend fun committed(
        outcome: CategoryCommandOutcome,
        operation: OperationId
    ): Boolean {
        val rejection = when (outcome) {
            is CategoryCommandOutcome.Committed -> return true

            is CategoryCommandOutcome.OutcomeUnknown -> when (
                val found = commands.find(
                    operation
                )
            ) {
                is Outcome.Failure -> {
                    render { it.copy(busy = false, saveError = SaveError.Unknown) }
                    return false
                }

                is Outcome.Success -> if (found.value != null) return true else null
            }

            is CategoryCommandOutcome.Rejected -> outcome.reason
        }
        // Certain: this change was not written, so this attempt is over.
        saved[KEY_OPERATION] = null
        if (rejection == CategoryRejection.NameTaken) {
            render { it.copy(busy = false, nameError = NameError.Taken) }
            return false
        }
        val error = when (rejection) {
            CategoryRejection.CategoryMissing -> SaveError.CategoryGone

            is CategoryRejection.StaleGeneration -> SaveError.DataChanged

            // Each of these says the category is no longer what this form read.
            is CategoryRejection.VersionConflict,
            CategoryRejection.CategoryArchived,
            CategoryRejection.AlreadyArchived,
            CategoryRejection.NotArchived -> SaveError.Conflict

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
        val operation = (saved.get<String>(KEY_OPERATION)?.let(OperationId::of) as? Outcome.Success)
            ?.value
        val step = saved.get<String>(KEY_STEP)
        if (operation == null) {
            saved[KEY_OPERATION] = null
            return
        }
        mutable.update { it.copy(busy = true, saveError = null) }
        val committed = when (val found = commands.find(operation)) {
            is Outcome.Failure ->
                return render { it.copy(busy = false, saveError = SaveError.Unknown) }

            is Outcome.Success -> found.value != null
        }
        saved[KEY_OPERATION] = null
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
        mutable.update { it.copy(busy = true, saveError = null, archiveQuestion = null) }
        viewModelScope.launch {
            if (stored.archived) {
                val operation = operation(STEP_UNARCHIVE)
                val command = UnarchiveCategory(operation, generation, id, stored.version)
                if (committed(commands.submit(command), operation)) {
                    saved[KEY_OPERATION] = null
                    archived(unarchived = true)
                }
                return@launch
            }
            var operation = operation(STEP_ARCHIVE)
            var outcome = commands.submit(
                ArchiveCategory(operation, generation, id, stored.version)
            )
            if (outcome.waitsForPayments()) {
                // A fixed payment of the category is due and not recorded yet (rule 9):
                // Sello records what is due, then archives under a new operation, since
                // the refused one wrote nothing.
                saved[KEY_OPERATION] = null
                if (catchUp.run().stopped != null) {
                    render { it.copy(busy = false, saveError = SaveError.PaymentsPending) }
                    return@launch
                }
                operation = operation(STEP_ARCHIVE)
                outcome =
                    commands.submit(ArchiveCategory(operation, generation, id, stored.version))
                if (outcome.waitsForPayments()) {
                    saved[KEY_OPERATION] = null
                    render { it.copy(busy = false, saveError = SaveError.PaymentsPending) }
                    return@launch
                }
            }
            if (committed(outcome, operation)) {
                saved[KEY_OPERATION] = null
                archived(unarchived = false)
            }
        }
    }

    private fun CategoryCommandOutcome.waitsForPayments() =
        this is CategoryCommandOutcome.Rejected && reason == CategoryRejection.SchedulesPending

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
        private const val KEY_NAME = "category.edit.name"
        private const val KEY_ICON = "category.edit.icon"
        private const val KEY_UNLIMITED = "category.edit.unlimited"
        private const val KEY_LIMIT = "category.edit.limit"
        private const val KEY_OPERATION = "category.edit.operation"
        private const val KEY_STEP = "category.edit.step"
        private const val KEY_ORIGINAL_VERSION = "category.edit.original.version"
        private const val KEY_ORIGINAL_NAME = "category.edit.original.name"
        private const val KEY_ORIGINAL_ICON = "category.edit.original.icon"
        private const val KEY_ORIGINAL_ARCHIVED = "category.edit.original.archived"
        private const val KEY_ORIGINAL_PAUSED = "category.edit.original.paused"
        private const val KEY_ORIGINAL_LIMIT = "category.edit.original.limit"
        private const val UNLIMITED = "unlimited"
        private const val STEP_EDIT = "edit"
        private const val STEP_ARCHIVE = "archive"
        private const val STEP_UNARCHIVE = "unarchive"
    }
}
