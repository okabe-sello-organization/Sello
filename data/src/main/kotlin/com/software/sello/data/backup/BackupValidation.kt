package com.software.sello.data.backup

import com.software.sello.data.repository.MAX_DEFAULT_HISTORY
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalHistoryError
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentAmountError
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.MAX_CHAIN_BASE
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RecurrenceError
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.RepeatRuleError
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.model.TransactionAmountError
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.CategoryBudgetPolicy
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.StoredSchedule
import java.time.DateTimeException
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.TreeSet
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * The wire spellings of version 1. They belong to the file, not to Room or to the
 * domain's Kotlin names, so renaming either cannot change what a backup says.
 */
internal object WireNames {
    const val FINITE = "finite"
    const val UNLIMITED = "unlimited"
    const val PAUSED = "paused"
    const val TARGET = "target"
    const val CONTRIBUTION = "contribution"
    const val WITHDRAWAL = "withdrawal"
    const val DIVIDEND = "dividend"
    const val VALUATION = "valuation"
    const val PAYMENT = "payment"
    const val INCOME = "income"
    const val MONTHLY = "monthly"
    const val TWICE_MONTHLY = "twice_monthly"
    const val EVERY_DAYS = "every_days"
    const val EVERY_WEEKS = "every_weeks"

    val themes = mapOf(
        ThemePreference.System to "system",
        ThemePreference.Light to "light",
        ThemePreference.Dark to "dark"
    )
    val inks = mapOf(InkPreference.Cobalto to "cobalto", InkPreference.Violeta to "violeta")
    val orders = mapOf(
        CategoryOrder.MostUsed to "most_used",
        CategoryOrder.Alphabetical to "alphabetical",
        CategoryOrder.AmountSpent to "amount_spent",
        CategoryOrder.Limit to "limit",
        CategoryOrder.LatestExpenseAdded to "latest_expense_added"
    )
    val directions = mapOf(
        CategoryOrderDirection.Descending to "descending",
        CategoryOrderDirection.Ascending to "ascending"
    )

    /** The currencies a new investment may start in, written as amounts write theirs. */
    val newCurrencies = mapOf(Currency.COP to "COP", Currency.USD to "USD")
    val investmentOrders = mapOf(
        InvestmentOrder.LargestValue to "largest_value",
        InvestmentOrder.Return to "return",
        InvestmentOrder.Alphabetical to "alphabetical",
        InvestmentOrder.Newest to "newest",
        InvestmentOrder.LatestUpdate to "latest_update"
    )
}

/** Thrown and caught inside [validate] only, carrying the first problem found. */
private class Rejected(val rejection: BackupRejection) : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

private fun reject(rejection: BackupRejection): Nothing = throw Rejected(rejection)

private fun invalid(path: String): Nothing = reject(BackupRejection.InvalidValue(path))

private fun <T> Outcome<T, *>.or(path: String): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> invalid(path)
}

private fun whole(element: JsonElement, path: String): Long =
    element.wholeNumberOrNull() ?: invalid(path)

/** [whole], or an explicit `null`. */
private fun optionalWhole(element: JsonElement, path: String): Long? =
    if (element is JsonNull) null else whole(element, path)

/** A bare `true` or `false`, never the quoted words. */
private fun flag(element: JsonElement, path: String): Boolean {
    val primitive = element as? JsonPrimitive
    if (primitive == null || primitive.isString) invalid(path)
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> invalid(path)
    }
}

/**
 * Checks a decoded file completely, older versions read as version 6 included; the
 * first problem rejects all of it.
 */
internal fun validate(file: BackupFileV6): Outcome<BackupContents, BackupRejection> = try {
    Outcome.Success(contentsOf(file))
} catch (rejected: Rejected) {
    Outcome.Failure(rejected.rejection)
}

