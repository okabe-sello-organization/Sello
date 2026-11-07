package com.software.sello.feature.resumen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons

/**
 * Gastos hormiga (SELLO-194; E34 patterns-mock.html, section 1): what the month's small
 * expenses came to, their share of what was spent, the same days of the month before when
 * it has data, and the three categories where they gather. Facts only: no warning or target.
 */
@Composable
internal fun SmallExpensesSlip(
    small: ResumenSmallExpenses,
    hero: ResumenHero,
    onRetry: () -> Unit
) {
    when (small) {
        ResumenSmallExpenses.Unreadable -> StatusSlip(
            kind = StatusKind.LoadFailed(stringResource(R.string.resumen_small_unreadable_title)),
            message = stringResource(R.string.resumen_small_unreadable),
            modifier = Modifier.testTag(RESUMEN_SMALL_UNREADABLE_TAG),
            action = StatusAction(StatusSlipDefaults.retryLabel, onClick = onRetry)
        )

        // Reading again cannot help a sum that does not add up: no Reintentar.
        ResumenSmallExpenses.Unavailable -> StatusSlip(
            kind = StatusKind.LoadFailed(stringResource(R.string.resumen_small_unavailable_title)),
            message = stringResource(R.string.resumen_small_unavailable),
            modifier = Modifier.testTag(RESUMEN_SMALL_UNAVAILABLE_TAG)
        )

        is ResumenSmallExpenses.Shown -> {
            if (small.olderLine) {
                StatusSlip(
                    kind = StatusKind.Older,
                    message = stringResource(R.string.resumen_small_older, small.line),
                    modifier = Modifier.testTag(RESUMEN_SMALL_OLDER_TAG),
                    action = StatusAction(StatusSlipDefaults.retryLabel, onClick = onRetry)
                )
            }
            Shown(small, hero)
        }
    }
}

@Composable
private fun Shown(small: ResumenSmallExpenses.Shown, hero: ResumenHero) {
    Slip(Modifier.fillMaxWidth().testTag(RESUMEN_SMALL_TAG)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ResumenHeading(stringResource(R.string.small_expense_title))
            val line = stringResource(R.string.resumen_small_line_spoken, small.line)
            ResumenNote(
                stringResource(R.string.resumen_small_line, small.line),
                Modifier.semantics { contentDescription = line }
            )
        }
        MoneyText(
            small.total,
            style = MoneyStyle.Card,
            modifier = Modifier.testTag(RESUMEN_SMALL_TOTAL_TAG)
        )
        val count = pluralStringResource(R.plurals.resumen_small_count, small.count, small.count)
        ResumenNote(
            if (hero.inProgress) {
                stringResource(
                    R.string.resumen_small_basis_in_progress,
                    count,
                    hero.throughDay,
                    small.sharePercent
                )
            } else {
                stringResource(
                    R.string.resumen_small_basis,
                    count,
                    hero.monthName,
                    small.sharePercent
                )
            }
        )
        small.bars?.let { bars ->
            ComparisonBars(
                bars,
                Modifier.padding(top = SelloTheme.spacing.md, bottom = SelloTheme.spacing.xs)
                    .testTag(RESUMEN_SMALL_BARS_TAG)
            )
            ResumenNote(basis(small, hero.monthName))
        }
        TearLine()
        Text(
            stringResource(R.string.resumen_small_where),
            style = MaterialTheme.typography.bodyMedium,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(bottom = SelloTheme.spacing.xs)
        )
        small.categories.forEach { category -> CategoryLine(category) }
    }
}

/** The days both bars run over, as the hero says them. */
@Composable
private fun basis(small: ResumenSmallExpenses.Shown, monthName: String): String {
    val days = small.throughDay
    val previousDays = small.previousThroughDay
    return when {
        days == null || previousDays == null -> stringResource(R.string.resumen_hero_whole_months)

        days == previousDays -> stringResource(R.string.resumen_hero_same_days, days)

        else -> stringResource(
            R.string.resumen_hero_capped_days,
            days,
            monthName,
            previousDays,
            small.previousName
        )
    }
}

/** A category, how many small expenses it has and what they add up to; read as one. */
@Composable
private fun CategoryLine(category: ResumenSmallCategory) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("$RESUMEN_SMALL_CATEGORY_TAG.${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        CategoryCircle(CategoryIcons.iconFor(category.iconKey), 1f, size = 32.dp, animate = false)
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.bodyMedium)
            ResumenNote(
                pluralStringResource(
                    R.plurals.resumen_small_category_count,
                    category.count,
                    category.count
                )
            )
        }
        MoneyText(category.amount)
    }
}

const val RESUMEN_SMALL_TAG = "resumen.small"
const val RESUMEN_SMALL_TOTAL_TAG = "resumen.small.total"
const val RESUMEN_SMALL_BARS_TAG = "resumen.small.bars"
const val RESUMEN_SMALL_CATEGORY_TAG = "resumen.small.category"
const val RESUMEN_SMALL_UNREADABLE_TAG = "resumen.small.unreadable"
const val RESUMEN_SMALL_OLDER_TAG = "resumen.small.older"
const val RESUMEN_SMALL_UNAVAILABLE_TAG = "resumen.small.unavailable"
