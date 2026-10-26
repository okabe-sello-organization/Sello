package com.software.sello.feature.backup

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.DataExports
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.DocumentWrite
import com.software.sello.domain.port.DocumentWriter
import com.software.sello.domain.port.ExportFormat
import com.software.sello.domain.port.ExportShares
import com.software.sello.domain.port.ExportWords
import com.software.sello.domain.port.SharedExport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why an export did not reach its file or the other app. */
enum class ExportProblem {
    /** The history could not be read: nothing was written. */
    Storage,

    /** The chosen document could not be written or closed: it may be incomplete. */
    Write,

    /** The file to share could not be placed: nothing left the phone. */
    Share
}

sealed interface ExportProgress {
    data object Idle : ExportProgress

    data object Working : ExportProgress

    /** Written, flushed and closed as [name]. */
    data class Saved(val name: String) : ExportProgress

    /** Placed for sharing; the share sheet is to be opened with it. */
    data class ReadyToShare(val share: SharedExport) : ExportProgress

    data class Failed(val problem: ExportProblem) : ExportProgress
}

/**
 * One export at a time, for the life of the process, so leaving the screen does not cut
 * a file short. Nothing is recorded: an export is not a backup and leaves no trace in Sello.
 */
class ExportSession(
    private val exports: DataExports,
    private val writer: DocumentWriter,
    private val shares: ExportShares,
    private val scope: CoroutineScope
) {
    private val state = MutableStateFlow<ExportProgress>(ExportProgress.Idle)
    val progress: StateFlow<ExportProgress> = state.asStateFlow()

    suspend fun suggestedName(format: ExportFormat): String = exports.suggestedName(format)

    /** Writes [format] to [destination] unless an export is already running. */
    fun save(
        format: ExportFormat,
        words: ExportWords,
        destination: DocumentDestination,
        suggestedName: String
    ) = start {
        val prepared = when (val made = exports.prepare(format, words)) {
            is Outcome.Success -> made.value
            is Outcome.Failure -> return@start ExportProgress.Failed(ExportProblem.Storage)
        }
        when (val written = writer.write(destination, prepared.bytes)) {
            DocumentWrite.Failed -> ExportProgress.Failed(ExportProblem.Write)
            is DocumentWrite.Completed -> ExportProgress.Saved(written.name ?: suggestedName)
        }
    }

    /** Places [format] for sharing, the earlier one removed first. */
    fun share(format: ExportFormat, words: ExportWords) = start {
        shares.clear()
        val prepared = when (val made = exports.prepare(format, words)) {
            is Outcome.Success -> made.value
            is Outcome.Failure -> return@start ExportProgress.Failed(ExportProblem.Storage)
        }
        shares.place(prepared.bytes, prepared.name, prepared.mimeType)
            ?.let { ExportProgress.ReadyToShare(it) }
            ?: ExportProgress.Failed(ExportProblem.Share)
    }

    /** Forgets a finished outcome once it has been shown or the share sheet opened. */
    fun acknowledge() {
        state.update { if (it == ExportProgress.Working) it else ExportProgress.Idle }
    }

    private fun start(work: suspend () -> ExportProgress) {
        var started = false
        state.update { current ->
            if (current == ExportProgress.Working) {
                current
            } else {
                started = true
                ExportProgress.Working
            }
        }
        if (started) scope.launch { state.value = work() }
    }
}
