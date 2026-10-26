package com.software.sello.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.DangerSlip
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.EntryLayout
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconChoiceSheetContent
import com.software.sello.designsystem.component.IconPickerCircle
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Currency
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import java.time.Instant
import java.time.ZoneOffset

/**
 * The form's root. A created investment hands its id to [onCreated] so its detail can
 * open; a deletion calls [onDeleted] so the detail it came from is left too; a saved
 * edit or a discarded form calls [onClosed]. Back asks before a draft is thrown away.
 */
@Composable
fun InvestmentEditorRoot(
    viewModels: ViewModelProvider.Factory,
    onCreated: (String) -> Unit,
    onDeleted: () -> Unit,
    onClosed: () -> Unit
) {
    val model: InvestmentEditorViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) {
        when (val result = state.result) {
            is InvestmentEditorResult.Created -> onCreated(result.investmentId)
            InvestmentEditorResult.Deleted -> onDeleted()
            InvestmentEditorResult.Updated, InvestmentEditorResult.Discarded -> onClosed()
            null -> Unit
        }
    }
    BackHandler { model.onAction(InvestmentEditorAction.Back) }
    InvestmentEditorScreen(state, model::onAction)
}

/**
 * Nueva inversión and Editar inversión, made alike with Nueva categoría and Nueva meta
 * (owner, 2026-10-26) in ink: the icon as a large circle beside the name, opening the
 * shared icons; the optional description; Pesos | Dólares. A new investment's figures
 * (Primer aporte with its date, Vale hoy when it is another value, the rate first for
 * dollars) are typed on the Sello pad, which is always there: the figure it types into
 * is edged in ink, and tapping another figure moves the pad to it. Editing changes name,
 * description and icon only and adds Borrar esta inversión.
 */
@Composable
fun InvestmentEditorScreen(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit
) {
    val dates = dateLabels()
    var choosingIcon by rememberSaveable { mutableStateOf(false) }
    // Kept here, not in the form: reading the form again must not take focus again.
    var nameFocused by rememberSaveable { mutableStateOf(false) }
    val identity: @Composable () -> Unit = {
        state.saveError?.let { error -> SaveError(error, onAction) }
        Identity(state, onAction, focusName = !nameFocused, { nameFocused = true }) {
            choosingIcon = true
        }
    }
    SelloScaffold(
        title = stringResource(
            if (state.editing) R.string.investment_editor_edit else R.string.investment_editor_new
        ),
        navigationIcon = {
            IconButton(
                onClick = { onAction(InvestmentEditorAction.Back) },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_CLOSE_TAG)
            ) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.expense_entry_close))
            }
        }
    ) { padding ->
        when {
            state.loading || state.loadFailed || state.editing -> Column(
                Modifier.fillMaxSize().padding(padding)
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(vertical = SelloTheme.spacing.md)
                        .testTag(INVESTMENT_EDITOR_TAG),
                    verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
                ) {
                    when {
                        state.loading -> SlipSkeleton(Modifier.testTag("investment.editor.loading"))

                        state.loadFailed -> ErrorSlip(
                            stringResource(R.string.investment_editor_load_failed),
                            { onAction(InvestmentEditorAction.Retry) },
                            Modifier.testTag("investment.editor.failed")
                        )

                        else -> {
                            identity()
                            DangerSlip(
                                title = stringResource(R.string.investment_delete_title),
                                consequences = deleteConsequences(state.movementCount),
                                actionLabel = stringResource(R.string.investment_delete_action),
                                onAction = { onAction(InvestmentEditorAction.AskToDelete) },
                                modifier = Modifier.testTag(INVESTMENT_EDITOR_DELETE_TAG)
                            )
                        }
                    }
                }
                if (state.editing && !state.loading && !state.loadFailed) {
                    Action(state, onAction)
                }
            }

            // As Anotar, Nueva meta and Nueva categoría the button and the pad stay put.
            // With up to three figures under a taller identity, nothing above them is
            // pinned: it all scrolls, and the figure the pad gets scrolls into view.
            else -> EntryLayout(
                modifier = Modifier.padding(padding).testTag(INVESTMENT_EDITOR_TAG),
                header = {},
                amount = {},
                choices = {
                    identity()
                    FirstContribution(state, onAction, dates)
                    if (!state.sameValue) OtherValue(state, onAction)
                    Rate(state, onAction, dates)
                },
                action = { Action(state, onAction) },
                keypad = {
                    val decimal = state.padField == InvestmentField.Rate ||
                        state.currency == Currency.USD
                    AmountKeypad(
                        onDigit = { onAction(InvestmentEditorAction.Digit(it)) },
                        onTripleZero = { onAction(InvestmentEditorAction.TripleZero) },
                        onBackspace = { onAction(InvestmentEditorAction.Backspace) },
                        onClearAll = { onAction(InvestmentEditorAction.Clear) },
                        modifier = Modifier.testTag(INVESTMENT_EDITOR_PAD_TAG),
                        enabled = state.editable,
                        compact = true,
                        onComma = if (decimal) {
                            { onAction(InvestmentEditorAction.Comma) }
                        } else {
                            null
                        }
                    )
                }
            )
        }
    }
    if (choosingIcon) {
        BackHandler { choosingIcon = false }
        IconSheet(state.iconKey, { choosingIcon = false }) {
            onAction(InvestmentEditorAction.IconPicked(it))
            choosingIcon = false
        }
    }
    if (state.datePicker != null) FormDatePicker(state, onAction)
    if (state.confirmDelete) {
        ConfirmSlip(
            title = stringResource(R.string.investment_delete_confirm_title, state.name.trim()),
            message = deleteConsequences(state.movementCount),
            confirmLabel = stringResource(R.string.investment_delete_confirm),
            onConfirm = { onAction(InvestmentEditorAction.Delete) },
            onDismiss = { onAction(InvestmentEditorAction.CancelDelete) },
            busy = state.busy
        )
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = stringResource(R.string.investment_editor_discard_title),
            message = AnnotatedString(stringResource(R.string.investment_editor_discard_message)),
            confirmLabel = stringResource(R.string.investment_editor_discard_confirm),
            onConfirm = { onAction(InvestmentEditorAction.DiscardConfirmed) },
            onDismiss = { onAction(InvestmentEditorAction.KeepEditing) },
            dismissLabel = stringResource(R.string.investment_editor_discard_keep)
        )
    }
}

