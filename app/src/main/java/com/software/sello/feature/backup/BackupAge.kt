package com.software.sello.feature.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How old the last backup is, for the stamp on Configuración's backup card. */
sealed interface BackupAge {
    /** No backup was ever made: Sin respaldo. */
    data object None : BackupAge

    /** Made [days] calendar days ago, at most [BACKUP_STALE_AFTER_DAYS]: Guardado. */
    data class Recent(val days: Long) : BackupAge

    /** Made more than [BACKUP_STALE_AFTER_DAYS] days ago: Pendiente. */
    data class Old(val days: Long) : BackupAge
}

/** A backup older than this many days asks for a new one (owner decision, SELLO-E20). */
const val BACKUP_STALE_AFTER_DAYS = 30L

/**
 * Days between the day [at] fell on in [zone] and [today], the financial day. A backup
 * dated after today, as when the financial clock is behind, counts as made today.
 */
fun backupAge(at: Instant?, zone: ZoneId, today: LocalDate): BackupAge {
    if (at == null) return BackupAge.None
    val days = ChronoUnit.DAYS.between(at.atZone(zone).toLocalDate(), today).coerceAtLeast(0)
    return if (days > BACKUP_STALE_AFTER_DAYS) BackupAge.Old(days) else BackupAge.Recent(days)
}
