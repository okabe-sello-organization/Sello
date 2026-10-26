package com.software.sello.feature.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupPlan
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupRecords
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.feature.settings.SETTINGS_TAG
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.platform.TestDocuments
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Saving a backup in the installed app, from Configuración to a document, then reading
 * that document back with the restore-side reader. Only the system picker is answered
 * by the test; the document itself is written through a real content provider.
 */
@RunWith(AndroidJUnit4::class)
class BackupFileJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val koin get() = GlobalContext.get()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var picker: Picker? = null

    /** Stands in for the system's save dialog: answers with [answer] and remembers the request. */
    private class Picker(private val answer: () -> Instrumentation.ActivityResult) :
        Instrumentation.ActivityMonitor() {
        val requests = mutableListOf<Intent>()

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_CREATE_DOCUMENT) return null
            requests += intent
            return answer()
        }
    }

    @Before
    fun syntheticG01() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        val today = TestCommands.today
        TestCommands.createCategory("Mercado", 100_000)
        TestCommands.createExpense("Mercado", today.minusDays(2), 10_000)
        TestCommands.createExpense("Mercado", today.minusDays(1), 15_000)
        TestCommands.createExpense("Mercado", today, 5_000)
        TestCommands.createIncome(today, 200_000)
    }

    @After
    fun leaveNoFinancialFixture() {
        picker?.let(instrumentation::removeMonitor)
        scenario?.close()
        TestData.reset()
        TestCommands.showCurrentMonth()
    }

    private fun answerWith(answer: () -> Instrumentation.ActivityResult) = Picker(answer).also {
        picker = it
        instrumentation.addMonitor(it)
    }

    private fun document(name: String) = Instrumentation.ActivityResult(
        Activity.RESULT_OK,
        Intent().setData(TestDocuments.uri("ok", name))
    )

    private fun record(): BackupRecord = runBlocking {
        (koin.get<BackupRecords>().observeBackups().first() as PreferenceRead.Ready).value
    }

    private fun openBackup() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(SETTINGS_TAG)
        rule.onNodeWithTag(BACKUP_CARD_CONTENTS_TAG).performScrollTo().performClick()
        rule.awaitTag(BACKUP_TAG)
    }

    private fun readBack(name: String): BackupPlan {
        val bytes = context.contentResolver.openInputStream(TestDocuments.uri("ok", name))!!
            .use { it.readBytes() }
        return when (val read = BackupCodec().read(bytes)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> throw AssertionError("the saved file was rejected: ${read.error}")
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        rule.waitUntil(timeoutMillis = 20_000) { condition() }
        assertTrue(what, condition())
    }

    @Test
    fun aSavedBackupReadsBackAsTheHistoryItWasMadeFrom() {
        val name = "sello-respaldo-${UUID.randomUUID()}.json"
        val picker = answerWith { document(name) }
        val revision = TestData.long("SELECT revision FROM profile")
        val rows = TestData.financialRows()
        val started = Instant.now()
        openBackup()

        // What a backup is, before anything is chosen.
        rule.onNodeWithTag(BACKUP_DISCLOSURE_TAG).assertExists()
        rule.onNode(hasText("Cualquiera que abra el archivo puede leerlo.", substring = true))
            .assertExists()
        rule.onNodeWithTag(BACKUP_CREATE_TAG).performScrollTo().performClick()
        rule.awaitTag(BACKUP_SAVED_TAG)

        assertEquals(1, picker.requests.size)
        assertEquals("application/json", picker.requests.single().type)
        assertTrue(
            picker.requests.single().getStringExtra(Intent.EXTRA_TITLE)!!
                .matches(Regex("sello-respaldo-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.json"))
        )
        val plan = readBack(name)
        assertEquals(1, plan.counts.categories)
        assertEquals(
            listOf(10_000L, 15_000L, 5_000L),
            plan.contents.financial.expenses.map { it.amount.money.minorUnits }
        )
        assertEquals(
            listOf(200_000L),
            plan.contents.financial.incomes.map { it.amount.money.minorUnits }
        )
        // Saving changed no financial data.
        assertEquals(revision, TestData.long("SELECT revision FROM profile"))
        assertEquals(rows, TestData.financialRows())
        val last = record().last!!
        assertEquals(name, last.name)
        assertFalse(record().unfinished)
        assertFalse(last.at.isBefore(started.minusSeconds(1)))
        rule.onNodeWithText(name).assertExists()
    }

    private fun openSettings() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(BACKUP_CARD_TAG)
    }

    private fun onCard(text: String) =
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(BACKUP_CARD_TAG)))

    @Test
    fun theCardSavesOnlyAfterTheDisclosureAndThenSaysGuardadoToday() {
        val name = "sello-respaldo-${UUID.randomUUID()}.json"
        val picker = answerWith { document(name) }
        openSettings()

        rule.onNodeWithTag(BACKUP_CARD_CREATE_TAG).performScrollTo().performClick()
        rule.awaitTag(BACKUP_CARD_DISCLOSURE_TAG)
        rule.onNode(hasText("Cualquiera que abra el archivo puede leerlo.", substring = true))
            .assertExists()
        // Nothing is asked of the system before the disclosure is accepted.
        assertEquals(0, picker.requests.size)

        rule.onNodeWithText("Elegir dónde guardar").performClick()
        waitUntil("the backup was recorded") { record().last?.name == name }
        rule.waitForIdle()

        assertEquals(1, picker.requests.size)
        assertEquals(1, readBack(name).counts.categories)
        onCard("Respaldar").assertExists()
        rule.onNode(
            hasText("Hoy", substring = true) and
                hasAnyAncestor(hasTestTag(BACKUP_CARD_TAG))
        ).assertExists()
    }

    @Test
    fun cancellingTheDisclosureAsksForNothingAndSavesNothing() {
        val before = record().last
        val picker = answerWith { document("sello-respaldo-${UUID.randomUUID()}.json") }
        openSettings()

        rule.onNodeWithTag(BACKUP_CARD_CREATE_TAG).performScrollTo().performClick()
        rule.awaitTag(BACKUP_CARD_DISCLOSURE_TAG)
        rule.onNodeWithText("Cancelar").performClick()
        rule.waitForIdle()

        assertEquals(
            0,
            rule.onAllNodes(hasTestTag(BACKUP_CARD_DISCLOSURE_TAG)).fetchSemanticsNodes().size
        )
        assertEquals(0, picker.requests.size)
        assertEquals(before, record().last)
        assertFalse(record().unfinished)
    }

    @Test
    fun dismissingThePickerSavesNothingAndSaysNothing() {
        val before = record().last
        val picker = answerWith { Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) }
        openBackup()

        rule.onNodeWithTag(BACKUP_CREATE_TAG).performScrollTo().performClick()
        waitUntil("the picker was asked") { picker.requests.size == 1 }
        rule.waitForIdle()

        rule.onNodeWithTag(BACKUP_CREATE_TAG).assertIsEnabled()
        assertEquals(0, rule.onAllNodes(hasTestTag(BACKUP_SAVED_TAG)).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(hasTestTag(BACKUP_FAILED_TAG)).fetchSemanticsNodes().size)
        assertEquals(before, record().last)
        assertFalse(record().unfinished)
    }

    @Test
    fun aDocumentThatCannotBeWrittenIsAFailureAndKeepsTheLastBackup() {
        val good = "sello-respaldo-${UUID.randomUUID()}.json"
        var answer = document(good)
        answerWith { answer }
        openBackup()
        rule.onNodeWithTag(BACKUP_CREATE_TAG).performScrollTo().performClick()
        rule.awaitTag(BACKUP_SAVED_TAG)
        val saved = record().last

        answer = Instrumentation.ActivityResult(
            Activity.RESULT_OK,
            Intent().setData(TestDocuments.uri("broken"))
        )
        rule.onNodeWithTag(BACKUP_CREATE_TAG).performScrollTo().performClick()
        rule.awaitTag(BACKUP_FAILED_TAG)

        assertEquals(0, rule.onAllNodes(hasTestTag(BACKUP_SAVED_TAG)).fetchSemanticsNodes().size)
        assertEquals(saved, record().last)
        assertFalse(record().unfinished)
    }

    @Test
    fun aLargeHistoryIsSavedWithoutFreezingTheScreen() {
        // 20,000 synthetic expenses, written in one statement for speed.
        val category = TestCommands.categoryId("Mercado")
        val day = TestCommands.today.toString()
        TestData.execute(
            """
            WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n WHERE x < 20000)
            INSERT INTO expense (id, category_id, effective_date, sequence, amount_minor,
                currency, note, version, created_at, updated_at)
            SELECT printf('00000000-0000-4000-8000-%012d', x), '$category', '$day',
                1000000 + x, 1000 + x, 'COP', NULL, 1, 1767225600000, 1767225600000 FROM n
            """.trimIndent()
        )
        val name = "sello-respaldo-${UUID.randomUUID()}.json"
        answerWith { document(name) }
        openBackup()

        // A tick on the main thread every 16 ms; the longest wait between ticks is how
        // long the screen could not respond.
        val main = Handler(Looper.getMainLooper())
        var last = SystemClock.uptimeMillis()
        var longest = 0L
        var ticking = true
        val tick = object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                longest = maxOf(longest, now - last)
                last = now
                if (ticking) main.postDelayed(this, 16)
            }
        }
        rule.onNodeWithTag(BACKUP_CREATE_TAG).performScrollTo().performClick()
        main.post(tick)
        rule.awaitTag(BACKUP_SAVED_TAG)
        ticking = false

        assertEquals(20_003, readBack(name).counts.expenses)
        assertTrue("the main thread stalled for $longest ms", longest < 700)
    }
}
