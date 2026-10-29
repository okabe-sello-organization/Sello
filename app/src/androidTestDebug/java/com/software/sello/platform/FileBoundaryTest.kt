package com.software.sello.platform

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.DocumentRead
import com.software.sello.domain.port.DocumentSource
import com.software.sello.domain.port.DocumentWrite
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The document reader and writer only ever reach documents a provider hands out. An
 * address to a file, Sello's own private ones included, or anything that is not a
 * content address is refused before any byte moves. The private file here is a
 * synthetic canary standing in for the database.
 */
@RunWith(AndroidJUnit4::class)
class FileBoundaryTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers = SystemDispatcherProvider()
    private val reader by lazy { AndroidDocumentReader(context.contentResolver, dispatchers) }
    private val writer by lazy { AndroidDocumentWriter(context.contentResolver, dispatchers) }
    private val canary = "MARCADOR-PRIVADO-4721".toByteArray()
    private val privateFile: File get() = File(context.filesDir, "boundary-canary.db")

    @Before
    fun privateCanary() = privateFile.writeBytes(canary)

    @After
    fun removeCanary() {
        privateFile.delete()
    }

    private val notDocuments get() = listOf(
        Uri.fromFile(privateFile).toString(),
        privateFile.path,
        "file://${context.getDatabasePath("sello.db").path}",
        "android.resource://${context.packageName}/raw/nothing",
        "content://com.software.sello.nowhere/document/1",
        "not a uri",
        ""
    )

    @Test
    fun onlyAProvidersDocumentCanBeRead() = runBlocking {
        for (address in notDocuments) {
            assertEquals(address, DocumentRead.Failed, reader.read(DocumentSource(address), 1_000))
        }
    }

    @Test
    fun onlyAProvidersDocumentCanBeWrittenAndPrivateFilesAreNeverTouched() = runBlocking {
        for (address in notDocuments) {
            assertEquals(
                address,
                DocumentWrite.Failed,
                writer.write(DocumentDestination(address), "{}".toByteArray())
            )
        }
        assertArrayEquals(canary, privateFile.readBytes())
    }

    @Test
    fun aProvidersDocumentStillWorks() = runBlocking {
        val uri = TestDocuments.uri("ok", "boundary-${System.nanoTime()}.json").toString()

        assertEquals(
            DocumentWrite.Completed::class,
            writer.write(DocumentDestination(uri), canary)::class
        )
        assertArrayEquals(
            canary,
            (reader.read(DocumentSource(uri), 1_000) as DocumentRead.Read).bytes
        )
    }
}