private fun contentsOf(file: BackupFileV6): BackupContents {
    val financial = file.financial
    val revision = whole(financial.sourceRevision, "financial.sourceRevision")
    val zone = try {
        ZoneId.of(financial.zone).also { if (it.id != financial.zone) invalid("financial.zone") }
    } catch (_: DateTimeException) {
        invalid("financial.zone")
    }
    val categories = categories(financial.categories)
    val known = categories.mapTo(HashSet()) { it.id }
    val goals = goals(financial.goals)
    val featured = financial.featuredGoal?.let { text ->
        val id = GoalId.of(text).or("financial.featuredGoal")
        if (goals.none { it.goal.id == id }) {
            reject(BackupRejection.DanglingReference("financial.featuredGoal"))
        }
        id
    }
    val schedules = schedules(financial.schedules, categories)
    val handled = schedules.associate { it.schedule.id to it.handled }
    val exportedAt = instant(file.exportedAt, "exportedAt")
    val defaultLimits = defaultLimits(financial.defaultLimits, known)
    val monthLimits = monthLimits(financial.monthLimits, known)
    return BackupContents(
        exportedAt = exportedAt,
        financial = FinancialSnapshot(
            zone = zone,
            revision = revision,
            categories = categories,
            defaultLimits = defaultLimits,
            monthLimits = monthLimits,
            expenses = expenses(financial.expenses, known, handled),
            incomes = incomes(financial.incomes, handled),
            goals = goals,
            featuredGoal = featured,
            schedules = schedules,
            investments = investments(financial.investments),
            usdRates = usdRates(financial.usdRates),
            carryChains = carryChains(
                financial.carryChains,
                categories,
                defaultLimits,
                monthLimits,
                YearMonth.from(exportedAt.atZone(zone))
            )
        ),
        preferences = file.preferences?.let(::preferences)
    )
}

private const val NANOS_PER_MILLI = 1_000_000

/** Exactly the text [Instant.toString] writes, so one moment has one spelling. */
private fun instant(text: String, path: String): Instant {
    val parsed = try {
        Instant.parse(text)
    } catch (_: DateTimeParseException) {
        invalid(path)
    }
    // Stored time keeps milliseconds, so finer time could not be restored as written.
    if (parsed.toString() != text || parsed.nano % NANOS_PER_MILLI != 0) invalid(path)
    return parsed
}

private fun recordId(text: String, path: String) = CategoryId.of(text).or(path)

private fun categories(wire: List<CategoryV1>): List<Category> {
    val ids = HashSet<CategoryId>()
    val names = HashSet<String>()
    return wire.mapIndexed { index, row ->
        val path = "financial.categories[$index]"
        val id = recordId(row.id, "$path.id")
        val name = CategoryName.of(row.name).or("$path.name")
        // A name must already be in the form the app stores, or two spellings of one
        // name could slip past the uniqueness check.
        if (name.value != row.name) invalid("$path.name")
        if (!ids.add(id)) reject(BackupRejection.DuplicateId("$path.id"))
        if (!names.add(name.uniquenessKey)) {
            reject(BackupRejection.DuplicateCategoryName("$path.name"))
        }
        Category(
            id = id,
            name = name,
            icon = IconKey.of(row.icon).or("$path.icon"),
            archived = flag(row.archived, "$path.archived"),
            version = 1,
            createdAt = instant(row.createdAt, "$path.createdAt"),
            updatedAt = instant(row.updatedAt, "$path.updatedAt")
        )
    }
}

private fun reference(text: String, known: Set<CategoryId>, path: String): CategoryId {
    val id = recordId(text, path)
    if (id !in known) reject(BackupRejection.DanglingReference(path))
    return id
}

private fun month(text: String, path: String): YearMonth = EffectiveDates.parseMonth(text).or(path)

private fun currency(code: String, path: String): Currency {
    val currency = Currency.entries.firstOrNull { it.code == code }
    if (currency == null || !currency.isMvpEntryCurrency) {
        reject(BackupRejection.UnsupportedCurrency(path))
    }
    return currency
}

/** A limit's amount and currency come together, and only for a finite limit. */
private fun limit(
    kind: String,
    rawAmount: JsonElement,
    currencyCode: String?,
    path: String
): BudgetLimit {
    val amount = optionalWhole(rawAmount, "$path.amount")
    return when (kind) {
        WireNames.FINITE -> {
            if (amount == null) invalid("$path.amount")
            if (currencyCode == null) invalid("$path.currency")
            val money = Money(amount, currency(currencyCode, "$path.currency"))
            BudgetLimit.finite(money).or("$path.amount")
        }

        WireNames.UNLIMITED -> {
            if (amount != null) invalid("$path.amount")
            if (currencyCode != null) invalid("$path.currency")
            BudgetLimit.Unlimited
        }

        else -> invalid("$path.kind")
    }
}

private fun defaultLimits(wire: List<DefaultLimitV1>, known: Set<CategoryId>): List<DefaultLimit> {
    val entries = HashSet<Pair<CategoryId, YearMonth>>()
    val perCategory = HashMap<CategoryId, Int>()
    return wire.mapIndexed { index, row ->
        val path = "financial.defaultLimits[$index]"
        val categoryId = reference(row.categoryId, known, "$path.categoryId")
        val from = month(row.fromMonth, "$path.from")
        if (!entries.add(categoryId to from)) reject(BackupRejection.DuplicateEntry(path))
        val count = perCategory.merge(categoryId, 1, Int::plus)!!
        if (count > MAX_DEFAULT_HISTORY) reject(BackupRejection.HistoryTooLong(path))
        val budget = if (row.kind == WireNames.PAUSED) {
            if (row.amount !is JsonNull) invalid("$path.amount")
            if (row.currency != null) invalid("$path.currency")
            AutomaticBudget.Paused
        } else {
            AutomaticBudget.Limit(limit(row.kind, row.amount, row.currency, path))
        }
        DefaultLimit(categoryId, from, budget)
    }
}

