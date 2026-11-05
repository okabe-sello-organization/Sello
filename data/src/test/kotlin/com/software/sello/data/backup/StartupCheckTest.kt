package com.software.sello.data.backup

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.RestoreRejection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the start-up failure screen is told about a chosen file ([checkFile]), where a
 * FinanceTracker file cannot be imported: it is named for what it is, never "not a
 * backup".
 */
class StartupCheckTest {
    private val financeTracker = requireNotNull(
        javaClass.getResourceAsStream("/legacy/financetracker-mixed.json")
    ).readBytes()

    private fun check(bytes: ByteArray) =
        (BackupCodec().checkFile(bytes) as? Outcome.Failure)?.error

    @Test
    fun aFinanceTrackerFileIsNamedForWhatItIs() {
        assertEquals(RestoreRejection.FinanceTrackerFile, check(financeTracker))
    }

    @Test
    fun aNewerFinanceTrackerFileIsNamedNewerAsRespaldoNamesItAndPromisesNoImport() {
        val newer = """{"version": 3, "exportedAt": "2026-10-25 14:01", "preferences": {},
            |"categories": [], "expenses": [], "income": []}
        """.trimMargin().toByteArray()

        assertEquals(RestoreRejection.NewerVersion, check(newer))
    }

    @Test
    fun aFileThatIsNeitherIsStillNotABackup() {
        for (text in listOf("{}", "[]", """{"exportedAt": "2026-10-25 14:01"}""")) {
            assertEquals(text, RestoreRejection.NotABackup, check(text.toByteArray()))
        }
    }
}
