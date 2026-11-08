package com.software.sello.feature.resumen

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.DismissedSuggestion
import com.software.sello.domain.model.LimitAdjustment
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.domain.port.SuggestionPreferences
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.feature.recurrence.scheduleId
import com.software.sello.feature.settings.HandSmallExpenses
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.testing.asNewObservation
import com.software.sello.testing.testDateLabels
import com.software.sello.testing.testMonthNames
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResumenViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val session = MonthSession(HandFinancialClock("2026-10-13"), HandMonotonicClock())
    private val reads = HandResumenSnapshots()
    private val suggestions = HandSuggestions()
    private val clock = HandFinancialClock("2026-10-22")
    private val schedules = HandScheduleReads()
    private val smallExpenses = HandSmallExpenses()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(saved: SavedStateHandle = SavedStateHandle()) = ResumenViewModel(
        saved,
        reads,
        session,
        resumenMoney(),
        testDateLabels(),
        testMonthNames(),
        suggestions,
        smallExpenses,
        schedules,
        clock
    )

    private fun TestScope.watch(model: ResumenViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

    @Test
    fun oneObservationFeedsEveryConsumerAndStopsWhenTheTabIsNotVisible() = runTest {
        val model = model()
        assertEquals(0, reads.active)
        val first = watch(model)
        val second = watch(model)
        assertEquals(1, reads.active)
        assertEquals(1, reads.started)
        first.cancel()
        assertEquals(1, reads.active)
        second.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, reads.active)
        watch(model)
        assertEquals(1, reads.active)
        assertEquals(2, reads.started)
    }

    @Test
    fun usesTheSharedMonthAndKeepsReadErrorsDistinctFromZero() = runTest {
        val model = model()
        watch(model)
        assertEquals(ResumenState.Loading, model.state.value)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())
        assertEquals("150.000 pesos", (model.state.value as ResumenState.Ready).income.spoken)
        reads.of(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)
        assertEquals(true, (model.state.value as ResumenState.Ready).stale)
        model.onAction(ResumenAction.Retry)
        assertEquals(2, reads.started)
        assertEquals(true, (model.state.value as ResumenState.Ready).stale)
        val september = YearMonth.of(2026, 9)
        session.select(september)
        assertEquals(ResumenState.Loading, model.state.value)
        assertEquals(1, reads.active)
    }

    @Test
    fun aSavedGastosHormigaLineReachesTheSlipWithoutReadingTheMonthAgain() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(smallSnapshot())

        // 5 × 3.000 = 15.000 at or under 20.000.
        val shown = (model.state.value as ResumenState.Ready).smallExpenses
            as ResumenSmallExpenses.Shown
        assertEquals("15.000", shown.total.digits)

        // A line of 2.000 leaves none of them small: no slip.
        smallExpenses.read.value = PreferenceRead.Ready(SmallExpenseLine.of(2_000)!!)
        assertNull((model.state.value as ResumenState.Ready).smallExpenses)
        assertEquals(1, reads.started)
    }

    @Test
    fun aLineThatFailsAfreshKeepsTheLastOneReadThroughReintentar() = runTest {
        smallExpenses.read.value = PreferenceRead.Ready(SmallExpenseLine.of(15_000)!!)
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(smallSnapshot())

        // A new read that fails has no last good line of its own, as DataStore's.
        smallExpenses.read.value = PreferenceRead.Failed(PreferenceFailure.Unavailable, null)
        val older = (model.state.value as ResumenState.Ready).smallExpenses
            as ResumenSmallExpenses.Shown
        assertTrue(older.olderLine)
        assertEquals("15.000", older.line)

        model.onAction(ResumenAction.Retry)

        val again = (model.state.value as ResumenState.Ready).smallExpenses
            as ResumenSmallExpenses.Shown
        assertTrue(again.olderLine)
        assertEquals("15.000", again.line)
        assertEquals("15.000", again.total.digits)
    }

    @Test
    fun reintentarReadsTheLineAgainToo() = runTest {
        smallExpenses.read.value =
            PreferenceRead.Failed(PreferenceFailure.Unavailable, null)
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(smallSnapshot())
        assertEquals(
            ResumenSmallExpenses.Unreadable,
            (model.state.value as ResumenState.Ready).smallExpenses
        )
        val asked = smallExpenses.observations

        model.onAction(ResumenAction.Retry)
        smallExpenses.read.value = PreferenceRead.Ready(SmallExpenseLine.DEFAULT)

        assertEquals(asked + 1, smallExpenses.observations)
        assertTrue(
            (model.state.value as ResumenState.Ready).smallExpenses is ResumenSmallExpenses.Shown
        )
    }

    @Test
    fun aFailedReadBeforeAnyIsTheErrorAndRetryKeepsItUntilARead() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)
        assertEquals(ResumenState.Failed, model.state.value)
        model.onAction(ResumenAction.Retry)
        assertEquals(ResumenState.Failed, model.state.value)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())
        assertEquals(false, (model.state.value as ResumenState.Ready).stale)
    }

    @Test
    fun comingBackAfterTheTabStoppedKeepsTheFiguresWithNoSkeleton() = runTest {
        val model = model()
        val first = watch(model)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())
        val shown = model.state.value as ResumenState.Ready
        first.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, reads.active)
        val seen = mutableListOf<ResumenState>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { seen += it }
        }

        assertEquals(2, reads.started)
        assertEquals(listOf<ResumenState>(shown), seen.distinct())
    }

    @Test
    fun exactSelectedValueUpdatesAfterCorrectionWithoutChangingAllocation() = runTest {
        val model = model()
        watch(model)
        val original = resumenSnapshot(income = 200_000)
        reads.of(october).value = ReadState.Ready(original)
        model.onAction(ResumenAction.SelectHistory("2026-10"))
        val corrected = resumenSnapshot(spent = 27_000, income = 200_000)
        reads.of(october).value = ReadState.Ready(corrected)
        val shown = model.state.value as ResumenState.Ready
        assertEquals("2026-10", shown.selectedHistory)
        assertEquals("27.000 pesos", shown.history.last().amount.spoken)
        assertEquals("173.000 pesos", shown.netFlow.spoken)
        assertEquals(1, reads.started)
    }

    @Test
    fun selectionExpiresWithVirtualTimeAndUnknownIdsAreIgnored() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())
        model.onAction(ResumenAction.SelectHistory("not-a-month"))
        assertNull((model.state.value as ResumenState.Ready).selectedHistory)
        model.onAction(ResumenAction.SelectHistory("2026-10"))
        advanceTimeBy(2_999)
        assertEquals("2026-10", (model.state.value as ResumenState.Ready).selectedHistory)
        advanceTimeBy(2)
        runCurrent()
        assertNull((model.state.value as ResumenState.Ready).selectedHistory)
        assertEquals(1, reads.started)
    }

    @Test
    fun aHelpSheetOpensClosesAndSurvivesARestart() = runTest {
        val saved = SavedStateHandle()
        val model = model(saved)
        watch(model)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())

        model.onAction(ResumenAction.OpenHelp(ResumenHelp.SmallExpenses))
        assertEquals(ResumenHelp.SmallExpenses, (model.state.value as ResumenState.Ready).help)

        // The process dies with the sheet open: the new screen opens it again.
        val restored = model(SavedStateHandle(mapOf("resumen.help" to "SmallExpenses")))
        watch(restored)
        assertEquals(ResumenHelp.SmallExpenses, (restored.state.value as ResumenState.Ready).help)

        model.onAction(ResumenAction.CloseHelp)
        assertNull((model.state.value as ResumenState.Ready).help)
        assertNull(saved.get<String>("resumen.help"))
    }

    @Test
    fun eachHelpOpensItsOwnSheetAndAnUnknownOneOpensNone() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())
        ResumenHelp.entries.forEach { help ->
            model.onAction(ResumenAction.OpenHelp(help))
            assertEquals(help, (model.state.value as ResumenState.Ready).help)
        }

        val restored = model(SavedStateHandle(mapOf("resumen.help" to "Trucos")))
        watch(restored)
        assertNull((restored.state.value as ResumenState.Ready).help)
    }

    @Test
    fun restoredSelectionUsesSourceIdsAndDoesNotLeakIntoAnotherMonth() = runTest {
        val saved = SavedStateHandle(mapOf("resumen.history" to "2026-10|2026-10"))
        val model = model(saved)
        watch(model)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())
        assertEquals("2026-10", (model.state.value as ResumenState.Ready).selectedHistory)
        val september = YearMonth.of(2026, 9)
        session.select(september)
        val original = resumenSnapshot()
        reads.of(september).value = ReadState.Ready(
            original.copy(budget = original.budget.copy(month = september))
        )
        assertNull((model.state.value as ResumenState.Ready).selectedHistory)
    }

    // --- SELLO-123: the stub's fixed payments and incomes ---

    private fun bills(day: Int, handled: String? = null, pausedFrom: String? = null) =
        StoredSchedule(
            Schedule(
                scheduleId(day),
                ScheduledEntry.Payment(
                    categoryId(1),
                    (TransactionAmount.of(Money.cop(90_000)) as Outcome.Success).value,
                    null
                ),
                (
                    Recurrence.of(
                        (RepeatRule.Monthly.of(day) as Outcome.Success).value,
                        LocalDate.parse("2026-10-01"),
                        null,
                        pausedFrom?.let {
                            listOf(SchedulePause(LocalDate.parse(it), null))
                        }.orEmpty()
                    ) as Outcome.Success
                    ).value
            ),
            sequence = day.toLong(),
            handled = handled?.let { Occurrence(LocalDate.parse(it), 1) },
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )

    private fun shown(model: ResumenViewModel) = (model.state.value as ResumenState.Ready).nextFixed

    @Test
    fun theStubSaysTheNearestFixedOneStillToComeAndNothingWhenItCannotBeRead() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(resumenSnapshot())
        // While loading, no fact: never "none".
        assertEquals(ResumenNextFixed.Unknown, shown(model))

        // The 5th went through on 5 Oct, so 5 Nov; the 28th is still to come; the 15th is paused.
        schedules.state.value = ReadState.Ready(
            ScheduleBook(
                1,
                1,
                listOf(
                    bills(5, handled = "2026-10-05"),
                    bills(28),
                    bills(15, pausedFrom = "2026-10-10")
                )
            )
        )
        val today = LocalDate.parse("2026-10-22")
        assertEquals(ResumenNextFixed.On(LocalDate.parse("2026-10-28"), today), shown(model))

        clock.goTo("2026-10-29")
        // The 28th not recorded yet by catch-up: it is today's.
        assertEquals(
            ResumenNextFixed.On(LocalDate.parse("2026-10-29"), LocalDate.parse("2026-10-29")),
            shown(model)
        )

        schedules.state.value = ReadState.Ready(ScheduleBook(1, 2, emptyList()))
        assertEquals(ResumenNextFixed.None, shown(model))

        schedules.state.value = ReadState.Failed(
            StorageFailure.Unavailable("hand"),
            ScheduleBook(1, 2, emptyList())
        )
        assertEquals(ResumenNextFixed.Unknown, shown(model))
    }

    // --- The suggestion ---

    private val hogar = (
        CategoryId.of(
            "00000000-0000-4000-8000-000000000002"
        ) as Outcome.Success
        ).value

    private fun withSuggestion() = resumenSnapshot().copy(
        suggestion = LimitAdjustment(
            hogar,
            (CategoryName.of("Hogar") as Outcome.Success).value,
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
            Money.cop(230_000),
            Money.cop(200_000)
        )
    )

    @Test
    fun dismissingTheSuggestionRemembersItForThisMonthAndHidesIt() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(withSuggestion())
        assertEquals("Hogar", (model.state.value as ResumenState.Ready).suggestion?.name)

        model.onAction(ResumenAction.DismissSuggestion)

        assertEquals(listOf(DismissedSuggestion(october, hogar)), suggestions.written)
        assertNull((model.state.value as ResumenState.Ready).suggestion)
    }

    private val ResumenViewModel.ready get() = state.value as ResumenState.Ready

    /** #377: a dismissal that was not saved is said, with the suggestion still there. */
    @Test
    fun aDismissalThatIsNotSavedIsSaidAndKeepsTheSuggestion() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(withSuggestion())
        suggestions.failing = true

        model.onAction(ResumenAction.DismissSuggestion)

        assertEquals(SuggestionDismissal.Failed, model.ready.dismissal)
        assertEquals("Hogar", model.ready.suggestion?.name)
    }

    /** A failure is said only by the suggestion it was asked for, never by another one. */
    @Test
    fun aDismissalFailureIsNotSaidByASuggestionForAnotherCategory() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(withSuggestion())
        suggestions.failing = true
        model.onAction(ResumenAction.DismissSuggestion)
        val failed = model.ready.dismissal
        val comida = (CategoryId.of("00000000-0000-4000-8000-000000000003") as Outcome.Success)
            .value

        reads.of(october).value = ReadState.Ready(
            withSuggestion().copy(
                suggestion = withSuggestion().suggestion!!.copy(
                    categoryId = comida,
                    name = (CategoryName.of("Comida") as Outcome.Success).value
                )
            )
        )

        assertEquals(SuggestionDismissal.Failed, failed)
        assertEquals("Comida", model.ready.suggestion?.name)
        assertEquals(SuggestionDismissal.Idle, model.ready.dismissal)
    }

    @Test
    fun dismissingAgainAfterAFailureThatSavesClearsTheFailureAndHidesTheSuggestion() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(withSuggestion())
        suggestions.failing = true
        model.onAction(ResumenAction.DismissSuggestion)
        suggestions.failing = false

        model.onAction(ResumenAction.DismissSuggestion)

        assertEquals(2, suggestions.written.size)
        assertEquals(SuggestionDismissal.Idle, model.ready.dismissal)
        assertNull(model.ready.suggestion)
    }

    @Test
    fun aSecondTapWhileTheDismissalIsSavedSendsNothingMore() = runTest {
        val model = model()
        watch(model)
        reads.of(october).value = ReadState.Ready(withSuggestion())
        val hold = CompletableDeferred<Unit>()
        suggestions.hold = hold

        model.onAction(ResumenAction.DismissSuggestion)
        val saving = model.ready.dismissal
        model.onAction(ResumenAction.DismissSuggestion)
        hold.complete(Unit)
        runCurrent()

        assertEquals(SuggestionDismissal.Saving, saving)
        assertEquals(1, suggestions.written.size)
        assertEquals(SuggestionDismissal.Idle, model.ready.dismissal)
        assertNull(model.ready.suggestion)
    }

    @Test
    fun aSuggestionDismissedLastMonthComesBackWhileItStillApplies() = runTest {
        suggestions.stored.value =
            PreferenceRead.Ready(DismissedSuggestion(YearMonth.of(2026, 9), hogar))
        val model = model()
        watch(model)

        reads.of(october).value = ReadState.Ready(withSuggestion())

        assertEquals("Hogar", (model.state.value as ResumenState.Ready).suggestion?.name)
    }

    @Test
    fun aDismissalThatCannotBeReadHidesNothing() = runTest {
        suggestions.stored.value =
            PreferenceRead.Failed(PreferenceFailure.InvalidValue, null)
        val model = model()
        watch(model)

        reads.of(october).value = ReadState.Ready(withSuggestion())

        assertEquals("Hogar", (model.state.value as ResumenState.Ready).suggestion?.name)
    }
}

