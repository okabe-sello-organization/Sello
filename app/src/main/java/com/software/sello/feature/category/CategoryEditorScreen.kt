package com.software.sello.feature.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
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
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.EntryLayout
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconChoiceSheetContent
import com.software.sello.designsystem.component.IconPickerCircle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CategoryName
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.month.monthNames

/**
 * Owns the view model and what happens when the editor ends: [onFinished] is called
 * once, with the new category's identifier or null when nothing was created.
 */
@Composable
fun CategoryEditorRoot(viewModels: ViewModelProvider.Factory, onFinished: (String?) -> Unit) {
    val viewModel: CategoryEditorViewModel = viewModel(factory = viewModels)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) {
        when (val result = state.result) {
            is EditorResult.Created -> onFinished(result.categoryId)

            EditorResult.Discarded,
            EditorResult.Updated,
            EditorResult.Archived,
            EditorResult.Unarchived -> onFinished(null)

            null -> Unit
        }
    }
    // Back asks before throwing a draft away, and does nothing while saving.
    BackHandler { viewModel.onAction(CategoryEditorAction.Back) }
    CategoryEditorScreen(state, viewModel::onAction)
}

/** The same form on an existing category. It ends when its changes are confirmed or left. */
@Composable
fun CategoryManagementRoot(viewModels: ViewModelProvider.Factory, onFinished: () -> Unit) {
    val viewModel: CategoryManagementViewModel = viewModel(factory = viewModels)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) { if (state.result != null) onFinished() }
    BackHandler { viewModel.onAction(CategoryEditorAction.Back) }
    CategoryEditorScreen(state, viewModel::onAction)
}

/**
 * Nueva categoría and Editar categoría (owner-revised 2026-10-26 to match Nueva meta): the
 * icon as a large circle beside the name, opening the icons to choose from; the monthly
 * limit as the big number on the Sello pad, which is always there, with Sin límite in the
 * slip's corner. It opens on the name with the keyboard up; the pad is there again once
 * the keyboard closes.
 */
@Composable
fun CategoryEditorScreen(
    state: CategoryEditorState,
    onAction: (CategoryEditorAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var choosingIcon by rememberSaveable { mutableStateOf(false) }
    // Kept here, not in the form: reading the form again must not take focus again.
    var nameFocused by rememberSaveable { mutableStateOf(false) }
    SelloScaffold(
        title = stringResource(
            if (state.editing) R.string.category_edit_title else R.string.category_editor_title
        ),
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = { onAction(CategoryEditorAction.Back) }) {
                Icon(SelloIcon.ArrowBack.painter(), stringResource(R.string.category_editor_back))
            }
        },
        actions = {
            // Only an existing category that has been read can be archived. It asks first.
            if (state.editing && !state.loading && !state.loadFailed) {
                val description = stringResource(
                    if (state.archived) {
                        R.string.category_edit_unarchive_description
                    } else {
                        R.string.category_edit_archive_description
                    }
                )
                SelloButton(
                    text = stringResource(
                        if (state.archived) {
                            R.string.category_edit_unarchive
                        } else {
                            R.string.category_edit_archive
                        }
                    ),
                    onClick = { onAction(CategoryEditorAction.Archive) },
                    kind = ButtonKind.Text,
                    enabled = state.canArchive,
                    modifier = Modifier
                        .testTag(EDITOR_ARCHIVE_TAG)
                        .semantics { contentDescription = description }
                )
            }
        }
    ) { padding ->
        when {
            state.loading || state.loadFailed -> Column(
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (state.loading) {
                    SlipSkeleton(Modifier.testTag(EDITOR_LOADING_TAG))
                } else {
                    ErrorSlip(
                        message = stringResource(R.string.category_editor_load_failed),
                        onRetry = { onAction(CategoryEditorAction.Retry) },
                        modifier = Modifier.testTag(EDITOR_LOAD_FAILED_TAG)
                    )
                }
            }

            // As Anotar: the identity and the limit hold still above what scrolls, and the
            // button and the pad stay put; short or wide windows rearrange it.
            else -> EntryLayout(
                modifier = Modifier.padding(padding),
                header = {
                    Identity(state, onAction, focusName = !nameFocused, { nameFocused = true }) {
                        choosingIcon = true
                    }
                },
                amount = { Limit(state, onAction) },
                choices = {
                    Said(state)
                    // Without the card (a past month), a carry refusal is said here.
                    if (state.carry == null) {
                        state.carryProblem?.let { problem ->
                            state.targetMonth?.let { month ->
                                FieldMessageText(
                                    FieldMessage(carryProblemText(problem, state.name, month)),
                                    Modifier.testTag(EDITOR_CARRY_PROBLEM_TAG)
                                )
                            }
                        }
                    }
                },
                action = { Action(state, onAction) },
                keypad = {
                    if (!state.limitLocked) {
                        AmountKeypad(
                            onDigit = { onAction(CategoryEditorAction.LimitDigit(it)) },
                            onTripleZero = { onAction(CategoryEditorAction.LimitTripleZero) },
                            onBackspace = { onAction(CategoryEditorAction.LimitBackspace) },
                            onClearAll = { onAction(CategoryEditorAction.LimitCleared) },
                            modifier = Modifier.testTag(EDITOR_PAD_TAG),
                            enabled = state.editable && !state.unlimited,
                            compact = true
                        )
                    }
                }
            )
        }
    }
    if (choosingIcon) {
        BackHandler { choosingIcon = false }
        IconSheet(state, { choosingIcon = false }) {
            onAction(CategoryEditorAction.IconPicked(it))
            choosingIcon = false
        }
    }
    if (state.confirmDiscard) {
        ConfirmSlip(
            title = stringResource(R.string.category_editor_discard_title),
            message = AnnotatedString(stringResource(R.string.category_editor_discard_message)),
            confirmLabel = stringResource(R.string.category_editor_discard_confirm),
            onConfirm = { onAction(CategoryEditorAction.DiscardConfirmed) },
            onDismiss = { onAction(CategoryEditorAction.KeepEditing) },
            dismissLabel = stringResource(R.string.category_editor_discard_keep)
        )
    }
    state.archiveQuestion?.let { question -> ArchiveConfirmation(state.name, question, onAction) }
    state.carryQuestion?.let { question -> CarryQuestionSlip(state, question, onAction) }
}

