package com.software.sello.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.SmallExpensePreferences
import com.software.sello.presentation.money.MoneyFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Gastos hormiga's line (SELLO-194): whole pesos typed on the Sello pad, 1.000 to 100.000.
 * The typed digits live in [saved], so the draft outlives process death. A preference, not
 * money: one write, no operation ID (ARCHITECTURE §5); a value outside the range is never
 * sent, and a failed save changes nothing and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmallExpenseLineViewModel(
    private val saved: SavedStateHandle,
    private val preferences: SmallExpensePreferences,
    private val money: MoneyFormatter
) : ViewModel() {
    private data class Progress(
        val busy: Boolean = false,
        val notSaved: Boolean = false,
        val closed: Boolean = false,
        val limitPulse: Int = 0
    )

    private val draft = MutableStateFlow(restored())
    private val progress = MutableStateFlow(Progress())
    private val attempts = MutableStateFlow(0)

    /** Null while the first read is on its way. */
    private val current = attempts.flatMapLatest { preferences.observeSmallExpenseLine() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<SmallExpenseLineState> =
        combine(draft, progress, current, ::render)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                render(draft.value, progress.value, current.value)
            )

    private fun restored(): AmountDraft {
        val digits = saved.get<String>(DRAFT) ?: return AmountDraft.Empty
        // Only digits this form wrote; anything else is no draft.
        return if (digits.matches(DIGITS)) AmountDraft.Digits(digits) else AmountDraft.Empty
    }

    fun onAction(action: SmallExpenseLineAction) {
        val now = state.value
        val editable = now.editable && !now.loading && !now.loadFailed
        when (action) {
            is SmallExpenseLineAction.Digit -> if (editable) key(draft.value.digit(action.digit))

            SmallExpenseLineAction.TripleZero -> if (editable) key(draft.value.tripleZero())

            SmallExpenseLineAction.Backspace -> if (editable) change(draft.value.backspace())

            SmallExpenseLineAction.Clear -> if (editable) change(AmountDraft.Empty)

            SmallExpenseLineAction.Submit -> if (now.canSubmit) submit()

            SmallExpenseLineAction.Retry -> if (!now.busy && now.loadFailed) {
                attempts.update { it + 1 }
            }

            // Never closed while a save is on its way: its result comes first.
            SmallExpenseLineAction.Back -> if (!now.busy) {
                progress.update { it.copy(closed = true) }
            }
        }
    }

    private fun key(change: AmountDraft.Change) {
        change(change.draft)
        if (change.limitReached) {
            progress.update { it.copy(limitPulse = it.limitPulse + 1) }
        }
    }

    private fun change(next: AmountDraft) {
        if (next != draft.value) progress.update { it.copy(notSaved = false) }
        saved[DRAFT] = (next as? AmountDraft.Digits)?.digits
        draft.value = next
    }

    private fun pesos(draft: AmountDraft): Long? = (draft as? AmountDraft.Digits)?.digits?.toLong()

    private fun render(
        draft: AmountDraft,
        progress: Progress,
        read: PreferenceRead<SmallExpenseLine>?
    ): SmallExpenseLineState {
        val line = when (read) {
            null -> null
            is PreferenceRead.Ready -> read.value
            is PreferenceRead.Failed -> read.lastGood
        }
        val pesos = pesos(draft)
        return SmallExpenseLineState(
            loading = read == null,
            loadFailed = read is PreferenceRead.Failed && line == null,
            amount = pesos?.let { money.format(Money.cop(it)).digits },
            problem = when {
                pesos == null -> null
                pesos < SmallExpenseLine.MIN_PESOS -> LineProblem.BelowMinimum
                pesos > SmallExpenseLine.MAX_PESOS -> LineProblem.AboveMaximum
                else -> null
            },
            current = line?.let { money.format(it.money).digits },
            limitPulse = progress.limitPulse,
            busy = progress.busy,
            notSaved = progress.notSaved,
            closed = progress.closed
        )
    }

    private fun submit() {
        val line = pesos(draft.value)?.let(SmallExpenseLine::of) ?: return
        progress.update { it.copy(busy = true, notSaved = false) }
        viewModelScope.launch {
            val result = preferences.setSmallExpenseLine(line)
            progress.update {
                when (result) {
                    PreferenceWrite.Saved -> it.copy(busy = false, closed = true)
                    is PreferenceWrite.Failed -> it.copy(busy = false, notSaved = true)
                }
            }
        }
    }

    private companion object {
        const val DRAFT = "small.line.draft"
        val DIGITS = Regex("[1-9][0-9]{0,11}")
    }
}
