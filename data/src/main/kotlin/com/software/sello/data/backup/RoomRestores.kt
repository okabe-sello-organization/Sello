package com.software.sello.data.backup

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.FeaturedGoalEntity
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.data.local.entity.ProfileEntity
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.data.repository.CommandRun
import com.software.sello.data.repository.nextCounter
import com.software.sello.data.repository.reading
import com.software.sello.data.repository.runCommand
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedRestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Properties
import kotlinx.coroutines.withContext

/**
 * Restores a backup over the whole history, through a private staging folder that
 * belongs to one storage profile (the sandbox has its own).
 *
 * - `staged.json` and `staged.properties`: a checked file and the operation it will
 *   be replaced under, its history position and when it was staged. Nothing financial
 *   has changed while only these exist.
 * - `restored.json`: the history was replaced from it; its preferences are not all
 *   applied yet. `applied` lists the groups that are. The folder empties once all are.
 *
 * Files are only ever read or written outside a transaction. A restore whose receipt
 * exists is committed whatever the files say, so a crash between the commit and the
 * rename that follows it is found and finished on the next look.
 */
class RoomRestores(
    private val database: SelloDatabase,
    private val folder: File,
    private val appearance: AppearancePreferences,
    private val ordering: CategoryOrderingPreferences,
    private val notice: CatchUpNoticePreferences,
    private val ids: RecordIdSource,
    private val audit: AuditClock,
    private val dispatchers: DispatcherProvider,
    private val codec: BackupCodec = BackupCodec()
) : Restores {
    private val staged = File(folder, "staged.json")
    private val manifest = File(folder, "staged.properties")
    private val restored = File(folder, "restored.json")
    private val applied = File(folder, "applied")

    override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
        val plan = withContext(dispatchers.default) { codec.read(bytes) }
        val contents = when (plan) {
            is Outcome.Success -> plan.value.contents
            is Outcome.Failure -> return Outcome.Failure(StageFailure.Rejected(plan.error.reason()))
        }
        // A replacement whose preferences are still pending is finished first.
        if (io { restored.exists() }) return Outcome.Failure(StageFailure.Storage)
        val position = when (val read = position()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return Outcome.Failure(StageFailure.Storage)
        }
        val operation = when (val id = OperationId.of(ids.next())) {
            is Outcome.Success -> id.value
            is Outcome.Failure -> error("The identifier source returned a malformed identifier")
        }
        val written = io {
            folder.mkdirs()
            val properties = Properties().apply {
                setProperty(OPERATION, operation.value)
                setProperty(GENERATION, position.generation.toString())
                setProperty(STAGED_AT, audit.now().toEpochMilli().toString())
            }
            writeAtomically(staged, bytes) && writeAtomically(manifest, properties.text())
        }
        if (!written) return Outcome.Failure(StageFailure.Storage)
        return Outcome.Success(position.preview(operation, contents))
    }

    override suspend fun staged(): StagedRestore? {
        val manifest = readManifest() ?: return null
        when (committed(manifest.operation)) {
            // A crash came between the commit and the rename that follows it.
            true -> {
                io { staged.renameTo(restored) }
                return null
            }

            // Unknown: neither offer it again nor treat it as committed.
            null -> return null

            false -> Unit
        }
        val position = (position() as? Outcome.Success)?.value ?: return null
        val expired = audit.now().isAfter(manifest.stagedAt.plus(STAGING_LIFETIME))
        if (position.generation != manifest.generation || expired) {
            discard()
            return null
        }
        val bytes = io { staged.takeIf { it.exists() }?.readBytes() } ?: return null
        val plan = codec.read(bytes) as? Outcome.Success ?: return null.also { discard() }
        return position.preview(manifest.operation, plan.value.contents)
    }

    override suspend fun discard() {
        io {
            if (!restored.exists()) {
                staged.delete()
                manifest.delete()
            }
        }
    }

    override suspend fun replace(staged: StagedRestore): RestoreOutcome {
        val bytes = io { this.staged.takeIf { it.exists() }?.readBytes() }
            ?: return pending() ?: RestoreOutcome.Failed
        val contents = (codec.read(bytes) as? Outcome.Success)?.value?.contents
            ?: return RestoreOutcome.Failed
        val digest = digest(staged, bytes)
        val run = database.runCommand { write(staged, contents, digest) }
        return when (run) {
            is CommandRun.Finished -> when (run.value) {
                Replacement.Done, Replacement.AlreadyDone -> {
                    io { this.staged.renameTo(restored) }
                    applyPending() ?: RestoreOutcome.Restored(
                        contents.financial.summary(),
                        PendingPreferences(null, null)
                    )
                }

                Replacement.Moved -> RestoreOutcome.Stale

                Replacement.Conflict -> RestoreOutcome.Failed
            }

            is CommandRun.RolledBack -> RestoreOutcome.Failed

            CommandRun.Unknown -> RestoreOutcome.OutcomeUnknown(staged.operationId)
        }
    }

    override suspend fun pending(): RestoreOutcome.Restored? {
        val contents = restoredContents() ?: return null
        val done = io { applied.takeIf { it.exists() }?.readLines()?.toSet() } ?: emptySet()
        val preferences = contents.preferences
        return RestoreOutcome.Restored(
            contents.financial.summary(),
            PendingPreferences(
                appearance = preferences?.appearance?.takeUnless { APPEARANCE in done },
                ordering = preferences?.ordering?.takeUnless { ORDERING in done }
            )
        )
    }

    override suspend fun applyPending(): RestoreOutcome.Restored? {
        val pending = pending() ?: return null
        var appearanceLeft = pending.preferences.appearance
        var orderingLeft = pending.preferences.ordering
        appearanceLeft?.let { wanted ->
            val saved = appearance.setTheme(wanted.theme).saved() &&
                appearance.setInk(wanted.ink).saved()
            if (saved && markApplied(APPEARANCE)) appearanceLeft = null
        }
        orderingLeft?.let { wanted ->
            val saved = ordering.setCategoryOrder(wanted.order).saved() &&
                ordering.setCategoryOrderDirection(wanted.direction).saved()
            if (saved && markApplied(ORDERING)) orderingLeft = null
        }
        reviewRestoredEntries()
        val left = PendingPreferences(appearanceLeft, orderingLeft)
        if (left.done) {
            io {
                restored.delete()
                applied.delete()
                manifest.delete()
            }
        }
        return RestoreOutcome.Restored(pending.summary, left)
    }

    /**
     * What a backup brings counts as reviewed: the catch-up notice lists only what Sello
     * records from the replacement on (owner, 2026-10-25), so a restore on a new phone
     * does not list every automatic entry the backup holds. Done once: a retry later
     * never moves it back past a Listo. Not kept pending if it cannot be saved: the worst
     * case is a notice listing what the backup brought.
     */
    private suspend fun reviewRestoredEntries() {
        val done = io { applied.takeIf { it.exists() }?.readLines()?.toSet() } ?: emptySet()
        if (NOTICE in done) return
        val restoredAt = (
            reading {
                Outcome.Success(
                    database.operationReceiptDao().lastCommittedAt(OperationKind.RestoreHistory.key)
                )
            } as? Outcome.Success
            )?.value ?: return
        if (notice.markCatchUpNoticeSeen(Instant.ofEpochMilli(restoredAt)).saved()) {
            markApplied(NOTICE)
        }
    }

    private sealed interface Replacement {
        data object Done : Replacement
        data object AlreadyDone : Replacement
        data object Moved : Replacement
        data object Conflict : Replacement
    }

    /**
     * Inside one transaction: a receipt for this operation means it already happened;
     * otherwise the history must still be where it was previewed. Then every financial
     * row and every kept undo copy goes, the backup's rows come in as they were, and
     * the profile takes the backup's zone and a new generation.
     */
    private suspend fun write(
        staged: StagedRestore,
        contents: BackupContents,
        digest: String
    ): Replacement {
        database.operationReceiptDao().find(staged.operationId.value)?.let { existing ->
            val original = (existing.toStored() as? Outcome.Success)?.value
                ?: return Replacement.Conflict
            val same = original.receipt.kind == OperationKind.RestoreHistory &&
                original.inputDigest == digest
            return if (same) Replacement.AlreadyDone else Replacement.Conflict
        }
        val profile = (profileFrom(database.profileDao().rows()) as? Outcome.Success)?.value
            ?: return Replacement.Moved
        if (profile.generation != staged.generation || profile.revision != staged.revision) {
            return Replacement.Moved
        }
        val financial = contents.financial
        with(database) {
            deletedExpenseDao().clear()
            deletedIncomeDao().clear()
            deletedGoalEventDao().clear()
            deletedInvestmentMovementDao().clear()
            investmentDao().deleteAllMovements()
            investmentDao().deleteAll()
            investmentDao().deleteAllRates()
            goalDao().deleteFeatured()
            goalDao().deleteAllEvents()
            goalDao().deleteAll()
            scheduleDao().deleteAllPauses()
            scheduleDao().deleteAll()
            expenseDao().deleteAll()
            incomeDao().deleteAll()
            limitDao().deleteAllMonthLimits()
            limitDao().deleteAllDefaults()
            categoryDao().deleteAll()
            categoryDao().insertAll(financial.categories.map { it.toEntity() })
            limitDao().insertDefaults(financial.defaultLimits.map { it.toEntity() })
            limitDao().insertMonthLimits(financial.monthLimits.map { it.toEntity() })
            expenseDao().insertAll(financial.expenses.map { it.toEntity() })
            incomeDao().insertAll(financial.incomes.map { it.toEntity() })
            for (history in financial.goals) insertGoal(history)
            // After the categories they record into; each keeps its checkpoint, so catch-up
            // goes on after the occurrences the backup already holds.
            for (stored in financial.schedules) {
                scheduleDao().insert(stored.toEntity())
                for (pause in stored.schedule.recurrence.pauses) {
                    scheduleDao().insertPause(pause.toEntity(stored.schedule.id))
                }
            }
            financial.featuredGoal?.let {
                goalDao().feature(FeaturedGoalEntity(FeaturedGoalEntity.SINGLE_ID, it.value))
            }
        }
        val generation = nextCounter(profile.generation, ProfileEntity.TABLE, null, "generation")
        val revision = nextCounter(profile.revision, ProfileEntity.TABLE, null, "revision")
        val moved = database.profileDao().replaceHistory(
            zone = financial.zone.id,
            generation = generation,
            revision = revision,
            expectedGeneration = profile.generation,
            expectedRevision = profile.revision
        )
        check(moved == 1) { "The profile changed inside the transaction that read it" }
        database.operationReceiptDao().insert(
            OperationReceiptEntity(
                operationId = staged.operationId.value,
                kind = OperationKind.RestoreHistory.key,
                inputDigest = digest,
                generation = generation,
                revision = revision,
                subjectId = null,
                committedAt = audit.now().toEpochMilli()
            )
        )
        return Replacement.Done
    }

    /**
     * A goal as the file has it: the next recording position follows its last event,
     * and each target, which a file does not name, gets a new identifier.
     */
    private suspend fun SelloDatabase.insertGoal(history: GoalHistory) {
        val goal = history.goal
        goalDao().insert(goal.toEntity(nextSequence = history.events.maxOf { it.sequence } + 1))
        goalDao().insertAllEvents(
            history.events.map { event ->
                when (event) {
                    is GoalTargetChange -> event.toEntity(ids.next(), goal.id, goal.createdAt)
                    is GoalMovement -> event.toEntity(goal.id)
                }
            }
        )
    }

    /** Where the history stands and what it holds, read together. */
    private class Position(val generation: Long, val revision: Long, val current: BackupSummary) {
        fun preview(operation: OperationId, contents: BackupContents) = StagedRestore(
            operationId = operation,
            backupAt = contents.exportedAt,
            current = current,
            replacement = contents.financial.summary(),
            generation = generation,
            revision = revision
        )
    }

    private suspend fun position(): Outcome<Position, StorageFailure> = reading {
        database.withTransaction {
            when (val profile = profileFrom(database.profileDao().rows())) {
                is Outcome.Failure -> profile

                is Outcome.Success -> Outcome.Success(
                    Position(
                        profile.value.generation,
                        profile.value.revision,
                        BackupSummary(
                            categories = database.categoryDao().count(),
                            expenses = database.expenseDao().count(),
                            incomes = database.incomeDao().count(),
                            goals = database.goalDao().count(),
                            schedules = database.scheduleDao().count()
                        )
                    )
                )
            }
        }
    }

    /** Whether [operation] committed: true, false, or null when that cannot be read. */
    private suspend fun committed(operation: OperationId): Boolean? = when (
        val read = reading {
            Outcome.Success(database.operationReceiptDao().find(operation.value) != null)
        }
    ) {
        is Outcome.Success -> read.value
        is Outcome.Failure -> null
    }

    private class Manifest(val operation: OperationId, val generation: Long, val stagedAt: Instant)

    private suspend fun readManifest(): Manifest? = io {
        if (!manifest.exists() || !staged.exists()) return@io null
        val properties = Properties().apply { manifest.inputStream().use(::load) }
        val operation = when (
            val id = OperationId.of(
                properties.getProperty(OPERATION).orEmpty()
            )
        ) {
            is Outcome.Success -> id.value
            is Outcome.Failure -> return@io null
        }
        Manifest(
            operation,
            properties.getProperty(GENERATION)?.toLongOrNull() ?: return@io null,
            Instant.ofEpochMilli(
                properties.getProperty(STAGED_AT)?.toLongOrNull() ?: return@io null
            )
        )
    }

    private suspend fun restoredContents(): BackupContents? {
        val bytes = io { restored.takeIf { it.exists() }?.readBytes() } ?: return null
        return (codec.read(bytes) as? Outcome.Success)?.value?.contents
    }

    private suspend fun markApplied(group: String): Boolean = io {
        val done = applied.takeIf { it.exists() }?.readLines().orEmpty() + group
        writeAtomically(applied, done.distinct().joinToString("\n").toByteArray())
    }

    private fun PreferenceWrite.saved() = this == PreferenceWrite.Saved

    /** File work off the main thread; any I/O error is null or false, never a crash. */
    private suspend fun <T> io(block: () -> T): T = withContext(dispatchers.io) { block() }

    private fun writeAtomically(target: File, bytes: ByteArray): Boolean = try {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        temporary.renameTo(target)
    } catch (_: IOException) {
        false
    }

    private fun Properties.text(): ByteArray =
        java.io.ByteArrayOutputStream().also { store(it, null) }.toByteArray()

    private companion object {
        const val OPERATION = "operation"
        const val GENERATION = "generation"
        const val STAGED_AT = "stagedAt"
        const val APPEARANCE = "appearance"
        const val ORDERING = "ordering"
        const val NOTICE = "notice"

        /** An unconfirmed file is forgotten after a day; a new one can always be chosen. */
        val STAGING_LIFETIME: Duration = Duration.ofDays(1)

        /** What the replacement is: the history it was previewed at and the exact file. */
        fun digest(staged: StagedRestore, bytes: ByteArray): String {
            val sha256 = MessageDigest.getInstance("SHA-256")
            listOf(
                OperationKind.RestoreHistory.key,
                "1",
                staged.generation.toString(),
                staged.revision.toString(),
                sha256Hex(bytes)
            ).forEach { field ->
                val encoded = field.toByteArray(Charsets.UTF_8)
                sha256.update("${encoded.size}:".toByteArray(Charsets.UTF_8))
                sha256.update(encoded)
            }
            return sha256.digest().joinToString("") { "%02x".format(it) }
        }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
    }
}

