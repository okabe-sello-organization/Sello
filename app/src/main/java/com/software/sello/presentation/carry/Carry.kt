package com.software.sello.presentation.carry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.CarryBreakdown
import com.software.sello.designsystem.component.CarryLine
import com.software.sello.designsystem.component.ChangeRow
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LineEmphasis
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.CarryBreakdown as CarryParts
import com.software.sello.domain.model.CarryMonth
import com.software.sello.domain.model.CarryVerdict
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.Verdict
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.policy.CarryMonthChange
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth

/** Which words go with a carry-in (ADR 0020 wording): a surplus, a deficit or nothing yet. */
enum class CarryDirection { Surplus, Deficit, Zero }

/**
 * One budget's carry, formatted once from the figures the snapshot holds (ADR 0020 CP27):
 * [carry] signed, [carryMagnitude] for the sentence that already says which way it goes.
 * [chainStart] is null for the month as a whole, which has no chain of its own; [source]
 * is the month whose remainder the carry is, null in a chain's first month.
 */
@Immutable
data class CarryFigures(
    val base: MoneyTextValue,
    val carry: MoneyTextValue,
    val carryMagnitude: MoneyTextValue,
    val direction: CarryDirection,
    val capacity: MoneyTextValue,
    val spent: MoneyTextValue?,
    val chainStart: YearMonth?,
    val source: YearMonth?
)

/**
 * The carry of [budget] in [month], or null when it does not carry. Nothing is added or
 * subtracted: every amount is one the snapshot already holds.
 */
fun MoneyFormatter.carryFigures(
    budget: LimitedBudget,
    month: YearMonth,
    withSpent: Boolean = true
): CarryFigures? = budget.carry?.let { carryFigures(it, month, budget.spent.takeIf { withSpent }) }

fun MoneyFormatter.carryFigures(parts: CarryParts, month: YearMonth, spent: Money?): CarryFigures =
    CarryFigures(
        base = format(parts.base),
        carry = format(parts.carryIn, signed = true),
        carryMagnitude = magnitude(parts.carryIn),
        direction = when {
            parts.carryIn.minorUnits > 0 -> CarryDirection.Surplus
            parts.carryIn.minorUnits < 0 -> CarryDirection.Deficit
            else -> CarryDirection.Zero
        },
        capacity = format(parts.capacity),
        spent = spent?.let(::format),
        chainStart = parts.chainStart,
        source = parts.chainStart?.takeIf { it < month }?.let { month.minusMonths(1) }
    )

/** "Sobrante" for a carry of 0 or more, "Faltante" below 0: the word says the direction. */
@Composable
fun carryLabel(carry: MoneyTextValue): String = stringResource(
    if (carry.sign == MoneySign.Minus) R.string.carry_in_deficit else R.string.carry_in_surplus
)

/** "Te sobró 80.000 de meses anteriores", "Te pasaste 20.000 en meses anteriores". */
@Composable
fun carryWords(figures: CarryFigures): String = when (figures.direction) {
    // The grouped figure as written ("80.000"); the line's amount is spoken in full.
    CarryDirection.Surplus -> stringResource(R.string.carry_surplus, figures.carryMagnitude.digits)

    CarryDirection.Deficit -> stringResource(R.string.carry_deficit, figures.carryMagnitude.digits)

    CarryDirection.Zero -> stringResource(R.string.carry_zero)
}

/**
 * Base, carry, capacity and spent as receipt lines, with [footnote] under them. [prefix]
 * names the category before the words when the lines are a month's sum ("Comida · …").
 */
@Composable
fun CarryBreakdownSection(
    figures: CarryFigures,
    footnote: String?,
    modifier: Modifier = Modifier,
    prefix: String? = null
) {
    val words = carryWords(figures).let { words ->
        if (prefix ==
            null
        ) {
            words
        } else {
            stringResource(R.string.carry_words_of, prefix, words.lowercaseFirst())
        }
    }
    val lines = buildList {
        add(CarryLine(stringResource(R.string.carry_base), figures.base))
        add(CarryLine(carryLabel(figures.carry), figures.carry, words))
        add(
            CarryLine(
                stringResource(R.string.carry_capacity),
                figures.capacity,
                emphasis = LineEmphasis.Strong
            )
        )
        figures.spent?.let { add(CarryLine(stringResource(R.string.carry_spent), it)) }
    }
    CarryBreakdown(lines, modifier.testTag(CARRY_BREAKDOWN_TAG), footnote)
}

private fun String.lowercaseFirst() = replaceFirstChar { it.lowercase() }

