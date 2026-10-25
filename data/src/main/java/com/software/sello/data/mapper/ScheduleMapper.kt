package com.software.sello.data.mapper

import com.software.sello.data.local.entity.ScheduleEntity
import com.software.sello.data.local.entity.SchedulePauseEntity
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RecurrenceError
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.StoredSchedule
import java.time.Instant

/**
 * A schedule and its [pauses], or the first damaged column. Every rule the domain puts on
 * a schedule holds for a stored one: the rule's days and interval, a start before any
 * end, pauses in order and apart, and a handled occurrence whose slot fits the rule.
 * Given a pause that belongs to another schedule, the read fails rather than lend it.
 */
internal fun ScheduleEntity.toStored(
    pauses: List<SchedulePauseEntity>
): Outcome<StoredSchedule, StorageFailure.Integrity> = decodeRow(ScheduleEntity.TABLE, id) {
    check("sequence", sequence >= 0)
    check("version", version >= 1)
    val currency = currency("currency", currency)
    check("currency", currency.isMvpEntryCurrency)
    val amount = valid("amount_minor", TransactionAmount.of(Money(amountMinor, currency)))
    val storedNote = note
    val note = storedNote?.let { valid("note", Note.of(it)) }
    check("note", note?.value == storedNote)
    val entry = when (kind) {
        ScheduleEntity.PAYMENT -> {
            check("source_key", sourceKey == null)
            check("source_name", sourceName == null)
            val category = valid("category_id", CategoryId.of(categoryId ?: reject("category_id")))
            ScheduledEntry.Payment(category, amount, note)
        }

        ScheduleEntity.INCOME -> {
            check("category_id", categoryId == null)
            val key = sourceKey ?: reject("source_key")
            check("source_key", key in IncomeSource.keys)
            val source = valid("source_name", IncomeSource.of(key, sourceName))
            check("source_name", (source as? IncomeSource.Other)?.name?.value == sourceName)
            ScheduledEntry.Income(source, amount, note)
        }

        else -> reject("kind")
    }
    val rule = when (rule) {
        ScheduleEntity.MONTHLY -> {
            check("rule_second", ruleSecond == null)
            valid("rule_first", RepeatRule.Monthly.of(ruleFirst))
        }

        ScheduleEntity.TWICE_MONTHLY ->
            valid(
                "rule_second",
                RepeatRule.TwiceMonthly.of(
                    ruleFirst,
                    ruleSecond ?: reject("rule_second")
                )
            )

        ScheduleEntity.EVERY_DAYS -> {
            check("rule_second", ruleSecond == null)
            valid("rule_first", RepeatRule.EveryDays.of(ruleFirst))
        }

        ScheduleEntity.EVERY_WEEKS -> {
            check("rule_second", ruleSecond == null)
            valid("rule_first", RepeatRule.EveryWeeks.of(ruleFirst))
        }

        else -> reject("rule")
    }
    val start = valid("start_date", EffectiveDates.parseDate(startDate))
    val end = endDate?.let { valid("end_date", EffectiveDates.parseDate(it)) }
    val periods = pauses.map { pause ->
        check("pauses", pause.scheduleId == id)
        SchedulePause(
            from = valid("from_date", EffectiveDates.parseDate(pause.fromDate)),
            until = pause.untilDate?.let { valid("until_date", EffectiveDates.parseDate(it)) }
        )
    }.sortedBy { it.from }
    val recurrence = when (val built = Recurrence.of(rule, start, end, periods)) {
        is Outcome.Success -> built.value

        is Outcome.Failure -> reject(
            when (built.error) {
                RecurrenceError.EndBeforeStart -> "end_date"
                is RecurrenceError.DateOutOfRange -> "start_date"
                is RecurrenceError.InvalidPause, is RecurrenceError.OverlappingPauses -> "pauses"
            }
        )
    }
    val handled = when {
        handledDate == null && handledSlot == null -> null

        else -> {
            val date =
                valid(
                    "handled_date",
                    EffectiveDates.parseDate(handledDate ?: reject("handled_date"))
                )
            val slot = handledSlot ?: reject("handled_slot")
            check("handled_slot", slot == 1 || (slot == 2 && rule is RepeatRule.TwiceMonthly))
            Occurrence(date, slot)
        }
    }
    StoredSchedule(
        schedule = Schedule(valid("id", ScheduleId.of(id)), entry, recurrence),
        sequence = sequence,
        handled = handled,
        version = version,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )
}