/** Checks [bytes] as a whole backup without any storage; see [CheckedBackup]. */
fun BackupCodec.check(bytes: ByteArray): Outcome<CheckedBackup, RestoreRejection> =
    when (val plan = read(bytes)) {
        is Outcome.Success -> with(plan.value.contents) {
            Outcome.Success(CheckedBackup(exportedAt, financial.zone, financial.summary()))
        }

        is Outcome.Failure -> Outcome.Failure(plan.error.reason())
    }

internal fun FinancialSnapshot.summary() =
    BackupSummary(categories.size, expenses.size, incomes.size, goals.size, schedules.size)

/** How a person is told why a file cannot be restored; the detail stays in the rejection. */
internal fun BackupRejection.reason(): RestoreRejection = when (this) {
    is BackupRejection.TooLarge, is BackupRejection.TooManyRecords -> RestoreRejection.TooLarge

    BackupRejection.NotSelloBackup -> RestoreRejection.NotABackup

    is BackupRejection.UnsupportedVersion ->
        if (version >
            BackupFormat.VERSION
        ) {
            RestoreRejection.NewerVersion
        } else {
            RestoreRejection.NotABackup
        }

    BackupRejection.Truncated, is BackupRejection.Malformed, is BackupRejection.TooDeep,
    is BackupRejection.FieldTooLong, is BackupRejection.DuplicateKey -> RestoreRejection.Damaged

    is BackupRejection.InvalidValue, is BackupRejection.UnsupportedCurrency,
    is BackupRejection.AmountOutOfRange, is BackupRejection.DuplicateId,
    is BackupRejection.DuplicateCategoryName, is BackupRejection.DuplicateEntry,
    is BackupRejection.DanglingReference, is BackupRejection.HistoryTooLong ->
        RestoreRejection.InvalidContent
}