/** What is still missing, said above the button as on Anotar, then the button. */
@Composable
private fun Action(state: InvestmentEditorState, onAction: (InvestmentEditorAction) -> Unit) {
    val unknown = state.saveError == InvestmentSaveError.Unknown
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        val missing = when {
            state.busy || unknown -> null
            state.iconKey == null -> R.string.investment_editor_missing_icon
            state.name.isBlank() -> R.string.investment_editor_missing_name
            state.editing -> null
            state.firstContribution == null -> R.string.investment_editor_missing_first
            !state.sameValue && state.otherValue == null -> R.string.investment_editor_missing_value
            state.rateNeeded && state.rate == null -> R.string.investment_editor_missing_rate
            else -> null
        }
        if (missing != null) {
            FieldMessageText(
                FieldMessage(stringResource(missing), isError = false),
                Modifier.testTag(INVESTMENT_EDITOR_MISSING_TAG)
            )
        }
        SelloButton(
            text = stringResource(
                when {
                    unknown -> R.string.investment_editor_check
                    state.editing -> R.string.investment_editor_save
                    else -> R.string.investment_editor_create
                }
            ),
            onClick = {
                onAction(
                    if (unknown) InvestmentEditorAction.Retry else InvestmentEditorAction.Submit
                )
            },
            modifier = Modifier.fillMaxWidth().testTag(INVESTMENT_EDITOR_SUBMIT_TAG),
            icon = if (state.editing) SelloIcon.Check else SelloIcon.TrendingUp,
            enabled = state.canSubmit || (unknown && !state.busy),
            loading = state.busy,
            tone = ButtonTone.Ink
        )
    }
}

/** "Se borran la inversión y sus **9 movimientos**. Esto no se puede deshacer." (IN23) */
@Composable
private fun deleteConsequences(count: Int): AnnotatedString {
    val movements = pluralStringResource(R.plurals.investment_delete_movements, count, count)
    val sentence = stringResource(R.string.investment_delete_consequences, movements)
    val start = sentence.indexOf(movements)
    return buildAnnotatedString {
        append(sentence)
        if (start >= 0) {
            addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + movements.length)
        }
    }
}

@Composable
private fun SaveError(error: InvestmentSaveError, onAction: (InvestmentEditorAction) -> Unit) {
    ErrorSlip(
        message = stringResource(
            when (error) {
                InvestmentSaveError.NotSaved -> R.string.investment_editor_not_saved
                InvestmentSaveError.Unknown -> R.string.investment_editor_unknown
                InvestmentSaveError.Changed -> R.string.investment_editor_changed
                InvestmentSaveError.FutureDate -> R.string.investment_editor_future_date
                InvestmentSaveError.Invalid -> R.string.investment_editor_invalid
                InvestmentSaveError.RateRequired -> R.string.investment_editor_rate_required
                InvestmentSaveError.Unchanged -> R.string.investment_editor_unchanged
            }
        ),
        onRetry = { onAction(InvestmentEditorAction.Retry) },
        modifier = Modifier.testTag(INVESTMENT_EDITOR_ERROR_TAG),
        retryLabel = stringResource(
            if (error == InvestmentSaveError.Unknown) {
                R.string.investment_editor_check
            } else {
                R.string.investment_editor_retry
            }
        )
    )
}

