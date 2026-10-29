package com.software.sello.feature.recurrence

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The bar on Recibo and Lo que Sello anotó, drawn from state given by hand. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class CatchUpNoticeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)
    private val edited = mutableListOf<Pair<String, Boolean>>()
    private var viewed = 0
    private var retried = 0

    private fun payment(id: String, note: String?, category: String, day: Int, amount: String) =
        RecordedItem(
            id, false, note, category, "home", null, null, LocalDate.of(2026, 10, day),
            pesos(amount)
        )

    private val freelance = RecordedItem(
        "f", true, null, null, null, "other", "Diseño", LocalDate.of(2026, 10, 15),
        pesos("250.000")
    )

    private val recorded = listOf(
        payment("a", "Arriendo", "Hogar", 5, "950.000"),
        freelance,
        payment("g", null, "Gimnasio", 15, "99.900")
    )

    private fun ready(items: List<RecordedItem>) = CatchUpNoticeState.Ready(items, today)

    private fun list(state: CatchUpNoticeState, fontScale: Float = 1f) {
        rule.showSello(Look(fontScale = fontScale)) {
            CatchUpRecordedScreen(
                state,
                PaddingValues(12.dp),
                onRetry = { retried++ },
                onEdit = { id, income -> edited += id to income }
            )
        }
    }

    @Test
    fun theBarSaysWhatWasRecordedAndIsGoneWhenThereIsNothingOrTheReadFailed() {
        var state by mutableStateOf<CatchUpNoticeState>(
            ready(List(4) { payment("b$it", "Bus", "Transporte", 3 + it * 7, "20.000") })
        )
        rule.showSello { Column { CatchUpNoticeBar(state, onTimeout = {}) { viewed++ } } }

        rule.onNodeWithText("Sello anotó 4 pagos fijos").assertIsDisplayed()
        rule.onNodeWithText("Ver").performClick()
        assertEquals(1, viewed)

        state = ready(listOf(freelance))
        rule.onNodeWithText("Sello anotó 1 ingreso fijo").assertIsDisplayed()
        state = ready(recorded)
        rule.onNodeWithText("Sello anotó 3 pagos e ingresos fijos").assertIsDisplayed()

        state = ready(emptyList())
        rule.onNodeWithTag(CATCH_UP_BAR_TAG).assertDoesNotExist()
        state = CatchUpNoticeState.Failed
        rule.onNodeWithTag(CATCH_UP_BAR_TAG).assertDoesNotExist()
        state = CatchUpNoticeState.Loading
        rule.onNodeWithTag(CATCH_UP_BAR_TAG).assertDoesNotExist()
    }

    private var timedOut = 0

    /** The bar alone, with the test's clock held still until it is moved by hand. */
    private fun bar(reduced: Boolean = false, recommendedMillis: Long? = null) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val longer = recommendedMillis?.let { millis ->
                object : AccessibilityManager {
                    override fun calculateRecommendedTimeoutMillis(
                        originalTimeoutMillis: Long,
                        containsIcons: Boolean,
                        containsText: Boolean,
                        containsControls: Boolean
                    ) = millis
                }
            }
            CompositionLocalProvider(LocalAccessibilityManager provides longer) {
                SelloTheme(reducedMotion = reduced) {
                    Column {
                        CatchUpNoticeBar(ready(recorded), onTimeout = { timedOut++ }) { viewed++ }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theBarGoesByItselfAfterFiveSecondsAndVerStillWorksMeanwhile() {
        bar()

        rule.mainClock.advanceTimeBy(4_800)
        rule.onNodeWithText("Sello anotó 3 pagos e ingresos fijos").assertIsDisplayed()
        rule.onNodeWithText("Ver").performClick()
        assertEquals(1, viewed)
        assertEquals(0, timedOut)

        rule.mainClock.advanceTimeBy(400)
        assertEquals(1, timedOut)
    }

    @Test
    fun withReducedMotionItStillGoesAfterFiveSeconds() {
        bar(reduced = true)

        rule.mainClock.advanceTimeBy(4_800)
        assertEquals(0, timedOut)
        rule.mainClock.advanceTimeBy(400)
        assertEquals(1, timedOut)
    }

    @Test
    fun whenTalkBackAsksForMoreTimeTheBarStaysLonger() {
        bar(recommendedMillis = 15_000)

        rule.mainClock.advanceTimeBy(5_200)
        assertEquals(0, timedOut)
        rule.mainClock.advanceTimeBy(10_000)
        assertEquals(1, timedOut)
    }

    @Test
    fun eachRecordedEntryIsOneSentenceThatOpensItsOwnEdit() {
        list(ready(recorded))

        rule.onNodeWithText("Sello anotó estos 3").assertIsDisplayed()
        // Named by note, else source, else category; an income says so.
        rule.onNode(hasContentDescription("Arriendo, 950.000 pesos, Hogar · ", substring = true))
            .performScrollTo().performClick()
        rule.onNode(hasContentDescription("Diseño, 250.000 pesos, Ingreso · ", substring = true))
            .performScrollTo().performClick()
        rule.onNode(hasContentDescription("Gimnasio, 99.900 pesos, Gimnasio · ", substring = true))
            .performScrollTo().performClick()

        assertEquals(listOf("a" to false, "f" to true, "g" to false), edited)
    }

    @Test
    fun oneRecordedEntryIsCalledThis() {
        list(ready(listOf(freelance)))

        rule.onNodeWithText("Sello anotó esto").assertIsDisplayed()
    }

    @Test
    fun aFailedReadSaysSoAndRetriesItNeverShowsNothingRecorded() {
        list(CatchUpNoticeState.Failed)

        rule.onNodeWithText("No pudimos leer lo que Sello anotó").assertIsDisplayed()
        rule.onNodeWithText("Ya no queda nada por revisar").assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun whenEverythingWasDeletedMeanwhileTheListSaysThereIsNothingLeft() {
        list(ready(emptyList()))

        rule.onNodeWithText("Ya no queda nada por revisar").assertIsDisplayed()
    }

    @Test
    fun atTwiceTheTextSizeEveryEntryCanStillBeReachedAndOpened() {
        list(ready(recorded), fontScale = 2f)

        rule.onNodeWithText("Sello anotó estos 3").assertIsDisplayed()
        rule.onNodeWithTag("catch-up.g").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("g" to false), edited)
    }
}
