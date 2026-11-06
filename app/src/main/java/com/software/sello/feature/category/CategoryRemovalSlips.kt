package com.software.sello.feature.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChangeRow
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.DialogSlip
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.carry.CarryCorrectionContent
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.month.monthNames
import java.time.YearMonth

/**
 * The slip or sheet of the step deleting the category is at (borrar-categoria.html): the
 * counts and the choice, the categories to move to, each confirmation, and when it did
 * not happen, that nothing was deleted or moved, why and the way forward.
 */
@Composable
internal fun RemovalSlips(removal: RemovalState, onAction: (CategoryEditorAction) -> Unit) {
    val name = removal.name
    val busy = removal.busy
    val dismiss = { onAction(RemovalAction.Dismiss) }
    val confirm = { onAction(RemovalAction.Confirm) }
    when (val step = removal.step) {
        RemovalStep.Counting -> RemovalDialog(
            title = stringResource(R.string.category_delete_title, name),
            onDismiss = {},
            modifier = Modifier.testTag(REMOVAL_COUNTING_TAG)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Body(AnnotatedString(stringResource(R.string.category_delete_counting, name)))
            }
        }

        RemovalStep.Empty -> ConfirmSlip(
            title = stringResource(R.string.category_delete_title, name),
            confirmLabel = stringResource(R.string.category_delete_confirm),
            onConfirm = confirm,
            onDismiss = dismiss,
            modifier = Modifier.testTag(REMOVAL_EMPTY_TAG),
            busy = busy
        ) {
            Body(
                withUndoable(stringResource(R.string.category_delete_empty_body))
            )
        }

        is RemovalStep.Choose -> ChooseSlip(name, step, onAction)

        is RemovalStep.Pick -> PickSheet(name, step, busy, onAction)

        is RemovalStep.Move -> ConfirmSlip(
            title = stringResource(R.string.category_delete_move_title, step.facts.target, name),
            confirmLabel = stringResource(R.string.category_delete_move_confirm),
            onConfirm = confirm,
            onDismiss = dismiss,
            modifier = Modifier.testTag(REMOVAL_MOVE_TAG),
            busy = busy
        ) {
            Body(moveBody(name, step.counts, step.facts.target))
            MoveRows(name, step.facts)
        }

        is RemovalStep.All -> ConfirmSlip(
            title = stringResource(R.string.category_delete_all_title, name),
            confirmLabel = stringResource(R.string.category_delete_all),
            onConfirm = confirm,
            onDismiss = dismiss,
            modifier = Modifier.testTag(REMOVAL_ALL_TAG),
            busy = busy,
            confirmEnabled = step.canDelete
        ) { AllContent(step, busy, onAction) }

        is RemovalStep.Correction -> ConfirmSlip(
            title = stringResource(R.string.category_delete_move_title, step.target, name),
            confirmLabel = stringResource(R.string.category_delete_move_confirm),
            onConfirm = confirm,
            onDismiss = dismiss,
            modifier = Modifier.testTag(REMOVAL_CORRECTION_TAG),
            busy = busy
        ) { CarryCorrectionContent(step.list, step.changed) }

        is RemovalStep.Checking -> RemovalDialog(
            title = stringResource(R.string.category_delete_checking_title),
            onDismiss = {},
            modifier = Modifier.testTag(REMOVAL_CHECKING_TAG),
            action = if (step.failed) {
                {
                    SelloButton(
                        text = stringResource(R.string.category_delete_check_again),
                        onClick = { onAction(RemovalAction.Next) },
                        kind = ButtonKind.Outlined,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                null
            }
        ) {
            val once = stringResource(R.string.category_delete_checking_once)
            Body(
                bold(
                    stringResource(R.string.category_delete_checking_body, name) + " " + once,
                    once
                )
            )
        }

        is RemovalStep.Problem -> ProblemSlip(name, step.problem, busy, onAction)
    }
}

/** It holds expenses or fixed payments: the counts first, then what to do with them. */
@Composable
private fun ChooseSlip(
    name: String,
    step: RemovalStep.Choose,
    onAction: (CategoryEditorAction) -> Unit
) {
    RemovalDialog(
        title = stringResource(R.string.category_delete_title, name),
        onDismiss = { onAction(RemovalAction.Dismiss) },
        modifier = Modifier.testTag(REMOVAL_CHOOSE_TAG),
        action = {
            SelloButton(
                text = stringResource(R.string.category_delete_cancel),
                onClick = { onAction(RemovalAction.Dismiss) },
                kind = ButtonKind.Outlined,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Body(holds(name, step.counts))
        Spacer(Modifier.height(10.dp))
        ChoiceRow(
            icon = SelloIcon.ReceiptLong,
            title = stringResource(R.string.category_delete_move),
            note = stringResource(R.string.category_delete_move_note),
            onClick = { onAction(RemovalAction.Move) },
            modifier = Modifier.testTag(REMOVAL_MOVE_CHOICE_TAG)
        )
        ChoiceRow(
            icon = SelloIcon.Delete,
            title = stringResource(R.string.category_delete_all),
            note = stringResource(R.string.category_delete_all_note),
            onClick = { onAction(RemovalAction.DeleteAll) },
            modifier = Modifier.testTag(REMOVAL_ALL_CHOICE_TAG),
            danger = true
        )
        // An archived category has nothing to archive: only the two choices.
        if (step.canArchive) {
            val link = stringResource(R.string.category_delete_archive_link)
            val text = stringResource(R.string.category_delete_archive_hint, link)
            Text(
                buildAnnotatedString {
                    val at = text.indexOf(link)
                    append(text.substring(0, at))
                    withStyle(
                        SpanStyle(color = SelloTheme.colors.brand, fontWeight = FontWeight.W800)
                    ) { append(link) }
                    append(text.substring(at + link.length))
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onAction(RemovalAction.ArchiveInstead) }
                    .padding(vertical = 12.dp)
                    .testTag(REMOVAL_ARCHIVE_TAG)
            )
        }
    }
}

/** Mover → the categories not archived, but this one, with what each has left this month. */
@Composable
private fun PickSheet(
    name: String,
    step: RemovalStep.Pick,
    busy: Boolean,
    onAction: (CategoryEditorAction) -> Unit
) {
    val dismiss = { onAction(RemovalAction.Dismiss) }
    BackHandler { dismiss() }
    RepeatSheet(
        title = stringResource(R.string.category_delete_pick_title, name),
        onDismiss = dismiss,
        modifier = Modifier.testTag(REMOVAL_PICK_TAG)
    ) {
        Caption(
            stringResource(
                R.string.category_delete_pick_caption,
                contents(step.counts),
                monthNames().inSentence(step.month)
            )
        )
        val targets = step.targets
        when {
            step.failed -> {
                Body(AnnotatedString(stringResource(R.string.category_delete_pick_failed)))
                SelloButton(
                    text = stringResource(R.string.category_delete_retry),
                    onClick = { onAction(RemovalAction.Next) },
                    kind = ButtonKind.Outlined,
                    modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.sm)
                )
            }

            targets == null -> Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(Modifier.size(24.dp)) }

            targets.isEmpty() ->
                Body(AnnotatedString(stringResource(R.string.category_delete_pick_none)))

            else -> targets.forEachIndexed { index, target ->
                TargetRow(
                    target = target,
                    last = index == targets.lastIndex,
                    enabled = !busy,
                    onClick = { onAction(RemovalAction.Pick(target.id)) }
                )
            }
        }
        Caption(stringResource(R.string.category_delete_pick_archived), top = 8.dp)
    }
}

@Composable
private fun TargetRow(target: MoveTarget, last: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = SelloTheme.colors
    val spoken =
        stringResource(R.string.category_delete_pick_spoken, target.name, target.amount.spoken)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .testTag(removalTargetTag(target.id))
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .drawBehind {
                // A rule between rows, as the fixed payments' list draws it.
                if (!last) {
                    val line = 1.dp.toPx()
                    drawLine(
                        colors.rule,
                        Offset(0f, size.height - line / 2),
                        Offset(size.width, size.height - line / 2),
                        line
                    )
                }
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        IconCircle(CategoryIcons.iconFor(target.iconKey), colors.brand)
        Text(
            target.name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
            modifier = Modifier.weight(1f)
        )
        MoneyText(
            target.amount,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800)
        )
    }
}