@Composable
private fun textMessage(error: InvestmentTextError?, hint: String, empty: Boolean): FieldMessage? =
    when (error) {
        InvestmentTextError.TooLong -> FieldMessage(
            stringResource(R.string.investment_editor_too_long)
        )

        InvestmentTextError.NotAllowed ->
            FieldMessage(stringResource(R.string.investment_editor_not_allowed))

        null -> if (empty) FieldMessage(hint, isError = false) else null
    }

/**
 * The identity slip: the icon circle beside Nombre, the optional description and, when
 * creating, Pesos | Dólares. A new investment opens on the name with the keyboard up.
 */
@Composable
private fun Identity(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit,
    focusName: Boolean,
    onNameFocused: () -> Unit,
    onChooseIcon: () -> Unit
) {
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (!state.editing && focusName) {
            nameFocus.requestFocus()
            keyboard?.show()
        }
        onNameFocused()
    }
    val key = state.iconKey
    Slip(Modifier.fillMaxWidth().testTag("investment.editor.identity"), pinked = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconPickerCircle(
                icon = key?.let(InvestmentIcons::iconFor),
                caption = stringResource(
                    if (key == null) R.string.icon_picker_choose else R.string.icon_picker_change
                ),
                description = key?.let {
                    stringResource(
                        R.string.icon_picker_spoken,
                        stringResource(InvestmentIcons.labelFor(it))
                    )
                } ?: stringResource(R.string.icon_picker_choose),
                onClick = onChooseIcon,
                modifier = Modifier.testTag(INVESTMENT_EDITOR_ICON_TAG),
                tone = ButtonTone.Ink,
                enabled = state.editable
            )
            FormField(
                label = stringResource(R.string.investment_editor_name),
                value = state.name,
                onValueChange = { onAction(InvestmentEditorAction.NameChanged(it)) },
                modifier = Modifier.weight(1f).focusRequester(nameFocus)
                    .testTag(INVESTMENT_EDITOR_NAME_TAG),
                message = textMessage(
                    state.nameError,
                    stringResource(R.string.investment_editor_name_hint),
                    state.name.isEmpty()
                ),
                counter = stringResource(
                    R.string.investment_editor_counter,
                    state.nameLength,
                    InvestmentEditorState.MAX_NAME
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                )
            )
        }
        FormField(
            label = stringResource(R.string.investment_editor_description),
            value = state.description,
            onValueChange = { onAction(InvestmentEditorAction.DescriptionChanged(it)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                .testTag(INVESTMENT_EDITOR_DESCRIPTION_TAG),
            message = textMessage(
                state.descriptionError,
                stringResource(R.string.investment_editor_description_hint),
                state.description.isEmpty()
            ),
            counter = stringResource(
                R.string.investment_editor_counter,
                state.descriptionLength,
                InvestmentEditorState.MAX_DESCRIPTION
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            )
        )
        if (!state.editing) {
            SegmentedSwitch(
                listOf(
                    stringResource(R.string.investment_editor_pesos),
                    stringResource(R.string.investment_editor_dollars)
                ),
                if (state.currency == Currency.USD) 1 else 0,
                {
                    onAction(
                        InvestmentEditorAction.CurrencyPicked(
                            if (it == 1) Currency.USD else Currency.COP
                        )
                    )
                },
                Modifier.padding(top = 12.dp).testTag(INVESTMENT_EDITOR_CURRENCY_TAG),
                enabled = state.editable
            )
            if (state.currency == Currency.USD) {
                FactLine(
                    AnnotatedString(stringResource(R.string.investment_editor_dollars_fact)),
                    Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** The shared 35 icons, the money and investment ones first. */
@Composable
private fun IconSheet(selected: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    RepeatSheet(
        title = stringResource(R.string.category_editor_icon),
        onDismiss = onDismiss,
        modifier = Modifier.testTag(INVESTMENT_EDITOR_ICONS_TAG)
    ) {
        IconChoiceSheetContent(
            choices = InvestmentIcons.options.map { (key, icon) ->
                IconChoice(key, icon, stringResource(InvestmentIcons.labelFor(key)))
            },
            selectedId = selected,
            onPick = onPick,
            note = stringResource(R.string.investment_icon_sheet_note),
            tone = ButtonTone.Ink
        )
    }
}

/**
 * One figure on the pad: its slip, edged in ink while the pad types into it. Tapping it
 * gives it the pad, and it scrolls into view when it gets it. A screen reader hears which
 * one the pad is typing.
 */
@Composable
private fun FigureSlip(
    state: InvestmentEditorState,
    field: InvestmentField,
    label: String,
    amount: MoneyTextValue?,
    onAction: (InvestmentEditorAction) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    date: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {}
) {
    val active = state.padField == field
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active) { if (active) requester.bringIntoView() }
    val typing = stringResource(R.string.investment_editor_typing)
    val typeHere = stringResource(R.string.investment_editor_type_here)
    val choose = { onAction(InvestmentEditorAction.TypeInto(field)) }
    EntryAmountSlip(
        label = label,
        amount = amount,
        modifier = modifier.bringIntoViewRequester(requester).semantics {
            if (active) stateDescription = typing
            onClick(label = typeHere) {
                choose()
                true
            }
        },
        error = error,
        date = date,
        limitPulse = if (state.atLimit == field) state.pulse else 0,
        amountAlpha = if (active) 1f else IDLE_ALPHA,
        onClick = if (active || !state.editable) null else choose,
        active = active,
        content = content
    )
}

/** Primer aporte with its date, then, at its foot, whether it is worth the same today. */
@Composable
private fun FirstContribution(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit,
    dates: DateLabels
) {
    val label = stringResource(
        if (state.currency == Currency.USD) {
            R.string.investment_editor_first_usd
        } else {
            R.string.investment_editor_first
        }
    )
    val error = limitMessage(state, InvestmentField.First)?.text
        ?: if (state.firstContribution != null && !state.firstReady) {
            stringResource(R.string.investment_editor_first_zero)
        } else {
            null
        }
    FigureSlip(
        state,
        InvestmentField.First,
        label,
        state.firstContribution,
        onAction,
        Modifier.testTag(INVESTMENT_EDITOR_FIRST_TAG),
        error = error,
        date = {
            FieldPill(
                text = dates.day(state.date, state.today),
                icon = SelloIcon.Event,
                onClick = {
                    onAction(InvestmentEditorAction.OpenDatePicker(InvestmentField.First))
                },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_DATE_TAG),
                enabled = state.editable
            )
        }
    ) {
        ValueLine(state, onAction)
    }
}

/** "Vale hoy: lo mismo que aportas · Otro valor", or back again once another value is given. */
@Composable
private fun ValueLine(state: InvestmentEditorState, onAction: (InvestmentEditorAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(
            SelloIcon.Insights.painter(),
            contentDescription = null,
            tint = SelloTheme.colors.ink,
            modifier = Modifier.size(18.dp)
        )
        Text(
            stringResource(
                if (state.sameValue) {
                    R.string.investment_editor_value_same
                } else {
                    R.string.investment_editor_value_other
                }
            ),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft
        )
        SelloButton(
            text = stringResource(
                if (state.sameValue) {
                    R.string.investment_editor_other
                } else {
                    R.string.investment_editor_same
                }
            ),
            onClick = { onAction(InvestmentEditorAction.SameValuePicked(!state.sameValue)) },
            modifier = Modifier.testTag(
                if (state.sameValue) INVESTMENT_EDITOR_OTHER_TAG else INVESTMENT_EDITOR_SAME_TAG
            ),
            kind = ButtonKind.Text,
            enabled = state.editable
        )
    }
}

/** Vale hoy, Otro valor: what it was worth before it was written down in Sello. */
@Composable
private fun OtherValue(state: InvestmentEditorState, onAction: (InvestmentEditorAction) -> Unit) {
    FigureSlip(
        state,
        InvestmentField.Value,
        stringResource(R.string.investment_editor_value),
        state.otherValue,
        onAction,
        Modifier.testTag(INVESTMENT_EDITOR_VALUE_TAG),
        error = limitMessage(state, InvestmentField.Value)?.text
    ) {
        FactLine(
            AnnotatedString(stringResource(R.string.investment_editor_value_fact)),
            Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Rate first (rule 12, IN33): a dollar investment and no rate yet asks for one here, with
 * its date. With a rate, one quiet line names it.
 */
@Composable
private fun Rate(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit,
    dates: DateLabels
) {
    if (!state.rateNeeded) {
        val current = state.currentRate
        if (state.currency == Currency.USD && current != null) {
            FactLine(
                AnnotatedString(
                    stringResource(
                        R.string.investment_editor_rate_line,
                        current.rate,
                        dates.shortDay(current.date, state.today)
                    )
                ),
                Modifier.padding(horizontal = SelloTheme.spacing.lg)
                    .testTag(INVESTMENT_EDITOR_RATE_LINE_TAG)
            )
        }
        return
    }
    val rate = state.rate
    FigureSlip(
        state,
        InvestmentField.Rate,
        stringResource(R.string.investment_editor_rate_ask),
        rate?.let {
            MoneyTextValue(
                MoneySign.None,
                "",
                it,
                stringResource(R.string.usd_rate_entry_spoken, it)
            )
        },
        onAction,
        Modifier.testTag(INVESTMENT_EDITOR_RATE_SLIP_TAG),
        error = when {
            state.rateProblem == RateProblem.BelowMinimum -> R.string.usd_rate_below
            state.rateProblem == RateProblem.AboveMaximum -> R.string.usd_rate_above
            state.atLimit == InvestmentField.Rate -> R.string.usd_rate_limit
            else -> null
        }?.let { stringResource(it) },
        date = {
            FieldPill(
                text = dates.day(state.rateDate, state.today),
                icon = SelloIcon.Event,
                onClick = { onAction(InvestmentEditorAction.OpenDatePicker(InvestmentField.Rate)) },
                modifier = Modifier.testTag(INVESTMENT_EDITOR_RATE_DATE_TAG),
                enabled = state.editable
            )
        }
    ) {
        FactLine(
            AnnotatedString(stringResource(R.string.investment_editor_rate_fact)),
            Modifier.padding(top = 4.dp)
        )
    }
}

/** A figure the pad is not typing into stays readable but steps back. */
private const val IDLE_ALPHA = 0.55f

@Composable
private fun limitMessage(state: InvestmentEditorState, field: InvestmentField): FieldMessage? =
    if (state.atLimit == field) {
        FieldMessage(
            stringResource(
                if (state.currency == Currency.USD) {
                    R.string.investment_editor_limit_usd
                } else {
                    R.string.investment_editor_limit_cop
                }
            )
        )
    } else {
        null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormDatePicker(
    state: InvestmentEditorState,
    onAction: (InvestmentEditorAction) -> Unit
) {
    val shown = if (state.datePicker == InvestmentField.Rate) state.rateDate else state.date
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = shown.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC).toLocalDate().isAfter(state.today)

            override fun isSelectableYear(year: Int) = year <= state.today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = { onAction(InvestmentEditorAction.CloseDatePicker) },
        confirmButton = {
            SelloButton(stringResource(R.string.expense_entry_date_confirm), {
                picker.selectedDateMillis?.let {
                    onAction(
                        InvestmentEditorAction.DatePicked(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    )
                } ?: onAction(InvestmentEditorAction.CloseDatePicker)
            })
        }
    ) { DatePicker(picker) }
}

const val INVESTMENT_EDITOR_TAG = "investment.editor"
const val INVESTMENT_EDITOR_ICON_TAG = "investment.editor.icon"
const val INVESTMENT_EDITOR_ICONS_TAG = "investment.editor.icons"
const val INVESTMENT_EDITOR_MISSING_TAG = "investment.editor.missing"
const val INVESTMENT_EDITOR_CLOSE_TAG = "investment.editor.close"
const val INVESTMENT_EDITOR_NAME_TAG = "investment.editor.name"
const val INVESTMENT_EDITOR_DESCRIPTION_TAG = "investment.editor.description"
const val INVESTMENT_EDITOR_CURRENCY_TAG = "investment.editor.currency"
const val INVESTMENT_EDITOR_FIRST_TAG = "investment.editor.firstAmount"
const val INVESTMENT_EDITOR_DATE_TAG = "investment.editor.date"
const val INVESTMENT_EDITOR_SAME_TAG = "investment.editor.same"
const val INVESTMENT_EDITOR_OTHER_TAG = "investment.editor.other"
const val INVESTMENT_EDITOR_VALUE_TAG = "investment.editor.value"
const val INVESTMENT_EDITOR_RATE_SLIP_TAG = "investment.editor.rateSlip"
const val INVESTMENT_EDITOR_RATE_DATE_TAG = "investment.editor.rateDate"
const val INVESTMENT_EDITOR_RATE_LINE_TAG = "investment.editor.rateLine"
const val INVESTMENT_EDITOR_PAD_TAG = "investment.editor.pad"
const val INVESTMENT_EDITOR_SUBMIT_TAG = "investment.editor.submit"
const val INVESTMENT_EDITOR_DELETE_TAG = "investment.editor.delete"
const val INVESTMENT_EDITOR_ERROR_TAG = "investment.editor.error"