/** The stored columns of what a schedule records: kind, category, source key and name. */
internal class StoredEntry(
    val kind: String,
    val categoryId: String?,
    val sourceKey: String?,
    val sourceName: String?,
    val amountMinor: Long,
    val currency: String,
    val note: String?
)

internal val ScheduledEntry.stored: StoredEntry
    get() = when (this) {
        is ScheduledEntry.Payment -> StoredEntry(
            ScheduleEntity.PAYMENT,
            categoryId.value,
            null,
            null,
            amount.money.minorUnits,
            amount.money.currency.code,
            note?.value
        )

        is ScheduledEntry.Income -> StoredEntry(
            ScheduleEntity.INCOME,
            null,
            source.key,
            (source as? IncomeSource.Other)?.name?.value,
            amount.money.minorUnits,
            amount.money.currency.code,
            note?.value
        )
    }

/** The stored columns of a rule: its key, its first number and, twice a month, its second. */
internal class StoredRule(val rule: String, val first: Int, val second: Int?)

internal val RepeatRule.stored: StoredRule
    get() = when (this) {
        is RepeatRule.Monthly -> StoredRule(ScheduleEntity.MONTHLY, day, null)
        is RepeatRule.TwiceMonthly -> StoredRule(ScheduleEntity.TWICE_MONTHLY, first, second)
        is RepeatRule.EveryDays -> StoredRule(ScheduleEntity.EVERY_DAYS, days, null)
        is RepeatRule.EveryWeeks -> StoredRule(ScheduleEntity.EVERY_WEEKS, weeks, null)
    }

/** A new schedule's row: version 1, nothing handled yet. */
internal fun Schedule.toNewEntity(sequence: Long, at: Instant): ScheduleEntity {
    val entry = entry.stored
    val rule = recurrence.rule.stored
    return ScheduleEntity(
        id = id.value,
        kind = entry.kind,
        categoryId = entry.categoryId,
        sourceKey = entry.sourceKey,
        sourceName = entry.sourceName,
        amountMinor = entry.amountMinor,
        currency = entry.currency,
        note = entry.note,
        rule = rule.rule,
        ruleFirst = rule.first,
        ruleSecond = rule.second,
        startDate = EffectiveDates.storedText(recurrence.start),
        endDate = recurrence.end?.let(EffectiveDates::storedText),
        sequence = sequence,
        handledDate = null,
        handledSlot = null,
        version = 1,
        createdAt = at.toEpochMilli(),
        updatedAt = at.toEpochMilli()
    )
}

internal fun SchedulePause.toEntity(scheduleId: ScheduleId) = SchedulePauseEntity(
    scheduleId = scheduleId.value,
    fromDate = EffectiveDates.storedText(from),
    untilDate = until?.let(EffectiveDates::storedText)
)

/** A schedule's row as restored from a backup: its checkpoint kept, its version 1. */
internal fun StoredSchedule.toEntity(): ScheduleEntity {
    val entry = schedule.entry.stored
    val rule = schedule.recurrence.rule.stored
    return ScheduleEntity(
        id = schedule.id.value,
        kind = entry.kind,
        categoryId = entry.categoryId,
        sourceKey = entry.sourceKey,
        sourceName = entry.sourceName,
        amountMinor = entry.amountMinor,
        currency = entry.currency,
        note = entry.note,
        rule = rule.rule,
        ruleFirst = rule.first,
        ruleSecond = rule.second,
        startDate = EffectiveDates.storedText(schedule.recurrence.start),
        endDate = schedule.recurrence.end?.let(EffectiveDates::storedText),
        sequence = sequence,
        handledDate = handled?.date?.let(EffectiveDates::storedText),
        handledSlot = handled?.slot,
        version = version,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )
}
