package com.software.sello.feature.recurrence

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.await
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.composition.viewModels
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.devtools.SandboxGraph
import com.software.sello.devtools.SandboxSession
import com.software.sello.devtools.Scenarios
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ScheduleRecordReads
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.navigation.SelloAppRoot
import com.software.sello.press
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening after a week (recurrence-examples RE10, rule 14; docs/design/v2/fijos.html),
 * on the sandbox's own data and day: what catch-up recorded since the last Listo is on
 * Recibo's bar, each item opens its edit, and Listo clears it for good.
 */
@RunWith(AndroidJUnit4::class)
class CatchUpNoticeJourneyTest {
    @get:Rule
    val rule = createComposeRule()
    private var sandbox: SandboxGraph? = null

    @After
    fun close() {
        sandbox?.erase()
    }

    /** "Una semana fuera" up to its check on 3 oct: two bus rides recorded so far. */
    private fun beforeTheWeekAway(): SandboxGraph {
        val scenario = Scenarios.WeekAway
        val graph = SandboxGraph(
            SandboxSession(
                active = true,
                date = scenario.start,
                zone = scenario.zone,
                scenario = scenario.key
            )
        )
        sandbox = graph
        graph.erase()
        graph.open()
        val runner = checkNotNull(graph.tools?.scenarios)
        runBlocking { repeat(STEPS_TO_THIRD_OCTOBER) { runner.step() } }
        return graph
    }

    /** The screen as it stands, kept in the app's files for the ticket's evidence. */
    private fun capture(name: String) {
        val files = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(files, "catch-up-$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /**
     * Listo closes the list only once what it showed is kept as seen, so Recibo is back
     * only after that. Recibo then reads the notice again off the main thread, and until
     * that read lands it still has the bar it had: wait for it to go rather than look the
     * moment Recibo is back. What was kept is checked against storage at the end.
     * The wait exists only because of issue #205 (Recibo briefly shows the dismissed bar
     * until its re-read lands); once #205 is fixed this check should be immediate again.
     */
    private fun barGone() = rule.waitUntil(15_000) {
        rule.onAllNodes(hasTestTag(CATCH_UP_BAR_TAG), true).fetchSemanticsNodes().isEmpty()
    }

    private fun inBar(text: String) =
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(CATCH_UP_BAR_TAG)), true)

    @Test
    fun openingAfterAWeekShowsWhatWasRecordedUntilListo() {
        val graph = beforeTheWeekAway()
        val runner = checkNotNull(graph.tools?.scenarios)
        rule.setContent {
            SelloTheme(reducedMotion = true) { SelloAppRoot(viewModels(graph.koin)) }
        }

        // The rides of 26 sep and 3 oct were recorded and never looked at.
        rule.awaitTag(CATCH_UP_BAR_TAG)
        inBar("Sello anotó 2 pagos fijos").assertExists()
        inBar("Ver").performClick()
        // The list opens on its placeholder while it reads what was recorded, so the
        // list's own tag says nothing yet: wait for the heading the read brings.
        rule.await(
            hasText("Sello anotó estos 2") and hasAnyAncestor(hasTestTag(CATCH_UP_LIST_TAG))
        )
        rule.onNode(hasText("Listo") and hasAnyAncestor(hasTestTag(SCAFFOLD_DOCK_TAG)), true)
            .assertExists()
        rule.onNodeWithContentDescription(LISTO).press()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        barGone()

        // A week away: on 24 oct catch-up records rent and three rides.
        runBlocking { while (runner.step()) Unit }
        assertEquals(true, runner.state.value.finished)
        rule.awaitTag(CATCH_UP_BAR_TAG)
        inBar("Sello anotó 4 pagos fijos").assertExists()
        capture("bar")
        inBar("Ver").performClick()
        rule.awaitText("Sello anotó estos 4")
        val rent = graph.rows("SELECT id FROM expense WHERE note = 'Arriendo'").single()
        val rides = graph.rows(
            "SELECT id FROM expense WHERE note = 'Bus' AND effective_date > '2026-10-03' " +
                "ORDER BY effective_date"
        )
        (listOf(rent) + rides).forEach { id ->
            rule.onNodeWithTag("catch-up.$id").performScrollTo().assertExists()
        }
        assertEquals(3, rides.size)
        rule.onNodeWithText("Sello anotó estos 4").performScrollTo()
        capture("list")

        // Each opens its edit; back keeps the list and the bar.
        rule.onNodeWithTag("catch-up.$rent").performScrollTo().press()
        rule.awaitText("Guardar cambios")
        Espresso.pressBack()
        rule.awaitTag(CATCH_UP_LIST_TAG)
        rule.onNodeWithContentDescription(LISTO).press()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        barGone()

        // Listo was kept: nothing is left to show the next time Sello opens.
        runBlocking {
            val seen = graph.koin.get<CatchUpNoticePreferences>().observeCatchUpNoticeSeen()
                .first() as PreferenceRead.Ready
            assertEquals(
                Outcome.Success(emptyList<Any>()),
                graph.koin.get<ScheduleRecordReads>().recordedAfter(seen.value)
            )
        }
        assertEquals(6, graph.rows("SELECT id FROM expense").size)
    }
}

/** The dock, read as what it does; it shows "Listo". */
private const val LISTO = "Listo, ya revisé lo que Sello anotó"

/** Two categories, rent, catch-up, the bus, catch-up, 3 oct, catch-up and its check. */
private const val STEPS_TO_THIRD_OCTOBER = 9
