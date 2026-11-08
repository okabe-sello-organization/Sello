package com.software.sello.domain.policy

import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.port.FinancialDay
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Which nudge a screen shows (E38): one at a time on a screen and at most one a day across
 * the app. Every screen with nudges asks it, with the nudges whose own trigger holds there.
 */
object NudgePolicy {
    /**
     * The one of [wanted] to show on [today], or null. [wanted] are the screen's nudges
     * whose trigger holds now, the most important first. A nudge seen (done or closed with
     * its ✕) never shows again, and a new-user nudge waits until Sello has checked whether
     * the person was already using it. Once a nudge showed on a day, that day belongs to it:
     * it may show again, and no other one does. A last day after [today], which a time-zone
     * change or a clock set back can leave, still belongs to its nudge until it has passed.
     */
    fun pick(guidance: Guidance, today: LocalDate, wanted: List<Nudge>): Nudge? {
        val open = wanted.filter { nudge ->
            nudge !in guidance.memory.seen &&
                (!nudge.forNewUsers || guidance.existingUseChecked)
        }
        val last = guidance.memory.lastShown
        if (last != null && last.day >= today) return last.nudge.takeIf { it in open }
        return open.firstOrNull()
    }
}

/**
 * The backup reminder's facts: [daysSinceBackup] days since the last backup, on the
 * financial days, or null when no backup was ever made.
 */
data class BackupReminder(val daysSinceBackup: Long?)

/**
 * When the backup reminder is due (SELLO-211). It is about once a month, quiet, and it
 * never guesses: the caller asks only with readings that worked.
 */
object BackupReminderPolicy {
    /** Days without a backup before it shows, and days it then waits after showing. */
    const val INTERVAL_DAYS = 30L

    /**
     * The reminder for [today], or null when it is not due. It needs a movement, so
     * [firstMovement] is the earliest expense or income. It is due once [INTERVAL_DAYS]
     * passed since the last backup, [lastBackupAt] read as a day in [today]'s zone, or,
     * with no backup ever, since [firstMovement]. Once shown it stays for that day, then
     * waits [INTERVAL_DAYS] days; a backup made since clears it at once, and "No volver a
     * mostrar" ends it for good.
     */
    fun due(
        today: FinancialDay,
        lastBackupAt: Instant?,
        firstMovement: LocalDate?,
        memory: GuidanceMemory
    ): BackupReminder? {
        if (memory.backupReminderOff || firstMovement == null) return null
        val date = today.date
        val backupDay = lastBackupAt?.atZone(today.zone)?.toLocalDate()
        val since = ChronoUnit.DAYS.between(backupDay ?: firstMovement, date)
        if (since < INTERVAL_DAYS) return null
        val shown = memory.backupReminderShown
        if (shown != null && shown != date && ChronoUnit.DAYS.between(shown, date) < INTERVAL_DAYS) {
            return null
        }
        return BackupReminder(daysSinceBackup = since.takeIf { backupDay != null })
    }
}
