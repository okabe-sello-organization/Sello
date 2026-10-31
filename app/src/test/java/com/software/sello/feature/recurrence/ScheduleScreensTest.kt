package com.software.sello.feature.recurrence

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MORE_MENU_TAG
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.recurrence.REPEAT_ROW_TAG
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatUi
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Pagos e ingresos fijos and one schedule's editor, drawn from state given by hand.
 * On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the layout below depends on how wide the words are.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val opened = mutableListOf<String>()
    private val listActions = mutableListOf<SchedulesAction>()
    private val added = mutableListOf<ScheduleTab>()
    private val editorActions = mutableListOf<ScheduleEditorAction>()

    private fun monthly(day: Int) = (RepeatRule.Monthly.of(day) as Outcome.Success).value

    private fun row(
        id: String,
        note: String?,
        category: String,
        day: Int,
        amount: String,
        soon: Boolean = false,
        pausedFrom: LocalDate? = null,
        next: LocalDate? = null,
        group: ScheduleGroup = ScheduleGroup.Due,
        recordedOn: LocalDate? = null
    ) = ScheduleRow(
        id, note, category, "home", null, null,
        monthly(
            day
        ),
        LocalDate.of(2026, 10, 1), next, pesos(amount), soon, pausedFrom, null, group, recordedOn
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
            row(
                "a",
                "Arriendo",
                "Hogar",
                5,
                "950.000",
                next = LocalDate.of(2026, 11, 5),
                group = ScheduleGroup.Recorded,
                recordedOn = LocalDate.of(2026, 10, 5)
            ),
            row("b", null, "Hogar", 28, "200.000", soon = true, next = LocalDate.of(2026, 10, 28)),
            row(
                "c",
                "Bus",
                "Transporte",
                3,
                "20.000",
                pausedFrom = LocalDate.of(2026, 10, 12),
                group = ScheduleGroup.Paused
            ),
            row("d", "Gas", "Hogar", 23, "60.000", next = LocalDate.of(2026, 10, 23))
        ),
        ended = emptyList()
    )

    private fun list(state: SchedulesState) {
        rule.showSello(Look()) {
            SchedulesScreen(
                state,
                today,
                { listActions += it },
                { opened += it },
                PaddingValues(12.dp),
                { added += it }
            )
        }
    }

    @Test
    fun theListLeadsWithTheMonthsTotalAndGroupsEachScheduleByWhatItsMonthHolds() {
        list(ready)

        rule.onNodeWithText("Tus pagos fijos de octubre").assertIsDisplayed()
        rule.onNodeWithText("1.249.900").assertIsDisplayed()
        rule.onNodeWithText("Ya se anotaron").assertIsDisplayed()
        rule.onNodeWithText("Faltan").assertIsDisplayed()
        // Still due, nearest first; then what went through; then what is paused.
        val tops = listOf(
            "schedules.group.due",
            "schedules.row.d",
            "schedules.row.b",
            "schedules.group.recorded",
            "schedules.row.a",
            "schedules.group.paused",
            "schedules.row.c"
        ).map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        rule.onNodeWithText("FALTAN EN OCTUBRE · 2").assertExists()
        rule.onNode(
            hasContentDescription(
                "Gas, 60.000 pesos, Hogar · el 23 de cada mes, Mañana, vie 23 oct"
            )
        ).assertExists()
        rule.onNode(
            hasContentDescription(
                "Arriendo, 950.000 pesos, Hogar · el 5 de cada mes · anotado lun 5 oct, " +
                    "próximo jue 5 nov"
            )
        ).assertExists()
        rule.onNode(hasContentDescription("En pausa", substring = true)).assertExists()
        rule.onNodeWithTag("schedules.row.b").performScrollTo().performClick()

        assertEquals(listOf("b"), opened)
    }

    // --- SELLO-125: the receipt's stub ---

    private fun stub() =
        rule.onNode(hasTestTag("schedules.add") and hasAnyAncestor(hasTestTag("schedules.plan")))

    @Test
    fun theReceiptEndsInAStubThatAddsOneForTheTabShown() {
        list(ready)

        stub().performScrollTo().assertContentDescriptionEquals("Nuevo pago fijo")
            .performClick()
        // Below the receipt's figures, above the list.
        val receipt = rule.onNodeWithText("1.249.900").getUnclippedBoundsInRoot()
        val add = rule.onNodeWithTag("schedules.add").getUnclippedBoundsInRoot()
        val list = rule.onNodeWithTag("schedules.list").getUnclippedBoundsInRoot()
        assertEquals(true, add.top > receipt.bottom && add.bottom <= list.top)
        assertEquals(listOf(ScheduleTab.Payments), added)
        // The footnote no longer tells how to add one: the stub does.
        rule.onNodeWithText("Para agregar uno", substring = true).assertDoesNotExist()
    }

    @Test
    fun onIngresosTheStubAddsAnIncome() {
        list(ready.copy(tab = ScheduleTab.Incomes))

        stub().performScrollTo().assertContentDescriptionEquals("Nuevo ingreso fijo")
            .performClick()

        assertEquals(listOf(ScheduleTab.Incomes), added)
    }

    @Test
    fun nothingRepeatingShowsTheMonthAtZeroAndTheStub() {
        val zero = ScheduleMonthPlan(
            YearMonth.of(2026, 10),
            pesos("0"),
            0,
            pesos("0"),
            0,
            pesos("0"),
            0
        )
        list(SchedulesState.Ready(ScheduleTab.Payments, zero, emptyList(), emptyList()))

        rule.onNodeWithText("Tus pagos fijos de octubre").assertIsDisplayed()
        rule.onNodeWithText("Aún no tienes pagos fijos").assertIsDisplayed()
        rule.onNodeWithTag("schedules.empty").assertExists()
        // No extra line: the same stub as on a list (owner, 2026-10-26).
        stub().assertContentDescriptionEquals("Nuevo pago fijo").performClick()
        assertEquals(listOf(ScheduleTab.Payments), added)
    }

    @Test
    fun anEmptyTabWithoutAPlanStillNamesTheMonthAndOffersTheStub() {
        list(SchedulesState.Ready(ScheduleTab.Incomes, null, emptyList(), emptyList()))

        rule.onNodeWithText("Tus ingresos fijos de octubre").assertIsDisplayed()
        rule.onNodeWithText("Aún no tienes ingresos fijos").assertIsDisplayed()
        stub().assertContentDescriptionEquals("Nuevo ingreso fijo")
        // No invented figure when there is no plan to show.
        rule.onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun theStubIsTheSameCompactSizeOnBothTabsEmptyOrNot() {
        val zero = ScheduleMonthPlan(
            YearMonth.of(2026, 10),
            pesos("0"),
            0,
            pesos("0"),
            0,
            pesos("0"),
            0
        )
        val shown = mutableStateOf<SchedulesState>(
            SchedulesState.Ready(ScheduleTab.Payments, zero, emptyList(), emptyList())
        )
        rule.showSello { SchedulesScreen(shown.value, today, {}, {}, PaddingValues(12.dp), {}) }
        fun height() = stub().getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val empty = height()
        rule.runOnIdle { shown.value = ready.copy(tab = ScheduleTab.Incomes) }
        val heights = listOf(empty, height())
        // Half the space around the words, never below a 48dp target (SELLO-125).
        heights.forEach { assertEquals(48f, it.value, 0.5f) }
    }

    @Test
    fun whenTheMonthsSumIsHiddenTheStubStillShows() {
        list(ready.copy(plan = null))

        rule.onNodeWithText("1.249.900").assertDoesNotExist()
        stub().performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(ScheduleTab.Payments), added)
    }

    @Test
    fun aFailedReadSaysSoAndCanBeReadAgain() {
        list(SchedulesState.Failed)
        rule.onNodeWithTag("schedules.add").assertDoesNotExist()

        rule.onNodeWithText("No pudimos leer tus pagos fijos").assertIsDisplayed()
        rule.onNodeWithText("Aún no tienes pagos fijos").assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<SchedulesAction>(SchedulesAction.Retry), listActions)
    }

    private val editing = ScheduleEditorState(
        loading = false,
        amount = pesos("950.000"),
        categories = listOf(ScheduleCategory("h", "Hogar", "home")),
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
        rule.showSello { ScheduleEditorScreen(state, dateLabels()) { editorActions += it } }
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
        rule.onNodeWithTag(MORE_MENU_TAG).performClick()
        rule.onNodeWithText("Borrar pago fijo").performClick()

        assertEquals(listOf(ScheduleEditorAction.Pause, ScheduleEditorAction.Delete), editorActions)
    }

    @Test
    fun aScheduleWhoseRuleWasRemovedOffersToRepeatItAgain() {
        editor(editing.copy(repeat = RepeatUi(), missing = ScheduleMissing.Repeat))

        rule.onNodeWithTag(REPEAT_ROW_TAG).performScrollTo()
            .assertTextEquals("Repetir este gasto")
            .assertContentDescriptionEquals("No se repite. Repetir este gasto")
        rule.onNodeWithTag(SCHEDULE_SAVE_TAG).assertIsNotEnabled()
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
