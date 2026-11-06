package com.software.sello.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.BandChip
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the frame looks like, kept as committed images (ADR 0022): the month bar, the dock
 * and the ribbon over a past month, and the month picker open.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun state(pickerOpen: Boolean) = ShellState(
        currentMonth = YearMonth.of(2026, 10),
        selectedMonth = YearMonth.of(2026, 9),
        monthPickerOpen = pickerOpen,
        pickerYear = 2026,
        pendingEntry = null
    )

    private fun capture(look: Look, name: String, pickerOpen: Boolean) {
        rule.showSello(look) {
            ShellScreen(
                state(pickerOpen),
                monthNames(),
                {},
                navigation = SelloNavigation(
                    listOf(
                        SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                        SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                    ),
                    "recibo"
                ) {}
            ) { padding ->
                Text("contenido", Modifier.padding(padding), color = SelloTheme.colors.ink)
            }
        }
        rule.captureStill("src/test/screenshots/shell-$name-${look.label}.png")
    }

    @Test
    fun pastMonthCobaltoLight() = capture(Look(), "past-month", pickerOpen = false)

    @Test
    fun pastMonthCobaltoDark() = capture(Look(dark = true), "past-month", pickerOpen = false)

    @Test
    fun pastMonthVioletaLight() =
        capture(Look(ink = SelloInk.Violeta), "past-month", pickerOpen = false)

    @Test
    fun pickerCobaltoLight() = capture(Look(), "picker", pickerOpen = true)

    @Test
    fun pickerCobaltoDark() = capture(Look(dark = true), "picker", pickerOpen = true)

    @Test
    fun pickerVioletaLight() = capture(Look(ink = SelloInk.Violeta), "picker", pickerOpen = true)

    private val tabs = SelloNavigation(
        listOf(
            SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
            SelloDestination("resumen", "Resumen", SelloIcon.Insights),
            SelloDestination("metas", "Metas", SelloIcon.Flag)
        ),
        "recibo"
    ) {}

    private val october = ShellState(
        currentMonth = YearMonth.of(2026, 10),
        selectedMonth = YearMonth.of(2026, 10),
        monthPickerOpen = false,
        pickerYear = 2026,
        pendingEntry = null
    )

    /** A frame as [frame] draws it, with a stand-in for the content, captured as [name]. */
    private fun frame(
        name: String,
        look: Look,
        frame: @Composable (@Composable (PaddingValues) -> Unit) -> Unit
    ) {
        rule.showSello(look) {
            frame { padding ->
                Text("contenido", Modifier.padding(padding), color = SelloTheme.colors.ink)
            }
        }
        rule.captureStill("src/test/screenshots/shell-$name-${look.label}.png")
    }

    private fun currentMonth(look: Look) = frame("current-month", look) { content ->
        ShellScreen(october, monthNames(), {}, navigation = tabs, content = content)
    }

    @Test fun currentMonthLight() = currentMonth(Look())

    @Test fun currentMonthDark() = currentMonth(Look(dark = true))

    /** Metas: the title, "Nueva meta" beside it and the green dock. */
    private fun titled(look: Look) = frame("titled", look) { content ->
        ShellScreen(
            october,
            monthNames(),
            {},
            navigation = tabs,
            titleOnly = "Metas",
            titleChip = { compact -> BandChip("Nueva meta", {}, compact = compact) },
            entryDock = false,
            dock = SelloDock(
                "Aportar a una meta",
                "Aportar a una meta",
                {},
                tone = ButtonTone.Gain,
                icon = SelloIcon.Flag
            ),
            content = content
        )
    }

    @Test fun titledLight() = titled(Look())

    @Test fun titledDark() = titled(Look(dark = true))

    /** A pushed screen's title row in one of its four forms. */
    private fun pushed(name: String, look: Look, dock: SelloDock? = null, form: String) =
        frame(name, look) { content ->
            when (form) {
                "screen-title" -> ShellScreen(
                    october,
                    monthNames(),
                    {},
                    onBack = {},
                    screenTitle = "Configuración",
                    pastMonthRibbon = false,
                    content = content
                )

                "title-label" -> ShellScreen(
                    october,
                    monthNames(),
                    {},
                    onBack = {},
                    titleLabel = "Ingresos de octubre",
                    content = content
                )

                "subtitle" -> ShellScreen(
                    october,
                    monthNames(),
                    {},
                    onBack = {},
                    screenTitle = "Todos los gastos",
                    showMonthAsSubtitle = true,
                    dock = dock,
                    content = content
                )

                else -> ShellScreen(
                    october,
                    monthNames(),
                    {},
                    onBack = {},
                    titleOnly = "Pagos e ingresos fijos",
                    entryDock = false,
                    content = content
                )
            }
        }

    @Test fun screenTitleLight() = pushed("screen-title", Look(), form = "screen-title")

    @Test fun screenTitleDark() = pushed("screen-title", Look(dark = true), form = "screen-title")

    @Test fun titleLabelLight() = pushed("title-label", Look(), form = "title-label")

    @Test fun titleLabelDark() = pushed("title-label", Look(dark = true), form = "title-label")

    @Test fun subtitleLight() = pushed("subtitle", Look(), form = "subtitle")

    @Test fun subtitleDark() = pushed("subtitle", Look(dark = true), form = "subtitle")

    @Test fun titleOnlyLight() = pushed("title-only", Look(), form = "title-only")

    @Test fun titleOnlyDark() = pushed("title-only", Look(dark = true), form = "title-only")

    private fun docked(name: String, look: Look, archived: Boolean, known: Boolean) {
        rule.showSello(look) {
            ShellScreen(
                october,
                monthNames(),
                {},
                onBack = {},
                screenTitle = "Comida",
                showMonthAsSubtitle = true,
                dock = categoryDock(archived, known) {}
            ) { padding ->
                Text("contenido", Modifier.padding(padding), color = SelloTheme.colors.ink)
            }
        }
        rule.captureStill("src/test/screenshots/shell-$name-${look.label}.png")
    }

    @Test fun archivedDockLight() = docked("archived-dock", Look(), archived = true, known = true)

    @Test
    fun archivedDockDark() =
        docked("archived-dock", Look(dark = true), archived = true, known = true)

    @Test fun waitingDockLight() = docked("waiting-dock", Look(), archived = false, known = false)

    @Test
    fun waitingDockDark() =
        docked("waiting-dock", Look(dark = true), archived = false, known = false)

    /** Recibo with Comida opened: on a phone the detail takes the frame, with its own title row. */
    private fun detail(name: String, look: Look) = frame(name, look) { content ->
        ShellScreen(
            october.copy(detailCategoryId = "c1"),
            monthNames(),
            {},
            navigation = tabs,
            detail = ShellDetail("Comida") { padding ->
                Text("detalle de Comida", Modifier.padding(padding), color = SelloTheme.colors.ink)
            },
            content = content
        )
    }

    @Test fun detailLight() = detail("detail", Look())

    @Test fun detailDark() = detail("detail", Look(dark = true))

    /** Landscape: the rail instead of the tab bar, and the detail beside Recibo. */
    @Test
    @Config(qualifiers = "w891dp-h412dp-mdpi")
    fun railBesideLight() = detail("rail-beside", Look())

    @Test
    @Config(qualifiers = "w891dp-h412dp-mdpi")
    fun railBesideDark() = detail("rail-beside", Look(dark = true))

    /** A large window: the month picker opens as a side panel. */
    private fun sidePanel(look: Look) = frame("side-panel", look) { content ->
        ShellScreen(
            october.copy(selectedMonth = YearMonth.of(2026, 9), monthPickerOpen = true),
            monthNames(),
            {},
            navigation = tabs,
            content = content
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun sidePanelLight() = sidePanel(Look())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun sidePanelDark() = sidePanel(Look(dark = true))

    /** The picker at the earliest year it offers: no way further back. */
    private fun earliest(look: Look) = frame("picker-earliest", look) { content ->
        ShellScreen(
            october.copy(
                selectedMonth = YearMonth.of(2000, 3),
                monthPickerOpen = true,
                pickerYear = ShellViewModel.EARLIEST_YEAR
            ),
            monthNames(),
            {},
            navigation = tabs,
            content = content
        )
    }

    @Test fun pickerEarliestLight() = earliest(Look())

    @Test fun pickerEarliestDark() = earliest(Look(dark = true))
}
