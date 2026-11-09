package com.software.sello.feature.recibo

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.software.sello.R
import com.software.sello.designsystem.component.NudgeRow
import com.software.sello.designsystem.component.nudgeText
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.month.MonthNames

/**
 * Category detail's one quiet line of guidance under its header (SELLO-212), each with a
 * ✕ that closes it for good. The arrastre line's text opens Editar categoría, where carry
 * is switched on.
 */
@Composable
internal fun CategoryDetailNudgeRow(
    nudge: DetailNudge,
    months: MonthNames,
    onAction: (CategoryDetailAction) -> Unit,
    onEditCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val close = { onAction(CategoryDetailAction.CloseNudge) }
    val closeLabel = stringResource(R.string.category_detail_nudge_close)
    when (nudge) {
        is DetailNudge.Carry -> {
            val edit = stringResource(R.string.category_detail_edit)
            NudgeRow(
                icon = SelloIcon.SwapVert,
                text = nudgeText(
                    // The month goes in first; the editor's name is the bold part.
                    stringResource(
                        if (nudge.surplus) {
                            R.string.category_detail_nudge_carry_surplus
                        } else {
                            R.string.category_detail_nudge_carry_deficit
                        },
                        months.inSentence(nudge.month),
                        PLACEHOLDER
                    ),
                    edit
                ),
                onClose = close,
                closeLabel = closeLabel,
                modifier = modifier
                    .testTag(CATEGORY_DETAIL_NUDGE_TAG)
                    .clip(SelloTheme.shapes.inset)
                    .clickable(role = Role.Button, onClickLabel = edit, onClick = onEditCategory)
            )
        }

        DetailNudge.FixedPayment -> NudgeRow(
            icon = SelloIcon.Autorenew,
            text = nudgeText(
                stringResource(R.string.category_detail_nudge_fixed),
                stringResource(R.string.repeat_add_expense)
            ),
            onClose = close,
            closeLabel = closeLabel,
            modifier = modifier.testTag(CATEGORY_DETAIL_NUDGE_TAG)
        )

        is DetailNudge.Deleting -> {
            val seconds = nudge.undoSeconds.toInt()
            NudgeRow(
                icon = SelloIcon.Delete,
                text = pluralStringResource(
                    R.plurals.category_detail_nudge_deleting,
                    seconds,
                    seconds
                ),
                onClose = close,
                closeLabel = closeLabel,
                modifier = modifier.testTag(CATEGORY_DETAIL_NUDGE_TAG)
            )
        }
    }
}

private const val PLACEHOLDER = "%1\$s"

const val CATEGORY_DETAIL_NUDGE_TAG = "detail:nudge"
