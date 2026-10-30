package com.software.sello

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.CorrectionAnnouncementPreference
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.port.CorrectionAnnouncementPreferences
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reset every journey starts and ends from leaves nothing behind, schedules and
 * their pauses included: one left by a test would be recorded into the next test's
 * history. It reaches every table, the preferences too, and the screens hear of it.
 */
@RunWith(AndroidJUnit4::class)
class TestDataTest {
    @After
    fun clean() = TestData.reset()

    @Test
    fun aResetLeavesNoScheduleOrPauseBehind() {
        TestData.reset()
        val category = "00000000-0000-4000-8000-000000000100"
        val schedule = "00000000-0000-4000-8000-000000000300"
        TestData.execute(
            "INSERT INTO category (id, name, name_key, icon, archived, version, created_at, " +
                "updated_at) VALUES ('$category', 'Hogar', 'hogar', 'home', 0, 1, 0, 0)"
        )
        TestData.execute(
            "INSERT INTO schedule (id, kind, category_id, amount_minor, currency, rule, " +
                "rule_first, start_date, sequence, version, created_at, updated_at) VALUES " +
                "('$schedule', 'payment', '$category', 950000, 'COP', 'monthly', 5, " +
                "'2026-10-05', 0, 1, 0, 0)"
        )
        TestData.execute(
            "INSERT INTO schedule_pause (schedule_id, from_date) VALUES ('$schedule', '2026-10-07')"
        )
        TestData.execute(
            "INSERT INTO carry_chain (category_id, start_month) VALUES ('$category', '2026-10')"
        )

        TestData.reset()

        assertEquals(
            listOf(0L, 0L, 0L, 0L),
            listOf("schedule_pause", "schedule", "carry_chain", "category").map(TestData::count)
        )
    }

    @Test
    fun aResetEmptiesEveryTableButTheProfileWhateverTheHistoryHeld() {
        TestData.reset()
        TestCommands.createCategory("Hogar", 900_000)
        TestCommands.createExpense("Hogar", TestCommands.today, 48_700, "Pan")
        TestCommands.createIncome(TestCommands.today, 4_200_000)
        TestCommands.recordGoalMovement(TestCommands.createGoal("Viaje", 2_000_000), 150_000)
        TestCommands.createInvestment("CDT", 1_000_000)
        // What an undo would put back, which no command leaves for long.
        TestData.execute(
            "INSERT INTO deleted_expense VALUES ('00000000-0000-4000-8000-000000000001', 1, 0, " +
                "'00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000003', " +
                "'2026-10-03', 1, 48700, 'COP', NULL, 1, 0, 0, NULL, NULL, NULL)"
        )

        TestData.reset()

        val tables = TestData.tables()
        assertEquals(
            emptyMap<String, Long>(),
            (tables - "profile").associateWith(TestData::count).filterValues { it > 0 }
        )
        assertEquals(listOf("0"), TestData.rows("SELECT revision FROM profile"))
        // The tables are read from the schema, not from a list kept by hand.
        assertEquals(
            true,
            tables.containsAll(listOf("carry_chain", "deleted_expense", "operation_receipt"))
        )
    }

    @Test
    fun whatTheScreensObserveHearsOfAReset() = runBlocking {
        TestData.reset()
        TestCommands.createCategory("Hogar", 900_000)
        // One observer the whole time, as a screen keeps it: it hears changes or nothing.
        val shown = MutableStateFlow<ReadState.Ready<MonthlySnapshot>?>(null)
        val watching = launch(Dispatchers.Default) {
            TestCommands.koin.get<MonthlySnapshots>()
                .observe(YearMonth.from(TestCommands.today))
                .filterIsInstance<ReadState.Ready<MonthlySnapshot>>()
                .collect { shown.value = it }
        }
        withTimeout(PATIENCE) { shown.first { it?.value?.categories?.size == 1 } }

        TestData.reset()

        // Written behind Room's back, the month would keep showing Hogar.
        withTimeout(PATIENCE) { shown.first { it?.value?.categories?.isEmpty() == true } }
        watching.cancel()
    }

    @Test
    fun aResetPutsThePreferencesBackToAFirstRun() = runBlocking {
        val announcements = TestCommands.koin.get<CorrectionAnnouncementPreferences>()
        assertEquals(PreferenceWrite.Saved, announcements.dismissPastExpenseAnnouncement())

        TestData.reset()

        assertEquals(
            PreferenceRead.Ready(CorrectionAnnouncementPreference()),
            announcements.observeCorrectionAnnouncements().first()
        )
    }

    private companion object {
        const val PATIENCE = 10_000L
    }
}