private fun monthLimits(wire: List<MonthLimitV1>, known: Set<CategoryId>): List<MonthLimit> {
    val entries = HashSet<Pair<CategoryId, YearMonth>>()
    return wire.mapIndexed { index, row ->
        val path = "financial.monthLimits[$index]"
        val categoryId = reference(row.categoryId, known, "$path.categoryId")
        val month = month(row.month, "$path.month")
        if (!entries.add(categoryId to month)) reject(BackupRejection.DuplicateEntry(path))
        MonthLimit(categoryId, month, limit(row.kind, row.amount, row.currency, path))
    }
}

/** One expense or income amount: an amount outside the range rejects the file (ADR 0003). */
private fun amount(units: JsonElement, currencyCode: String, path: String): TransactionAmount {
    val money = Money(whole(units, "$path.amount"), currency(currencyCode, "$path.currency"))
    return when (val amount = TransactionAmount.of(money)) {
        is Outcome.Success -> amount.value

        is Outcome.Failure -> when (amount.error) {
            TransactionAmountError.NotPositive, TransactionAmountError.AboveMaximum ->
                reject(BackupRejection.AmountOutOfRange("$path.amount"))

            is TransactionAmountError.UnsupportedCurrency ->
                reject(BackupRejection.UnsupportedCurrency("$path.currency"))
        }
    }
}

/** Absent, or exactly the text the note rule stores; an empty note is written as null. */
private fun note(text: String?, path: String): Note? {
    if (text == null) return null
    val note = Note.of(text).or(path)
    if (note?.value != text) invalid(path)
    return note
}

private fun expenses(
    wire: List<ExpenseV3>,
    known: Set<CategoryId>,
    handled: Map<ScheduleId, Occurrence?>
): List<Expense> {
    val ids = HashSet<String>()
    val sequences = HashSet<Long>()
    val origins = HashSet<EntryOrigin>()
    return wire.mapIndexed { index, row ->
        val path = "financial.expenses[$index]"
        val id = ExpenseId.of(row.id).or("$path.id")
        if (!ids.add(id.value)) reject(BackupRejection.DuplicateId("$path.id"))
        val sequence = whole(row.sequence, "$path.sequence")
        if (!sequences.add(sequence)) reject(BackupRejection.DuplicateEntry("$path.sequence"))
        Expense(
            id = id,
            categoryId = reference(row.categoryId, known, "$path.categoryId"),
            date = EffectiveDates.parseDate(row.date).or("$path.date"),
            sequence = sequence,
            amount = amount(row.amount, row.currency, path),
            note = note(row.note, "$path.note"),
            version = 1,
            createdAt = instant(row.createdAt, "$path.createdAt"),
            updatedAt = instant(row.updatedAt, "$path.updatedAt"),
            origin = row.origin?.let { origin(it, "$path.origin", origins, handled) }
        )
    }
}

private fun incomes(wire: List<IncomeV3>, handled: Map<ScheduleId, Occurrence?>): List<Income> {
    val ids = HashSet<String>()
    val sequences = HashSet<Long>()
    val origins = HashSet<EntryOrigin>()
    return wire.mapIndexed { index, row ->
        val path = "financial.incomes[$index]"
        val id = IncomeId.of(row.id).or("$path.id")
        if (!ids.add(id.value)) reject(BackupRejection.DuplicateId("$path.id"))
        val sequence = whole(row.sequence, "$path.sequence")
        if (!sequences.add(sequence)) reject(BackupRejection.DuplicateEntry("$path.sequence"))
        if (row.source !in IncomeSource.keys) invalid("$path.source")
        val source = IncomeSource.of(row.source, row.sourceName).or("$path.sourceName")
        if ((source as? IncomeSource.Other)?.name?.value != row.sourceName) {
            invalid("$path.sourceName")
        }
        Income(
            id = id,
            date = EffectiveDates.parseDate(row.date).or("$path.date"),
            sequence = sequence,
            amount = amount(row.amount, row.currency, path),
            source = source,
            note = note(row.note, "$path.note"),
            version = 1,
            createdAt = instant(row.createdAt, "$path.createdAt"),
            updatedAt = instant(row.updatedAt, "$path.updatedAt"),
            origin = row.origin?.let { origin(it, "$path.origin", origins, handled) }
        )
    }
}