/** The move's exact facts: the spending that moves this month and the limits that go or stay. */
@Composable
private fun MoveRows(name: String, facts: MoveFacts) {
    val month = monthNames().inSentence(facts.month)
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val spent = stringResource(R.string.category_delete_row_spent, facts.target, month)
        ChangeRow(
            spent,
            stringResource(
                R.string.category_delete_change,
                facts.targetSpent.shown(),
                facts.targetSpentAfter.shown()
            ),
            stringResource(
                R.string.category_delete_change_spoken,
                spent,
                facts.targetSpent.spoken,
                facts.targetSpentAfter.spoken
            )
        )
        facts.sourceLimit?.let { limit ->
            val label = stringResource(R.string.category_delete_row_source_limit, name, month)
            val value = stringResource(R.string.category_delete_goes, limit.shown())
            val spoken = stringResource(R.string.category_delete_goes, limit.spoken())
            ChangeRow(
                label,
                value,
                stringResource(R.string.category_delete_row_spoken, label, spoken)
            )
        }
        facts.targetLimit?.let { limit ->
            val label = stringResource(R.string.category_delete_row_target_limit, facts.target)
            val value = stringResource(R.string.category_delete_stays, limit.shown())
            val spoken = stringResource(R.string.category_delete_stays, limit.spoken())
            ChangeRow(
                label,
                value,
                stringResource(R.string.category_delete_row_spoken, label, spoken)
            )
        }
    }
}

