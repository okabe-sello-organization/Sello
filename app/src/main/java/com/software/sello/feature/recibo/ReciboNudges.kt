package com.software.sello.feature.recibo

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import com.software.sello.R
import com.software.sello.designsystem.component.NudgeAction
import com.software.sello.designsystem.component.NudgeRow
import com.software.sello.designsystem.component.nudgeText
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Recibo's one quiet line of guidance (SELLO-211): under the month's answer, the Guía hint,
 * whose text opens the Guía and whose ✕ closes it, or the backup reminder, with No volver
 * a mostrar on the left and Ir a Respaldo on the right; under the goal card, how to put a
 * goal first there (SELLO-213), with a ✕.
 */
@Composable
internal fun ReciboNudgeRow(
    nudge: ReciboNudge,
    onAction: (ReciboAction) -> Unit,
    modifier: Modifier = Modifier
) {
    when (nudge) {
        ReciboNudge.GuideHint -> NudgeRow(
            icon = SelloIcon.Settings,
            text = nudgeText(
                stringResource(R.string.recibo_guide_hint),
                stringResource(R.string.recibo_guide_hint_name)
            ),
            onClose = { onAction(ReciboAction.CloseGuideHint) },
            closeLabel = stringResource(R.string.recibo_guide_hint_close),
            modifier = modifier
                .testTag(RECIBO_GUIDE_HINT_TAG)
                .clip(SelloTheme.shapes.inset)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.recibo_guide_hint_open)
                ) { onAction(ReciboAction.OpenGuide) }
        )

        is ReciboNudge.BackupReminder -> NudgeRow(
            icon = SelloIcon.Backup,
            text = nudge.daysSinceBackup?.let { days ->
                nudgeText(
                    stringResource(R.string.recibo_backup_reminder),
                    pluralStringResource(
                        R.plurals.recibo_backup_reminder_days,
                        days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        days
                    )
                )
            } ?: AnnotatedString(stringResource(R.string.recibo_backup_reminder_none)),
            link = NudgeAction(stringResource(R.string.recibo_backup_reminder_off)) {
                onAction(ReciboAction.TurnOffBackupReminder)
            },
            pill = NudgeAction(stringResource(R.string.recibo_backup_reminder_go)) {
                onAction(ReciboAction.OpenBackup)
            },
            modifier = modifier.testTag(RECIBO_BACKUP_REMINDER_TAG)
        )

        ReciboNudge.Goal -> NudgeRow(
            icon = SelloIcon.Star,
            text = withBold(
                stringResource(R.string.recibo_goal_nudge),
                stringResource(R.string.goal_pin_off)
            ),
            onClose = { onAction(ReciboAction.CloseGoalNudge) },
            closeLabel = stringResource(R.string.recibo_goal_nudge_close),
            modifier = modifier.testTag(RECIBO_GOAL_NUDGE_TAG)
        )
    }
}

const val RECIBO_GUIDE_HINT_TAG = "recibo:nudge:guide"
const val RECIBO_BACKUP_REMINDER_TAG = "recibo:nudge:backup"
const val RECIBO_GOAL_NUDGE_TAG = "recibo:nudge:goal"
