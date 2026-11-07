package com.software.sello.feature.movements

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.port.MovementFilter
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

private fun plus(digits: String) = MoneyTextValue(MoneySign.Plus, "", digits, "más $digits pesos")

private fun day(date: String, spent: String?, vararg rows: DayRow) =
    DayGroup(LocalDate.parse(date), spent?.let(::plain), rows.toList())

private fun expense(
    id: String,
    note: String?,
    category: String,
    icon: String,
    amount: String,
    fixed: Boolean = false
) = DayRow.Spent(id, note, plain(amount), category, icon, fixed, archived = false)

/** The mock's October, synthetic: today the 6th. */
internal val dayByDayReady = DayByDayState.Ready(
    month = YearMonth.of(2026, 10),
    today = LocalDate.of(2026, 10, 6),
    filter = MovementFilter.All,
    spent = plain("1.240.300"),
    income = plain("3.150.000"),
    days = listOf(
        day(
            "2026-10-06",
            "28.950",
            expense("1", "Almuerzo", "Alimentación", "restaurant", "18.000"),
            expense("2", "Bus", "Transporte", "directions_bus", "2.950"),
            expense("3", null, "Café", "local_cafe", "8.000")
        ),
        day(
            "2026-10-05",
            "1.196.400",
            expense("4", "Arriendo", "Hogar", "home", "1.100.000", fixed = true),
            expense("5", "Mercado D1", "Alimentación", "shopping_cart", "96.400")
        ),
        day(
            "2026-10-03",
            null,
            DayRow.Received("6", "Salario", plus("2.500.000"), "salary", null)
        ),
        day(
            "2026-10-01",
            "15.000",
            DayRow.Received("7", "Proyecto web", plus("650.000"), "freelance", null),
            expense("8", "Rappi, café", "Café", "local_cafe", "15.000")
        )
    ),
    totalCount = 8,
    more = false
)

/** What Día a día looks like in every state, light and dark (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayByDayScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    /** As MovementDestinations.kt frames it: the title over the month, no dock. */
    private fun capture(name: String, state: DayByDayState, look: Look) {
        rule.showSello(look) {
            ShellScreen(
                octoberShell,
                monthNames(),
                {},
                onBack = {},
                screenTitle = stringResource(R.string.day_by_day_title),
                showMonthAsSubtitle = true,
                entryDock = false
            ) { padding ->
                DayByDayScreen(state, {}, {}, {}, {}, padding)
            }
        }
        rule.captureStill("src/test/screenshots/day-by-day-$name-${look.label}.png")
    }

    private val empty = dayByDayReady.copy(days = emptyList(), totalCount = 0)
    private val noIncomes =
        dayByDayReady.copy(filter = MovementFilter.Incomes, days = emptyList(), totalCount = 0)
    private val more = dayByDayReady.copy(more = true, totalCount = 48)
    private val pageFailed = more.copy(pageFailed = true)
    private val stale = dayByDayReady.copy(stale = true)
    private val failed = DayByDayState.Failed(DayByDayProblem.Unread)
    private val tooLarge = DayByDayState.Failed(DayByDayProblem.TooLarge)

    @Test fun readyClaro() = capture("ready", dayByDayReady, Look())

    @Test fun readyOscuro() = capture("ready", dayByDayReady, Look(dark = true))

    @Test fun moreClaro() = capture("more", more, Look())

    @Test fun moreOscuro() = capture("more", more, Look(dark = true))

    @Test fun emptyClaro() = capture("empty", empty, Look())

    @Test fun emptyOscuro() = capture("empty", empty, Look(dark = true))

    /** A chip that finds nothing keeps the chips, so another can be picked. */
    @Test fun noIncomesClaro() = capture("no-incomes", noIncomes, Look())

    @Test fun noIncomesOscuro() = capture("no-incomes", noIncomes, Look(dark = true))

    @Test fun loadingClaro() = capture("loading", DayByDayState.Loading, Look())

    @Test fun loadingOscuro() = capture("loading", DayByDayState.Loading, Look(dark = true))

    @Test fun failedClaro() = capture("failed", failed, Look())

    @Test fun failedOscuro() = capture("failed", failed, Look(dark = true))

    @Test fun tooLargeClaro() = capture("too-large", tooLarge, Look())

    @Test fun tooLargeOscuro() = capture("too-large", tooLarge, Look(dark = true))

    @Test fun staleClaro() = capture("stale", stale, Look())

    @Test fun staleOscuro() = capture("stale", stale, Look(dark = true))

    @Test fun pageFailedClaro() = capture("page-failed", pageFailed, Look())

    @Test fun pageFailedOscuro() = capture("page-failed", pageFailed, Look(dark = true))
}
