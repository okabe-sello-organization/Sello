package com.software.sello.feature.startup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** What the start-up failure screen looks like, kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupFailureScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(look: Look) {
        rule.showSello(look) { StartupFailureScreen(StartupFailureState(), {}) }
        rule.captureStill("src/test/screenshots/startup-failure-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    private fun shot(name: String, state: StartupFailureState, look: Look) {
        rule.showSello(look) { StartupFailureScreen(state, {}) }
        rule.captureStill("src/test/screenshots/startup-failure-$name-${look.label}.png")
    }

    @Test fun stillFailingLight() =
        shot("still-failing", StartupFailureState(stillFailing = true), Look())

    @Test fun stillFailingDark() =
        shot("still-failing", StartupFailureState(stillFailing = true), Look(dark = true))

    @Test fun notSetAsideLight() = shot(
        "not-set-aside",
        StartupFailureState(notSetAside = true),
        Look()
    )

    @Test fun notSetAsideDark() =
        shot("not-set-aside", StartupFailureState(notSetAside = true), Look(dark = true))

    @Test fun startOverLight() = shot("start-over", StartupFailureState(confirming = true), Look())

    @Test fun startOverDark() =
        shot("start-over", StartupFailureState(confirming = true), Look(dark = true))

    @Test fun startOverTypedLight() =
        shot("start-over-typed", StartupFailureState(confirming = true, typed = "BORRAR"), Look())

    @Test fun startOverTypedDark() = shot(
        "start-over-typed",
        StartupFailureState(confirming = true, typed = "BORRAR"),
        Look(dark = true)
    )

    @Test fun checkingLight() =
        shot("checking", StartupFailureState(restore = StartupRestoreStep.Checking), Look())

    @Test fun checkingDark() = shot(
        "checking",
        StartupFailureState(restore = StartupRestoreStep.Checking),
        Look(dark = true)
    )

    @Test fun rejectedLight() = shot(
        "rejected",
        StartupFailureState(restore = StartupRestoreStep.Rejected(RestoreRejection.Damaged)),
        Look()
    )

    @Test fun rejectedDark() = shot(
        "rejected",
        StartupFailureState(restore = StartupRestoreStep.Rejected(RestoreRejection.Damaged)),
        Look(dark = true)
    )

    @Test fun interruptedLight() =
        shot("interrupted", StartupFailureState(restore = StartupRestoreStep.Interrupted), Look())

    @Test fun interruptedDark() = shot(
        "interrupted",
        StartupFailureState(restore = StartupRestoreStep.Interrupted),
        Look(dark = true)
    )

    @Test fun restoredLight() = shot(
        "restored",
        StartupFailureState(
            restore = StartupRestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(7, 96, 5, 1, 2, 1),
                    PendingPreferences(null, null)
                )
            )
        ),
        Look()
    )

    @Test fun restoredDark() = shot(
        "restored",
        StartupFailureState(
            restore = StartupRestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(7, 96, 5, 1, 2, 1),
                    PendingPreferences(null, null)
                )
            )
        ),
        Look(dark = true)
    )
}
