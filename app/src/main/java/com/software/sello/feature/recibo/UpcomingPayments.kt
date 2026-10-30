package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.ScheduleRow
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.month.monthNames
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One reserved payment: its schedule, name (note, else category), category and date. */
data class UpcomingPayment(
    val scheduleId: String,
    val name: String,
    val categoryName: String?,
    val iconKey: String?,
    val date: LocalDate,
    val amount: MoneyTextValue
)

sealed interface UpcomingState {
    data object Loading : UpcomingState

    /** The month could not be read: never shown as nothing reserved. */
    data object Failed : UpcomingState

    /** Nothing is reserved any more this month: all of it was recorded. */
    data class Empty(val month: YearMonth) : UpcomingState

    data class Ready(
        val month: YearMonth,
        val today: LocalDate,
        val total: MoneyTextValue,
        val payments: List<UpcomingPayment>
    ) : UpcomingState
}

/**
 * Pagos previstos (docs/design/v2/fijos.html): the payments the month in progress
 * reserves, from the same snapshot as Recibo's footnote, so the two always agree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingPaymentsViewModel(
    snapshots: MonthlySnapshots,
    clock: FinancialClock,
    private val money: MoneyFormatter
) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    val state: StateFlow<UpcomingState> = combine(
        clock.today.map { it.date }.distinctUntilChanged(),
        attempts
    ) { today, _ -> today }
        .flatMapLatest { today ->
            snapshots.observe(YearMonth.from(today)).map { render(it, today) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UpcomingState.Loading)

    fun retry() = attempts.update { it + 1 }

    private fun render(state: SnapshotState, today: LocalDate): UpcomingState {
        val snapshot = when (state) {
            ReadState.Loading -> return UpcomingState.Loading
            is ReadState.Failed -> return UpcomingState.Failed
            is ReadState.Ready -> state.value
        }
        val reservation = (snapshot.overall as? OverallBudget.Limited)?.reservation
            ?: return UpcomingState.Empty(snapshot.month)
        val categories = snapshot.categories.associateBy { it.category.id }
        return UpcomingState.Ready(
            month = snapshot.month,
            today = today,
            total = money.format(reservation.total),
            payments = reservation.payments.map { payment ->
                val category = categories[payment.categoryId]?.category
                UpcomingPayment(
                    scheduleId = payment.schedule.value,
                    name = payment.note?.value ?: category?.name?.value.orEmpty(),
                    categoryName = category?.name?.value,
                    iconKey = category?.icon?.value,
                    date = payment.occurrence.date,
                    amount = money.format(payment.amount.money)
                )
            }
        )
    }
}

@Composable
fun UpcomingPaymentsRoot(
    viewModels: ViewModelProvider.Factory,
    padding: PaddingValues,
    onOpenSchedule: (String) -> Unit,
    onAllSchedules: () -> Unit
) {
    val model: UpcomingPaymentsViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    UpcomingPaymentsScreen(state, padding, model::retry, onOpenSchedule, onAllSchedules)
}

@Composable
fun UpcomingPaymentsScreen(
    state: UpcomingState,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onOpenSchedule: (String) -> Unit,
    onAllSchedules: () -> Unit
) {
    val months = monthNames()
    val dates = dateLabels()
    LazyColumn(
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = Modifier.testTag(UPCOMING_TAG)
    ) {
        when (state) {
            UpcomingState.Loading -> item { SlipSkeleton() }

            UpcomingState.Failed -> item {
                ErrorSlip(
                    message = stringResource(R.string.upcoming_failed),
                    onRetry = onRetry,
                    title = stringResource(R.string.upcoming_failed_title),
                    icon = SelloIcon.Error
                )
            }

            is UpcomingState.Empty -> item {
                EmptySlip(
                    title = stringResource(
                        R.string.upcoming_empty_title,
                        months.inSentence(state.month)
                    ),
                    body = stringResource(R.string.upcoming_empty_body),
                    icon = SelloIcon.Check,
                    actionLabel = stringResource(R.string.upcoming_all_schedules),
                    onAction = onAllSchedules
                )
            }

            is UpcomingState.Ready -> {
                item {
                    val count = pluralStringResource(
                        R.plurals.recibo_reserved_payments,
                        state.payments.size,
                        state.payments.size
                    )
                    AnswerSlip(
                        label = stringResource(
                            R.string.upcoming_title,
                            months.inSentence(state.month)
                        ),
                        amount = state.total,
                        stamp = null,
                        ink = StampInk.Neutral
                    ) {
                        Text(
                            stringResource(
                                R.string.upcoming_until,
                                count,
                                dates.day(state.month.atEndOfMonth(), state.today)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = SelloTheme.colors.inkSoft
                        )
                        TearLine()
                        state.payments.forEachIndexed { index, payment ->
                            val day = dates.day(payment.date, state.today)
                            val caption = listOfNotNull(
                                payment.categoryName,
                                day
                            ).joinToString(" · ")
                            ScheduleRow(
                                name = payment.name,
                                caption = caption,
                                amount = payment.amount,
                                icon =
                                    payment.iconKey?.let(CategoryIcons::iconFor)
                                        ?: SelloIcon.ReceiptLong,
                                onClick = { onOpenSchedule(payment.scheduleId) },
                                spoken = stringResource(
                                    R.string.upcoming_row_spoken,
                                    payment.name,
                                    payment.amount.spoken,
                                    caption
                                ),
                                modifier = Modifier.testTag(
                                    "upcoming.${payment.scheduleId}.${payment.date}"
                                ),
                                last = index == state.payments.lastIndex
                            )
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.upcoming_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = SelloTheme.colors.inkSoft,
                        modifier = Modifier.padding(horizontal = SelloTheme.spacing.xs)
                    )
                }
                item {
                    SelloButton(
                        text = stringResource(R.string.upcoming_all_schedules),
                        onClick = onAllSchedules,
                        kind = ButtonKind.Text
                    )
                }
            }
        }
    }
}

const val UPCOMING_TAG = "upcoming"
