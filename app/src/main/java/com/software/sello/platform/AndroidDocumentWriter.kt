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
                DescriptorDocument(descriptor).writeAll(bytes)
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

    /** Whether the document is a real file, not a pipe or socket to its provider. */
    val isRealFile: Boolean

    /** Asks the storage to keep what was written; throws when it can't. */
    fun sync()

    /** Throws when the provider reported a failure on its side of the document. */
    fun checkError()
}

/**
 * Writes every byte of [bytes], flushes, syncs and closes the document, then asks
 * whether the provider reported a failure; throws [IOException] at the first failure.
 * The document is closed whatever happens.
 */
internal fun OpenDocument.writeAll(bytes: ByteArray) {
    stream.use {
        it.write(bytes)
        it.flush()
        try {
            sync()
        } catch (failure: IOException) {
            // A pipe or socket to a provider cannot sync, and that is no failure. A real
            // file that cannot sync may not keep what was written.
            if (isRealFile) throw failure
        }
    }
    // A provider reports its own failure, such as an upload it could not finish, on its
    // side of the document. The close reads that report, so it is checked only now.
    checkError()
}

private class DescriptorDocument(private val descriptor: ParcelFileDescriptor) : OpenDocument {
    private val file = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)

    // Asked while open: the close ends the channel a provider reports through.
    private val canReport = descriptor.canDetectErrors()

    override val stream: OutputStream get() = file

    override val isRealFile: Boolean
        get() = try {
            OsConstants.S_ISREG(Os.fstat(descriptor.fileDescriptor).st_mode)
        } catch (_: ErrnoException) {
            // Nothing is known about it, so a failed sync is not excused.
            true
        }

    override fun sync() = file.fd.sync()

    override fun checkError() {
        if (canReport) descriptor.checkError()
    }
}
