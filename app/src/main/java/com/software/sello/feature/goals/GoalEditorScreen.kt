package com.software.sello.feature.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.EntryLayout
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FieldTone
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconChoiceSheetContent
import com.software.sello.designsystem.component.IconPickerCircle
import com.software.sello.designsystem.component.MilestoneRow
import com.software.sello.designsystem.component.MonthCell
import com.software.sello.designsystem.component.MonthGrid
import com.software.sello.designsystem.component.MoreMenu
import com.software.sello.designsystem.component.MoreMenuItem
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.SelloSheet
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.month.monthNames
import com.software.sello.presentation.movement.MovementCloseButton
import java.time.YearMonth

/** The form's root: its result closes it, and Back asks before a draft is thrown away. */
@Composable
fun GoalEditorRoot(viewModels: ViewModelProvider.Factory, onFinished: (GoalEditorResult) -> Unit) {
    val model: GoalEditorViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) { state.result?.let(onFinished) }
    BackHandler { model.onAction(GoalEditorAction.Back) }
    GoalEditorScreen(state, model::onAction)
}

/**
 * Nueva meta and Editar meta (owner-revised 2026-10-26 to match Nueva categoría):
 * the icon as a large circle beside the name, opening the icons to choose from; the amount
 * as the big number on the Sello pad, which is always there, with Para cuándo in the
 * slip's corner; and the stamps with the month each could arrive at that pace, centred,
 * a plan with its assumption. Editing adds ¿Ya la lograste? and, in the top bar's ⋮,
 * Borrar meta; a completed goal shows only that it can be deleted from there.
 */
