package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.policy.CopAmountInput
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.MonthBudget
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Creates a category through the real command. The draft and the identifier of a save
 * in progress are kept in [saved], so rotation or the process being killed loses
 * neither. The form reports a category as created only when a receipt says so.
 */
class CategoryEditorViewModel(
    private val saved: SavedStateHandle,
    private val commands: CategoryCommands,
    private val reads: CategoryReads,
    private val session: MonthSession,
    private val ids: RecordIdSource,
    money: MoneyFormatter
) : ViewModel() {
    private var generation: Long? = null
    private val figures = LimitFigures(money)

    /** The month as last read: what the suggestions and the total are worked out from. */
    private var budget: MonthBudget? = null

    /** What the existing categories are called, archived ones too, as names are compared. */
    private var takenNames: Set<String> = emptySet()

    private val mutable = MutableStateFlow(
        CategoryEditorState(
            forEntry = saved[KEY_FOR_ENTRY] ?: false,
            name = saved[KEY_NAME] ?: "",
            iconKeys = CategoryIcons.keys,
            iconKey = saved[KEY_ICON] ?: "",
            // No limit is chosen, never assumed: a new draft starts asking for an amount.
            unlimited = saved[KEY_UNLIMITED] ?: false,
            limitText = saved[KEY_LIMIT] ?: "",
            limitAmount = figures.amount(saved[KEY_LIMIT] ?: ""),
            suggestions = LimitFigures(money).suggestions(null),
            result = saved.get<String>(KEY_CREATED)?.let(EditorResult::Created)
        )
    )
    val state: StateFlow<CategoryEditorState> = mutable.asStateFlow()

    init {
        load()
    }

    fun onAction(action: CategoryEditorAction) {
        val now = mutable.value
        when (action) {
            is CategoryEditorAction.NameChanged -> if (now.editable) {
                saved[KEY_NAME] = action.text
                mutable.update { it.copy(name = action.text, nameError = takenError(action.text)) }
            }

            is CategoryEditorAction.IconPicked ->
                if (now.editable && action.key in CategoryIcons.keys) {
                    saved[KEY_ICON] = action.key
                    saved[KEY_ICON_CHOSEN] = true
                    mutable.update { it.copy(iconKey = action.key) }
                }

            is CategoryEditorAction.UnlimitedChanged -> if (now.editable) {
                // The amount typed is kept: it is not sent while there is no limit, and
                // it is there again if the switch was touched by mistake.
                setLimit(action.unlimited, now.limitText)
            }

            is CategoryEditorAction.LimitChanged -> if (now.editable && !now.unlimited) {
                setLimit(unlimited = false, text = action.text)
            }

            is CategoryEditorAction.LimitDigit -> if (now.editable && !now.unlimited) {
                LimitKeys.press(now.limitText) { it.digit(action.digit) }
                    ?.let { setLimit(unlimited = false, text = it) }
            }

            CategoryEditorAction.LimitTripleZero -> if (now.editable && !now.unlimited) {
                LimitKeys.press(now.limitText) { it.tripleZero() }
                    ?.let { setLimit(unlimited = false, text = it) }
            }

            CategoryEditorAction.LimitBackspace -> if (now.editable && !now.unlimited) {
                setLimit(unlimited = false, text = LimitKeys.backspace(now.limitText))
            }

            CategoryEditorAction.LimitCleared -> if (now.editable && !now.unlimited) {
                setLimit(unlimited = false, text = "")
            }

            is CategoryEditorAction.SuggestionPicked -> if (now.editable) {
                setLimit(unlimited = false, text = action.pesos.toString())
            }

            CategoryEditorAction.Submit -> if (now.canSubmit && now.editable) submit()

            CategoryEditorAction.Retry -> when {
                now.busy -> Unit
                saved.get<String>(KEY_OPERATION) != null -> recover()
                else -> load()
            }

            CategoryEditorAction.Back -> when {
                now.busy -> Unit
                isDirty(now) -> mutable.update { it.copy(confirmDiscard = true) }
                else -> mutable.update { it.copy(result = EditorResult.Discarded) }
            }

            CategoryEditorAction.DiscardConfirmed ->
                mutable.update { it.copy(confirmDiscard = false, result = EditorResult.Discarded) }

            CategoryEditorAction.KeepEditing -> mutable.update { it.copy(confirmDiscard = false) }

            // A category that does not exist yet has nothing to archive or to reload.
            CategoryEditorAction.Archive,
            CategoryEditorAction.ArchiveConfirmed,
            CategoryEditorAction.ArchiveDismissed,
            CategoryEditorAction.ReloadDiscardingDraft,
            CategoryEditorAction.ReloadKeepingDraft -> Unit
        }
    }

    /**
     * Said while typing, not after trying to save: a name another category already has,
     * however it is written. The command still refuses it, for a name taken meanwhile.
     */
    private fun takenError(text: String): NameError? {
        val name = (CategoryName.of(text) as? Outcome.Success)?.value ?: return null
        return NameError.Taken.takeIf { name.uniquenessKey in takenNames }
    }

    private fun setLimit(unlimited: Boolean, text: String) {
        saved[KEY_UNLIMITED] = unlimited
        saved[KEY_LIMIT] = text
        mutable.update {
            it.copy(
                unlimited = unlimited,
                limitText = text,
                limitAmount = figures.amount(text),
                limitError = null,
                limitTotal = figures.total(budget, null, unlimited, text)
            )
        }
    }

    /** Whether leaving would lose something the person entered or chose. */
    private fun isDirty(state: CategoryEditorState) = state.name.isNotBlank() ||
        state.limitText.isNotBlank() ||
        state.unlimited ||
        saved.get<Boolean>(KEY_ICON_CHOSEN) == true

    /**
     * Reads the generation a command must carry and which icons are taken, then, if a
     * save was in progress when the app last stopped, finds out how it ended.
     */
    private fun load() {
        mutable.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            when (val read = reads.monthBudget(session.currentMonthNow)) {
                is Outcome.Failure -> mutable.update { it.copy(loading = false, loadFailed = true) }

                is Outcome.Success -> {
                    generation = read.value.generation
                    budget = read.value
                    takenNames =
                        read.value.categories.map { it.category.name.uniquenessKey }.toSet()
                    val used = read.value.categories.map { it.category.icon.value }.toSet()
                    mutable.update { it.copy(usedIconKeys = used) }
                    if (mutable.value.iconKey.isEmpty()) {
                        val first = CategoryIcons.keys.firstOrNull { it !in used }
                            ?: CategoryIcons.keys.first()
                        saved[KEY_ICON] = first
                        mutable.update { it.copy(iconKey = first) }
                    }
                    // A restored draft may hold a name that is taken.
                    mutable.update {
                        it.copy(
                            loading = false,
                            nameError = it.nameError ?: takenError(it.name),
                            suggestions = figures.suggestions(read.value),
                            limitTotal = figures.total(read.value, null, it.unlimited, it.limitText)
                        )
                    }
                    if (saved.get<String>(KEY_OPERATION) != null) recover()
                }
            }
        }
    }

    private fun submit() {
        val state = mutable.value
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
        val limit = when {
            state.unlimited -> BudgetLimit.Unlimited

            state.limitText.isBlank() ->
                return mutable.update { it.copy(limitError = LimitError.Required) }

            else -> {
                val money = CopAmountInput.parse(state.limitText) as? Outcome.Success
                val finite = money?.let { BudgetLimit.finite(it.value) } as? Outcome.Success
                finite?.value
                    ?: return mutable.update { it.copy(limitError = LimitError.NotAnAmount) }
            }
        }
        val icon = (IconKey.of(state.iconKey) as? Outcome.Success)?.value ?: return
        val generation = generation ?: return
        // The identifier is saved before the command leaves, so whatever happens next
        // this attempt can be recognised and never made twice.
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[KEY_OPERATION] = operation.value
        mutable.update { it.copy(busy = true, saveError = null, nameError = null) }
        viewModelScope.launch {
            settle(commands.submit(CreateCategory(operation, generation, name, icon, limit)))
        }
    }

    private suspend fun settle(outcome: CategoryCommandOutcome) {
        when (outcome) {
            is CategoryCommandOutcome.Committed -> created(outcome.change.categoryId.value)

            is CategoryCommandOutcome.OutcomeUnknown -> recoverNow()

            is CategoryCommandOutcome.Rejected -> {
                // Certain: nothing was written, so this attempt is over.
                saved[KEY_OPERATION] = null
                when (outcome.reason) {
                    CategoryRejection.NameTaken ->
                        mutable.update { it.copy(busy = false, nameError = NameError.Taken) }

                    is CategoryRejection.StaleGeneration -> {
                        generation = null
                        mutable.update { it.copy(busy = false, saveError = SaveError.DataChanged) }
                        load()
                    }

                    else -> mutable.update { it.copy(busy = false, saveError = SaveError.NotSaved) }
                }
            }
        }
    }

    private fun recover() {
        mutable.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch { recoverNow() }
    }

    /** Asks storage how the saved operation ended. Only a receipt counts as created. */
    private suspend fun recoverNow() {
        val operation = (saved.get<String>(KEY_OPERATION)?.let(OperationId::of) as? Outcome.Success)
            ?.value
        if (operation == null) {
            saved[KEY_OPERATION] = null
            return mutable.update { it.copy(busy = false) }
        }
        when (val found = commands.find(operation)) {
            is Outcome.Failure ->
                mutable.update { it.copy(busy = false, saveError = SaveError.Unknown) }

            is Outcome.Success -> {
                val change = found.value
                if (change != null) {
                    created(change.categoryId.value)
                } else {
                    saved[KEY_OPERATION] = null
                    mutable.update { it.copy(busy = false, saveError = null) }
                }
            }
        }
    }

    private fun created(categoryId: String) {
        saved[KEY_CREATED] = categoryId
        saved[KEY_OPERATION] = null
        mutable.update {
            it.copy(busy = false, saveError = null, result = EditorResult.Created(categoryId))
        }
    }

    companion object {
        /** The route's argument: opened because an expense needs a category. */
        const val KEY_FOR_ENTRY = "forEntry"

        private const val KEY_NAME = "category.name"
        private const val KEY_ICON = "category.icon"
        private const val KEY_ICON_CHOSEN = "category.iconChosen"
        private const val KEY_UNLIMITED = "category.unlimited"
        private const val KEY_LIMIT = "category.limit"
        private const val KEY_OPERATION = "category.operation"
        private const val KEY_CREATED = "category.created"
    }
}
