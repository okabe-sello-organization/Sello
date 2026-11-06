package com.software.sello.feature.recurrence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.carry.CarryChangeGroup
import com.software.sello.presentation.carry.CarryChangeRow
import com.software.sello.presentation.carry.CarryCorrectionList
import com.software.sello.presentation.carry.ChangeKind
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What the catch-up notice looks like, kept as committed images (ADR 0022): the bar and
 * the list of what Sello recorded, in three looks each.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatchUpNoticeScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val recorded = CatchUpNoticeState.Ready(
        listOf(
            RecordedItem(
                "a", false, "Arriendo", "Hogar", "home", null, null, LocalDate.of(2026, 10, 5),
                pesos("950.000")
            ),
            RecordedItem(
                "f", true, null, null, null, "other", "Diseño", LocalDate.of(2026, 10, 15),
                pesos("250.000")
            ),
            RecordedItem(
                "g", false, null, "Gimnasio", "home", null, null, LocalDate.of(2026, 10, 15),
                pesos("99.900")
            )
        ),
        LocalDate.of(2026, 10, 24)
    )

    private fun bar(look: Look) {
        rule.showSello(look) {
            Column { CatchUpNoticeBar(recorded, onTimeout = {}, onView = {}) }
        }
        rule.captureStill("src/test/screenshots/catch-up-bar-${look.label}.png")
    }

    private fun list(look: Look) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                CatchUpRecordedScreen(
                    recorded,
                    PaddingValues(12.dp),
                    onRetry = {},
                    onEdit = { _, _ -> }
                )
            }
        }
        rule.captureStill("src/test/screenshots/catch-up-recorded-${look.label}.png")
    }

    @Test
    fun barCobaltoLight() = bar(Look())

    @Test
    fun barCobaltoDark() = bar(Look(dark = true))

    @Test
    fun barVioletaLight() = bar(Look(ink = SelloInk.Violeta))

    @Test
    fun listCobaltoLight() = list(Look())

    @Test
    fun listCobaltoDark() = list(Look(dark = true))

    @Test
    fun listVioletaLight() = list(Look(ink = SelloInk.Violeta))

    /** As RecurrenceDestinations.kt frames Lo que Sello anotó: its title, back, Listo. */
    private fun framed(name: String, state: CatchUpNoticeState, look: Look) {
        rule.showSello(look) {
            ShellScreen(
                octoberShell,
                monthNames(),
                {},
                navigation = null,
                onBack = {},
                screenTitle = stringResource(R.string.catch_up_title),
                pastMonthRibbon = false,
                dock = SelloDock(
                    label = stringResource(R.string.catch_up_done_description),
                    actionLabel = stringResource(R.string.catch_up_done),
                    onClick = {},
                    icon = SelloIcon.Check
                )
            ) { padding ->
                CatchUpRecordedScreen(state, padding, onRetry = {}, onEdit = { _, _ -> })
            }
        }
        rule.captureStill("src/test/screenshots/catch-up-recorded-$name-${look.label}.png")
    }

    @Test fun loadingLight() = framed("loading", CatchUpNoticeState.Loading, Look())

    @Test fun loadingDark() = framed("loading", CatchUpNoticeState.Loading, Look(dark = true))

    @Test fun failedLight() = framed("failed", CatchUpNoticeState.Failed, Look())

    @Test fun failedDark() = framed("failed", CatchUpNoticeState.Failed, Look(dark = true))

    private val empty = CatchUpNoticeState.Ready(emptyList(), LocalDate.of(2026, 10, 24))

    @Test fun emptyLight() = framed("empty", empty, Look())

    @Test fun emptyDark() = framed("empty", empty, Look(dark = true))

    private val carryFailed = recorded.copy(carry = CatchUpCarry.Failed)

    @Test fun carryFailedLight() = framed("carry-failed", carryFailed, Look())

    @Test fun carryFailedDark() = framed("carry-failed", carryFailed, Look(dark = true))

    /** Arriendo recorded in Hogar, which carries: October's limit with carry 1.200.000 → 250.000. */
    private val withCarry = recorded.copy(
        carry = CatchUpCarry.Changed(
            CarryCorrectionList(
                months = 1,
                groups = listOf(
                    CarryChangeGroup(
                        "Hogar",
                        "home",
                        listOf(
                            CarryChangeRow(
                                month = YearMonth.of(2026, 10),
                                kind = ChangeKind.Edited,
                                before = pesos("1.200.000"),
                                after = pesos("1.200.000"),
                                change = pesos("0"),
                                remainingBefore = pesos("1.200.000"),
                                remainingAfter = pesos("250.000"),
                                verdictBefore = null,
                                verdictAfter = null
                            )
                        )
                    )
                ),
                totalUnchanged = false
            )
        )
    )

    /** The bar's wording when recording changed what carries: Ver lists it. */
    private fun barCarry(look: Look) {
        rule.showSello(look) {
            Column { CatchUpNoticeBar(withCarry, onTimeout = {}, onView = {}) }
        }
        rule.captureStill("src/test/screenshots/catch-up-bar-carry-${look.label}.png")
    }

    @Test fun barCarryLight() = barCarry(Look())

    @Test fun barCarryDark() = barCarry(Look(dark = true))
}