/**
 * Says what archiving or unarchiving does to new expenses, to the budget and to what
 * is already recorded, before it is done. Nothing is erased either way, so it is not
 * drawn as a destructive choice.
 */
@Composable
private fun ArchiveConfirmation(
    name: String,
    question: ArchiveQuestion,
    onAction: (CategoryEditorAction) -> Unit
) {
    val months = monthNames()
    val thisMonth = months.inSentence(question.currentMonth)
    val sentences = buildList {
        if (question.unarchive) {
            add(
                stringResource(
                    if (question.resumesBudget) {
                        R.string.category_unarchive_resumes
                    } else {
                        R.string.category_unarchive_same
                    },
                    thisMonth
                )
            )
        } else {
            add(
                stringResource(
                    R.string.category_archive_effect,
                    thisMonth.replaceFirstChar { it.titlecase() },
                    months.inSentence(question.currentMonth.plusMonths(1))
                )
            )
            val count = question.expensesThisMonth
            add(
                if (count == null) {
                    stringResource(R.string.category_archive_keeps)
                } else {
                    pluralStringResource(
                        R.plurals.category_archive_keeps_count,
                        count,
                        countText(count),
                        thisMonth
                    )
                }
            )
        }
        if (question.discardsChanges) add(stringResource(R.string.category_archive_discards))
    }
    ConfirmSlip(
        title = stringResource(
            if (question.unarchive) {
                R.string.category_unarchive_title
            } else {
                R.string.category_archive_title
            },
            name
        ),
        message = AnnotatedString(sentences.joinToString(" ")),
        confirmLabel = stringResource(
            if (question.unarchive) {
                R.string.category_edit_unarchive
            } else {
                R.string.category_edit_archive
            }
        ),
        onConfirm = { onAction(CategoryEditorAction.ArchiveConfirmed) },
        onDismiss = { onAction(CategoryEditorAction.ArchiveDismissed) },
        destructive = false
    )
}

/**
 * What the form says above the button: that a category is needed first or is archived,
 * the name's problem, and what is missing.
 */
