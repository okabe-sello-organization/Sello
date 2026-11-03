package com.software.sello.platform

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.DocumentWrite
import com.software.sello.domain.port.DocumentWriter
import java.io.IOException
import java.io.OutputStream
import java.io.SyncFailedException
import kotlinx.coroutines.withContext

/**
 * Writes to a document the system picker created, through its provider. Success means
 * every byte was written, flushed, synced where the provider allows, and the document
 * closed without error; anything less is [DocumentWrite.Failed]. A failed document is
 * left as it is: it belongs to the provider and to the person, not to Sello.
 */
class AndroidDocumentWriter(
    private val resolver: ContentResolver,
    private val dispatchers: DispatcherProvider
) : DocumentWriter {
    override suspend fun write(destination: DocumentDestination, bytes: ByteArray): DocumentWrite =
        withContext(dispatchers.io) {
            val uri = providerDocument(destination.id) ?: return@withContext DocumentWrite.Failed
            try {
                // "wt" truncates, so an existing document chosen again is replaced, not mixed.
                val descriptor = resolver.openFileDescriptor(uri, "wt")
                    ?: return@withContext DocumentWrite.Failed
                DescriptorDocument(descriptor).writeAll(bytes, cannotSync)
                DocumentWrite.Completed(displayName(uri), bytes.size.toLong())
            } catch (_: IOException) {
                DocumentWrite.Failed
            } catch (_: SecurityException) {
                DocumentWrite.Failed
            } catch (_: IllegalArgumentException) {
                DocumentWrite.Failed
            } catch (_: IllegalStateException) {
                DocumentWrite.Failed
            }
        }

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
        }
    } catch (_: RuntimeException) {
        null
    }
}

/** An open document as the writer sees it, apart from the provider that opened it. */
internal interface OpenDocument {
    /** Takes the bytes; closing it closes the document. */
    val stream: OutputStream

    /** Asks the storage to keep what was written: null when it did, else the error number. */
    fun sync(): Int?

    /** Throws when the provider reported a failure before the close. */
    fun checkError()
}

/**
 * Writes every byte of [bytes], flushes, syncs and closes the document, then asks
 * whether the provider reported a failure; throws [IOException] at the first failure.
 * A sync that fails with one of [cannotSync] is excused: those numbers mean the
 * document cannot be synced at all, not that it lost what was written. The document is
 * closed whatever happens.
 */
internal fun OpenDocument.writeAll(bytes: ByteArray, cannotSync: Set<Int>) {
    stream.use {
        it.write(bytes)
        it.flush()
        val error = sync()
        if (error != null && error !in cannotSync) {
            throw SyncFailedException("The storage could not keep the document: error $error")
        }
    }
    // A failure the provider reported before the close, such as an upload it refused
    // partway, waits on its side of the document. The close reads that report without
    // waiting, so it is checked only now; a failure reported after the close is not seen.
    checkError()
}

/**
 * The error numbers a sync gives when the document cannot be synced at all: a pipe or
 * socket to its provider, a provider's proxy file that does not sync, or a file system
 * without sync (EOPNOTSUPP, which on Linux is also ENOTSUP). Any other number, such as a
 * failed disk, a full one or a closed descriptor, means what was written may be lost.
 */
private val cannotSync: Set<Int>
    get() = setOf(OsConstants.EINVAL, OsConstants.EROFS, OsConstants.EOPNOTSUPP)

private class DescriptorDocument(private val descriptor: ParcelFileDescriptor) : OpenDocument {
    private val file = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)

    // Asked while open: the close ends the channel a provider reports through.
    private val canReport = descriptor.canDetectErrors()

    override val stream: OutputStream get() = file

    override fun sync(): Int? = try {
        Os.fsync(descriptor.fileDescriptor)
        null
    } catch (failure: ErrnoException) {
        failure.errno
    }

    override fun checkError() {
        if (canReport) descriptor.checkError()
    }
}