/** A slot is a bare 1, or 2 where [twice] allows it. */
private fun slot(element: JsonElement, twice: Boolean, path: String): Int =
    when (whole(element, path)) {
        1L -> 1
        2L -> if (twice) 2 else invalid(path)
        else -> invalid(path)
    }

/**
 * An entry's origin (rule 4 of docs/testing/recurrence-examples.md): recorded at most
 * once in its list, and, when its schedule is in the file, at or before that schedule's
 * checkpoint, or catch-up would try to record it again. A schedule deleted since is not
 * in the file, and its entries stay as they are.
 */
private fun origin(
    wire: OriginV3,
    path: String,
    seen: MutableSet<EntryOrigin>,
    handled: Map<ScheduleId, Occurrence?>
): EntryOrigin {
    val schedule = ScheduleId.of(wire.schedule).or("$path.schedule")
    val date = EffectiveDates.parseDate(wire.date).or("$path.date")
    val origin =
        EntryOrigin(schedule, Occurrence(date, slot(wire.slot, twice = true, "$path.slot")))
    if (!seen.add(origin)) reject(BackupRejection.DuplicateEntry(path))
    if (schedule in handled) {
        val checkpoint = handled[schedule] ?: invalid(path)
        val after = date.isAfter(checkpoint.date) ||
            (date == checkpoint.date && origin.occurrence.slot > checkpoint.slot)
        if (after) invalid(path)
    }
    return origin
}

/**
 * Each schedule as the app stores it: its entry with the amount rules of an expense, a
 * rule the domain accepts, a start before any end, pauses in order and apart, a
 * checkpoint whose slot fits the rule, and a creation order of its own. A payment points
 * at a category in the file and, if that category is archived, is paused, as archiving
 * leaves it (rule 9). Versions start again at 1.
 */
private fun schedules(wire: List<ScheduleV3>, categories: List<Category>): List<StoredSchedule> {
    val ids = HashSet<ScheduleId>()
    val sequences = HashSet<Long>()
    val known = categories.associateBy { it.id }
    return wire.mapIndexed { index, row ->
        val path = "financial.schedules[$index]"
        val id = ScheduleId.of(row.id).or("$path.id")
        if (!ids.add(id)) reject(BackupRejection.DuplicateId("$path.id"))
        val amount = amount(row.amount, row.currency, path)
        val note = note(row.note, "$path.note")
        val entry = when (row.kind) {
            WireNames.PAYMENT -> {
                if (row.source != null) invalid("$path.source")
                if (row.sourceName != null) invalid("$path.sourceName")
                val categoryId =
                    reference(
                        row.categoryId ?: invalid("$path.categoryId"),
                        known.keys,
                        "$path.categoryId"
                    )
                ScheduledEntry.Payment(categoryId, amount, note)
            }

            WireNames.INCOME -> {
                if (row.categoryId != null) invalid("$path.categoryId")
                val key = row.source ?: invalid("$path.source")
                if (key !in IncomeSource.keys) invalid("$path.source")
                val source = IncomeSource.of(key, row.sourceName).or("$path.sourceName")
                if ((source as? IncomeSource.Other)?.name?.value !=
                    row.sourceName
                ) {
                    invalid("$path.sourceName")
                }
                ScheduledEntry.Income(source, amount, note)
            }

            else -> invalid("$path.kind")
        }
        val first = whole(row.first, "$path.first").toIntOrNull("$path.first")
        val second = optionalWhole(row.second, "$path.second")?.toIntOrNull("$path.second")
        val rule: RepeatRule = when (row.repeat) {
            WireNames.TWICE_MONTHLY ->
                RepeatRule.TwiceMonthly.of(
                    first,
                    second ?: invalid("$path.second")
                ).orRule(first, path)

            WireNames.MONTHLY, WireNames.EVERY_DAYS, WireNames.EVERY_WEEKS -> {
                if (second != null) invalid("$path.second")
                when (row.repeat) {
                    WireNames.MONTHLY -> RepeatRule.Monthly.of(first)
                    WireNames.EVERY_DAYS -> RepeatRule.EveryDays.of(first)
                    else -> RepeatRule.EveryWeeks.of(first)
                }.or("$path.first")
            }

            else -> invalid("$path.repeat")
        }
        val start = EffectiveDates.parseDate(row.start).or("$path.start")
        val end = row.end?.let { EffectiveDates.parseDate(it).or("$path.end") }
        val pauses = row.pauses.mapIndexed { pauseIndex, pause ->
            SchedulePause(
                from = EffectiveDates.parseDate(pause.from).or("$path.pauses[$pauseIndex].from"),
                until = pause.until?.let {
                    EffectiveDates.parseDate(it).or("$path.pauses[$pauseIndex].until")
                }
            )
        }
        val recurrence = when (val built = Recurrence.of(rule, start, end, pauses)) {
            is Outcome.Success -> built.value

            is Outcome.Failure -> invalid(
                when (built.error) {
                    RecurrenceError.EndBeforeStart -> "$path.end"

                    is RecurrenceError.DateOutOfRange -> "$path.start"

                    is RecurrenceError.InvalidPause,
                    is RecurrenceError.OverlappingPauses -> "$path.pauses"
                }
            )
        }
        val category = (entry as? ScheduledEntry.Payment)?.let { known.getValue(it.categoryId) }
        val running = recurrence.pauses.lastOrNull()?.until != null || recurrence.pauses.isEmpty()
        if (category?.archived == true && running) invalid("$path.pauses")
        val sequence = whole(row.sequence, "$path.sequence")
        if (!sequences.add(sequence)) reject(BackupRejection.DuplicateEntry("$path.sequence"))
        val handled = row.handled?.let {
            Occurrence(
                EffectiveDates.parseDate(it.date).or("$path.handled.date"),
                slot(it.slot, rule is RepeatRule.TwiceMonthly, "$path.handled.slot")
            )
        }
        StoredSchedule(
            schedule = Schedule(id, entry, recurrence),
            sequence = sequence,
            handled = handled,
            version = 1,
            createdAt = instant(row.createdAt, "$path.createdAt"),
            updatedAt = instant(row.updatedAt, "$path.updatedAt")
        )
    }
}

