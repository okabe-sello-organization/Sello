package com.software.sello.feature.resumen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.DismissedSuggestion
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.policy.ScheduleListPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.SmallExpensePreferences
import com.software.sello.domain.port.SuggestionPreferences
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.read.LastGoodRead
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModel(
    private val saved: SavedStateHandle,
    reads: ResumenSnapshots,
    session: MonthSession,
    money: MoneyFormatter,
    labels: DateLabels,
    months: MonthNames,
    private val suggestions: SuggestionPreferences,
    smallExpenses: SmallExpensePreferences,
    schedules: ScheduleReads,
    clock: FinancialClock
) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val render = ResumenRenderModel(money, labels, months)
    private var weekdayExpiry: Job? = null
    private var historyExpiry: Job? = null

    /** The month as last read, so a Retry or a return never blanks figures already shown. */
    private val lastGood = LastGoodRead<ResumenSnapshot, SnapshotFailure>()

    /** The last line read in this view model's life, for a read that fails afresh. */
    private var lastLine: SmallExpenseLine? = null

    /**
     * Gastos hormiga's line; Reintentar reads it again too. A new read starts with no last
     * good line of its own, so a failure keeps the one this view model read last.
     */
    private val line = attempts.flatMapLatest { smallExpenses.observeSmallExpenseLine() }
        .map { read ->
            when (read) {
                is PreferenceRead.Ready -> read.also { lastLine = it.value }

                is PreferenceRead.Failed -> {
                    read.lastGood?.let { lastLine = it }
                    PreferenceRead.Failed(read.failure, read.lastGood ?: lastLine)
                }
            }
        }

    private val snapshot = combine(
        combine(session.selectedMonth, attempts) { month, _ -> month }
            .flatMapLatest { month -> lastGood.keep(month, reads.observe(month)) },
        session.currentMonth,
        line,
        render::render
    )

    /** What was dismissed, as last read. A failed read hides nothing: it is only advice. */
    private val dismissed = suggestions.observeDismissedSuggestion().map { read ->
        when (read) {
            is PreferenceRead.Ready -> read.value
            is PreferenceRead.Failed -> read.lastGood
        }
    }

    /** The dismissal being saved or not saved, with the suggestion it was asked for. */
    private data class Dismissal(val of: DismissedSuggestion, val status: SuggestionDismissal)

    private val dismissal = MutableStateFlow<Dismissal?>(null)

    private val dismissing = combine(dismissed, dismissal) { stored, asked -> stored to asked }

    /** The stub's fact: the nearest fixed one still to come, on the financial day. */
    private val nextFixed = combine(schedules.observe(), clock.today) { read, today ->
        when (read) {
            is ReadState.Ready -> {
                val date = today.date
                val lines = ScheduleListPolicy.lines(
                    read.value.schedules,
                    date,
                    YearMonth.from(date)
                )
                ScheduleListPolicy.nextDate(lines, date)?.let { ResumenNextFixed.On(it, date) }
                    ?: ResumenNextFixed.None
            }

            // A failed read is never "none", even with an earlier book to hand.
            ReadState.Loading, is ReadState.Failed -> ResumenNextFixed.Unknown
        }
    }

    /** The help sheet open, kept across a restart; a name this version lacks opens none. */
    private val help = saved.getStateFlow<String?>(HELP, null).map { name ->
        ResumenHelp.entries.firstOrNull { it.name == name }
    }

    val state = combine(
        combine(snapshot, help) { state, help -> state to help },
        saved.getStateFlow<String?>(HISTORY, null),
        saved.getStateFlow<String?>(WEEKDAY, null),
        dismissing,
        nextFixed
    ) { (state, help), history, weekday, (dismissed, asked), next ->
        if (state is ResumenState.Ready) {
            val shown = state.suggestion?.takeUnless {
                dismissed?.month?.toString() == state.month &&
                    dismissed.categoryId.value == it.categoryId
            }
            state.copy(
                help = help,
                nextFixed = next,
                selectedHistory = selection(history, state.month, state.history),
                selectedWeekday = selection(
                    weekday,
                    state.month,
                    state.weekdays?.points.orEmpty()
                ),
                suggestion = shown,
                // Said only by the suggestion it was asked for, while it is shown.
                dismissal = asked?.takeIf {
                    shown != null && it.of.month.toString() == state.month &&
                        it.of.categoryId.value == shown.categoryId
                }?.status ?: SuggestionDismissal.Idle
            )
        } else {
            state
        }
    }
        // The last state is kept while the tab is away, so coming back keeps the scroll.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenState.Loading)

    init {
        if (saved.get<String>(HISTORY) != null) historyExpiry = expire(HISTORY)
        if (saved.get<String>(WEEKDAY) != null) weekdayExpiry = expire(WEEKDAY)
    }

    fun onAction(action: ResumenAction) {
        when (action) {
            ResumenAction.Retry -> attempts.update { it + 1 }
            is ResumenAction.SelectHistory -> select(HISTORY, action.id)
            is ResumenAction.SelectWeekday -> select(WEEKDAY, action.id)
            ResumenAction.DismissSuggestion -> dismiss()
            is ResumenAction.OpenHelp -> saved[HELP] = action.help.name
            ResumenAction.CloseHelp -> saved[HELP] = null
        }
    }

    /** Remembers it for this month only: if it still applies next month, it comes back. */
    private fun dismiss() {
        val current = state.value as? ResumenState.Ready ?: return
        val suggestion = current.suggestion ?: return
        val month = runCatching { YearMonth.parse(current.month) }.getOrNull() ?: return
        val id = (CategoryId.of(suggestion.categoryId) as? Outcome.Success)?.value ?: return
        // One save at a time: a second tap while it is saved sends nothing more.
        if (dismissal.value?.status == SuggestionDismissal.Saving) return
        val asked = DismissedSuggestion(month, id)
        dismissal.value = Dismissal(asked, SuggestionDismissal.Saving)
        viewModelScope.launch {
            dismissal.value = when (suggestions.dismissSuggestion(asked)) {
                // Hidden by the stored dismissal; an earlier failure goes with it.
                PreferenceWrite.Saved -> null

                is PreferenceWrite.Failed -> Dismissal(asked, SuggestionDismissal.Failed)
            }
        }
    }

    private fun select(key: String, id: String) {
        val current = state.value as? ResumenState.Ready ?: return
        val points = if (key == WEEKDAY) current.weekdays?.points.orEmpty() else current.history
        if (points.none { it.chart.id == id }) return
        saved[key] = "${current.month}|$id"
        if (key == WEEKDAY) {
            weekdayExpiry?.cancel()
            weekdayExpiry = expire(key)
        } else {
            historyExpiry?.cancel()
            historyExpiry = expire(key)
        }
    }

    private fun expire(key: String) = viewModelScope.launch {
        delay(3_000)
        saved[key] = null
    }

    private fun selection(value: String?, month: String, points: List<ResumenPoint>): String? =
        value?.takeIf { it.startsWith("$month|") }?.substringAfter('|')
            ?.takeIf { id -> points.any { it.chart.id == id } }

    private companion object {
        const val HISTORY = "resumen.history"
        const val WEEKDAY = "resumen.weekday"
        const val HELP = "resumen.help"
    }
}
