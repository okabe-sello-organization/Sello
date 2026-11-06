package com.software.sello.feature.category

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.presentation.carry.CarryChangeGroup
import com.software.sello.presentation.carry.CarryChangeRow
import com.software.sello.presentation.carry.CarryCorrectionList
import com.software.sello.presentation.carry.ChangeKind
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.revealed
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the form to change a category looks like, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryEditorScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    /** "Café", limit 100.000, opened from October with a name typed over it. */
    private val editing = CategoryEditorState(
        loading = false,
        iconKeys = CategoryIcons.keys,
        iconKey = "local_cafe",
        suggestions = listOf(
            LimitSuggestion(200_000, "200.000", "200.000 pesos"),
            LimitSuggestion(300_000, "300.000", "300.000 pesos"),
            LimitSuggestion(500_000, "500.000", "500.000 pesos")
        ),
        editing = true,
        name = "Cafetería",
        changed = true,
        limitText = "100000",
        limitAmount = MoneyTextValue(MoneySign.None, "", "100.000", "100.000 pesos"),
        targetMonth = YearMonth.of(2026, 10)
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            CategoryEditorScreen(editing, {})
        }
        rule.captureStill("src/test/screenshots/category-editor-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** SELLO-154: October ended before the limit was saved; the form reads November. */
    private fun aLimitRefusedBecauseItsMonthEnded(look: Look) {
        val refused = editing.copy(
            name = "Café",
            limitText = "150000",
            limitAmount = MoneyTextValue(MoneySign.None, "", "150.000", "150.000 pesos"),
            targetMonth = YearMonth.of(2026, 11),
            saveError = SaveError.MonthEnded,
            monthEnded = MonthEnded(YearMonth.of(2026, 10), carry = false)
        )
        rule.showSello(look) {
            CategoryEditorScreen(refused, {})
        }
        // The message sits under the limit, below the first screenful.
        rule.onNodeWithTag(EDITOR_SAVE_ERROR_TAG).performScrollTo()
        rule.captureStill("src/test/screenshots/category-editor-month-ended-${look.label}.png")
    }

    @Test
    fun aLimitRefusedBecauseItsMonthEndedLight() = aLimitRefusedBecauseItsMonthEnded(Look())

    @Test
    fun aLimitRefusedBecauseItsMonthEndedDark() =
        aLimitRefusedBecauseItsMonthEnded(Look(dark = true))

    /** A 13-digit limit pasted over it: kept as pasted, with why it cannot be set. */
    private fun aLimitAboveTheMaximum(look: Look) {
        val pasted = editing.copy(
            limitText = "1.000.000.000.000",
            limitAmount = MoneyTextValue(
                MoneySign.None,
                "",
                "1.000.000.000.000",
                "1.000.000.000.000 pesos"
            ),
            limitError = LimitError.TooLarge
        )
        rule.showSello(look) {
            CategoryEditorScreen(pasted, {})
        }
        rule.captureStill("src/test/screenshots/category-editor-too-large-${look.label}.png")
    }

    @Test
    fun aLimitAboveTheMaximumLight() = aLimitAboveTheMaximum(Look())

    @Test
    fun aLimitAboveTheMaximumDark() = aLimitAboveTheMaximum(Look(dark = true))

    /** [tap] opens what it names; the save error is only scrolled into view. */
    private fun shot(name: String, state: CategoryEditorState, look: Look, tap: String? = null) {
        rule.showSello(look) { CategoryEditorScreen(state, {}) }
        when (tap) {
            null -> Unit
            EDITOR_SAVE_ERROR_TAG -> rule.onNodeWithTag(tap).revealed()
            else -> rule.onNodeWithTag(tap).revealed().performClick()
        }
        rule.captureStill("src/test/screenshots/category-editor-$name-${look.label}.png")
    }

    /** Nueva categoría as it opens: nothing typed, the first free icon, the suggestions. */
    private val fresh = CategoryEditorState(
        loading = false,
        iconKeys = CategoryIcons.keys,
        iconKey = "restaurant",
        usedIconKeys = setOf("home", "directions_bus"),
        suggestions = editing.suggestions,
        targetMonth = YearMonth.of(2026, 10)
    )

    @Test fun newLight() = shot("new", fresh, Look())

    @Test fun newDark() = shot("new", fresh, Look(dark = true))

    /** Opened because Anotar needs a category first. */
    @Test fun forEntryLight() = shot("for-entry", fresh.copy(forEntry = true), Look())

    @Test
    fun forEntryDark() = shot("for-entry", fresh.copy(forEntry = true), Look(dark = true))

    @Test fun loadingLight() = shot("loading", CategoryEditorState(), Look())

    @Test fun loadingDark() = shot("loading", CategoryEditorState(), Look(dark = true))

    private val failed = CategoryEditorState(loading = false, loadFailed = true, editing = true)

    @Test fun loadFailedLight() = shot("load-failed", failed, Look())

    @Test fun loadFailedDark() = shot("load-failed", failed, Look(dark = true))

    private val taken = fresh.copy(name = "Hogar", nameError = NameError.Taken)

    @Test fun nameTakenLight() = shot("name-taken", taken, Look())

    @Test fun nameTakenDark() = shot("name-taken", taken, Look(dark = true))

    @Test fun busyLight() = shot("busy", editing.copy(busy = true), Look())

    @Test fun busyDark() = shot("busy", editing.copy(busy = true), Look(dark = true))

    private val unknown = editing.copy(saveError = SaveError.Unknown)

    @Test fun unknownLight() = shot("unknown", unknown, Look(), EDITOR_SAVE_ERROR_TAG)

    @Test fun unknownDark() = shot("unknown", unknown, Look(dark = true), EDITOR_SAVE_ERROR_TAG)

    private val notSaved = editing.copy(saveError = SaveError.NotSaved)

    @Test fun notSavedLight() = shot("not-saved", notSaved, Look(), EDITOR_SAVE_ERROR_TAG)

    @Test
    fun notSavedDark() = shot("not-saved", notSaved, Look(dark = true), EDITOR_SAVE_ERROR_TAG)

    private val conflict = editing.copy(saveError = SaveError.Conflict)

    @Test fun conflictLight() = shot("conflict", conflict, Look(), EDITOR_SAVE_ERROR_TAG)

    @Test
    fun conflictDark() = shot("conflict", conflict, Look(dark = true), EDITOR_SAVE_ERROR_TAG)

    /** A month that ended: only its own limit changes. */
    private val past = editing.copy(targetMonth = YearMonth.of(2026, 8), historical = true)

    @Test fun pastMonthLight() = shot("past-month", past, Look())

    @Test fun pastMonthDark() = shot("past-month", past, Look(dark = true))

    @Test fun carryUnreadableLight() =
        shot("carry-unreadable", editing.copy(carry = CarryCard.Unreadable), Look())

    @Test
    fun carryUnreadableDark() =
        shot("carry-unreadable", editing.copy(carry = CarryCard.Unreadable), Look(dark = true))

    /** SELLO-188: the month cannot be read, and the draft still turns carry off. */
    private val unreadablePending = editing.copy(
        changed = true,
        carry = CarryCard.Unreadable,
        carryPending = CarryPending(on = false, month = YearMonth.of(2026, 10), forgets = null)
    )

    @Test fun carryUnreadablePendingLight() = shot(
        "carry-unreadable-pending",
        unreadablePending,
        Look()
    )

    @Test
    fun carryUnreadablePendingDark() =
        shot("carry-unreadable-pending", unreadablePending, Look(dark = true))

    @Test fun iconSheetLight() = shot("icon-sheet", fresh, Look(), EDITOR_ICON_TAG)

    @Test fun iconSheetDark() = shot("icon-sheet", fresh, Look(dark = true), EDITOR_ICON_TAG)

    @Test fun discardLight() = shot("discard", editing.copy(confirmDiscard = true), Look())

    @Test
    fun discardDark() = shot("discard", editing.copy(confirmDiscard = true), Look(dark = true))

    private val archive = editing.copy(
        changed = false,
        archiveQuestion = ArchiveQuestion(
            unarchive = false,
            expensesThisMonth = 4,
            currentMonth = YearMonth.of(2026, 10),
            resumesBudget = false,
            discardsChanges = false
        )
    )

    @Test fun archiveLight() = shot("archive", archive, Look())

    @Test fun archiveDark() = shot("archive", archive, Look(dark = true))

    private val unarchive = editing.copy(
        changed = false,
        archived = true,
        limitLocked = true,
        archiveQuestion = ArchiveQuestion(
            unarchive = true,
            expensesThisMonth = null,
            currentMonth = YearMonth.of(2026, 10),
            resumesBudget = true,
            discardsChanges = false
        )
    )

    @Test fun unarchiveLight() = shot("unarchive", unarchive, Look())

    @Test fun unarchiveDark() = shot("unarchive", unarchive, Look(dark = true))

    private val carryOn = editing.copy(
        changed = false,
        carry = CarryCard.Off(YearMonth.of(2026, 10)),
        carryQuestion = CarryQuestion.Enable(
            YearMonth.of(2026, 10),
            MoneyTextValue(MoneySign.None, "", "40.000", "40.000 pesos")
        )
    )

    @Test fun carryOnLight() = shot("carry-on", carryOn, Look())

    @Test fun carryOnDark() = shot("carry-on", carryOn, Look(dark = true))

    // ---------- Field errors, refusals and the carry slips (category.html) ----------

    /** Guardar pressed with nothing typed: each field says what it needs. */
    private val missing = fresh.copy(
        name = "",
        nameError = NameError.Required,
        limitText = "",
        limitError = LimitError.Required
    )

    @Test fun missingLight() = shot("missing", missing, Look())

    @Test fun missingDark() = shot("missing", missing, Look(dark = true))

    /** A pasted name past 40 characters and a pasted limit that is not an amount. */
    private val tooLong = editing.copy(
        name = "Cafetería de la esquina y panadería del barrio",
        nameError = NameError.TooLong,
        limitText = "cien mil",
        limitAmount = null,
        limitError = LimitError.NotAnAmount
    )

    @Test fun tooLongLight() = shot("too-long", tooLong, Look())

    @Test fun tooLongDark() = shot("too-long", tooLong, Look(dark = true))

    private val notAllowed = editing.copy(name = "Café\u0007", nameError = NameError.NotAllowed)

    @Test fun notAllowedLight() = shot("not-allowed", notAllowed, Look())

    @Test fun notAllowedDark() = shot("not-allowed", notAllowed, Look(dark = true))

    /** Archiving waited for a fixed payment that is due and could not be recorded first. */
    private val pending = editing.copy(saveError = SaveError.PaymentsPending)

    @Test
    fun paymentsPendingLight() = shot("payments-pending", pending, Look(), EDITOR_SAVE_ERROR_TAG)

    @Test
    fun paymentsPendingDark() =
        shot("payments-pending", pending, Look(dark = true), EDITOR_SAVE_ERROR_TAG)

    /** October ended before carry was turned on: the form reads November. */
    private val monthEndedCarry = editing.copy(
        targetMonth = YearMonth.of(2026, 11),
        saveError = SaveError.MonthEnded,
        monthEnded = MonthEnded(YearMonth.of(2026, 10), carry = true)
    )

    @Test
    fun monthEndedCarryLight() =
        shot("month-ended-carry", monthEndedCarry, Look(), EDITOR_SAVE_ERROR_TAG)

    @Test
    fun monthEndedCarryDark() =
        shot("month-ended-carry", monthEndedCarry, Look(dark = true), EDITOR_SAVE_ERROR_TAG)

    private fun plainPesos(digits: String) =
        MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun plusPesos(digits: String) =
        MoneyTextValue(MoneySign.Plus, "", digits, "más $digits pesos")

    /**
     * Cafetería carries from August: its August limit from 100.000 to 150.000 adds 50.000 to
     * September's and October's limits with carry, 120.000 → 170.000, and to what is left
     * (20.000 → 70.000 in September, 60.000 → 110.000 in October).
     */
    private fun row(month: Int, left: String, leftAfter: String) = CarryChangeRow(
        month = YearMonth.of(2026, month),
        kind = ChangeKind.Later,
        before = plainPesos("120.000"),
        after = plainPesos("170.000"),
        change = plusPesos("50.000"),
        remainingBefore = plainPesos(left),
        remainingAfter = plainPesos(leftAfter),
        verdictBefore = null,
        verdictAfter = null
    )

    private fun correction(changed: Boolean) = CarryQuestion.Correction(
        month = YearMonth.of(2026, 8),
        before = plainPesos("100.000"),
        after = plainPesos("150.000"),
        list = CarryCorrectionList(
            months = 2,
            groups = listOf(
                CarryChangeGroup(
                    "Cafetería",
                    "local_cafe",
                    listOf(row(9, "20.000", "70.000"), row(10, "60.000", "110.000"))
                )
            ),
            totalUnchanged = false
        ),
        changed = changed
    )

    private val asked = past.copy(
        limitText = "150000",
        limitAmount = plainPesos("150.000"),
        carryQuestion = correction(changed = false)
    )

    @Test fun pastCorrectionLight() = shot("past-correction", asked, Look())

    @Test fun pastCorrectionDark() = shot("past-correction", asked, Look(dark = true))

    private val askedAgain = asked.copy(carryQuestion = correction(changed = true))

    @Test fun pastCorrectionChangedLight() = shot("past-correction-changed", askedAgain, Look())

    @Test
    fun pastCorrectionChangedDark() = shot("past-correction-changed", askedAgain, Look(dark = true))

    @Test
    fun pastCorrectionBusyLight() = shot("past-correction-busy", asked.copy(busy = true), Look())

    @Test
    fun pastCorrectionBusyDark() =
        shot("past-correction-busy", asked.copy(busy = true), Look(dark = true))
}