private fun Long.toIntOrNull(path: String): Int = if (this <=
    Int.MAX_VALUE
) {
    toInt()
} else {
    invalid(path)
}

/** Two days twice a month: a day out of range is named as such, two out of order by the second. */
private fun Outcome<RepeatRule.TwiceMonthly, RepeatRuleError>.orRule(
    first: Int,
    path: String
): RepeatRule = when (this) {
    is Outcome.Success -> value

    is Outcome.Failure -> when (val error = error) {
        is RepeatRuleError.DayOutOfRange ->
            invalid(if (error.day == first) "$path.first" else "$path.second")

        is RepeatRuleError.DaysNotInOrder, is RepeatRuleError.IntervalOutOfRange ->
            invalid("$path.second")
    }
}

/**
 * Each goal and its history as the app stores them: names in stored form, the history
 * in recording order and starting with the target, every amount in range, and a
 * history that works out, so a withdrawal never takes more than was saved (rule 1 of
 * docs/testing/goal-examples.md). Versions start again at 1.
 */
private fun goals(wire: List<GoalV2>): List<GoalHistory> {
    val goalIds = HashSet<GoalId>()
    val eventIds = HashSet<GoalMovementId>()
    return wire.mapIndexed { index, row ->
        val path = "financial.goals[$index]"
        val id = GoalId.of(row.id).or("$path.id")
        if (!goalIds.add(id)) reject(BackupRejection.DuplicateId("$path.id"))
        val name = GoalName.of(row.name).or("$path.name")
        if (name.value != row.name) invalid("$path.name")
        val goal = Goal(
            id = id,
            name = name,
            icon = IconKey.of(row.icon).or("$path.icon"),
            targetMonth = row.targetMonth?.let { month(it, "$path.targetMonth") },
            completedOn = row.completedOn?.let {
                EffectiveDates.parseDate(it).or("$path.completedOn")
            },
            version = 1,
            createdAt = instant(row.createdAt, "$path.createdAt"),
            updatedAt = instant(row.updatedAt, "$path.updatedAt")
        )
        if (row.history.isEmpty()) invalid("$path.history")
        var previous = -1L
        val events = row.history.mapIndexed { position, event ->
            val at = "$path.history[$position]"
            val sequence = whole(event.sequence, "$at.sequence")
            if (sequence <= previous) reject(BackupRejection.DuplicateEntry("$at.sequence"))
            previous = sequence
            val amount = amount(event.amount, event.currency, at)
            val recordedOn = EffectiveDates.parseDate(event.recordedOn).or("$at.recordedOn")
            if (position == 0 && event.kind != WireNames.TARGET) invalid("$at.kind")
            when (event.kind) {
                WireNames.TARGET -> {
                    if (event.id != null) invalid("$at.id")
                    if (event.date != null) invalid("$at.date")
                    if (event.note != null) invalid("$at.note")
                    if (event.createdAt != null) invalid("$at.createdAt")
                    if (event.updatedAt != null) invalid("$at.updatedAt")
                    GoalTargetChange(amount, sequence, recordedOn)
                }

                WireNames.CONTRIBUTION, WireNames.WITHDRAWAL -> {
                    val movementId = GoalMovementId.of(event.id ?: invalid("$at.id")).or("$at.id")
                    if (!eventIds.add(movementId)) reject(BackupRejection.DuplicateId("$at.id"))
                    GoalMovement(
                        id = movementId,
                        kind = if (event.kind == WireNames.CONTRIBUTION) {
                            GoalMovementKind.Contribution
                        } else {
                            GoalMovementKind.Withdrawal
                        },
                        amount = amount,
                        date = EffectiveDates.parseDate(event.date ?: invalid("$at.date"))
                            .or("$at.date"),
                        sequence = sequence,
                        recordedOn = recordedOn,
                        note = note(event.note, "$at.note"),
                        version = 1,
                        createdAt = instant(
                            event.createdAt ?: invalid("$at.createdAt"),
                            "$at.createdAt"
                        ),
                        updatedAt = instant(
                            event.updatedAt ?: invalid("$at.updatedAt"),
                            "$at.updatedAt"
                        )
                    )
                }

                else -> invalid("$at.kind")
            }
        }
        val history = GoalHistory(goal, events)
        when (val worked = GoalPolicy.progress(history)) {
            is Outcome.Success -> Unit

            is Outcome.Failure -> when (val error = worked.error) {
                is GoalHistoryError.WithdrawalAboveSaved -> {
                    val position = events.indexOfFirst {
                        (it as? GoalMovement)?.id ==
                            error.withdrawal
                    }
                    invalid("$path.history[$position].amount")
                }

                GoalHistoryError.Unrepresentable -> invalid("$path.history")

                GoalHistoryError.NoTarget -> invalid("$path.history[0].kind")
            }
        }
        history
    }
}

