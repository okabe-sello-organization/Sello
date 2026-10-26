package com.software.sello.data.backup

import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.port.StoredSchedule
import java.time.Instant
import java.time.ZoneId

/**
 * Every financial fact a backup carries, read at one [revision]. Record versions in a
 * snapshot are the stored ones; in a validated file they are all 1, because a file
 * carries no runtime versions and a restore starts each record's history again.
 */
data class FinancialSnapshot(
    val zone: ZoneId,
    val revision: Long,
    val categories: List<Category>,
    val defaultLimits: List<DefaultLimit>,
    val monthLimits: List<MonthLimit>,
    val expenses: List<Expense>,
    val incomes: List<Income>,
    /** Each goal with its whole history, the earliest created first. */
    val goals: List<GoalHistory>,
    /** Recibo's chosen goal, one of [goals], or null. */
    val featuredGoal: GoalId?,
    /** Recurring payments and incomes in creation order, with their pauses and checkpoints. */
    val schedules: List<StoredSchedule>,
    /**
     * Each investment with every movement in recording order, the earliest created first.
     * Its next recording position is not carried: a restore puts it after the last one.
     */
    val investments: List<InvestmentHistory>,
    /** Every entered USD rate, as entered; never changed by a restore. */
    val usdRates: List<UsdRateEntry>
) {
    val counts: BackupCounts
        get() = BackupCounts(
            categories = categories.size,
            archivedCategories = categories.count { it.archived },
            defaultLimits = defaultLimits.size,
            monthLimits = monthLimits.size,
            expenses = expenses.size,
            incomes = incomes.size,
            goals = goals.size,
            goalEvents = goals.sumOf { it.events.size },
            schedules = schedules.size,
            investments = investments.size,
            investmentMovements = investments.sumOf { it.movements.size },
            usdRates = usdRates.size
        )
}

/** The preferences that travel with a backup. Device-only notices stay behind. */
data class PortablePreferences(
    val appearance: AppearancePreference,
    val ordering: CategoryOrderingPreference,
    /** Version 5 on; an older file brings the defaults (SELLO-120). */
    val investments: InvestmentPreference = InvestmentPreference()
)

/**
 * A whole backup. [preferences] is null when they could not be read while backing
 * up: the money is what matters, so the file is still made, and says so.
 */
data class BackupContents(
    val exportedAt: Instant,
    val financial: FinancialSnapshot,
    val preferences: PortablePreferences?
)

data class BackupCounts(
    val categories: Int,
    val archivedCategories: Int,
    val defaultLimits: Int,
    val monthLimits: Int,
    val expenses: Int,
    val incomes: Int,
    val goals: Int,
    val goalEvents: Int,
    val schedules: Int,
    val investments: Int,
    val investmentMovements: Int,
    val usdRates: Int
)

/**
 * A file that passed every check, ready for a restore to preview and apply. Making
 * one writes nothing anywhere.
 */
data class BackupPlan(val contents: BackupContents) {
    val counts: BackupCounts get() = contents.financial.counts
}

/**
 * Why a file is not a usable backup. The first problem found rejects the whole file;
 * nothing in it is skipped, cut or repaired. [path]s name the place in the file, such
 * as `financial.expenses[3].amount`, never its contents.
 */
sealed interface BackupRejection {
    data class TooLarge(val maxBytes: Int, val actualBytes: Long) : BackupRejection

    /** The file ends before its JSON does: most likely a copy that did not finish. */
    data object Truncated : BackupRejection

    /** Not valid JSON in the shape this version describes. */
    data class Malformed(val path: String?) : BackupRejection

    data class TooDeep(val maxDepth: Int) : BackupRejection

    data class TooManyRecords(val maxRecords: Int) : BackupRejection

    data class FieldTooLong(val maxCharacters: Int) : BackupRejection

    /** The same key twice in one object, which JSON readers disagree about. */
    data class DuplicateKey(val key: String) : BackupRejection

    /** Valid JSON, but not a Sello backup at all. */
    data object NotSelloBackup : BackupRejection

    /** A Sello backup of a version this app cannot read, such as one from a newer app. */
    data class UnsupportedVersion(val version: Long) : BackupRejection

    data class InvalidValue(val path: String) : BackupRejection

    /**
     * Only COP can be recorded, except that an investment and its movements may be USD;
     * any other currency, or a movement not in its investment's, rejects the file.
     */
    data class UnsupportedCurrency(val path: String) : BackupRejection

    /**
     * Outside 1 to 999.999.999.999 COP for one expense or income (ADR 0003), or outside
     * 1 to 999.999.999.999 minor units for an investment movement (0 allowed for a value).
     */
    data class AmountOutOfRange(val path: String) : BackupRejection

    data class DuplicateId(val path: String) : BackupRejection

    data class DuplicateCategoryName(val path: String) : BackupRejection

    /** Two entries for the same category and month, or two records in one ordering place. */
    data class DuplicateEntry(val path: String) : BackupRejection

    /** Points at a category the file does not contain. */
    data class DanglingReference(val path: String) : BackupRejection

    /** More default-limit history for one category than the app can hold. */
    data class HistoryTooLong(val path: String) : BackupRejection
}