@Composable
fun GoalEditorScreen(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit) {
    val dates = dateLabels()
    val months = monthNames()
    var choosingIcon by rememberSaveable { mutableStateOf(false) }
    // Kept here, not in the form: reading the form again must not take focus again.
    var nameFocused by rememberSaveable { mutableStateOf(false) }
    // While the name is typed the keyboard covers the pad: anything else puts it away.
    val focus = LocalFocusManager.current
    val elsewhere: (GoalEditorAction) -> Unit = {
        focus.clearFocus()
        onAction(it)
    }
    SelloScaffold(
        title = stringResource(
            if (state.editing) R.string.goal_editor_edit else R.string.goal_editor_new
        ),
        navigationIcon = {
            MovementCloseButton(
                { onAction(GoalEditorAction.Back) },
                Modifier.testTag(GOAL_EDITOR_CLOSE_TAG)
            )
        },
        actions = {
            // With a save unresolved, only Comprobar is offered: never Borrar meta.
            if (state.editing && !state.loading && !state.loadFailed && !state.unresolved) {
                MoreMenu(
                    listOf(
                        MoreMenuItem(
                            stringResource(R.string.goal_delete_action),
                            SelloIcon.Delete,
                            { onAction(GoalEditorAction.AskToDelete) },
                            loss = true,
                            tag = GOAL_EDITOR_DELETE_TAG
                        )
                    )
                )
            }
        }
    ) { padding ->
        when {
            state.loading || state.loadFailed || state.completed -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(vertical = SelloTheme.spacing.md)
                    .testTag(GOAL_EDITOR_TAG),
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
            ) {
                when {
                    state.loading -> SlipSkeleton(Modifier.testTag("goal.editor.loading"))

                    state.loadFailed -> StatusSlip(
                        kind = StatusKind.LoadFailed(),
                        message = stringResource(R.string.goal_editor_load_failed),
                        modifier = Modifier.testTag("goal.editor.failed"),
                        action = StatusAction(StatusSlipDefaults.retryLabel) {
                            onAction(GoalEditorAction.Retry)
                        }
                    )

                    else -> {
                        FactLine(AnnotatedString(stringResource(R.string.goal_editor_completed)))
                        SaveProblem(state)
                        if (state.saveError == GoalSaveError.Unknown) CheckAgain(state, onAction)
                    }
                }
            }

            // As Anotar: the identity and the amount hold still above what scrolls, and
            // the button and the pad stay put; short or wide windows rearrange it.
            else -> EntryLayout(
                modifier = Modifier.padding(padding).testTag(GOAL_EDITOR_TAG),
                header = {
                    Identity(
                        state,
                        onAction,
                        focusName = !nameFocused,
                        onNameFocused = { nameFocused = true }
                    ) {
                        focus.clearFocus()
                        choosingIcon = true
                    }
                },
                amount = { Plan(state, elsewhere, dates) },
                choices = {
                    Stamps(state, dates)
                    if (state.editing) DoneAsk(state, elsewhere)
                },
                action = { Action(state, elsewhere) },
                keypad = {
                    AmountKeypad(
                        onDigit = { onAction(GoalEditorAction.Digit(it)) },
                        onTripleZero = { onAction(GoalEditorAction.TripleZero) },
                        onBackspace = { onAction(GoalEditorAction.Backspace) },
                        onClearAll = { onAction(GoalEditorAction.ClearAmount) },
                        modifier = Modifier.testTag(GOAL_EDITOR_PAD_TAG),
                        enabled = !state.busy && state.result == null,
                        compact = true
                    )
                }
            )
        }
    }
    LogradaMoment(state.completion, { onAction(GoalEditorAction.CelebrationDone) })
    if (choosingIcon) {
        BackHandler { choosingIcon = false }
        IconSheet(state.iconKey, { choosingIcon = false }) {
            onAction(GoalEditorAction.IconPicked(it))
            choosingIcon = false
        }
    }
    if (state.choosingMonth) MonthSheet(state, onAction, months)
    if (state.confirmDelete) {
        ConfirmSlip(
            title = stringResource(R.string.goal_delete_confirm_title, state.name.trim()),
            message = deleteConsequencesText(state.movementCount),
            confirmLabel = stringResource(R.string.goal_delete_confirm),
            onConfirm = { onAction(GoalEditorAction.Delete) },
            onDismiss = { onAction(GoalEditorAction.CancelDelete) },
            busy = state.busy
        )
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = stringResource(R.string.goal_editor_discard_title),
            message = AnnotatedString(stringResource(R.string.goal_editor_discard_message)),
            confirmLabel = stringResource(R.string.goal_editor_discard_confirm),
            onConfirm = { onAction(GoalEditorAction.DiscardConfirmed) },
            onDismiss = { onAction(GoalEditorAction.KeepEditing) },
            dismissLabel = stringResource(R.string.goal_editor_discard_keep)
        )
    }
}

/**
 * Why the save did not finish, said above the button as on Anotar: an unknown outcome in
 * the warning tone, a certain failure as an error. Never titled as a failed read.
 */
@Composable
private fun SaveProblem(state: GoalEditorState) {
    val error = state.saveError ?: return
    FieldMessageText(
        FieldMessage(
            stringResource(
                when (error) {
                    GoalSaveError.NotSaved -> R.string.goal_editor_not_saved

                    GoalSaveError.Unknown ->
                        if (state.deleting) {
                            R.string.expense_edit_delete_unknown
                        } else {
                            R.string.goal_editor_unknown
                        }

                    GoalSaveError.Changed -> R.string.goal_editor_changed
                }
            ),
            if (error == GoalSaveError.Unknown) FieldTone.Warning else FieldTone.Error
        ),
        Modifier.testTag(GOAL_EDITOR_ERROR_TAG)
    )
}

/** In the button's place while a save's outcome is unknown: asks again, never sends twice. */
@Composable
private fun CheckAgain(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit) {
    SelloButton(
        text = stringResource(
            if (state.deleting) {
                R.string.expense_edit_check_deleted
            } else {
                R.string.expense_entry_check_again
            }
        ),
        onClick = { onAction(GoalEditorAction.Retry) },
        modifier = Modifier.fillMaxWidth().testTag(GOAL_EDITOR_SUBMIT_TAG),
        loading = state.busy,
        tone = ButtonTone.Gain
    )
}

