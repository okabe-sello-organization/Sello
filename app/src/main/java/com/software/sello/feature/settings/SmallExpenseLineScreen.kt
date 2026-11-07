package com.software.sello.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldTone
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.PadForm
import com.software.sello.designsystem.component.PadFormStage
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.movement.MovementCloseButton

@Composable
fun SmallExpenseLineRoot(viewModels: ViewModelProvider.Factory, onClosed: () -> Unit) {
    val model: SmallExpenseLineViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    BackHandler { model.onAction(SmallExpenseLineAction.Back) }
    SmallExpenseLineScreen(state, model::onAction)
}

/**
 * Gastos hormiga's line (SELLO-194): the Tasa del dólar form without the date. "Hasta, en
 * pesos" typed on the Sello pad (000 instead of a comma), the line in use under it, and
 * Guardar. A value outside 1.000 to 100.000 says why and cannot be saved.
 */
@Composable
fun SmallExpenseLineScreen(
    state: SmallExpenseLineState,
    onAction: (SmallExpenseLineAction) -> Unit
) {
    PadForm(
        title = stringResource(R.string.small_expense_title),
        stage = when {
            state.loading -> PadFormStage.Loading

            state.loadFailed -> PadFormStage.Failed(
                stringResource(R.string.small_expense_failed),
                stringResource(R.string.small_expense_failed_body)
            ) { onAction(SmallExpenseLineAction.Retry) }

            else -> PadFormStage.Ready
        },
        navigationIcon = {
            MovementCloseButton(
                { onAction(SmallExpenseLineAction.Back) },
                Modifier.testTag(SMALL_LINE_CLOSE_TAG)
            )
        },
        amount = { Amount(state) },
        fact = { Fact(state) },
        action = { Action(state, onAction) },
        keypad = {
            AmountKeypad(
                { onAction(SmallExpenseLineAction.Digit(it)) },
                { onAction(SmallExpenseLineAction.TripleZero) },
                { onAction(SmallExpenseLineAction.Backspace) },
                { onAction(SmallExpenseLineAction.Clear) },
                enabled = state.editable,
                modifier = Modifier.testTag(SMALL_LINE_KEYPAD_TAG),
                compact = true
            )
        },
        loadingModifier = Modifier.testTag(SMALL_LINE_LOADING_TAG),
        failedModifier = Modifier.testTag(SMALL_LINE_FAILED_TAG)
    )
}

@Composable
private fun Amount(state: SmallExpenseLineState) {
    EntryAmountSlip(
        label = stringResource(R.string.small_expense_label),
        amount = state.amount?.let {
            MoneyTextValue(
                MoneySign.None,
                "",
                it,
                stringResource(R.string.small_expense_spoken, it)
            )
        },
        modifier = Modifier.testTag(SMALL_LINE_AMOUNT_TAG),
        error = when (state.problem) {
            LineProblem.BelowMinimum -> stringResource(R.string.small_expense_below)
            LineProblem.AboveMaximum -> stringResource(R.string.small_expense_above)
            null -> null
        },
        icon = SelloIcon.BugReport,
        tone = ButtonTone.Ink,
        limitPulse = state.limitPulse
    )
}

/** "Hoy es 20.000. El nuevo vale para todos los meses, también los pasados. …" */
@Composable
private fun Fact(state: SmallExpenseLineState) {
    val current = state.current ?: return
    FactLine(
        stringResource(R.string.small_expense_fact, current),
        current,
        Modifier.testTag(SMALL_LINE_FACT_TAG)
    )
}

@Composable
private fun Action(state: SmallExpenseLineState, onAction: (SmallExpenseLineAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        if (state.notSaved) {
            FieldMessageText(
                FieldMessage(stringResource(R.string.small_expense_not_saved), FieldTone.Error),
                Modifier.testTag(SMALL_LINE_ERROR_TAG)
            )
        }
        SelloButton(
            text = stringResource(
                if (state.busy) R.string.expense_entry_saving else R.string.small_expense_save
            ),
            onClick = { onAction(SmallExpenseLineAction.Submit) },
            enabled = state.canSubmit,
            loading = state.busy,
            tone = ButtonTone.Ink,
            modifier = Modifier.fillMaxWidth().testTag(SMALL_LINE_SUBMIT_TAG)
        )
    }
}

const val SMALL_LINE_CLOSE_TAG = "small.line.close"
const val SMALL_LINE_LOADING_TAG = "small.line.loading"
const val SMALL_LINE_FAILED_TAG = "small.line.failed"
const val SMALL_LINE_AMOUNT_TAG = "small.line.amount"
const val SMALL_LINE_FACT_TAG = "small.line.fact"
const val SMALL_LINE_KEYPAD_TAG = "small.line.keypad"
const val SMALL_LINE_ERROR_TAG = "small.line.error"
const val SMALL_LINE_SUBMIT_TAG = "small.line.submit"
