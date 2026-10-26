package com.software.sello.presentation.restore

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.software.sello.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.port.LegacyImportLine
import com.software.sello.domain.port.LegacyImportReport
import com.software.sello.domain.port.LegacyReason
import com.software.sello.domain.port.LegacyRecord
import com.software.sello.presentation.count.countText

/**
 * What translating a FinanceTracker backup changed and left out, before the person
 * confirms (ADR 0019): one line per kind of record and reason, and a warning that the
 * totals can differ whenever anything is left out.
 */
@Composable
fun LegacyReportSection(report: LegacyImportReport, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.restore_legacy_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = SelloTheme.spacing.md).testTag(RESTORE_LEGACY_TAG)
            .semantics { heading() }
    )
    if (report.lines.isEmpty()) {
        RestoreBody(stringResource(R.string.restore_legacy_complete))
        return
    }
    if (report.changed.isNotEmpty()) {
        RestoreBody(stringResource(R.string.restore_legacy_changed))
        Lines(report.changed)
    }
    if (report.leftOut.isNotEmpty()) {
        RestoreBody(stringResource(R.string.restore_legacy_left_out))
        Lines(report.leftOut)
        RestoreBody(stringResource(R.string.restore_legacy_totals))
    }
}

@Composable
private fun Lines(lines: List<LegacyImportLine>) {
    BulletList(
        lines.map { line ->
            Bullet(
                null,
                stringResource(
                    R.string.restore_legacy_line,
                    pluralStringResource(line.record.plural(), line.count, countText(line.count)),
                    stringResource(line.reason.label())
                )
            )
        },
        Modifier.padding(top = SelloTheme.spacing.sm)
    )
}

private fun LegacyRecord.plural() = when (this) {
    LegacyRecord.Category -> R.plurals.restore_categories
    LegacyRecord.Limit -> R.plurals.restore_legacy_limits
    LegacyRecord.Expense -> R.plurals.restore_expenses
    LegacyRecord.Income -> R.plurals.restore_incomes
    LegacyRecord.RepeatingEntry -> R.plurals.restore_schedules
    LegacyRecord.Goal -> R.plurals.restore_goals
    LegacyRecord.Investment -> R.plurals.restore_legacy_investments
    LegacyRecord.InvestmentEntry -> R.plurals.restore_legacy_investment_entries
    LegacyRecord.ExchangeRate -> R.plurals.restore_legacy_rates
    LegacyRecord.MerchantRule -> R.plurals.restore_legacy_merchant_rules
}

private fun LegacyReason.label() = when (this) {
    LegacyReason.Shortened -> R.string.restore_legacy_shortened
    LegacyReason.Renamed -> R.string.restore_legacy_renamed
    LegacyReason.Rounded -> R.string.restore_legacy_rounded
    LegacyReason.ValueAdded -> R.string.restore_legacy_value_added
    LegacyReason.InterestAsValue -> R.string.restore_legacy_interest_as_value
    LegacyReason.NoAutomaticInterest -> R.string.restore_legacy_no_interest
    LegacyReason.Unreadable -> R.string.restore_legacy_unreadable
    LegacyReason.DuplicateId -> R.string.restore_legacy_duplicate
    LegacyReason.AmountOutOfRange -> R.string.restore_legacy_amount
    LegacyReason.InvalidDate -> R.string.restore_legacy_date
    LegacyReason.DateAfterExport -> R.string.restore_legacy_after_export
    LegacyReason.InvalidText -> R.string.restore_legacy_text
    LegacyReason.CategoryMissing -> R.string.restore_legacy_category_missing
    LegacyReason.CategoryLeftOut -> R.string.restore_legacy_category_left_out
    LegacyReason.UnknownRepeat -> R.string.restore_legacy_repeat
    LegacyReason.UnsupportedCurrency -> R.string.restore_legacy_currency
    LegacyReason.InvestmentMissing -> R.string.restore_legacy_investment_missing
    LegacyReason.InvestmentLeftOut -> R.string.restore_legacy_investment_left_out
    LegacyReason.NoteOnly -> R.string.restore_legacy_note_only
    LegacyReason.BeforeFirstContribution -> R.string.restore_legacy_before_first
    LegacyReason.WithdrawalAboveValue -> R.string.restore_legacy_withdrawal
    LegacyReason.NoContribution -> R.string.restore_legacy_no_contribution
    LegacyReason.NotImportedYet -> R.string.restore_legacy_not_yet
}

const val RESTORE_LEGACY_TAG = "restore:legacy"
