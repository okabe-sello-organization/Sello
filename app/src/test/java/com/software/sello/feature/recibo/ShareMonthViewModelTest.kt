package com.software.sello.feature.recibo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ExportShares
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SharedExport
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareMonthViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val clock = HandFinancialClock("2026-10-28")
    private val session = MonthSession(clock, HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val png = HandPng()
    private val shares = HandShares()
    private val image = HandImage()

    private val fresh = monthOf(
        "2026-10-28",
        listOf(categoryIn(1, "Alimentación", 900_000, icon = "restaurant")),
        listOf(storedExpense(100, 1, "2026-10-03", 812_400))
    )

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun TestScope.started(): ShareMonthViewModel {
        val viewModel = ShareMonthViewModel(
            session,
            snapshots,
            HandOrderings(),
            testMoney(),
            png,
            shares
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        return viewModel
    }

    private fun ShareMonthViewModel.open() = state.value as ShareMonthState.Open

    private fun ShareMonthViewModel.readyToCapture() {
        snapshots.ready(fresh)
        onAction(ShareMonthAction.Open)
        onAction(ShareMonthAction.Confirm)
    }

    @Test
    fun closedItReadsNothingAndOpenItReadsTheMonthOnScreen() = runTest {
        val viewModel = started()

        assertEquals(ShareMonthState.Closed, viewModel.state.value)
        assertEquals(emptyList<YearMonth>(), snapshots.observed)

        viewModel.onAction(ShareMonthAction.Open)
        assertEquals(ShareMonthState.Open(october, SharePreview.Loading), viewModel.state.value)
        assertEquals(listOf(october), snapshots.observed)

        snapshots.ready(fresh)
        val preview = viewModel.open().preview as SharePreview.Ready
        assertEquals(october, preview.image.month)
    }

    @Test
    fun cancelClosesTheSheetAndWritesNothing() = runTest {
        val viewModel = started()
        snapshots.ready(fresh)
        viewModel.onAction(ShareMonthAction.Open)

        viewModel.onAction(ShareMonthAction.Cancel)

        assertEquals(ShareMonthState.Closed, viewModel.state.value)
        assertEquals(0, png.encoded)
        assertEquals(emptyList<String>(), shares.placed)
        assertEquals(0, shares.cleared)
    }

    @Test
    fun olderFiguresWaitAndCannotBeShared() = runTest {
        val viewModel = started()
        snapshots.ready(fresh)
        viewModel.onAction(ShareMonthAction.Open)

        snapshots.of(october).value = ReadState.Failed(failure, lastGood = fresh)
        assertEquals(SharePreview.Waiting, viewModel.open().preview)
        viewModel.onAction(ShareMonthAction.Confirm)
        assertEquals(ShareStep.Previewing, viewModel.open().step)

        // A fresh read lets it go ahead.
        snapshots.ready(fresh)
        viewModel.onAction(ShareMonthAction.Confirm)
        assertEquals(ShareStep.Capturing, viewModel.open().step)
    }

    @Test
    fun aMonthNeverReadIsAFailureThatRetryReadsAgain() = runTest {
        val viewModel = started()
        viewModel.onAction(ShareMonthAction.Open)
        snapshots.of(october).value = ReadState.Failed(failure, lastGood = null)
        assertEquals(SharePreview.Failed, viewModel.open().preview)

        viewModel.onAction(ShareMonthAction.Retry)

        assertEquals(listOf(october, october), snapshots.observed)
    }

    @Test
    fun aMonthWithoutCategoriesHasNothingToShare() = runTest {
        val viewModel = started()
        snapshots.ready(monthOf("2026-10-28", emptyList()))
        viewModel.onAction(ShareMonthAction.Open)

        assertEquals(SharePreview.Empty, viewModel.open().preview)
        viewModel.onAction(ShareMonthAction.Confirm)
        assertEquals(ShareStep.Previewing, viewModel.open().step)
    }

    @Test
    fun theDrawnImageIsPlacedAsOnePngThenTheShareSheetOpens() = runTest {
        val viewModel = started()
        viewModel.readyToCapture()
        assertEquals(ShareStep.Capturing, viewModel.open().step)
        assertTrue(viewModel.open().busy)

        viewModel.onAction(ShareMonthAction.Captured(image))

        assertEquals(listOf(image), png.images)
        assertEquals(listOf("sello-2026-10.png image/png 3"), shares.placed)
        assertEquals(
            ShareStep.ReadyToShare(
                SharedExport("content://exports/sello-2026-10.png", "image/png")
            ),
            viewModel.open().step
        )

        viewModel.onAction(ShareMonthAction.Opened)
        assertEquals(ShareMonthState.Closed, viewModel.state.value)
    }

    @Test
    fun aSecondPressPlacesNothingMore() = runTest {
        val viewModel = started()
        viewModel.readyToCapture()
        viewModel.onAction(ShareMonthAction.Confirm)
        val waiting = CompletableDeferred<SharedExport?>()
        shares.answer = { waiting.await() }

        viewModel.onAction(ShareMonthAction.Captured(image))
        viewModel.onAction(ShareMonthAction.Captured(image))
        viewModel.onAction(ShareMonthAction.Confirm)
        // Busy: Cancel and Back do nothing either.
        viewModel.onAction(ShareMonthAction.Cancel)

        assertEquals(ShareStep.Placing, viewModel.open().step)
        assertEquals(1, shares.placed.size)
        waiting.complete(SharedExport("content://exports/sello-2026-10.png", "image/png"))
        assertTrue(viewModel.open().step is ShareStep.ReadyToShare)
    }

    @Test
    fun aFailedPlaceSaysSoAndCompartirTriesAgain() = runTest {
        val viewModel = started()
        viewModel.readyToCapture()
        shares.answer = { null }

        viewModel.onAction(ShareMonthAction.Captured(image))
        assertEquals(ShareStep.Failed, viewModel.open().step)

        viewModel.onAction(ShareMonthAction.Confirm)
        assertEquals(ShareStep.Capturing, viewModel.open().step)
    }

    @Test
    fun anImageThatCannotBeDrawnOrEncodedIsNeverPlaced() = runTest {
        val viewModel = started()
        viewModel.readyToCapture()
        viewModel.onAction(ShareMonthAction.CaptureFailed)
        assertEquals(ShareStep.Failed, viewModel.open().step)

        viewModel.onAction(ShareMonthAction.Confirm)
        png.answer = null
        viewModel.onAction(ShareMonthAction.Captured(image))

        assertEquals(ShareStep.Failed, viewModel.open().step)
        assertEquals(emptyList<String>(), shares.placed)
    }

    @Test
    fun anImageDrawnAsTheFiguresWentOlderIsNotPlaced() = runTest {
        val viewModel = started()
        viewModel.readyToCapture()
        snapshots.of(october).value = ReadState.Failed(failure, lastGood = fresh)

        viewModel.onAction(ShareMonthAction.Captured(image))

        assertEquals(ShareStep.Previewing, viewModel.open().step)
        assertEquals(0, png.encoded)
        assertEquals(emptyList<String>(), shares.placed)
    }

    @Test
    fun figuresThatWentOlderWhileItWasPlacedRemoveItAndNeverReachTheShareSheet() = runTest {
        val viewModel = started()
        viewModel.readyToCapture()
        val placing = CompletableDeferred<SharedExport?>()
        shares.answer = { placing.await() }
        viewModel.onAction(ShareMonthAction.Captured(image))

        snapshots.of(october).value = ReadState.Failed(failure, lastGood = fresh)
        placing.complete(SharedExport("content://exports/sello-2026-10.png", "image/png"))

        assertEquals(SharePreview.Waiting, viewModel.open().preview)
        assertEquals(ShareStep.Previewing, viewModel.open().step)
        assertEquals(1, shares.cleared)
    }

    @Test
    fun leavingWhileItIsPlacedRemovesWhatWasWritten() = runTest {
        val viewModel = started()
        viewModel.readyToCapture()
        val never = CompletableDeferred<SharedExport?>()
        shares.answer = { never.await() }
        viewModel.onAction(ShareMonthAction.Captured(image))
        assertEquals(1, shares.placed.size)

        // What clearing the view model does when its screen goes.
        viewModel.viewModelScope.cancel()

        assertEquals(1, shares.cleared)
    }

    private val failure = SnapshotFailure.Storage(StorageFailure.Unavailable("disk"))
}

/** Encodes by hand: three bytes, or nothing when [answer] is null. */
private class HandPng : ImagePng {
    val images = mutableListOf<ImageBitmap>()
    val encoded get() = images.size
    var answer: ByteArray? = byteArrayOf(1, 2, 3)

    override suspend fun encode(image: ImageBitmap): ByteArray? {
        images += image
        return answer
    }
}

/** Records each placement as "name type size"; [answer] decides what it returns. */
private class HandShares : ExportShares {
    val placed = mutableListOf<String>()
    var cleared = 0
    var answer: suspend (
        String
    ) -> SharedExport? = { SharedExport("content://exports/$it", "image/png") }

    override suspend fun place(bytes: ByteArray, name: String, mimeType: String): SharedExport? {
        placed += "$name $mimeType ${bytes.size}"
        return answer(name)
    }

    override suspend fun clear() {
        cleared++
    }
}

/** An image that is never drawn: the view model only passes it on. */
private class HandImage : ImageBitmap {
    override val width = 1
    override val height = 1
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val hasAlpha = false
    override val config = ImageBitmapConfig.Argb8888

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int
    ) = Unit

    override fun prepareToDraw() = Unit
}