/** What is still missing, said above the button as on Anotar, then Crear meta. */
@Composable
private fun Action(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        val missing = when {
            state.busy || state.saveError == GoalSaveError.Unknown -> null
            state.iconKey == null -> R.string.goal_editor_missing_icon
            state.name.isBlank() -> R.string.goal_editor_missing_name
            state.amount == null -> R.string.goal_editor_missing_amount
            else -> null
        }
        SaveProblem(state)
        if (missing != null) {
            FieldMessageText(
                FieldMessage(stringResource(missing), tone = FieldTone.Hint),
                Modifier.testTag(GOAL_EDITOR_MISSING_TAG)
            )
        }
        if (state.saveError == GoalSaveError.Unknown) {
            CheckAgain(state, onAction)
        } else {
            SelloButton(
                text = stringResource(
                    if (state.editing) R.string.goal_editor_save else R.string.goal_editor_create
                ),
                onClick = { onAction(GoalEditorAction.Submit) },
                modifier = Modifier.fillMaxWidth().testTag(GOAL_EDITOR_SUBMIT_TAG),
                icon = SelloIcon.Flag,
                enabled = state.canSubmit,
                loading = state.busy,
                tone = ButtonTone.Gain
            )
        }
    }
}

/** ¿Ya la lograste?: for a goal reached outside Sello, at any percentage (MT26). */
@Composable
private fun DoneAsk(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit) {
    Slip(Modifier.fillMaxWidth().testTag("goal.editor.doneAsk"), pinked = false) {
        Text(
            stringResource(R.string.goal_done_ask_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.goal_done_ask_body),
            Modifier.padding(vertical = SelloTheme.spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft
        )
        (state.completion as? CompletionState.Failed)?.let { failed ->
            val unknown = failed.error == GoalDetailError.Unknown
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        if (unknown) R.string.goal_error_unknown else R.string.goal_error_not_saved
                    ),
                    if (unknown) FieldTone.Warning else FieldTone.Error
                ),
                Modifier.testTag(GOAL_EDITOR_DONE_ERROR_TAG)
            )
        }
        SelloButton(
            stringResource(R.string.goal_mark_complete),
            { onAction(GoalEditorAction.MarkComplete) },
            Modifier.fillMaxWidth().testTag(GOAL_EDITOR_MARK_COMPLETE_TAG),
            kind = ButtonKind.Outlined,
            icon = SelloIcon.Check,
            enabled = !state.busy && state.result == null &&
                state.completion !is CompletionState.Celebrating,
            loading = state.completion == CompletionState.Busy,
            tone = ButtonTone.Gain
        )
    }
}

/**
 * The icon circle beside the name. A new goal opens on the name with the keyboard up,
 * as Nueva categoría does; Siguiente, the amount or the month puts it away for the pad.
 */
@Composable
private fun Identity(
    state: GoalEditorState,
    onAction: (GoalEditorAction) -> Unit,
    focusName: Boolean,
    onNameFocused: () -> Unit,
    onChooseIcon: () -> Unit
) {
    val focus = LocalFocusManager.current
    val option = state.iconKey?.let { key -> GoalIcons.options.firstOrNull { it.key == key } }
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (!state.editing && focusName) {
            nameFocus.requestFocus()
            keyboard?.show()
        }
        onNameFocused()
    }
    Slip(Modifier.fillMaxWidth().testTag("goal.editor.identity"), pinked = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
        ) {
            IconPickerCircle(
                icon = option?.icon,
                caption = stringResource(
                    if (option == null) R.string.icon_picker_choose else R.string.icon_picker_change
                ),
                description = option?.let {
                    stringResource(R.string.icon_picker_spoken, stringResource(it.label))
                } ?: stringResource(R.string.icon_picker_choose),
                onClick = onChooseIcon,
                modifier = Modifier.testTag(GOAL_EDITOR_ICON_TAG),
                tone = ButtonTone.Gain,
                enabled = !state.busy
            )
            FormField(
                label = stringResource(R.string.goal_editor_name),
                value = state.name,
                onValueChange = { onAction(GoalEditorAction.NameChanged(it)) },
                modifier = Modifier.weight(1f).focusRequester(nameFocus)
                    .testTag(GOAL_EDITOR_NAME_TAG),
                enabled = state.editable,
                message = when (state.nameError) {
                    GoalNameError.TooLong ->
                        FieldMessage(stringResource(R.string.goal_editor_name_too_long))

                    GoalNameError.NotAllowed ->
                        FieldMessage(stringResource(R.string.goal_editor_name_not_allowed))

                    null -> if (state.name.isEmpty()) {
                        FieldMessage(
                            stringResource(R.string.goal_editor_name_hint),
                            tone = FieldTone.Hint
                        )
                    } else {
                        null
                    }
                },
                counter = stringResource(
                    R.string.goal_editor_name_counter,
                    state.nameLength,
                    GoalEditorState.MAX_NAME
                ),
                // The pad comes next, so the key says Siguiente and puts the keyboard away.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { focus.clearFocus() })
            )
        }
    }
}

