package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.DeletedInvestmentMovementEntity
import com.software.sello.data.local.entity.InvestmentEntity
import com.software.sello.data.local.entity.InvestmentMovementEntity
import com.software.sello.data.local.entity.UsdRateEntity
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.investmentAmount
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toHistory
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentFigures
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.EffectiveDateError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.DeleteInvestment
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.EditInvestment
import com.software.sello.domain.port.EditInvestmentMovement
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommandOutcome.Committed
import com.software.sello.domain.port.InvestmentCommandOutcome.OutcomeUnknown
import com.software.sello.domain.port.InvestmentCommandOutcome.Rejected
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentEdit
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.InvestmentUndoOffer
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.domain.port.RestoreInvestmentMovement
import com.software.sello.domain.port.SetUsdRate
import java.time.Instant
import java.time.LocalDate

/**
 * Investments and the USD rate, by the steps of [RoomGoalCommands]: check the
 * generation, answer a replay, validate, then write the rows, the revision and the
 * receipt together. Every write to an investment's history is first replayed by
 * [InvestmentPolicy], so a stored history always works out (rules 3–8), and raises the
 * investment's version. Nothing here reads or writes any other record (rule 15).
 *
 * A deleted movement shares [undo] with expenses, income and goal movements.
 *
 * Two inputs the port has no rejection of their own for are refused as
 * [InvestmentRejection.Unrepresentable]: a date whose year cannot be stored (before year
 * 0000; a later one is already in the future) and an aporte, retiro or dividend of 0,
 * which only a recorded value may be (rule 2).
 */
