package com.software.sello.data.backup

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.CarryChainEntity
import com.software.sello.data.local.entity.CategoryEntity
import com.software.sello.data.local.entity.DefaultLimitEntity
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.data.local.entity.MonthLimitEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toHistory
import com.software.sello.data.mapper.toStored
import com.software.sello.data.repository.PAGE
import com.software.sello.data.repository.pages
import com.software.sello.data.repository.reading
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.BackupExports
import com.software.sello.domain.port.BackupPreparationFailure
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreparedBackup
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Every financial row, read in one transaction so all of it belongs to one revision. */
class RoomBackupSnapshots(private val database: SelloDatabase) {
    suspend fun read(): Outcome<FinancialSnapshot, StorageFailure> =
        reading { database.withTransaction { database.snapshot() } }

    /** Just the financial zone, for naming a file before the data is read. */
    suspend fun zone(): Outcome<ZoneId, StorageFailure> = reading {
        when (val profile = profileFrom(database.profileDao().rows())) {
            is Outcome.Success -> Outcome.Success(profile.value.zone)
            is Outcome.Failure -> profile
        }
    }
}

private suspend fun SelloDatabase.snapshot(): Outcome<FinancialSnapshot, StorageFailure> {
    val profile = when (val stored = profileFrom(profileDao().rows())) {
        is Outcome.Success -> stored.value
        is Outcome.Failure -> return stored
    }
    val categories = pages<CategoryEntity> { categoryDao().page(it?.id.orEmpty(), PAGE) }
        .decodeAll { it.toDomain() }
    val defaults = pages<DefaultLimitEntity> {
        limitDao().allDefaults(it?.categoryId.orEmpty(), it?.effectiveMonth.orEmpty(), PAGE)
    }.decodeAll { it.toDomain() }
    val monthLimits = pages<MonthLimitEntity> {
        limitDao().allMonthLimits(it?.categoryId.orEmpty(), it?.month.orEmpty(), PAGE)
    }.decodeAll { it.toDomain() }
    val expenses = pages<ExpenseEntity> {
        expenseDao().all(it?.effectiveDate.orEmpty(), it?.sequence ?: 0, PAGE)
    }.decodeAll { it.toDomain() }
    val incomes = pages<IncomeEntity> {
        incomeDao().all(it?.effectiveDate.orEmpty(), it?.sequence ?: 0, PAGE)
    }.decodeAll { it.toDomain() }
    val events = goalDao().allEvents().groupBy { it.goalId }
    val goals = goalDao().all().decodeAll { it.toHistory(events[it.id].orEmpty()) }
    val featured = goalDao().featured()?.let { row ->
        when (val id = GoalId.of(row.goalId)) {
            is Outcome.Success -> id.value

            is Outcome.Failure ->
                return Outcome.Failure(StorageFailure.Integrity("featured_goal", null, "goal_id"))
        }
    }
    val pauses = scheduleDao().allPauses().groupBy { it.scheduleId }
    val schedules = scheduleDao().all().decodeAll { it.toStored(pauses[it.id].orEmpty()) }
    // In recording order, as a file writes them; the reads replay them by date instead.
    val movements = investmentDao().allMovements().sortedBy { it.sequence }
        .groupBy { it.investmentId }
    val investments = investmentDao().all()
        .decodeAll { it.toHistory(movements[it.id].orEmpty()) }
    val rates = investmentDao().rates().decodeAll { it.toDomain() }
    val chains = pages<CarryChainEntity> {
        carryChainDao().all(it?.categoryId.orEmpty(), it?.startMonth.orEmpty(), PAGE)
    }.decodeAll { it.toDomain() }
    return Outcome.Success(
        FinancialSnapshot(
            zone = profile.zone,
            revision = profile.revision,
            categories = categories.orReturn { return it },
            defaultLimits = defaults.orReturn { return it },
            monthLimits = monthLimits.orReturn { return it },
            expenses = expenses.orReturn { return it },
            incomes = incomes.orReturn { return it },
            goals = goals.orReturn { return it },
            featuredGoal = featured,
            schedules = schedules.orReturn { return it },
            investments = investments.orReturn { return it },
            usdRates = rates.orReturn { return it },
            carryChains = chains.orReturn { return it }
        )
    )
}

private inline fun <T, E> Outcome<T, E>.orReturn(failed: (Outcome.Failure<E>) -> Nothing): T =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> failed(this)
    }

