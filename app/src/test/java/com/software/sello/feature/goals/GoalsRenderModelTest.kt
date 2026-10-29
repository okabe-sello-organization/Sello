package com.software.sello.feature.goals

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Metas and the goal detail from a board. Every figure was worked out by hand from the
 * approved examples (docs/testing/goal-examples.md); today is 23 October 2026.
 */
class GoalsRenderModelTest {
    private val render = GoalsRenderModel(
        MoneyFormatter(object : MoneyLabels {
            override fun amount(currency: Currency, digits: String, isOne: Boolean) =
                "$digits pesos"

            override fun negative(amount: String) = "menos $amount"

            override fun positive(amount: String) = "más $amount"
        })
    )
    private val today = LocalDate.of(2026, 10, 23)

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    /** A goal's history as it was recorded, each call the next event. */
    private class Goals(
        val number: Int,
        val name: String,
        target: Long,
        created: LocalDate,
        val icon: String = "flight",
        val targetMonth: YearMonth? = null
    ) {
        val events = mutableListOf<GoalEvent>(GoalTargetChange(amount(target), 0, created))
        var completedOn: LocalDate? = null

        fun move(kind: GoalMovementKind, pesos: Long, date: LocalDate, note: String? = null) {
            val sequence = events.size.toLong()
            events += GoalMovement(
                (
                    GoalMovementId.of("00000000-0000-4000-8000-%04d%08d".format(number, sequence))
                        as Outcome.Success
                    ).value,
                kind,
                amount(pesos),
                date,
                sequence,
                date,
                (Note.of(note) as Outcome.Success).value,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            )
        }

        fun aporte(pesos: Long, date: LocalDate) = move(GoalMovementKind.Contribution, pesos, date)

        fun retiro(pesos: Long, date: LocalDate, note: String? = null) =
            move(GoalMovementKind.Withdrawal, pesos, date, note)

        val id get() = "00000000-0000-4000-9000-%012d".format(number)

        fun summary(): GoalSummary {
            val goal = Goal(
                (GoalId.of(id) as Outcome.Success).value,
                (GoalName.of(name) as Outcome.Success).value,
                (IconKey.of(icon) as Outcome.Success).value,
                targetMonth,
                completedOn,
                1,
                Instant.ofEpochSecond(number.toLong()),
                Instant.ofEpochSecond(number.toLong())
            )
            val history = GoalHistory(goal, events.toList())
            return GoalSummary(history, (GoalPolicy.progress(history) as Outcome.Success).value)
        }

        companion object {
            fun amount(pesos: Long) =
                (TransactionAmount.of(Money.cop(pesos)) as Outcome.Success).value
        }
    }

    private fun celular() = Goals(1, "Celular nuevo", 1_200_000, day(9, 1), "phone_iphone")
        .apply { aporte(450_000, day(10, 5)) }

    /** 750.000 in September, 960.000 in October: 1.710.000 of 3.000.000, 57 %. */
    private fun viaje() =
        Goals(2, "Viaje a la costa", 3_000_000, day(9, 15), targetMonth = YearMonth.of(2026, 12))
            .apply {
                aporte(750_000, day(9, 15))
                aporte(960_000, day(10, 2))
            }

    private fun vacunas() = Goals(3, "Vacunas de Luna", 400_000, day(7, 15), "pets").apply {
        aporte(400_000, day(9, 20))
        completedOn = day(9, 30)
    }

    private fun board(vararg goals: Goals, featured: String? = null) = GoalBoard(
        generation = 1,
        revision = 7,
        goals = goals.map { it.summary() },
        featured = featured?.let { (GoalId.of(it) as Outcome.Success).value }
    )

    private fun ready(state: GoalBoardState) = render.metas(state, today) as MetasState.Ready

    @Test
    fun metasOrdersActiveGoalsByCompletionAndPutsCompletedGoalsLast() {
        val state = ready(ReadState.Ready(board(celular(), vacunas(), viaje())))

        assertEquals(listOf("Viaje a la costa", "Celular nuevo"), state.goals.map { it.name })
        val viaje = state.goals.first()
        assertEquals(57L, viaje.percentage)
        assertEquals("1.710.000", viaje.saved.digits)
        assertEquals("3.000.000", viaje.target.digits)
        assertEquals(YearMonth.of(2026, 12), viaje.targetMonth)
        assertEquals("flight", viaje.iconKey)
        assertEquals(
            listOf(
                PlaceModel(GoalPlace.Quarter, PlaceLook.Earned, day(9, 15)),
                PlaceModel(GoalPlace.Half, PlaceLook.Earned, day(10, 2)),
                PlaceModel(GoalPlace.ThreeQuarters, PlaceLook.Empty, null),
                PlaceModel(GoalPlace.Meta, PlaceLook.Empty, null)
            ),
            viaje.places
        )
        assertNull(state.goals[1].targetMonth)
        assertEquals(
            listOf(
                CompletedGoalModel(
                    vacunas().id,
                    "Vacunas de Luna",
                    "pets",
                    state.completed.single().saved,
                    day(9, 30)
                )
            ),
            state.completed
        )
        assertEquals("400.000", state.completed.single().saved.digits)
    }

    @Test
    fun theBandAddsActiveGoalsAndThisMonthsAportesOnly() {
        val band = ready(ReadState.Ready(board(celular(), vacunas(), viaje()))).band!!

        // 1.710.000 + 450.000; aportes dated in October: 960.000 + 450.000.
        assertEquals("2.160.000", band.saved.digits)
        assertEquals("1.410.000", band.contributed!!.digits)
        assertEquals(MoneySign.None, band.contributed!!.sign)
        assertEquals(YearMonth.of(2026, 10), band.month)
    }

