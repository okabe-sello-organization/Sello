package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.data.mapper.SUBJECT_SEPARATOR
import com.software.sello.data.mapper.StoredReceipt
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toStored
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDateError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.EntryBatchPolicy
import com.software.sello.domain.port.AuditClock
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedExpenses
import com.software.sello.domain.port.CommittedIncomes
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.CreateIncomes
import com.software.sello.domain.port.ExpenseBatchCommands
import com.software.sello.domain.port.ExpenseBatchOutcome
import com.software.sello.domain.port.ExpenseBatchRejection
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.IncomeBatchCommands
import com.software.sello.domain.port.IncomeBatchOutcome
import com.software.sello.domain.port.IncomeBatchRejection
import com.software.sello.domain.port.LineRefusal
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RefusedLine
import java.time.LocalDate
import java.time.YearMonth

/*
 * A receipt of several expenses or incomes (Anotar varios, SELLO-E23) in one transaction:
 * check the generation, answer a replay, check every line, then write every line, one
 * revision and one receipt. The receipt names the new records in line order, so a replay
 * answers with them however they were changed since. Each line becomes an ordinary
 * expense or income, in its own place in the order, exactly as a single entry would.
 */

/**
 * Records a receipt of expenses. A line goes into a category that exists and is not
 * archived; the receipt's date is one a single expense could have; the month's total must
 * still hold every line. A receipt that reaches a closed month of a carry chain and changes
 * later months is a correction (ADR 0020 CP21), let through only when its list was seen for
 * this very receipt at the current revision.
 */
