package com.software.sello.feature.recurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatKind
import com.software.sello.presentation.recurrence.RepeatUi
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Pagos fijos and a schedule's editor look like, kept as committed images (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleScreensScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

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
        id, note, category, "home", null, null, monthly(day),
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

    private fun list(look: Look) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                SchedulesScreen(ready, today, {}, {}, PaddingValues(12.dp), {})
            }
        }
        rule.captureStill("src/test/screenshots/schedules-${look.label}.png")
    }

    /** A refresh failed: the month as last read, under the notice. */
    @Test
    fun listStale() {
        val look = Look()
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                SchedulesScreen(ready.copy(stale = true), today, {}, {}, PaddingValues(12.dp), {})
            }
        }
        rule.captureStill("src/test/screenshots/schedules-stale-${look.label}.png")
    }

    private fun editor(look: Look) {
        rule.showSello(look) { ScheduleEditorScreen(editing, dateLabels()) {} }
        rule.captureStill("src/test/screenshots/schedule-editor-${look.label}.png")
    }

    /** A refresh failed: the form on what was last read, under the notice. */
    @Test
    fun editorStale() {
        val look = Look()
        rule.showSello(look) { ScheduleEditorScreen(editing.copy(stale = true), dateLabels()) {} }
        rule.captureStill("src/test/screenshots/schedule-editor-stale-${look.label}.png")
    }

    @Test
    fun listCobaltoLight() = list(Look())

    @Test
    fun listCobaltoDark() = list(Look(dark = true))

    @Test
    fun listVioletaLight() = list(Look(ink = SelloInk.Violeta))

    @Test
    fun editorCobaltoLight() = editor(Look())

    @Test
    fun editorCobaltoDark() = editor(Look(dark = true))

    @Test
    fun editorVioletaLight() = editor(Look(ink = SelloInk.Violeta))

    private fun listShot(name: String, state: SchedulesState, look: Look) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                SchedulesScreen(state, today, {}, {}, PaddingValues(12.dp), {})
            }
        }
        rule.captureStill("src/test/screenshots/schedules-$name-${look.label}.png")
    }

    @Test fun listStaleDark() = listShot("stale", ready.copy(stale = true), Look(dark = true))

    private fun income(id: String, key: String, name: String?, day: Int, amount: String) =
        ScheduleRow(
            id, null, null, null, key, name, monthly(day), LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, day).let { if (it < today) it.plusMonths(1) else it },
            pesos(amount), false, null, null,
            if (day < 22) ScheduleGroup.Recorded else ScheduleGroup.Due,
            if (day < 22) LocalDate.of(2026, 10, day) else null
        )

    private val incomes = SchedulesState.Ready(
        tab = ScheduleTab.Incomes,
        plan = ScheduleMonthPlan(
            YearMonth.of(2026, 10),
            pesos("4.200.000"),
            1,
            pesos("600.000"),
            1,
            pesos("4.800.000"),
            2
        ),
        rows = listOf(
            income("i1", "salary", null, 15, "4.200.000"),
            income("i2", "other", "Arriendo del local", 28, "600.000")
        ),
        ended = emptyList()
    )

    @Test fun incomesLight() = listShot("incomes", incomes, Look())

    @Test fun incomesDark() = listShot("incomes", incomes, Look(dark = true))

    @Test fun listLoadingLight() = listShot("loading", SchedulesState.Loading, Look())

    @Test fun listLoadingDark() = listShot("loading", SchedulesState.Loading, Look(dark = true))

    @Test fun listFailedLight() = listShot("failed", SchedulesState.Failed, Look())

    @Test fun listFailedDark() = listShot("failed", SchedulesState.Failed, Look(dark = true))

    private val empty = ready.copy(rows = emptyList(), ended = emptyList())

    @Test fun listEmptyLight() = listShot("empty", empty, Look())

    @Test fun listEmptyDark() = listShot("empty", empty, Look(dark = true))

    /** A sum that does not fit an exact amount: no plan, the rows still listed. */
    @Test fun listNoPlanLight() = listShot("no-plan", ready.copy(plan = null), Look())

    @Test
    fun listNoPlanDark() = listShot("no-plan", ready.copy(plan = null), Look(dark = true))

    private val withEnded = ready.copy(
        rows = ready.rows.take(2),
        ended = listOf(
            ScheduleRow(
                "e", "Gimnasio", "Salud", "favorite", null, null, monthly(10),
                LocalDate.of(2026, 1, 10), null, pesos("90.000"), false, null,
                LocalDate.of(2026, 9, 30)
            )
        )
    )

    @Test fun listEndedLight() = listShot("ended", withEnded, Look())

    @Test fun listEndedDark() = listShot("ended", withEnded, Look(dark = true))

    private fun editorShot(name: String, state: ScheduleEditorState, look: Look) {
        rule.showSello(look) { ScheduleEditorScreen(state, dateLabels()) {} }
        rule.captureStill("src/test/screenshots/schedule-editor-$name-${look.label}.png")
    }

    @Test
    fun editorStaleDark() = editorShot("stale", editing.copy(stale = true), Look(dark = true))

    private val incomeEditing = editing.copy(
        income = true,
        amount = pesos("4.200.000"),
        categories = emptyList(),
        categoryId = null,
        sourceKeys = listOf("salary", "freelance", "other"),
        sourceKey = "salary",
        note = "",
        repeat = RepeatUi(RepeatChoice.of(monthly(15), null), LocalDate.of(2026, 11, 15)),
        next = LocalDate.of(2026, 11, 15)
    )

    @Test fun editorIncomeLight() = editorShot("income", incomeEditing, Look())

    @Test fun editorIncomeDark() = editorShot("income", incomeEditing, Look(dark = true))

    @Test
    fun editorLoadingLight() = editorShot("loading", ScheduleEditorState(today = today), Look())

    @Test
    fun editorLoadingDark() =
        editorShot("loading", ScheduleEditorState(today = today), Look(dark = true))

    private val failed = ScheduleEditorState(loading = false, loadFailed = true, today = today)

    @Test fun editorFailedLight() = editorShot("failed", failed, Look())

    @Test fun editorFailedDark() = editorShot("failed", failed, Look(dark = true))

    /** The amount cleared: what is missing is said above the button. */
    private val missing =
        editing.copy(amount = null, missing = ScheduleMissing.Amount)

    @Test fun editorMissingLight() = editorShot("missing", missing, Look())

    @Test fun editorMissingDark() = editorShot("missing", missing, Look(dark = true))

    private val paused = editing.copy(
        pausedFrom = LocalDate.of(2026, 10, 12),
        pausedByArchive = "Hogar",
        next = null
    )

    @Test fun editorPausedLight() = editorShot("paused", paused, Look())

    @Test fun editorPausedDark() = editorShot("paused", paused, Look(dark = true))

    @Test fun editorBusyLight() = editorShot("busy", editing.copy(busy = true), Look())

    @Test
    fun editorBusyDark() = editorShot("busy", editing.copy(busy = true), Look(dark = true))

    private val unknown = editing.copy(error = ScheduleEditorError.Unknown)

    @Test fun editorUnknownLight() = editorShot("unknown", unknown, Look())

    @Test fun editorUnknownDark() = editorShot("unknown", unknown, Look(dark = true))

    private val refused = editing.copy(error = ScheduleEditorError.CatchUpPending)

    @Test fun editorRefusedLight() = editorShot("refused", refused, Look())

    @Test fun editorRefusedDark() = editorShot("refused", refused, Look(dark = true))

    private val delete = editing.copy(confirmDelete = true)

    @Test fun editorDeleteLight() = editorShot("delete", delete, Look())

    @Test fun editorDeleteDark() = editorShot("delete", delete, Look(dark = true))

    private val discard = editing.copy(confirmDiscard = true)

    @Test fun editorDiscardLight() = editorShot("discard", discard, Look())

    @Test fun editorDiscardDark() = editorShot("discard", discard, Look(dark = true))

    private val startPicker = editing.copy(startPickerOpen = true)

    @Test fun editorStartPickerLight() = editorShot("start-picker", startPicker, Look())

    @Test fun editorStartPickerDark() = editorShot("start-picker", startPicker, Look(dark = true))

    private val noteComposer = editing.copy(
        composer = ScheduleComposer.Note,
        composerDraft = "Arriendo apartamento"
    )

    @Test fun editorNoteLight() = editorShot("note", noteComposer, Look())

    @Test fun editorNoteDark() = editorShot("note", noteComposer, Look(dark = true))

    private val otroComposer = incomeEditing.copy(
        sourceKey = "other",
        composer = ScheduleComposer.SourceName,
        composerDraft = "Arriendo del local"
    )

    @Test fun editorOtroLight() = editorShot("otro", otroComposer, Look())

    @Test fun editorOtroDark() = editorShot("otro", otroComposer, Look(dark = true))

    // Se repite's sheet, open over the editor, in each of its kinds.

    private fun sheet(choice: RepeatChoice, next: LocalDate?, endPicker: Boolean = false) =
        editing.copy(
            repeat = editing.repeat.copy(
                sheet = choice,
                sheetNext = next,
                endPickerOpen = endPicker
            )
        )

    private val monthlySheet = sheet(
        RepeatChoice(RepeatKind.Monthly, listOf(5), 1, null),
        LocalDate.of(2026, 11, 5)
    )
    private val twiceSheet = sheet(
        RepeatChoice(RepeatKind.TwiceMonthly, listOf(5, 20), 1, null),
        LocalDate.of(2026, 11, 5)
    )
    private val weeksSheet = sheet(
        RepeatChoice(RepeatKind.Weeks, emptyList(), 2, LocalDate.of(2027, 3, 31)),
        LocalDate.of(2026, 10, 22)
    )
    private val daysSheet = sheet(
        RepeatChoice(RepeatKind.Days, emptyList(), 10, null),
        LocalDate.of(2026, 10, 22)
    )

    /** An end before the first date: nothing would ever be recorded. */
    private val neverSheet = sheet(
        RepeatChoice(RepeatKind.Monthly, listOf(30), 1, LocalDate.of(2026, 10, 25)),
        null
    )
    private val endPickerSheet = sheet(
        RepeatChoice(RepeatKind.Monthly, listOf(5), 1, LocalDate.of(2027, 3, 31)),
        LocalDate.of(2026, 11, 5),
        endPicker = true
    )

    @Test fun repeatMonthlyLight() = editorShot("repeat-monthly", monthlySheet, Look())

    @Test
    fun repeatMonthlyDark() = editorShot("repeat-monthly", monthlySheet, Look(dark = true))

    @Test fun repeatTwiceLight() = editorShot("repeat-twice", twiceSheet, Look())

    @Test fun repeatTwiceDark() = editorShot("repeat-twice", twiceSheet, Look(dark = true))

    @Test fun repeatWeeksLight() = editorShot("repeat-weeks", weeksSheet, Look())

    @Test fun repeatWeeksDark() = editorShot("repeat-weeks", weeksSheet, Look(dark = true))

    @Test fun repeatDaysLight() = editorShot("repeat-days", daysSheet, Look())

    @Test fun repeatDaysDark() = editorShot("repeat-days", daysSheet, Look(dark = true))

    @Test fun repeatNeverLight() = editorShot("repeat-never", neverSheet, Look())

    @Test fun repeatNeverDark() = editorShot("repeat-never", neverSheet, Look(dark = true))

    @Test fun repeatEndPickerLight() = editorShot("repeat-end-picker", endPickerSheet, Look())

    @Test
    fun repeatEndPickerDark() = editorShot("repeat-end-picker", endPickerSheet, Look(dark = true))
}