@Composable
private fun Identity(
    state: CategoryEditorState,
    onAction: (CategoryEditorAction) -> Unit,
    focusName: Boolean,
    onNameFocused: () -> Unit,
    onChooseIcon: () -> Unit
) {
    if (state.forEntry) {
        FieldMessageText(
            FieldMessage(
                stringResource(R.string.category_editor_needed_for_entry),
                isError = false
            ),
            Modifier.testTag(EDITOR_PREREQUISITE_TAG)
        )
    }
    if (state.archived) {
        FieldMessageText(
            FieldMessage(stringResource(R.string.category_edit_archived), isError = false),
            Modifier.testTag(EDITOR_ARCHIVED_TAG)
        )
    }
    // The name is what is typed first, so the keyboard is ready for it. Only once, when
    // the form can first be changed: not again after the person moves elsewhere, nor
    // when the form is read again, as archiving does just before the editor closes.
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.editable) {
        if (state.editable && focusName) {
            nameFocus.requestFocus()
            keyboard?.show()
            onNameFocused()
        }
    }
    val option = CategoryIcons.options.firstOrNull { it.key == state.iconKey }
    Slip(modifier = Modifier.fillMaxWidth(), pinked = false) {
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
                modifier = Modifier.testTag(EDITOR_ICON_TAG),
                enabled = state.editable
            )
            FormField(
                label = stringResource(R.string.category_editor_name),
                value = state.name,
                onValueChange = { onAction(CategoryEditorAction.NameChanged(it)) },
                modifier = Modifier.weight(1f).testTag(EDITOR_NAME_TAG).focusRequester(nameFocus),
                message = state.nameError?.let { FieldMessage(stringResource(it.text())) },
                counter = stringResource(
                    R.string.category_editor_name_counter,
                    state.name.trim().codePointCount(0, state.name.trim().length),
                    CategoryName.MAX_CODE_POINTS
                ),
                enabled = state.editable,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )
        }
    }
}

/** The 24 icons; the ones other categories use are dashed and can still be chosen. */
@Composable
private fun IconSheet(state: CategoryEditorState, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    RepeatSheet(
        title = stringResource(R.string.category_editor_icon),
        onDismiss = onDismiss,
        modifier = Modifier.testTag(EDITOR_ICONS_TAG)
    ) {
        IconChoiceSheetContent(
            choices = state.iconKeys.map { key ->
                val option = CategoryIcons.options.first { it.key == key }
                IconChoice(key, option.icon, stringResource(option.label))
            },
            selectedId = state.iconKey,
            onPick = onPick,
            note = stringResource(R.string.category_icon_sheet_note),
            inUse = state.usedIconKeys,
            inUseNote = stringResource(R.string.category_icon_in_use)
        )
    }
}

/**
 * Límite mensual, typed on the pad below, with Sin límite in the slip's corner. With Sin
 * límite on, the amount and the suggestions stay where they are, greyed out and unusable:
 * what was typed is kept, comes back with the switch, and is not sent. Text pasted that
 * is not an amount is shown as it came, with why in place of the label.
 */
@Composable
private fun Limit(state: CategoryEditorState, onAction: (CategoryEditorAction) -> Unit) {
    val label = stringResource(R.string.category_editor_limit)
    if (state.limitLocked) {
        Slip(Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = SelloTheme.colors.inkSoft
            )
            Text(
                text = stringResource(R.string.category_edit_limit_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.padding(top = SelloTheme.spacing.sm)
            )
            CarrySwitch(state, onAction)
        }
        return
    }
    val usable = state.editable && !state.unlimited
    val rejected = state.limitText.takeIf { it.isNotBlank() && state.limitAmount == null }
    EntryAmountSlip(
        label = label,
        amount = state.limitAmount,
        modifier = Modifier.testTag(EDITOR_LIMIT_TAG),
        error = state.limitError?.let { stringResource(it.text()) },
        date = {
            SwitchRow(
                title = stringResource(R.string.category_editor_unlimited),
                checked = state.unlimited,
                onCheckedChange = { onAction(CategoryEditorAction.UnlimitedChanged(it)) },
                modifier = Modifier.width(IntrinsicSize.Max).testTag(EDITOR_UNLIMITED_TAG),
                enabled = state.editable
            )
        },
        rejectedText = rejected,
        onPaste = if (usable) {
            { onAction(CategoryEditorAction.LimitChanged(it)) }
        } else {
            null
        },
        amountAlpha = if (state.unlimited) GREYED_OUT_ALPHA else 1f
    ) {
        ChipFlow(Modifier.padding(top = SelloTheme.spacing.sm)) {
            state.suggestions.forEach { suggestion ->
                SelloChip(
                    label = suggestion.label,
                    selected = !state.unlimited && state.limitText == suggestion.pesos.toString(),
                    onClick = { onAction(CategoryEditorAction.SuggestionPicked(suggestion.pesos)) },
                    modifier = Modifier.semantics { contentDescription = suggestion.spoken },
                    enabled = usable
                )
            }
        }
        state.limitTotal?.let { LimitTotalLine(it, Modifier.padding(top = SelloTheme.spacing.sm)) }
        Text(
            text = limitScope(state),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag(EDITOR_SCOPE_TAG)
        )
        CarrySwitch(state, onAction)
    }
}

