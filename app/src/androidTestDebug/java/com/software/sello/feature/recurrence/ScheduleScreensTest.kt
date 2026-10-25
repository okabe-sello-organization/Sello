package com.software.sello.feature.recurrence

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.feature.expense.EntryCategory
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatUi
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pagos e ingresos fijos and one schedule's editor, drawn from state given by hand. */
@RunWith(AndroidJUnit4::class)
class ScheduleScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val opened = mutableListOf<String>()
    private val added = mutableListOf<Boolean>()
    private val listActions = mutableListOf<SchedulesAction>()
    private val editorActions = mutableListOf<ScheduleEditorAction>()

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun monthly(day: Int) = (RepeatRule.Monthly.of(day) as Outcome.Success).value

    private fun row(
        id: String,
        note: String?,
        category: String,
        day: Int,
        amount: String,
        soon: Boolean = false,
        pausedFrom: LocalDate? = null,
        next: LocalDate? = null
    ) = ScheduleRow(
        id, note, category, "home", null, null,
        monthly(
            day
        ),
        LocalDate.of(2026, 10, 1), next, pesos(amount), soon, pausedFrom, null
    )

    private val ready = SchedulesState.Ready(
        tab = ScheduleTab.Payments,
        plan = ScheduleMonthPlan(
            YearMonth.of(2026, 10),
            pesos("1.049.900"),
            2,
            pesos("200.000"),
            1,
            pesos("1.249.900"),
            3
        ),
        rows = listOf(
            row("a", "Arriendo", "Hogar", 5, "950.000", next = LocalDate.of(2026, 11, 5)),
            row("b", null, "Hogar", 28, "200.000", soon = true, next = LocalDate.of(2026, 10, 28)),
            row("c", "Bus", "Transporte", 3, "20.000", pausedFrom = LocalDate.of(2026, 10, 12))
        ),
        ended = emptyList()
    )

    private fun list(state: SchedulesState, fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                SelloTheme(reducedMotion = true) {
                    SchedulesScreen(state, today, { listActions += it }, { opened += it }, {
                        added +=
                            it
                    }, PaddingValues(12.dp))
                }
            }
        }
    }

    @Test
    fun theListShowsTheMonthAsAPlanAndEachScheduleAsOneSentence() {
        list(ready)

        rule.onNodeWithText("PREVISTO EN OCTUBRE").assertIsDisplayed()
        rule.onNodeWithText("3 pagos activos").assertIsDisplayed()
        rule.onNodeWithText("Faltan (1)").assertIsDisplayed()
        rule.onNode(
            hasContentDescription(
                "Arriendo, 950.000 pesos, Hogar · el 5 de cada mes · próximo jue 5 nov"
            )
        ).assertExists()
        rule.onNode(hasContentDescription("En pausa", substring = true)).assertExists()
        rule.onNodeWithTag("schedules.row.b").performScrollTo().performClick()

        assertEquals(listOf("b"), opened)
    }

    @Test
    fun nothingRepeatingOffersToRecordSomethingThatDoesAndAFailedReadIsNeverEmpty() {
        list(SchedulesState.Ready(ScheduleTab.Incomes, null, emptyList(), emptyList()))
        rule.onNodeWithText("Nada se repite todavía").assertIsDisplayed()
        rule.onNodeWithText("Anotar algo que se repite").performClick()

        assertEquals(listOf(true), added)
    }

    @Test
    fun aFailedReadSaysSoAndCanBeReadAgain() {
        list(SchedulesState.Failed)

        rule.onNodeWithText("No pudimos leer tus pagos fijos").assertIsDisplayed()
        rule.onNodeWithText("Nada se repite todavía").assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<SchedulesAction>(SchedulesAction.Retry), listActions)
    }

    @Test
    fun atTwiceTheFontSizeEveryRowCanBeReached() {
        list(ready, fontScale = 2f)

        rule.onNodeWithTag(SCHEDULES_TAG).performScrollToNode(hasTestTag("schedules.row.c"))
        rule.onNodeWithTag("schedules.row.c").assertIsDisplayed()
    }

    private val editing = ScheduleEditorState(
        loading = false,
        amount = pesos("950.000"),
        categories = listOf(EntryCategory("h", "Hogar", "home")),
        categoryId = "h",
        note = "Arriendo",
        start = LocalDate.of(2026, 10, 1),
        today = today,
        repeat = RepeatUi(
            choice = RepeatChoice.of(monthly(5), null),
            next = LocalDate.of(2026, 11, 5)
        ),
        next = LocalDate.of(2026, 11, 5),
        missing = ScheduleMissing.Changes
    )

    private fun editor(state: ScheduleEditorState) {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                ScheduleEditorScreen(state, dateLabels()) { editorActions += it }
            }
        }
    }

    @Test
    fun theEditorSaysChangesApplyFromTomorrowAndWaitsForOne() {
        editor(editing)

        // The title, and the amount slip's label.
        rule.onAllNodesWithText("Pago fijo").assertCountEquals(2)
        rule.onNodeWithText("Desde jue 1 oct").assertExists()
        rule.onNodeWithText(
            "Los cambios aplican desde mañana. Lo que ya se anotó no cambia."
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(SCHEDULE_SAVE_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(SCHEDULE_PAUSE_TAG).performScrollTo().performClick()
        rule.onNode(
            hasText("Borrar") and hasAnyAncestor(hasTestTag(SCHEDULE_DELETE_TAG))
        ).performScrollTo().performClick()

        assertEquals(listOf(ScheduleEditorAction.Pause, ScheduleEditorAction.Delete), editorActions)
    }

    @Test
    fun aPausedScheduleSaysWhyAndOffersReanudar() {
        editor(
            editing.copy(
                pausedFrom = LocalDate.of(2026, 10, 12),
                pausedByArchive = "Hogar",
                missing = null
            )
        )

        rule.onNodeWithText(
            "En pausa porque Hogar está archivada"
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(SCHEDULE_PAUSE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(SCHEDULE_SAVE_TAG).assertIsEnabled()
        rule.onNodeWithTag(SCHEDULE_RESUME_TAG).performScrollTo().performClick()

        assertEquals(listOf<ScheduleEditorAction>(ScheduleEditorAction.Resume), editorActions)
    }

    @Test
    fun deletingAsksFirstAndSaysRecordedEntriesStay() {
        editor(editing.copy(confirmDelete = true))

        rule.onNodeWithText(
            "Sello deja de anotarlo desde hoy. Los gastos ya anotados no se borran."
        ).assertIsDisplayed()
    }
}
