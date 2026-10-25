package com.software.sello.data.mapper

import com.software.sello.data.local.entity.InvestmentEntity
import com.software.sello.data.local.entity.InvestmentMovementEntity
import com.software.sello.data.local.entity.UsdRateEntity
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.EffectiveDates
import java.time.Instant

/** The stable key a movement kind is stored as; never its enum position or class name. */
internal val InvestmentMovementKind.storedKey: String
    get() = when (this) {
        InvestmentMovementKind.Contribution -> InvestmentMovementEntity.CONTRIBUTION
        InvestmentMovementKind.Withdrawal -> InvestmentMovementEntity.WITHDRAWAL
        InvestmentMovementKind.Dividend -> InvestmentMovementEntity.DIVIDEND
        InvestmentMovementKind.Valuation -> InvestmentMovementEntity.VALUATION
    }

private fun movementKind(key: String): InvestmentMovementKind? =
    InvestmentMovementKind.entries.firstOrNull { it.storedKey == key }

/**
 * An amount checked as its kind requires: a recorded value may be 0, an aporte, retiro
 * or dividend may not (rule 2).
 */
internal fun investmentAmount(kind: InvestmentMovementKind, money: Money) =
    if (kind == InvestmentMovementKind.Valuation) {
        InvestmentAmount.ofValue(money)
    } else {
        InvestmentAmount.of(money)
    }

internal fun Investment.toEntity(nextSequence: Long) = InvestmentEntity(
    id = id.value,
    name = name.value,
    description = description?.value,
    iconKey = icon.value,
    currency = currency.code,
    nextSequence = nextSequence,
    version = version,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

internal fun InvestmentEntity.toDomain(): Outcome<Investment, StorageFailure.Integrity> =
    decodeRow(InvestmentEntity.TABLE, id) {
        // Created with its first aporte and its first value, so two positions are taken.
        check("next_sequence", nextSequence >= 2)
        check("version", version >= 1)
        val name = valid("name", InvestmentName.of(name))
        check("name", name.value == this@toDomain.name)
        val stored = description
        val description = stored?.let { valid("description", InvestmentDescription.of(it)) }
        check("description", description?.value == stored)
        val currency = currency("currency", currency)
        check("currency", currency in InvestmentAmount.currencies)
        Investment(
            id = valid("id", InvestmentId.of(id)),
            name = name,
            description = description,
            icon = valid("icon_key", IconKey.of(iconKey)),
            currency = currency,
            version = version,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

internal fun InvestmentMovement.toEntity(investmentId: InvestmentId) = InvestmentMovementEntity(
    id = id.value,
    investmentId = investmentId.value,
    sequence = sequence,
    kind = kind.storedKey,
    amountMinor = amount.money.minorUnits,
    currency = amount.money.currency.code,
    effectiveDate = EffectiveDates.storedText(date),
    note = note?.value,
    version = version,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli()
)

internal fun InvestmentMovementEntity.toDomain():
    Outcome<InvestmentMovement, StorageFailure.Integrity> =
    decodeRow(InvestmentMovementEntity.TABLE, id) {
        check("sequence", sequence >= 0)
        check("version", version >= 1)
        valid("investment_id", InvestmentId.of(investmentId))
        val kind = movementKind(kind) ?: reject("kind")
        val currency = currency("currency", currency)
        check("currency", currency in InvestmentAmount.currencies)
        val amount = valid("amount_minor", investmentAmount(kind, Money(amountMinor, currency)))
        val stored = note
        val note = stored?.let { valid("note", Note.of(it)) }
        check("note", note?.value == stored)
        InvestmentMovement(
            id = valid("id", InvestmentMovementId.of(id)),
            kind = kind,
            amount = amount,
            date = valid("effective_date", EffectiveDates.parseDate(effectiveDate)),
            sequence = sequence,
            note = note,
            version = version,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }

/**
 * An investment and every movement, or the first damaged row. Every movement must belong
 * to the investment, come before its [InvestmentEntity.nextSequence] and be in its
 * currency.
 */
internal fun InvestmentEntity.toHistory(
    movements: List<InvestmentMovementEntity>
): Outcome<InvestmentHistory, StorageFailure.Integrity> {
    val investment = when (val decoded = toDomain()) {
        is Outcome.Success -> decoded.value
        is Outcome.Failure -> return decoded
    }
    movements.firstOrNull { it.investmentId != id || it.sequence >= nextSequence }?.let {
        return Outcome.Failure(
            StorageFailure.Integrity(InvestmentMovementEntity.TABLE, it.id, "sequence")
        )
    }
    movements.firstOrNull { it.currency != currency }?.let {
        return Outcome.Failure(
            StorageFailure.Integrity(InvestmentMovementEntity.TABLE, it.id, "currency")
        )
    }
    return when (val decoded = movements.decodeAll { it.toDomain() }) {
        is Outcome.Success -> Outcome.Success(InvestmentHistory(investment, decoded.value))
        is Outcome.Failure -> decoded
    }
}

internal fun UsdRateEntry.toEntity() = UsdRateEntity(
    id = id.value,
    rateHundredths = rate.hundredths,
    effectiveDate = EffectiveDates.storedText(date),
    sequence = sequence,
    createdAt = createdAt.toEpochMilli()
)

/** A rate outside 1,00–100.000,00 is damage, never clamped or rounded (rule 11, IN32). */
internal fun UsdRateEntity.toDomain(): Outcome<UsdRateEntry, StorageFailure.Integrity> =
    decodeRow(UsdRateEntity.TABLE, id) {
        check("sequence", sequence >= 0)
        UsdRateEntry(
            id = valid("id", UsdRateId.of(id)),
            rate = valid("rate_hundredths", UsdRate.ofHundredths(rateHundredths)),
            date = valid("effective_date", EffectiveDates.parseDate(effectiveDate)),
            sequence = sequence,
            createdAt = Instant.ofEpochMilli(createdAt)
        )
    }