/** What went wrong, or that nothing changed yet, between the limit and the button. */
@Composable
private fun Said(state: CategoryEditorState) {
    if (state.savedParts.isNotEmpty() && state.saveError != null) {
        val saved = state.savedParts.map { stringResource(it.text()) }.joinToString(", ")
        FieldMessageText(
            FieldMessage(stringResource(R.string.category_edit_saved_parts, saved)),
            Modifier.testTag(EDITOR_SAVED_PARTS_TAG)
        )
    } else {
        state.saveError?.let { error ->
            FieldMessageText(
                FieldMessage(stringResource(error.text(state.editing))),
                Modifier.testTag(EDITOR_SAVE_ERROR_TAG)
            )
        }
    }
    if (state.editing && !state.changed && state.saveError == null && !state.busy) {
        FieldMessageText(
            FieldMessage(stringResource(R.string.category_edit_no_changes), isError = false),
            Modifier.testTag(EDITOR_NO_CHANGES_TAG)
        )
    }
}

/** What is missing, then the button: create or save, ask again, or settle a conflict. */
@Composable
private fun Action(state: CategoryEditorState, onAction: (CategoryEditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
        if (state.name.isBlank() && state.editable && !state.editing) {
            FieldMessageText(
                FieldMessage(
                    stringResource(R.string.category_editor_missing_name),
                    isError = false
                ),
                Modifier.testTag(EDITOR_MISSING_TAG)
            )
        }
        when (state.saveError) {
            SaveError.Unknown -> SelloButton(
                text = stringResource(R.string.category_editor_check_again),
                onClick = { onAction(CategoryEditorAction.Retry) },
                modifier = Modifier.fillMaxWidth().testTag(EDITOR_SUBMIT_TAG),
                loading = state.busy
            )

            // The person decides: what the category is now, or what they typed over it.
            SaveError.Conflict -> {
                SelloButton(
                    text = stringResource(R.string.category_edit_conflict_reload),
                    onClick = { onAction(CategoryEditorAction.ReloadDiscardingDraft) },
                    modifier = Modifier.fillMaxWidth().testTag(EDITOR_CONFLICT_RELOAD_TAG)
                )
                SelloButton(
                    text = stringResource(R.string.category_edit_conflict_keep),
                    onClick = { onAction(CategoryEditorAction.ReloadKeepingDraft) },
                    modifier = Modifier.fillMaxWidth().testTag(EDITOR_CONFLICT_KEEP_TAG),
                    kind = ButtonKind.Outlined
                )
            }

            else -> SelloButton(
                text = stringResource(
                    if (state.editing) {
                        R.string.category_edit_submit
                    } else {
                        R.string.category_editor_create
                    }
                ),
                onClick = { onAction(CategoryEditorAction.Submit) },
                modifier = Modifier.fillMaxWidth().testTag(EDITOR_SUBMIT_TAG),
                icon = if (state.editing) null else SelloIcon.Add,
                enabled = state.canSubmit,
                loading = state.busy
            )
        }
    }
}

