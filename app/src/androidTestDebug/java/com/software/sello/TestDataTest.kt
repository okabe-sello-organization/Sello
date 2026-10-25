package com.software.sello

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reset every journey starts and ends from leaves nothing behind, schedules and
 * their pauses included: one left by a test would be recorded into the next test's
 * history.
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

        TestData.reset()

        assertEquals(
            listOf(0L, 0L, 0L),
            listOf("schedule_pause", "schedule", "category").map(TestData::count)
        )
    }
}
