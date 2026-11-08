package com.software.sello.domain.policy

import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.port.FinancialDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One nudge at a time, one a day, and the backup reminder's month (SELLO-211). */
class NudgePolicyTest {
    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)
    private val bogota = ZoneId.of("America/Bogota")
    private val utc = ZoneId.of("UTC")

    private fun checked(memory: GuidanceMemory = GuidanceMemory()) =
        Guidance(memory, existingUseChecked = true)

    @Test
    fun `the first open nudge of the screen shows, one at a time`() {
        val wanted = listOf(Nudge.BackupReminder, Nudge.GuideHint)

        assertEquals(Nudge.BackupReminder, NudgePolicy.pick(checked(), day(11, 8), wanted))
    }

    @Test
    fun `a nudge seen never shows again and the next one takes its place`() {
        val guidance = checked(GuidanceMemory(seen = setOf(Nudge.BackupReminder)))

        assertEquals(
            Nudge.GuideHint,
            NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.BackupReminder, Nudge.GuideHint))
        )
        assertNull(NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.BackupReminder)))
    }

    @Test
    fun `nothing is wanted, nothing shows`() {
        assertNull(NudgePolicy.pick(checked(), day(11, 8), emptyList()))
    }

    @Test
    fun `a day belongs to the nudge shown on it, here or on another screen`() {
        val guidance = checked(GuidanceMemory(lastShown = ShownNudge(Nudge.Carry, day(11, 8))))

        assertNull(NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.GuideHint)))
        assertEquals(Nudge.Carry, NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.Carry)))
    }

    @Test
    fun `the nudge of the day keeps it over one placed first`() {
        val guidance =
            checked(GuidanceMemory(lastShown = ShownNudge(Nudge.GuideHint, day(11, 8))))

        assertEquals(
            Nudge.GuideHint,
            NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.BackupReminder, Nudge.GuideHint))
        )
    }

    @Test
    fun `a nudge closed today leaves the rest of the day without one`() {
        val guidance = checked(
            GuidanceMemory(
                seen = setOf(Nudge.GuideHint),
                lastShown = ShownNudge(Nudge.GuideHint, day(11, 8))
            )
        )

        assertNull(
            NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.BackupReminder, Nudge.GuideHint))
        )
    }

    @Test
    fun `after midnight another nudge may show`() {
        val guidance = checked(GuidanceMemory(lastShown = ShownNudge(Nudge.Carry, day(11, 8))))

        assertEquals(
            Nudge.GuideHint,
            NudgePolicy.pick(guidance, day(11, 9), listOf(Nudge.GuideHint))
        )
    }

    @Test
    fun `a zone change back to the day before still leaves that day to its nudge`() {
        // Shown on 9 November in one zone; travelling west the date is 8 November again.
        val guidance = checked(GuidanceMemory(lastShown = ShownNudge(Nudge.Carry, day(11, 9))))

        assertNull(NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.GuideHint)))
        assertEquals(Nudge.Carry, NudgePolicy.pick(guidance, day(11, 8), listOf(Nudge.Carry)))
        assertEquals(
            Nudge.GuideHint,
            NudgePolicy.pick(guidance, day(11, 10), listOf(Nudge.GuideHint))
        )
    }

    @Test
    fun `new-user nudges wait until existing use was checked, the hint and reminder do not`() {
        val unchecked = Guidance(GuidanceMemory(), existingUseChecked = false)

        assertNull(NudgePolicy.pick(unchecked, day(11, 8), listOf(Nudge.BulkEntry)))
        assertEquals(
            Nudge.GuideHint,
            NudgePolicy.pick(unchecked, day(11, 8), listOf(Nudge.BulkEntry, Nudge.GuideHint))
        )
    }

    // The backup reminder.

    private fun today(month: Int, dayOfMonth: Int, zone: ZoneId = bogota) =
        FinancialDay(day(month, dayOfMonth), zone)

    /** 9 October, 23:30 in Bogotá: already 10 October in UTC. */
    private val lateBackup = Instant.parse("2026-10-10T04:30:00Z")

    @Test
    fun `no movement, no reminder`() {
        assertNull(BackupReminderPolicy.due(today(11, 8), null, null, GuidanceMemory()))
    }

    @Test
    fun `due 30 days after the last backup, counted from midnight in the zone`() {
        val memory = GuidanceMemory()

        assertNull(BackupReminderPolicy.due(today(11, 7), lateBackup, day(1, 5), memory))
        assertEquals(
            BackupReminder(daysSinceBackup = 30),
            BackupReminderPolicy.due(today(11, 8), lateBackup, day(1, 5), memory)
        )
    }

    @Test
    fun `in another zone the same backup falls on another day`() {
        // In UTC the backup was on 10 October: 8 November is only 29 days later.
        assertNull(
            BackupReminderPolicy.due(today(11, 8, utc), lateBackup, day(1, 5), GuidanceMemory())
        )
        assertEquals(
            BackupReminder(daysSinceBackup = 30),
            BackupReminderPolicy.due(today(11, 9, utc), lateBackup, day(1, 5), GuidanceMemory())
        )
    }

    @Test
    fun `with no backup ever it counts from the first movement`() {
        assertNull(BackupReminderPolicy.due(today(11, 7), null, day(10, 9), GuidanceMemory()))
        assertEquals(
            BackupReminder(daysSinceBackup = null),
            BackupReminderPolicy.due(today(11, 8), null, day(10, 9), GuidanceMemory())
        )
    }

    @Test
    fun `it stays the day it showed, then waits 30 days`() {
        val memory = GuidanceMemory(backupReminderShown = day(11, 8))
        val first = day(1, 5)

        assertEquals(
            BackupReminder(daysSinceBackup = null),
            BackupReminderPolicy.due(today(11, 8), null, first, memory)
        )
        assertNull(BackupReminderPolicy.due(today(11, 9), null, first, memory))
        assertNull(BackupReminderPolicy.due(today(12, 7), null, first, memory))
        assertEquals(
            BackupReminder(daysSinceBackup = null),
            BackupReminderPolicy.due(today(12, 8), null, first, memory)
        )
    }

    @Test
    fun `a backup made clears it at once`() {
        val memory = GuidanceMemory(backupReminderShown = day(11, 8))
        val now = Instant.parse("2026-11-08T15:00:00Z")

        assertNull(BackupReminderPolicy.due(today(11, 8), now, day(1, 5), memory))
    }

    @Test
    fun `No volver a mostrar ends it for good`() {
        val memory = GuidanceMemory(backupReminderOff = true)

        assertNull(BackupReminderPolicy.due(today(11, 8), null, day(1, 5), memory))
    }

    @Test
    fun `a backup dated after today never counts as overdue`() {
        val later = Instant.parse("2026-12-01T12:00:00Z")

        assertNull(BackupReminderPolicy.due(today(11, 8), later, day(1, 5), GuidanceMemory()))
    }
}
