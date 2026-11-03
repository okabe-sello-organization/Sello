package com.software.sello.feature.recurrence

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.feature.expense.storedExpense
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lo que Sello anotó and its Listo, drawn from one read of the notice (SELLO-146). On the
 * JVM, frame by frame: catch-up records the bus after the last frame and before Listo.
 */
@RunWith(AndroidJUnit4::class)
class CatchUpListoTest {
    @get:Rule
    val rule = createComposeRule()

    private val preferences = HandCatchUpPreferences()
    private val records = HandRecords()
    private val categories = HandCategoryReads().apply {
        categories = listOf(category(1, "Hogar"), category(2, "Transporte", icon = "bus"))
    }

    private fun payment(number: Int, category: Int, createdAt: String, note: String? = null) =
        ScheduleRecord.Payment(
            storedExpense(number, category, "2026-10-22", 20_000, note)
                .copy(createdAt = Instant.parse(createdAt))
        )

    private fun row(number: Int) =
        rule.onNodeWithTag("catch-up.${storedExpense(number, 1, "2026-10-22", 1).id.value}")

    @Test
    fun anEntryRecordedAfterTheLastFrameStaysInTheListAfterListo() {
        records.entries.value = listOf(payment(50, 1, "2026-10-22T14:00:00Z", "Arriendo"))
        val model = CatchUpNoticeViewModel(
            preferences,
            records,
            categories,
            HandFinancialClock("2026-10-22"),
            testMoney(),
            HandCarry()
        )
        var closed = 0
        rule.showSello {
            CatchUpRecordedRoot(model, onEdit = { _, _ ->
            }, onClosed = { closed++ }) { done, list ->
                Column {
                    Button(onClick = done) { Text("Listo") }
                    list(PaddingValues())
                }
            }
        }
        row(50).assertIsDisplayed()

        rule.mainClock.autoAdvance = false
        records.entries.value += payment(51, 2, "2026-10-22T14:30:00Z")
        rule.waitForIdle()
        // The model has the bus; no frame has drawn it.
        assertEquals(2, (model.state.value as CatchUpNoticeState.Ready).items.size)
        row(51).assertDoesNotExist()
        // Its click as the last frame laid it out; a touch would move the clock and draw a frame.
        rule.onNodeWithText("Listo").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(listOf(Instant.parse("2026-10-22T14:00:00Z")), preferences.marked)
        assertTrue(closed > 0)
        row(51).assertIsDisplayed()
        row(50).assertDoesNotExist()
    }
}
