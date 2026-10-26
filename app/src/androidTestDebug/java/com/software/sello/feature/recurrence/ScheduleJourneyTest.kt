package com.software.sello.feature.recurrence

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.MainActivity
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.hideKeyboard
import com.software.sello.presentation.recurrence.REPEAT_SHEET_TAG
import com.software.sello.press
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pagos e ingresos fijos on real storage: reached from Resumen, a monthly income made with
 * Se repite from today is recorded by catch-up at once, then paused and deleted, and the
 * income it recorded stays (recurrence-examples rules 6, 8 and 10).
 */
@RunWith(AndroidJUnit4::class)
class ScheduleJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun emptyHistory() = TestData.reset()

    @After
    fun close() {
        scenario?.hideKeyboard()
        scenario?.close()
        TestData.reset()
    }

    private fun openList() {
        rule.awaitText("Resumen")
        rule.onNodeWithText("Resumen").performClick()
        rule.awaitTag("resumen.income")
        rule.onNodeWithTag("resumen.openSchedules").performScrollTo().performClick()
        rule.awaitTag(SCHEDULES_TAG)
    }

    private fun inSheet(text: String) =
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(REPEAT_SHEET_TAG)))

    @Test
    fun aMonthlyIncomeFromTodayIsRecordedPausedAndDeletedKeepingWhatItRecorded() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        openList()
        rule.onNodeWithText("Ingresos").performClick()
        rule.awaitTag("schedules.empty")
        // On Ingresos the top bar's + adds an income that repeats (SELLO-122).
        rule.onNodeWithTag("schedules.add").performClick()

        // The entry opens with Se repite's sheet, each month on today's day.
        rule.awaitTag(REPEAT_SHEET_TAG)
        inSheet("Próximo: hoy. Se anota en cuanto guardes.").assertExists()
        inSheet("Listo").press()
        listOf("1", "000", "000").forEach { key ->
            rule.onNode(hasText(key) and hasAnyAncestor(hasTestTag("income.keypad"))).performClick()
        }
        rule.onNodeWithText("Guardar ingreso fijo").assertExists()
        rule.onNodeWithTag("income.submit").press()

        // Saved, and recorded today by catch-up: one income from it, nothing else.
        rule.awaitTag(SCHEDULES_TAG)
        assertEquals(
            listOf("1000000|income|1"),
            TestData.rows("SELECT amount_minor, kind, version FROM schedule")
        )
        val id = TestData.rows("SELECT id FROM schedule").single()
        rule.awaitTag("schedules.row.$id")
        assertEquals(
            listOf("1000000|$id"),
            TestData.rows("SELECT amount_minor, origin_schedule_id FROM income")
        )

        rule.onNodeWithTag("schedules.row.$id").performClick()
        rule.awaitTag(SCHEDULE_PAUSE_TAG)
        rule.onNodeWithTag(SCHEDULE_PAUSE_TAG).performScrollTo().press()
        rule.awaitText("Ingreso fijo en pausa")
        assertEquals(1L, TestData.count("schedule_pause"))
        // The row is read as one sentence that now says it is paused.
        rule.waitUntil(WAIT_MS) {
            rule.onAllNodes(hasContentDescription("En pausa", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("schedules.row.$id").performClick()
        rule.awaitTag(SCHEDULE_RESUME_TAG)
        rule.onNode(hasText("Borrar") and hasAnyAncestor(hasTestTag(SCHEDULE_DELETE_TAG)))
            .performScrollTo().press()
        rule.onNodeWithText("¿Borrarlo?").assertExists()
        rule.onNode(hasText("Borrar") and !hasAnyAncestor(hasTestTag(SCHEDULE_DELETE_TAG)))
            .press()
        rule.awaitText("Ingreso fijo borrado. Lo ya anotado se queda.")
        assertEquals(0L, TestData.count("schedule"))
        assertEquals(0L, TestData.count("schedule_pause"))
        assertEquals(listOf("1000000"), TestData.rows("SELECT amount_minor FROM income"))
    }
}

private const val WAIT_MS = 15_000L
