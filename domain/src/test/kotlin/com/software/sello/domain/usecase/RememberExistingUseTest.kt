package com.software.sello.domain.usecase

import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.ResetPreview
import com.software.sello.domain.port.Resets
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one-time "already using Sello" check (SELLO-205). */
class RememberExistingUseTest {
    /** Counts as a test sets them; [failing] makes the read fail. */
    private class Counted(var summary: BackupSummary = BackupSummary(0, 0, 0, 0, 0, 0)) : Resets {
        var failing = false
        var reads = 0

        override suspend fun preview(): Outcome<ResetPreview, StorageFailure> {
            reads++
            return if (failing) {
                Outcome.Failure(StorageFailure.Unavailable("test"))
            } else {
                Outcome.Success(ResetPreview(summary, generation = 1, revision = 1))
            }
        }

        override suspend fun reset(preview: ResetPreview, operationId: OperationId) =
            throw AssertionError("the check never erases")

        override suspend fun pending() = false

        override suspend fun finishPending(): ResetOutcome.Erased? = null
    }

    /** The guidance as a test sets it; what [rememberExistingUse] was asked is recorded. */
    private class Remembered : GuidancePreferences {
        var value = Guidance()
        var unreadable = false
        var failWrite = false
        val asked = mutableListOf<Boolean>()

        override fun observeGuidance(): Flow<PreferenceRead<Guidance>> = flowOf(
            if (unreadable) {
                PreferenceRead.Failed(PreferenceFailure.InvalidValue, null)
            } else {
                PreferenceRead.Ready(value)
            }
        )

        override suspend fun rememberExistingUse(wasUsingSello: Boolean): PreferenceWrite {
            asked += wasUsingSello
            if (failWrite) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
            val newUser = Nudge.entries.filter { it.forNewUsers }.toSet()
            value = Guidance(
                GuidanceMemory(seen = if (wasUsingSello) newUser else emptySet()),
                existingUseChecked = true
            )
            return PreferenceWrite.Saved
        }

        override suspend fun markSeen(nudge: Nudge) = throw AssertionError("not asked")
        override suspend fun markShown(nudge: Nudge, day: LocalDate) =
            throw AssertionError("not asked")
        override suspend fun turnOffBackupReminder() = throw AssertionError("not asked")
        override suspend fun mergeRestored(memory: GuidanceMemory) =
            throw AssertionError("not asked")
    }

    private val counts = Counted()
    private val guidance = Remembered()
    private val check = RememberExistingUse(counts, guidance)

    @Test
    fun anEmptyPhoneIsANewPerson() = runBlocking {
        assertEquals(ExistingUseCheck.Checked, check.run())
        assertEquals(listOf(false), guidance.asked)
    }

    @Test
    fun anyExpenseIncomeGoalOrInvestmentMeansAlreadyUsingSello() = runBlocking {
        for (summary in listOf(
            BackupSummary(categories = 0, expenses = 1, incomes = 0, goals = 0, schedules = 0),
            BackupSummary(categories = 0, expenses = 0, incomes = 1, goals = 0, schedules = 0),
            BackupSummary(categories = 0, expenses = 0, incomes = 0, goals = 1, schedules = 0),
            BackupSummary(0, 0, 0, 0, 0, investments = 1)
        )) {
            val guidance = Remembered()
            val check = RememberExistingUse(Counted(summary), guidance)

            assertEquals(summary.toString(), ExistingUseCheck.Checked, check.run())
            assertEquals(summary.toString(), listOf(true), guidance.asked)
        }
    }

    @Test
    fun categoriesSchedulesAndChainsAloneAreNotUse() = runBlocking {
        counts.summary = BackupSummary(
            categories = 4,
            expenses = 0,
            incomes = 0,
            goals = 0,
            schedules = 2,
            investments = 0,
            carryChains = 1
        )

        assertEquals(ExistingUseCheck.Checked, check.run())
        assertEquals(listOf(false), guidance.asked)
    }

    @Test
    fun itRunsOnceAndNeverAgain() = runBlocking {
        counts.summary = BackupSummary(0, 3, 0, 0, 0)
        check.run()
        counts.summary = BackupSummary(0, 0, 0, 0, 0)

        assertEquals(ExistingUseCheck.AlreadyChecked, check.run())
        assertEquals(listOf(true), guidance.asked)
        assertEquals(1, counts.reads)
    }

    @Test
    fun unreadableGuidanceChecksNothingAndWritesNothing() = runBlocking {
        guidance.unreadable = true

        assertEquals(ExistingUseCheck.NotNow, check.run())
        assertEquals(0, counts.reads)
        assertEquals(emptyList<Boolean>(), guidance.asked)
    }

    @Test
    fun anUnreadableHistoryIsNeverTakenAsEmpty() = runBlocking {
        counts.failing = true

        assertEquals(ExistingUseCheck.NotNow, check.run())
        assertEquals(emptyList<Boolean>(), guidance.asked)
        assertEquals(Guidance(), guidance.value)
    }

    @Test
    fun aDeathOrFailedWriteMidwayIsCheckedAgainAtTheNextStart() = runBlocking {
        counts.summary = BackupSummary(0, 2, 0, 0, 0)
        guidance.failWrite = true

        assertEquals(ExistingUseCheck.NotNow, check.run())
        assertEquals(Guidance(), guidance.value)

        // The next start: the history has been read again, and this time the answer lands.
        guidance.failWrite = false
        assertEquals(ExistingUseCheck.Checked, RememberExistingUse(counts, guidance).run())
        assertEquals(listOf(true, true), guidance.asked)
        assertEquals(true, guidance.value.existingUseChecked)
    }
}
