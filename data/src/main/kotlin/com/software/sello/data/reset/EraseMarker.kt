package com.software.sello.data.reset

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.repository.reading
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import kotlinx.coroutines.withContext

/**
 * The files in an erase's private folder, read and written by the erase and, through
 * [preferencesFirst], by a restore about to replace the history: `pending.properties`
 * names the erase's operation and whether the preferences are back to first-run, and
 * `compact.pending` says a compaction is still owed. Neither holds anything of the
 * erased history. Callers that change them hold the storage's recovery turn.
 */
internal class EraseMarker(
    private val database: SelloDatabase,
    private val folder: File,
    private val dispatchers: DispatcherProvider
) {
    private val marker = File(folder, "pending.properties")
    private val compaction = File(folder, "compact.pending")

    data class Marker(val operation: OperationId, val preferencesDone: Boolean)

    /** Where an erase stands against the history now. */
    enum class Place {
        /** It committed and the history is still the one it made. */
        Current,

        /** It committed, and a restore has replaced the history it made since. */
        Replaced,
        NeverCommitted,

        /** Its receipt or the profile could not be read. */
        Unknown
    }

    /**
     * Before a restore replaces the history: an erase that committed and has not put the
     * preferences back to first-run does it now. False when that cannot be done or it
     * cannot be told whether an erase committed: the restore then waits.
     */
    suspend fun preferencesFirst(preferences: PreferenceReset): Boolean {
        val marker = read() ?: return true
        return when (place(marker.operation)) {
            Place.NeverCommitted -> true

            Place.Unknown -> false

            Place.Current, Place.Replaced ->
                marker.preferencesDone || (
                    preferences.resetAll() == PreferenceWrite.Saved &&
                        write(marker.copy(preferencesDone = true))
                    )
        }
    }

    /** Read together: [operation]'s receipt and the generation the history is at now. */
    suspend fun place(operation: OperationId): Place = when (
        val read = reading {
            database.withTransaction {
                val receipt = database.operationReceiptDao().find(operation.value)
                    ?: return@withTransaction Outcome.Success(Place.NeverCommitted)
                when (val profile = profileFrom(database.profileDao().rows())) {
                    is Outcome.Failure -> profile

                    is Outcome.Success -> Outcome.Success(
                        if (profile.value.generation == receipt.generation) {
                            Place.Current
                        } else {
                            Place.Replaced
                        }
                    )
                }
            }
        }
    ) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> Place.Unknown
    }

    suspend fun read(): Marker? = io {
        if (!marker.exists()) return@io null
        val properties = try {
            Properties().apply { marker.inputStream().use(::load) }
        } catch (_: IOException) {
            return@io null
        }
        val operation = OperationId.of(properties.getProperty(OPERATION).orEmpty())
            as? Outcome.Success ?: return@io null
        Marker(operation.value, properties.getProperty(PREFERENCES) == DONE)
    }

    suspend fun write(value: Marker): Boolean {
        val properties = Properties().apply {
            setProperty(OPERATION, value.operation.value)
            if (value.preferencesDone) setProperty(PREFERENCES, DONE)
        }
        val bytes = ByteArrayOutputStream().also { properties.store(it, null) }.toByteArray()
        return writeAtomically(marker, bytes)
    }

    suspend fun forget() {
        io { marker.delete() }
    }

    suspend fun compactionOwed(): Boolean = io { compaction.exists() }

    /** True once `compact.pending` is there. */
    suspend fun oweCompaction(): Boolean =
        compactionOwed() || writeAtomically(compaction, ByteArray(0))

    suspend fun compacted() {
        io { compaction.delete() }
    }

    private suspend fun writeAtomically(target: File, bytes: ByteArray): Boolean = io {
        folder.mkdirs()
        try {
            val temporary = File(folder, "${target.name}.tmp")
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            temporary.renameTo(target)
        } catch (_: IOException) {
            false
        }
    }

    /** File work off the main thread. */
    private suspend fun <T> io(block: () -> T): T = withContext(dispatchers.io) { block() }

    private companion object {
        const val OPERATION = "operation"
        const val PREFERENCES = "preferences"
        const val DONE = "done"
    }
}