/** An investment's or movement's currency: COP or USD, the only ones investments keep. */
private fun investmentCurrency(code: String, path: String): Currency =
    Currency.entries.firstOrNull { it.code == code && it in InvestmentAmount.currencies }
        ?: reject(BackupRejection.UnsupportedCurrency(path))

/** A movement's kind, by its wire spelling. */
private fun movementKind(text: String, path: String): InvestmentMovementKind = when (text) {
    WireNames.CONTRIBUTION -> InvestmentMovementKind.Contribution
    WireNames.WITHDRAWAL -> InvestmentMovementKind.Withdrawal
    WireNames.DIVIDEND -> InvestmentMovementKind.Dividend
    WireNames.VALUATION -> InvestmentMovementKind.Valuation
    else -> invalid(path)
}

/**
 * A movement's amount, in its investment's [currency] and nothing else (investment rule
 * 2): 1 to 999.999.999.999 minor units, or 0 as well for a recorded value.
 */
private fun investmentAmount(
    kind: InvestmentMovementKind,
    units: JsonElement,
    code: String,
    currency: Currency,
    path: String
): InvestmentAmount {
    val minor = whole(units, "$path.amount")
    if (investmentCurrency(code, "$path.currency") != currency) {
        reject(BackupRejection.UnsupportedCurrency("$path.currency"))
    }
    val money = Money(minor, currency)
    val amount = if (kind == InvestmentMovementKind.Valuation) {
        InvestmentAmount.ofValue(money)
    } else {
        InvestmentAmount.of(money)
    }
    return when (amount) {
        is Outcome.Success -> amount.value

        is Outcome.Failure -> when (amount.error) {
            InvestmentAmountError.BelowMinimum, InvestmentAmountError.AboveMaximum ->
                reject(BackupRejection.AmountOutOfRange("$path.amount"))

            is InvestmentAmountError.UnsupportedCurrency ->
                reject(BackupRejection.UnsupportedCurrency("$path.currency"))
        }
    }
}

/**
 * Each investment and its history as the app stores them (docs/testing/investment-examples.md):
 * a name and description in stored form, COP or USD, every movement in that currency and
 * within its bounds, the movements in recording order, and a history the rules accept, so
 * a retiro never takes more than the value on its date and nothing comes before the first
 * aporte. A movement belongs to the investment it is written in: the file cannot point one
 * anywhere else. Versions start again at 1.
 */
