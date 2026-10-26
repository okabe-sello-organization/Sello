package com.software.sello.feature.backup

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.DataExports
import com.software.sello.domain.port.DocumentDestination
import com.software.sello.domain.port.DocumentWrite
import com.software.sello.domain.port.ExportFailure
import com.software.sello.domain.port.ExportFormat
import com.software.sello.domain.port.ExportShares
import com.software.sello.domain.port.ExportWords
import com.software.sello.domain.port.PreparedExport
import com.software.sello.domain.port.SharedExport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exportar (SELLO-121): saving through the picker, sharing, and what a failure says. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private val words = ExportWords(
        "Fecha", "Tipo", "Nombre", "Monto", "Moneda", "Nota", "Gasto", "Ingreso",
        "Aporte a meta", "Retiro de meta", "Aporte a inversión", "Retiro de inversión",
        "Dividendo", "Valor de inversión", mapOf("salary" to "Salario")
    )
    private val exports = HandDataExports()
    private val writer = HandDocumentWriter().apply {
        answer = DocumentWrite.Completed("sello-datos-2026-10-26.csv", 120)
    }
    private val shares = HandExportShares()
    private val document = "content://provider/document/7"

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun session(scope: CoroutineScope) = ExportSession(exports, writer, shares, scope)

    private fun TestScope.model(
        session: ExportSession = session(backgroundScope),
        handle: SavedStateHandle = SavedStateHandle()
    ) = ExportViewModel(handle, session).also { model ->
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
    }

    /** Taps Guardar for [format] and lets the screen hand the request to the picker. */
    private fun TestScope.openPicker(model: ExportViewModel, format: ExportFormat): String {
        model.onAction(ExportAction.Save(format))
        runCurrent()
        val request = requireNotNull(model.state.value.pickerRequest) { "no picker request" }
        assertEquals(format, request.format)
        model.onAction(ExportAction.PickerOpened)
        runCurrent()
        return request.name
    }

    @Test
    fun guardarAsksThePickerWithTheNameThenWritesTheChosenFormat() = runTest {
        val model = model()

        assertEquals("sello-datos-2026-10-26.json", openPicker(model, ExportFormat.Json))
        // Nothing is read before a place is chosen.
        assertTrue(exports.prepared.isEmpty())

        model.onAction(ExportAction.DestinationChosen(document, words))
        runCurrent()

        assertEquals(listOf(ExportFormat.Json), exports.prepared)
        assertEquals(listOf(DocumentDestination(document) to 3), writer.written)
        assertEquals("sello-datos-2026-10-26.csv", model.state.value.saved)
        assertFalse(model.state.value.busy)
        assertTrue(shares.placed.isEmpty())
    }

    @Test
    fun dismissingThePickerReadsNothingAndSaysNothing() = runTest {
        val model = model()
        openPicker(model, ExportFormat.Csv)

        model.onAction(ExportAction.DestinationChosen(null, words))
        runCurrent()

        assertTrue(exports.prepared.isEmpty())
        assertTrue(writer.written.isEmpty())
        assertEquals(ExportState(), model.state.value)
    }

    @Test
    fun aDocumentThatCannotBeWrittenSaysTheFileMayBeIncomplete() = runTest {
        writer.answer = DocumentWrite.Failed
        val model = model()
        openPicker(model, ExportFormat.Csv)

        model.onAction(ExportAction.DestinationChosen(document, words))
        runCurrent()

        assertEquals(ExportProblem.Write, model.state.value.problem)
        assertNull(model.state.value.saved)
    }

    @Test
    fun anUnreadableHistoryWritesNothingNeverAnEmptyFile() = runTest {
        exports.next = Outcome.Failure(ExportFailure.StorageUnavailable)
        val model = model()
        openPicker(model, ExportFormat.Csv)

        model.onAction(ExportAction.DestinationChosen(document, words))
        runCurrent()

        assertEquals(ExportProblem.Storage, model.state.value.problem)
        assertTrue(writer.written.isEmpty())

        model.onAction(ExportAction.Share(ExportFormat.Csv, words))
        runCurrent()
        assertEquals(ExportProblem.Storage, model.state.value.problem)
        assertTrue(shares.placed.isEmpty())
    }

    @Test
    fun compartirReplacesTheEarlierSharedFileAndOpensTheShareSheetOnce() = runTest {
        val model = model()

        model.onAction(ExportAction.Share(ExportFormat.Csv, words))
        runCurrent()

        assertEquals(1, shares.cleared)
        assertEquals(listOf("sello-datos-2026-10-26.csv|text/csv"), shares.placed)
        assertEquals(
            SharedExport(
                "content://com.software.sello.exports/exports/sello-datos-2026-10-26.csv",
                "text/csv"
            ),
            model.state.value.share
        )

        model.onAction(ExportAction.ShareOpened)
        runCurrent()
        assertNull(model.state.value.share)

        model.onAction(ExportAction.Share(ExportFormat.Json, words))
        runCurrent()
        assertEquals(2, shares.cleared)
    }

    @Test
    fun aFileThatCannotBePlacedForSharingSaysNothingLeftThePhone() = runTest {
        shares.fail = true
        val model = model()

        model.onAction(ExportAction.Share(ExportFormat.Json, words))
        runCurrent()

        assertEquals(ExportProblem.Share, model.state.value.problem)
        assertNull(model.state.value.share)
        model.onAction(ExportAction.Dismiss)
        runCurrent()
        assertNull(model.state.value.problem)
    }

    @Test
    fun thePickersAnswerReachesTheViewModelOfARecreatedProcess() = runTest {
        val handle = SavedStateHandle()
        openPicker(model(handle = handle), ExportFormat.Json)

        // The process is gone; the picker answers a new view model with the same state.
        val recreated = model(session(backgroundScope), handle)
        recreated.onAction(ExportAction.DestinationChosen(document, words))
        runCurrent()

        assertEquals(listOf(ExportFormat.Json), exports.prepared)
        assertEquals("sello-datos-2026-10-26.csv", recreated.state.value.saved)
    }
}

private class HandDataExports : DataExports {
    val prepared = mutableListOf<ExportFormat>()
    var next: Outcome<PreparedExport, ExportFailure>? = null

    override suspend fun suggestedName(format: ExportFormat) =
        "sello-datos-2026-10-26.${if (format == ExportFormat.Csv) "csv" else "json"}"

    override suspend fun prepare(
        format: ExportFormat,
        words: ExportWords
    ): Outcome<PreparedExport, ExportFailure> {
        prepared += format
        return next ?: Outcome.Success(
            PreparedExport(
                byteArrayOf(1, 2, 3),
                suggestedName(format),
                if (format == ExportFormat.Csv) "text/csv" else "application/json"
            )
        )
    }
}

private class HandExportShares : ExportShares {
    val placed = mutableListOf<String>()
    var cleared = 0
    var fail = false

    override suspend fun place(bytes: ByteArray, name: String, mimeType: String): SharedExport? {
        if (fail) return null
        placed += "$name|$mimeType"
        return SharedExport("content://com.software.sello.exports/exports/$name", mimeType)
    }

    override suspend fun clear() {
        cleared++
    }
}
