package com.software.sello.feature.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconChoiceSheetContent
import com.software.sello.designsystem.component.IconPickerCircle
import com.software.sello.designsystem.component.MilestoneRow
import com.software.sello.designsystem.component.MonthCell
import com.software.sello.designsystem.component.MonthGrid
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.month.monthNames
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
 * Nueva meta and Editar meta (v2 create, owner-revised 2026-10-26 to match Nueva categoría):
 * the icon as a large circle beside the name, opening the icons to choose from; the amount
 * as the big number on the Sello pad, which is always there, with Para cuándo in the
 * slip's corner; and the stamps with the month each could arrive at that pace, centred,
 * a plan with its assumption. Editing adds ¿Ya la lograste? and Borrar esta meta; a
 * completed goal shows only that it can be deleted.
 */
@Composable
fun GoalEditorScreen(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit) {
    val dates = dateLabels()
    val months = monthNames()
    var choosingIcon by rememberSaveable { mutableStateOf(false) }
    // Kept here, not in the form: reading the form again must not take focus again.
    var nameFocused by rememberSaveable { mutableStateOf(false) }
    SelloScaffold(
        title = stringResource(
            if (state.editing) R.string.goal_editor_edit else R.string.goal_editor_new
        ),
        navigationIcon = {
            IconButton(
                onClick = { onAction(GoalEditorAction.Back) },
                modifier = Modifier.testTag(GOAL_EDITOR_CLOSE_TAG)
            ) {
                Icon(SelloIcon.Close.painter(), stringResource(R.string.goal_editor_close))
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

                    state.loadFailed -> ErrorSlip(
                        stringResource(R.string.goal_editor_load_failed),
                        { onAction(GoalEditorAction.Retry) },
                        Modifier.testTag("goal.editor.failed")
                    )

                    else -> {
                        SaveError(state, onAction)
                        FactLine(AnnotatedString(stringResource(R.string.goal_editor_completed)))
                        Delete(state, onAction)
                    }
                }
            }

            // As Anotar: the identity and the amount hold still above what scrolls, and
            // the button and the pad stay put; short or wide windows rearrange it.
            else -> EntryLayout(
                modifier = Modifier.padding(padding).testTag(GOAL_EDITOR_TAG),
                header = {
                    SaveError(state, onAction)
                    Identity(state, onAction, focusName = !nameFocused, { nameFocused = true }) {
                        choosingIcon = true
                    }
                },
                amount = { Plan(state, onAction, dates) },
                choices = {
                    Stamps(state, dates)
                    if (state.editing) {
                        DoneAsk(state, onAction)
                        Delete(state, onAction)
                    }
                },
                action = { Action(state, onAction) },
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

@Composable
private fun SaveError(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit) {
    val error = state.saveError ?: return
    ErrorSlip(
        message = stringResource(
            when (error) {
                GoalSaveError.NotSaved -> R.string.goal_editor_not_saved
                GoalSaveError.Unknown -> R.string.goal_editor_unknown
                GoalSaveError.Changed -> R.string.goal_editor_changed
            }
        ),
        onRetry = { onAction(GoalEditorAction.Retry) },
        modifier = Modifier.testTag("goal.editor.error"),
        retryLabel = stringResource(
            if (error == GoalSaveError.Unknown) {
                R.string.goal_error_check
            } else {
                R.string.goal_error_retry
            }
        )
    )
}

@Composable
private fun Delete(state: GoalEditorState, onAction: (GoalEditorAction) -> Unit) {
    if (!state.editing) return
    DangerSlip(
        title = stringResource(R.string.goal_delete_title),
        consequences = deleteConsequencesText(state.movementCount),
        actionLabel = stringResource(R.string.goal_delete_action),
        onAction = { onAction(GoalEditorAction.AskToDelete) },
        modifier = Modifier.testTag(GOAL_EDITOR_DELETE_TAG)
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
        if (missing != null) {
            FieldMessageText(
                FieldMessage(stringResource(missing), isError = false),
                Modifier.testTag(GOAL_EDITOR_MISSING_TAG)
            )
        }
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
            Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft
        )
        if (state.completion is CompletionState.Failed) {
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        if ((state.completion as CompletionState.Failed).error ==
                            GoalDetailError.Unknown
                        ) {
                            R.string.goal_error_unknown
                        } else {
                            R.string.goal_error_not_saved
                        }
                    )
                )
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
 * as Nueva categoría does; the pad is there again once the keyboard closes.
 */
@Composable
private fun Identity(
    state: GoalEditorState,
    onAction: (GoalEditorAction) -> Unit,
    focusName: Boolean,
    onNameFocused: () -> Unit,
    onChooseIcon: () -> Unit
) {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                message = when (state.nameError) {
                    GoalNameError.TooLong ->
                        FieldMessage(stringResource(R.string.goal_editor_name_too_long))

                    GoalNameError.NotAllowed ->
                        FieldMessage(stringResource(R.string.goal_editor_name_not_allowed))

                    null -> if (state.name.isEmpty()) {
                        FieldMessage(
                            stringResource(R.string.goal_editor_name_hint),
                            isError = false
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
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )
        }
    }
}

/** The 24 icons, the same as a category's, in a sheet over the form. */
@Composable
private fun IconSheet(selected: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    RepeatSheet(
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
    val month = state.targetMonth?.let(dates::monthYear)
        ?: stringResource(R.string.goal_editor_no_date)
    val spokenMonth = stringResource(R.string.goal_editor_when, month)
    EntryAmountSlip(
        label = stringResource(R.string.goal_editor_amount),
        amount = state.amount,
        modifier = Modifier.testTag(GOAL_EDITOR_AMOUNT_TAG),
        limitPulse = state.amountPulse,
        date = {
            SelloChip(
                label = month,
                selected = false,
                onClick = { onAction(GoalEditorAction.ChooseMonth) },
                icon = SelloIcon.Event,
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
                Modifier.padding(top = 8.dp).testTag("goal.editor.monthly")
            )

            state.nothingMissing -> FactLine(
                AnnotatedString(stringResource(R.string.goal_editor_nothing_missing)),
                Modifier.padding(top = 8.dp).testTag("goal.editor.monthly")
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
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp)
                .testTag(GOAL_EDITOR_MONTHS_TAG)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                Modifier.padding(top = 4.dp, bottom = 12.dp),
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
const val GOAL_EDITOR_DELETE_TAG = "goal.editor.delete"
const val GOAL_EDITOR_MONTHS_TAG = "goal.editor.months"
const val GOAL_EDITOR_MARK_COMPLETE_TAG = "goal.editor.markComplete"
