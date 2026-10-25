package com.software.sello.feature.recurrence

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.DeleteSchedule
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.ResumeSchedule
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.expense.HandSnapshots
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.pesos
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.ScheduleChange
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.presentation.recurrence.RepeatAction
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Changing Arriendo, 950.000 on the 5th from 1 Oct, on jue 22 oct 2026 (recurrence
 * examples RE17–RE24). Expected dates are worked out by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEditorViewModelTest {
    private val clock = HandFinancialClock("2026-10-22")
    private val reads = HandScheduleReads()
    private val commands = HandScheduleCommands()
    private val snapshots = HandSnapshots()
    private var nextId = 500
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })
    private lateinit var corrections: ExpenseCorrections

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        corrections = ExpenseCorrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            { Duration.ZERO },
            { uuid(nextId++) },
            CoroutineScope(Dispatchers.Main)
        )
    }

    @After
    fun reset() = Dispatchers.resetMain()

    private fun d(text: String) = LocalDate.parse(text)

    private fun <T> Outcome<T, *>.value() = (this as Outcome.Success).value

    private fun operation(number: Int) = OperationId.of(uuid(number)).value()

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).value()

    private fun rent(
        version: Long = 3,
        pauses: List<SchedulePause> = emptyList(),
        category: Int = 1
    ) = StoredSchedule(
        Schedule(
            scheduleId(1),
            ScheduledEntry.Payment(
                categoryId(category),
                pesos(950_000),
                Note.of("Arriendo").value()
            ),
            Recurrence.of(monthly(5), d("2026-10-01"), null, pauses).value()
        ),
        sequence = 1,
        handled = Occurrence(d("2026-10-05"), 1),
        version = version,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun month(archived: Boolean = false) = snapshots.ready(
        monthOf(
            "2026-10-22",
            listOf(
                categoryIn(1, "Hogar", 400_000, archived = archived),
                categoryIn(2, "Comida", 200_000, icon = "restaurant")
            )
        )
    )

    private fun opened(
        stored: StoredSchedule = rent(),
        saved: SavedStateHandle = SavedStateHandle(
            mapOf(ScheduleEditorViewModel.KEY_SCHEDULE to scheduleId(1).value)
        )
    ): ScheduleEditorViewModel {
        reads.schedules.clear()
        reads.schedules += stored
        month()
        return ScheduleEditorViewModel(saved, commands, reads, snapshots, clock, {
            uuid(nextId++)
        }, money, corrections)
    }

    private val ScheduleEditorViewModel.now get() = state.value

    private fun ScheduleEditorViewModel.type(digits: String) {
        onAction(ScheduleEditorAction.ClearAmount)
        digits.forEach { onAction(ScheduleEditorAction.Digit(it.digitToInt())) }
    }

    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    @Test
    fun itOpensOnWhatIsStoredWithNothingToSaveYet() {
        val editor = opened()

        assertEquals("950.000 pesos", editor.now.amount?.spoken)
        assertEquals(uuid(1), editor.now.categoryId)
        assertEquals("Arriendo", editor.now.note)
        assertEquals(monthly(5), editor.now.repeat.choice?.rule())
        assertEquals(d("2026-11-05"), editor.now.next)
        assertEquals(ScheduleMissing.Changes, editor.now.missing)
        assertFalse(editor.now.canSave)
    }

    @Test
    fun re17aNewAmountKeepsTheRuleAndItsStart() {
        val editor = opened()
        editor.type("1000000")
        editor.onAction(ScheduleEditorAction.Save)

        assertEquals(
            listOf(
                ChangeSchedule(
                    operation(500),
                    1,
                    scheduleId(1),
                    3,
                    ScheduledEntry.Payment(
                        categoryId(1),
                        pesos(1_000_000),
                        Note.of("Arriendo").value()
                    ),
                    monthly(5),
                    d("2026-10-01"),
                    null
                )
            ),
            commands.submitted
        )
        assertEquals(
            CorrectionNotice.ScheduleChanged(ScheduleChange.Changed, income = false),
            corrections.notice.value
        )
        assertTrue(editor.now.closed)
    }

    @Test
    fun re18aNewDayStartsTomorrowOrOnALaterDayPicked() {
        val editor = opened()
        editor.onAction(ScheduleEditorAction.Repeat(RepeatAction.Open))
        editor.onAction(ScheduleEditorAction.Repeat(RepeatAction.DayToggled(25)))
        editor.onAction(ScheduleEditorAction.Repeat(RepeatAction.Done))
        val tomorrow = editor.now.start
        // Today or earlier is not a start for new terms; a later day is.
        editor.onAction(ScheduleEditorAction.StartPicked(d("2026-10-22")))
        val refused = editor.now.start
        editor.onAction(ScheduleEditorAction.StartPicked(d("2026-11-01")))
        editor.onAction(ScheduleEditorAction.Save)

        assertEquals(d("2026-10-23"), tomorrow)
        assertEquals(d("2026-10-23"), refused)
        val sent = commands.submitted.single() as ChangeSchedule
        assertEquals(monthly(25) to d("2026-11-01"), sent.rule to sent.start)
    }

    @Test
    fun pausingResumingAndDeletingEachSendTheirCommand() {
        val pausing = opened()
        pausing.onAction(ScheduleEditorAction.Pause)
        val paused = opened(rent(pauses = listOf(SchedulePause(d("2026-10-12"), null))))
        val pausedFrom = paused.now.pausedFrom
        paused.onAction(ScheduleEditorAction.Resume)
        val deleting = opened()
        deleting.onAction(ScheduleEditorAction.Delete)
        val asked = deleting.now.confirmDelete
        val sentBeforeConfirming = commands.submitted.size
        deleting.onAction(ScheduleEditorAction.DeleteConfirmed)

        assertEquals(d("2026-10-12"), pausedFrom)
        assertTrue(asked)
        assertEquals(2, sentBeforeConfirming)
        assertEquals(
            listOf(
                PauseSchedule(operation(500), 1, scheduleId(1), 3),
                ResumeSchedule(operation(501), 1, scheduleId(1), 3),
                DeleteSchedule(operation(502), 1, scheduleId(1), 3)
            ),
            commands.submitted
        )
        assertEquals(
            CorrectionNotice.ScheduleChanged(ScheduleChange.Deleted, income = false),
            corrections.notice.value
        )
    }

    @Test
    fun aRefusalSaysWhyAndAChangedScheduleCannotBeSentOver() {
        val editor = opened()
        editor.type("1000000")
        commands.answer = { ScheduleCommandOutcome.Rejected(ScheduleRejection.CatchUpPending) }
        editor.onAction(ScheduleEditorAction.Save)
        val pending = editor.now
        commands.answer = { ScheduleCommandOutcome.Rejected(ScheduleRejection.VersionConflict(4)) }
        editor.onAction(ScheduleEditorAction.Save)

        assertEquals(ScheduleEditorError.CatchUpPending, pending.error)
        assertTrue(pending.canSave)
        assertEquals(ScheduleEditorError.ScheduleChanged, editor.now.error)
        assertFalse(editor.now.canSave)
        assertNull(corrections.notice.value)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutAfterARestartAndNeverSentTwice() {
        val saved =
            SavedStateHandle(mapOf(ScheduleEditorViewModel.KEY_SCHEDULE to scheduleId(1).value))
        val editor = opened(saved = saved)
        commands.answer = { ScheduleCommandOutcome.OutcomeUnknown(it.operationId) }
        commands.found = { Outcome.Failure(StorageFailure.Unavailable("x")) }
        editor.onAction(ScheduleEditorAction.Pause)
        val unknown = editor.now
        commands.found = { Outcome.Success(commands.committed(it)) }

        // The process stops and the screen comes back: it asks, it does not send.
        val restored = opened(saved = saved.afterProcessDeath())

        assertEquals(ScheduleEditorError.Unknown, unknown.error)
        assertFalse(unknown.editable)
        assertEquals(1, commands.submitted.size)
        assertEquals(listOf(operation(500), operation(500)), commands.looked)
        assertTrue(restored.now.closed)
        assertEquals(
            CorrectionNotice.ScheduleChanged(ScheduleChange.Paused, income = false),
            corrections.notice.value
        )
    }

    @Test
    fun aDraftKeptAcrossARestartIsRefusedWhenTheScheduleChangedMeanwhile() {
        val saved =
            SavedStateHandle(mapOf(ScheduleEditorViewModel.KEY_SCHEDULE to scheduleId(1).value))
        val editor = opened(saved = saved)
        editor.type("1000000")

        val restored = opened(rent(version = 4), saved.afterProcessDeath())

        assertEquals("1.000.000 pesos", restored.now.amount?.spoken)
        assertEquals(ScheduleEditorError.ScheduleChanged, restored.now.error)
        assertFalse(restored.now.canSave)
    }

    @Test
    fun aFailedReadSaysSoAndRetryReadsAgain() {
        reads.failing = true
        val editor = opened()
        val failed = editor.now.loadFailed
        reads.failing = false
        editor.onAction(ScheduleEditorAction.Retry)

        assertTrue(failed)
        assertFalse(editor.now.loadFailed)
        assertEquals("950.000 pesos", editor.now.amount?.spoken)
    }

    @Test
    fun aPaymentPausedByArchivingItsCategorySaysWhichAndNeedsAnother() {
        reads.schedules.clear()
        reads.schedules += rent(pauses = listOf(SchedulePause(d("2026-10-12"), null)))
        month(archived = true)
        val editor = ScheduleEditorViewModel(
            SavedStateHandle(mapOf(ScheduleEditorViewModel.KEY_SCHEDULE to scheduleId(1).value)),
            commands,
            reads,
            snapshots,
            clock,
            { uuid(nextId++) },
            money,
            corrections
        )

        assertEquals("Hogar", editor.now.pausedByArchive)
        editor.type("1000000")
        assertEquals(ScheduleMissing.Category, editor.now.missing)
        editor.onAction(ScheduleEditorAction.CategoryPicked(uuid(2)))
        assertNull(editor.now.missing)
    }
}
