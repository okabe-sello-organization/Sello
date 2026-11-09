package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.policy.BackupReminderPolicy
import com.software.sello.domain.policy.ClosedMonthInvitePolicy
import com.software.sello.domain.policy.NudgePolicy
import com.software.sello.domain.port.BackupRecords
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.ClosedMonthInvitePreferences
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.HistoryStartReads
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.YearSnapshots
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import java.time.Month
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shows the selected month from the shared snapshot, through [ReciboRenderModel]:
 * no amount is added, compared or defaulted here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReciboViewModel(
    session: MonthSession,
    private val snapshots: MonthlySnapshots,
    preferences: CategoryOrderingPreferences,
    money: MoneyFormatter,
    private val invitations: ClosedMonthInvitePreferences,
    clock: FinancialClock,
    private val categories: CategoryReads,
    private val years: YearSnapshots,
    private val guidance: GuidancePreferences,
    backups: BackupRecords,
    history: HistoryStartReads,
    private val saved: SavedStateHandle
) : ViewModel() {
    private val render = ReciboRenderModel(money)
    private val attempt = MutableStateFlow(0)

    /** The month as last read, so a Retry or a return never blanks figures already shown. */
    private val lastGood = LastGoodRead<MonthlySnapshot, SnapshotFailure>()

    private val ordering = preferences.observeCategoryOrdering()
        .map { result ->
            when (result) {
                is PreferenceRead.Ready -> result.value
                is PreferenceRead.Failed -> result.lastGood ?: CategoryOrderingPreference()
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            CategoryOrderingPreference()
        )

    /** What was handled in this session, so the line goes even when keeping it fails. */
    private val handledHere = MutableStateFlow<YearMonth?>(null)

    /**
     * The latest month handled, kept or only in this session. A failed read hides nothing:
     * it is only advice.
     */
    private val handled = combine(
        invitations.observeHandled().map { read ->
            when (read) {
                is PreferenceRead.Ready -> read.value
                is PreferenceRead.Failed -> read.lastGood
            }
        },
        handledHere
    ) { kept, here -> listOfNotNull(kept, here).maxOrNull() }

    /**
     * The month just closed, on the financial day, while it had expenses and was not yet
     * handled. In January's first week it is the year that closed instead, while the year
     * had expenses: never December alone. Its read is only made when the invitation could
     * show, and a failed read hides the line: it is advice, never a figure.
     */
    private val invitation = combine(
        session.selectedMonth,
        clock.today,
        handled
    ) { shown, today, done ->
        ClosedMonthInvitePolicy.monthToReview(today.date, shown)
            ?.takeIf { done == null || it > done }
            ?.let { month ->
                val year = ClosedMonthInvitePolicy.yearToCelebrate(today.date, shown)
                ReciboInvitation(month, closesYear = year != null)
            }
    }
        .distinctUntilChanged()
        .flatMapLatest { candidate ->
            when {
                candidate == null -> flowOf(null)

                candidate.closesYear -> years.observe(candidate.month.year).map { read ->
                    candidate.takeIf {
                        read is ReadState.Ready && read.value.expenseCount > 0
                    }
                }

                else -> snapshots.observe(candidate.month).map { read ->
                    candidate.takeIf {
                        read is ReadState.Ready && read.value.spent.minorUnits > 0
                    }
                }
            }
        }
        // Recibo's first state never waits on a preference read: no line until it is known.
        .onStart { emit(null) }

    /** Nudges closed in this session, so they go even when keeping that fails. */
    private val closedHere = MutableStateFlow<Set<Nudge>>(emptySet())

    /**
     * Whether the goal card is shown taking turns among two or more goals, with current
     * figures (SELLO-213), as the root reports it from the card itself: Hidden, loading,
     * failed or older goals count as no, so the nudge never shows without its card.
     */
    private val goalCarousel = MutableStateFlow(false)

    /**
     * The one nudge due on Recibo: the backup reminder first, then the Guía hint once an
     * expense exists, then the goal card's once it takes turns among two or more goals. Only
     * readings that worked count: while the guidance, the last backup or the history is
     * loading or failed, no nudge shows, never one worked out from an older value.
     */
    private val nudge = combine(
        guidance.observeGuidance(),
        backups.observeBackups(),
        combine(history.observe(), goalCarousel, ::Pair),
        clock.today,
        closedHere
    ) { guidanceRead, backupRead, (historyRead, carousel), today, closed ->
        if (
            guidanceRead !is PreferenceRead.Ready ||
            backupRead !is PreferenceRead.Ready ||
            historyRead !is ReadState.Ready
        ) {
            return@combine null
        }
        val memory = guidanceRead.value.memory
        val start = historyRead.value
        val reminder =
            BackupReminderPolicy.due(today, backupRead.value.last?.at, start.firstMovement, memory)
        val wanted = listOfNotNull(
            Nudge.BackupReminder.takeIf { reminder != null },
            Nudge.GuideHint.takeIf { start.firstExpense != null },
            Nudge.ReciboGoal.takeIf { carousel }
        ) - closed
        val picked = NudgePolicy.pick(guidanceRead.value, today.date, wanted)
        val render = when (picked) {
            Nudge.BackupReminder -> ReciboNudge.BackupReminder(reminder!!.daysSinceBackup)
            Nudge.GuideHint -> ReciboNudge.GuideHint
            Nudge.ReciboGoal -> ReciboNudge.Goal
            else -> null
        }
        val shown = if (picked != null && render != null) {
            ShownOnRecibo(
                ShownNudge(picked, today.date),
                render,
                kept = NudgePolicy.keptFor(memory, picked, today.date)
            )
        } else {
            null
        }
        shown
    }
        // Recibo's first state never waits on these reads: no nudge until they are known.
        .onStart { emit(null) }
        .distinctUntilChanged()

    /** The nudge last asked to be kept as shown, so one showing is written once. */
    private var keeping: ShownNudge? = null

    val state: StateFlow<ReciboState> =
        combine(session.selectedMonth, attempt) { month, _ -> month }
            .flatMapLatest { month ->
                lastGood.keep(month, snapshots.observe(month))
                    .combine(ordering) { snapshot, preference ->
                        render.recibo(snapshot, preference)
                    }
                    .mapLatest { named(it, month) }
                    .mapLatest { withTakenNames(it, month) }
            }
            .combine(invitation) { state, invitation ->
                if (state is ReciboState.Month) state.copy(invitation = invitation) else state
            }
            // Only a month's current figures carry a nudge: never while loading, failed or
            // empty, nor over older figures or a carry problem, whose notice comes first.
            .combine(nudge) { state, nudge ->
                val fresh = state is ReciboState.Month && !state.stale && state.carryProblem == null
                val shown = nudge.takeIf { fresh }
                keepShown(shown)
                if (state is ReciboState.Month) state.copy(nudge = shown?.render) else state
            }
            // Kept across a restart, as the screen's other state is; shown with the month.
            .combine(saved.getStateFlow<String?>(KEY_HELP, null)) { state, help ->
                if (state is ReciboState.Month) state.copy(help = ReciboHelp.named(help)) else state
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
                ReciboState.Loading
            )

    /**
     * The first run with the names categories already use, archived ones too, read as the
     * category form reads them. A failed read leaves them unknown, so no starter is offered.
     */
    private suspend fun withTakenNames(state: ReciboState, month: YearMonth): ReciboState {
        if (state !is ReciboState.FirstRun) return state
        val read = categories.monthBudget(month) as? Outcome.Success
        return ReciboState.FirstRun(
            read?.value?.categories?.map { it.category.name.uniquenessKey }?.toSet()
        )
    }

    /**
     * A carry failure with no earlier read to take the name from: the category is named
     * from the month's categories, so the repair can say which one. Without them it is
     * said without a name, never left out.
     */
    private suspend fun named(state: ReciboState, month: YearMonth): ReciboState {
        val failed = state as? ReciboState.CarryFailed ?: return state
        if (failed.problem.name != null) return state
        val name = (categories.monthBudget(month) as? Outcome.Success)?.value?.categories
            ?.firstOrNull {
                it.category.id.value == failed.problem.categoryId
            }?.category?.name?.value
        return failed.copy(problem = failed.problem.copy(name = name))
    }

    fun onAction(action: ReciboAction) {
        when (action) {
            // A new attempt starts a new observation of the same month.
            ReciboAction.Retry -> attempt.update { it + 1 }

            // Remembered either way; opening the month is the root's.
            is ReciboAction.OpenInvitation -> handle(action.month)

            is ReciboAction.DismissInvitation -> handle(action.month)

            // The year card stands in December's place: handling it handles December.
            is ReciboAction.OpenYear -> handle(YearMonth.of(action.year, Month.DECEMBER))

            is ReciboAction.ShowHelp -> saved[KEY_HELP] = action.topic.name

            // The Guía opens over Recibo: Back returns to the month, not to the sheet.
            ReciboAction.CloseHelp, ReciboAction.OpenHelpGuide -> saved[KEY_HELP] = null

            // Closed for good; opening the Guía is the root's.
            ReciboAction.OpenGuide,
            ReciboAction.CloseGuideHint -> close(Nudge.GuideHint) { guidance.markSeen(it) }

            ReciboAction.CloseGoalNudge -> close(Nudge.ReciboGoal) { guidance.markSeen(it) }

            is ReciboAction.GoalCard -> goalCarousel.value = action.takesTurns

            ReciboAction.TurnOffBackupReminder ->
                close(Nudge.BackupReminder) { guidance.turnOffBackupReminder() }

            // Handled by the root, which owns navigation and the selection.
            ReciboAction.OpenBackup,
            ReciboAction.CreateCategory,
            is ReciboAction.StartCategory,
            is ReciboAction.OpenCategory,
            is ReciboAction.EditCategory,
            ReciboAction.OpenReserved,
            ReciboAction.AdjustBudget,
            is ReciboAction.SetMonthLimit -> Unit
        }
    }

    /** Gone for [month] at once, and across restarts when it can be kept. */
    private fun handle(month: YearMonth) {
        handledHere.update { maxOf(it ?: month, month) }
        viewModelScope.launch { invitations.markHandled(month) }
    }

    /** Gone at once, and for good when it can be kept; a failed write lets it come back later. */
    private fun close(nudge: Nudge, keep: suspend (Nudge) -> Unit) {
        closedHere.update { it + nudge }
        viewModelScope.launch { keep(nudge) }
    }

    /**
     * Keeps the day [shown] showed on, for the one-a-day rule, unless it is kept already. The
     * same write keeps an expired nudge as seen. A failed write is not retried until
     * something changes: the nudge still shows.
     */
    private fun keepShown(shown: ShownOnRecibo?) {
        val showing = shown?.takeUnless { it.kept }?.nudge ?: return
        if (keeping == showing) return
        keeping = showing
        viewModelScope.launch { guidance.markShown(showing.nudge, showing.day) }
    }

    /** A nudge Recibo shows: what it is and its day, what to draw, and if that day is kept. */
    private data class ShownOnRecibo(
        val nudge: ShownNudge,
        val render: ReciboNudge,
        val kept: Boolean
    )

    private companion object {
        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L

        const val KEY_HELP = "reciboHelp"
    }
}
