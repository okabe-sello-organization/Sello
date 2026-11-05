package com.software.sello.data.backup

import androidx.room.withTransaction
import com.software.sello.data.backup.legacy.LegacyTranslation
import com.software.sello.data.backup.legacy.LegacyTranslator
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.data.local.entity.ProfileEntity
import com.software.sello.data.local.entity.StarredGoalEntity
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
import com.software.sello.domain.port.CheckedBackupFile
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Files are only ever read or written outside a transaction, off the caller's thread; an
 * I/O error is a failure of the step that met it, never a crash. A restore whose receipt
 * exists is committed whatever the files say, so a crash or a failed rename between the
 * commit and the move that follows it is found and finished on the next look, and until
 * then every preference it brings is reported pending.
 *
 * A file is parsed once: the contents a check, stage or look read are kept in memory for
 * the replacement under that operation, which reads the file again only in a later
 * process. Staging, committing a replacement, applying its preferences and the expiry
 * at start take turns with finishing an erase of the same storage through [recovery],
 * always the last lock taken; [beforeReplacing] runs in
 * that turn just before a replacement commits, and the replacement fails if it says no.
 *
 * `restored.json` belongs to the replacement that made the history as it is now, found by
 * its receipt. With none, an erase removed it since, and the file and what it would apply
 * are forgotten. A file that cannot be read (an I/O error, or receipts that cannot be read)
 * applies nothing and a look says so, until it reads. A file that reads but is damaged is
 * cleared, with its manifest and `applied` list, and the next answer says once that its
 * preferences could not be applied.
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
    private val recovery: Mutex,
    private val beforeReplacing: suspend () -> Boolean,
    private val codec: BackupCodec = BackupCodec(),
    private val legacy: LegacyTranslator = LegacyTranslator(ids)
) : Restores {
    private val staged = File(folder, "staged.json")
    private val manifest = File(folder, "staged.properties")
    private val restored = File(folder, "restored.json")
    private val applied = File(folder, "applied")

    /** A file read and checked whole, with the SHA-256 of its bytes. */
    internal class Parsed(val contents: BackupContents, val bytes: String)

    /** The staged file's contents as last parsed, for the replacement under [operation]. */
    private class Held(val operation: OperationId, val parsed: Parsed)

    @Volatile
    private var held: Held? = null

    override suspend fun stage(bytes: ByteArray): Outcome<StagedRestore, StageFailure> {
        val plan = withContext(dispatchers.default) { codec.read(bytes) to sha256Hex(bytes) }
        val contents = when (val read = plan.first) {
            is Outcome.Success -> Parsed(read.value.contents, plan.second)

            is Outcome.Failure -> return if (read.error == BackupRejection.NotSelloBackup) {
                stageLegacy(bytes)
            } else {
                Outcome.Failure(StageFailure.Rejected(read.error.reason()))
            }
        }
        return stageParsed(bytes, contents)
    }

    /** A file [checkFile] read is staged from what it read; any other is read here. */
    override suspend fun stage(file: CheckedBackupFile): Outcome<StagedRestore, StageFailure> =
        if (file is ParsedBackupFile) stageParsed(file.bytes, file.parsed) else stage(file.bytes)

    /** Staged in turn with a replacement, its preferences, an erase and the expiry at start. */
    private suspend fun stageParsed(
        bytes: ByteArray,
        parsed: Parsed
    ): Outcome<StagedRestore, StageFailure> = recovery.withLock {
        val position = when (val read = stagingPosition()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return@withLock read
        }
        keep(bytes, parsed, position, report = null)
    }

    /**
     * A file that is not a Sello backup may be a FinanceTracker one. It is translated
     * in the current financial zone, and the translation, written out as a Sello backup
     * and read back by the same reader as any other, is what is kept and restored.
     */
    private suspend fun stageLegacy(bytes: ByteArray): Outcome<StagedRestore, StageFailure> =
        recovery.withLock {
            val position = when (val read = stagingPosition()) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return@withLock read
            }
            val translation = withContext(dispatchers.default) {
                legacy.translate(bytes, position.zone)
            }
            val translated = when (translation) {
                LegacyTranslation.NotLegacy -> return@withLock rejected(RestoreRejection.NotABackup)
                is LegacyTranslation.Rejected -> return@withLock rejected(translation.reason)
                is LegacyTranslation.Translated -> translation
            }
            val file = when (
                val encoded = withContext(dispatchers.default) {
                    codec.translatedFile(translated.contents)
                }
            ) {
                is Outcome.Success -> encoded.value
                is Outcome.Failure -> return@withLock rejected(encoded.error)
            }
            keep(
                file,
                Parsed(translated.contents, withContext(dispatchers.default) { sha256Hex(file) }),
                position,
                translated.report
            )
        }

    private fun rejected(reason: RestoreRejection) = Outcome.Failure(StageFailure.Rejected(reason))

    private suspend fun stagingPosition(): Outcome<Position, StageFailure> {
        // A file that may have replaced the history is looked at before another takes its
        // place (ARCHITECTURE §5). Read before `restored.json`: a look renaming it between
        // the two is then still seen.
        // A manifest beside its file that cannot be read whole still names its operation;
        // one that names none cannot be told apart from a committed one.
        val earlier = if (io(failed = true) { manifest.exists() && staged.exists() }) {
            readManifest()?.operation
                ?: io(failed = null) { manifestOperation() }
                ?: return Outcome.Failure(StageFailure.Storage)
        } else {
            null
        }
        if (earlier != null && committed(earlier) != false) {
            return Outcome.Failure(StageFailure.Unsettled)
        }
        // A replacement whose preferences are still pending is finished first.
        if (io(failed = true) { restored.exists() }) return Outcome.Failure(StageFailure.Storage)
        return when (val read = position()) {
            is Outcome.Success -> read
            is Outcome.Failure -> Outcome.Failure(StageFailure.Storage)
        }
    }

    private suspend fun keep(
        bytes: ByteArray,
        parsed: Parsed,
        position: Position,
        report: LegacyImportReport?
    ): Outcome<StagedRestore, StageFailure> {
        val operation = when (val id = OperationId.of(ids.next())) {
            is Outcome.Success -> id.value
            is Outcome.Failure -> error("The identifier source returned a malformed identifier")
        }
        val written = io(failed = false) {
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
        held = Held(operation, parsed)
        return Outcome.Success(position.preview(operation, parsed.contents, report))
    }

    override suspend fun staged(): StagedLookup {
        val manifest = readManifest() ?: return withoutStaging()
        when (committed(manifest.operation)) {
            // A crash came between the commit and the rename that follows it. Replaced
            // only once [pending] can read it; otherwise it stays to look at again.
            true -> {
                val moved = io(failed = false) { moveToRestored() && restored.isFile }
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
        val bytes = io(failed = null) { staged.takeIf { it.exists() }?.readBytes() }
            ?: return manifest.notReplaced()
        val parsed = parse(bytes) ?: return manifest.notReplaced().also { discard() }
        held = Held(manifest.operation, parsed)
        return StagedLookup.Waiting(
            position.preview(manifest.operation, parsed.contents, manifest.report),
            attempted = manifest.attempted
        )
    }

    override suspend fun expireStaged(): StagedLookup.NotReplaced? = recovery.withLock {
        // A file whose manifest cannot be read is left for a look to settle.
        val found = readManifest() ?: return@withLock null
        if (!audit.now().isAfter(found.stagedAt.plus(STAGING_LIFETIME))) return@withLock null
        // A replacement that committed, or may have, and one with preferences still
        // pending are left for a look, which finishes or reports them.
        if (committed(found.operation) != false) return@withLock null
        if (io(failed = true) { restored.exists() }) return@withLock null
        discard()
        // Not deleted: the next start tries again.
        if (io(failed = true) { staged.exists() || manifest.exists() }) return@withLock null
        found.notReplaced()
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
        held = null
        io(failed = Unit) {
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
     * A manifest beside its file that cannot be read whole is not a leftover: it is kept,
     * and its replacement is unknown, unless the operation it names did not commit.
     */
    private suspend fun withoutStaging(): StagedLookup {
        // A restored file that cannot be read is not nothing.
        (restoredFile() as? RestoredFile.Unreadable)?.let {
            return StagedLookup.Unknown(it.operation)
        }
        if (io(failed = true) { manifest.exists() && staged.exists() }) {
            val operation = when (val named = named()) {
                Named.Unreadable -> return StagedLookup.Unknown(null)

                is Named.Operation -> named.id

                // Damaged for good: the receipts say whether this very file was replaced.
                Named.Damaged -> when (val found = stagedReplacement()) {
                    is Outcome.Failure -> return StagedLookup.Unknown(null)

                    is Outcome.Success -> when (val file = found.value) {
                        StagedFile.NotReplaced -> null

                        is StagedFile.Replaced -> file.operation

                        // Moved as it is: the next answer clears it and says so once.
                        is StagedFile.Damaged -> return if (io(failed = false) {
                                moveToRestored()
                            }
                        ) {
                            StagedLookup.Replaced(file.operation)
                        } else {
                            StagedLookup.Unknown(file.operation)
                        }
                    }
                }
            }
            if (operation != null) {
                when (committed(operation)) {
                    null -> return StagedLookup.Unknown(operation)

                    // Committed, and the manifest will not read better: the file moves now.
                    true -> return if (io(failed = false) { moveToRestored() }) {
                        StagedLookup.Replaced(operation)
                    } else {
                        StagedLookup.Unknown(operation)
                    }

                    false -> Unit
                }
            }
        }
        io(failed = Unit) {
            if (!restored.exists()) {
                clear(staged)
                clear(manifest)
                clear(applied)
            }
        }
        return StagedLookup.None
    }

    override suspend fun replace(staged: StagedRestore): RestoreOutcome = recovery.withLock {
        val kept = held?.takeIf { it.operation == staged.operationId }
        val parsed = if (kept != null && io(failed = false) { this.staged.isFile }) {
            kept.parsed
        } else {
            val bytes = io(failed = null) { this.staged.takeIf { it.exists() }?.readBytes() }
                ?: return@withLock pending() ?: RestoreOutcome.Failed
            parse(bytes) ?: return@withLock RestoreOutcome.Failed
        }
        val contents = parsed.contents
        val digest = digest(staged.generation, staged.revision, parsed.bytes)
        // An erase still finishing puts the preferences back to first-run first, so the
        // backup's own land on a clean slate; until it can, nothing is replaced.
        if (!beforeReplacing()) return@withLock RestoreOutcome.Failed
        markAttempted(staged.operationId, attempted = true)
        val run = database.runCommand { write(staged, contents, digest) }
        when (run) {
            is CommandRun.Finished -> when (run.value) {
                // Applied from what was just read. A file that did not move keeps every
                // preference pending; the next look moves it before applying any.
                Replacement.Done, Replacement.AlreadyDone -> {
                    held = null
                    if (io(failed = false) { moveToRestored() }) {
                        apply(contents)
                    } else {
                        pendingFrom(contents, done = null)
                    }
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
        settleMove()
        return when (val file = restoredFile()) {
            is RestoredFile.Ready -> pendingFrom(file.contents, appliedGroups())
            RestoredFile.Absent, is RestoredFile.Unreadable -> unmoved()
            RestoredFile.Erased -> null
            RestoredFile.Damaged -> unreadablePreferences()
        }
    }

    override suspend fun applyPending(): RestoreOutcome.Restored? = recovery.withLock {
        settleMove()
        when (val file = restoredFile()) {
            is RestoredFile.Ready -> apply(file.contents)
            RestoredFile.Absent, is RestoredFile.Unreadable -> unmoved()
            RestoredFile.Erased -> null
            RestoredFile.Damaged -> unreadablePreferences()
        }
    }

    /** Restored, with nothing left to apply because the backup's preferences are gone. */
    private suspend fun unreadablePreferences() = RestoreOutcome.Restored(
        (position() as? Outcome.Success)?.value?.current ?: BackupSummary(0, 0, 0, 0, 0),
        PendingPreferences(null, null),
        preferencesUnreadable = true
    )

    /** What `restored.json` is now; see the class comment. */
    private sealed interface RestoredFile {
        data object Absent : RestoredFile

        /** It belonged to a history an erase removed since; it is gone now. */
        data object Erased : RestoredFile

        /** It reads but is damaged; it is gone now, and its preferences with it. */
        data object Damaged : RestoredFile

        /** It, or the receipts saying whose it is, cannot be read. */
        data class Unreadable(val operation: OperationId?) : RestoredFile

        class Ready(val contents: BackupContents) : RestoredFile
    }

    private suspend fun restoredFile(): RestoredFile {
        if (!io(failed = true) { restored.exists() }) return RestoredFile.Absent
        val owner = when (val read = currentRestore()) {
            is Outcome.Success -> read.value

            is Outcome.Failure -> return RestoredFile.Unreadable(
                (named() as? Named.Operation)?.id
            )
        }
        // Beside a staged file the manifest is that file's: it stays.
        val alone = io(failed = false) { !staged.exists() }
        if (owner == null) {
            clearRestored(withManifest = alone)
            return RestoredFile.Erased
        }
        val bytes = io(failed = null) { restored.takeIf { it.isFile }?.readBytes() }
            ?: return RestoredFile.Unreadable(owner.operation)
        val contents = parse(bytes)?.contents
        if (contents != null) return RestoredFile.Ready(contents)
        // Damaged whatever sits beside it; a staged file and its manifest stay.
        clearRestored(withManifest = alone)
        return RestoredFile.Damaged
    }

    /** `restored.json` first, so no `applied` list or manifest outlives it. */
    private suspend fun clearRestored(withManifest: Boolean) {
        io(failed = Unit) {
            if (clear(restored)) {
                clear(applied)
                if (withManifest) clear(manifest)
            }
        }
    }

    /** The restore that made the history as it is now, and the revision it is at since. */
    private class CurrentRestore(val receipt: OperationReceiptEntity, val revision: Long) {
        val operation: OperationId?
            get() = (OperationId.of(receipt.operationId) as? Outcome.Success)?.value
    }

    /** Read with the profile, by its generation; null when no restore made this history. */
    private suspend fun currentRestore(): Outcome<CurrentRestore?, StorageFailure> = reading {
        database.withTransaction {
            when (val profile = profileFrom(database.profileDao().rows())) {
                is Outcome.Failure -> profile

                is Outcome.Success -> Outcome.Success(
                    database.operationReceiptDao()
                        .atGeneration(OperationKind.RestoreHistory.key, profile.value.generation)
                        ?.let { CurrentRestore(it, profile.value.revision) }
                )
            }
        }
    }

    /** What became of a staged file beside a manifest that names no operation. */
    private sealed interface StagedFile {
        data object NotReplaced : StagedFile

        data class Replaced(val operation: OperationId) : StagedFile

        /** The restore that made this history may have been its; it no longer reads. */
        data class Damaged(val operation: OperationId) : StagedFile
    }

    /**
     * Whether the staged file, beside a manifest damaged for good, is the one the restore
     * that made this history replaced it with: with nothing written since that restore,
     * its input digest is worked out again from the file at the position just before it
     * (one generation and revision back). A file that no longer reads, beside such a
     * restore, is answered as damaged rather than as never replaced.
     */
    private suspend fun stagedReplacement(): Outcome<StagedFile, StorageFailure> {
        val current = when (val read = currentRestore()) {
            is Outcome.Success -> read.value ?: return Outcome.Success(StagedFile.NotReplaced)
            is Outcome.Failure -> return read
        }
        val operation = current.operation ?: return Outcome.Success(StagedFile.NotReplaced)
        val bytes = io(failed = null) { staged.readBytes() }
            ?: return Outcome.Failure(StorageFailure.Unavailable("IOException"))
        val receipt = current.receipt
        val file = withContext(dispatchers.default) { sha256Hex(bytes) }
        val same = current.revision == receipt.revision &&
            digest(receipt.generation - 1, receipt.revision - 1, file) == receipt.inputDigest
        return Outcome.Success(
            when {
                same -> StagedFile.Replaced(operation)
                parse(bytes) == null -> StagedFile.Damaged(operation)
                else -> StagedFile.NotReplaced
            }
        )
    }

    /** What the manifest names. */
    private sealed interface Named {
        data class Operation(val id: OperationId) : Named

        /** It reads, but names no operation: it will not read better later. */
        data object Damaged : Named

        data object Unreadable : Named
    }

    private suspend fun named(): Named = withContext(dispatchers.io) {
        try {
            manifestOperation()?.let { Named.Operation(it) } ?: Named.Damaged
        } catch (_: IOException) {
            Named.Unreadable
        }
    }

    /**
     * What of [contents] is still to apply. [done] is the groups applied; null when that
     * cannot be read, and then every group is pending: none is applied twice.
     */
    private fun pendingFrom(contents: BackupContents, done: Set<String>?): RestoreOutcome.Restored {
        val preferences = contents.preferences
        fun applied(group: String) = done != null && group in done
        return RestoreOutcome.Restored(
            contents.financial.summary(),
            PendingPreferences(
                appearance = preferences?.appearance?.takeUnless { applied(APPEARANCE) },
                ordering = preferences?.ordering?.takeUnless { applied(ORDERING) },
                investments = preferences?.investments?.takeUnless { applied(INVESTMENTS) }
            )
        )
    }

    /**
     * A replacement that committed while its file is still staged, as after a crash or a
     * rename that failed: nothing of it was applied, so every preference it brings is
     * pending. Null when there is none.
     */
    private suspend fun unmoved(): RestoreOutcome.Restored? {
        val manifest = readManifest() ?: return null
        if (committed(manifest.operation) != true) return null
        val bytes = io(failed = null) { staged.takeIf { it.isFile }?.readBytes() } ?: return null
        return pendingFrom(parse(bytes)?.contents ?: return null, done = null)
    }

    /** A committed replacement whose file is still staged is moved first, as a look does. */
    private suspend fun settleMove() {
        if (io(failed = true) { restored.exists() }) return
        val manifest = readManifest() ?: return
        if (committed(manifest.operation) == true) io(failed = false) { moveToRestored() }
    }

    /** Applies what of [contents], the restored file, is not applied yet. */
    private suspend fun apply(contents: BackupContents): RestoreOutcome.Restored {
        val done = appliedGroups()
        val pending = pendingFrom(contents, done)
        // What was applied cannot be read: nothing is applied again until it can.
        if (done == null) return pending
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
            io(failed = Unit) {
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
        val done = appliedGroups() ?: return
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
            goalDao().deleteAllStars()
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
            goalDao().starAll(financial.starredGoals.map { StarredGoalEntity(it.value) })
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
        // Left unmarked when it cannot be read or written; see above.
        io(failed = Unit) {
            if (!manifest.exists()) return@io
            val properties = manifestProperties() ?: return@io
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
        }
    }

    /**
     * The manifest as it reads; null when it is damaged for good, as a malformed `\u`
     * escape makes [Properties.load] fail. One that cannot be read now throws [IOException].
     */
    private fun manifestProperties(): Properties? = try {
        Properties().apply { manifest.inputStream().use(::load) }
    } catch (_: IllegalArgumentException) {
        null
    }

    /** The operation the manifest names, if it names a well-formed one. */
    private fun manifestOperation(): OperationId? {
        val properties = manifestProperties() ?: return null
        return (OperationId.of(properties.getProperty(OPERATION).orEmpty()) as? Outcome.Success)
            ?.value
    }

    private suspend fun readManifest(): Manifest? = io(failed = null) {
        if (!manifest.exists() || !staged.exists()) return@io null
        val properties = manifestProperties() ?: return@io null
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

    /** The groups applied so far; null when the list cannot be read. */
    private suspend fun appliedGroups(): Set<String>? = io(failed = null) {
        if (applied.exists()) applied.readLines().toSet() else emptySet()
    }

    private suspend fun markApplied(group: String): Boolean = io(failed = false) {
        val done = applied.takeIf { it.exists() }?.readLines().orEmpty() + group
        writeAtomically(applied, done.distinct().joinToString("\n").toByteArray())
    }

    /** Reads [bytes] as a backup on the computation dispatcher; null if it is not one. */
    private suspend fun parse(bytes: ByteArray): Parsed? = withContext(dispatchers.default) {
        (codec.read(bytes) as? Outcome.Success)?.value?.contents?.let {
            Parsed(it, sha256Hex(bytes))
        }
    }

    private fun PreferenceWrite.saved() = this == PreferenceWrite.Saved

    /** File work off the main thread; an I/O error answers [failed], never a crash. */
    private suspend fun <T> io(failed: T, block: () -> T): T = withContext(dispatchers.io) {
        try {
            block()
        } catch (_: IOException) {
            failed
        }
    }

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

        /**
         * What the replacement is: the history it was previewed at and the exact file,
         * by the SHA-256 of its bytes.
         */
        fun digest(generation: Long, revision: Long, bytes: String): String {
            val sha256 = MessageDigest.getInstance("SHA-256")
            listOf(
                OperationKind.RestoreHistory.key,
                "1",
                generation.toString(),
                revision.toString(),
                bytes
            ).forEach { field ->
                val encoded = field.toByteArray(Charsets.UTF_8)
                sha256.update("${encoded.size}:".toByteArray(Charsets.UTF_8))
                sha256.update(encoded)
            }
            return sha256.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** A backup file [checkFile] read whole: what it holds, kept for staging it. */
internal class ParsedBackupFile(
    override val bytes: ByteArray,
    override val checked: CheckedBackup,
    val parsed: RoomRestores.Parsed
) : CheckedBackupFile

/**
 * Checks [bytes] as a whole backup without any storage, keeping what it read, so that
 * staging the file later reads nothing again. A FinanceTracker file is not translated
 * here, only named for what it is: translating needs the history's zone.
 */
fun BackupCodec.checkFile(bytes: ByteArray): Outcome<CheckedBackupFile, RestoreRejection> =
    when (val plan = read(bytes)) {
        is Outcome.Success -> with(plan.value.contents) {
            Outcome.Success(
                ParsedBackupFile(
                    bytes,
                    CheckedBackup(exportedAt, financial.zone, financial.summary()),
                    RoomRestores.Parsed(this, sha256Hex(bytes))
                )
            )
        }

        is Outcome.Failure -> Outcome.Failure(
            plan.error.takeIf { it == BackupRejection.NotSelloBackup }
                ?.let { LegacyTranslator.Recognition.untranslated(bytes) }
                ?: plan.error.reason()
        )
    }

/**
 * A FinanceTracker translation written out as a Sello backup. Encoding reads the file
 * back, so only what the released reader accepts is kept. One that outgrows a backup's
 * size or record bounds once written out is refused as too large; any other refusal is
 * of Sello's own writing, never blamed on the person's file as damage.
 */
internal fun BackupCodec.translatedFile(
    contents: BackupContents
): Outcome<ByteArray, RestoreRejection> = when (val encoded = encode(contents)) {
    is Outcome.Success -> encoded

    is Outcome.Failure -> Outcome.Failure(
        when (encoded.error) {
            is BackupRejection.TooLarge, is BackupRejection.TooManyRecords ->
                RestoreRejection.TooLarge

            else -> RestoreRejection.InvalidContent
        }
    )
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
