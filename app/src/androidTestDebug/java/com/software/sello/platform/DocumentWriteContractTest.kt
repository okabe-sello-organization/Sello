package com.software.sello.platform

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.DocumentWrite
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The Android document writer against a real content provider and its failures. */
@RunWith(AndroidJUnit4::class)
class DocumentWriteContractTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatchers = SystemDispatcherProvider()
    private val writer by lazy { AndroidDocumentWriter(context.contentResolver, dispatchers) }

    // Synthetic content: no real backup is needed to prove the bytes arrive intact.
    private val bytes = ByteArray(300_000) { (it % 251).toByte() }

    private fun read(name: String): ByteArray = requireNotNull(
        context.contentResolver.openInputStream(TestDocuments.uri("ok", name))
    ).use { it.readBytes() }

    private fun write(vararg path: String) = runBlocking {
        writer.write(DocumentDestination(TestDocuments.uri(*path).toString()), bytes)
    }

    @Test
    fun everyByteArrivesAndTheProvidersNameIsReported() {
        val name = "sello-respaldo-${UUID.randomUUID()}.json"

        assertEquals(DocumentWrite.Completed(name, bytes.size.toLong()), write("ok", name))
        assertArrayEquals(bytes, read(name))
    }

    @Test
    fun aDocumentChosenAgainIsReplacedNotMixedWithItsOldContents() {
        val name = "sello-respaldo-${UUID.randomUUID()}.json"
        context.contentResolver.openOutputStream(TestDocuments.uri("ok", name))!!
            .use { it.write(ByteArray(bytes.size * 2) { 7 }) }

        write("ok", name)

        assertArrayEquals(bytes, read(name))
    }

    @Test
    fun aProviderThatRefusesTheDocumentIsAFailure() {
        assertEquals(DocumentWrite.Failed, write("refuse"))
    }

    @Test
    fun aDocumentThatCannotBeWrittenIsAFailure() {
        assertEquals(DocumentWrite.Failed, write("readonly"))
    }

    @Test
    fun aProviderThatGoesAwayMidWriteIsAFailure() {
        assertEquals(DocumentWrite.Failed, write("broken"))
    }

    @Test
    fun aDestinationThatIsNotADocumentIsAFailure() = runBlocking {
        assertEquals(DocumentWrite.Failed, writer.write(DocumentDestination("not a uri"), bytes))
    }
}