private fun investments(wire: List<InvestmentV4>): List<InvestmentHistory> {
    val investmentIds = HashSet<InvestmentId>()
    val movementIds = HashSet<InvestmentMovementId>()
    return wire.mapIndexed { index, row ->
        val path = "financial.investments[$index]"
        val id = InvestmentId.of(row.id).or("$path.id")
        if (!investmentIds.add(id)) reject(BackupRejection.DuplicateId("$path.id"))
        val name = InvestmentName.of(row.name).or("$path.name")
        if (name.value != row.name) invalid("$path.name")
        val description = InvestmentDescription.of(row.description).or("$path.description")
        if (description?.value != row.description) invalid("$path.description")
        val currency = investmentCurrency(row.currency, "$path.currency")
        val investment = Investment(
            id = id,
            name = name,
            description = description,
            icon = IconKey.of(row.icon).or("$path.icon"),
            currency = currency,
            version = 1,
            createdAt = instant(row.createdAt, "$path.createdAt"),
            updatedAt = instant(row.updatedAt, "$path.updatedAt")
        )
        var previous = -1L
        val movements = row.movements.mapIndexed { position, movement ->
            val at = "$path.movements[$position]"
            val movementId = InvestmentMovementId.of(movement.id).or("$at.id")
            if (!movementIds.add(movementId)) reject(BackupRejection.DuplicateId("$at.id"))
            val sequence = whole(movement.sequence, "$at.sequence")
            if (sequence <= previous) reject(BackupRejection.DuplicateEntry("$at.sequence"))
            // A restore puts the next recording position after the last one.
            if (sequence == Long.MAX_VALUE) invalid("$at.sequence")
            previous = sequence
            val kind = movementKind(movement.kind, "$at.kind")
            InvestmentMovement(
                id = movementId,
                kind = kind,
                amount = investmentAmount(kind, movement.amount, movement.currency, currency, at),
                date = EffectiveDates.parseDate(movement.date).or("$at.date"),
                sequence = sequence,
                note = note(movement.note, "$at.note"),
                version = 1,
                createdAt = instant(movement.createdAt, "$at.createdAt"),
                updatedAt = instant(movement.updatedAt, "$at.updatedAt")
            )
        }
        val history = InvestmentHistory(investment, movements)
        val worked = InvestmentPolicy.figures(history)
        if (worked is Outcome.Failure) invalid(historyPath(path, movements, worked.error))
        history
    }
}

/** Where a history the rules refuse goes wrong: the movement named, or the whole history. */
private fun historyPath(
    path: String,
    movements: List<InvestmentMovement>,
    error: InvestmentHistoryError
): String {
    fun place(movement: InvestmentMovementId) =
        "$path.movements[${movements.indexOfFirst { it.id == movement }}]"
    return when (error) {
        is InvestmentHistoryError.WithdrawalAboveValue -> "${place(error.withdrawal)}.amount"

        is InvestmentHistoryError.WithdrawalWithoutValue -> "${place(error.withdrawal)}.kind"

        is InvestmentHistoryError.BeforeFirstContribution -> "${place(error.movement)}.date"

        is InvestmentHistoryError.CurrencyMismatch -> "${place(error.movement)}.currency"

        InvestmentHistoryError.NoContribution,
        InvestmentHistoryError.Unrepresentable -> "$path.movements"
    }
}

/**
 * Every entered USD rate (investment rule 11): whole hundredths of a peso per dollar from
 * 1,00 to 100.000,00, so a decimal JSON number, such as a rate with three decimals, is
 * refused and never rounded; a real date; and an entry position of its own.
 */
private fun usdRates(wire: List<UsdRateV4>): List<UsdRateEntry> {
    val ids = HashSet<UsdRateId>()
    val sequences = HashSet<Long>()
    return wire.mapIndexed { index, row ->
        val path = "financial.usdRates[$index]"
        val id = UsdRateId.of(row.id).or("$path.id")
        if (!ids.add(id)) reject(BackupRejection.DuplicateId("$path.id"))
        val hundredths = whole(row.rateHundredths, "$path.rateHundredths")
        val sequence = whole(row.sequence, "$path.sequence")
        if (!sequences.add(sequence)) reject(BackupRejection.DuplicateEntry("$path.sequence"))
        UsdRateEntry(
            id = id,
            rate = UsdRate.ofHundredths(hundredths).or("$path.rateHundredths"),
            date = EffectiveDates.parseDate(row.date).or("$path.date"),
            sequence = sequence,
            createdAt = instant(row.createdAt, "$path.createdAt")
        )
    }
}

/**
 * The carry chains of a file (ADR 0020 CP26), checked for what they are and for what
 * they would need: every month a chain carries into has a finite base of at most
 * [MAX_CHAIN_BASE] (CP25), so no restored chain reaches a month the calculation cannot
 * answer. A month is checked where its base can change: the chain's first month, each
 * default's first month, each month with a limit of its own and the month after it. An
 * open chain reaches every later month, so the default in force after the last one, its
 * latest, is checked too (CP3). A chain Sello could not have written is
 * refused as well: one starting after the month the backup was made in (CP3), and an open one on
 * an archived category (CP9, CP11). A file holds no carry, so none is validated here; the
 * figures are worked out again from the records. They fit a signed `Long` whatever the
 * file says: the base is capped, and no more than 100,000 records of at most
 * 999.999.999.999 each are spent against it.
 */
