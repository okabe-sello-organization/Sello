package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldTone
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A goal change that did not finish, such as Marcar como lograda: titled as a save, never
 * as a failed read. An unknown outcome is said in the warning tone and only offers
 * Comprobar si se guardó, which asks again and never sends twice; a certain failure is an
 * error and offers to try again.
 */
@Composable
internal fun GoalSaveProblem(
    error: GoalDetailError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unknown = error == GoalDetailError.Unknown
    Slip(modifier.fillMaxWidth(), pinked = false) {
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
            Text(
                stringResource(
                    if (unknown) {
                        R.string.goal_save_unknown_title
                    } else {
                        R.string.goal_save_failed_title
                    }
                ),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                color = SelloTheme.colors.ink
            )
            FieldMessageText(
                FieldMessage(
                    stringResource(
                        if (unknown) R.string.goal_error_unknown else R.string.goal_error_not_saved
                    ),
                    if (unknown) FieldTone.Warning else FieldTone.Error
                )
            )
            SelloButton(
                stringResource(
                    if (unknown) R.string.expense_entry_check_again else R.string.goal_error_retry
                ),
                onRetry,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Outlined
            )
        }
    }
}
