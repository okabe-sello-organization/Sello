package com.software.sello.feature.investments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.InvestmentMovementNotice

/**
 * What is said about an investment's movement, as goal movements say it: "Movimiento
 * borrado" with Deshacer while it can be undone, then a statement that goes away by
 * itself. The notice bar hands every [InvestmentMovementNotice] here.
 */
@Composable
fun InvestmentMovementNoticeBar(
    notice: InvestmentMovementNotice,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (notice) {
        is CorrectionNotice.InvestmentMovementDeleted -> SelloNotice(
            message = stringResource(
                if (notice.undoFailed) {
                    R.string.correction_undo_failed
                } else {
                    R.string.investment_movement_deleted
                }
            ),
            modifier = modifier,
            actionLabel = stringResource(R.string.correction_undo).takeIf { notice.canUndo },
            onAction = onUndo.takeIf { notice.canUndo },
            busy = notice.undoing,
            spoken = stringResource(
                when {
                    notice.undoFailed -> R.string.correction_undo_failed
                    notice.canUndo -> R.string.investment_movement_deleted_spoken
                    else -> R.string.investment_movement_deleted
                }
            )
        )

        CorrectionNotice.InvestmentMovementRestored ->
            SelloNotice(stringResource(R.string.investment_movement_restored), modifier)

        CorrectionNotice.InvestmentMovementUndoTooLate ->
            SelloNotice(stringResource(R.string.investment_movement_too_late), modifier)

        CorrectionNotice.InvestmentMovementUndoBlocked ->
            SelloNotice(stringResource(R.string.investment_movement_undo_blocked), modifier)

        CorrectionNotice.InvestmentMovementUpdated ->
            SelloNotice(stringResource(R.string.investment_movement_updated), modifier)

        is CorrectionNotice.InvestmentMovementRecorded -> {
            val (said, plain) = when (notice.kind) {
                InvestmentMovementKind.Contribution ->
                    R.string.notice_investment_contribution to
                        R.string.notice_investment_contribution_plain

                InvestmentMovementKind.Valuation ->
                    R.string.notice_investment_valuation to
                        R.string.notice_investment_valuation_plain

                InvestmentMovementKind.Withdrawal ->
                    R.string.notice_investment_withdrawal to
                        R.string.notice_investment_withdrawal_plain

                InvestmentMovementKind.Dividend ->
                    R.string.notice_investment_dividend to R.string.notice_investment_dividend_plain
            }
            val amount = notice.amount
            val written = amount?.let {
                if (it.symbol.isEmpty()) it.digits else "${it.symbol} ${it.digits}"
            }
            SelloNotice(
                message = written?.let { stringResource(said, it) } ?: stringResource(plain),
                modifier = modifier,
                spoken = amount?.let { stringResource(said, it.spoken) } ?: stringResource(plain),
                confirmation = true
            )
        }

        is CorrectionNotice.InvestmentMovementsRecorded -> {
            val name = notice.investmentName
            SelloNotice(
                message = if (name != null) {
                    pluralStringResource(
                        R.plurals.notice_investment_movements,
                        notice.count,
                        notice.count,
                        name
                    )
                } else {
                    pluralStringResource(
                        R.plurals.notice_investment_movements_plain,
                        notice.count,
                        notice.count
                    )
                },
                modifier = modifier,
                confirmation = true
            )
        }
    }
}
