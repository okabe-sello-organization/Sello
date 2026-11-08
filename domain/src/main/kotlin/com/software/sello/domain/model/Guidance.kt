package com.software.sello.domain.model

import java.time.LocalDate

/**
 * One quiet line of guidance in a screen (E38). [forNewUsers]: shown only to people who
 * started with this version; people already using Sello when it landed see only the Guía
 * hint and the backup reminder.
 */
enum class Nudge(val forNewUsers: Boolean) {
    GuideHint(forNewUsers = false),
    BackupReminder(forNewUsers = false),
    BulkEntry(forNewUsers = true),
    FixedPayment(forNewUsers = true),
    Carry(forNewUsers = true),
    Deleting(forNewUsers = true),
    Investments(forNewUsers = true),
    ReciboGoal(forNewUsers = true)
}

/** [nudge] was the last one shown, on [day]. */
data class ShownNudge(val nudge: Nudge, val day: LocalDate)

/**
 * What guidance a person has seen, as a backup carries it (version 11 on). A nudge in
 * [seen] was seen and closed for good. [lastShown] is the last nudge shown and its day,
 * for the one-a-day rule. [backupReminderShown] is the day the backup reminder last
 * showed; [backupReminderOff] is its "No volver a mostrar".
 */
data class GuidanceMemory(
    val seen: Set<Nudge> = emptySet(),
    val lastShown: ShownNudge? = null,
    val backupReminderShown: LocalDate? = null,
    val backupReminderOff: Boolean = false
) {
    /**
     * This phone's memory with a restored backup's: a nudge counts as seen if either says
     * so, "No volver a mostrar" holds if either says so, and the later of each pair of days
     * wins. On the same day the phone's last nudge stays. Merging never forgets anything.
     */
    fun mergedWith(backup: GuidanceMemory): GuidanceMemory = GuidanceMemory(
        seen = seen + backup.seen,
        lastShown = when {
            lastShown == null -> backup.lastShown
            backup.lastShown == null -> lastShown
            backup.lastShown.day > lastShown.day -> backup.lastShown
            else -> lastShown
        },
        backupReminderShown = listOfNotNull(backupReminderShown, backup.backupReminderShown)
            .maxOrNull(),
        backupReminderOff = backupReminderOff || backup.backupReminderOff
    )
}

/**
 * The phone's guidance: its [memory], and whether Sello has checked yet if the person was
 * already using it when this version landed. Until [existingUseChecked], no new-user nudge
 * may show: it could be someone who already knows the app.
 */
data class Guidance(
    val memory: GuidanceMemory = GuidanceMemory(),
    val existingUseChecked: Boolean = false
)
