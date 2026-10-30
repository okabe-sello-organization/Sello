package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.LimitAdjustmentPolicy
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The limit sheet of "Ajustar presupuesto": one category's limit for the month the screen
 * was opened on, for that month only ([SetMonthLimit]) or from it on ([SetDefaultLimit]).
 *
 * The category, the draft, the scope and the operation in flight are kept in [saved], so
 * a sheet restored after the process was killed is the same sheet, and a save whose
 * outcome was not known is asked about with [CategoryCommands.find], never sent again.
 * The shortcuts and the line under them come from [LimitAdjustmentPolicy] over the
 * month's snapshot; nothing is added up here.
 */
class LimitSheetViewModel(
    private val saved: SavedStateHandle,
    private val commands: CategoryCommands,
    private val reads: CategoryReads,
    private val snapshots: MonthlySnapshots,
    private val session: MonthSession,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val notices: Corrections
) : ViewModel() {
    private val month: YearMonth =
        saved.get<String>(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: session.currentMonthNow

    private val submission = Submission(saved, KEY_OPERATION, ids)
    private val mutable = MutableStateFlow(LimitSheetState(month))
    val state: StateFlow<LimitSheetState> = mutable.asStateFlow()

    private var generation: Long? = null
    private var version: Long? = null

    /** The month's finite limit as read, or null without one. */
    private var currentLimit: Money? = null
    private var snapshot: MonthlySnapshot? = null
    private var watching: Job? = null

    init {
        saved[KEY_MONTH] = month.toString()
        saved.get<String>(KEY_CATEGORY)?.let { id ->
            viewModelScope.launch {
                open(id)
                if (submission.pending) checkSaved()
            }
        }
    }

    fun onAction(action: LimitSheetAction) {
        val now = mutable.value
        val editable = now.categoryId != null && !now.busy &&
            now.error != LimitSheetError.Unknown
        when (action) {
            is LimitSheetAction.Open -> if (!now.busy) {
                saved[KEY_CATEGORY] = action.categoryId
                saved[KEY_DIGITS] = null
                saved[KEY_CHOSEN] = null
                saved[KEY_SCOPE] = null
                submission.clear()
                viewModelScope.launch { open(action.categoryId) }
            }

            LimitSheetAction.Close -> if (!now.busy) close()

            is LimitSheetAction.Scope -> if (editable) {
                saved[KEY_SCOPE] = action.scope.name
                render()
            }

            is LimitSheetAction.Digit -> if (editable) type(draft().digit(action.digit).draft)

            LimitSheetAction.TripleZero -> if (editable) type(draft().tripleZero().draft)

            LimitSheetAction.Backspace -> if (editable) type(draft().backspace())

            LimitSheetAction.ClearAll -> if (editable) type(AmountDraft.Empty)

            is LimitSheetAction.Chip -> if (editable) {
                now.chips.firstOrNull { it.kind == action.kind }?.let { chip ->
                    type(AmountDraft.Digits(chip.pesos.toString()), chosen = chip.kind)
                }
            }

            LimitSheetAction.Save -> if (now.canSave) save()

            LimitSheetAction.CheckSaved ->
                if (!now.busy && now.error == LimitSheetError.Unknown) {
                    viewModelScope.launch { checkSaved() }
                }

            LimitSheetAction.Retry -> {
                val id = now.categoryId
                val reread = now.loadFailed || now.error == LimitSheetError.Conflict ||
                    now.error == LimitSheetError.DataChanged
                if (id != null && !now.busy && reread) viewModelScope.launch { open(id) }
            }
        }
    }

    private fun scope(): LimitScope = saved.get<String>(KEY_SCOPE)?.let { name ->
        LimitScope.entries.firstOrNull { it.name == name }
    }
        ?: LimitScope.ThisMonth

    private fun digits(): String? = saved.get<String>(KEY_DIGITS)?.takeIf { it.isNotEmpty() }

    private fun draft(): AmountDraft = digits()?.let(AmountDraft::Digits) ?: AmountDraft.Empty

    private fun type(draft: AmountDraft, chosen: ChipKind? = null) {
        saved[KEY_DIGITS] = (draft as? AmountDraft.Digits)?.digits
        saved[KEY_CHOSEN] = chosen?.name
        // A new amount is a new attempt: a refusal of the last one no longer applies.
        mutable.update { if (it.error == LimitSheetError.NotSaved) it.copy(error = null) else it }
        render()
    }

    /** Reads the category's version and limit, then follows the month for its figures. */
    private suspend fun open(id: String) {
        mutable.update {
            LimitSheetState(month, categoryId = id, loading = true)
        }
        val read = when (val read = reads.monthBudget(month)) {
            is Outcome.Failure -> {
                mutable.update { it.copy(loading = false, loadFailed = true) }
                return render()
            }

            is Outcome.Success -> read.value
        }
        generation = read.generation
        val found = read.categories.firstOrNull { it.category.id.value == id }
        version = found?.category?.version
        currentLimit =
            ((found?.state as? MonthBudgetState.Limited)?.limit as? BudgetLimit.Finite)?.amount
        mutable.update {
            it.copy(
                name = found?.category?.name?.value.orEmpty(),
                iconKey = found?.category?.icon?.value.orEmpty(),
                current = currentLimit?.let(money::format),
                loading = false,
                error = when {
                    found == null -> LimitSheetError.CategoryGone
                    found.category.archived -> LimitSheetError.Archived
                    else -> null
                }
            )
        }
        watching?.cancel()
        watching = viewModelScope.launch {
            snapshots.observe(month).collect { observed ->
                snapshot = (observed as? ReadState.Ready)?.value
                render()
            }
        }
        render()
    }

    /** Everything that follows from the draft, the scope and the latest snapshot. */
    private fun render() {
        val id = mutable.value.categoryId ?: return
        val categoryId = (CategoryId.of(id) as? Outcome.Success)?.value
        val pesos = digits()?.toLongOrNull()
        val limit = pesos?.let { (BudgetLimit.finite(Money.cop(it)) as? Outcome.Success)?.value }
        val month = snapshot
        val line = month?.categories?.firstOrNull { it.category.id.value == id }
        val chips = if (month != null &&
            categoryId != null
        ) {
            chips(month, categoryId)
        } else {
            emptyList()
        }
        val fact = if (month != null && categoryId != null && limit != null) {
            (
                LimitAdjustmentPolicy.preview(
                    month,
                    categoryId,
                    limit
                ) as? Outcome.Success
                )?.value?.let {
                LimitFact(
                    total = money.format(it.total),
                    left = money.magnitude(it.left),
                    outcome = when {
                        it.left.minorUnits > 0 -> FactOutcome.Left
                        it.left.minorUnits == 0L -> FactOutcome.Exactly
                        else -> FactOutcome.Short
                    },
                    afterPayments = it.afterPayments,
                    capacity = it.carry?.capacity?.let(money::format)
                )
            }
        } else {
            null
        }
        mutable.update { now ->
            now.copy(
                spent = line?.spent?.let(money::format),
                carry = line?.budget?.carry?.carryIn?.let { money.format(it, signed = true) },
                scope = scope(),
                amount = pesos?.let { money.format(Money.cop(it)) },
                chips = chips,
                chosen = saved.get<String>(KEY_CHOSEN)
                    ?.let { name -> ChipKind.entries.firstOrNull { it.name == name } },
                fact = fact
            ).let { it.copy(canSave = canSave(it, pesos)) }
        }
    }

    private fun canSave(state: LimitSheetState, pesos: Long?): Boolean =
        state.categoryId != null && !state.loading && !state.loadFailed && !state.busy &&
            pesos != null && pesos != currentLimit?.minorUnits &&
            (state.error == null || state.error == LimitSheetError.NotSaved)

    /** The shortcuts for one category, without one equal to its limit or to another. */
    private fun chips(month: MonthlySnapshot, categoryId: CategoryId): List<LimitChip> {
        val shortcuts = (LimitAdjustmentPolicy.shortcuts(month, categoryId) as? Outcome.Success)
            ?.value ?: return emptyList()
        return listOfNotNull(
            shortcuts.spent?.let { ChipKind.Spent to it },
            shortcuts.coverSpent?.let { ChipKind.CoverSpent to it },
            shortcuts.coverMonth?.let { ChipKind.CoverMonth to it },
            shortcuts.coverPayments?.let { ChipKind.CoverPayments to it }
        )
            .filter { (_, amount) -> amount.minorUnits != currentLimit?.minorUnits }
            .distinctBy { (_, amount) -> amount.minorUnits }
            .map { (kind, amount) -> LimitChip(kind, money.format(amount), amount.minorUnits) }
    }

    private fun save() {
        val now = mutable.value
        // The month the screen was opened on is the one the person is adjusting.
        if (session.currentMonthNow != month) {
            return mutable.update { it.copy(error = LimitSheetError.MonthEnded, canSave = false) }
        }
        val id = (now.categoryId?.let(CategoryId::of) as? Outcome.Success)?.value ?: return
        val generation = generation ?: return
        val version = version ?: return
        val pesos = digits()?.toLongOrNull() ?: return
        val limit = (BudgetLimit.finite(Money.cop(pesos)) as? Outcome.Success)?.value ?: return
        mutable.update { it.copy(busy = true, canSave = false, error = null) }
        val scope = now.scope
        viewModelScope.launch {
            settle(
                submission.send(commands::find) { operation ->
                    commands.submit(
                        when (scope) {
                            LimitScope.ThisMonth ->
                                SetMonthLimit(operation, generation, id, version, month, limit)

                            LimitScope.FromThisMonth ->
                                SetDefaultLimit(operation, generation, id, version, limit)
                        }
                    )
                }
            )
        }
    }

    /** Asks how the saved operation ended. Nothing is sent again. */
    private suspend fun checkSaved() {
        if (!submission.pending) return
        mutable.update { it.copy(busy = true, canSave = false) }
        settle(submission.recover(commands::find))
    }

    private fun settle(settled: Settled<CommittedCategoryChange, CategoryRejection>?) {
        when (settled) {
            // Something is still in flight and will settle; or nothing was left to ask about.
            null -> if (!submission.pending) {
                mutable.update { it.copy(busy = false) }
                render()
            }

            is Settled.Committed -> done()

            is Settled.Unknown -> mutable.update {
                it.copy(busy = false, error = LimitSheetError.Unknown, canSave = false)
            }

            Settled.NotCommitted -> {
                mutable.update { it.copy(busy = false, error = LimitSheetError.NotSaved) }
                render()
            }

            // Certain: nothing was written, so this attempt is over.
            is Settled.Rejected -> {
                val error = when (val reason = settled.reason) {
                    is CommandRejection.VersionConflict -> LimitSheetError.Conflict

                    is CommandRejection.StaleGeneration -> LimitSheetError.DataChanged

                    Specific(CategoryRejection.CategoryMissing) -> LimitSheetError.CategoryGone

                    Specific(CategoryRejection.CategoryArchived) -> LimitSheetError.Archived

                    is Specific -> when (reason.reason) {
                        is CategoryRejection.ChainBaseAboveLimit -> LimitSheetError.LimitAboveCap
                        is CategoryRejection.CarryUnavailable -> LimitSheetError.CarryUnavailable
                        else -> LimitSheetError.NotSaved
                    }

                    else -> LimitSheetError.NotSaved
                }
                mutable.update { it.copy(busy = false, error = error) }
                render()
            }
        }
    }

    /** The receipt is in: say so with what was saved, and close. */
    private fun done() {
        val now = mutable.value
        digits()?.toLongOrNull()?.let { pesos ->
            notices.categoryChanged(
                CorrectionNotice.LimitAdjusted(
                    categoryName = now.name,
                    limit = money.format(Money.cop(pesos)),
                    month = month,
                    fromThisMonth = scope() == LimitScope.FromThisMonth
                )
            )
        }
        close()
    }

    private fun close() {
        watching?.cancel()
        watching = null
        snapshot = null
        listOf(KEY_CATEGORY, KEY_DIGITS, KEY_CHOSEN, KEY_SCOPE).forEach { saved.remove<Any>(it) }
        submission.clear()
        mutable.value = LimitSheetState(month)
    }

    companion object {
        /** The route's month: the one the screen was opened on. */
        const val KEY_MONTH = "month"
        const val KEY_CATEGORY = "limit.category"
        const val KEY_SCOPE = "limit.scope"
        const val KEY_DIGITS = "limit.digits"
        const val KEY_CHOSEN = "limit.chosen"
        const val KEY_OPERATION = "limit.operation"
    }
}
