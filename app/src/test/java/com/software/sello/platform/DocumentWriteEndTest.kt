package com.software.sello.platform

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a document's write ends: which failures count, and that it always closes. */
class DocumentWriteEndTest {
    private val bytes = ByteArray(1_000) { (it % 251).toByte() }

    @Test
    fun aDocumentWrittenSyncedAndClosedIsComplete() {
        val document = HandDocument()

        document.writeAll(bytes, CANNOT_SYNC)

        assertArrayEquals(bytes, document.received.toByteArray())
        assertTrue(document.closed)
    }

    @Test
    fun aPipeOrSocketThatCannotSyncStillCompletes() {
        val document = HandDocument(syncError = EINVAL)

        document.writeAll(bytes, CANNOT_SYNC)

        assertArrayEquals(bytes, document.received.toByteArray())
        assertTrue(document.closed)
    }

    @Test
    fun aProvidersProxyFileThatCannotSyncStillCompletes() {
        // A proxy file looks like a regular file, yet its provider may not sync it.
        val document = HandDocument(syncError = EINVAL)

        document.writeAll(bytes, CANNOT_SYNC)

        assertArrayEquals(bytes, document.received.toByteArray())
    }

    @Test
    fun aDocumentOnStorageThatCannotSyncStillCompletes() {
        val document = HandDocument(syncError = EROFS)

        document.writeAll(bytes, CANNOT_SYNC)

        assertTrue(document.closed)
    }

    @Test
    fun aSyncThatFailsOnTheDiskIsAFailureAndTheDocumentIsStillClosed() {
        val document = HandDocument(syncError = EIO)

        assertThrows(IOException::class.java) { document.writeAll(bytes, CANNOT_SYNC) }
        assertTrue(document.closed)
    }

    @Test
    fun aSyncThatFindsTheStorageFullIsAFailure() {
        listOf(ENOSPC, EDQUOT).forEach { error ->
            val document = HandDocument(syncError = error)

            assertThrows(IOException::class.java) { document.writeAll(bytes, CANNOT_SYNC) }
            assertTrue(document.closed)
        }
    }

    @Test
    fun anErrorTheProviderReportsByTheCloseIsAFailure() {
        val document = HandDocument(syncError = EINVAL, reportsOnClose = "Could not upload")

        assertThrows(IOException::class.java) { document.writeAll(bytes, CANNOT_SYNC) }
        assertTrue(document.closed)
    }

    @Test
    fun anErrorTheProviderReportsIsAFailureWhenTheSyncWorkedToo() {
        val document = HandDocument(reportsOnClose = "Could not commit")

        assertThrows(IOException::class.java) { document.writeAll(bytes, CANNOT_SYNC) }
    }

    /**
     * A document whose provider's report, like a [android.os.ParcelFileDescriptor]'s, is
     * read when the document closes, so it is seen only when checked after the close.
     */
    private class HandDocument(
        private val syncError: Int? = null,
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

        override fun sync(): Int? = syncError

        override fun checkError() {
            report?.let { throw IOException(it) }
        }
    }

    private companion object {
        // Linux's error numbers, as Android's OsConstants hold them on a device.
        const val EIO = 5
        const val EINVAL = 22
        const val ENOSPC = 28
        const val EROFS = 30
        const val EDQUOT = 122

        val CANNOT_SYNC = setOf(EINVAL, EROFS)
    }
}
