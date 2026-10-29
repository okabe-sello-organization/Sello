package com.software.sello.feature.recurrence

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.ScheduleRow
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.ScheduleRecordReads
import com.software.sello.domain.port.ScheduleRecordsState
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One entry catch-up recorded. An expense is named by its note, else its category; an
 * income by its note, else its source ([sourceKey], [sourceName]).
 */
data class RecordedItem(
    val id: String,
    val income: Boolean,
    val note: String?,
    val categoryName: String?,
    val iconKey: String?,
    val sourceKey: String?,
    val sourceName: String?,
    val date: LocalDate,
    val amount: MoneyTextValue
)

sealed interface CatchUpNoticeState {
    data object Loading : CatchUpNoticeState

    /** What was recorded could not be read: never shown as nothing. */
    data object Failed : CatchUpNoticeState

    /** [items] since the notice was last dismissed; none means there is no notice. */
    data class Ready(val items: List<RecordedItem>, val today: LocalDate) : CatchUpNoticeState {
        val payments: Int get() = items.count { !it.income }
        val incomes: Int get() = items.count { it.income }
    }
}

/**
 * The notice after catching up (recurrence-examples rule 14; docs/design/v2/fijos.html):
 * every entry catch-up recorded since the person last pressed Listo, across runs, each
 * opening its edit. It is derived from the entries themselves, so one deleted meanwhile
 * leaves it, and only when Listo was pressed is kept, on the audit clock that stamps them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatchUpNoticeViewModel(
    private val preferences: CatchUpNoticePreferences,
    records: ScheduleRecordReads,
    private val categories: CategoryReads,
    clock: FinancialClock,
    private val audit: AuditClock,
    private val money: MoneyFormatter
) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val closing = MutableStateFlow(false)

    /** Listo was pressed and kept: the screen closes. */
    val closed: StateFlow<Boolean> = closing

    val state: StateFlow<CatchUpNoticeState> = combine(
        preferences.observeCatchUpNoticeSeen(),
        attempts
    ) { seen, _ -> seen }
        .flatMapLatest { seen ->
            when (seen) {
                is PreferenceRead.Failed -> flowOf(CatchUpNoticeState.Failed)

                // What was shown belongs to the old "seen": until the read for the new one
                // lands, say nothing rather than keep the bar Listo has just dismissed.
                is PreferenceRead.Ready -> records.observeRecordedAfter(seen.value)
                    .map { read -> render(read, clock.today.value.date) }
                    .onStart { emit(CatchUpNoticeState.Loading) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CatchUpNoticeState.Loading)

    fun retry() = attempts.update { it + 1 }

    /**
     * Listo, or the notice's time on Recibo running out: everything shown so far is seen.
     * The notice stays if that cannot be kept.
     */
    fun done() {
        viewModelScope.launch {
            if (preferences.markCatchUpNoticeSeen(audit.now()) == PreferenceWrite.Saved) {
                closing.value = true
            }
        }
    }

    private suspend fun render(read: ScheduleRecordsState, today: LocalDate): CatchUpNoticeState {
        val records = when (read) {
            ReadState.Loading -> return CatchUpNoticeState.Loading
            is ReadState.Failed -> return CatchUpNoticeState.Failed
            is ReadState.Ready -> read.value
        }
        if (records.isEmpty()) return CatchUpNoticeState.Ready(emptyList(), today)
        // Archived categories included: an entry may still be in one.
        val names = when (val budget = categories.monthBudget(YearMonth.from(today))) {
            is Outcome.Success -> budget.value.categories.associateBy { it.category.id }
            is Outcome.Failure -> return CatchUpNoticeState.Failed
        }
        return CatchUpNoticeState.Ready(
            records.map { record ->
                when (record) {
                    is ScheduleRecord.Payment -> {
                        val category = names[record.expense.categoryId]?.category
                        RecordedItem(
                            id = record.expense.id.value,
                            income = false,
                            note = record.expense.note?.value,
                            categoryName = category?.name?.value,
                            iconKey = category?.icon?.value,
                            sourceKey = null,
                            sourceName = null,
                            date = record.expense.date,
                            amount = money.format(record.expense.amount.money)
                        )
                    }

                    is ScheduleRecord.Earning -> RecordedItem(
                        id = record.income.id.value,
                        income = true,
                        note = record.income.note?.value,
                        categoryName = null,
                        iconKey = null,
                        sourceKey = record.income.source.key,
                        sourceName = (record.income.source as? IncomeSource.Other)?.name?.value,
                        date = record.income.date,
                        amount = money.format(record.income.amount.money)
                    )
                }
            },
            today
        )
    }
}

/**
 * "Sello anotó 4 pagos fijos · Ver", above Anotar on Recibo. It only informs: the person
 * set these up already. It goes by itself after five seconds, longer when a screen reader
 * asks for it, calling [onTimeout]; the time left drains across the bar (in steps with
 * reduced motion). Ver still opens the list while it shows.
 */
@Composable
fun CatchUpNoticeBar(state: CatchUpNoticeState, onTimeout: () -> Unit, onView: () -> Unit) {
    val ready = state as? CatchUpNoticeState.Ready ?: return
    if (ready.items.isEmpty()) return
    val accessibility = LocalAccessibilityManager.current
    val shownFor = remember(accessibility) {
        accessibility?.calculateRecommendedTimeoutMillis(
            CATCH_UP_NOTICE_MILLIS,
            containsIcons = true,
            containsText = true,
            containsControls = true
        ) ?: CATCH_UP_NOTICE_MILLIS
    }
    val reduced = SelloTheme.reducedMotion
    val left = remember(ready.items) { Animatable(1f) }
    val timeUp by rememberUpdatedState(onTimeout)
    // A new recording starts the time again; leaving the screen pauses it with its frames.
    LaunchedEffect(ready.items, shownFor) {
        if (reduced) {
            for (step in TIME_STEPS - 1 downTo 0) {
                delay(shownFor / TIME_STEPS)
                left.snapTo(step.toFloat() / TIME_STEPS)
            }
        } else {
            left.animateTo(0f, tween(shownFor.toInt(), easing = LinearEasing))
        }
        timeUp()
    }
    val count = ready.items.size
    val message = when {
        ready.incomes == 0 -> pluralStringResource(R.plurals.catch_up_payments, count, count)
        ready.payments == 0 -> pluralStringResource(R.plurals.catch_up_incomes, count, count)
        else -> pluralStringResource(R.plurals.catch_up_both, count, count)
    }
    SelloNotice(
        message = message,
        modifier = Modifier.testTag(CATCH_UP_BAR_TAG),
        actionLabel = stringResource(R.string.catch_up_view),
        onAction = onView,
        icon = SelloIcon.Autorenew,
        remaining = left.value
    )
}

/** How long the notice shows before it goes by itself. */
const val CATCH_UP_NOTICE_MILLIS = 5_000L

/** With reduced motion the time left drains in this many steps, not continuously. */
private const val TIME_STEPS = 5

@Composable
fun CatchUpRecordedRoot(
    viewModels: ViewModelProvider.Factory,
    padding: PaddingValues,
    onEdit: (id: String, income: Boolean) -> Unit,
    onClosed: () -> Unit
) {
    val model: CatchUpNoticeViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    val closed by model.closed.collectAsStateWithLifecycle()
    LaunchedEffect(closed) { if (closed) onClosed() }
    CatchUpRecordedScreen(state, padding, model::retry, onEdit)
}

/** Lo que Sello anotó: each entry with its date and amount, opening its edit. */
@Composable
fun CatchUpRecordedScreen(
    state: CatchUpNoticeState,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onEdit: (id: String, income: Boolean) -> Unit
) {
    val dates = dateLabels()
    LazyColumn(
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = Modifier.testTag(CATCH_UP_LIST_TAG)
    ) {
        when (state) {
            CatchUpNoticeState.Loading -> item { SlipSkeleton() }

            CatchUpNoticeState.Failed -> item {
                ErrorSlip(
                    message = stringResource(R.string.catch_up_failed),
                    onRetry = onRetry,
                    title = stringResource(R.string.catch_up_failed_title),
                    icon = SelloIcon.Error
                )
            }

            is CatchUpNoticeState.Ready -> if (state.items.isEmpty()) {
                item {
                    EmptySlip(
                        title = stringResource(R.string.catch_up_empty_title),
                        body = stringResource(R.string.catch_up_empty_body),
                        icon = SelloIcon.Check
                    )
                }
            } else {
                item {
                    Slip(pinked = false) {
                        Text(
                            pluralStringResource(
                                R.plurals.catch_up_heading,
                                state.items.size,
                                state.items.size
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            stringResource(R.string.catch_up_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = SelloTheme.colors.inkSoft
                        )
                        state.items.forEachIndexed { index, item ->
                            val name = item.note ?: item.categoryName
                                ?: item.sourceKey?.let { incomeSourceLabel(it, item.sourceName) }
                                ?: ""
                            val kind = item.categoryName
                                ?: stringResource(R.string.catch_up_income)
                            val caption = "$kind · ${dates.day(item.date, state.today)}"
                            ScheduleRow(
                                name = name,
                                caption = caption,
                                amount = item.amount,
                                icon = when {
                                    item.income -> SelloIcon.AccountBalance
                                    item.iconKey != null -> CategoryIcons.iconFor(item.iconKey)
                                    else -> SelloIcon.ReceiptLong
                                },
                                onClick = { onEdit(item.id, item.income) },
                                spoken = stringResource(
                                    R.string.catch_up_row_spoken,
                                    name,
                                    item.amount.spoken,
                                    caption
                                ),
                                modifier = Modifier.testTag("catch-up.${item.id}"),
                                income = item.income,
                                last = index == state.items.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

const val CATCH_UP_BAR_TAG = "catch-up:bar"
const val CATCH_UP_LIST_TAG = "catch-up:list"
