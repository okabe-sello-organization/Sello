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
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.ChangeGoalTarget
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.EditGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.platform.ScreenReader
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

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
    private val corrections: Corrections,
    screenReader: ScreenReader
) : ViewModel() {
    private val goalId: String? = saved[KEY_GOAL]
    private var generation: Long? = null

    /** The goal as last read, and its version as each saved part raises it. */
    private var stored: GoalSummary? = null
    private var version: Long = 0

    /** What was typed: the name, the icon, the target's digits and the month, as text. */
    @Serializable
    private data class Draft(
        val name: String = "",
        val icon: String? = null,
        val digits: String? = null,
        val month: String? = null
    )

    private val drafts = SavedDraft(saved, KEY_DRAFT, Draft.serializer())
    private var form = drafts.read() ?: Draft()
    private val submission = Submission(saved, KEY_OP, ids)

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
        val editable = now.editable
        when (action) {
            is GoalEditorAction.NameChanged -> if (editable) edit(form.copy(name = action.text))

            is GoalEditorAction.IconPicked ->
                if (editable && action.key in GoalIcons.keys) edit(form.copy(icon = action.key))

            is GoalEditorAction.Digit -> if (editable) press { it.digit(action.digit) }

            GoalEditorAction.TripleZero -> if (editable) press { it.tripleZero() }

            GoalEditorAction.Backspace -> if (editable) {
                edit(form.copy(digits = (draft().backspace() as? AmountDraft.Digits)?.digits))
            }

            GoalEditorAction.ClearAmount -> if (editable) edit(form.copy(digits = null))

            GoalEditorAction.ChooseMonth -> if (editable) set(KEY_CHOOSING, true)

            is GoalEditorAction.ShowYear -> {
                val first = firstMonth().year
                set(KEY_YEAR, action.year.coerceIn(first, first + YEARS_AHEAD))
            }

            is GoalEditorAction.PickMonth -> if (editable && offered(action.month)) {
                form = form.copy(month = action.month.toString()).also(drafts::write)
                set(KEY_CHOOSING, false)
            }

            GoalEditorAction.CloseMonthPicker -> set(KEY_CHOOSING, false)

            // From the month sheet: no date, and the sheet closes as after a month.
            GoalEditorAction.NoDate -> if (editable) {
                form = form.copy(month = null).also(drafts::write)
                set(KEY_CHOOSING, false)
            }

            GoalEditorAction.Submit -> if (now.canSubmit && !submission.pending) {
                submit()
            }

            GoalEditorAction.Retry -> when {
                completer.state.value is CompletionState.Failed -> completer.retry()
                now.busy -> Unit
                submission.pending -> viewModelScope.launch { recover() }
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
        if (submission.pending) return
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

    private fun edit(next: Draft) {
        form = next
        drafts.write(next)
        render()
    }

    private fun set(key: String, value: Any?) {
        saved[key] = value
        render()
    }

    private fun draft(): AmountDraft = form.digits?.let(AmountDraft::Digits) ?: AmountDraft.Empty

    private fun press(key: (AmountDraft) -> AmountDraft.Change) {
        val change = key(draft())
        if (change.limitReached) {
            mutable.update { it.copy(amountPulse = it.amountPulse + 1) }
        } else {
            edit(form.copy(digits = (change.draft as? AmountDraft.Digits)?.digits))
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

    private fun month(): YearMonth? = form.month?.let(YearMonth::parse)

    private fun name(): String = form.name

    private fun storedTarget(summary: GoalSummary): TransactionAmount? =
        summary.history.events.filterIsInstance<GoalTargetChange>()
            .maxByOrNull(GoalTargetChange::sequence)?.target

    private fun detailsChanged(summary: GoalSummary): Boolean {
        val goal = summary.history.goal
        return name().trim() != goal.name.value || form.icon != goal.icon.value ||
            month() != goal.targetMonth
    }

    private fun targetChanged(summary: GoalSummary): Boolean = target() != storedTarget(summary)

    private fun dirty(state: GoalEditorState): Boolean = if (state.editing) {
        state.changed
    } else {
        name().isNotBlank() || form.icon != null || form.digits != null || month() != null
    }

    /** Reads the generation a command must carry and, when editing, the goal itself. */
    private fun load() {
        mutable.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            if (!read()) return@launch
            if (submission.pending) recover()
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
                form = Draft(
                    name = goal.name.value,
                    icon = goal.icon.value,
                    digits = storedTarget(summary)?.money?.minorUnits?.toString(),
                    month = goal.targetMonth?.toString()
                ).also(drafts::write)
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
                iconKey = form.icon,
                amount = form.digits?.let {
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
        val icon = form.icon?.let { (IconKey.of(it) as? Outcome.Success)?.value }
            ?: return
        val target = target() ?: return
        val generation = generation ?: return
        val month = month()
        val summary = stored
        mutable.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch {
            if (summary == null) {
                settle(
                    STEP_CREATE,
                    submission.send(commands::find, STEP_CREATE) { operation ->
                        commands.submit(
                            CreateGoal(operation, generation, name, icon, target, month)
                        )
                    }
                )
                return@launch
            }
            val id = summary.history.goal.id
            val parts = buildList {
                if (detailsChanged(summary)) add(STEP_DETAILS)
                if (targetChanged(summary)) add(STEP_TARGET)
            }
            for (step in parts) {
                val settled = submission.send(commands::find, step) { operation ->
                    commands.submit(
                        if (step == STEP_DETAILS) {
                            EditGoal(operation, generation, id, version, name, icon, month)
                        } else {
                            ChangeGoalTarget(operation, generation, id, version, target)
                        }
                    )
                }
                if (!settle(step, settled)) return@launch
            }
            finish(GoalEditorResult.Updated)
        }
    }

    private fun delete() {
        val summary = stored ?: return
        val generation = generation ?: return
        mutable.update { it.copy(busy = true, saveError = null) }
        viewModelScope.launch {
            val id = summary.history.goal.id
            settle(
                STEP_DELETE,
                submission.send(commands::find, STEP_DELETE) { operation ->
                    commands.submit(DeleteGoal(operation, generation, id, version))
                }
            )
        }
    }

    /** Whether [settled] is a receipt; if not, the form says why and nothing more is sent. */
    private suspend fun settle(
        step: String,
        settled: Settled<CommittedGoal, GoalRejection>?
    ): Boolean = when (settled) {
        // Another save is still in flight and settles the form itself.
        null -> false

        // A save found after an unknown outcome is never celebrated.
        is Settled.Committed -> {
            committed(step, settled.value.goalId, settled.value.change.takeIf { !settled.found })
            true
        }

        is Settled.Unknown -> {
            mutable.update { it.copy(busy = false, saveError = GoalSaveError.Unknown) }
            false
        }

        Settled.NotCommitted -> notSaved(GoalSaveError.NotSaved)

        is Settled.Rejected -> when (settled.reason) {
            is CommandRejection.StaleGeneration,
            is CommandRejection.VersionConflict,
            Specific(GoalRejection.GoalMissing),
            Specific(GoalRejection.Completed) -> {
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
        saved[KEY_CONFIRM] = false
        mutable.update { it.copy(busy = false, saveError = error) }
        render()
        return false
    }

    /** Asks how the saved operation ended, with its own identifier, then reads again. */
    private suspend fun recover() {
        if (!submission.pending) return
        val step = submission.step ?: STEP_CREATE
        mutable.update { it.copy(busy = true, saveError = null) }
        when (val settled = submission.recover(commands::find)) {
            null -> mutable.update { it.copy(busy = false) }

            is Settled.Unknown ->
                mutable.update { it.copy(busy = false, saveError = GoalSaveError.Unknown) }

            Settled.NotCommitted -> notSaved(GoalSaveError.NotSaved)

            is Settled.Committed -> if (step == STEP_CREATE || step == STEP_DELETE) {
                committed(step, settled.value.goalId)
            } else {
                if (read() && !mutable.value.changed) finish(GoalEditorResult.Updated)
                mutable.update { it.copy(busy = false) }
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

        private const val KEY_DRAFT = "goal.editor.draft"
        private const val KEY_CHOOSING = "goal.editor.choosingMonth"
        private const val KEY_YEAR = "goal.editor.pickerYear"
        private const val KEY_DRAFTED = "goal.editor.drafted"
        private const val KEY_CONFIRM = "goal.editor.confirmDelete"
        private const val KEY_OP = "goal.editor.operation"
        private const val KEY_RESULT = "goal.editor.result"
        private const val STEP_CREATE = "create"
        private const val STEP_DETAILS = "details"
        private const val STEP_TARGET = "target"
        private const val STEP_DELETE = "delete"
    }
}
