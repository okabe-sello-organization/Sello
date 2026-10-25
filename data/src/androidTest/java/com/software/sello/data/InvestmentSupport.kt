package com.software.sello.data

import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.domain.port.SetUsdRate
import java.time.LocalDate

/*
 * Investments through the real commands and reads of a [TestLedger]. Amounts are typed
 * the way the examples write them (docs/testing/investment-examples.md): whole pesos, or
 * USD cents. Expected figures in tests are the examples' own hand-worked numbers.
 */

fun pesos(amount: Long): InvestmentAmount = InvestmentAmount.of(Money.cop(amount)).valueOrFail()

fun pesosValue(amount: Long): InvestmentAmount =
    InvestmentAmount.ofValue(Money.cop(amount)).valueOrFail()

fun cents(amount: Long): InvestmentAmount =
    InvestmentAmount.of(Money(amount, Currency.USD)).valueOrFail()

fun rate(hundredths: Long): UsdRate = UsdRate.ofHundredths(hundredths).valueOrFail()

fun investmentId(number: Int): InvestmentId = InvestmentId.of(uuid(number)).valueOrFail()

fun movementId(number: Int): InvestmentMovementId =
    InvestmentMovementId.of(uuid(number)).valueOrFail()

fun day(text: String): LocalDate = LocalDate.parse(text)

suspend fun TestLedger.invest(command: InvestmentCommand): InvestmentCommandOutcome =
    investments.submit(command)

fun TestLedger.createInvestment(
    name: String,
    contribution: InvestmentAmount,
    date: String,
    value: InvestmentAmount = contribution,
    currency: Currency = contribution.money.currency,
    newRate: DatedUsdRate? = null,
    description: String? = null,
    operation: OperationId = operation(),
    generation: Long = 1
) = CreateInvestment(
    operationId = operation,
    generation = generation,
    name = InvestmentName.of(name).valueOrFail(),
    description = InvestmentDescription.of(description).valueOrFail(),
    icon = IconKey.of("trending_up").valueOrFail(),
    currency = currency,
    firstContribution = contribution,
    firstValue = value,
    date = day(date),
    newRate = newRate
)

/** Creates an investment and returns what was committed; fails the test otherwise. */
suspend fun TestLedger.created(command: CreateInvestment): CommittedInvestment =
    when (val outcome = invest(command)) {
        is InvestmentCommandOutcome.Committed -> outcome.change
        else -> throw AssertionError("expected the investment to be created, got $outcome")
    }

suspend fun TestLedger.summary(id: InvestmentId): InvestmentSummary =
    checkNotNull(investmentReads.investment(id).valueOrFail()) { "no investment $id" }

suspend fun TestLedger.investmentVersion(id: InvestmentId): Long =
    summary(id).history.investment.version

suspend fun TestLedger.board(): InvestmentBoard = investmentReads.board().valueOrFail()

suspend fun TestLedger.movement(
    id: InvestmentId,
    kind: InvestmentMovementKind,
    amount: InvestmentAmount,
    date: String,
    note: String? = null,
    operation: OperationId = operation()
): InvestmentCommandOutcome = invest(
    RecordInvestmentMovement(
        operation,
        1,
        id,
        investmentVersion(id),
        kind,
        amount,
        day(date),
        Note.of(note).valueOrFail()
    )
)

/** Records a movement and fails the test if it is not committed. */
suspend fun TestLedger.recorded(
    id: InvestmentId,
    kind: InvestmentMovementKind,
    amount: InvestmentAmount,
    date: String
) {
    val outcome = movement(id, kind, amount, date)
    if (outcome !is InvestmentCommandOutcome.Committed) {
        throw AssertionError("expected the movement to be recorded, got $outcome")
    }
}

suspend fun TestLedger.setRate(hundredths: Long, date: String): InvestmentCommandOutcome =
    invest(SetUsdRate(operation(), 1, rate(hundredths), day(date)))

fun <T> Outcome<T, *>.failureOrNull(): Any? = (this as? Outcome.Failure)?.error

/** One COP investment, its first aporte, its first value and one rate, as raw rows. */
fun SelloDatabase.insertInvestmentRows(first: Int = 500) {
    val millis = recordedAt.toEpochMilli()
    insertRaw(
        "investment",
        "id" to uuid(first),
        "name" to "CDT Banco",
        "description" to null,
        "icon_key" to "savings",
        "currency" to "COP",
        "next_sequence" to 2L,
        "version" to 1L,
        "created_at" to millis,
        "updated_at" to millis
    )
    for ((offset, kind) in listOf("contribution", "valuation").withIndex()) {
        insertRaw(
            "investment_movement",
            "id" to uuid(first + 1 + offset),
            "investment_id" to uuid(first),
            "sequence" to offset.toLong(),
            "kind" to kind,
            "amount_minor" to 10_000_000L,
            "currency" to "COP",
            "effective_date" to "2026-09-30",
            "note" to null,
            "version" to 1L,
            "created_at" to millis,
            "updated_at" to millis
        )
    }
    insertRaw(
        "usd_rate",
        "id" to uuid(first + 3),
        "rate_hundredths" to 415_025L,
        "effective_date" to "2026-10-09",
        "sequence" to 0L,
        "created_at" to millis
    )
}
