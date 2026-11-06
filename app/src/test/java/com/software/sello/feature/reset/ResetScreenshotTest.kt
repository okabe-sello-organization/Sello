package com.software.sello.feature.reset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.ResetPreview
import com.software.sello.feature.expense.uuid
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What the reset screen looks like at its first confirmation, kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResetScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val preview = ResetPreview(BackupSummary(3, 42, 1, 0, 0), generation = 1, revision = 9)

    private fun capture(look: Look) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                ResetScreen(ResetState(ResetStep.Ready(preview)), {}, PaddingValues(), {})
            }
        }
        rule.captureStill("src/test/screenshots/reset-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    private val operation = (OperationId.of(uuid(5_002)) as Outcome.Success).value

    private fun shot(name: String, state: ResetState, look: Look) {
        rule.showSello(look) {
            // As ResetDestinations.kt frames Borrar datos.
            ShellScreen(
                octoberShell,
                monthNames(),
                {},
                onBack = {},
                screenTitle = "Borrar datos",
                pastMonthRibbon = false,
                entryDock = false
            ) { padding -> ResetScreen(state, {}, padding, {}) }
        }
        rule.captureStill("src/test/screenshots/reset-$name-${look.label}.png")
    }

    @Test fun countingLight() = shot("counting", ResetState(ResetStep.Counting), Look())

    @Test fun countingDark() = shot("counting", ResetState(ResetStep.Counting), Look(dark = true))

    @Test fun unreadableLight() = shot("unreadable", ResetState(ResetStep.Unreadable), Look())

    @Test fun unreadableDark() = shot(
        "unreadable",
        ResetState(ResetStep.Unreadable),
        Look(dark = true)
    )

    @Test fun refreshedLight() =
        shot("refreshed", ResetState(ResetStep.Ready(preview, refreshed = true)), Look())

    @Test fun refreshedDark() =
        shot("refreshed", ResetState(ResetStep.Ready(preview, refreshed = true)), Look(dark = true))

    @Test fun wordLight() = shot(
        "word",
        ResetState(ResetStep.Ready(preview), confirming = true),
        Look()
    )

    @Test fun wordDark() =
        shot("word", ResetState(ResetStep.Ready(preview), confirming = true), Look(dark = true))

    @Test fun wordTypedLight() = shot(
        "word-typed",
        ResetState(ResetStep.Ready(preview), confirming = true, typed = "BORRAR"),
        Look()
    )

    @Test fun wordTypedDark() = shot(
        "word-typed",
        ResetState(ResetStep.Ready(preview), confirming = true, typed = "BORRAR"),
        Look(dark = true)
    )

    @Test fun erasingLight() = shot(
        "erasing",
        ResetState(ResetStep.Erasing(preview, operation), confirming = true, typed = "BORRAR"),
        Look()
    )

    @Test fun erasingDark() = shot(
        "erasing",
        ResetState(ResetStep.Erasing(preview, operation), confirming = true, typed = "BORRAR"),
        Look(dark = true)
    )

    @Test fun failedLight() = shot("failed", ResetState(ResetStep.Failed), Look())

    @Test fun failedDark() = shot("failed", ResetState(ResetStep.Failed), Look(dark = true))

    @Test fun unknownLight() = shot(
        "unknown",
        ResetState(ResetStep.Unknown(preview, operation)),
        Look()
    )

    @Test fun unknownDark() =
        shot("unknown", ResetState(ResetStep.Unknown(preview, operation)), Look(dark = true))

    @Test fun erasedLight() =
        shot("erased", ResetState(ResetStep.Erased(preferencesPending = false)), Look())

    @Test fun erasedDark() =
        shot("erased", ResetState(ResetStep.Erased(preferencesPending = false)), Look(dark = true))

    @Test fun pendingLight() =
        shot("pending", ResetState(ResetStep.Erased(preferencesPending = true)), Look())

    @Test fun pendingDark() =
        shot("pending", ResetState(ResetStep.Erased(preferencesPending = true)), Look(dark = true))
}