private fun carryChains(
    wire: List<CarryChainV6>,
    categories: List<Category>,
    defaults: List<DefaultLimit>,
    overrides: List<MonthLimit>,
    exportMonth: YearMonth
): List<CarryChain> {
    val known = categories.mapTo(HashSet()) { it.id }
    val archived = categories.filter { it.archived }.mapTo(HashSet()) { it.id }
    val starts = HashSet<Pair<CategoryId, YearMonth>>()
    val paths = HashMap<CarryChain, String>()
    val chains = wire.mapIndexed { index, row ->
        val path = "financial.carryChains[$index]"
        val category = reference(row.categoryId, known, "$path.categoryId")
        val start = month(row.start, "$path.start")
        val end = row.end?.let { month(it, "$path.end") }
        if (!starts.add(category to start)) reject(BackupRejection.DuplicateEntry("$path.start"))
        // A chain that ends in or before its start never existed: it is removed, not stored.
        val chain = CarryChain.of(category, start, end).or("$path.end")
        // Sello writes neither: carry starts in the current month (CP3), and archiving
        // ends the chain (CP9, CP11), so no file of Sello's can hold these.
        if (start > exportMonth) {
            reject(BackupRejection.InvalidChain(path, ChainFault.StartsAfterExport))
        }
        if (end == null && category in archived) {
            reject(BackupRejection.InvalidChain(path, ChainFault.OpenOnArchivedCategory))
        }
        chain.also { paths[it] = path }
    }
    val defaultsOf = defaults.groupBy { it.categoryId }
    val overridesOf = overrides.groupBy { it.categoryId }
    for ((category, own) in chains.groupBy { it.categoryId }) {
        val sorted = own.sortedBy { it.start }
        sorted.zipWithNext().firstOrNull { (earlier, later) ->
            earlier.end == null || later.start < earlier.end
        }?.let { (_, later) ->
            reject(BackupRejection.InvalidChain(paths.getValue(later), ChainFault.Overlap))
        }
        val ownDefaults = defaultsOf[category].orEmpty()
        val ownOverrides = overridesOf[category].orEmpty().associateBy { it.month }
        val changes = TreeSet<YearMonth>().apply {
            ownDefaults.mapTo(this) { it.effectiveMonth }
            ownOverrides.keys.forEach {
                add(it)
                add(it.plusMonths(1))
            }
        }
        for (chain in sorted) {
            val months = if (chain.end == null) {
                changes.tailSet(chain.start, true)
            } else {
                changes.subSet(chain.start, true, chain.end, false)
            }
            for (month in listOf(chain.start) + months) {
                val limit = (
                    CategoryBudgetPolicy.stateFor(month, ownDefaults, ownOverrides[month])
                        as? MonthBudgetState.Limited
                    )?.limit as? BudgetLimit.Finite
                if (limit == null) {
                    reject(
                        BackupRejection.InvalidChain(paths.getValue(chain), ChainFault.NoFiniteBase)
                    )
                }
                if (limit.amount.minorUnits > MAX_CHAIN_BASE) {
                    reject(
                        BackupRejection.InvalidChain(
                            paths.getValue(chain),
                            ChainFault.BaseAboveLimit
                        )
                    )
                }
            }
        }
    }
    return chains
}

private fun <T> Map<T, String>.decode(text: String, path: String): T =
    entries.firstOrNull { it.value == text }?.key ?: invalid(path)

private fun preferences(wire: PreferencesV5) = PortablePreferences(
    appearance = AppearancePreference(
        theme = WireNames.themes.decode(wire.theme, "preferences.theme"),
        ink = WireNames.inks.decode(wire.ink, "preferences.ink")
    ),
    ordering = CategoryOrderingPreference(
        order = WireNames.orders.decode(wire.categoryOrder, "preferences.categoryOrder"),
        direction = WireNames.directions.decode(
            wire.categoryDirection,
            "preferences.categoryDirection"
        )
    ),
    investments = InvestmentPreference(
        newCurrency = WireNames.newCurrencies.decode(
            wire.investmentCurrency,
            "preferences.investmentCurrency"
        ),
        order = WireNames.investmentOrders.decode(
            wire.investmentOrder,
            "preferences.investmentOrder"
        )
    )
)
