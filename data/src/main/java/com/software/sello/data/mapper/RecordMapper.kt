package com.software.sello.data.mapper

import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.EffectiveDates
import java.time.Instant

private fun RowDecoder.amount(minor: Long, currencyCode: String): TransactionAmount {
    val currency = currency("currency", currencyCode)
    check("currency", currency.isMvpEntryCurrency)
    return valid("amount_minor", TransactionAmount.of(Money(minor, currency)))
}

/** Absent, or exactly the text the note rule would have stored; a blank string is damage. */
private fun RowDecoder.note(stored: String?): Note? {
    if (stored == null) return null
    val note = valid("note", Note.of(stored))
    check("note", note?.value == stored)
    return note
}

/**
 * All three origin columns or none. A slot is 1 or 2; whether 2 fits the schedule's rule
 * is the schedule's business, not this row's.
 */
private fun RowDecoder.origin(scheduleId: String?, date: String?, slot: Int?): EntryOrigin? {
    if (scheduleId == null && date == null && slot == null) return null
    val schedule =
        valid("origin_schedule_id", ScheduleId.of(scheduleId ?: reject("origin_schedule_id")))
    val day = valid("origin_date", EffectiveDates.parseDate(date ?: reject("origin_date")))
    check("origin_slot", slot == 1 || slot == 2)
    return EntryOrigin(schedule, Occurrence(day, slot!!))
}

private val EntryOrigin?.storedDate get() = this?.occurrence?.date?.let(EffectiveDates::storedText)

internal fun Expense.toEntity() = ExpenseEntity(
    id = id.value,
    categoryId = categoryId.value,
    effectiveDate = EffectiveDates.storedText(date),
    sequence = sequence,
    amountMinor = amount.money.minorUnits,
    currency = amount.money.currency.code,
    note = note?.value,
    version = version,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    originScheduleId = origin?.schedule?.value,
    originDate = origin.storedDate,
    originSlot = origin?.occurrence?.slot
)

internal fun ExpenseEntity.toDomain(): Outcome<Expense, StorageFailure.Integrity> =
    decodeRow(ExpenseEntity.TABLE, id) {
        check("sequence", sequence >= 0)
        check("version", version >= 1)
        Expense(
            id = valid("id", ExpenseId.of(id)),
            categoryId = valid("category_id", CategoryId.of(categoryId)),
            date = valid("effective_date", EffectiveDates.parseDate(effectiveDate)),
            sequence = sequence,
            amount = amount(amountMinor, currency),
            note = note(note),
            version = version,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            origin = origin(originScheduleId, originDate, originSlot)
        )
    }

internal fun Income.toEntity() = IncomeEntity(
    id = id.value,
    effectiveDate = EffectiveDates.storedText(date),
    sequence = sequence,
    amountMinor = amount.money.minorUnits,
    currency = amount.money.currency.code,
    sourceKey = source.key,
    sourceName = (source as? IncomeSource.Other)?.name?.value,
    note = note?.value,
    version = version,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    originScheduleId = origin?.schedule?.value,
    originDate = origin.storedDate,
    originSlot = origin?.occurrence?.slot
)

internal fun IncomeEntity.toDomain(): Outcome<Income, StorageFailure.Integrity> =
    decodeRow(IncomeEntity.TABLE, id) {
        check("sequence", sequence >= 0)
        check("version", version >= 1)
        check("source_key", sourceKey in IncomeSource.keys)
        val source = valid("source_name", IncomeSource.of(sourceKey, sourceName))
        check("source_name", (source as? IncomeSource.Other)?.name?.value == sourceName)
        Income(
            id = valid("id", IncomeId.of(id)),
            date = valid("effective_date", EffectiveDates.parseDate(effectiveDate)),
            sequence = sequence,
            amount = amount(amountMinor, currency),
            source = source,
            note = note(note),
            version = version,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            origin = origin(originScheduleId, originDate, originSlot)
        )
    }