/**
 * Where a category's carry comes from (CP27): "Viene del restante de ene 2027 · pasas lo que
 * sobra o falta desde ene 2027", or only the start in the chain's first month; with [asOf], the day read.
 */
@Composable
fun categoryCarryFootnote(figures: CarryFigures, asOf: LocalDate? = null): String? {
    val dates = dateLabels()
    val start = figures.chainStart ?: return null
    val origin = figures.source?.let {
        stringResource(R.string.carry_from_source, dates.monthYear(it), dates.monthYear(start))
    } ?: stringResource(R.string.carry_since, dates.monthYear(start))
    return asOf?.let { stringResource(R.string.carry_read_on, origin, dates.shortDay(it, it)) }
        ?: origin
}

/**
 * Where the month's carry comes from: one category, named with its start, or how many
 * categories carry; then the day read.
 */
@Composable
fun overallCarryFootnote(categories: List<Pair<String, YearMonth>>, asOf: LocalDate): String {
    val dates = dateLabels()
    val day = dates.shortDay(asOf, asOf)
    val single = categories.singleOrNull()
    return if (single != null) {
        stringResource(
            R.string.carry_overall_one,
            single.first,
            dates.monthYear(single.second),
            day
        )
    } else {
        pluralStringResource(R.plurals.carry_overall_many, categories.size, categories.size, day)
    }
}

/** A verdict as a list says it: "Al día", "Excedido", "Cumplido". */
enum class VerdictWord { OnTrack, AtLimit, Over, WithinLimit }

private fun CarryVerdict.word(): VerdictWord = when (this) {
    is CarryVerdict.Closed -> when (verdict) {
        PastVerdict.WithinLimit -> VerdictWord.WithinLimit
        PastVerdict.Over -> VerdictWord.Over
    }

    is CarryVerdict.InProgress -> when (verdict) {
        Verdict.OnTrack -> VerdictWord.OnTrack
        Verdict.AtLimit -> VerdictWord.AtLimit
        Verdict.Over -> VerdictWord.Over
    }
}

/** What one row of a list says changes in one month. */
enum class ChangeKind {
    /** A later month: its limit with carry moves (CP21). */
    Later,

    /** The month of the change itself: its remaining moves; its carry-in does not. */
    Edited,

    /** A balance a chain dropped when it ended (CP22). */
    Settled
}

/**
 * One month in a correction list. For [ChangeKind.Settled], [before] and [after] are the
 * balance not carried; otherwise the limit with carry. [change] is by how much, signed, as
 * the domain worked it out (CP20). [remainingBefore] and
 * [remainingAfter] are the month's remaining; [verdictBefore]/[verdictAfter] differ only
 * when the verdict flips.
 */
@Immutable
data class CarryChangeRow(
    val month: YearMonth,
    val kind: ChangeKind,
    val before: MoneyTextValue,
    val after: MoneyTextValue,
    val change: MoneyTextValue,
    val remainingBefore: MoneyTextValue?,
    val remainingAfter: MoneyTextValue?,
    val verdictBefore: VerdictWord?,
    val verdictAfter: VerdictWord?
)

/** One category's rows; [name] is null when it could not be read, never shown blank. */
@Immutable
data class CarryChangeGroup(val name: String?, val iconKey: String?, val rows: List<CarryChangeRow>)

/**
 * What a correction changes before it is saved (ADR 0020 CP20–CP22): [months] later months
 * whose limit with carry moves, by category. [totalUnchanged] when two or more categories
 * move and the month's total limit with carry does not (a move between chains, SC26).
 */
@Immutable
data class CarryCorrectionList(
    val months: Int,
    val groups: List<CarryChangeGroup>,
    val totalUnchanged: Boolean
)