/** Borrar todo: what goes for good, that past months change, and the word to type. */
@Composable
private fun AllContent(
    step: RemovalStep.All,
    busy: Boolean,
    onAction: (CategoryEditorAction) -> Unit
) {
    val counts = step.counts
    Body(AnnotatedString(stringResource(R.string.category_delete_all_lead)))
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (counts.expenses > 0) {
            val expenses = expensesText(counts.expenses)
            val label = shortRange(counts.firstMonth, counts.lastMonth)
                ?.let { stringResource(R.string.category_delete_all_expenses_row, expenses, it) }
                ?: expenses
            ChangeRow(
                label,
                counts.total.shown(),
                stringResource(R.string.category_delete_row_spoken, label, counts.total.spoken)
            )
        }
        if (counts.schedules > 0) {
            val label = stringResource(R.string.category_delete_all_schedules_row)
            val value = countText(counts.schedules)
            ChangeRow(
                label,
                value,
                stringResource(R.string.category_delete_row_spoken, label, value)
            )
        }
        val limits = stringResource(R.string.category_delete_all_limits_row)
        val every = stringResource(R.string.category_delete_all_limits_value)
        ChangeRow(limits, every, stringResource(R.string.category_delete_row_spoken, limits, every))
    }
    Body(
        withUndoable(stringResource(R.string.category_delete_all_past)),
        Modifier.padding(top = 10.dp)
    )
    FormField(
        label = stringResource(R.string.category_delete_word_label),
        value = step.typed,
        onValueChange = { onAction(RemovalAction.Word(it)) },
        modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag(REMOVAL_WORD_TAG),
        enabled = !busy,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        )
    )
}

