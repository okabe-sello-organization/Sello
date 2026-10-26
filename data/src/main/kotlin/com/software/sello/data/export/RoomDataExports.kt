package com.software.sello.data.export

import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.DataExports
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.ExportFailure
import com.software.sello.domain.port.ExportFormat
import com.software.sello.domain.port.ExportWords
import com.software.sello.domain.port.PreparedExport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.withContext

/**
 * Exports the whole history from one read, so it all belongs to one revision. Nothing is
 * changed; a failed read is a failure, never an empty file.
 */
class RoomDataExports(
    private val snapshots: RoomBackupSnapshots,
    private val auditClock: AuditClock,
    private val dispatchers: DispatcherProvider
) : DataExports {
    override suspend fun suggestedName(format: ExportFormat): String =
        fileName(format, auditClock.now(), (snapshots.zone() as? Outcome.Success)?.value)

    override suspend fun prepare(
        format: ExportFormat,
        words: ExportWords
    ): Outcome<PreparedExport, ExportFailure> {
        val snapshot = when (val read = snapshots.read()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return Outcome.Failure(ExportFailure.StorageUnavailable)
        }
        val at = auditClock.now().truncatedTo(ChronoUnit.MILLIS)
        return withContext(dispatchers.default) {
            val bytes = when (format) {
                ExportFormat.Csv -> CsvExport.encode(movements(snapshot), words)
                ExportFormat.Json -> JsonExport.encode(snapshot, at)
            }
            Outcome.Success(PreparedExport(bytes, fileName(format, at, snapshot.zone), mime(format)))
        }
    }

    companion object {
        private val day = DateTimeFormatter.ofPattern("uuuu-MM-dd")

        /** `sello-datos-2026-10-26.csv`, the day in [zone]; without a zone, a plain name. */
        fun fileName(format: ExportFormat, at: Instant, zone: ZoneId?): String {
            val extension = if (format == ExportFormat.Csv) "csv" else "json"
            return zone?.let { "sello-datos-${day.format(at.atZone(it))}.$extension" }
                ?: "sello-datos.$extension"
        }

        fun mime(format: ExportFormat): String =
            if (format == ExportFormat.Csv) CsvExport.MIME_TYPE else JsonExport.MIME_TYPE
    }
}