/** The 24 icons, the same as a category's, in a sheet over the form. */
@Composable
private fun IconSheet(selected: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    SelloSheet(
        title = stringResource(R.string.category_editor_icon),
        onDismiss = onDismiss,
        modifier = Modifier.testTag(GOAL_EDITOR_ICONS_TAG)
    ) {
        IconChoiceSheetContent(
            choices = GoalIcons.options.map {
                IconChoice(it.key, it.icon, stringResource(it.label))
            },
            selectedId = selected,
            onPick = onPick,
            note = stringResource(R.string.goal_icon_sheet_note),
            tone = ButtonTone.Gain
        )
    }
}

/**
 * Cuánto necesitas, typed on the pad below, with Para cuándo in the slip's corner as
 * Anotar's date: the month, or Sin fecha. With a month, the monthly line follows.
 */
@Composable
private fun Plan(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit, dates: DateLabels) {
    val focus = LocalFocusManager.current
    val month = state.targetMonth?.let(dates::monthYear)
        ?: stringResource(R.string.goal_editor_no_date)
    val spokenMonth = stringResource(R.string.goal_editor_when, month)
    EntryAmountSlip(
        label = stringResource(R.string.goal_editor_amount),
        amount = state.amount,
        // A tap on the slip itself puts the keyboard away; no button, so it reads as before.
        modifier = Modifier.testTag(GOAL_EDITOR_AMOUNT_TAG).pointerInput(Unit) {
            detectTapGestures { focus.clearFocus() }
        },
        limitPulse = state.amountPulse,
        date = {
            FieldPill(
                text = month,
                icon = SelloIcon.Event,
                onClick = { onAction(GoalEditorAction.ChooseMonth) },
                modifier = Modifier.testTag("goal.editor.month")
                    .semantics { contentDescription = spokenMonth },
                enabled = !state.busy
            )
        }
    ) {
        val pace = state.pace
        when {
            pace != null -> FactLine(
                AnnotatedString(
                    pluralStringResource(
                        R.plurals.goal_editor_monthly,
                        pace.months,
                        pace.monthly.digits,
                        pace.months
                    )
                ),
                Modifier.padding(top = SelloTheme.spacing.sm).testTag("goal.editor.monthly")
            )

            state.nothingMissing -> FactLine(
                AnnotatedString(stringResource(R.string.goal_editor_nothing_missing)),
                Modifier.padding(top = SelloTheme.spacing.sm).testTag("goal.editor.monthly")
            )
        }
    }
}

