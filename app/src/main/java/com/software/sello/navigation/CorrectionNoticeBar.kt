package com.software.sello.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.feature.goals.CelebrationTicket
import com.software.sello.feature.goals.celebrationLine
import com.software.sello.feature.investments.InvestmentMovementNoticeBar
import com.software.sello.presentation.carry.CarryCorrectionContent
import com.software.sello.presentation.corrections.CorrectionKind
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.CorrectionQuestion
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.corrections.InvestmentMovementNotice
import com.software.sello.presentation.corrections.ScheduleChange
import com.software.sello.presentation.corrections.celebration
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.month.monthNames
import com.software.sello.presentation.recurrence.repeatRuleLabel

/** "Gasto anotado: 48.700 en Comida", with as much as could be read back. */
@Composable
private fun expenseRecorded(figure: String?, category: String?): String = when {
    figure == null -> stringResource(R.string.notice_expense_recorded_plain)
    category == null -> stringResource(R.string.notice_expense_recorded_amount, figure)
    else -> stringResource(R.string.notice_expense_recorded, figure, category)
}

/**
 * What is said about the last correction. Only a deletion that can still be undone
 * has something to press; everything else is a statement that goes away by itself.
 */
@Composable
internal fun CorrectionNoticeBar(notice: CorrectionNotice, onUndo: () -> Unit) {
    val tagged = Modifier.testTag(CORRECTION_NOTICE_TAG)
    when (notice) {
        is CorrectionNotice.Deleted -> SelloNotice(
            message = withCarryMonths(
                stringResource(
                    if (notice.undoFailed) {
                        R.string.correction_undo_failed
                    } else {
                        R.string.correction_deleted
                    }
                ),
                notice.carryMonths
            ),
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = stringResource(
                when {
                    notice.undoFailed -> R.string.correction_undo_failed
                    notice.canUndo -> R.string.correction_deleted_spoken
                    else -> R.string.correction_deleted
                }
            )
        )

        CorrectionNotice.NotDeleted ->
            SelloNotice(stringResource(R.string.correction_not_deleted), tagged)

        CorrectionNotice.DeleteUnknown ->
            SelloNotice(stringResource(R.string.correction_delete_unknown), tagged)

        CorrectionNotice.Restored ->
            SelloNotice(stringResource(R.string.correction_restored), tagged)

        CorrectionNotice.UndoTooLate ->
            SelloNotice(stringResource(R.string.correction_too_late), tagged)

        CorrectionNotice.UndoBlocked ->
            SelloNotice(stringResource(R.string.correction_blocked), tagged)

        CorrectionNotice.Updated ->
            SelloNotice(stringResource(R.string.correction_updated), tagged)

        is CorrectionNotice.CategorySaved -> SelloNotice(
            message = notice.limitOf?.let {
                stringResource(R.string.category_saved_month, monthNames().inSentence(it))
            } ?: stringResource(R.string.category_saved),
            modifier = tagged
        )

        CorrectionNotice.CategoryArchived ->
            SelloNotice(stringResource(R.string.category_archived_notice), tagged)

        CorrectionNotice.CategoryUnarchived ->
            SelloNotice(stringResource(R.string.category_unarchived_notice), tagged)

        is CorrectionNotice.IncomeDeleted -> SelloNotice(
            message = stringResource(
                if (notice.undoFailed) {
                    R.string.correction_undo_failed
                } else {
                    R.string.income_deleted
                }
            ),
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = stringResource(
                when {
                    notice.undoFailed -> R.string.correction_undo_failed
                    notice.canUndo -> R.string.income_deleted_spoken
                    else -> R.string.income_deleted
                }
            )
        )

        CorrectionNotice.IncomeRestored ->
            SelloNotice(stringResource(R.string.income_restored), tagged)

        CorrectionNotice.IncomeUndoTooLate ->
            SelloNotice(stringResource(R.string.income_too_late), tagged)

        CorrectionNotice.IncomeUndoBlocked ->
            SelloNotice(stringResource(R.string.income_blocked), tagged)

        CorrectionNotice.IncomeUpdated ->
            SelloNotice(stringResource(R.string.income_updated), tagged)

        is CorrectionNotice.Confirmed -> SelloNotice(
            message = notice.message,
            modifier = tagged,
            spoken = notice.spoken,
            confirmation = true
        )

        is CorrectionNotice.LimitAdjusted -> {
            val text = if (notice.fromThisMonth) {
                R.string.notice_limit_adjusted_from
            } else {
                R.string.notice_limit_adjusted_this
            }
            val month = monthNames().inSentence(notice.month)
            // On screen the figure as it is written; read aloud with its currency.
            SelloNotice(
                message = stringResource(text, notice.categoryName, month, notice.limit.digits),
                modifier = tagged,
                spoken = stringResource(text, notice.categoryName, month, notice.limit.spoken),
                confirmation = true
            )
        }

        is CorrectionNotice.ExpenseRecorded -> {
            val category = notice.categoryName?.takeIf { it.isNotBlank() }
            // On screen the figure as it is written; read aloud with its currency.
            SelloNotice(
                message = expenseRecorded(notice.amount?.digits, category),
                modifier = tagged,
                spoken = expenseRecorded(notice.amount?.spoken, category),
                confirmation = true
            )
        }

        is CorrectionNotice.ExpensesRecorded -> {
            // On screen the figure as it is written; read aloud with its currency.
            SelloNotice(
                message = pluralStringResource(
                    R.plurals.notice_expenses_recorded,
                    notice.count,
                    notice.count,
                    notice.total.digits
                ),
                modifier = tagged,
                spoken = pluralStringResource(
                    R.plurals.notice_expenses_recorded,
                    notice.count,
                    notice.count,
                    notice.total.spoken
                ),
                confirmation = true
            )
        }

        is CorrectionNotice.GoalMovementDeleted -> SelloNotice(
            message = stringResource(
                if (notice.undoFailed) {
                    R.string.correction_undo_failed
                } else {
                    R.string.goal_movement_deleted
                }
            ),
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = stringResource(
                when {
                    notice.undoFailed -> R.string.correction_undo_failed
                    notice.canUndo -> R.string.goal_movement_deleted_spoken
                    else -> R.string.goal_movement_deleted
                }
            )
        )

        CorrectionNotice.GoalMovementRestored ->
            SelloNotice(stringResource(R.string.goal_movement_restored), tagged)

        CorrectionNotice.GoalMovementUndoTooLate ->
            SelloNotice(stringResource(R.string.goal_movement_too_late), tagged)

        CorrectionNotice.GoalMovementUndoBlocked ->
            SelloNotice(stringResource(R.string.goal_movement_undo_blocked), tagged)

        CorrectionNotice.GoalMovementUpdated ->
            SelloNotice(stringResource(R.string.goal_movement_updated), tagged)

        is CorrectionNotice.GoalCompleted -> SelloNotice(
            message = if (notice.undoFailed) {
                stringResource(R.string.correction_undo_failed)
            } else {
                stringResource(R.string.goal_completed, notice.goalName)
            },
            modifier = tagged,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = when {
                notice.undoFailed -> stringResource(R.string.correction_undo_failed)
                notice.canUndo -> stringResource(R.string.goal_completed_spoken, notice.goalName)
                else -> stringResource(R.string.goal_completed, notice.goalName)
            },
            confirmation = !notice.undoFailed
        )

        is CorrectionNotice.GoalCompletionUndone ->
            SelloNotice(stringResource(R.string.goal_completion_undone, notice.goalName), tagged)

        CorrectionNotice.GoalCompletionUndoTooLate ->
            SelloNotice(stringResource(R.string.goal_completion_too_late), tagged)

        is CorrectionNotice.GoalMovementRecorded -> {
            val amount = notice.amount
            val (said, plain) = if (notice.withdrawal) {
                R.string.notice_goal_withdrawal to R.string.notice_goal_withdrawal_plain
            } else {
                R.string.notice_goal_contribution to R.string.notice_goal_contribution_plain
            }
            val message = amount?.let { stringResource(said, it.digits) } ?: stringResource(plain)
            val spoken = amount?.let { stringResource(said, it.spoken) } ?: stringResource(plain)
            val celebration = notice.celebration
            val line = celebration?.let { celebrationLine(it) }
            when {
                line != null -> SelloNotice(
                    message = stringResource(R.string.goal_celebration_notice, message, line),
                    modifier = tagged,
                    spoken = stringResource(R.string.goal_celebration_notice, spoken, line),
                    confirmation = true
                )

                else -> SelloNotice(message, tagged, spoken = spoken, confirmation = true)
            }
        }

        is CorrectionNotice.GoalTargetChanged -> {
            val message = stringResource(R.string.notice_goal_target_changed)
            val line = celebrationLine(notice.celebration)
            if (line != null) {
                SelloNotice(
                    message = stringResource(R.string.goal_celebration_notice, message, line),
                    modifier = tagged,
                    confirmation = true
                )
            } else {
                SelloNotice(message, tagged, confirmation = true)
            }
        }

        is CorrectionNotice.ScheduleChanged -> SelloNotice(
            stringResource(
                when (notice.change) {
                    ScheduleChange.Changed -> R.string.notice_schedule_changed

                    ScheduleChange.Paused -> if (notice.income) {
                        R.string.notice_income_paused
                    } else {
                        R.string.notice_payment_paused
                    }

                    ScheduleChange.Resumed -> if (notice.income) {
                        R.string.notice_income_resumed
                    } else {
                        R.string.notice_payment_resumed
                    }

                    ScheduleChange.Deleted -> if (notice.income) {
                        R.string.notice_income_deleted_schedule
                    } else {
                        R.string.notice_payment_deleted_schedule
                    }
                }
            ),
            tagged,
            confirmation = true
        )

        is CorrectionNotice.ScheduleSaved -> {
            val name = notice.name?.takeIf { it.isNotBlank() }
                ?: notice.sourceKey?.let { incomeSourceLabel(it) }
            val rule = notice.rule
            val start = notice.start
            val message = if (name != null && rule != null && start != null) {
                stringResource(
                    if (notice.income) {
                        R.string.notice_income_schedule_saved
                    } else {
                        R.string.notice_payment_schedule_saved
                    },
                    name,
                    repeatRuleLabel(rule, start)
                )
            } else {
                stringResource(
                    if (notice.income) {
                        R.string.notice_income_schedule_saved_plain
                    } else {
                        R.string.notice_payment_schedule_saved_plain
                    }
                )
            }
            SelloNotice(message, tagged, confirmation = true)
        }

        is InvestmentMovementNotice -> InvestmentMovementNoticeBar(notice, onUndo, tagged)

        is CorrectionNotice.IncomeRecorded -> {
            val amount = notice.amount
            SelloNotice(
                message = amount?.let {
                    stringResource(R.string.notice_income_recorded, it.digits)
                } ?: stringResource(R.string.notice_income_recorded_plain),
                modifier = tagged,
                spoken = amount?.let {
                    stringResource(R.string.notice_income_recorded, it.spoken)
                } ?: stringResource(R.string.notice_income_recorded_plain),
                confirmation = true
            )
        }
    }
}