private class HandSuggestions : SuggestionPreferences {
    val stored = MutableStateFlow<PreferenceRead<DismissedSuggestion?>>(PreferenceRead.Ready(null))
    val written = mutableListOf<DismissedSuggestion>()
    var failing = false
    var hold: CompletableDeferred<Unit>? = null

    override fun observeDismissedSuggestion(): Flow<PreferenceRead<DismissedSuggestion?>> = stored

    override suspend fun dismissSuggestion(dismissed: DismissedSuggestion): PreferenceWrite {
        written += dismissed
        hold?.await()
        if (failing) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        stored.value = PreferenceRead.Ready(dismissed)
        return PreferenceWrite.Saved
    }
}

private class HandResumenSnapshots : ResumenSnapshots {
    private val states = mutableMapOf<YearMonth, MutableStateFlow<ResumenSnapshotState>>()
    var active = 0
    var started = 0

    fun of(month: YearMonth) = states.getOrPut(month) {
        MutableStateFlow<ResumenSnapshotState>(ReadState.Loading)
    }

    override suspend fun read(month: YearMonth): Outcome<ResumenSnapshot, SnapshotFailure> =
        error("The screen observes once, not per chart")

    /** Each observation is new, as the real one is: Loading first, no earlier read kept. */
    override fun observe(month: YearMonth): Flow<ResumenSnapshotState> = of(month)
        .asNewObservation()
        .onStart {
            active++
            started++
        }.onCompletion { active-- }
}