class RoomInvestmentCommands internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val undo: UndoWindow
) : InvestmentCommands {
    override fun movementUndoOffer(): InvestmentUndoOffer? =
        undo.offered(UndoWindow.Kind.InvestmentMovement)?.let { offer ->
            (InvestmentId.of(offer.subjectId) as? Outcome.Success)
                ?.let { InvestmentUndoOffer(offer.deletionId, it.value, offer.until) }
        }

    override suspend fun submit(command: InvestmentCommand): InvestmentCommandOutcome =
        when (command) {
            is DeleteInvestmentMovement -> undo.changing {
                val outcome = run(command.operationId) { decide(command) }
                // A replay is the original deletion, so it never starts the time again.
                if (outcome is Committed && !outcome.replayed) {
                    undo.opened(
                        UndoWindow.Kind.InvestmentMovement,
                        command.operationId,
                        command.investmentId.value
                    )
                }
                outcome
            }

            is RestoreInvestmentMovement -> undo.changing {
                val outcome = run(command.operationId) { decide(command) }
                if (outcome is Committed && !outcome.replayed) undo.closed()
                outcome
            }

            is CreateInvestment -> run(command.operationId) { decide(command) }

            is EditInvestment -> run(command.operationId) { decide(command) }

            is RecordInvestmentMovement -> run(command.operationId) { decide(command) }

            is EditInvestmentMovement -> run(command.operationId) { decide(command) }

            is DeleteInvestment -> run(command.operationId) { decide(command) }

            is SetUsdRate -> run(command.operationId) { decide(command) }
        }

    private suspend fun run(
        operationId: OperationId,
        decide: suspend () -> InvestmentCommandOutcome
    ): InvestmentCommandOutcome = when (val run = database.runCommand(decide)) {
        is CommandRun.Finished -> run.value
        is CommandRun.RolledBack -> failed(run.failure)
        CommandRun.Unknown -> OutcomeUnknown(operationId)
    }

    private fun failed(failure: StorageFailure) =
        Rejected(InvestmentRejection.StorageFailed(failure))

    /** The committed change a receipt describes, or null when it is not an investment's. */
    private fun StoredReceipt.committedOrNull(): CommittedInvestment? = when {
        investmentId != null -> CommittedInvestment(receipt, investmentId)
        usdRateId != null -> CommittedInvestment(receipt, null)
        else -> null
    }

    private fun StoredReceipt.committed(): CommittedInvestment =
        checkNotNull(committedOrNull()) { "Not an investment receipt" }

    /** Steps 1 and 2 of every command. [proceed] runs only for an operation not seen before. */
    private suspend inline fun starting(
        command: InvestmentCommand,
        proceed: (FinancialProfile) -> InvestmentCommandOutcome
    ): InvestmentCommandOutcome = when (
        val start = database.startCommand(
            command.operationId,
            command.generation,
            command.kind,
            inputDigest(command)
        )
    ) {
        is CommandStart.Proceed -> proceed(start.profile)
        is CommandStart.Stale -> Rejected(InvestmentRejection.StaleGeneration(start.current))
        is CommandStart.Replay -> Committed(start.original.committed(), true)
        CommandStart.Conflict -> Rejected(InvestmentRejection.OperationConflict)
        is CommandStart.Damaged -> failed(start.failure)
    }

    private suspend fun commit(
        profile: FinancialProfile,
        command: InvestmentCommand,
        subjectId: String,
        at: Instant
    ): StoredReceipt = database.commitCommand(
        profile,
        command.operationId,
        command.kind,
        inputDigest(command),
        subjectId,
        at
    )

    private suspend fun committed(
        profile: FinancialProfile,
        command: InvestmentCommand,
        investmentId: String,
        at: Instant
    ) = Committed(commit(profile, command, investmentId, at).committed(), replayed = false)

    /** An investment as stored, read inside the transaction. */
    private class Loaded(val row: InvestmentEntity, val history: InvestmentHistory)

    private sealed interface Load {
        data class Found(val investment: Loaded) : Load

        data class Refused(val outcome: InvestmentCommandOutcome) : Load
    }

    /** The investment [command] is about, at the version the caller read. */
    private suspend fun load(command: InvestmentEdit): Load {
        val row = database.investmentDao().byId(command.investmentId.value)
            ?: return Load.Refused(Rejected(InvestmentRejection.InvestmentMissing))
        val history =
            when (val read = row.toHistory(database.investmentDao().movements(row.id))) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return Load.Refused(failed(read.error))
            }
        if (history.investment.version != command.expectedVersion) {
            return Load.Refused(
                Rejected(InvestmentRejection.VersionConflict(history.investment.version))
            )
        }
        // What is stored was checked before it was written; a history that does not work
        // out was damaged afterwards.
        val worked = InvestmentPolicy.figures(history)
        if (worked is Outcome.Failure) {
            return Load.Refused(failed(damage(history.investment.id, worked.error)))
        }
        return Load.Found(Loaded(row, history))
    }

    /** [history] worked out, or why it may not be written (rules 3, 6–8). */
    private fun checked(
        history: InvestmentHistory
    ): Outcome<InvestmentFigures, InvestmentRejection> =
        when (val worked = InvestmentPolicy.figures(history)) {
            is Outcome.Success -> worked
            is Outcome.Failure -> Outcome.Failure(worked.error.toRejection())
        }

    /** Raises the investment's version and writes [changed] over its row. */
    private suspend fun write(
        row: InvestmentEntity,
        now: Instant,
        changed: InvestmentEntity = row
    ) {
        val newVersion = nextCounter(row.version, InvestmentEntity.TABLE, row.id, "version")
        val updated = database.investmentDao().update(
            id = row.id,
            expectedVersion = row.version,
            name = changed.name,
            description = changed.description,
            iconKey = changed.iconKey,
            nextSequence = changed.nextSequence,
            newVersion = newVersion,
            updatedAt = now.toEpochMilli()
        )
        check(updated == 1) { "The investment changed inside the transaction that read it" }
    }

    private fun today(): LocalDate = clock.today.value.date

    /** Today or earlier (rule 3), and a date the stored text can hold. */
    private fun dateRejection(date: LocalDate): InvestmentRejection? {
        val today = today()
        return when (val checked = EffectiveDates.forManualEntry(date, today)) {
            is Outcome.Success -> null

            is Outcome.Failure -> when (checked.error) {
                is EffectiveDateError.InFuture -> InvestmentRejection.FutureDate(today)

                EffectiveDateError.OutOfRange, is EffectiveDateError.Malformed ->
                    InvestmentRejection.Unrepresentable
            }
        }
    }

    /** [amount] in [currency], and above 0 unless it is a recorded value (rules 1, 2). */
    private fun amountRejection(
        kind: InvestmentMovementKind,
        amount: InvestmentAmount,
        currency: Currency
    ): InvestmentRejection? = when {
        amount.money.currency != currency -> InvestmentRejection.CurrencyMismatch

        investmentAmount(kind, amount.money) !is Outcome.Success ->
            InvestmentRejection.Unrepresentable

        else -> null
    }

    private fun newId(): String {
        val id = ids.next()
        check(InvestmentMovementId.of(id) is Outcome.Success) {
            "The identifier source returned a malformed identifier"
        }
        return id
    }

    private fun newMovementId(): InvestmentMovementId =
        (InvestmentMovementId.of(newId()) as Outcome.Success).value

    /** A new rate, entered after every rate already stored. */
    private suspend fun insertRate(rate: DatedUsdRate, now: Instant): UsdRateEntry {
        val last = database.investmentDao().lastRateSequence()
        val sequence =
            if (last == null) 0 else nextCounter(last, UsdRateEntity.TABLE, null, "sequence")
        val entry = UsdRateEntry(
            id = (UsdRateId.of(newId()) as Outcome.Success).value,
            rate = rate.rate,
            date = rate.date,
            sequence = sequence,
            createdAt = now
        )
        database.investmentDao().insertRate(entry.toEntity())
        return entry
    }

    // --- The decisions. Each runs inside the transaction; every rejection returns before the first write. ---

    /**
     * The investment, its first aporte and its first value (the later position, same
     * date) and, when given, a new rate. A USD investment needs a rate (rule 12).
     */
    private suspend fun decide(command: CreateInvestment): InvestmentCommandOutcome =
        starting(command) { profile ->
            if (command.currency !in InvestmentAmount.currencies) {
                return Rejected(InvestmentRejection.CurrencyMismatch)
            }
            amountRejection(
                InvestmentMovementKind.Contribution,
                command.firstContribution,
                command.currency
            )?.let { return Rejected(it) }
            amountRejection(InvestmentMovementKind.Valuation, command.firstValue, command.currency)
                ?.let { return Rejected(it) }
            dateRejection(command.date)?.let { return Rejected(it) }
            command.newRate?.let { rate -> dateRejection(rate.date)?.let { return Rejected(it) } }
            val needsRate = command.currency == Currency.USD && command.newRate == null &&
                database.investmentDao().lastRateSequence() == null
            if (needsRate) return Rejected(InvestmentRejection.RateRequired)
            val now = audit.now()
            val investmentId = when (val id = InvestmentId.of(ids.next())) {
                is Outcome.Success -> id.value
                is Outcome.Failure -> error("The identifier source returned a malformed identifier")
            }
            val investment = Investment(
                id = investmentId,
                name = command.name,
                description = command.description,
                icon = command.icon,
                currency = command.currency,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
            fun first(kind: InvestmentMovementKind, amount: InvestmentAmount, sequence: Long) =
                InvestmentMovement(
                    id = newMovementId(),
                    kind = kind,
                    amount = amount,
                    date = command.date,
                    sequence = sequence,
                    note = null,
                    version = 1,
                    createdAt = now,
                    updatedAt = now
                )
            val movements = listOf(
                first(InvestmentMovementKind.Contribution, command.firstContribution, 0),
                first(InvestmentMovementKind.Valuation, command.firstValue, 1)
            )
            when (val worked = checked(InvestmentHistory(investment, movements))) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return Rejected(worked.error)
            }
            database.investmentDao().insert(investment.toEntity(nextSequence = 2))
            movements.forEach { database.investmentDao().insertMovement(it.toEntity(investmentId)) }
            command.newRate?.let { insertRate(it, now) }
            committed(profile, command, investmentId.value, now)
        }

    private suspend fun decide(command: EditInvestment): InvestmentCommandOutcome =
        starting(command) { profile ->
            val loaded = when (val load = load(command)) {
                is Load.Found -> load.investment
                is Load.Refused -> return load.outcome
            }
            val current = loaded.history.investment
            val same = current.name == command.name &&
                current.description == command.description && current.icon == command.icon
            if (same) return Rejected(InvestmentRejection.Unchanged)
            val now = audit.now()
            write(
                loaded.row,
                now,
                loaded.row.copy(
                    name = command.name.value,
                    description = command.description?.value,
                    iconKey = command.icon.value
                )
            )
            committed(profile, command, loaded.row.id, now)
        }

    private suspend fun decide(command: RecordInvestmentMovement): InvestmentCommandOutcome =
        starting(command) { profile ->
            val loaded = when (val load = load(command)) {
                is Load.Found -> load.investment
                is Load.Refused -> return load.outcome
            }
            val investment = loaded.history.investment
            amountRejection(command.kind, command.amount, investment.currency)
                ?.let { return Rejected(it) }
            dateRejection(command.date)?.let { return Rejected(it) }
            val now = audit.now()
            val movement = InvestmentMovement(
                id = newMovementId(),
                kind = command.kind,
                amount = command.amount,
                date = command.date,
                sequence = loaded.row.nextSequence,
                note = command.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            )
            when (val worked = checked(InvestmentPolicy.with(loaded.history, movement))) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return Rejected(worked.error)
            }
            database.investmentDao().insertMovement(movement.toEntity(investment.id))
            val next = nextCounter(
                loaded.row.nextSequence,
                InvestmentEntity.TABLE,
                loaded.row.id,
                "next_sequence"
            )
            write(loaded.row, now, loaded.row.copy(nextSequence = next))
            committed(profile, command, loaded.row.id, now)
        }

    /** The movement [id] of [investment], as stored, or why it cannot be changed. */
    private fun movement(
        investment: Loaded,
        id: InvestmentMovementId,
        expectedVersion: Long
    ): Outcome<InvestmentMovement, InvestmentRejection> {
        val stored = investment.history.movements.firstOrNull { it.id == id }
            ?: return Outcome.Failure(InvestmentRejection.MovementMissing)
        if (stored.version != expectedVersion) {
            return Outcome.Failure(InvestmentRejection.VersionConflict(stored.version))
        }
        return Outcome.Success(stored)
    }

    /** A correction in place: the movement keeps its kind and position (rule 9). */
    private suspend fun decide(command: EditInvestmentMovement): InvestmentCommandOutcome =
        starting(command) { profile ->
            val loaded = when (val load = load(command)) {
                is Load.Found -> load.investment
                is Load.Refused -> return load.outcome
            }
            val current =
                when (val found = movement(loaded, command.movementId, command.movementVersion)) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return Rejected(found.error)
                }
            amountRejection(current.kind, command.amount, loaded.history.investment.currency)
                ?.let { return Rejected(it) }
            val same = current.amount == command.amount && current.date == command.date &&
                current.note == command.note
            if (same) return Rejected(InvestmentRejection.Unchanged)
            if (command.date != current.date) {
                dateRejection(command.date)?.let { return Rejected(it) }
            }
            val corrected =
                current.copy(amount = command.amount, date = command.date, note = command.note)
            when (val worked = checked(InvestmentPolicy.with(loaded.history, corrected))) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return Rejected(worked.error)
            }
            val now = audit.now()
            val updated = database.investmentDao().updateMovement(
                id = current.id.value,
                expectedVersion = current.version,
                amountMinor = command.amount.money.minorUnits,
                currency = command.amount.money.currency.code,
                effectiveDate = EffectiveDates.storedText(command.date),
                note = command.note?.value,
                newVersion = nextCounter(
                    current.version,
                    InvestmentMovementEntity.TABLE,
                    current.id.value,
                    "version"
                ),
                updatedAt = now.toEpochMilli()
            )
            check(updated == 1) { "The movement changed inside the transaction that read it" }
            write(loaded.row, now)
            committed(profile, command, loaded.row.id, now)
        }

    /**
     * The row is moved, column for column, into the copy kept for undo; any copy an
     * earlier deletion of any kind left is removed, which makes that deletion final. The
     * only aporte is never deleted (rule 6, IN22).
     */
    private suspend fun decide(command: DeleteInvestmentMovement): InvestmentCommandOutcome =
        starting(command) { profile ->
            val loaded = when (val load = load(command)) {
                is Load.Found -> load.investment
                is Load.Refused -> return load.outcome
            }
            val current =
                when (val found = movement(loaded, command.movementId, command.movementVersion)) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return Rejected(found.error)
                }
            val contributions =
                loaded.history.movements.count { it.kind == InvestmentMovementKind.Contribution }
            if (current.kind == InvestmentMovementKind.Contribution && contributions == 1) {
                return Rejected(InvestmentRejection.LastContribution)
            }
            when (val worked = checked(InvestmentPolicy.without(loaded.history, current))) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return Rejected(worked.error)
            }
            val row = checkNotNull(database.investmentDao().movement(current.id.value))
            val now = audit.now()
            database.discardKeptCopies()
            database.deletedInvestmentMovementDao().insert(
                DeletedInvestmentMovementEntity(
                    deletionId = command.operationId.value,
                    generation = profile.generation,
                    deletedAt = now.toEpochMilli(),
                    movementId = row.id,
                    investmentId = row.investmentId,
                    sequence = row.sequence,
                    kind = row.kind,
                    amountMinor = row.amountMinor,
                    currency = row.currency,
                    effectiveDate = row.effectiveDate,
                    note = row.note,
                    version = row.version,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt
                )
            )
            val removed = database.investmentDao().deleteMovement(row.id, row.version)
            check(removed == 1) { "The movement changed inside the transaction that read it" }
            write(loaded.row, now)
            committed(profile, command, loaded.row.id, now)
        }

    /**
     * A replay is answered first, whatever the time is. Otherwise the deletion must be
     * the one on offer and still in time, belong to this history and to an investment
     * that still has its place free, and the history with it back must still work out.
     */
    private suspend fun decide(command: RestoreInvestmentMovement): InvestmentCommandOutcome =
        starting(command) { profile ->
            val inTime = undo.offered(UndoWindow.Kind.InvestmentMovement)?.deletionId ==
                command.deletionId
            if (!inTime) return Rejected(InvestmentRejection.UndoExpired)
            val kept = database.deletedInvestmentMovementDao().find(command.deletionId.value)
                ?: return Rejected(InvestmentRejection.UndoExpired)
            val dao = database.investmentDao()
            val row = dao.byId(kept.investmentId)
            val taken = kept.generation != profile.generation || row == null ||
                dao.movement(kept.movementId) != null ||
                dao.countAt(kept.investmentId, kept.sequence) != 0
            if (taken || row == null) return Rejected(InvestmentRejection.UndoConflict)
            val movement = InvestmentMovementEntity(
                id = kept.movementId,
                investmentId = kept.investmentId,
                sequence = kept.sequence,
                kind = kept.kind,
                amountMinor = kept.amountMinor,
                currency = kept.currency,
                effectiveDate = kept.effectiveDate,
                note = kept.note,
                version = kept.version,
                createdAt = kept.createdAt,
                updatedAt = kept.updatedAt
            )
            val history = when (val read = row.toHistory(dao.movements(row.id) + movement)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return failed(read.error)
            }
            if (checked(history) is Outcome.Failure) {
                return Rejected(InvestmentRejection.UndoConflict)
            }
            dao.insertMovement(movement)
            val removed = database.deletedInvestmentMovementDao().remove(kept.deletionId)
            check(removed == 1) { "The kept copy changed inside the transaction that read it" }
            val now = audit.now()
            write(row, now)
            committed(profile, command, row.id, now)
        }

    /** The investment and every movement go in this one transaction; no undo (rule 9). */
    private suspend fun decide(command: DeleteInvestment): InvestmentCommandOutcome =
        starting(command) { profile ->
            val loaded = when (val load = load(command)) {
                is Load.Found -> load.investment
                is Load.Refused -> return load.outcome
            }
            val now = audit.now()
            val dao = database.investmentDao()
            val removedMovements = dao.deleteMovements(loaded.row.id)
            check(removedMovements == loaded.history.movements.size) {
                "The movements changed inside the transaction that read them"
            }
            val removed = dao.delete(loaded.row.id, loaded.row.version)
            check(removed == 1) { "The investment changed inside the transaction that read it" }
            committed(profile, command, loaded.row.id, now)
        }

    /** A new dated rate; earlier ones stay (rule 11). */
    private suspend fun decide(command: SetUsdRate): InvestmentCommandOutcome =
        starting(command) { profile ->
            dateRejection(command.date)?.let { return Rejected(it) }
            val now = audit.now()
            val entry = insertRate(DatedUsdRate(command.rate, command.date), now)
            Committed(commit(profile, command, entry.id.value, now).committed(), replayed = false)
        }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedInvestment?, StorageFailure> = reading {
        when (val receipt = database.operationReceiptDao().find(operationId.value)?.toStored()) {
            null -> Outcome.Success(null)
            is Outcome.Failure -> receipt
            is Outcome.Success -> Outcome.Success(receipt.value.committedOrNull())
        }
    }
}

