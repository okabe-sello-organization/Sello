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
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatUi
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Pagos fijos and a schedule's editor look like, kept as committed images (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+xhdpi")
class ScheduleScreensScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

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

    private fun editor(look: Look) {
        rule.showSello(look) { ScheduleEditorScreen(editing, dateLabels()) {} }
        rule.captureStill("src/test/screenshots/schedule-editor-${look.label}.png")
    }

    @Test
    fun listCobaltoLight() = list(Look())

    @Test
    fun listCobaltoDarkAtTwiceTheFontSize() = list(Look(dark = true, fontScale = 2f))

    @Test
    fun listVioletaLight() = list(Look(ink = SelloInk.Violeta))

    @Test
    fun editorCobaltoLight() = editor(Look())

    @Test
    fun editorCobaltoDarkAtTwiceTheFontSize() = editor(Look(dark = true, fontScale = 2f))

    @Test
    fun editorVioletaLight() = editor(Look(ink = SelloInk.Violeta))
}