/** Nothing was deleted or moved: why, and the one way forward. */
@Composable
private fun ProblemSlip(
    name: String,
    problem: RemovalProblem,
    busy: Boolean,
    onAction: (CategoryEditorAction) -> Unit
) {
    val title = when (problem) {
        is RemovalProblem.Changed -> stringResource(R.string.category_delete_changed_title, name)

        is RemovalProblem.TargetClosed ->
            stringResource(R.string.category_delete_target_title, problem.target)

        is RemovalProblem.TargetCarry ->
            stringResource(R.string.category_delete_carry_title, problem.target)

        is RemovalProblem.NotSaved -> stringResource(R.string.category_delete_storage_title)

        RemovalProblem.CannotCount -> stringResource(R.string.category_delete_count_title)
    }
    val forward = when (problem) {
        is RemovalProblem.Changed -> R.string.category_delete_review

        is RemovalProblem.TargetClosed, is RemovalProblem.TargetCarry ->
            R.string.category_delete_choose_other

        is RemovalProblem.NotSaved, RemovalProblem.CannotCount -> R.string.category_delete_retry
    }
    val nothing = stringResource(R.string.category_delete_nothing)
    val why = when (problem) {
        is RemovalProblem.Changed -> {
            val before = problem.before
            val now = problem.now
            val counts = if (before != null &&
                (before.expenses != now.expenses || before.total != now.total)
            ) {
                pluralStringResource(
                    R.plurals.category_delete_changed_counts,
                    now.expenses,
                    name,
                    countText(now.expenses),
                    now.total.shown(),
                    countText(before.expenses),
                    before.total.shown()
                )
            } else {
                stringResource(R.string.category_delete_changed_body, name)
            }
            counts + " " + stringResource(R.string.category_delete_changed_review)
        }

        is RemovalProblem.TargetClosed ->
            stringResource(R.string.category_delete_target_body, problem.target, name)

        is RemovalProblem.TargetCarry ->
            stringResource(R.string.category_delete_carry_body, problem.target, name)

        is RemovalProblem.NotSaved -> problem.counts?.let {
            stringResource(R.string.category_delete_storage_body, name, keeps(it))
        } ?: stringResource(R.string.category_delete_storage_body_plain, name)

        RemovalProblem.CannotCount -> stringResource(R.string.category_delete_count_body, name)
    }
    // It did not happen: an action that failed, inside the dialog, with its one way
    // forward. Counting failed before anything was offered: nothing could be deleted.
    val message = if (problem == RemovalProblem.CannotCount) why else "$nothing $why"
    RemovalDialog(
        title = title,
        onDismiss = { if (!busy) onAction(RemovalAction.Dismiss) },
        modifier = Modifier.testTag(REMOVAL_PROBLEM_TAG),
        action = {
            SelloButton(
                text = stringResource(R.string.category_delete_cancel),
                onClick = { onAction(RemovalAction.Dismiss) },
                kind = ButtonKind.Outlined,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        StatusSlip(
            kind = StatusKind.ActionFailed(),
            message = message,
            action = StatusAction(stringResource(forward), busy = busy) {
                onAction(RemovalAction.Next)
            }
        )
    }
}

/**
 * A slip over the editor for a step with one answer or none: the shared [DialogSlip], with
 * [action], when there is one, under the tear line.
 */
@Composable
private fun RemovalDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    DialogSlip(
        title = title,
        onDismissRequest = onDismiss,
        modifier = modifier,
        buttons = action?.let { { it() } },
        content = content
    )
}

/** One choice of the slip that asks what to do: its icon, what it does and why, and ›. */
@Composable
private fun ChoiceRow(
    icon: SelloIcon,
    title: String,
    note: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val colors = SelloTheme.colors
    val ink = if (danger) colors.semantic.loss else colors.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        IconCircle(icon, if (danger) colors.semantic.loss else colors.brand)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
                color = ink
            )
            Text(note, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        }
        Icon(SelloIcon.ChevronRight.painter(), null, tint = colors.inkSoft)
    }
}

@Composable
private fun IconCircle(icon: SelloIcon, tint: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(34.dp).background(SelloTheme.colors.paperAlt, CircleShape)
    ) { Icon(icon.painter(), null, Modifier.size(19.dp), tint) }
}

