package com.software.sello.feature.movements

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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

private val icons = mapOf("Alimentación" to "restaurant", "Café" to "local_cafe", "Hogar" to "home")

/** An expense a search found, on [date], with its category's icon. */
private fun hit(date: String, id: String, note: String, category: String, amount: String) =
    FoundRow(LocalDate.parse(date), expense(id, note, category, icons.getValue(category), amount))

/** The mock's «rappi» search, synthetic: nine expenses over three months. */
internal val foundRappi = SearchState.Ready(
    words = "rappi",
    amount = null,
    today = LocalDate.of(2026, 10, 6),
    filter = MovementFilter.All,
    expenses = FoundTotal(9, plain("214.800")),
    incomes = null,
    months = listOf(
        FoundMonth(
            YearMonth.of(2026, 10),
            3,
            plain("61.200"),
            listOf(
                hit("2026-10-03", "11", "Rappi, almuerzo", "Alimentación", "20.600"),
                hit("2026-10-01", "8", "Rappi, café", "Café", "15.000"),
                hit("2026-10-01", "12", "RAPPI cena", "Alimentación", "25.600")
            )
        ),
        FoundMonth(
            YearMonth.of(2026, 9),
            4,
            plain("98.300"),
            listOf(
                hit("2026-09-27", "13", "Rappi, almuerzo", "Alimentación", "22.100"),
                hit("2026-09-19", "14", "Rappí mercado", "Alimentación", "41.200"),
                hit("2026-09-10", "15", "Rappi, desayuno", "Café", "17.000"),
                hit("2026-09-02", "16", "Rappi, almuerzo", "Alimentación", "18.000")
            )
        ),
        FoundMonth(
            YearMonth.of(2026, 8),
            2,
            plain("55.300"),
            listOf(
                hit("2026-08-12", "17", "Rappi, almuerzo", "Alimentación", "27.900"),
                hit("2026-08-05", "18", "Rappi, cena", "Alimentación", "27.400")
            )
        )
    ),
    more = false
)

/** The mock's «18.000»: the exact amount, an income among the expenses. */
internal val foundAmount = SearchState.Ready(
    words = "18.000",
    amount = plain("18.000"),
    today = LocalDate.of(2026, 10, 6),
    filter = MovementFilter.All,
    expenses = FoundTotal(14, plain("252.000")),
    incomes = FoundTotal(1, plus("18.000")),
    months = listOf(
        FoundMonth(
            YearMonth.of(2026, 10),
            2,
            plain("36.000"),
            listOf(
                hit("2026-10-06", "1", "Almuerzo", "Alimentación", "18.000"),
                hit("2026-10-04", "21", "Almuerzo", "Alimentación", "18.000")
            )
        ),
        FoundMonth(
            YearMonth.of(2026, 9),
            6,
            plain("90.000"),
            listOf(
                hit("2026-09-30", "22", "Almuerzo", "Alimentación", "18.000"),
                FoundRow(
                    LocalDate.of(2026, 9, 20),
                    DayRow.Received("23", "Reembolso", plus("18.000"), "transfer", null)
                ),
                hit("2026-09-13", "24", "Plomero", "Hogar", "18.000")
            )
        )
    ),
    more = true
)

/** What Día a día looks like in every state, light and dark (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayByDayScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * As MovementDestinations.kt frames it: the title over the month, no dock. [atEnd]
     * scrolls to the foot of the list first, where a failed page is said.
     */
    private fun capture(
        name: String,
        state: DayByDayState,
        look: Look,
        atEnd: Boolean = false,
        query: String = "",
        found: SearchState = SearchState.Off
    ) {
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
                DayByDayScreen(state, query, found, {}, {}, {}, {}, padding)
            }
        }
        if (atEnd) rule.onNodeWithTag("dayByDay.pageFailed").performScrollTo()
        rule.captureStill("src/test/screenshots/day-by-day-$name-${look.label}.png")
    }

    private val empty = dayByDayReady.copy(days = emptyList(), totalCount = 0)

    /** A month with expenses and no income: Entró is 0, as the app reads it. */
    private val noIncomes = dayByDayReady.copy(
        filter = MovementFilter.Incomes,
        income = plain("0"),
        days = emptyList(),
        totalCount = 0
    )
    private val more = dayByDayReady.copy(more = true, totalCount = 48)
    private val pageFailed = more.copy(pageFailure = DayByDayProblem.Unread)
    private val pageTooLarge =
        more.copy(pageFailure = DayByDayProblem.TooLarge, more = false)
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

    @Test fun pageFailedClaro() = capture("page-failed", pageFailed, Look(), atEnd = true)

    @Test
    fun pageFailedOscuro() = capture("page-failed", pageFailed, Look(dark = true), atEnd = true)

    /** A day too large after rows were listed: said as such, with no Reintentar. */
    @Test fun pageTooLargeClaro() = capture("page-too-large", pageTooLarge, Look(), atEnd = true)

    @Test
    fun pageTooLargeOscuro() =
        capture("page-too-large", pageTooLarge, Look(dark = true), atEnd = true)

    private val notFound = foundRappi.copy(
        words = "plomería",
        expenses = null,
        months = emptyList()
    )
    private val searchFailed =
        SearchState.Failed("rappi", MovementFilter.All, DayByDayProblem.Unread)

    private fun search(name: String, query: String, found: SearchState, look: Look) =
        capture("search-$name", dayByDayReady, look, query = query, found = found)

    @Test fun searchWordsClaro() = search("words", "rappi", foundRappi, Look())

    @Test fun searchWordsOscuro() = search("words", "rappi", foundRappi, Look(dark = true))

    @Test fun searchAmountClaro() = search("amount", "18.000", foundAmount, Look())

    @Test fun searchAmountOscuro() = search("amount", "18.000", foundAmount, Look(dark = true))

    @Test fun searchNoneClaro() = search("none", "plomería", notFound, Look())

    @Test fun searchNoneOscuro() = search("none", "plomería", notFound, Look(dark = true))

    /** A failed read keeps the query in the field and offers Reintentar. */
    @Test fun searchFailedClaro() = search("failed", "rappi", searchFailed, Look())

    @Test fun searchFailedOscuro() = search("failed", "rappi", searchFailed, Look(dark = true))
}
