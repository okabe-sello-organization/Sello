package com.software.sello.feature.income

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.recurrence.FollowingSchedule
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatKind
import com.software.sello.presentation.recurrence.RepeatUi
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the income form looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IncomeFormScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 13)

    /** An amount typed and a source chosen: the form ready to record. */
    private val ready = IncomeFormState(
        loading = false,
        amount = MoneyTextValue(MoneySign.None, "", "5.000.000", "5.000.000 pesos"),
        sourceKeys = listOf("salary", "freelance", "other"),
        sourceKey = "salary",
        date = today,
        today = today,
        missing = null
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            IncomeFormScreen(ready, {})
        }
        rule.captureStill("src/test/screenshots/income-form-${look.label}.png")
    }

    /** A refresh failed after the month was read: the draft stays under the notice. */
    private fun stale(look: Look) {
        val look = look
        rule.showSello(look) { IncomeFormScreen(ready.copy(stale = true), {}) }
        rule.captureStill("src/test/screenshots/income-form-stale-${look.label}.png")
    }

    @Test
    fun staleLight() = stale(Look())

    @Test
    fun staleDark() = stale(Look(dark = true))

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** Opened where Anotar varios is reachable: the Varios pill in the bar. */
    private fun captureVarios(look: Look, label: String) {
        rule.showSello(look) { IncomeFormScreen(ready, {}, severalOffered = true) }
        rule.captureStill("src/test/screenshots/income-form-varios-$label.png")
    }

    @Test
    fun variosClaro() = captureVarios(Look(), "claro")

    @Test
    fun variosOscuro() = captureVarios(Look(dark = true), "oscuro")

    private fun shot(name: String, state: IncomeFormState, look: Look) {
        rule.showSello(look) { IncomeFormScreen(state, {}) }
        rule.captureStill("src/test/screenshots/income-form-$name-${look.label}.png")
    }

    private val loading = IncomeFormState(date = today, today = today)

    @Test fun loadingLight() = shot("loading", loading, Look())

    @Test fun loadingDark() = shot("loading", loading, Look(dark = true))

    private val failed = loading.copy(loading = false, loadFailed = true)

    @Test fun failedLight() = shot("failed", failed, Look())

    @Test fun failedDark() = shot("failed", failed, Look(dark = true))

    @Test fun busyLight() = shot("busy", ready.copy(busy = true), Look())

    @Test fun busyDark() = shot("busy", ready.copy(busy = true), Look(dark = true))

    private val unknown = ready.copy(error = IncomeFormError.Unknown)

    @Test fun unknownLight() = shot("unknown", unknown, Look())

    @Test fun unknownDark() = shot("unknown", unknown, Look(dark = true))

    private val refused = ready.copy(error = IncomeFormError.NotSaved)

    @Test fun refusedLight() = shot("refused", refused, Look())

    @Test fun refusedDark() = shot("refused", refused, Look(dark = true))

    /** Otro chosen and not named yet: its name line and what is missing. */
    private val otro = ready.copy(sourceKey = "other", missing = IncomeMissing.SourceName)

    @Test fun otroLight() = shot("otro", otro, Look())

    @Test fun otroDark() = shot("otro", otro, Look(dark = true))

    private val tooLarge = ready.copy(
        amount = null,
        amountText = "1.000.000.000.000",
        amountProblem = IncomeAmountProblem.TooLarge,
        missing = IncomeMissing.Amount
    )

    @Test fun amountRefusedLight() = shot("amount-refused", tooLarge, Look())

    @Test fun amountRefusedDark() = shot("amount-refused", tooLarge, Look(dark = true))

    /** The data was replaced meanwhile (a restore or a reset): nothing more can be sent. */
    private val replaced = ready.copy(generationChanged = true)

    @Test fun replacedLight() = shot("replaced", replaced, Look())

    @Test fun replacedDark() = shot("replaced", replaced, Look(dark = true))

    /** "Editar ingreso" of August, with the past months it falls in. */
    private val editing = ready.copy(
        editing = true,
        note = "Quincena",
        date = LocalDate.of(2026, 8, 14),
        pastMonths = listOf(YearMonth.of(2026, 8))
    )

    @Test fun editingLight() = shot("editing", editing, Look())

    @Test fun editingDark() = shot("editing", editing, Look(dark = true))

    @Test fun discardLight() = shot("discard", ready.copy(confirmDiscard = true), Look())

    @Test
    fun discardDark() = shot("discard", ready.copy(confirmDiscard = true), Look(dark = true))

    @Test fun datePickerLight() = shot("date-picker", ready.copy(datePickerOpen = true), Look())

    @Test
    fun datePickerDark() = shot("date-picker", ready.copy(datePickerOpen = true), Look(dark = true))

    private val composing = ready.copy(
        composer = IncomeComposer.Note,
        composerDraft = "Quincena de octubre",
        composerLength = 19
    )

    @Test fun noteLight() = shot("note", composing, Look())

    @Test fun noteDark() = shot("note", composing, Look(dark = true))

    private val sheet = ready.copy(
        repeat = RepeatUi(
            sheet = RepeatChoice(RepeatKind.TwiceMonthly, listOf(15, 30), 1, null),
            sheetNext = LocalDate.of(2026, 10, 15)
        )
    )

    @Test fun repeatSheetLight() = shot("repeat-sheet", sheet, Look())

    @Test fun repeatSheetDark() = shot("repeat-sheet", sheet, Look(dark = true))

    private val following = editing.copy(
        askFollowing = true,
        following = FollowingSchedule(
            null,
            (RepeatRule.Monthly.of(15) as Outcome.Success).value,
            LocalDate.of(2026, 1, 15),
            "salary"
        )
    )

    @Test fun followingLight() = shot("following", following, Look())

    @Test fun followingDark() = shot("following", following, Look(dark = true))
}
