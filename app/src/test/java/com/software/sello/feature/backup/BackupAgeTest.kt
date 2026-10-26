package com.software.sello.feature.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stamp rule: Guardado up to 30 days, Pendiente after, Sin respaldo with none. */
class BackupAgeTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val today = LocalDate.parse("2026-10-25")

    private fun age(at: String?) = backupAge(at?.let(Instant::parse), bogota, today)

    @Test
    fun noBackupIsNone() {
        assertEquals(BackupAge.None, age(null))
    }

    @Test
    fun todayYesterdayAndThirtyDaysAreRecent() {
        assertEquals(BackupAge.Recent(0), age("2026-10-25T15:00:00Z"))
        assertEquals(BackupAge.Recent(1), age("2026-10-24T15:00:00Z"))
        assertEquals(BackupAge.Recent(30), age("2026-09-25T15:00:00Z"))
    }

    @Test
    fun thirtyOneDaysIsOld() {
        assertEquals(BackupAge.Old(31), age("2026-09-24T15:00:00Z"))
        assertEquals(BackupAge.Old(84), age("2026-08-02T15:00:00Z"))
    }

    @Test
    fun daysAreCountedInTheFinancialZoneNotInUtc() {
        // 22:30 on 25 October in Bogotá is already 26 October in UTC.
        assertEquals(BackupAge.Recent(0), age("2026-10-26T03:30:00Z"))
        // 23:30 on 24 September in Bogotá: 31 days, though UTC says 25 September.
        assertEquals(BackupAge.Old(31), age("2026-09-25T04:30:00Z"))
    }

    @Test
    fun aBackupAfterTodayCountsAsToday() {
        assertEquals(BackupAge.Recent(0), age("2026-10-27T15:00:00Z"))
    }
}
