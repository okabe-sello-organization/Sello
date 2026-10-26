package com.software.sello.data.backup

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentOrder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/*
 * The wire shapes of versions 1 to 5, documented in docs/data/backup-format.md.
 * Version 2 is version 1 plus `financial.goals` and `financial.featuredGoal`; version 3
 * is version 2 plus `financial.schedules` and each expense's and income's `origin`;
 * version 4 is version 3 plus `financial.investments` and `financial.usdRates`. These classes
 * are the file and nothing else: they never become Room entities or domain values
 * without going through BackupValidation. Every property is required, including the
 * nullable ones, and the JSON configuration refuses unknown keys. Numbers and flags
 * are kept as raw JSON for validation to check strictly.
 */

/** Only the two keys every version must keep, read before the rest of the file. */
@Serializable
internal data class BackupHeaderWire(
    val format: JsonElement? = null,
    val version: JsonElement? = null
)

@Serializable
internal data class BackupFileV1(
    val format: String,
    val version: JsonElement,
    val exportedAt: String,
    val financial: FinancialV1,
    val preferences: PreferencesV1?
)

@Serializable
internal data class FinancialV1(
    val sourceRevision: JsonElement,
    val zone: String,
    val categories: List<CategoryV1>,
    val defaultLimits: List<DefaultLimitV1>,
    val monthLimits: List<MonthLimitV1>,
    val expenses: List<ExpenseV1>,
    val incomes: List<IncomeV1>
)