/** Why a candidate history may not be written, as the port names it. */
internal fun InvestmentHistoryError.toRejection(): InvestmentRejection = when (this) {
    is InvestmentHistoryError.WithdrawalAboveValue ->
        InvestmentRejection.AboveValue(withdrawal, available)

    is InvestmentHistoryError.WithdrawalWithoutValue -> InvestmentRejection.NoValue(withdrawal)

    is InvestmentHistoryError.BeforeFirstContribution ->
        InvestmentRejection.BeforeFirstContribution(firstContribution)

    InvestmentHistoryError.NoContribution -> InvestmentRejection.LastContribution

    is InvestmentHistoryError.CurrencyMismatch -> InvestmentRejection.CurrencyMismatch

    InvestmentHistoryError.Unrepresentable -> InvestmentRejection.Unrepresentable
}

/**
 * A stored history that no longer works out was damaged after it was written: the
 * failure names the movement the rule broke at, or the investment when none is named.
 */
internal fun damage(investment: InvestmentId, error: InvestmentHistoryError) = when (error) {
    is InvestmentHistoryError.WithdrawalAboveValue ->
        StorageFailure.Integrity(
            InvestmentMovementEntity.TABLE,
            error.withdrawal.value,
            "amount_minor"
        )

    is InvestmentHistoryError.WithdrawalWithoutValue ->
        StorageFailure.Integrity(InvestmentMovementEntity.TABLE, error.withdrawal.value, "kind")

    is InvestmentHistoryError.BeforeFirstContribution ->
        StorageFailure.Integrity(
            InvestmentMovementEntity.TABLE,
            error.movement.value,
            "effective_date"
        )

    InvestmentHistoryError.NoContribution ->
        StorageFailure.Integrity(InvestmentMovementEntity.TABLE, investment.value, "kind")

    is InvestmentHistoryError.CurrencyMismatch ->
        StorageFailure.Integrity(InvestmentMovementEntity.TABLE, error.movement.value, "currency")

    InvestmentHistoryError.Unrepresentable ->
        StorageFailure.Integrity(InvestmentMovementEntity.TABLE, investment.value, "amount_minor")
}