/** Tus sellos, centred: each stamp in an equal share of the width, its month under it. */
@Composable
private fun Stamps(state: GoalEditorState, dates: DateLabels) {
    Slip(Modifier.fillMaxWidth().testTag("goal.editor.stamps"), pinked = false) {
        Text(
            stringResource(R.string.goal_editor_stamps),
            Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        val pace = state.pace
        Text(
            if (pace != null) {
                stringResource(R.string.goal_editor_stamps_pace, pace.monthly.digits)
            } else {
                stringResource(R.string.goal_editor_stamps_no_date)
            },
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            textAlign = TextAlign.Center
        )
        val earned = stringResource(R.string.goal_editor_earned)
        MilestoneRow(
            state.places.map { planned ->
                val spec = milestone(planned.place)
                when (val caption = planned.caption) {
                    PlanCaption.Earned -> spec.copy(caption = earned)

                    is PlanCaption.Month -> {
                        val month = dates.monthShortYear(caption.month)
                        spec.copy(
                            caption = month,
                            description = stringResource(
                                R.string.goal_editor_projected,
                                spec.description,
                                month
                            )
                        )
                    }

                    null -> spec
                }
            },
            Modifier.testTag("goal.editor.places"),
            evenly = true
        )
    }
}

/** Para cuándo: Sin fecha on the title line, then the months from next month on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSheet(
    state: GoalEditorState,
    onAction: (GoalEditorAction) -> Unit,
    months: MonthNames
) {
    val year = state.pickerYear
    val first = state.firstMonth
    ModalBottomSheet(
        onDismissRequest = { onAction(GoalEditorAction.CloseMonthPicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SelloTheme.colors.paper,
        contentColor = SelloTheme.colors.ink
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(SelloTheme.spacing.lg)
                .testTag(GOAL_EDITOR_MONTHS_TAG)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
            ) {
                Text(
                    stringResource(R.string.goal_editor_month_title),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium
                )
                SelloButton(
                    text = stringResource(R.string.goal_editor_no_date),
                    onClick = { onAction(GoalEditorAction.NoDate) },
                    modifier = Modifier.testTag("goal.editor.noDate"),
                    kind = ButtonKind.Outlined,
                    icon = SelloIcon.EventBusy,
                    tone = ButtonTone.Gain
                )
            }
            Text(
                stringResource(R.string.goal_editor_month_note),
                Modifier.padding(top = SelloTheme.spacing.xs, bottom = SelloTheme.spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.inkSoft
            )
            MonthGrid(
                yearLabel = year.toString(),
                months = (1..12).map { number ->
                    val month = YearMonth.of(year, number)
                    MonthCell(
                        id = month.toString(),
                        label = months.short(month),
                        spoken = months.title(month),
                        enabled = month >= first
                    )
                },
                selectedId = state.targetMonth?.toString(),
                onPick = { onAction(GoalEditorAction.PickMonth(YearMonth.parse(it))) },
                previousYearLabel = stringResource(R.string.goal_editor_previous_year),
                nextYearLabel = stringResource(R.string.goal_editor_next_year),
                onPreviousYear = if (year > first.year) {
                    { onAction(GoalEditorAction.ShowYear(year - 1)) }
                } else {
                    null
                },
                onNextYear = if (year < first.year + GoalEditorViewModel.YEARS_AHEAD) {
                    { onAction(GoalEditorAction.ShowYear(year + 1)) }
                } else {
                    null
                }
            )
        }
    }
}

const val GOAL_EDITOR_TAG = "goal.editor"
const val GOAL_EDITOR_CLOSE_TAG = "goal.editor.close"
const val GOAL_EDITOR_NAME_TAG = "goal.editor.name"
const val GOAL_EDITOR_ICON_TAG = "goal.editor.icon"
const val GOAL_EDITOR_ICONS_TAG = "goal.editor.icons"
const val GOAL_EDITOR_AMOUNT_TAG = "goal.editor.amount"
const val GOAL_EDITOR_PAD_TAG = "goal.editor.pad"
const val GOAL_EDITOR_SUBMIT_TAG = "goal.editor.submit"
const val GOAL_EDITOR_MISSING_TAG = "goal.editor.missing"

const val GOAL_EDITOR_ERROR_TAG = "goal.editor.error"

const val GOAL_EDITOR_DONE_ERROR_TAG = "goal.editor.doneAsk.error"
const val GOAL_EDITOR_DELETE_TAG = "goal.editor.delete"
const val GOAL_EDITOR_MONTHS_TAG = "goal.editor.months"
const val GOAL_EDITOR_MARK_COMPLETE_TAG = "goal.editor.markComplete"