@Serializable
internal data class CategoryV1(
    val id: String,
    val name: String,
    val icon: String,
    val archived: JsonElement,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class DefaultLimitV1(
    val categoryId: String,
    @SerialName("from") val fromMonth: String,
    val kind: String,
    val amount: JsonElement,
    val currency: String?
)

@Serializable
internal data class MonthLimitV1(
    val categoryId: String,
    val month: String,
    val kind: String,
    val amount: JsonElement,
    val currency: String?
)

@Serializable
internal data class ExpenseV1(
    val id: String,
    val categoryId: String,
    val date: String,
    val sequence: JsonElement,
    val amount: JsonElement,
    val currency: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class IncomeV1(
    val id: String,
    val date: String,
    val sequence: JsonElement,
    val amount: JsonElement,
    val currency: String,
    val source: String,
    val sourceName: String?,
    val note: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
internal data class BackupFileV2(
    val format: String,
    val version: JsonElement,
    val exportedAt: String,
    val financial: FinancialV2,
    val preferences: PreferencesV1?
)

@Serializable
internal data class FinancialV2(
    val sourceRevision: JsonElement,
    val zone: String,
    val categories: List<CategoryV1>,
    val defaultLimits: List<DefaultLimitV1>,
    val monthLimits: List<MonthLimitV1>,
    val expenses: List<ExpenseV1>,
    val incomes: List<IncomeV1>,
    val goals: List<GoalV2>,
    val featuredGoal: String?
)

/** A goal and its history, targets and movements, in the order they were recorded. */
@Serializable
internal data class GoalV2(
    val id: String,
    val name: String,
    val icon: String,
    val targetMonth: String?,
    val completedOn: String?,
    val createdAt: String,
    val updatedAt: String,
    val history: List<GoalEventV2>
)

/**
 * `kind` is `target`, `contribution` or `withdrawal`. A target has no `id`, `date`,
 * `note` or audit times: nothing refers to it but its place in the history.
 */
@Serializable
internal data class GoalEventV2(
    val id: String?,
    val sequence: JsonElement,
    val kind: String,
    val amount: JsonElement,
    val currency: String,
    val date: String?,
    val recordedOn: String,
    val note: String?,
    val createdAt: String?,
    val updatedAt: String?
)

/** A version 1 file read as version 2: it holds no goals. */
internal fun BackupFileV1.asVersion2() = BackupFileV2(
    format = format,
    version = version,
    exportedAt = exportedAt,
    financial = FinancialV2(
        sourceRevision = financial.sourceRevision,
        zone = financial.zone,
        categories = financial.categories,
        defaultLimits = financial.defaultLimits,
        monthLimits = financial.monthLimits,
        expenses = financial.expenses,
        incomes = financial.incomes,
        goals = emptyList(),
        featuredGoal = null
    ),
    preferences = preferences
)

@Serializable
internal data class BackupFileV3(
    val format: String,
    val version: JsonElement,
    val exportedAt: String,
    val financial: FinancialV3,
    val preferences: PreferencesV1?
)

@Serializable
internal data class FinancialV3(
    val sourceRevision: JsonElement,
    val zone: String,
    val categories: List<CategoryV1>,
    val defaultLimits: List<DefaultLimitV1>,
    val monthLimits: List<MonthLimitV1>,
    val expenses: List<ExpenseV3>,
    val incomes: List<IncomeV3>,
    val goals: List<GoalV2>,
    val featuredGoal: String?,
    val schedules: List<ScheduleV3>
)

/** The occurrence a schedule recorded this entry for, or null for one entered by hand. */
@Serializable
internal data class OriginV3(val schedule: String, val date: String, val slot: JsonElement)

@Serializable
internal data class ExpenseV3(
    val id: String,
    val categoryId: String,
    val date: String,
    val sequence: JsonElement,
    val amount: JsonElement,
    val currency: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val origin: OriginV3?
)

@Serializable
internal data class IncomeV3(
    val id: String,
    val date: String,
    val sequence: JsonElement,
    val amount: JsonElement,
    val currency: String,
    val source: String,
    val sourceName: String?,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val origin: OriginV3?
)

/** The last occurrence catch-up dealt with. */
@Serializable
internal data class HandledV3(val date: String, val slot: JsonElement)

@Serializable
internal data class PauseV3(val from: String, val until: String?)

/**
 * A recurring payment (`kind` `payment`, with `categoryId`) or income (`income`, with
 * `source` and, for `other`, `sourceName`). `repeat` is `monthly` (`first` the day),
 * `twice_monthly` (`first` and `second` the days), `every_days` or `every_weeks` (`first`
 * the interval); `second` is null but twice a month. `sequence` is the creation order.
 */
@Serializable
internal data class ScheduleV3(
    val id: String,
    val kind: String,
    val categoryId: String?,
    val source: String?,
    val sourceName: String?,
    val amount: JsonElement,
    val currency: String,
    val note: String?,
    val repeat: String,
    val first: JsonElement,
    val second: JsonElement,
    val start: String,
    val end: String?,
    val sequence: JsonElement,
    val handled: HandledV3?,
    val pauses: List<PauseV3>,
    val createdAt: String,
    val updatedAt: String
)

/** A version 2 file read as version 3: no schedules, and no entry recorded by one. */
internal fun BackupFileV2.asVersion3() = BackupFileV3(
    format = format,
    version = version,
    exportedAt = exportedAt,
    financial = FinancialV3(
        sourceRevision = financial.sourceRevision,
        zone = financial.zone,
        categories = financial.categories,
        defaultLimits = financial.defaultLimits,
        monthLimits = financial.monthLimits,
        expenses = financial.expenses.map {
            ExpenseV3(
                id = it.id,
                categoryId = it.categoryId,
                date = it.date,
                sequence = it.sequence,
                amount = it.amount,
                currency = it.currency,
                note = it.note,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                origin = null
            )
        },
        incomes = financial.incomes.map {
            IncomeV3(
                id = it.id,
                date = it.date,
                sequence = it.sequence,
                amount = it.amount,
                currency = it.currency,
                source = it.source,
                sourceName = it.sourceName,
                note = it.note,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                origin = null
            )
        },
        goals = financial.goals,
        featuredGoal = financial.featuredGoal,
        schedules = emptyList()
    ),
    preferences = preferences
)

@Serializable
internal data class BackupFileV4(
    val format: String,
    val version: JsonElement,
    val exportedAt: String,
    val financial: FinancialV4,
    val preferences: PreferencesV1?
)

@Serializable
internal data class FinancialV4(
    val sourceRevision: JsonElement,
    val zone: String,
    val categories: List<CategoryV1>,
    val defaultLimits: List<DefaultLimitV1>,
    val monthLimits: List<MonthLimitV1>,
    val expenses: List<ExpenseV3>,
    val incomes: List<IncomeV3>,
    val goals: List<GoalV2>,
    val featuredGoal: String?,
    val schedules: List<ScheduleV3>,
    val investments: List<InvestmentV4>,
    val usdRates: List<UsdRateV4>
)

/**
 * An investment in `COP` or `USD` and its whole history, in recording order. Value, gain
 * and return are not here: they are worked out from [movements].
 */
@Serializable
internal data class InvestmentV4(
    val id: String,
    val name: String,
    val description: String?,
    val icon: String,
    val currency: String,
    val createdAt: String,
    val updatedAt: String,
    val movements: List<InvestmentMovementV4>
)

/**
 * `kind` is `contribution`, `withdrawal`, `dividend` or `valuation`; `amount` is whole
 * minor units of `currency`, the investment's: pesos, or USD cents.
 */
@Serializable
internal data class InvestmentMovementV4(
    val id: String,
    val sequence: JsonElement,
    val kind: String,
    val amount: JsonElement,
    val currency: String,
    val date: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String
)

/**
 * One entered USD rate: pesos per dollar as whole hundredths, `415025` for 4.150,25,
 * never a decimal number. `sequence` is the order rates were entered in.
 */
@Serializable
internal data class UsdRateV4(
    val id: String,
    val rateHundredths: JsonElement,
    val date: String,
    val sequence: JsonElement,
    val createdAt: String
)

/** A version 3 file read as version 4: no investments and no rates. */
internal fun BackupFileV3.asVersion4() = BackupFileV4(
    format = format,
    version = version,
    exportedAt = exportedAt,
    financial = FinancialV4(
        sourceRevision = financial.sourceRevision,
        zone = financial.zone,
        categories = financial.categories,
        defaultLimits = financial.defaultLimits,
        monthLimits = financial.monthLimits,
        expenses = financial.expenses,
        incomes = financial.incomes,
        goals = financial.goals,
        featuredGoal = financial.featuredGoal,
        schedules = financial.schedules,
        investments = emptyList(),
        usdRates = emptyList()
    ),
    preferences = preferences
)

@Serializable
internal data class PreferencesV1(
    val theme: String,
    val ink: String,
    val categoryOrder: String,
    val categoryDirection: String
)

/** Version 5: version 4 with Inversiones' two settings in `preferences` (SELLO-120). */
@Serializable
internal data class BackupFileV5(
    val format: String,
    val version: JsonElement,
    val exportedAt: String,
    val financial: FinancialV4,
    val preferences: PreferencesV5?
)

@Serializable
internal data class PreferencesV5(
    val theme: String,
    val ink: String,
    val categoryOrder: String,
    val categoryDirection: String,
    val investmentCurrency: String,
    val investmentOrder: String
)

/** A version 4 file read as version 5: its investments start in pesos, largest value first. */
internal fun BackupFileV4.asVersion5() = BackupFileV5(
    format = format,
    version = version,
    exportedAt = exportedAt,
    financial = financial,
    preferences = preferences?.let {
        PreferencesV5(
            theme = it.theme,
            ink = it.ink,
            categoryOrder = it.categoryOrder,
            categoryDirection = it.categoryDirection,
            investmentCurrency = WireNames.newCurrencies.getValue(Currency.COP),
            investmentOrder = WireNames.investmentOrders.getValue(InvestmentOrder.LargestValue)
        )
    }
)

private val plainWhole = Regex("0|[1-9][0-9]*")

/**
 * A bare, non-negative JSON integer: `125000`. The JSON library alone would also take
 * `"125000"`, `1.25e5` and `0125000` as a number, so numbers stay raw JSON until
 * validation checks the literal as written and knows where in the file it is.
 */
internal fun JsonElement.wholeNumberOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.isString || !plainWhole.matches(primitive.content)) return null
    return primitive.content.toLongOrNull()
}