    @Test
    fun withoutAportesThisMonthTheBandHasNoSecondLine() {
        val quiet = Goals(4, "Fondo", 1_000_000, day(8, 1)).apply { aporte(100_000, day(8, 2)) }

        val band = ready(ReadState.Ready(board(quiet))).band!!

        assertEquals("100.000", band.saved.digits)
        assertNull(band.contributed)
    }

    @Test
    fun noGoalsIsTheTrueEmptyStateWithoutABand() {
        val state = ready(ReadState.Ready(board()))

        assertTrue(state.empty)
        assertNull(state.band)
    }

    @Test
    fun loadingAndAFailedFirstReadAreNeverNoGoals() {
        assertEquals(MetasState.Loading, render.metas(ReadState.Loading, today))
        assertEquals(
            MetasState.Failed,
            render.metas(ReadState.Failed(StorageFailure.Unavailable("x"), null), today)
        )
    }

    @Test
    fun aFailedRefreshKeepsTheLastGoodGoalsMarkedStale() {
        val state = ready(
            ReadState.Failed(StorageFailure.Unavailable("x"), board(viaje()))
        )

        assertTrue(state.stale)
        assertEquals(listOf("Viaje a la costa"), state.goals.map { it.name })
    }

    private fun detail(goal: Goals, vararg others: Goals, featured: String? = null) = (
        render.detail(
            ReadState.Ready(board(goal, *others, featured = featured)),
            goal.id,
            today
        ) as GoalDetailState.Ready
        ).goal

    @Test
    fun mt07AfterAWithdrawalTheDetailIsHonestAndKeepsTheStamp() {
        val viaje = viaje().apply { retiro(400_000, day(10, 20), "Arreglo del carro") }

        val goal = detail(viaje, celular(), featured = viaje.id)

        assertEquals(43L, goal.percentage)
        assertEquals("1.310.000", goal.saved.digits)
        assertEquals("1.690.000", goal.missing!!.digits)
        assertEquals(
            listOf(PlaceLook.Earned, PlaceLook.Kept, PlaceLook.Empty, PlaceLook.Empty),
            goal.places.map { it.look }
        )
        assertEquals(2, goal.earnedCount)
        assertEquals(listOf(GoalPlace.Half), goal.kept.map { it.place })
        assertEquals(day(10, 2), goal.kept.single().earnedOn)
        // 2.250.000 − 1.310.000.
        assertEquals(GoalPlace.ThreeQuarters, goal.next!!.place)
        assertEquals("940.000", goal.next!!.missing.digits)
        assertEquals(false, goal.next!!.reached)
        assertEquals(
            listOf(
                Triple(GoalMovementKind.Withdrawal, "400.000", emptyList()),
                Triple(GoalMovementKind.Contribution, "960.000", listOf(GoalPlace.Half)),
                Triple(GoalMovementKind.Contribution, "750.000", listOf(GoalPlace.Quarter))
            ),
            goal.movements.map { Triple(it.kind, it.amount.digits, it.stamps) }
        )
        assertEquals(MoneySign.Minus, goal.movements.first().amount.sign)
        assertEquals(MoneySign.Plus, goal.movements.last().amount.sign)
        assertEquals("Arreglo del carro", goal.movements.first().note)
        assertEquals(day(10, 20), goal.movements.first().date)
        assertEquals(day(9, 15), goal.since)
        assertEquals(true, goal.featured)
    }

    @Test
    fun aportesPorMesRunFromTheGoalsFirstMonthToThisMonth() {
        val goal = detail(viaje())

        assertEquals(
            listOf(
                Triple(YearMonth.of(2026, 9), "750.000", false),
                Triple(YearMonth.of(2026, 10), "960.000", true)
            ),
            goal.months.map { Triple(it.month, it.amount.digits, it.current) }
        )
        assertEquals(listOf(750_000L, 960_000L), goal.months.map { it.value })
    }

    @Test
    fun anotherGoalOnReciboLeavesThisOneUnfeatured() {
        val celular = celular()

        assertEquals(false, detail(viaje(), celular, featured = celular.id).featured)
    }

    @Test
    fun mt05AGoalPastItsTargetWaitsAtTheMetaWithNothingMissing() {
        val over = Goals(5, "Viaje", 3_000_000, day(10, 1)).apply {
            aporte(2_950_000, day(10, 1))
            aporte(110_000, day(10, 2))
        }

        val goal = detail(over)

        assertEquals(102L, goal.percentage)
        assertNull(goal.missing)
        assertEquals(PlaceLook.Ready, goal.places.last().look)
        assertEquals(GoalPlace.Meta, goal.next!!.place)
        assertEquals(true, goal.next!!.reached)
    }

    @Test
    fun aCompletedGoalHasEveryStampAndNothingNext() {
        val goal = detail(vacunas())

        assertEquals(day(9, 30), goal.completedOn)
        assertEquals(List(4) { PlaceLook.Earned }, goal.places.map { it.look })
        assertNull(goal.next)
        assertNull(goal.missing)
        assertEquals(
            listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9)),
            goal.months.map { it.month }
        )
        assertEquals(listOf(false, false, false), goal.months.map { it.current })
    }

    @Test
    fun aGoalThatIsNotOnTheBoardIsMissingNotEmpty() {
        assertEquals(
            GoalDetailState.Missing,
            render.detail(ReadState.Ready(board(viaje())), celular().id, today)
        )
        assertEquals(
            GoalDetailState.Failed,
            render.detail(
                ReadState.Failed(StorageFailure.Unavailable("x"), null),
                celular().id,
                today
            )
        )
        assertEquals(
            GoalDetailState.Loading,
            render.detail(ReadState.Loading, celular().id, today)
        )
    }
}