/** Builds [CarryCorrectionList] from a domain [CarryCorrection]; only formats and orders. */
class CarryCorrectionText(private val money: MoneyFormatter) {
    fun list(
        correction: CarryCorrection,
        categories: Map<CategoryId, Category>
    ): CarryCorrectionList {
        val ids = (
            correction.edited.map {
                it.categoryId
            } + correction.months.map { it.categoryId } +
                correction.settled.map { it.categoryId }
            ).distinct()
        val groups = ids.map { id ->
            val rows = buildList {
                correction.edited.filter { it.categoryId == id }.forEach {
                    add(row(ChangeKind.Edited, it))
                }
                correction.months.filter { it.categoryId == id }.forEach {
                    add(row(ChangeKind.Later, it))
                }
                correction.settled.filter { it.categoryId == id }.forEach {
                    add(
                        CarryChangeRow(
                            it.after.endMonth,
                            ChangeKind.Settled,
                            money.format(it.before.balance),
                            money.format(it.after.balance),
                            money.format(it.balanceChange, signed = true),
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
            }.sortedWith(compareBy({ it.month }, { it.kind.ordinal }))
            val category = categories[id]
            CarryChangeGroup(category?.name?.value, category?.icon?.value, rows)
        }
        return CarryCorrectionList(
            months = correction.months.size,
            groups = groups,
            totalUnchanged = ids.size > 1 &&
                correction.overallCapacityChange.values.all { it.minorUnits == 0L }
        )
    }

    /**
     * Several categories' corrections as one list, each with its own group: what recording
     * catch-up's payments did (CP18). Each lowers availability on its own, so the total is
     * never said to be unchanged.
     */
    fun list(
        corrections: List<CarryCorrection>,
        categories: Map<CategoryId, Category>
    ): CarryCorrectionList {
        val lists = corrections.map { list(it, categories) }
        return CarryCorrectionList(
            months = lists.sumOf { it.months },
            groups = lists.flatMap { it.groups },
            totalUnchanged = false
        )
    }

    private fun row(kind: ChangeKind, change: CarryMonthChange): CarryChangeRow {
        val before = change.before
        val after = change.after
        return CarryChangeRow(
            month = change.month,
            kind = kind,
            before = money.format(before.capacity),
            after = money.format(after.capacity),
            change = money.format(change.capacityChange, signed = true),
            remainingBefore = money.format(before.remaining),
            remainingAfter = money.format(after.remaining),
            verdictBefore = before.verdict.word().takeIf { it != after.verdict.word() },
            verdictAfter = after.verdict.word().takeIf { it != before.verdict.word() }
        )
    }
}

@Composable
private fun verdictText(word: VerdictWord) = stringResource(
    when (word) {
        VerdictWord.OnTrack -> R.string.carry_verdict_on_track
        VerdictWord.AtLimit -> R.string.carry_verdict_at_limit
        VerdictWord.Over -> R.string.carry_verdict_over
        VerdictWord.WithinLimit -> R.string.carry_verdict_within
    }
)

/** The figure as a list writes it, with a true minus, and a plus when it is a change. */
private fun MoneyTextValue.text() = when (sign) {
    MoneySign.Minus -> "−$digits"
    MoneySign.Plus -> "+$digits"
    else -> digits
}

/**
 * The list, as the content of a confirmation: a lead line, then each category with its
 * months. [changed] says the list was read again because the data moved (SC34). With
 * [saved] the change is already recorded (catch-up, CP18): nothing is left to confirm.
 */
@Composable
fun CarryCorrectionContent(
    list: CarryCorrectionList,
    changed: Boolean,
    what: String? = null,
    saved: Boolean = false
) {
    val dates = dateLabels()
    Column(
        Modifier.testTag(CARRY_CORRECTION_TAG),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (changed) {
            Text(
                stringResource(R.string.carry_list_changed),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.semantic.warn,
                modifier = Modifier.padding(bottom = 8.dp).testTag(CARRY_CORRECTION_CHANGED_TAG)
            )
        }
        what?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SelloTheme.colors.inkSoft)
        }
        // Already recorded, the heading above says what changed; the lead asks before a save.
        if (list.months > 0 && !saved) {
            Text(
                pluralStringResource(R.plurals.carry_list_lead, list.months, list.months),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() }
            )
        }
        list.groups.forEach { group ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                group.iconKey?.let {
                    Icon(
                        CategoryIcons.iconFor(it).painter(),
                        null,
                        Modifier.size(18.dp),
                        SelloTheme.colors.brand
                    )
                }
                Text(
                    group.name ?: stringResource(R.string.carry_list_unknown_category),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            group.rows.forEach { ChangeLine(it, dates) }
        }
        Text(
            stringResource(
                when {
                    saved -> R.string.carry_list_bases_saved
                    list.totalUnchanged -> R.string.carry_list_total_same
                    else -> R.string.carry_list_bases
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ChangeLine(row: CarryChangeRow, dates: DateLabels) {
    val month = dates.monthYear(row.month)
    val remaining = if (row.remainingBefore != null && row.remainingAfter != null) {
        stringResource(
            R.string.carry_list_remaining,
            row.remainingBefore.text(),
            row.remainingAfter.text()
        )
    } else {
        null
    }
    val flip = if (row.verdictBefore != null && row.verdictAfter != null) {
        stringResource(
            R.string.carry_list_flip,
            verdictText(row.verdictBefore),
            verdictText(row.verdictAfter)
        )
    } else {
        null
    }
    val value = when (row.kind) {
        ChangeKind.Later, ChangeKind.Settled -> stringResource(
            R.string.carry_list_change_by,
            row.before.text(),
            row.after.text(),
            row.change.text()
        )

        ChangeKind.Edited -> stringResource(R.string.carry_list_edited)
    }
    val caption = when (row.kind) {
        ChangeKind.Settled -> stringResource(R.string.carry_list_settled)
        else -> listOfNotNull(remaining, flip).joinToString(" · ").ifEmpty { null }
    }
    val spoken = when (row.kind) {
        ChangeKind.Later -> stringResource(
            R.string.carry_list_spoken,
            month,
            row.before.spoken,
            row.after.spoken,
            row.change.spoken
        )

        ChangeKind.Settled -> stringResource(
            R.string.carry_list_settled_spoken,
            month,
            row.before.spoken,
            row.after.spoken,
            row.change.spoken
        )

        ChangeKind.Edited -> stringResource(R.string.carry_list_edited_spoken, month)
    } + listOfNotNull(
        row.remainingBefore?.let { before ->
            row.remainingAfter?.let { after ->
                stringResource(R.string.carry_list_remaining_spoken, before.spoken, after.spoken)
            }
        },
        flip
    ).joinToString("") { " $it." }
    ChangeRow(month, value, spoken, caption = caption, emphasized = flip != null)
}

const val CARRY_BREAKDOWN_TAG = "carry:breakdown"
const val CARRY_CORRECTION_TAG = "carry:correction"
const val CARRY_CORRECTION_CHANGED_TAG = "carry:correction.changed"

/** Why a month's carry has no figures, and what can repair it (ADR 0020 CP8, CP23). */
enum class CarryProblemKind {
    /** [CarryProblem.month] of the chain has no finite limit: giving it one repairs it. */
    NoBase,

    /** A month's spending could not be read: reading again may work. */
    Missing,

    /** The chain itself is damaged: restoring a backup repairs it. */
    Damaged
}

/**
 * A carry figure that could not be calculated, for [categoryId] (named [name] once known).
 * It is never shown as 0 or as the base alone.
 */
@Immutable
data class CarryProblem(
    val categoryId: String,
    val name: String?,
    val kind: CarryProblemKind,
    val month: YearMonth?
)

/** The carry problem behind [failure], or null when it is not one. */
fun carryProblem(
    failure: SnapshotFailure,
    names: Map<CategoryId, String> = emptyMap()
): CarryProblem? {
    val carry = failure as? SnapshotFailure.Carryover ?: return null
    val (kind, month) = when (val cause = carry.failure) {
        is CarryoverFailure.NoFiniteBase -> CarryProblemKind.NoBase to cause.month
        is CarryoverFailure.MissingHistory -> CarryProblemKind.Missing to cause.month
        else -> CarryProblemKind.Damaged to null
    }
    return CarryProblem(carry.categoryId.value, names[carry.categoryId], kind, month)
}

/**
 * The failure in words, with the one action that can repair it: giving the month its limit
 * ([onSetLimit], with the category and month), or reading again ([onRetry]).
 */
@Composable
fun CarryProblemSlip(
    problem: CarryProblem,
    onRetry: () -> Unit,
    onSetLimit: (categoryId: String, month: YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    val dates = dateLabels()
    val name = problem.name
    val title = if (name == null) {
        stringResource(R.string.carry_failed_title)
    } else {
        stringResource(R.string.carry_failed_title_of, name)
    }
    val who = name ?: stringResource(R.string.carry_failed_someone)
    val month = problem.month
    when {
        problem.kind == CarryProblemKind.NoBase && month != null -> ErrorSlip(
            message = stringResource(R.string.carry_failed_no_base, dates.monthYear(month), who),
            onRetry = { onSetLimit(problem.categoryId, month) },
            modifier = modifier.testTag(CARRY_PROBLEM_TAG),
            title = title,
            icon = SelloIcon.Error,
            retryLabel = stringResource(R.string.carry_failed_set_limit, dates.monthYear(month))
        )

        problem.kind == CarryProblemKind.Missing -> ErrorSlip(
            message = stringResource(R.string.carry_failed_missing, who),
            onRetry = onRetry,
            modifier = modifier.testTag(CARRY_PROBLEM_TAG),
            title = title,
            icon = SelloIcon.Error
        )

        else -> ErrorSlip(
            message = stringResource(R.string.carry_failed_damaged, who),
            onRetry = onRetry,
            modifier = modifier.testTag(CARRY_PROBLEM_TAG),
            title = title,
            icon = SelloIcon.Error
        )
    }
}

const val CARRY_PROBLEM_TAG = "carry:problem"
