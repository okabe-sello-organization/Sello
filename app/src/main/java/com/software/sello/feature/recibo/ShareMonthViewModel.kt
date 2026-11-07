package com.software.sello.feature.recibo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.ExportShares
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compartir el mes for the month on screen. While the sheet is open it reads Recibo's
 * snapshot for that month, so the image says only what Recibo says; older figures wait for
 * a fresh read. Nothing is written until Compartir imagen, and then only the PNG, through
 * [ExportShares]: one file in `cache/exports/`, replacing any earlier one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareMonthViewModel(
    session: MonthSession,
    private val snapshots: MonthlySnapshots,
    preferences: CategoryOrderingPreferences,
    money: MoneyFormatter,
    private val png: ImagePng,
    private val shares: ExportShares
) : ViewModel() {
    private val render = MonthShareRenderModel(money)
    private val open = MutableStateFlow(false)
    private val attempt = MutableStateFlow(0)
    private val step = MutableStateFlow<ShareStep>(ShareStep.Previewing)
    private val lastGood = LastGoodRead<MonthlySnapshot, SnapshotFailure>()

    /** Recibo's order; a failed read keeps the last order known, as Recibo does. */
    private val ordering = preferences.observeCategoryOrdering().map { result ->
        when (result) {
            is PreferenceRead.Ready -> result.value
            is PreferenceRead.Failed -> result.lastGood ?: CategoryOrderingPreference()
        }
    }

    val state: StateFlow<ShareMonthState> =
        combine(open, session.selectedMonth, attempt) { isOpen, month, _ ->
            month.takeIf { isOpen }
        }
            .flatMapLatest { month ->
                if (month == null) {
                    flowOf(null)
                } else {
                    lastGood.keep(month, snapshots.observe(month))
                        .combine(ordering) { read, order -> month to preview(read, order) }
                }
            }
            .combine(step) { shown, now ->
                shown?.let { (month, preview) -> ShareMonthState.Open(month, preview, now) }
                    ?: ShareMonthState.Closed
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
                ShareMonthState.Closed
            )

    private fun preview(read: SnapshotState, order: CategoryOrderingPreference): SharePreview =
        when (read) {
            ReadState.Loading -> SharePreview.Loading

            is ReadState.Ready ->
                render.image(read.value, order)?.let(SharePreview::Ready) ?: SharePreview.Empty

            // Older figures are never shared: the sheet waits for a fresh read.
            is ReadState.Failed ->
                if (read.lastGood != null) SharePreview.Waiting else SharePreview.Failed
        }

    fun onAction(action: ShareMonthAction) {
        val shown = state.value as? ShareMonthState.Open
        when (action) {
            ShareMonthAction.Open -> if (!open.value) {
                step.value = ShareStep.Previewing
                open.value = true
            }

            ShareMonthAction.Cancel -> if (shown?.busy != true) close()

            ShareMonthAction.Retry -> attempt.update { it + 1 }

            ShareMonthAction.Confirm -> if (
                shown?.preview is SharePreview.Ready &&
                (shown.step == ShareStep.Previewing || shown.step == ShareStep.Failed)
            ) {
                step.value = ShareStep.Capturing
            }

            is ShareMonthAction.Captured -> when {
                shown?.step != ShareStep.Capturing -> Unit

                // The figures went older while it was drawn: it waits with the rest.
                shown.preview !is SharePreview.Ready -> step.value = ShareStep.Previewing

                else -> place(action, shown.month)
            }

            ShareMonthAction.CaptureFailed -> if (shown?.step == ShareStep.Capturing) {
                step.value = ShareStep.Failed
            }

            ShareMonthAction.Opened -> if (shown?.step is ShareStep.ReadyToShare) close()
        }
    }

    private fun place(captured: ShareMonthAction.Captured, month: YearMonth) {
        step.value = ShareStep.Placing
        viewModelScope.launch {
            step.value = try {
                val bytes = png.encode(captured.image)
                bytes?.let { shares.place(it, "sello-$month.png", PNG) }
                    ?.let(ShareStep::ReadyToShare)
                    ?: ShareStep.Failed
            } catch (cancelled: CancellationException) {
                // Left before it was shared: whatever was written is removed.
                withContext(NonCancellable) { shares.clear() }
                throw cancelled
            }
        }
    }

    private fun close() {
        open.value = false
        step.value = ShareStep.Previewing
    }

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
        const val PNG = "image/png"
    }
}
