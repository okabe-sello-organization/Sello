package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The movement marks drawn large, then in two movement rows: Add (+) for an aporte and
 * Remove (−) for a retiro (SELLO-187). Synthetic figures.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MovementMarksScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(dark: Boolean) {
        rule.setContent {
            SelloTheme(darkTheme = dark, reducedMotion = true) {
                Box(
                    Modifier.requiredWidth(412.dp).background(SelloTheme.colors.paper)
                        .padding(16.dp)
                        .testTag("stage")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            listOf(SelloIcon.Add, SelloIcon.Remove, SelloIcon.Insights).forEach {
                                Icon(
                                    it.painter(),
                                    contentDescription = null,
                                    tint = SelloTheme.colors.ink,
                                    modifier = Modifier.size(96.dp)
                                )
                            }
                        }
                        MovementRow(
                            "Aporte",
                            "mié 2 sep",
                            MoneyTextValue(MoneySign.Plus, "", "350.000", "más 350.000 pesos"),
                            icon = SelloIcon.Add,
                            tone = MovementTone.In
                        )
                        MovementRow(
                            "Retiro",
                            "lun 20 oct",
                            MoneyTextValue(MoneySign.Minus, "", "400.000", "menos 400.000 pesos"),
                            icon = SelloIcon.Remove,
                            tone = MovementTone.Out,
                            rule = false
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val theme = if (dark) "oscuro" else "claro"
        rule.onNodeWithTag("stage").drawnBitmap()
            .captureBaseline("src/test/screenshots/movement-marks-$theme.png")
    }

    @Test fun claro() = capture(dark = false)

    @Test fun oscuro() = capture(dark = true)
}