@Composable
private fun Body(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

@Composable
private fun Caption(text: String, top: Dp = 0.dp) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = top, bottom = 6.dp)
    )
}

/** "Café tiene 34 gastos por 412.000, de mayo a octubre de 2026, y 1 pago fijo. ¿Qué…?" */
@Composable
private fun holds(name: String, counts: RemovalCounts): AnnotatedString {
    val expenses = expensesText(counts.expenses)
    val schedules = schedulesText(counts.schedules)
    val total = counts.total.shown()
    val range = longRange(counts.firstMonth, counts.lastMonth).orEmpty()
    val sentence = when {
        counts.expenses > 0 && counts.schedules > 0 -> stringResource(
            R.string.category_delete_has_both,
            name,
            expenses,
            total,
            range,
            schedules
        )

        counts.expenses > 0 ->
            stringResource(R.string.category_delete_has_expenses, name, expenses, total, range)

        else -> stringResource(R.string.category_delete_has_schedules, name, schedules)
    }
    val question = pluralStringResource(
        R.plurals.category_delete_what_now,
        counts.expenses + counts.schedules
    )
    return bold(
        "$sentence $question",
        *listOfNotNull(
            expenses.takeIf { counts.expenses > 0 },
            total.takeIf { counts.expenses > 0 },
            schedules.takeIf { counts.schedules > 0 }
        ).toTypedArray()
    )
}

/** "Los 34 gastos y su pago fijo pasan a Comida, …. Los límites … se borran. Esto no…" */
@Composable
private fun moveBody(name: String, counts: RemovalCounts, target: String): AnnotatedString {
    val expenses = countText(counts.expenses)
    val schedules = countText(counts.schedules)
    val moves = when {
        counts.expenses > 0 && counts.schedules > 0 -> stringResource(
            R.string.category_delete_move_both,
            pluralStringResource(
                R.plurals.category_delete_move_expenses_lead,
                counts.expenses,
                expenses
            ),
            pluralStringResource(
                R.plurals.category_delete_move_schedules_tail,
                counts.schedules,
                schedules
            ),
            target
        )

        counts.expenses > 0 -> pluralStringResource(
            R.plurals.category_delete_move_expenses,
            counts.expenses,
            expenses,
            target
        )

        else -> pluralStringResource(
            R.plurals.category_delete_move_schedules,
            counts.schedules,
            schedules,
            target
        )
    }
    val limits = stringResource(R.string.category_delete_move_limits, name)
    val undoable = stringResource(R.string.category_delete_undoable)
    return bold("$moves $limits $undoable", undoable)
}

/** "34 gastos y 1 pago fijo", or only the part it has. */
@Composable
private fun contents(counts: RemovalCounts): String {
    val expenses = expensesText(counts.expenses)
    val schedules = schedulesText(counts.schedules)
    return when {
        counts.expenses > 0 && counts.schedules > 0 ->
            stringResource(R.string.category_delete_and, expenses, schedules)

        counts.expenses > 0 -> expenses

        else -> schedules
    }
}

/** "sus 34 gastos, sus límites y su pago fijo": what the category still holds. */
@Composable
private fun keeps(counts: RemovalCounts): String {
    val parts = buildList {
        if (counts.expenses > 0) {
            add(
                pluralStringResource(
                    R.plurals.category_delete_keeps_expenses,
                    counts.expenses,
                    countText(counts.expenses)
                )
            )
        }
        add(stringResource(R.string.category_delete_keeps_limits))
        if (counts.schedules > 0) {
            add(
                pluralStringResource(
                    R.plurals.category_delete_move_schedules_tail,
                    counts.schedules,
                    countText(counts.schedules)
                )
            )
        }
    }
    return if (parts.size == 1) {
        parts.single()
    } else {
        stringResource(
            R.string.category_delete_and,
            parts.dropLast(1).joinToString(", "),
            parts.last()
        )
    }
}

