package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedbackInteractionTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun assertTouchTarget(text: String, scheme: String) {
        val height = rule.onNodeWithText(text).getUnclippedBoundsInRoot().height
        assertTrue("$scheme: $text is $height tall", height >= 48.dp)
    }

    @Test
    fun retryInvokesExactlyItsCallbackOncePerPress() {
        var retries = 0
        rule.inEveryScheme(
            reset = { retries = 0 },
            content = { ErrorSlip("No pudimos leer tus gastos.", onRetry = { retries++ }) }
        ) {
            rule.onNodeWithText("No se pudo cargar").assertIsDisplayed()
            rule.onNodeWithText("No pudimos leer tus gastos.").assertIsDisplayed()
            rule.onNodeWithText("Reintentar").assert(isButton).performClick()
            assertEquals(it, 1, retries)
            assertTouchTarget("Reintentar", it)
        }
    }

    @Test
    fun emptySlipOffersItsActionOnlyWhenThereIsOne() {
        var actions = 0
        rule.inEveryScheme(
            reset = { actions = 0 },
            content = {
                Column {
                    EmptySlip(
                        "Aún no hay gastos",
                        "Anota el primero.",
                        SelloIcon.ReceiptLong,
                        Modifier,
                        "Anotar"
                    ) {
                        actions++
                    }
                    EmptySlip(
                        "Sin movimientos",
                        "Nada que mostrar.",
                        SelloIcon.Insights,
                        Modifier.testTag("bare")
                    )
                }
            }
        ) {
            rule.onNodeWithText("Anotar").assert(isButton).performClick()
            assertEquals(it, 1, actions)
            assertTouchTarget("Anotar", it)
            assertEquals(it, 1, rule.onAllNodes(isButton).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun stateSlipsCentreTheirTextAndKeepTheIconSilent() {
        var retries = 0
        rule.inEveryScheme(
            reset = { retries = 0 },
            content = {
                Column {
                    EmptySlip(
                        "Dale forma a tu mes",
                        "Crea una categoría.",
                        SelloIcon.ReceiptLong,
                        Modifier.testTag("empty")
                    )
                    ErrorSlip(
                        "Tus registros siguen guardados.",
                        { retries++ },
                        Modifier.testTag("error"),
                        title = "No pudimos leer tu mes",
                        icon = SelloIcon.Error
                    )
                }
            }
        ) {
            listOf(
                "empty" to "Dale forma a tu mes",
                "error" to "No pudimos leer tu mes"
            ).forEach { (tag, title) ->
                val slip = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                val heading = rule.onNodeWithText(title).getUnclippedBoundsInRoot()
                val slipMiddle = (slip.left + slip.right) / 2
                val headingMiddle = (heading.left + heading.right) / 2
                assertTrue(it, (slipMiddle - headingMiddle).value in -1f..1f)
            }
            // The icons are decorative: the only described nodes are none.
            assertEquals(
                it,
                0,
                rule.onAllNodes(
                    hasContentDescription("", substring = true)
                ).fetchSemanticsNodes().size
            )
            rule.onNodeWithText("Reintentar").assert(isButton).performClick()
            assertEquals(it, 1, retries)
            assertTouchTarget("Reintentar", it)
        }
    }

    @Test
    fun everyStateSlipShowsItsIconCentredAboveTheText() {
        rule.inEveryScheme(
            content = {
                Column {
                    EmptySlip(
                        "Dale forma a tu mes",
                        "Crea una categoría.",
                        SelloIcon.ReceiptLong,
                        Modifier.testTag("empty")
                    )
                    ErrorSlip(
                        "Tus registros siguen guardados.",
                        {},
                        Modifier.testTag("error"),
                        title = "No pudimos leer tu mes"
                    )
                }
            }
        ) {
            listOf("empty" to "Dale forma a tu mes", "error" to "No pudimos leer tu mes")
                .forEach { (tag, title) ->
                    val slip = hasTestTag(tag)
                    val icon = rule.onNode(hasAnyAncestor(slip) and hasTestTag(STATE_ICON_TAG))
                        .getUnclippedBoundsInRoot()
                    val heading = rule.onNode(hasAnyAncestor(slip) and hasText(title))
                        .getUnclippedBoundsInRoot()
                    val iconMiddle = (icon.left + icon.right) / 2
                    val headingMiddle = (heading.left + heading.right) / 2
                    assertTrue(
                        "$it: $tag icon is centred on its text",
                        (iconMiddle - headingMiddle).value in -1f..1f
                    )
                    assertTrue("$it: $tag icon is above its text", icon.bottom <= heading.top)
                }
        }
    }

    @Test
    fun skeletonAnnouncesLoadingAndNeverAFigure() {
        rule.inEveryScheme(content = { SlipSkeleton(Modifier.testTag("skeleton")) }) {
            rule.onNodeWithTag("skeleton").assertContentDescriptionEquals("Cargando")
            rule.onNodeWithTag("skeleton").assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
        }
    }

    private fun pressBack() {
        Espresso.pressBack()
    }

    private class ConfirmCalls {
        var confirmed = 0
        var dismissed = 0
    }

    private fun showConfirm(calls: ConfirmCalls, busy: () -> Boolean = { false }) {
        rule.inEveryScheme(
            reset = {
                calls.confirmed = 0
                calls.dismissed = 0
            },
            content = {
                ConfirmSlip(
                    title = "¿Borrar Café?",
                    message = AnnotatedString("Se borran sus 12 gastos."),
                    confirmLabel = "Borrar",
                    onConfirm = { calls.confirmed++ },
                    onDismiss = { calls.dismissed++ },
                    busy = busy()
                )
            }
        ) { scheme -> check(scheme) }
    }

    private var check: (String) -> Unit = {}

    @Test
    fun cancelInvokesOnlyCancellationAndConfirmOnlyConfirmation() {
        val calls = ConfirmCalls()
        check = {
            rule.onNodeWithText("¿Borrar Café?").assertIsDisplayed()
            rule.onNodeWithText("Se borran sus 12 gastos.").assertIsDisplayed()
            rule.onNodeWithText("Cancelar").assert(isButton).performClick()
            assertEquals(it, 0 to 1, calls.confirmed to calls.dismissed)
            rule.onNodeWithText("Borrar").assert(isButton).performClick()
            assertEquals(it, 1 to 1, calls.confirmed to calls.dismissed)
            assertTouchTarget("Cancelar", it)
            assertTouchTarget("Borrar", it)
        }
        showConfirm(calls)
    }

    @Test
    fun backCancelsAConfirmation() {
        val calls = ConfirmCalls()
        rule.setContent {
            com.software.sello.designsystem.theme.SelloTheme {
                ConfirmSlip(
                    title = "¿Borrar Café?",
                    message = AnnotatedString("Se borran sus 12 gastos."),
                    confirmLabel = "Borrar",
                    onConfirm = { calls.confirmed++ },
                    onDismiss = { calls.dismissed++ }
                )
            }
        }
        rule.onNodeWithText("Borrar").assertIsDisplayed()
        pressBack()
        rule.waitForIdle()
        assertEquals(0 to 1, calls.confirmed to calls.dismissed)
    }

    @Test
    fun aBusyConfirmationCannotBeSubmittedAgainOrDismissed() {
        val calls = ConfirmCalls()
        var busy by mutableStateOf(false)
        rule.setContent {
            com.software.sello.designsystem.theme.SelloTheme {
                ConfirmSlip(
                    title = "¿Borrar Café?",
                    message = AnnotatedString("Se borran sus 12 gastos."),
                    confirmLabel = "Borrar",
                    onConfirm = { calls.confirmed++ },
                    onDismiss = { calls.dismissed++ },
                    busy = busy
                )
            }
        }
        rule.onNodeWithText("Borrar").assertIsEnabled()
        busy = true
        rule.waitForIdle()
        rule.onNodeWithText("Borrar").assertIsNotEnabled().performClick()
        rule.onNodeWithText("Cancelar").assertIsNotEnabled().performClick()
        pressBack()
        rule.waitForIdle()
        assertEquals(0 to 0, calls.confirmed to calls.dismissed)
        rule.onNodeWithText("Borrar").assertIsDisplayed()
    }

    @Test
    fun aLoadingButtonKeepsItsLabelAndIgnoresPresses() {
        var presses = 0
        rule.inEveryScheme(
            reset = { presses = 0 },
            content = {
                Column {
                    SelloButton("Guardar", { presses++ }, Modifier.testTag("ready"))
                    SelloButton("Guardando", {
                        presses++
                    }, Modifier.testTag("busy"), loading = true)
                    SelloButton("Escribe un monto", {
                        presses++
                    }, Modifier.testTag("off"), enabled = false)
                }
            }
        ) {
            rule.onNodeWithTag("ready").assert(isButton).performClick()
            rule.onNodeWithTag("busy").assertIsNotEnabled().performClick()
            rule.onNodeWithTag("off").assertIsNotEnabled().performClick()
            assertEquals(it, 1, presses)
            rule.onNodeWithText("Guardando").assertIsDisplayed()
        }
    }
}
