package com.software.sello.feature.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.domain.port.ExportShares
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.platform.AndroidExportShares
import com.software.sello.platform.TestDocuments
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Exportar in the installed app (SELLO-121): a CSV saved through the system picker, read
 * back from the document, and a JSON shared through the share sheet with read access to
 * that one file, which the next share removes.
 */
@RunWith(AndroidJUnit4::class)
class ExportJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()
    private val folder get() = File(context.cacheDir, AndroidExportShares.FOLDER)

    /** Answers the first activity started with [action], and keeps what it was asked. */
    private class Answer(
        private val action: String,
        private val result: () -> Instrumentation.ActivityResult
    ) : Instrumentation.ActivityMonitor() {
        val requests = mutableListOf<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != action) return null
            requests += intent
            return result()
        }
    }

    @Before
    fun syntheticHistory() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        TestCommands.createCategory("Mercado", 100_000)
        TestCommands.createExpense("Mercado", TestCommands.today, 12_500, note = "=plaza")
        TestCommands.createIncome(TestCommands.today, 200_000)
    }

    @After
    fun leaveNoFixture() {
        monitors.forEach(instrumentation::removeMonitor)
        scenario?.close()
        TestData.reset()
        TestCommands.showCurrentMonth()
        runBlocking { GlobalContext.get().get<ExportShares>().clear() }
    }

    private fun answer(action: String, result: () -> Instrumentation.ActivityResult) =
        Answer(action, result).also {
            monitors += it
            instrumentation.addMonitor(it)
        }

    private fun openExport() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(EXPORT_ROW_TAG)
        rule.onNodeWithTag(EXPORT_ROW_TAG).performScrollTo().performClick()
        rule.awaitTag(EXPORT_SLIP_TAG)
    }

    private fun inSlip(text: String, substring: Boolean = false) = rule.onNode(
        hasText(text, substring = substring) and hasAnyAncestor(hasTestTag(EXPORT_SLIP_TAG))
    )

    private fun waitUntil(what: String, condition: () -> Boolean) {
        rule.waitUntil(timeoutMillis = 20_000) { condition() }
        assertTrue(what, condition())
    }

    @Test
    fun guardarWritesTheCsvToTheChosenDocumentAsASpreadsheetReadsIt() {
        val name = "sello-datos-${UUID.randomUUID()}.csv"
        val picker = answer(Intent.ACTION_CREATE_DOCUMENT) {
            Instrumentation.ActivityResult(
                Activity.RESULT_OK,
                Intent().setData(TestDocuments.uri("ok", name))
            )
        }
        openExport()
        // What the file is, and is not, before anything is asked of the system.
        inSlip("Cualquiera que abra el archivo puede leerlo.", substring = true).assertExists()
        inSlip("No es un respaldo:", substring = true).assertExists()

        inSlip("Guardar").performClick()
        rule.awaitTag(EXPORT_ROW_TAG)
        waitUntil("the row names the saved file") {
            rule.onAllNodes(hasText("Guardado:", substring = true)).fetchSemanticsNodes()
                .isNotEmpty()
        }

        assertEquals("text/csv", picker.requests.single().type)
        val text = context.contentResolver.openInputStream(TestDocuments.uri("ok", name))!!
            .use { String(it.readBytes(), Charsets.UTF_8) }
        val day = TestCommands.today.toString()
        assertEquals(
            listOf(
                "\uFEFFFecha,Tipo,Nombre,Monto,Moneda,Nota",
                "$day,Gasto,Mercado,12500,COP,'=plaza",
                "$day,Ingreso,Salario,200000,COP,",
                ""
            ),
            text.split("\r\n")
        )
        // A saved export leaves nothing behind for sharing.
        assertTrue(folder.listFiles().isNullOrEmpty())
    }

    @Test
    fun compartirGrantsReadAccessToOneFileThatTheNextShareRemoves() {
        val chooser = answer(Intent.ACTION_CHOOSER) {
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
        openExport()
        rule.onNode(hasText("JSON") and hasAnyAncestor(hasTestTag(EXPORT_FORMAT_TAG)))
            .performClick()
        inSlip("Compartir").performClick()
        waitUntil("the share sheet was asked") { chooser.requests.size == 1 }

        val first = sent(chooser.requests.single())
        assertEquals("application/json", first.second)
        assertEquals("${context.packageName}.exports", first.first.authority)
        val json = context.contentResolver.openInputStream(first.first)!!
            .use { String(it.readBytes(), Charsets.UTF_8) }
        assertTrue(json.contains("\"format\": \"sello-export\""))
        assertTrue(json.contains("\"note\": \"=plaza\""))
        assertEquals(1, folder.listFiles()?.size)

        rule.onNodeWithTag(EXPORT_ROW_TAG).performScrollTo().performClick()
        rule.awaitTag(EXPORT_SLIP_TAG)
        inSlip("Compartir").performClick()
        waitUntil("the share sheet was asked again") { chooser.requests.size == 2 }

        // One file at a time: the earlier one is gone.
        assertEquals(1, folder.listFiles()?.size)
        assertEquals(
            "csv",
            sent(chooser.requests[1]).first.lastPathSegment?.substringAfterLast('.')
        )
    }

    @Test
    fun clearingRemovesEveryExportLeftForSharingAndAPathIsNotAName() = runBlocking {
        val shares = GlobalContext.get().get<ExportShares>()
        val placed = shares.place(byteArrayOf(1, 2), "sello-datos.csv", "text/csv")
        assertFalse(placed == null)
        assertNull(shares.place(byteArrayOf(1), "../fuera.csv", "text/csv"))

        shares.clear()

        assertTrue(folder.listFiles().isNullOrEmpty())
        assertFalse(File(context.cacheDir, "fuera.csv").exists())
    }

    /** The document and type a share sheet request carries, and that it grants reading. */
    private fun sent(chooser: Intent): Pair<Uri, String> {
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        return uri to send.type!!
    }
}
