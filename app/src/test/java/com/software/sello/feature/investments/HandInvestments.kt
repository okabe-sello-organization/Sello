package com.software.sello.feature.investments

import com.software.sello.domain.model.Currency
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
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioOverview
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentEdit
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.InvestmentUndoOffer
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Words around figures as the tests read them. */
fun testMoney() = MoneyFormatter(object : MoneyLabels {
    override fun amount(currency: Currency, digits: String, isOne: Boolean) =
        if (currency == Currency.COP) "$digits pesos" else "$digits dólares"

    override fun negative(amount: String) = "menos $amount"

    override fun positive(amount: String) = "más $amount"
})

/** A rate entered on [date] in [sequence] order, [hundredths] pesos per dollar. */
fun rateEntry(hundredths: Long, date: String, sequence: Long = 1) = UsdRateEntry(
    (UsdRateId.of("00000000-0000-4000-8000-%012d".format(900 + sequence)) as Outcome.Success).value,
    (UsdRate.ofHundredths(hundredths) as Outcome.Success).value,
    LocalDate.parse(date),
    sequence,
    Instant.EPOCH
)

/** A board with no investment and these [rates], current by the rule (latest date, then last). */
fun ratesBoard(vararg rates: UsdRateEntry) = InvestmentBoard(
    generation = 1,
    revision = 1,
    investments = emptyList(),
    rates = UsdRateBook(
        rates.maxWithOrNull(compareBy({ it.date }, { it.sequence })),
        rates.toList()
    ),
    overview = Outcome.Success(PortfolioOverview(emptyList(), null, null))
)

/** The board as a test sets it. */
class HandInvestmentReads(initial: InvestmentBoardState = ReadState.Ready(ratesBoard())) :
    InvestmentReads {
    val board = MutableStateFlow(initial)

    override suspend fun board(): Outcome<InvestmentBoard, StorageFailure> =
        when (val state = board.value) {
            is ReadState.Ready -> Outcome.Success(state.value)
            else -> Outcome.Failure(StorageFailure.Unavailable("hand"))
        }

    override fun observeBoard(): Flow<InvestmentBoardState> = board

    override suspend fun investment(id: InvestmentId): Outcome<InvestmentSummary?, StorageFailure> =
        Outcome.Success(null)
}

/**
 * Commands as a test answers them: [answers] in turn, then committed with a receipt for
 * the command's own operation; [found] for find and [undoOffer] for the offer.
 */
class HandInvestmentCommands : InvestmentCommands {
    val sent = mutableListOf<InvestmentCommand>()
    val answers = ArrayDeque<InvestmentCommandOutcome>()
    var found: Outcome<CommittedInvestment?, StorageFailure> = Outcome.Success(null)
    val asked = mutableListOf<OperationId>()

    /** What [movementUndoOffer] says is on offer. */
    var undoOffer: () -> InvestmentUndoOffer? = { null }

    override suspend fun submit(command: InvestmentCommand): InvestmentCommandOutcome {
        sent += command
        return answers.removeFirstOrNull() ?: CommandOutcome.Committed(
            CommittedInvestment(
                OperationReceipt(
                    command.operationId,
                    OperationKind.RecordInvestmentMovement,
                    command.generation,
                    10,
                    Instant.EPOCH
                ),
                (command as? InvestmentEdit)?.investmentId
            ),
            replayed = false
        )
    }

    override fun movementUndoOffer(): InvestmentUndoOffer? = undoOffer()

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedInvestment?, StorageFailure> {
        asked += operationId
        return found
    }
}

/** An investment with [movements] (kind, minor units, date), worked out as the board does. */
fun handInvestment(
    id: String,
    name: String,
    description: String?,
    icon: String,
    movements: List<Triple<InvestmentMovementKind, Long, String>>,
    currency: Currency = Currency.COP,
    version: Long = 1
): InvestmentSummary {
    fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value
    val investment = Investment(
        InvestmentId.of(id).value(),
        InvestmentName.of(name).value(),
        InvestmentDescription.of(description).value(),
        IconKey.of(icon).value(),
        currency,
        version,
        Instant.EPOCH,
        Instant.EPOCH
    )
    val recorded = movements.mapIndexed { index, (kind, units, date) ->
        val money = Money(units, currency)
        InvestmentMovement(
            InvestmentMovementId.of("00000000-0000-4000-8000-%012d".format(500 + index)).value(),
            kind,
            if (kind == InvestmentMovementKind.Valuation) {
                InvestmentAmount.ofValue(money).value()
            } else {
                InvestmentAmount.of(money).value()
            },
            LocalDate.parse(date),
            index + 1L,
            null,
            1,
            Instant.EPOCH,
            Instant.EPOCH
        )
    }
    val history = InvestmentHistory(investment, recorded)
    return InvestmentSummary(history, InvestmentPolicy.figures(history).value())
}

/** [ratesBoard] holding [investments] too, at [generation]. */
fun investmentsBoard(
    investments: List<InvestmentSummary>,
    vararg rates: UsdRateEntry,
    generation: Long = 1
) = ratesBoard(*rates).copy(generation = generation, investments = investments)

/** A receipt for [operation], naming [investmentId] when the change has one. */
fun committedInvestment(operation: OperationId, kind: OperationKind, investmentId: String?) =
    CommandOutcome.Committed(
        CommittedInvestment(
            OperationReceipt(operation, kind, 1, 2, Instant.EPOCH),
            investmentId?.let { (InvestmentId.of(it) as Outcome.Success).value }
        ),
        replayed = false
    )

/** Inversiones' settings as a test sets them; [fail] makes reads fail with no last value. */
class HandInvestmentPreferences(value: InvestmentPreference = InvestmentPreference()) :
    InvestmentPreferences {
    val current =
        MutableStateFlow<PreferenceRead<InvestmentPreference>>(PreferenceRead.Ready(value))

    override fun observeInvestments(): Flow<PreferenceRead<InvestmentPreference>> = current

    override suspend fun setNewInvestmentCurrency(currency: Currency): PreferenceWrite {
        val now = (current.value as? PreferenceRead.Ready)?.value ?: InvestmentPreference()
        current.value = PreferenceRead.Ready(now.copy(newCurrency = currency))
        return PreferenceWrite.Saved
    }

    override suspend fun setInvestmentOrder(order: InvestmentOrder): PreferenceWrite {
        val now = (current.value as? PreferenceRead.Ready)?.value ?: InvestmentPreference()
        current.value = PreferenceRead.Ready(now.copy(order = order))
        return PreferenceWrite.Saved
    }
}
