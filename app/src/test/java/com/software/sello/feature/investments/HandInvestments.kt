package com.software.sello.feature.investments

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioOverview
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommand
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.InvestmentUndoOffer
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
class HandInvestmentReads(
    initial: InvestmentBoardState = InvestmentBoardState.Ready(ratesBoard())
) : InvestmentReads {
    val board = MutableStateFlow(initial)

    override suspend fun board(): Outcome<InvestmentBoard, StorageFailure> =
        when (val state = board.value) {
            is InvestmentBoardState.Ready -> Outcome.Success(state.board)
            else -> Outcome.Failure(StorageFailure.Unavailable("hand"))
        }

    override fun observeBoard(): Flow<InvestmentBoardState> = board

    override suspend fun investment(id: InvestmentId): Outcome<InvestmentSummary?, StorageFailure> =
        Outcome.Success(null)
}

/** Commands as a test answers them: [answers] in turn, and [found] for find. */
class HandInvestmentCommands : InvestmentCommands {
    val sent = mutableListOf<InvestmentCommand>()
    val answers = ArrayDeque<InvestmentCommandOutcome>()
    var found: Outcome<CommittedInvestment?, StorageFailure> = Outcome.Success(null)
    val asked = mutableListOf<OperationId>()

    override suspend fun submit(command: InvestmentCommand): InvestmentCommandOutcome {
        sent += command
        return answers.removeFirst()
    }

    override fun movementUndoOffer(): InvestmentUndoOffer? = null

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedInvestment?, StorageFailure> {
        asked += operationId
        return found
    }
}