/** Which months a limit typed here is for. A new category's applies from this month. */
@Composable
private fun limitScope(state: CategoryEditorState): String {
    val month = state.targetMonth ?: return stringResource(R.string.category_editor_limit_scope)
    return stringResource(
        if (state.historical) {
            R.string.category_edit_scope_past
        } else {
            R.string.category_edit_scope_current
        },
        monthNames().title(month)
    )
}

/** What the month's total limit would be, or is, with the limit in the form. */
@Composable
private fun LimitTotalLine(total: LimitTotal, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    val lead = stringResource(
        if (total.changes) {
            R.string.category_editor_limit_total_changes
        } else {
            R.string.category_editor_limit_total_same
        },
        monthNames().inSentence(total.month)
    )
    val style = MaterialTheme.typography.bodySmall
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag(EDITOR_TOTAL_TAG)
            .clearAndSetSemantics { contentDescription = "$lead ${total.amount.spoken}" }
    ) {
        Icon(
            SelloIcon.Info.painter(),
            contentDescription = null,
            tint = colors.inkSoft,
            modifier = Modifier.size(16.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs)) {
            Text(lead, style = style, color = colors.inkSoft)
            MoneyText(
                total.amount,
                textStyle = style.copy(fontWeight = FontWeight.W700),
                color = colors.ink
            )
        }
    }
}

/** As the design system greys out a switch that cannot be used. */
private const val GREYED_OUT_ALPHA = 0.38f

private fun NameError.text() = when (this) {
    NameError.Required -> R.string.category_editor_name_required
    NameError.TooLong -> R.string.category_editor_name_too_long
    NameError.NotAllowed -> R.string.category_editor_name_not_allowed
    NameError.Taken -> R.string.category_editor_name_taken
}

private fun LimitError.text() = when (this) {
    LimitError.Required -> R.string.category_editor_limit_required
    LimitError.NotAnAmount -> R.string.category_editor_limit_invalid
    LimitError.TooLarge -> R.string.expense_entry_amount_too_large
}

private fun EditPart.text() = when (this) {
    EditPart.Name -> R.string.category_edit_part_name
    EditPart.Icon -> R.string.category_edit_part_icon
    EditPart.Limit -> R.string.category_edit_part_limit
}

/** A change that was refused left the category as it was; a new one was not created. */
private fun SaveError.text(editing: Boolean) = when (this) {
    SaveError.NotSaved ->
        if (editing) R.string.category_edit_not_saved else R.string.category_editor_not_saved

    SaveError.DataChanged ->
        if (editing) R.string.category_edit_data_changed else R.string.category_editor_data_changed

    SaveError.Unknown -> R.string.category_editor_unknown

    SaveError.Conflict -> R.string.category_edit_conflict

    SaveError.CategoryGone -> R.string.category_edit_gone

    SaveError.PaymentsPending -> R.string.category_archive_payments_pending
}

const val EDITOR_LOADING_TAG = "category-editor:loading"
const val EDITOR_LOAD_FAILED_TAG = "category-editor:load-failed"
const val EDITOR_PREREQUISITE_TAG = "category-editor:prerequisite"
const val EDITOR_NAME_TAG = "category-editor:name"
const val EDITOR_ICON_TAG = "category-editor:icon"
const val EDITOR_ICONS_TAG = "category-editor:icons"
const val EDITOR_PAD_TAG = "category-editor:pad"
const val EDITOR_MISSING_TAG = "category-editor:missing"
const val EDITOR_UNLIMITED_TAG = "category-editor:unlimited"
const val EDITOR_LIMIT_TAG = "category-editor:limit"
const val EDITOR_SAVE_ERROR_TAG = "category-editor:save-error"
const val EDITOR_SUBMIT_TAG = "category-editor:submit"
const val EDITOR_ARCHIVE_TAG = "category-editor:archive"
const val EDITOR_ARCHIVED_TAG = "category-editor:archived"
const val EDITOR_SCOPE_TAG = "category-editor:scope"
const val EDITOR_SAVED_PARTS_TAG = "category-editor:saved-parts"
const val EDITOR_NO_CHANGES_TAG = "category-editor:no-changes"
const val EDITOR_CONFLICT_RELOAD_TAG = "category-editor:conflict-reload"
const val EDITOR_CONFLICT_KEEP_TAG = "category-editor:conflict-keep"
const val EDITOR_TOTAL_TAG = "category-editor:total"
