package com.software.sello.domain.policy

import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.PortfolioItem

/**
 * The order Inversiones lists investments in (SELLO-120). Every order ends on the name,
 * case and accents ignored, then the identifier, so the result never depends on the
 * order rows were read in.
 *
 * - Mayor valor: value in pesos, largest first; those without one last, oldest first.
 * - Rendimiento: the simple return, highest first, losses below even; those without a
 *   return (no value or nothing contributed) last.
 * - Alfabético: by name.
 * - Más reciente: the newest investment first.
 * - Última actualización: the latest movement's date first, then when it was recorded;
 *   those without movements last.
 */
object InvestmentOrderPolicy {
    fun order(items: List<PortfolioItem>, order: InvestmentOrder): List<PortfolioItem> =
        items.sortedWith(metric(order).then(byName))

    private val byName: Comparator<PortfolioItem> =
        compareBy<PortfolioItem> { it.history.investment.name.sortKey }
            .thenBy { it.history.investment.id.value }

    private fun metric(order: InvestmentOrder): Comparator<PortfolioItem> = when (order) {
        InvestmentOrder.LargestValue ->
            compareBy<PortfolioItem> { it.valueInPesos == null }
                .thenByDescending { it.valueInPesos?.minorUnits }
                // Without a value they keep the overview's order: the oldest first.
                .thenBy { if (it.valueInPesos == null) it.history.investment.createdAt else null }

        InvestmentOrder.Return ->
            compareBy<PortfolioItem> { it.figures.`return` == null }
                .thenByDescending { signedTenths(it) }

        InvestmentOrder.Alphabetical -> Comparator { _, _ -> 0 }

        InvestmentOrder.Newest ->
            compareByDescending { it.history.investment.createdAt }

        InvestmentOrder.LatestUpdate ->
            compareBy<PortfolioItem> { latest(it) == null }
                .thenByDescending { latest(it)?.date }
                .thenByDescending { latest(it)?.createdAt }
    }

    private fun signedTenths(item: PortfolioItem): Long? = item.figures.`return`?.let {
        when (it.direction) {
            GainDirection.Gain -> it.tenths
            GainDirection.Loss -> -it.tenths
            GainDirection.Even -> 0
        }
    }

    /** The movement with the latest date, the last recorded among those on it. */
    private fun latest(item: PortfolioItem): InvestmentMovement? =
        item.history.movements.maxWithOrNull(compareBy({ it.date }, { it.createdAt }))
}
