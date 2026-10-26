package com.software.sello.data.export

import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import java.math.BigDecimal
import java.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON export (`sello-export`, version 1): the movements of the CSV with stable
 * codes, and categories with their limits, goals, investments, the USD rates and the
 * recurring payments and incomes. Names stand in for identifiers; amounts are exact text
 * from minor units. Not a backup: it has no identifiers to restore from.
 */
internal object JsonExport {
    const val MIME_TYPE = "application/json"
    const val FORMAT = "sello-export"
    const val VERSION = 1

    @OptIn(ExperimentalSerializationApi::class)
    private val writer = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun encode(snapshot: FinancialSnapshot, exportedAt: Instant): ByteArray {
        val file = ExportFileV1(
            format = FORMAT,
            version = VERSION,
            exportedAt = exportedAt.toString(),
            zone = snapshot.zone.id,
            revision = snapshot.revision,
            movements = movements(snapshot).map { it.toJson() },
            categories = categories(snapshot),
            goals = snapshot.goals.map { history ->
                val target = history.events.filterIsInstance<GoalTargetChange>()
                    .maxByOrNull { it.sequence }?.target?.money
                GoalJson(
                    name = history.goal.name.value,
                    icon = history.goal.icon.value,
                    target = target?.exportText(),
                    currency = target?.currency?.code,
                    targetMonth = history.goal.targetMonth?.toString(),
                    completedOn = history.goal.completedOn?.toString()
                )
            },
            investments = snapshot.investments.map {
                InvestmentJson(
                    name = it.investment.name.value,
                    description = it.investment.description?.value,
                    icon = it.investment.icon.value,
                    currency = it.investment.currency.code
                )
            },
            usdRates = snapshot.usdRates.sortedWith(compareBy({ it.date }, { it.sequence })).map {
                RateJson(
                    it.date.toString(),
                    BigDecimal.valueOf(it.rate.hundredths, 2).toPlainString()
                )
            },
            recurring = snapshot.schedules.sortedBy { it.sequence }.map { stored ->
                val schedule = stored.schedule
                val entry = schedule.entry
                val rule = schedule.recurrence.rule
                val source = (entry as? ScheduledEntry.Income)?.source
                RecurringJson(
                    kind = if (entry is ScheduledEntry.Payment) "payment" else "income",
                    category = (entry as? ScheduledEntry.Payment)?.let { payment ->
                        snapshot.categories.first { it.id == payment.categoryId }.name.value
                    },
                    source = source?.key,
                    sourceName = (source as? IncomeSource.Other)?.name?.value,
                    amount = entry.amount.money.exportText(),
                    currency = entry.amount.money.currency.code,
                    note = entry.note?.value,
                    repeat = when (rule) {
                        is RepeatRule.Monthly -> "monthly"
                        is RepeatRule.TwiceMonthly -> "twice_monthly"
                        is RepeatRule.EveryDays -> "every_days"
                        is RepeatRule.EveryWeeks -> "every_weeks"
                    },
                    days = when (rule) {
                        is RepeatRule.Monthly -> listOf(rule.day)
                        is RepeatRule.TwiceMonthly -> listOf(rule.first, rule.second)
                        else -> null
                    },
                    every = when (rule) {
                        is RepeatRule.EveryDays -> rule.days
                        is RepeatRule.EveryWeeks -> rule.weeks
                        else -> null
                    },
                    start = schedule.recurrence.start.toString(),
                    end = schedule.recurrence.end?.toString(),
                    pauses = schedule.recurrence.pauses.sortedBy { it.from }.map {
                        PauseJson(it.from.toString(), it.until?.toString())
                    }
                )
            }
        )
        return (writer.encodeToString(ExportFileV1.serializer(), file) + "\n")
            .toByteArray(Charsets.UTF_8)
    }

    private fun ExportRow.toJson() = MovementJson(
        date = date.toString(),
        kind = kind.code,
        name = (name as? ExportName.Named)?.text,
        source = (name as? ExportName.Source)?.key
            ?: IncomeSource.Other.KEY.takeIf { kind == ExportKind.Income },
        amount = amount.exportText(),
        currency = amount.currency.code,
        note = note
    )

    private fun categories(snapshot: FinancialSnapshot) = snapshot.categories.sortedWith(
        compareBy({
            it.createdAt
        }, { it.id.value })
    ).map { category ->
        CategoryJson(
            name = category.name.value,
            icon = category.icon.value,
            archived = category.archived,
            limits = snapshot.defaultLimits.filter { it.categoryId == category.id }
                .sortedBy { it.effectiveMonth }
                .map {
                    when (val budget = it.budget) {
                        AutomaticBudget.Paused ->
                            LimitJson(it.effectiveMonth.toString(), "paused", null, null)

                        is AutomaticBudget.Limit ->
                            limit(it.effectiveMonth.toString(), budget.limit)
                    }
                },
            monthLimits = snapshot.monthLimits.filter { it.categoryId == category.id }
                .sortedBy { it.month }
                .map { limit(it.month.toString(), it.limit) }
        )
    }

    private fun limit(month: String, limit: BudgetLimit): LimitJson = when (limit) {
        BudgetLimit.Unlimited -> LimitJson(month, "unlimited", null, null)
        is BudgetLimit.Finite -> amountLimit(month, limit.amount)
    }

    private fun amountLimit(month: String, amount: Money) =
        LimitJson(month, "limit", amount.exportText(), amount.currency.code)
}

@Serializable
internal data class ExportFileV1(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val zone: String,
    val revision: Long,
    val movements: List<MovementJson>,
    val categories: List<CategoryJson>,
    val goals: List<GoalJson>,
    val investments: List<InvestmentJson>,
    val usdRates: List<RateJson>,
    val recurring: List<RecurringJson>
)

/** `source` is the income source's key for an income, `other` with [name]; null otherwise. */
@Serializable
internal data class MovementJson(
    val date: String,
    val kind: String,
    val name: String?,
    val source: String?,
    val amount: String,
    val currency: String,
    val note: String?
)

/** `budget` is `limit` (with an amount), `unlimited` or `paused` (archived). */
@Serializable
internal data class LimitJson(
    val month: String,
    val budget: String,
    val amount: String?,
    val currency: String?
)

@Serializable
internal data class CategoryJson(
    val name: String,
    val icon: String,
    val archived: Boolean,
    val limits: List<LimitJson>,
    val monthLimits: List<LimitJson>
)

@Serializable
internal data class GoalJson(
    val name: String,
    val icon: String,
    val target: String?,
    val currency: String?,
    val targetMonth: String?,
    val completedOn: String?
)

@Serializable
internal data class InvestmentJson(
    val name: String,
    val description: String?,
    val icon: String,
    val currency: String
)

@Serializable
internal data class RateJson(val date: String, val rate: String)

@Serializable
internal data class PauseJson(val from: String, val until: String?)

/** `days` for `monthly` and `twice_monthly`; `every` for `every_days` and `every_weeks`. */
@Serializable
internal data class RecurringJson(
    val kind: String,
    val category: String?,
    val source: String?,
    val sourceName: String?,
    val amount: String,
    val currency: String,
    val note: String?,
    val repeat: String,
    val days: List<Int>?,
    val every: Int?,
    val start: String,
    val end: String?,
    val pauses: List<PauseJson>
)
