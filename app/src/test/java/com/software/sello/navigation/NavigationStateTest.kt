package com.software.sello.navigation

import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.presentation.month.MonthNames
import java.time.YearMonth
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationStateTest {
    private val clock = HandFinancialClock("2026-10-09")
    private val monotonic = HandMonotonicClock()
    private val august = YearMonth.of(2026, 8)
    private val october = YearMonth.of(2026, 10)

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private val categories = HandCategoryReads()

    private fun shell(saved: SavedStateHandle = SavedStateHandle()) =
        ShellViewModel(saved, MonthSession(clock, monotonic), categories)

    /** What the system hands a new process: the same keys and values, new objects. */
    private fun SavedStateHandle.afterProcessDeath() =
        SavedStateHandle(keys().associateWith { get<Any?>(it) })

    @Test
    fun backClosesAPanelThenADetailThenReturnsToReciboThenLeaves() {
        val recibo = ShellTab.Recibo

        assertEquals(BackTarget.ClosePanel, backTarget(panelOpen = true, onDetail = true, recibo))
        assertEquals(BackTarget.ClosePanel, backTarget(panelOpen = true, onDetail = false, recibo))
        assertEquals(BackTarget.LeaveDetail, backTarget(panelOpen = false, onDetail = true, recibo))
        assertEquals(BackTarget.Exit, backTarget(panelOpen = false, onDetail = false, recibo))
    }

    @Test
    fun aDetailGoesBesideItsListOnlyInATwoColumnWindowWideEnoughForBoth() {
        val beside = listOf(760 to 360, 640 to 400, 900 to 700, 1280 to 800, 600 to 479)
        // An upright phone, a medium window, and half a phone's screen: short but narrow.
        val alone = listOf(360 to 760, 700 to 1000, 320 to 372, 599 to 400, 360 to 330)

        assertEquals(beside, beside.filter { (w, h) -> showsDetailBeside(w.dp, h.dp) })
        assertEquals(
            emptyList<Pair<Int, Int>>(),
            alone.filter { (w, h) ->
                showsDetailBeside(w.dp, h.dp)
            }
        )
    }

    @Test
    fun onlyTheWorkingReciboResumenMetasAndInversionesDestinationsAreExposedAsTabs() {
        assertEquals(
            listOf("recibo", "resumen", "metas", "inversiones"),
            ShellTab.entries.map { it.id }
        )
        assertEquals(
            BackTarget.GoToRecibo,
            backTarget(panelOpen = false, onDetail = false, ShellTab.Resumen)
        )
        assertEquals(
            BackTarget.GoToRecibo,
            backTarget(panelOpen = false, onDetail = false, ShellTab.Metas)
        )
    }

    @Test
    fun theFrameStartsOnTheCurrentMonthWithNothingOpenOrPending() {
        assertEquals(
            ShellState(
                currentMonth = october,
                selectedMonth = october,
                monthPickerOpen = false,
                pickerYear = 2026,
                pendingEntry = null
            ),
            shell().state.value
        )
    }

    @Test
    fun pickingAMonthSelectsItAndClosesThePicker() {
        val shell = shell()
        shell.onAction(ShellAction.OpenMonthPicker)
        val open = shell.state.value

        shell.onAction(ShellAction.PickMonth(august))

        assertEquals(true, open.monthPickerOpen)
        assertEquals(august to false, shell.state.value.run { selectedMonth to monthPickerOpen })
    }

    @Test
    fun aFutureMonthIsNotSelectedAndThePickerStaysOpen() {
        val shell = shell()
        shell.onAction(ShellAction.OpenMonthPicker)

        shell.onAction(ShellAction.PickMonth(YearMonth.of(2026, 11)))

        assertEquals(october to true, shell.state.value.run { selectedMonth to monthPickerOpen })
    }

    @Test
    fun thePickerOpensOnTheSelectedMonthsYearAndNeverShowsAFutureYear() {
        val shell = shell()
        shell.onAction(ShellAction.PickMonth(YearMonth.of(2024, 3)))
        shell.onAction(ShellAction.OpenMonthPicker)
        val opened = shell.state.value.pickerYear

        shell.onAction(ShellAction.ShowPickerYear(2025))
        val shown = shell.state.value.pickerYear
        shell.onAction(ShellAction.ShowPickerYear(2027))
        val capped = shell.state.value.pickerYear
        shell.onAction(ShellAction.ShowPickerYear(1900))

        assertEquals(listOf(2024, 2025, 2026), listOf(opened, shown, capped))
        assertEquals(ShellViewModel.EARLIEST_YEAR, shell.state.value.pickerYear)
    }

    @Test
    fun reopeningThePickerStartsAgainOnTheSelectedMonthsYear() {
        val shell = shell()
        shell.onAction(ShellAction.OpenMonthPicker)
        shell.onAction(ShellAction.ShowPickerYear(2023))
        shell.onAction(ShellAction.CloseMonthPicker)

        shell.onAction(ShellAction.OpenMonthPicker)

        assertEquals(2026, shell.state.value.pickerYear)
    }

    @Test
    fun theMonthGridDisablesMonthsAfterTheCurrentOne() {
        val names = MonthNames(
            full = (1..12).map { "Mes$it" },
            short = (1..12).map { "M$it" },
            titleFormat = "%1\$s %2\$d"
        )

        val thisYear = monthCells(2026, october, names)
        val lastYear = monthCells(2025, october, names)

        assertEquals((1..12).map { it <= 10 }, thisYear.map { it.enabled })
        assertEquals(listOf("2026-10"), thisYear.filter { it.isCurrent }.map { it.id })
        assertEquals("M8" to "Mes8 2026", thisYear[7].run { label to spoken })
        assertEquals(List(12) { true }, lastYear.map { it.enabled })
        assertEquals(emptyList<String>(), lastYear.filter { it.isCurrent }.map { it.id })
    }

    @Test
    fun aLongAbsenceResetsTheMonthAndLeavesThePendingEntryAlone() {
        val shell = shell()
        shell.onAction(ShellAction.PickMonth(august))
        shell.onAction(ShellAction.OpenLink("sello://anotar?monto=48700"))
        val entry = shell.state.value.pendingEntry

        shell.onAction(ShellAction.EnteredBackground)
        monotonic.now += 31.minutes
        shell.onAction(ShellAction.ReturnedToForeground)

        assertEquals(october, shell.state.value.selectedMonth)
        assertEquals(entry, shell.state.value.pendingEntry)
        assertEquals(
            (TransactionAmount.of(Money.cop(48_700)) as Outcome.Success).value,
            shell.state.value.pendingEntry?.amount
        )
    }

    @Test
    fun theMonthPickerAndPendingEntryComeBackAfterTheProcessIsKilled() {
        val saved = SavedStateHandle()
        val first = shell(saved)
        first.onAction(ShellAction.PickMonth(august))
        first.onAction(ShellAction.OpenMonthPicker)
        first.onAction(ShellAction.ShowPickerYear(2025))
        first.onAction(ShellAction.OpenLink("sello://anotar?monto=48700"))
        first.onAction(ShellAction.EnteredBackground)
        val before = first.state.value

        monotonic.now += 10.minutes
        val restored = shell(saved.afterProcessDeath())
        restored.onAction(ShellAction.ReturnedToForeground)

        assertEquals(
            ShellState(
                currentMonth = october,
                selectedMonth = august,
                monthPickerOpen = true,
                pickerYear = 2025,
                pendingEntry = before.pendingEntry,
                // There is still no category, and nobody was sent to create one yet.
                entryNeedsCategory = true
            ),
            restored.state.value
        )
    }

    @Test
    fun aProcessRestoredAfterALongAbsenceResetsTheMonthOnly() {
        val saved = SavedStateHandle()
        val first = shell(saved)
        first.onAction(ShellAction.PickMonth(august))
        first.onAction(ShellAction.OpenLink("sello://anotar?monto=48700"))
        first.onAction(ShellAction.EnteredBackground)
        val entry = first.state.value.pendingEntry

        monotonic.now += 31.minutes
        val restored = shell(saved.afterProcessDeath())
        restored.onAction(ShellAction.ReturnedToForeground)

        assertEquals(october, restored.state.value.selectedMonth)
        assertEquals(entry, restored.state.value.pendingEntry)
    }

    @Test
    fun savedValuesThatWereTamperedWithDoNotBecomeARequest() {
        val saved = SavedStateHandle(
            mapOf(
                "shell.session" to true,
                "shell.month" to "2099-12",
                "shell.entry" to 1,
                "shell.entry.category" to "' OR 1=1 --",
                "shell.entry.amount" to -5L
            )
        )

        val state = shell(saved).state.value

        assertEquals(october, state.selectedMonth)
        assertEquals(EntryRequest(null, null), state.pendingEntry)
    }

    @Test
    fun anOpenedCategoryStaysOpenThroughProcessDeathUntilItIsClosed() {
        val saved = SavedStateHandle()
        val shell = shell(saved)
        val id = "00000000-0000-4000-8000-000000000007"

        shell.onAction(ShellAction.OpenCategory(id))
        val restored = shell(saved.afterProcessDeath())
        val reopened = restored.state.value.detailCategoryId
        restored.onAction(ShellAction.CloseCategory)

        assertEquals(id, shell.state.value.detailCategoryId)
        assertEquals(id, reopened)
        assertEquals(null, restored.state.value.detailCategoryId)
    }

    @Test
    fun somethingThatIsNotACategoryIdentifierOpensNothing() {
        val tampered = shell(SavedStateHandle(mapOf("shell.detail" to "' OR 1=1 --")))
        val asked = shell()

        asked.onAction(ShellAction.OpenCategory("../../etc"))

        assertEquals(null, tampered.state.value.detailCategoryId)
        assertEquals(null, asked.state.value.detailCategoryId)
    }

    @Test
    fun anotarFromACategoryStartsTheEntryInThatCategoryAndLeavesItOpen() {
        categories.categories = listOf(categories.category(7, "Mercado"))
        val shell = shell()
        val id = "00000000-0000-4000-8000-000000000007"
        shell.onAction(ShellAction.OpenCategory(id))

        shell.onAction(ShellAction.StartEntryIn(id))
        val asked = shell.state.value
        shell.onAction(ShellAction.EntryTaken)

        assertEquals(true, asked.entryReady)
        assertEquals(id, asked.pendingEntry?.categoryId?.value)
        assertEquals(null, asked.pendingEntry?.amount)
        assertEquals(id, shell.state.value.detailCategoryId)
        assertEquals(null, shell.state.value.pendingEntry)
    }

    @Test
    fun anEntryWithNoCategoryToRecordItInAsksForOneOnceAndKeepsTheRequest() {
        val shell = shell()

        shell.onAction(ShellAction.OpenLink("sello://anotar?monto=48700"))
        val asked = shell.state.value
        shell.onAction(ShellAction.CategoryPrerequisiteShown)

        assertEquals(true, asked.entryNeedsCategory)
        assertEquals(false, shell.state.value.entryNeedsCategory)
        assertEquals(asked.pendingEntry, shell.state.value.pendingEntry)
    }

    @Test
    fun onlyACategoryThatIsNotArchivedCountsForAnEntry() {
        categories.categories = listOf(categories.category(1, "Vieja", archived = true))
        val onlyArchived = shell()
        onlyArchived.onAction(ShellAction.OpenLink("sello://anotar"))

        categories.categories += categories.category(2, "Mercado")
        val withOne = shell()
        withOne.onAction(ShellAction.OpenLink("sello://anotar"))

        assertEquals(true, onlyArchived.state.value.entryNeedsCategory)
        assertEquals(false, withOne.state.value.entryNeedsCategory)
    }

    @Test
    fun aFailedReadOrAForeignLinkSendsNobodyToTheEditor() {
        val shell = shell()
        shell.onAction(ShellAction.OpenLink("https://example.com/anotar"))
        val foreign = shell.state.value.entryNeedsCategory
        categories.failure = StorageFailure.Unavailable("SQLiteException")

        shell.onAction(ShellAction.OpenLink("sello://anotar"))

        assertEquals(false, foreign)
        assertEquals(false, shell.state.value.entryNeedsCategory)
    }

    @Test
    fun anotarOpensTheEntryFormWhenThereIsACategoryAndAsksForOneWhenThereIsNot() {
        val empty = shell()
        empty.onAction(ShellAction.StartEntry)

        categories.categories = listOf(categories.category(1, "Mercado"))
        val withCategory = shell()
        withCategory.onAction(ShellAction.StartEntry)
        val ready = withCategory.state.value
        withCategory.onAction(ShellAction.EntryTaken)

        assertEquals(true to false, empty.state.value.run { entryNeedsCategory to entryReady })
        assertEquals(false to true, ready.entryNeedsCategory to ready.entryReady)
        // Nothing prefilled: the form starts empty.
        assertEquals(EntryRequest(null, null), ready.pendingEntry)
        assertEquals(
            ShellState(october, october, false, 2026, pendingEntry = null),
            withCategory.state.value
        )
    }

    @Test
    fun theMonthChangeReachesTheFrameWhileItIsOpen() {
        val shell = shell()

        clock.goTo("2026-11-01")

        assertEquals(
            YearMonth.of(2026, 11) to YearMonth.of(2026, 11),
            shell.state.value.run { currentMonth to selectedMonth }
        )
    }

    @Test
    fun pickingTheCurrentMonthFromAnotherYearGoesBackClosesThePickerAndFollowsTheClock() {
        val shell = shell()
        shell.onAction(ShellAction.PickMonth(YearMonth.of(2024, 3)))
        shell.onAction(ShellAction.OpenMonthPicker)
        shell.onAction(ShellAction.ShowPickerYear(2021))

        // What "Mes actual" sends: the month the shell itself says is current.
        shell.onAction(ShellAction.PickMonth(shell.state.value.currentMonth))
        val back = shell.state.value
        shell.onAction(ShellAction.OpenMonthPicker)
        val reopened = shell.state.value.pickerYear
        // Not pinned to October: when the month turns, what is shown turns with it.
        clock.goTo("2026-11-01")

        assertEquals(october to false, back.selectedMonth to back.monthPickerOpen)
        assertEquals(2026, reopened)
        assertEquals(YearMonth.of(2026, 11), shell.state.value.selectedMonth)
    }

    @Test
    fun theRibbonsReturnSelectsTheCurrentMonthAndThenFollowsTheClock() {
        val shell = shell()
        shell.onAction(ShellAction.PickMonth(august))

        shell.onAction(ShellAction.ReturnToCurrentMonth)
        val back = shell.state.value.selectedMonth
        clock.goTo("2026-11-01")

        assertEquals(october, back)
        assertEquals(YearMonth.of(2026, 11), shell.state.value.selectedMonth)
    }

    @Test
    fun aReturnTappedAfterMidnightGoesToTheNewMonthNotTheOneShownBefore() {
        val shell = shell()
        shell.onAction(ShellAction.PickMonth(august))
        // The ribbon was drawn while October was current; the month turns before the tap.
        clock.goTo("2026-11-01")

        shell.onAction(ShellAction.ReturnToCurrentMonth)

        assertEquals(
            YearMonth.of(2026, 11) to YearMonth.of(2026, 11),
            shell.state.value.run { currentMonth to selectedMonth }
        )
    }

    @Test
    fun aMonthReturnedFromStaysTheCurrentOneAfterTheProcessIsKilled() {
        val saved = SavedStateHandle()
        val shell = shell(saved)
        shell.onAction(ShellAction.PickMonth(august))
        val past = shell(saved.afterProcessDeath()).state.value.selectedMonth

        shell.onAction(ShellAction.ReturnToCurrentMonth)
        val restored = shell(saved.afterProcessDeath()).state.value.selectedMonth

        assertEquals(august, past)
        assertEquals(october, restored)
    }
}
