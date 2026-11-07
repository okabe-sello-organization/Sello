package com.software.sello.feature.expense

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.REPEAT_ROW_TAG
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.carry.CarryChangeGroup
import com.software.sello.presentation.carry.CarryChangeRow
import com.software.sello.presentation.carry.CarryCorrectionList
import com.software.sello.presentation.carry.ChangeKind
import com.software.sello.presentation.carry.VerdictWord
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.recurrence.FollowingSchedule
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatKind
import com.software.sello.presentation.recurrence.RepeatUi
import com.software.sello.revealed
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the form looks like, kept as committed images (ADR 0022). The host gate compares
 * each capture with its baseline in `src/test/screenshots/`; a visual change re-records
 * them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpenseEntryScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 9)

    /** An amount typed and a category chosen: the form ready to record. */
    private val ready = ExpenseEntryState(
        loading = false,
        categories = listOf(
            EntryCategory("a", "Alimentación", "restaurant"),
            EntryCategory("b", "Café", "local_cafe")
        ),
        categoryId = "a",
        amount = MoneyTextValue(MoneySign.None, "", "48.700", "48.700 pesos"),
        missing = null,
        date = today,
        today = today
    )

    private fun capture(look: Look) {
        rule.showSello(look) { ExpenseEntryScreen(ready, dateLabels(), {}) }
        rule.captureStill("src/test/screenshots/expense-entry-${look.label}.png")
    }

    /** Opened where Anotar varios is reachable: the Varios pill in the bar. */
    private fun captureVarios(look: Look, label: String) {
        rule.showSello(look) { ExpenseEntryScreen(ready, dateLabels(), {}, severalOffered = true) }
        rule.captureStill("src/test/screenshots/expense-entry-varios-$label.png")
    }

    @Test
    fun variosClaro() = captureVarios(Look(), "claro")

    @Test
    fun variosOscuro() = captureVarios(Look(dark = true), "oscuro")

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** A refresh failed after the categories were read: the draft stays under the notice. */
    private fun stale(look: Look) {
        val look = look
        rule.showSello(look) { ExpenseEntryScreen(ready.copy(stale = true), dateLabels(), {}) }
        rule.captureStill("src/test/screenshots/expense-entry-stale-${look.label}.png")
    }

    @Test
    fun staleLight() = stale(Look())

    @Test
    fun staleDark() = stale(Look(dark = true))

    /** Correcting, after a deletion whose outcome is unknown: only checking again is offered. */
    private fun deleteUnknown(look: Look) {
        val look = look
        val unknown = ready.copy(
            editing = true,
            missing = Missing.Changes,
            error = EntryError.DeleteUnknown
        )
        rule.showSello(look) { ExpenseEntryScreen(unknown, dateLabels(), {}) }
        rule.captureStill("src/test/screenshots/expense-entry-delete-unknown-${look.label}.png")
    }

    @Test
    fun deleteUnknownLight() = deleteUnknown(Look())

    @Test
    fun deleteUnknownDark() = deleteUnknown(Look(dark = true))

    private fun money(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun shot(
        name: String,
        state: ExpenseEntryState,
        look: Look,
        reveal: String? = null,
        announcement: Boolean = false,
        announcementBusy: Boolean = false,
        announcementFailed: Boolean = false
    ) {
        rule.showSello(look) {
            ExpenseEntryScreen(
                state,
                dateLabels(),
                {},
                onIncome = {}.takeIf { !state.editing },
                showPastExpenseAnnouncement = announcement,
                announcementBusy = announcementBusy,
                announcementFailed = announcementFailed
            )
        }
        if (reveal != null) rule.onNodeWithTag(reveal).revealed()
        rule.captureStill("src/test/screenshots/expense-entry-$name-${look.label}.png")
    }

    private val loading = ExpenseEntryState(date = today, today = today)

    @Test fun loadingLight() = shot("loading", loading, Look())

    @Test fun loadingDark() = shot("loading", loading, Look(dark = true))

    private val failed = loading.copy(loading = false, loadFailed = true)

    @Test fun failedLight() = shot("failed", failed, Look())

    @Test fun failedDark() = shot("failed", failed, Look(dark = true))

    @Test fun busyLight() = shot("busy", ready.copy(busy = true), Look())

    @Test fun busyDark() = shot("busy", ready.copy(busy = true), Look(dark = true))

    private val unknown = ready.copy(error = EntryError.Unknown)

    @Test fun unknownLight() = shot("unknown", unknown, Look())

    @Test fun unknownDark() = shot("unknown", unknown, Look(dark = true))

    /** The date became one Sello cannot take: refused, nothing saved. */
    private val refused = ready.copy(error = EntryError.NotSaved)

    @Test fun refusedLight() = shot("refused", refused, Look())

    @Test fun refusedDark() = shot("refused", refused, Look(dark = true))

    private val missing = ready.copy(categoryId = null, missing = Missing.Category)

    @Test fun missingLight() = shot("missing", missing, Look())

    @Test fun missingDark() = shot("missing", missing, Look(dark = true))

    /** Thirteen digits pasted: kept as pasted, with why. */
    private val tooLarge = ready.copy(
        amount = null,
        amountText = "1.000.000.000.000",
        amountProblem = AmountProblem.TooLarge,
        missing = Missing.Amount
    )

    @Test fun amountRefusedLight() = shot("amount-refused", tooLarge, Look())

    @Test fun amountRefusedDark() = shot("amount-refused", tooLarge, Look(dark = true))

    /** Correcting an expense of August: the past months it falls in are said. */
    private val past = ready.copy(
        editing = true,
        missing = null,
        date = LocalDate.of(2026, 8, 14),
        pastMonths = listOf(YearMonth.of(2026, 8))
    )

    @Test fun pastMonthLight() = shot("past-month", past, Look())

    @Test fun pastMonthDark() = shot("past-month", past, Look(dark = true))

    @Test fun announcementLight() = shot("announcement", past, Look(), announcement = true)

    @Test
    fun announcementDark() = shot("announcement", past, Look(dark = true), announcement = true)

    @Test
    fun announcementBusyLight() =
        shot("announcement-busy", past, Look(), announcement = true, announcementBusy = true)

    @Test
    fun announcementBusyDark() = shot(
        "announcement-busy",
        past,
        Look(dark = true),
        announcement = true,
        announcementBusy = true
    )

    @Test
    fun announcementFailedLight() =
        shot("announcement-failed", past, Look(), announcement = true, announcementFailed = true)

    @Test
    fun announcementFailedDark() = shot(
        "announcement-failed",
        past,
        Look(dark = true),
        announcement = true,
        announcementFailed = true
    )

    /** "Editar gasto" with a change made: ready to save. */
    private val editing = ready.copy(editing = true, note = "Mercado", missing = null)

    @Test fun editingLight() = shot("editing", editing, Look())

    @Test fun editingDark() = shot("editing", editing, Look(dark = true))

    /** A schedule's later payments were changed; saving this one was refused. */
    private val followingSaved =
        editing.copy(error = EntryError.NotSaved, followingSaved = true)

    @Test
    fun followingSavedLight() = shot("following-saved", followingSaved, Look(), ENTRY_ERROR_TAG)

    @Test
    fun followingSavedDark() =
        shot("following-saved", followingSaved, Look(dark = true), ENTRY_ERROR_TAG)

    /** A new expense: the "Gasto | Ingreso" switch at the top and Se repite's row. */
    @Test fun switchLight() = shot("switch", ready, Look(), reveal = REPEAT_ROW_TAG)

    @Test fun switchDark() = shot("switch", ready, Look(dark = true), reveal = REPEAT_ROW_TAG)

    private val repeating = ready.copy(
        repeat = RepeatUi(
            RepeatChoice(RepeatKind.Monthly, listOf(9), 1, null),
            LocalDate.of(2026, 11, 9)
        )
    )

    @Test fun repeatOnLight() = shot("repeat-on", repeating, Look(), reveal = REPEAT_ROW_TAG)

    @Test
    fun repeatOnDark() = shot("repeat-on", repeating, Look(dark = true), reveal = REPEAT_ROW_TAG)

    private val sheet = ready.copy(
        repeat = RepeatUi(
            sheet = RepeatChoice(RepeatKind.Monthly, listOf(9), 1, null),
            sheetNext = LocalDate.of(2026, 10, 9)
        )
    )

    @Test fun repeatSheetLight() = shot("repeat-sheet", sheet, Look())

    @Test fun repeatSheetDark() = shot("repeat-sheet", sheet, Look(dark = true))

    @Test fun discardLight() = shot("discard", ready.copy(confirmDiscard = true), Look())

    @Test
    fun discardDark() = shot("discard", ready.copy(confirmDiscard = true), Look(dark = true))

    @Test fun datePickerLight() = shot("date-picker", ready.copy(datePickerOpen = true), Look())

    @Test
    fun datePickerDark() = shot("date-picker", ready.copy(datePickerOpen = true), Look(dark = true))

    private val composing = ready.copy(noteDraft = "Mercado de la semana", noteDraftLength = 20)

    @Test fun noteLight() = shot("note", composing, Look())

    @Test fun noteDark() = shot("note", composing, Look(dark = true))

    /** Moving the expense to August changes the limit with carry of two later months. */
    private val correcting = past.copy(
        correction = EntryCorrection(
            CarryCorrectionList(
                months = 2,
                groups = listOf(
                    CarryChangeGroup(
                        "Alimentación",
                        "restaurant",
                        listOf(
                            CarryChangeRow(
                                YearMonth.of(2026, 9), ChangeKind.Later, money("420.000"),
                                money(
                                    "371.300"
                                ),
                                MoneyTextValue(
                                    MoneySign.Minus,
                                    "",
                                    "48.700",
                                    "menos 48.700 pesos"
                                ),
                                money(
                                    "20.000"
                                ),
                                MoneyTextValue(
                                    MoneySign.Minus,
                                    "",
                                    "28.700",
                                    "menos 28.700 pesos"
                                ),
                                VerdictWord.WithinLimit, VerdictWord.Over
                            ),
                            CarryChangeRow(
                                YearMonth.of(2026, 10), ChangeKind.Later, money("500.000"),
                                money(
                                    "451.300"
                                ),
                                MoneyTextValue(
                                    MoneySign.Minus,
                                    "",
                                    "48.700",
                                    "menos 48.700 pesos"
                                ),
                                money("300.000"), money("251.300"), null, null
                            )
                        )
                    )
                ),
                totalUnchanged = false
            ),
            changed = false
        )
    )

    @Test fun correctionLight() = shot("correction", correcting, Look())

    @Test fun correctionDark() = shot("correction", correcting, Look(dark = true))

    /** A schedule's payment changed: whether the following ones change too. */
    private val following = editing.copy(
        askFollowing = true,
        following = FollowingSchedule(
            "Arriendo",
            (RepeatRule.Monthly.of(5) as Outcome.Success).value,
            LocalDate.of(2026, 1, 5)
        )
    )

    @Test fun followingLight() = shot("following", following, Look())

    @Test fun followingDark() = shot("following", following, Look(dark = true))

    @Test fun followingBusyLight() = shot("following-busy", following.copy(busy = true), Look())

    @Test
    fun followingBusyDark() = shot("following-busy", following.copy(busy = true), Look(dark = true))

    // --- Frequent expenses (SELLO-199), as the approved mock draws them ---

    private fun frequent(
        category: String,
        note: String,
        name: String,
        icon: String,
        count: Int,
        digits: String
    ) = EntryFrequent(category, note.lowercase(), note, name, icon, count, money(digits))

    private val lunch = frequent("a", "Almuerzo", "Alimentación", "restaurant", 12, "18.000")
    private val often = listOf(
        lunch,
        frequent("t", "Bus", "Transporte", "directions_bus", 22, "2.950"),
        frequent("b", "Tinto", "Café", "local_cafe", 9, "3.500"),
        frequent("a", "Mercado D1", "Alimentación", "restaurant", 4, "96.400"),
        frequent("h", "Recarga", "Hogar", "home", 3, "20.000")
    )

    /**
     * ⚡ Frecuentes at the end of Agregar nota, the form as it opens. Every frequent
     * expense is in a category the form offers, with that category's icon, as the view
     * model gives them.
     */
    private val offered = ready.copy(
        categories = ready.categories + listOf(
            EntryCategory("t", "Transporte", "directions_bus"),
            EntryCategory("h", "Hogar", "home")
        ),
        categoryId = null,
        amount = null,
        missing = Missing.Amount,
        frequent = often
    )

    @Test fun frequentButtonLight() = shot("frequent-button", offered, Look())

    @Test fun frequentButtonDark() = shot("frequent-button", offered, Look(dark = true))

    @Test
    fun frequentSheetLight() = shot("frequent-sheet", offered.copy(frequentOpen = true), Look())

    @Test
    fun frequentSheetDark() =
        shot("frequent-sheet", offered.copy(frequentOpen = true), Look(dark = true))

    /** After tapping Almuerzo: category, note and usual amount filled; nothing saved. */
    private val filled = offered.copy(
        categoryId = "a",
        amount = money("18.000"),
        note = "Almuerzo",
        missing = null
    )

    @Test fun frequentFilledLight() = shot("frequent-filled", filled, Look())

    @Test fun frequentFilledDark() = shot("frequent-filled", filled, Look(dark = true))

    /** Typing "alm" offers the notes that start that way, with their usual amounts. */
    private val suggesting = offered.copy(
        categoryId = "a",
        amount = money("48.700"),
        missing = null,
        noteDraft = "alm",
        noteDraftLength = 3,
        noteSuggestions = listOf(
            lunch,
            frequent("a", "Almuerzo con Ana", "Alimentación", "restaurant", 1, "42.000")
        )
    )

    @Test fun frequentSuggestionsLight() = shot("frequent-suggestions", suggesting, Look())

    @Test
    fun frequentSuggestionsDark() = shot("frequent-suggestions", suggesting, Look(dark = true))
}
