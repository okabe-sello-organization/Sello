package com.software.sello.domain.port

import com.software.sello.domain.model.Outcome

/** The two export formats: a table for spreadsheets, or everything for other programs. */
enum class ExportFormat { Csv, Json }

/**
 * The words a CSV export is written in: its column names and how each kind of movement
 * and income source is called. They come from the app's text, never from storage.
 */
data class ExportWords(
    val date: String,
    val kind: String,
    val name: String,
    val amount: String,
    val currency: String,
    val note: String,
    val expense: String,
    val income: String,
    val goalContribution: String,
    val goalWithdrawal: String,
    val investmentContribution: String,
    val investmentWithdrawal: String,
    val dividend: String,
    val investmentValue: String,
    /** By income source key (`salary`, `freelance`, …); `other` uses its own name. */
    val sources: Map<String, String>
)

/** A finished export: its bytes, the name to suggest and its MIME type. */
class PreparedExport(val bytes: ByteArray, val name: String, val mimeType: String)

sealed interface ExportFailure {
    /** The history could not be read: nothing is written, never an empty file. */
    data object StorageUnavailable : ExportFailure
}

/**
 * An analytical export of the whole history, from one revision. It is not a backup:
 * Sello cannot restore it, and its money is written for reading, not for import.
 */
interface DataExports {
    /** The file name to suggest before anything is read, in the financial zone's date. */
    suspend fun suggestedName(format: ExportFormat): String

    suspend fun prepare(
        format: ExportFormat,
        words: ExportWords
    ): Outcome<PreparedExport, ExportFailure>
}

/** An export placed where another app may read it, by its content address. */
data class SharedExport(val uri: String, val mimeType: String)

/**
 * Where an export waits while another app reads it: one private folder of Sello's own,
 * holding at most the last export shared, emptied again on the next start.
 */
interface ExportShares {
    /** [bytes] as [name], after every earlier one is removed; null if it cannot be written. */
    suspend fun place(bytes: ByteArray, name: String, mimeType: String): SharedExport?

    /** Removes every export placed for sharing. */
    suspend fun clear()
}
