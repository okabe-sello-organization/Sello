package com.software.sello.feature.guide

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The Guía de Sello in its frame, kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(name: String, look: Look, page: GuidePage?) {
        rule.showSello(look) {
            ShellScreen(
                state = octoberShell,
                names = monthNames(),
                onAction = {},
                onBack = {},
                titleOnly = stringResource(page?.title ?: R.string.guide_title),
                pastMonthRibbon = false,
                entryDock = false
            ) { padding ->
                if (page == null) {
                    GuideScreen(padding, onOpen = {})
                } else {
                    GuidePageScreen(page, padding)
                }
            }
        }
        rule.captureStill("src/test/screenshots/guide-$name-${look.label}.png")
    }

    @Test fun listLight() = capture("list", Look(), null)

    @Test fun listDark() = capture("list", Look(dark = true), null)

    @Test fun reciboPageLight() = capture("recibo", Look(), GuidePage.Recibo)

    @Test fun reciboPageDark() = capture("recibo", Look(dark = true), GuidePage.Recibo)
}
