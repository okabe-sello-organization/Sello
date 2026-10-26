package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalProgress
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.EditGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.platform.ScreenReader
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.money.MoneyFormatter
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Nueva meta and Editar meta through the real goal commands. The draft, a save in
 * progress and the open month picker live in [saved], so rotation or the process
 * being killed loses none of them. A save whose outcome is not known is asked about with
 * its own identifier before anything else is sent. Editing sends the name, icon and
 * month as one command and the target as another, each at the version it was read.
 */
class GoalEditorViewModel(
    private val saved: SavedStateHandle,
    private val reads: GoalReads,
    private val commands: GoalCommands,
    private val clock: FinancialClock,
    private val money: MoneyFormatter,
    private val ids: RecordIdSource,
    private val corrections: ExpenseCorrections,
    screenReader: ScreenReader
) : ViewModel() {
    private val goalId: String? = saved[KEY_GOAL]
    private var generation: Long? = null

    /** The goal as last read, and its version as each saved part raises it. */
    private var stored: GoalSummary? = null
    private var version: Long = 0

    private val mutable = MutableStateFlow(GoalEditorState(editing = goalId != null))
    private val completer =
        GoalCompleter(saved, commands, corrections, ids, clock, screenReader, viewModelScope)

    val state: StateFlow<GoalEditorState> =
        combine(mutable, completer.state) { form, completion -> form.copy(completion = completion) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, mutable.value)

    init {
        load()
    }

    fun onAction(action: GoalEditorAction) {
        val now = state.value
        val editable = !now.loading && !now.loadFailed && !now.busy && !now.completed &&
            now.result == null
        when (action) {
            is GoalEditorAction.NameChanged -> if (editable) set(KEY_NAME, action.text)

            is GoalEditorAction.IconPicked ->
                if (editable && action.key in GoalIcons.keys) set(KEY_ICON, action.key)

            is GoalEditorAction.Digit -> if (editable) press { it.digit(action.digit) }

            GoalEditorAction.TripleZero -> if (editable) press { it.tripleZero() }

            GoalEditorAction.Backspace -> if (editable) {
                set(KEY_DIGITS, (draft().backspace() as? AmountDraft.Digits)?.digits)
            }

            GoalEditorAction.ClearAmount -> if (editable) set(KEY_DIGITS, null)

            GoalEditorAction.ChooseMonth -> if (editable) set(KEY_CHOOSING, true)

            is GoalEditorAction.ShowYear -> {
                val first = firstMonth().year
                set(KEY_YEAR, action.year.coerceIn(first, first + YEARS_AHEAD))
            }

            is GoalEditorAction.PickMonth -> if (editable && offered(action.month)) {
                saved[KEY_MONTH] = action.month.toString()
                set(KEY_CHOOSING, false)
            }

            GoalEditorAction.CloseMonthPicker -> set(KEY_CHOOSING, false)

            // From the month sheet: no date, and the sheet closes as after a month.
            GoalEditorAction.NoDate -> if (editable) {
                saved[KEY_MONTH] = null
                set(KEY_CHOOSING, false)
            }

            GoalEditorAction.Submit -> if (now.canSubmit && saved.get<String>(KEY_OP) == null) {
                submit()
            }

            GoalEditorAction.Retry -> when {
                completer.state.value is CompletionState.Failed -> completer.retry()
                now.busy -> Unit
                saved.get<String>(KEY_OP) != null -> viewModelScope.launch { recover() }
                else -> load()
            }

            GoalEditorAction.Back -> when {
                now.busy -> Unit
                now.confirmDelete -> set(KEY_CONFIRM, false)
                dirty(now) -> mutable.update { it.copy(confirmDiscard = true) }
                else -> finish(GoalEditorResult.Discarded)
            }

            GoalEditorAction.DiscardConfirmed -> finish(GoalEditorResult.Discarded)

            GoalEditorAction.KeepEditing -> mutable.update { it.copy(confirmDiscard = false) }

            GoalEditorAction.AskToDelete ->
                if (stored != null && !now.busy && now.result == null) set(KEY_CONFIRM, true)

            GoalEditorAction.CancelDelete -> if (!now.busy) set(KEY_CONFIRM, false)

            GoalEditorAction.Delete -> if (now.confirmDelete && !now.busy) delete()

            GoalEditorAction.MarkComplete -> markComplete(now)

            GoalEditorAction.CelebrationDone -> {
                completer.done()
                finish(GoalEditorResult.Updated)
            }
        }
    }

    /**
     * ¿Ya la lograste?: a goal reached outside Sello is marked lograda from here, at any
     * percentage (rule 8, MT26). It is the goal as stored, not the draft.
     */
    private fun markComplete(now: GoalEditorState) {
        val summary = stored ?: return
        val generation = generation ?: return
        if (now.busy || now.completed || now.result != null) return
        if (saved.get<String>(KEY_OP) != null) return
        val goal = summary.history.goal
        completer.complete(
            CompletionTarget(
                goalId = goal.id.value,
                version = version,
                generation = generation,
                name = goal.name.value,
                iconKey = goal.icon.value,
                saved = money.format(summary.progress.saved),
                contributions = summary.progress.contributions
            )
        )
    }

    private fun set(key: String, value: Any?) {
        saved[key] = value
        render()
    }

    private fun draft(): AmountDraft =
        saved.get<String>(KEY_DIGITS)?.let(AmountDraft::Digits) ?: AmountDraft.Empty

    private fun press(key: (AmountDraft) -> AmountDraft.Change) {
        val change = key(draft())
        if (change.limitReached) {
            mutable.update { it.copy(amountPulse = it.amountPulse + 1) }
        } else {
            set(KEY_DIGITS, (change.draft as? AmountDraft.Digits)?.digits)
        }
    }

    private fun firstMonth(): YearMonth = YearMonth.from(clock.today.value.date).plusMonths(1)

    /** From next month up to [YEARS_AHEAD] years ahead: this month or earlier is never offered. */
    private fun offered(month: YearMonth): Boolean {
        val first = firstMonth()
        return month >= first && month.year <= first.year + YEARS_AHEAD
    }

    private fun target(): TransactionAmount? =
        (draft().toTransactionAmount() as? Outcome.Success)?.value

    private fun month(): YearMonth? = saved.get<String>(KEY_MONTH)?.let(YearMonth::parse)

    private fun name(): String = saved[KEY_NAME] ?: ""

    private fun storedTarget(summary: GoalSummary): TransactionAmount? =
        summary.history.events.filterIsInstance<GoalTargetChange>()
            .maxByOrNull(GoalTargetChange::sequence)?.target

    private fun detailsChanged(summary: GoalSummary): Boolean {
        val goal = summary.history.goal
        return name().trim() != goal.name.value || saved.get<String>(KEY_ICON) != goal.icon.value ||
            month() != goal.targetMonth
    }

    private fun targetChanged(summary: GoalSummary): Boolean = target() != storedTarget(summary)

    private fun dirty(state: GoalEditorState): Boolean = if (state.editing) {
        state.changed
    } else {
        name().isNotBlank() || saved.get<String>(KEY_ICON) != null ||
            saved.get<String>(KEY_DIGITS) != null || month() != null
    }

    /** Reads the generation a command must carry and, when editing, the goal itself. */
    private fun load() {
        mutable.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            if (!read()) return@launch
            if (saved.get<String>(KEY_OP) != null) recover()
        }
    }

    /** Whether the goal could be read. A draft already made is kept as it is. */
    private suspend fun read(): Boolean {
        val board = when (val read = reads.board()) {
            is Outcome.Success -> read.value

            is Outcome.Failure -> {
                mutable.update { it.copy(loading = false, loadFailed = true, busy = false) }
                return false
            }
        }
        generation = board.generation
        if (goalId != null) {
            val summary = board.goals.firstOrNull { it.history.goal.id.value == goalId }
            if (summary == null) {
                finish(GoalEditorResult.Discarded)
                return false
            }
            stored = summary
            version = summary.history.goal.version
            if (saved.get<Boolean>(KEY_DRAFTED) != true) {
                val goal = summary.history.goal
                saved[KEY_NAME] = goal.name.value
                saved[KEY_ICON] = goal.icon.value
                saved[KEY_DIGITS] = storedTarget(summary)?.money?.minorUnits?.toString()
                saved[KEY_MONTH] = goal.targetMonth?.toString()
                saved[KEY_DRAFTED] = true
            }
        }
        mutable.update { it.copy(loading = false, loadFailed = false) }
        render()
        return true
    }

    /** The state from the draft, the goal as read and the goal rules. */
    private fun render() {
        val today = clock.today.value.date
        val summary = stored
        val text = name()
        val trimmed = text.trim()
        val target = target()
        val month = month()
        val preview: GoalProgress? = summary?.let {
            if (target == null) {
                it.progress
            } else {
                (GoalPolicy.withTarget(it.history, target, today) as? Outcome.Success)?.value
            }
        }
        val pace = if (month != null && target != null) {
            GoalPolicy.pace(target, preview, today, month)
        } else {
            null
        }
        val places = preview?.placeModels() ?: GoalPlace.entries.map {
            PlaceModel(it, PlaceLook.Empty, null)
        }
        val first = firstMonth()
        mutable.update { state ->
            state.copy(
                completed = summary?.history?.goal?.completedOn != null,
                name = text,
                nameLength = trimmed.codePointCount(0, trimmed.length),
                nameError = when ((GoalName.of(text) as? Outcome.Failure)?.error) {
                    is TextError.TooLong -> GoalNameError.TooLong
                    TextError.ControlCharacter -> GoalNameError.NotAllowed
                    TextError.Required, null -> null
                },
                iconKey = saved[KEY_ICON],
                amount = saved.get<String>(KEY_DIGITS)?.let {
                    money.format(Money.cop(it.toLong()))
                },
                targetMonth = month,
                choosingMonth = saved[KEY_CHOOSING] ?: false,
                pickerYear = saved[KEY_YEAR] ?: (month ?: first).year,
                firstMonth = first,
                pace = pace?.let { PlanPace(money.format(it.monthly), it.months) },
                nothingMissing = month != null && preview != null && target != null &&
                    GoalPolicy.missing(preview).minorUnits == 0L,
                places = places.map { place ->
                    PlannedPlace(
                        place,
                        when (place.look) {
                            PlaceLook.Earned, PlaceLook.Kept -> PlanCaption.Earned
                            else -> pace?.projected?.get(place.place)?.let(PlanCaption::Month)
                        }
                    )
                },
                movementCount = summary?.history?.events?.count { it is GoalMovement } ?: 0,
                confirmDelete = saved[KEY_CONFIRM] ?: false,
                changed = summary != null && (detailsChanged(summary) || targetChanged(summary)),
                result = state.result ?: saved.get<String>(KEY_RESULT)?.let(::result)
            )
        }
    }

    private fun submit() {
        val name = (GoalName.of(name()) as? Outcome.Success)?.value ?: return
        val icon = saved.get<String>(KEY_ICON)?.let { (IconKey.of(it) as? Outcome.Success)?.value }
            ?: return
        val target = target() ?: return
        val generation = generation ?: return
        val month = month()
        val summary = stored
        mutable.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch {
            if (summary == null) {
                val operation = operation(STEP_CREATE)
                settle(
                    STEP_CREATE,
                    operation,
                    commands.submit(CreateGoal(operation, generation, name, icon, target, month))
                )
                return@launch
            }
            val id = summary.history.goal.id
            val parts = buildList {
                if (detailsChanged(summary)) add(STEP_DETAILS)
                if (targetChanged(summary)) add(STEP_TARGET)
            }
            for (step in parts) {
                val operation = operation(step)
                val command: GoalCommand = if (step == STEP_DETAILS) {
                    EditGoal(operation, generation, id, version, name, icon, month)
                } else {
                    ChangeGoalTarget(operation, generation, id, version, target)
                }
                if (!settle(step, operation, commands.submit(command))) return@launch
            }
            finish(GoalEditorResult.Updated)
        }
    }

    private fun delete() {
        val summary = stored ?: return
        val generation = generation ?: return
        mutable.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch {
            val operation = operation(STEP_DELETE)
            val id = summary.history.goal.id
            settle(
                STEP_DELETE,
                operation,
                commands.submit(DeleteGoal(operation, generation, id, version))
            )
        }
    }

    /** Saved before the command leaves, so this attempt is recognised whatever happens. */
    private fun operation(step: String): OperationId {
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[KEY_OP] = operation.value
        saved[KEY_STEP] = step
        return operation
    }

    /** Whether [outcome] is a receipt; if not, the form says why and nothing more is sent. */
    private suspend fun settle(
        step: String,
        operation: OperationId,
        outcome: GoalCommandOutcome
    ): Boolean = when (outcome) {
        is GoalCommandOutcome.Committed -> {
            committed(step, outcome.goal.goalId, outcome.goal.change)
            true
        }

        is GoalCommandOutcome.OutcomeUnknown -> when (val found = commands.find(operation)) {
            is Outcome.Failure -> {
                mutable.update { it.copy(busy = false, saveError = GoalSaveError.Unknown) }
                false
            }

            is Outcome.Success -> found.value?.let {
                committed(step, it.goalId)
                true
            } ?: notSaved(GoalSaveError.NotSaved)
        }

        is GoalCommandOutcome.Rejected -> when (outcome.reason) {
            is GoalRejection.StaleGeneration,
            is GoalRejection.VersionConflict,
            GoalRejection.GoalMissing,
            GoalRejection.Completed -> {
                notSaved(GoalSaveError.Changed)
                read()
                false
            }

            else -> notSaved(GoalSaveError.NotSaved)
        }
    }

    /**
     * A receipt for [step]. [change] is what this save did to the stamps, absent for a
     * replay or a save found after an unknown outcome, which are never celebrated.
     */
    private fun committed(step: String, id: GoalId, change: GoalChange? = null) {
        saved[KEY_OP] = null
        saved[KEY_STEP] = null
        when (step) {
            STEP_CREATE -> finish(GoalEditorResult.Created(id.value))

            STEP_DELETE -> finish(GoalEditorResult.Deleted)

            // Each saved part raises the goal's version by one.
            else -> {
                version += 1
                if (step == STEP_TARGET && change != null) celebrate(change)
            }
        }
    }

    /** The modest celebration of a stamp a new target earned or relit (rule 7, MT18, MT19). */
    private fun celebrate(change: GoalChange) {
        if (change.earned.isEmpty() && change.relit.isEmpty() && !change.reachedMeta) return
        val summary = stored ?: return
        val target = target() ?: return
        val name = (GoalName.of(name()) as? Outcome.Success)?.value ?: return
        val after = GoalPolicy.withTarget(summary.history, target, clock.today.value.date)
        corrections.goalTargetChanged(
            GoalCelebration(
                goalId = summary.history.goal.id.value,
                goalName = name.value,
                earned = change.earned,
                relit = change.relit,
                reachedMeta = change.reachedMeta,
                missing = (after as? Outcome.Success)?.value?.let {
                    money.format(GoalPolicy.missing(it))
                }
            )
        )
    }

    /** Certain: nothing was written, so this attempt is over. */
    private fun notSaved(error: GoalSaveError): Boolean {
        saved[KEY_OP] = null
        saved[KEY_STEP] = null
        saved[KEY_CONFIRM] = false
        mutable.update { it.copy(busy = false, saveError = error) }
        render()
        return false
    }

    /** Asks how the saved operation ended, with its own identifier, then reads again. */
    private suspend fun recover() {
        val operation = (saved.get<String>(KEY_OP)?.let(OperationId::of) as? Outcome.Success)?.value
        val step = saved.get<String>(KEY_STEP) ?: STEP_CREATE
        if (operation == null) {
            saved[KEY_OP] = null
            return
        }
        mutable.update { it.copy(busy = true, saveError = null) }
        when (val found = commands.find(operation)) {
            is Outcome.Failure ->
                mutable.update { it.copy(busy = false, saveError = GoalSaveError.Unknown) }

            is Outcome.Success -> {
                val committed = found.value
                if (committed == null) {
                    notSaved(GoalSaveError.NotSaved)
                } else if (step == STEP_CREATE || step == STEP_DELETE) {
                    committed(step, committed.goalId)
                } else {
                    saved[KEY_OP] = null
                    saved[KEY_STEP] = null
                    if (read() && !mutable.value.changed) finish(GoalEditorResult.Updated)
                    mutable.update { it.copy(busy = false) }
                }
            }
        }
    }

    private fun finish(result: GoalEditorResult) {
        saved[KEY_RESULT] = when (result) {
            is GoalEditorResult.Created -> "created:${result.goalId}"
            GoalEditorResult.Updated -> "updated"
            GoalEditorResult.Deleted -> "deleted"
            GoalEditorResult.Discarded -> "discarded"
        }
        mutable.update { it.copy(busy = false, confirmDiscard = false, result = result) }
    }

    private fun result(stored: String): GoalEditorResult = when {
        stored.startsWith("created:") -> GoalEditorResult.Created(stored.removePrefix("created:"))
        stored == "updated" -> GoalEditorResult.Updated
        stored == "deleted" -> GoalEditorResult.Deleted
        else -> GoalEditorResult.Discarded
    }

    companion object {
        /** The route argument: the goal to edit, absent for a new one. */
        const val KEY_GOAL = "goalId"

        /** How far ahead a target month can be chosen. */
        const val YEARS_AHEAD = 50

        private const val KEY_NAME = "goal.editor.name"
        private const val KEY_ICON = "goal.editor.icon"
        private const val KEY_DIGITS = "goal.editor.digits"
        private const val KEY_MONTH = "goal.editor.month"
        private const val KEY_CHOOSING = "goal.editor.choosingMonth"
        private const val KEY_YEAR = "goal.editor.pickerYear"
        private const val KEY_DRAFTED = "goal.editor.drafted"
        private const val KEY_CONFIRM = "goal.editor.confirmDelete"
        private const val KEY_OP = "goal.editor.operation"
        private const val KEY_STEP = "goal.editor.step"
        private const val KEY_RESULT = "goal.editor.result"
        private const val STEP_CREATE = "create"
        private const val STEP_DETAILS = "details"
        private const val STEP_TARGET = "target"
        private const val STEP_DELETE = "delete"
    }
}