/**
 * The notice on Metas and Recibo: a stamp a confirmed aporte earned lands on a paper
 * ticket, as the goal's own stamps are not in view there; anything else is said as
 * everywhere.
 */
@Composable
internal fun StampNoticeBar(
    notice: CorrectionNotice,
    onUndo: () -> Unit,
    lands: (GoalCelebration) -> Boolean
) {
    val celebration = notice.celebration
    if (celebration != null && celebrationLine(celebration) != null) {
        val land = remember(celebration) { lands(celebration) }
        Box(Modifier.testTag(CORRECTION_NOTICE_TAG)) { CelebrationTicket(celebration, land = land) }
    } else {
        CorrectionNoticeBar(notice, onUndo)
    }
}

const val CORRECTION_NOTICE_TAG = "shell:correction-notice"

/** "Gasto borrado · cambió el límite con arrastre de 3 meses" when a deletion changed later months. */
@Composable
private fun withCarryMonths(message: String, months: Int?): String =
    if (months == null || months == 0) {
        message
    } else {
        stringResource(
            R.string.carry_with_change,
            message,
            pluralStringResource(R.plurals.carry_changed_months, months, months)
        )
    }

/** The list of later months a deletion, or undoing one, changes; asked before it is sent. */
@Composable
internal fun CorrectionQuestionSlip(
    question: CorrectionQuestion,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmSlip(
        title = stringResource(
            when (question.kind) {
                CorrectionKind.Delete -> R.string.carry_correct_delete_title
                CorrectionKind.Undo -> R.string.carry_correct_undo_title
            }
        ),
        confirmLabel = stringResource(
            when (question.kind) {
                CorrectionKind.Delete -> R.string.carry_correct_delete
                CorrectionKind.Undo -> R.string.carry_correct_undo
            }
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = Modifier.testTag(CORRECTION_QUESTION_TAG),
        destructive = question.kind == CorrectionKind.Delete,
        busy = question.busy
    ) {
        CarryCorrectionContent(question.list, question.changed)
    }
}

const val CORRECTION_QUESTION_TAG = "correction:question"