class RoomExpenseBatchCommands internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource
) : ExpenseBatchCommands {
    override suspend fun create(command: CreateExpenses): ExpenseBatchOutcome {
        val digest = inputDigest(command)
        return database.runOutcome(command.operationId) { decide(command, digest) }
    }

    /** Runs inside the transaction. Every rejection returns before the first write. */
    private suspend fun decide(command: CreateExpenses, digest: String): ExpenseBatchOutcome {
        val kind = OperationKind.CreateExpenses
        val profile = database.proceedOr(
            command.operationId,
            command.generation,
            kind,
            digest,
            ::committed
        ) { return it }
        if (!EntryBatchPolicy.holds(command.lines.size)) {
            return Rejected(Specific(ExpenseBatchRejection.LineCount(command.lines.size)))
        }
        val today = clock.today.value.date
        val refused = dateRefusal(command.date, today)
            ?.let { reason -> command.lines.indices.associateWith { reason } }
            ?: when (val checked = lineRefusals(command)) {
                is Outcome.Success -> checked.value
                is Outcome.Failure -> return storageFailed(checked.error)
            }
        if (refused.isNotEmpty()) {
            return Rejected(Specific(ExpenseBatchRejection.Lines(refusedLines(refused))))
        }

        val now = audit.now()
        var sequence = maxOf(
            database.expenseDao().highestSequence() ?: 0,
            database.deletedExpenseDao().highestSequence() ?: 0
        )
        val rows = command.lines.map { line ->
            sequence = nextCounter(sequence, ExpenseEntity.TABLE, null, "sequence")
            Expense(
                id = issued(ExpenseId.of(ids.next())),
                categoryId = line.categoryId,
                date = command.date,
                sequence = sequence,
                amount = line.amount,
                note = line.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        }
        when (
            val carry = database.expensesCarry(
                null,
                rows,
                YearMonth.from(today),
                today,
                profile.revision,
                correctionIdentity(command),
                command.confirmation
            )
        ) {
            null -> Unit

            is ExpensesCarry.Failed -> return storageFailed(carry.failure)

            is ExpensesCarry.Unavailable ->
                return Rejected(Specific(ExpenseBatchRejection.CarryUnavailable(carry.failure)))

            is ExpensesCarry.Unconfirmed -> return Rejected(
                Specific(
                    ExpenseBatchRejection.CorrectionUnconfirmed(
                        carry.confirmation,
                        carry.correction
                    )
                )
            )
        }
        val receipt = database.commitCommand(
            profile,
            command.operationId,
            kind,
            digest,
            rows.joinToString(SUBJECT_SEPARATOR) { it.id },
            now
        )
        rows.forEach { database.expenseDao().insert(it) }
        return Committed(committed(receipt), replayed = false)
    }

    /**
     * Each line's category, then the month's total, by position. A category is read once
     * however many lines name it; one that cannot be read fails the command.
     */
    private suspend fun lineRefusals(
        command: CreateExpenses
    ): Outcome<Map<Int, LineRefusal>, StorageFailure> {
        val categories = mutableMapOf<String, LineRefusal?>()
        val refused = mutableMapOf<Int, LineRefusal>()
        command.lines.forEachIndexed { position, line ->
            val key = line.categoryId.value
            val reason = if (key in categories) {
                categories[key]
            } else {
                val row = database.categoryDao().byId(key)
                val found = when (val stored = row?.toDomain()) {
                    null -> LineRefusal.CategoryMissing

                    is Outcome.Failure -> return stored

                    is Outcome.Success ->
                        LineRefusal.CategoryArchived.takeIf { stored.value.archived }
                }
                found.also { categories[key] = it }
            }
            if (reason != null) refused[position] = reason
        }
        val recorded = when (val month = database.monthExpenses(YearMonth.from(command.date))) {
            is Outcome.Success -> month.value.map { it.amount.money }
            is Outcome.Failure -> return month
        }
        monthTotalRefusals(recorded, command.lines.map { it.amount.money })
            .forEach { refused.putIfAbsent(it, LineRefusal.MonthTotalFull) }
        return Outcome.Success(refused)
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedExpenses?, StorageFailure> = reading {
        when (val receipt = database.operationReceiptDao().find(operationId.value)?.toStored()) {
            null -> Outcome.Success(null)

            is Outcome.Failure -> receipt

            is Outcome.Success -> Outcome.Success(
                receipt.value.takeIf { it.receipt.kind == OperationKind.CreateExpenses }
                    ?.let(::committed)
            )
        }
    }

    private fun committed(stored: StoredReceipt) =
        CommittedExpenses(stored.receipt, stored.expenseIds)
}

/** Records a receipt of incomes, as [RoomExpenseBatchCommands] does expenses, with no category. */
class RoomIncomeBatchCommands internal constructor(
    private val database: SelloDatabase,
    private val audit: AuditClock,
    private val clock: FinancialClock,
    private val ids: RecordIdSource
) : IncomeBatchCommands {
    override suspend fun create(command: CreateIncomes): IncomeBatchOutcome {
        val digest = inputDigest(command)
        return database.runOutcome(command.operationId) { decide(command, digest) }
    }

    /** Runs inside the transaction. Every rejection returns before the first write. */
    private suspend fun decide(command: CreateIncomes, digest: String): IncomeBatchOutcome {
        val kind = OperationKind.CreateIncomes
        val profile = database.proceedOr(
            command.operationId,
            command.generation,
            kind,
            digest,
            ::committed
        ) { return it }
        if (!EntryBatchPolicy.holds(command.lines.size)) {
            return Rejected(Specific(IncomeBatchRejection.LineCount(command.lines.size)))
        }
        val refused = dateRefusal(command.date, clock.today.value.date)
            ?.let { reason -> command.lines.indices.associateWith { reason } }
            ?: when (val month = database.monthIncomes(YearMonth.from(command.date))) {
                is Outcome.Failure -> return storageFailed(month.error)

                is Outcome.Success -> monthTotalRefusals(
                    month.value.map { it.amount.money },
                    command.lines.map { it.amount.money }
                ).associateWith { LineRefusal.MonthTotalFull }
            }
        if (refused.isNotEmpty()) {
            return Rejected(Specific(IncomeBatchRejection.Lines(refusedLines(refused))))
        }

        val now = audit.now()
        var sequence = maxOf(
            database.incomeDao().highestSequence() ?: 0,
            database.deletedIncomeDao().highestSequence() ?: 0
        )
        val rows = command.lines.map { line ->
            sequence = nextCounter(sequence, IncomeEntity.TABLE, null, "sequence")
            Income(
                id = issued(IncomeId.of(ids.next())),
                date = command.date,
                sequence = sequence,
                amount = line.amount,
                source = line.source,
                note = line.note,
                version = 1,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        }
        val receipt = database.commitCommand(
            profile,
            command.operationId,
            kind,
            digest,
            rows.joinToString(SUBJECT_SEPARATOR) { it.id },
            now
        )
        rows.forEach { database.incomeDao().insert(it) }
        return Committed(committed(receipt), replayed = false)
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedIncomes?, StorageFailure> = reading {
        when (val receipt = database.operationReceiptDao().find(operationId.value)?.toStored()) {
            null -> Outcome.Success(null)

            is Outcome.Failure -> receipt

            is Outcome.Success -> Outcome.Success(
                receipt.value.takeIf { it.receipt.kind == OperationKind.CreateIncomes }
                    ?.let(::committed)
            )
        }
    }

    private fun committed(stored: StoredReceipt) =
        CommittedIncomes(stored.receipt, stored.incomeIds)
}

/** Why no line can be dated [date] on [today], or null when every line can. */
private fun dateRefusal(date: LocalDate, today: LocalDate): LineRefusal? =
    when (val checked = EffectiveDates.forManualEntry(date, today)) {
        is Outcome.Success -> null

        is Outcome.Failure -> when (checked.error) {
            is EffectiveDateError.InFuture -> LineRefusal.FutureDate(today)

            // Refused here, before the write: once stored it could not be read back.
            EffectiveDateError.OutOfRange,
            is EffectiveDateError.Malformed -> LineRefusal.InvalidDate
        }
    }

/** The positions of [lines] the month holding [recorded] cannot take in (COP, as recorded). */
private fun monthTotalRefusals(recorded: List<Money>, lines: List<Money>): List<Int> =
    EntryBatchPolicy.overflowing(ExactTotal.of(Currency.COP, recorded), lines)

private fun refusedLines(refused: Map<Int, LineRefusal>): List<RefusedLine> =
    refused.entries.sortedBy { it.key }.map { RefusedLine(it.key, it.value) }
