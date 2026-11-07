package com.software.sello.feature.recibo

import androidx.compose.ui.graphics.ImageBitmap
import com.software.sello.domain.port.SharedExport
import java.time.YearMonth

/** Compartir el mes: closed, or the preview sheet over Resumen for [ShareMonthState.Open.month]. */
sealed interface ShareMonthState {
    data object Closed : ShareMonthState

    data class Open(
        val month: YearMonth,
        val preview: SharePreview,
        val step: ShareStep = ShareStep.Previewing
    ) : ShareMonthState {
        /** Drawing or placing the image: nothing else can be pressed. */
        val busy: Boolean get() = step == ShareStep.Capturing || step == ShareStep.Placing
    }
}

/** What the sheet shows of the month. Only [Ready] can be shared. */
sealed interface SharePreview {
    data object Loading : SharePreview

    /** The image, from a fresh read of Recibo's snapshot. */
    data class Ready(val image: MonthShareImage) : SharePreview

    /** Recibo shows older figures: sharing waits for a fresh read and says so. */
    data object Waiting : SharePreview

    /** The month was never read. */
    data object Failed : SharePreview

    /** The month has no category to show. */
    data object Empty : SharePreview
}

/** Where Compartir imagen has got to. Nothing is written before [Placing]. */
sealed interface ShareStep {
    data object Previewing : ShareStep

    /** The screen is to draw the image it shows and send [ShareMonthAction.Captured]. */
    data object Capturing : ShareStep

    /** Turning the image into a PNG and placing it in `cache/exports/`. */
    data object Placing : ShareStep

    /** Placed; the screen is to open the share sheet with it, then send Opened. */
    data class ReadyToShare(val share: SharedExport) : ShareStep

    /** Nothing left the phone; Compartir imagen tries again. */
    data object Failed : ShareStep
}

sealed interface ShareMonthAction {
    /** The share button on Resumen's bar. */
    data object Open : ShareMonthAction

    /** Cancelar, Back or the scrim: nothing is written. */
    data object Cancel : ShareMonthAction

    /** Read the month again, after a failed read. */
    data object Retry : ShareMonthAction

    /** Compartir imagen. */
    data object Confirm : ShareMonthAction

    /** The image as the sheet drew it. */
    class Captured(val image: ImageBitmap) : ShareMonthAction

    /** The image could not be drawn. */
    data object CaptureFailed : ShareMonthAction

    /** The share sheet was opened with the placed image. */
    data object Opened : ShareMonthAction
}
