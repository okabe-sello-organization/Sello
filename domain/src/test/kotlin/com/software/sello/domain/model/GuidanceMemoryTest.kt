package com.software.sello.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** How a restored backup's guidance meets the phone's (SELLO-205). */
class GuidanceMemoryTest {
    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    @Test
    fun `a nudge counts as seen if the phone or the backup says so`() {
        val phone = GuidanceMemory(seen = setOf(Nudge.GuideHint, Nudge.Carry))
        val backup = GuidanceMemory(seen = setOf(Nudge.Carry, Nudge.Deleting))

        assertEquals(
            setOf(Nudge.GuideHint, Nudge.Carry, Nudge.Deleting),
            phone.mergedWith(backup).seen
        )
    }

    @Test
    fun `No volver a mostrar holds if either says so`() {
        val on = GuidanceMemory(backupReminderOff = true)
        val off = GuidanceMemory(backupReminderOff = false)

        assertEquals(true, on.mergedWith(off).backupReminderOff)
        assertEquals(true, off.mergedWith(on).backupReminderOff)
        assertEquals(false, off.mergedWith(off).backupReminderOff)
    }

    @Test
    fun `the later backup-reminder day wins, whichever side has it`() {
        val earlier = GuidanceMemory(backupReminderShown = day(10, 1))
        val later = GuidanceMemory(backupReminderShown = day(11, 1))

        assertEquals(day(11, 1), earlier.mergedWith(later).backupReminderShown)
        assertEquals(day(11, 1), later.mergedWith(earlier).backupReminderShown)
        assertEquals(day(10, 1), earlier.mergedWith(GuidanceMemory()).backupReminderShown)
        assertEquals(day(10, 1), GuidanceMemory().mergedWith(earlier).backupReminderShown)
    }

    @Test
    fun `the later last nudge wins with its nudge, and on the same day the phone's stays`() {
        val phone = GuidanceMemory(lastShown = ShownNudge(Nudge.GuideHint, day(11, 5)))
        val backupLater = GuidanceMemory(lastShown = ShownNudge(Nudge.Deleting, day(11, 6)))
        val backupEarlier = GuidanceMemory(lastShown = ShownNudge(Nudge.Deleting, day(11, 4)))
        val backupSameDay = GuidanceMemory(lastShown = ShownNudge(Nudge.Deleting, day(11, 5)))

        assertEquals(backupLater.lastShown, phone.mergedWith(backupLater).lastShown)
        assertEquals(phone.lastShown, phone.mergedWith(backupEarlier).lastShown)
        assertEquals(phone.lastShown, phone.mergedWith(backupSameDay).lastShown)
        assertEquals(backupLater.lastShown, GuidanceMemory().mergedWith(backupLater).lastShown)
        assertEquals(phone.lastShown, phone.mergedWith(GuidanceMemory()).lastShown)
    }

    @Test
    fun `a backup with nothing seen changes nothing on the phone`() {
        val phone = GuidanceMemory(
            seen = setOf(Nudge.Investments),
            lastShown = ShownNudge(Nudge.Investments, day(11, 2)),
            backupReminderShown = day(10, 30),
            backupReminderOff = true
        )

        assertEquals(phone, phone.mergedWith(GuidanceMemory()))
    }

    @Test
    fun `only the Guía hint and the backup reminder are for everyone`() {
        assertEquals(
            listOf(Nudge.GuideHint, Nudge.BackupReminder),
            Nudge.entries.filterNot { it.forNewUsers }
        )
    }
}
