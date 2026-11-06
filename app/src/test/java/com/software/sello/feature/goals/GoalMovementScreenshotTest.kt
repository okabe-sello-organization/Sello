package com.software.sello.feature.goals

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Aportar a una meta looks like, kept as committed images (ADR 0022)
 * in all four themes: Cobalto and Violeta, light and dark. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalMovementScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 23)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val aporte = GoalMovementState(
        loading = false,
        amount = plain("350.000"),
        goals = listOf(
            GoalChoice("a", "Viaje a la costa", "flight", 57, plain("1.710.000")),
            GoalChoice("b", "Celular nuevo", "phone_iphone", 37, plain("450.000"))
        ),
        goalId = "a",
        date = today,
        today = today
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            GoalMovementScreen(aporte, severalOffered = true) {}
        }
        rule.captureStill("src/test/screenshots/goal-movement-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    @Test
    fun violetaDark() = capture(Look(ink = SelloInk.Violeta, dark = true))

    /** A save whose outcome is unknown: only checking again is offered. */
    private fun captureUnknown(look: Look) {
        rule.showSello(look) {
            GoalMovementScreen(aporte.copy(error = GoalMovementError.Unknown)) {}
        }
        rule.captureStill("src/test/screenshots/goal-movement-unknown-${look.label}.png")
    }

    @Test
    fun unknownCobaltoLight() = captureUnknown(Look())

    @Test
    fun unknownCobaltoDark() = captureUnknown(Look(dark = true))

    @Test
    fun unknownVioletaLight() = captureUnknown(Look(ink = SelloInk.Violeta))

    @Test
    fun unknownVioletaDark() = captureUnknown(Look(ink = SelloInk.Violeta, dark = true))

    private fun shot(name: String, state: GoalMovementState, look: Look) {
        rule.showSello(look) { GoalMovementScreen(state) {} }
        rule.captureStill("src/test/screenshots/goal-movement-$name-${look.label}.png")
    }

    private val retiro = aporte.copy(withdrawal = true, amount = plain("200.000"))

    @Test fun retiroLight() = shot("retiro", retiro, Look())

    @Test fun retiroDark() = shot("retiro", retiro, Look(dark = true))

    /** Correcting an aporte already saved, with a change made. */
    private val correcting = aporte.copy(editing = true, changed = true, note = "Prima")

    @Test fun correctingLight() = shot("correcting", correcting, Look())

    @Test fun correctingDark() = shot("correcting", correcting, Look(dark = true))

    private val loading = GoalMovementState(date = today, today = today)

    @Test fun loadingLight() = shot("loading", loading, Look())

    @Test fun loadingDark() = shot("loading", loading, Look(dark = true))

    private val failed = loading.copy(loading = false, loadFailed = true)

    @Test fun failedLight() = shot("failed", failed, Look())

    @Test fun failedDark() = shot("failed", failed, Look(dark = true))

    private val noGoals = loading.copy(loading = false)

    @Test fun noGoalsLight() = shot("no-goals", noGoals, Look())

    @Test fun noGoalsDark() = shot("no-goals", noGoals, Look(dark = true))

    /** A retiro of more than the goal holds. */
    private val over = retiro.copy(amount = plain("2.000.000"), overSaved = plain("1.710.000"))

    @Test fun overLight() = shot("over", over, Look())

    @Test fun overDark() = shot("over", over, Look(dark = true))

    @Test fun busyLight() = shot("busy", aporte.copy(busy = true), Look())

    @Test fun busyDark() = shot("busy", aporte.copy(busy = true), Look(dark = true))

    private val refused = aporte.copy(error = GoalMovementError.NotSaved)

    @Test fun refusedLight() = shot("refused", refused, Look())

    @Test fun refusedDark() = shot("refused", refused, Look(dark = true))

    @Test fun discardLight() = shot("discard", aporte.copy(confirmDiscard = true), Look())

    @Test
    fun discardDark() = shot("discard", aporte.copy(confirmDiscard = true), Look(dark = true))

    @Test fun datePickerLight() = shot("date-picker", aporte.copy(datePickerOpen = true), Look())

    @Test
    fun datePickerDark() =
        shot("date-picker", aporte.copy(datePickerOpen = true), Look(dark = true))

    private val composing = aporte.copy(
        composerOpen = true,
        composerDraft = "Prima",
        composerLength = 5
    )

    @Test fun noteLight() = shot("note", composing, Look())

    @Test fun noteDark() = shot("note", composing, Look(dark = true))

    /** Twelve digits typed: the most an amount takes, said under it. */
    private val atLimit = aporte.copy(amount = plain("999.999.999.999"), atLimit = true)

    @Test fun atLimitLight() = shot("at-limit", atLimit, Look())

    @Test fun atLimitDark() = shot("at-limit", atLimit, Look(dark = true))

    /** Text pasted that is not a whole amount: kept as pasted, with why. */
    private val pasted = aporte.copy(amount = null, amountText = "350.000,50")

    @Test fun pastedLight() = shot("pasted", pasted, Look())

    @Test fun pastedDark() = shot("pasted", pasted, Look(dark = true))

    /** Correcting this aporte down would leave the retiro of 400.000 on 20 oct short. */
    private val blocked = correcting.copy(
        amount = plain("100.000"),
        blockedBy = BlockingWithdrawal(plain("400.000"), LocalDate.of(2026, 10, 20))
    )

    @Test fun blockedLight() = shot("blocked", blocked, Look())

    @Test fun blockedDark() = shot("blocked", blocked, Look(dark = true))
}
