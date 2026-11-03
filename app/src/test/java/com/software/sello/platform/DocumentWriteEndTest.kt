package com.software.sello.platform

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.SyncFailedException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a document's write ends: which failures count, and that it always closes. */
class DocumentWriteEndTest {
    private val bytes = ByteArray(1_000) { (it % 251).toByte() }

    @Test
    fun aDocumentWrittenSyncedAndClosedIsComplete() {
        val document = HandDocument(isRealFile = true)

        document.writeAll(bytes)

        assertArrayEquals(bytes, document.received.toByteArray())
        assertTrue(document.closed)
    }

    @Test
    fun aFailedSyncOnARealFileIsAFailureAndTheDocumentIsStillClosed() {
        val document = HandDocument(isRealFile = true, syncFails = true)

        assertThrows(IOException::class.java) { document.writeAll(bytes) }
        assertTrue(document.closed)
    }

    @Test
    fun aPipeOrSocketThatCannotSyncStillCompletes() {
        val document = HandDocument(isRealFile = false, syncFails = true)

        document.writeAll(bytes)

        assertArrayEquals(bytes, document.received.toByteArray())
        assertTrue(document.closed)
    }

    @Test
    fun anErrorTheProviderReportsByTheCloseIsAFailure() {
        val document = HandDocument(isRealFile = false, reportsOnClose = "Could not upload")

        assertThrows(IOException::class.java) { document.writeAll(bytes) }
        assertTrue(document.closed)
    }

    @Test
    fun anErrorTheProviderReportsIsAFailureOnARealFileToo() {
        val document = HandDocument(isRealFile = true, reportsOnClose = "Could not commit")

        assertThrows(IOException::class.java) { document.writeAll(bytes) }
    }

    /**
     * A document whose provider's report, like a [android.os.ParcelFileDescriptor]'s, is
     * read when the document closes, so it is seen only when checked after the close.
     */
    private class HandDocument(
        override val isRealFile: Boolean,
        private val syncFails: Boolean = false,
        private val reportsOnClose: String? = null
    ) : OpenDocument {
        val received = ByteArrayOutputStream()
        var closed = false
        private var report: String? = null

        override val stream: OutputStream = object : FilterOutputStream(received) {
            override fun write(b: ByteArray, off: Int, len: Int) = received.write(b, off, len)

            override fun close() {
                super.close()
                closed = true
                report = reportsOnClose
            }
        }

        override fun sync() {
            if (syncFails) throw SyncFailedException("sync failed")
        }

        override fun checkError() {
            report?.let { throw IOException(it) }
        }
    }
}