/** A backup ready to be written to a document the person chose. */
class BackupFile(
    val name: String,
    val bytes: ByteArray,
    val counts: BackupCounts,
    val preferencesIncluded: Boolean
)

sealed interface BackupExportFailure {
    /** The financial data could not be read; nothing was produced. */
    data class Storage(val failure: StorageFailure) : BackupExportFailure

    /** The data read would make a file the app itself refuses, such as one too large. */
    data class Unrestorable(val rejection: BackupRejection) : BackupExportFailure
}

/**
 * Makes a backup without changing anything. The financial rows come from one
 * revision; preferences are read separately afterwards and do not pretend to share
 * that moment. If they cannot be read, the backup is made without them.
 */
class BackupExport(
    private val snapshots: RoomBackupSnapshots,
    private val appearance: AppearancePreferences,
    private val ordering: CategoryOrderingPreferences,
    private val investments: InvestmentPreferences,
    private val auditClock: AuditClock,
    private val financialClock: FinancialClock,
    private val dispatchers: DispatcherProvider,
    private val codec: BackupCodec = BackupCodec()
) : BackupExports {
    /** Named in the financial zone; if that cannot be read, a plain name will do. */
    override suspend fun suggestedName(): String = when (val zone = snapshots.zone()) {
        is Outcome.Success -> BackupFormat.fileName(auditClock.now(), zone.value)
        is Outcome.Failure -> FALLBACK_NAME
    }

    override suspend fun prepare(): Outcome<PreparedBackup, BackupPreparationFailure> =
        when (val file = create()) {
            is Outcome.Success -> Outcome.Success(
                PreparedBackup(
                    bytes = file.value.bytes,
                    summary = BackupSummary(
                        categories = file.value.counts.categories,
                        expenses = file.value.counts.expenses,
                        incomes = file.value.counts.incomes,
                        goals = file.value.counts.goals,
                        schedules = file.value.counts.schedules,
                        investments = file.value.counts.investments,
                        carryChains = file.value.counts.carryChains
                    )
                )
            )

            is Outcome.Failure -> Outcome.Failure(
                when (val failure = file.error) {
                    is BackupExportFailure.Storage -> BackupPreparationFailure.StorageUnavailable
                    is BackupExportFailure.Unrestorable -> preparationFailure(failure.rejection)
                }
            )
        }

    suspend fun create(): Outcome<BackupFile, BackupExportFailure> {
        val financial = when (val read = snapshots.read()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return Outcome.Failure(BackupExportFailure.Storage(read.error))
        }
        val preferences = portablePreferences()
        // The format keeps milliseconds; newer Android clocks give finer time.
        val contents = BackupContents(
            exportedAt = auditClock.now().truncatedTo(ChronoUnit.MILLIS),
            // The financial clock's month, not the audit clock's: a moved or sandbox clock
            // differs from the real one, and chains are judged against the former.
            financialMonth = YearMonth.from(financialClock.today.value.date),
            financial = financial,
            preferences = preferences
        )
        return withContext(dispatchers.default) {
            when (val encoded = codec.encode(contents)) {
                is Outcome.Success -> Outcome.Success(
                    BackupFile(
                        name = BackupFormat.fileName(contents.exportedAt, financial.zone),
                        bytes = encoded.value,
                        counts = financial.counts,
                        preferencesIncluded = preferences != null
                    )
                )

                is Outcome.Failure ->
                    Outcome.Failure(BackupExportFailure.Unrestorable(encoded.error))
            }
        }
    }

    private suspend fun portablePreferences(): PortablePreferences? {
        val appearance = appearance.observeAppearance().first()
        val ordering = ordering.observeCategoryOrdering().first()
        val investments = investments.observeInvestments().first()
        if (appearance !is PreferenceRead.Ready || ordering !is PreferenceRead.Ready ||
            investments !is PreferenceRead.Ready
        ) {
            return null
        }
        return PortablePreferences(appearance.value, ordering.value, investments.value)
    }

    private companion object {
        const val FALLBACK_NAME = "sello-respaldo.json"
    }
}

/** Why the screen is told a backup could not be made, from why its own check refused it. */
internal fun preparationFailure(rejection: BackupRejection): BackupPreparationFailure =
    when (rejection) {
        is BackupRejection.TooLarge,
        is BackupRejection.TooManyRecords,
        is BackupRejection.HistoryTooLong -> BackupPreparationFailure.TooLarge

        else -> BackupPreparationFailure.Unrestorable
    }
