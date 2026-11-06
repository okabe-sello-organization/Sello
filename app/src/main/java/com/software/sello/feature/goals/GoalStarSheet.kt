package com.software.sello.feature.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonPair
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldTone
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloSheet
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Starring a sixth goal: the five starred goals in Recibo's order, each with its icon,
 * name and percentage. Picking one saves at once, moving its star to [goal]; Cancelar,
 * back or the scrim change nothing. A refused or unknown save says so in the sheet and
 * keeps the pick; while the save is on its way nothing else may be picked.
 */
@Composable
internal fun ReplaceStarSheet(
    goal: String,
    sheet: ReplaceSheet,
    busy: Boolean,
    error: GoalDetailError?,
    onAction: (GoalDetailAction) -> Unit
) {
    val dismiss = { if (!busy) onAction(GoalDetailAction.CancelReplace) }
    BackHandler { dismiss() }
    SelloSheet(
        title = pluralStringResource(
            R.plurals.goal_replace_title,
            sheet.choices.size,
            sheet.choices.size
        ),
        onDismiss = dismiss,
        modifier = Modifier.testTag(GOAL_REPLACE_TAG)
    ) {
        val line = stringResource(R.string.goal_replace_line, goal)
        val at = line.indexOf(goal)
        Text(
            buildAnnotatedString {
                append(line)
                if (at >= 0) {
                    addStyle(
                        SpanStyle(fontWeight = SelloTheme.type.weight.emphasis),
                        at,
                        at + goal.length
                    )
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 6.dp)
        )
        Column(Modifier.selectableGroup()) {
            sheet.choices.forEachIndexed { index, choice ->
                ChoiceRow(
                    choice,
                    picked = choice.id == sheet.picked,
                    last = index == sheet.choices.lastIndex,
                    enabled = !busy && error != GoalDetailError.Unknown,
                    onClick = { onAction(GoalDetailAction.Replace(choice.id)) }
                )
            }
        }
        error?.let { SheetError(it, sheet.choices.size) }
        val retry = error != null && sheet.picked != null
        val cancel = @Composable {
            SelloButton(
                text = stringResource(R.string.goal_replace_cancel),
                onClick = dismiss,
                kind = ButtonKind.Outlined,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag(GOAL_REPLACE_CANCEL_TAG)
            )
        }
        if (retry) {
            ButtonPair(1f, 2f, Modifier.padding(top = 10.dp), first = cancel) {
                SelloButton(
                    text = stringResource(
                        if (error == GoalDetailError.Unknown) {
                            R.string.goal_error_check
                        } else {
                            R.string.goal_error_retry
                        }
                    ),
                    onClick = {
                        if (error == GoalDetailError.Unknown) {
                            onAction(GoalDetailAction.Retry)
                        } else {
                            sheet.picked?.let { onAction(GoalDetailAction.Replace(it)) }
                        }
                    },
                    loading = busy,
                    modifier = Modifier.testTag(GOAL_REPLACE_RETRY_TAG)
                )
            }
        } else {
            Box(Modifier.padding(top = 10.dp)) { cancel() }
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: StarChoice,
    picked: Boolean,
    last: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = SelloTheme.colors
    val percentage = stringResource(R.string.goal_percent, choice.percentage)
    val spoken = stringResource(
        R.string.goal_replace_choice_spoken,
        choice.name,
        choice.percentage.toString()
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .testTag(goalReplaceChoiceTag(choice.id))
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .drawBehind {
                // A rule between rows, as the move list draws it.
                if (!last && !picked) {
                    val line = 1.dp.toPx()
                    drawLine(
                        colors.rule,
                        Offset(0f, size.height - line / 2),
                        Offset(size.width, size.height - line / 2),
                        line
                    )
                }
            }
            .background(
                if (picked) colors.paperAlt else Color.Transparent,
                SelloTheme.shapes.inset
            )
            .selectable(picked, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = 6.dp, horizontal = if (picked) SelloTheme.spacing.sm else 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(34.dp).background(colors.paperAlt, CircleShape)
        ) { Icon(goalIcon(choice.iconKey).painter(), null, Modifier.size(19.dp), colors.brand) }
        Text(
            choice.name,
            style = SelloTheme.type.rowTitle,
            modifier = Modifier.weight(1f).clearAndSetSemantics {}
        )
        Text(
            percentage,
            style = SelloTheme.type.rowTitle,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Icon(SelloIcon.Star.painter(true), null, Modifier.size(20.dp), colors.brand)
    }
}

/** Nothing changed (refused) or not known yet (unknown): said in the sheet, over its actions. */
@Composable
private fun SheetError(error: GoalDetailError, count: Int) {
    // An unknown outcome is a doubt to clear, in the warning tone; a refusal is an error.
    val (text, tone) = when (error) {
        GoalDetailError.NotSaved -> stringResource(R.string.goal_replace_refused_lead) + " " +
            pluralStringResource(R.plurals.goal_replace_refused_body, count, count) to
            FieldTone.Error

        GoalDetailError.Unknown -> stringResource(R.string.goal_error_unknown) to FieldTone.Warning
    }
    FieldMessageText(
        FieldMessage(text, tone),
        Modifier.padding(top = SelloTheme.spacing.sm).testTag(GOAL_REPLACE_ERROR_TAG)
    )
}

const val GOAL_REPLACE_TAG = "goal.replace"
const val GOAL_REPLACE_CANCEL_TAG = "goal.replace.cancel"
const val GOAL_REPLACE_RETRY_TAG = "goal.replace.retry"
const val GOAL_REPLACE_ERROR_TAG = "goal.replace.error"

fun goalReplaceChoiceTag(id: String) = "goal.replace.choice.$id"