@Composable
private fun expensesText(count: Int) =
    pluralStringResource(R.plurals.category_delete_expenses, count, countText(count))

@Composable
private fun schedulesText(count: Int) =
    pluralStringResource(R.plurals.category_delete_schedules, count, countText(count))

/** "de mayo a octubre de 2026", "de diciembre de 2025 a marzo de 2026", "en octubre de 2026". */
@Composable
private fun longRange(first: YearMonth?, last: YearMonth?): String? {
    if (first == null || last == null) return null
    val months = monthNames()
    return when {
        first == last ->
            stringResource(R.string.category_delete_one_month, months.inSentence(first), first.year)

        first.year == last.year -> stringResource(
            R.string.category_delete_range,
            months.inSentence(first),
            months.inSentence(last),
            last.year
        )

        else -> stringResource(
            R.string.category_delete_range_years,
            months.inSentence(first),
            first.year,
            months.inSentence(last),
            last.year
        )
    }
}

/** "may – oct 2026", "dic 2025 – mar 2026", "oct 2026". */
@Composable
private fun shortRange(first: YearMonth?, last: YearMonth?): String? {
    if (first == null || last == null) return null
    val names = stringArrayResource(R.array.month_names_abbreviated)
    fun name(month: YearMonth) = names[month.monthValue - 1]
    return when {
        first == last -> stringResource(
            R.string.category_delete_short_month,
            name(first),
            first.year
        )

        first.year == last.year -> stringResource(
            R.string.category_delete_short_range,
            name(first),
            name(last),
            last.year
        )

        else -> stringResource(
            R.string.category_delete_short_range_years,
            name(first),
            first.year,
            name(last),
            last.year
        )
    }
}

@Composable
private fun withUndoable(text: String): AnnotatedString {
    val undoable = stringResource(R.string.category_delete_undoable)
    return bold("$text $undoable", undoable)
}

@Composable
private fun RemovalLimit.shown(): String = when (this) {
    is RemovalLimit.Finite -> amount.shown()
    RemovalLimit.Unlimited -> stringResource(R.string.category_editor_unlimited)
}

@Composable
private fun RemovalLimit.spoken(): String = when (this) {
    is RemovalLimit.Finite -> amount.spoken
    RemovalLimit.Unlimited -> stringResource(R.string.category_editor_unlimited)
}

/** An amount as a row writes it, with its true minus when it is below zero. */
private fun MoneyTextValue.shown() = (if (sign == MoneySign.Minus) "−" else "") + digits

/** [text] with each of [parts] in bold, in order, as the design writes the counts. */
private fun bold(text: String, vararg parts: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    parts.forEach { part ->
        val start = text.indexOf(part, at)
        if (start < 0) return@forEach
        append(text.substring(at, start))
        withStyle(SpanStyle(fontWeight = FontWeight.W800)) { append(part) }
        at = start + part.length
    }
    append(text.substring(at))
}

const val REMOVAL_COUNTING_TAG = "category-removal:counting"
const val REMOVAL_EMPTY_TAG = "category-removal:empty"
const val REMOVAL_CHOOSE_TAG = "category-removal:choose"
const val REMOVAL_MOVE_CHOICE_TAG = "category-removal:choose.move"
const val REMOVAL_ALL_CHOICE_TAG = "category-removal:choose.all"
const val REMOVAL_ARCHIVE_TAG = "category-removal:choose.archive"
const val REMOVAL_PICK_TAG = "category-removal:pick"
const val REMOVAL_MOVE_TAG = "category-removal:move"
const val REMOVAL_ALL_TAG = "category-removal:all"
const val REMOVAL_WORD_TAG = "category-removal:all.word"
const val REMOVAL_CORRECTION_TAG = "category-removal:correction"
const val REMOVAL_CHECKING_TAG = "category-removal:checking"
const val REMOVAL_PROBLEM_TAG = "category-removal:problem"

fun removalTargetTag(id: String) = "category-removal:target.$id"
