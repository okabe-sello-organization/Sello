package com.software.sello.data.backup

import androidx.room.withTransaction
import com.software.sello.data.backup.legacy.LegacyTranslation
import com.software.sello.data.backup.legacy.LegacyTranslator
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
import com.software.sello.domain.model.InvestmentHistory
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
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.LegacyImportLine
import com.software.sello.domain.port.LegacyImportReport
import com.software.sello.domain.port.LegacyReason
import com.software.sello.domain.port.LegacyRecord
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedLookup
import com.software.sello.domain.port.StagedRestore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Properties
import kotlinx.coroutines.withContext

/**
 * Restores a backup over the whole history, through a private staging folder that
 * belongs to one storage profile (the sandbox has its own).
 *
 * - `staged.json` and `staged.properties`: a checked file and the operation it will
 *   be replaced under, its history position and when it was staged, and for a
 *   FinanceTracker backup what its translation changed and left out (ADR 0019): the
 *   file kept is then the translated Sello backup. `attempted` is added just before a
 *   replacement starts, so a later look can tell a confirmed file from one only
 *   previewed. Nothing financial has changed while only these exist.
 * - `restored.json`: the history was replaced from it; its preferences are not all
 *   applied yet. `applied` lists the groups that are. The folder empties once all are.
 * - `<file>.tmp`: a write in progress, renamed over its file once synced. A death before
 *   the rename leaves it; it goes with its file, or with what a look finds left over.
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
    private val investments: InvestmentPreferences,
    private val notice: CatchUpNoticePreferences,
    private val ids: RecordIdSource,
    private val audit: AuditClock,
    private val dispatchers: DispatcherProvider,
    private val codec: BackupCodec = BackupCodec(),
    private val legacy: LegacyTranslator = LegacyTranslator(ids)
) : Restores {
    private val staged = File(folder, "staged.json")
    private val manifest = File(folder, "staged.properties")
    private val restored = File(folder, "restored.json")
    private val applied = File(folder, "applied")

    override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
        val plan = withContext(dispatchers.default) { codec.read(bytes) }
        val contents = when (plan) {
            is Outcome.Success -> plan.value.contents

            is Outcome.Failure -> return if (plan.error == BackupRejection.NotSelloBackup) {
                stageLegacy(bytes)
            } else {
                Outcome.Failure(StageFailure.Rejected(plan.error.reason()))
            }
        }
        val position = when (val read = stagingPosition()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        return keep(bytes, contents, position, report = null)
    }

    /**
     * A file that is not a Sello backup may be a FinanceTracker one. It is translated
     * in the current financial zone, and the translation, written out as a Sello backup
     * and read back by the same reader as any other, is what is kept and restored.
     */
    private suspend fun stageLegacy(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
        val position = when (val read = stagingPosition()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        val translation = withContext(dispatchers.default) {
            legacy.translate(bytes, position.zone)
        }
        val translated = when (translation) {
            LegacyTranslation.NotLegacy -> return rejected(RestoreRejection.NotABackup)
            is LegacyTranslation.Rejected -> return rejected(translation.reason)
            is LegacyTranslation.Translated -> translation
        }
        // Encoding reads the file back, so only what the released reader accepts is kept.
        val file = withContext(dispatchers.default) { codec.encode(translated.contents) }
        val written = when (file) {
            is Outcome.Success -> file.value
            is Outcome.Failure -> return rejected(RestoreRejection.InvalidContent)
        }
        return keep(written, translated.contents, position, translated.report)
    }

    private fun rejected(reason: RestoreRejection) = Outcome.Failure(StageFailure.Rejected(reason))

    private suspend fun stagingPosition(): Outcome<Position, StageFailure> {
        // A file that may have replaced the history is looked at before another takes its
        // place (ARCHITECTURE §5). Read before `restored.json`: a look renaming it between
        // the two is then still seen.
        val earlier = try {
            readManifest()
        } catch (_: IOException) {
            return Outcome.Failure(StageFailure.Storage)
        }
        if (earlier != null && committed(earlier.operation) != false) {
            return Outcome.Failure(StageFailure.Unsettled)
        }
        // A replacement whose preferences are still pending is finished first.
        if (io { restored.exists() }) return Outcome.Failure(StageFailure.Storage)
        return when (val read = position()) {
            is Outcome.Success -> read
            is Outcome.Failure -> Outcome.Failure(StageFailure.Storage)
        }
    }

    private suspend fun keep(
        bytes: ByteArray,
        contents: BackupContents,
        position: Position,
        report: LegacyImportReport?
    ): Outcome<StagedRestore, StageFailure> {
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
                report?.let { write(it) }
            }
            // The old manifest goes first: a stage cut short then finds nothing, never this
            // file under another file's operation or confirmation. An `applied` list here
            // is an earlier replacement's whose clean-up was cut short; it is not this one's.
            (!manifest.exists() || manifest.delete()) &&
                clear(applied) &&
                writeAtomically(staged, bytes) &&
                writeAtomically(manifest, properties.text())
        }
        if (!written) return Outcome.Failure(StageFailure.Storage)
        return Outcome.Success(position.preview(operation, contents, report))
    }

    override suspend fun staged(): StagedLookup {
        val manifest = readManifest() ?: return StagedLookup.None.also { clearLeftovers() }
        when (committed(manifest.operation)) {
            // A crash came between the commit and the rename that follows it. Replaced
            // only once [pending] can read it; otherwise it stays to look at again.
            true -> {
                val moved = io { moveToRestored() && restored.isFile }
                return if (moved) {
                    StagedLookup.Replaced(manifest.operation)
                } else {
                    StagedLookup.Unknown(manifest.operation)
                }
            }

            // Neither offer it again nor treat it as committed; keep it to look again.
            null -> return StagedLookup.Unknown(manifest.operation)

            false -> Unit
        }
        val position = (position() as? Outcome.Success)?.value
            ?: return manifest.notReplaced()
        val expired = audit.now().isAfter(manifest.stagedAt.plus(STAGING_LIFETIME))
        if (position.generation != manifest.generation || expired) {
            discard()
            return manifest.notReplaced()
        }
        val bytes = io { staged.takeIf { it.exists() }?.readBytes() }
            ?: return manifest.notReplaced()
        val plan = codec.read(bytes) as? Outcome.Success
            ?: return manifest.notReplaced().also { discard() }
        return StagedLookup.Waiting(
            position.preview(manifest.operation, plan.value.contents, manifest.report),
            attempted = manifest.attempted
        )
    }

    /**
     * The staged file becomes the restored one. Until then an `applied` list can only be
     * an earlier replacement's, left by a clean-up cut short, even one an earlier version
     * staged beside; it is not this one's.
     */
    private fun moveToRestored(): Boolean {
        if (!restored.exists()) clear(applied)
        return staged.renameTo(restored)
    }

    override suspend fun discard() {
        io {
            if (!restored.exists()) {
                clear(staged)
                clear(manifest)
            }
        }
    }

    /**
     * With no replacement pending and no usable staging, what is in the folder is what a
     * stage, discard or clean-up cut short left: a file without its manifest or the
     * reverse, an `applied` list, temporary copies. None says what became of a replacement.
     * A manifest beside its file that cannot be read whole is not a leftover: it is kept
     * unless the operation it names is known not to have committed.
     */
    private suspend fun clearLeftovers() {
        if (io { manifest.exists() && staged.exists() }) {
            val operation = io { manifestOperation() } ?: return
            if (committed(operation) != false) return
        }
        io {
            if (!restored.exists()) {
                clear(staged)
                clear(manifest)
                clear(applied)
            }
        }
    }

    override suspend fun replace(staged: StagedRestore): RestoreOutcome {
        val bytes = io { this.staged.takeIf { it.exists() }?.readBytes() }
            ?: return pending() ?: RestoreOutcome.Failed
        val contents = (codec.read(bytes) as? Outcome.Success)?.value?.contents
            ?: return RestoreOutcome.Failed
        val digest = digest(staged, bytes)
        markAttempted(staged.operationId, attempted = true)
        val run = database.runCommand { write(staged, contents, digest) }
        return when (run) {
            is CommandRun.Finished -> when (run.value) {
                Replacement.Done, Replacement.AlreadyDone -> {
                    io { moveToRestored() }
                    applyPending() ?: RestoreOutcome.Restored(
                        contents.financial.summary(),
                        PendingPreferences(null, null)
                    )
                }

                // Previewed again with the new counts, as a file not confirmed yet.
                Replacement.Moved -> {
                    markAttempted(staged.operationId, attempted = false)
                    RestoreOutcome.Stale
                }

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
                ordering = preferences?.ordering?.takeUnless { ORDERING in done },
                investments = preferences?.investments?.takeUnless { INVESTMENTS in done }
            )
        )
    }

    override suspend fun applyPending(): RestoreOutcome.Restored? {
        val pending = pending() ?: return null
        var appearanceLeft = pending.preferences.appearance
        var orderingLeft = pending.preferences.ordering
        var investmentsLeft = pending.preferences.investments
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
        investmentsLeft?.let { wanted ->
            val saved = investments.setNewInvestmentCurrency(wanted.newCurrency).saved() &&
                investments.setInvestmentOrder(wanted.order).saved()
            if (saved && markApplied(INVESTMENTS)) investmentsLeft = null
        }
        reviewRestoredEntries()
        val left = PendingPreferences(appearanceLeft, orderingLeft, investmentsLeft)
        if (left.done) {
            // The manifest first and `applied` last: a death between them leaves either this
            // replacement with every group applied, finished again quietly, or a list with no
            // replacement, which nothing reads and a later look or stage clears.
            io {
                clear(manifest)
                if (restored.delete() || !restored.exists()) clear(applied)
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
     * row and every kept undo copy goes, the backup's rows come in as they were, carry
     * chains included, and the profile takes the backup's zone and a new generation.
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
            carryChainDao().deleteAll()
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
            for (history in financial.investments) insertInvestment(history)
            for (rate in financial.usdRates) investmentDao().insertRate(rate.toEntity())
            // Months only: carry is worked out again from what was just inserted (CP26).
            carryChainDao().insertAll(financial.carryChains.map { it.toEntity() })
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

    /**
     * An investment as the file has it, every movement at its recording position. The next
     * position follows the last one, and is at least 2, as for an investment created with
     * its first aporte and value.
     */
    private suspend fun SelloDatabase.insertInvestment(history: InvestmentHistory) {
        val investment = history.investment
        val next = maxOf(history.movements.maxOf { it.sequence } + 1, FIRST_FREE_POSITION)
        investmentDao().insert(investment.toEntity(nextSequence = next))
        for (movement in history.movements) {
            investmentDao().insertMovement(movement.toEntity(investment.id))
        }
    }

    /** Where the history stands and what it holds, read together. */
    private class Position(
        val generation: Long,
        val revision: Long,
        val zone: ZoneId,
        val current: BackupSummary
    ) {
        fun preview(operation: OperationId, contents: BackupContents, report: LegacyImportReport?) =
            StagedRestore(
                operationId = operation,
                backupAt = contents.exportedAt,
                current = current,
                replacement = contents.financial.summary(),
                generation = generation,
                revision = revision,
                legacy = report
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
                        profile.value.zone,
                        BackupSummary(
                            categories = database.categoryDao().count(),
                            expenses = database.expenseDao().count(),
                            incomes = database.incomeDao().count(),
                            goals = database.goalDao().count(),
                            schedules = database.scheduleDao().count(),
                            investments = database.investmentDao().count(),
                            carryChains = database.carryChainDao().count()
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

    private class Manifest(
        val operation: OperationId,
        val generation: Long,
        val stagedAt: Instant,
        val report: LegacyImportReport?,
        val attempted: Boolean
    ) {
        fun notReplaced() = StagedLookup.NotReplaced(operation, attempted)
    }

    /**
     * Records in the manifest whether [operation]'s replacement was started: just before
     * it starts, and taken back when it was refused because the history moved. If that
     * cannot be written the replacement still goes ahead: a later look then shows the
     * file without saying it was confirmed, never anything untrue.
     */
    private suspend fun markAttempted(operation: OperationId, attempted: Boolean) {
        io {
            try {
                if (!manifest.exists()) return@io
                val properties = Properties().apply { manifest.inputStream().use(::load) }
                if (properties.getProperty(OPERATION) != operation.value) return@io
                if ((properties.getProperty(ATTEMPTED) == true.toString()) == attempted) {
                    return@io
                }
                if (attempted) {
                    properties.setProperty(ATTEMPTED, true.toString())
                } else {
                    properties.remove(ATTEMPTED)
                }
                writeAtomically(manifest, properties.text())
            } catch (_: IOException) {
                // Left unmarked; see above.
            }
        }
    }

    /** The operation the manifest names, if it names a well-formed one. */
    private fun manifestOperation(): OperationId? {
        val properties = Properties().apply { manifest.inputStream().use(::load) }
        return (OperationId.of(properties.getProperty(OPERATION).orEmpty()) as? Outcome.Success)
            ?.value
    }

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
            ),
            if (properties.getProperty(LEGACY_LINES) == null) {
                null
            } else {
                // A report that cannot be read makes the staging unusable, not complete.
                properties.report() ?: return@io null
            },
            // A manifest written before this key existed was never attempted from it.
            attempted = properties.getProperty(ATTEMPTED) == true.toString()
        )
    }

    /** `legacy.lines`, then `legacy.<n>` as `Record,Reason,count` for each line. */
    private fun Properties.write(report: LegacyImportReport) {
        setProperty(LEGACY_LINES, report.lines.size.toString())
        report.lines.forEachIndexed { index, line ->
            setProperty(
                "$LEGACY_LINE$index",
                "${line.record.name},${line.reason.name},${line.count}"
            )
        }
    }

    private fun Properties.report(): LegacyImportReport? {
        val size = getProperty(LEGACY_LINES)?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val lines = (0 until size).map { index ->
            val parts = getProperty("$LEGACY_LINE$index")?.split(',') ?: return null
            if (parts.size != 3) return null
            LegacyImportLine(
                LegacyRecord.entries.firstOrNull { it.name == parts[0] } ?: return null,
                LegacyReason.entries.firstOrNull { it.name == parts[1] } ?: return null,
                parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
            )
        }
        return LegacyImportReport(lines)
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

    /** A write that fails takes its temporary copy with it, which may hold part of a backup. */
    private fun writeAtomically(target: File, bytes: ByteArray): Boolean {
        val temporary = target.temporary()
        val written = try {
            FileOutputStream(temporary).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            temporary.renameTo(target)
        } catch (_: IOException) {
            false
        }
        if (!written && temporary.isFile) temporary.delete()
        return written
    }

    private fun File.temporary() = File(parentFile, "$name.tmp")

    /** Deletes [file] and its temporary copy; true when neither is left. */
    private fun clear(file: File): Boolean {
        val temporary = file.temporary()
        temporary.delete()
        file.delete()
        return !temporary.exists() && !file.exists()
    }

    private fun Properties.text(): ByteArray =
        java.io.ByteArrayOutputStream().also { store(it, null) }.toByteArray()

    private companion object {
        const val OPERATION = "operation"
        const val GENERATION = "generation"
        const val STAGED_AT = "stagedAt"
        const val ATTEMPTED = "attempted"
        const val LEGACY_LINES = "legacy.lines"
        const val LEGACY_LINE = "legacy."
        const val APPEARANCE = "appearance"
        const val ORDERING = "ordering"
        const val INVESTMENTS = "investments"
        const val NOTICE = "notice"

        /** An investment starts with its first aporte and value at positions 0 and 1. */
        const val FIRST_FREE_POSITION = 2L

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

internal fun FinancialSnapshot.summary() = BackupSummary(
    categories = categories.size,
    expenses = expenses.size,
    incomes = incomes.size,
    goals = goals.size,
    schedules = schedules.size,
    investments = investments.size,
    carryChains = carryChains.size
)

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
    is BackupRejection.DanglingReference, is BackupRejection.HistoryTooLong,
    is BackupRejection.InvalidChain -> RestoreRejection.InvalidContent
}
