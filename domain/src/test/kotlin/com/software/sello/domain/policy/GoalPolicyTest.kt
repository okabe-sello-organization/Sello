package com.software.sello.domain.policy

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalHistoryError
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalOverview
import com.software.sello.domain.model.GoalPlace.Half
import com.software.sello.domain.model.GoalPlace.Meta
import com.software.sello.domain.model.GoalPlace.Quarter
import com.software.sello.domain.model.GoalPlace.ThreeQuarters
import com.software.sello.domain.model.GoalProgress
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthContribution
import com.software.sello.domain.model.NextPlace
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approved examples of docs/testing/goal-examples.md, by their IDs. Every expected
 * figure is copied from that document, where it was worked out by hand.
 */
class GoalPolicyTest {
    private val today = LocalDate.of(2026, 10, 23)

    /** A goal's history written as it was recorded: each call is the next event. */
    private class Recorder(target: Long, private val on: LocalDate) {
        val events = mutableListOf<GoalEvent>(GoalTargetChange(amount(target), 0, on))
        private var next = 1L

        fun aporte(pesos: Long, date: LocalDate = on, recordedOn: LocalDate = on) =
            add(GoalMovementKind.Contribution, pesos, date, recordedOn)

        fun retiro(pesos: Long, date: LocalDate = on, recordedOn: LocalDate = on) =
            add(GoalMovementKind.Withdrawal, pesos, date, recordedOn)

        fun target(pesos: Long, recordedOn: LocalDate = on) {
            events += GoalTargetChange(amount(pesos), next++, recordedOn)
        }

        /** A correction: the movement keeps its place in the history. */
        fun correct(movement: GoalMovement, pesos: Long) {
            events[events.indexOf(movement)] = movement.copy(amount = amount(pesos))
        }

        fun delete(movement: GoalMovement) {
            events.remove(movement)
        }

        private fun add(
            kind: GoalMovementKind,
            pesos: Long,
            date: LocalDate,
            recordedOn: LocalDate
        ): GoalMovement {
            val sequence = next++
            val movement = GoalMovement(
                GoalMovementId.of("00000000-0000-4000-8000-%012d".format(sequence)).valueOrFail(),
                kind,
                amount(pesos),
                date,
                sequence,
                recordedOn,
                null,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            )
            events += movement
            return movement
        }
    }

    private fun goal(
        id: Int = 1,
        completedOn: LocalDate? = null,
        createdAt: Instant = Instant.EPOCH
    ) = Goal(
        GoalId.of("00000000-0000-4000-9000-%012d".format(id)).valueOrFail(),
        GoalName.of("Viaje a la costa").valueOrFail(),
        IconKey.of("flight").valueOrFail(),
        null,
        completedOn,
        1,
        createdAt,
        createdAt
    )

    private fun Recorder.progress(completedOn: LocalDate? = null): GoalProgress =
        GoalPolicy.progress(GoalHistory(goal(completedOn = completedOn), events.toList()))
            .valueOrFail()

    private fun Recorder.refusal(): GoalHistoryError =
        GoalPolicy.progress(GoalHistory(goal(), events.toList())).errorOrFail()

    private fun viaje() = Recorder(3_000_000, today)

    private fun GoalProgress.states() = places.map { it.state }

    private fun earned(on: LocalDate = today) = PlaceState.Earned(on)

    private fun kept(on: LocalDate = today) = PlaceState.Kept(on)

    private val empty = PlaceState.Empty

    // --- Contributions and percentage ---

    @Test
    fun mt01ReachingTheQuarterExactlyEarnsItsStamp() {
        val goal = viaje()
        val before = goal.progress()
        goal.aporte(750_000)
        val after = goal.progress()

        assertEquals(Money.cop(750_000), after.saved)
        assertEquals(25L, after.percentage)
        assertEquals(listOf(earned(), empty, empty, empty), after.states())
        assertEquals(
            GoalChange(listOf(Quarter), emptyList(), false),
            GoalPolicy.change(before, after)
        )
    }

    @Test
    fun mt02OnePesoShortIsNotTheStamp() {
        val goal = viaje().apply { aporte(749_999) }.progress()

        assertEquals(24L, goal.percentage)
        assertEquals(listOf(empty, empty, empty, empty), goal.states())
    }

    @Test
    fun mt03ThePercentageRoundsDown() {
        val celular = Recorder(1_200_000, today).apply { aporte(450_000) }

        assertEquals(37L, celular.progress().percentage)
    }

    @Test
    fun mt04OneAporteCanEarnTwoStamps() {
        val goal = viaje().apply { aporte(700_000) }
        val before = goal.progress()
        goal.aporte(900_000)
        val after = goal.progress()

        assertEquals(23L, before.percentage)
        assertEquals(Money.cop(1_600_000), after.saved)
        assertEquals(53L, after.percentage)
        assertEquals(listOf(Quarter, Half), GoalPolicy.change(before, after).earned)
    }

    @Test
    fun mt05ThePercentagePassesAHundredAndMetaWaits() {
        val goal = viaje().apply { aporte(2_950_000) }
        val before = goal.progress()
        goal.aporte(110_000)
        val after = goal.progress()

        assertEquals(Money.cop(3_060_000), after.saved)
        assertEquals(102L, after.percentage)
        assertEquals(PlaceState.Ready, after.state(Meta))
        assertEquals(NextPlace(Meta, Money.cop(0)), after.next)
        assertTrue(GoalPolicy.change(before, after).reachedMeta)
        assertFalse(Meta in GoalPolicy.change(before, after).earned)
    }

    // --- Withdrawals ---

    private fun viajeAt1710000() = viaje().apply { aporte(1_710_000) }

    @Test
    fun mt07AWithdrawalKeepsTheStampItFallsBelowAndPointsAtTheNextOne() {
        val goal = viajeAt1710000().apply { retiro(400_000) }.progress()

        assertEquals(Money.cop(1_310_000), goal.saved)
        assertEquals(43L, goal.percentage)
        assertEquals(listOf(earned(), kept(), empty, empty), goal.states())
        assertEquals(NextPlace(ThreeQuarters, Money.cop(940_000)), goal.next)
    }

    @Test
    fun mt08AWithdrawalAboveWhatIsSavedIsRefusedAndSaysHowMuchThereIs() {
        val goal = viajeAt1710000()
        val withdrawal = goal.retiro(1_710_001)

        assertEquals(
            GoalHistoryError.WithdrawalAboveSaved(withdrawal.id, Money.cop(1_710_000)),
            goal.refusal()
        )
    }

    @Test
    fun mt09WithdrawingEverythingKeepsBothStampsFaded() {
        val goal = viajeAt1710000().apply { retiro(1_710_000) }.progress()

        assertEquals(Money.cop(0), goal.saved)
        assertEquals(0L, goal.percentage)
        assertEquals(listOf(kept(), kept(), empty, empty), goal.states())
    }

    @Test
    fun mt10ReachingAKeptStampAgainLightsItAndEarnsNothingNew() {
        val goal = viajeAt1710000().apply { retiro(400_000) }
        val before = goal.progress()
        goal.aporte(200_000)
        val after = goal.progress()

        assertEquals(Money.cop(1_510_000), after.saved)
        assertEquals(50L, after.percentage)
        assertEquals(GoalChange(emptyList(), listOf(Half), false), GoalPolicy.change(before, after))
    }

    // --- Replay ---

    @Test
    fun mt11WorkingTheSameHistoryOutAgainEarnsNothingNew() {
        val goal = viaje().apply {
            aporte(700_000)
            aporte(900_000)
        }
        val first = goal.progress()
        val again = goal.progress()
        val reordered = GoalPolicy.progress(GoalHistory(goal(), goal.events.reversed()))
            .valueOrFail()

        assertEquals(first, again)
        assertEquals(first, reordered)
        assertEquals(GoalChange(emptyList(), emptyList(), false), GoalPolicy.change(first, again))
    }

    @Test
    fun mt12AHistoryWithoutTheAporteHasNoStamps() {
        assertEquals(
            listOf(empty, empty, empty, empty),
            viaje().apply { aporte(700_000) }.progress().states()
        )
    }

    // --- Corrections ---

    @Test
    fun mt13CorrectingAMistypedAporteUnearnsWhatOnlyTheMistakeReached() {
        val goal = viaje().apply { aporte(700_000) }
        val mistake = goal.aporte(9_000_000)
        assertEquals(PlaceState.Ready, goal.progress().state(Meta))

        goal.correct(mistake, 900_000)
        val corrected = goal.progress()

        assertEquals(Money.cop(1_600_000), corrected.saved)
        assertEquals(53L, corrected.percentage)
        assertEquals(listOf(earned(), earned(), empty, empty), corrected.states())
    }

    @Test
    fun mt14DeletingTheAporteUnearnsItsStampsAndUndoBringsThemBack() {
        val goal = viaje().apply { aporte(700_000) }
        val aporte = goal.aporte(900_000)
        val withIt = goal.progress()
        goal.delete(aporte)
        val without = goal.progress()

        assertEquals(Money.cop(700_000), without.saved)
        assertEquals(23L, without.percentage)
        assertEquals(listOf(empty, empty, empty, empty), without.states())

        goal.events += aporte
        assertEquals(withIt, goal.progress())
    }

    @Test
    fun mt15DeletingTheAporteAWithdrawalUsesIsRefusedNamingTheWithdrawal() {
        val goal = viaje()
        val aporte = goal.aporte(500_000)
        val retiro = goal.retiro(300_000, date = LocalDate.of(2026, 10, 20))
        goal.delete(aporte)

        assertEquals(
            GoalHistoryError.WithdrawalAboveSaved(retiro.id, Money.cop(0)),
            goal.refusal()
        )
    }

    @Test
    fun mt16ACorrectionMayLeaveZeroButNotLess() {
        val goal = viaje()
        val aporte = goal.aporte(500_000)
        val retiro = goal.retiro(300_000)

        goal.correct(aporte, 300_000)
        assertEquals(Money.cop(0), goal.progress().saved)

        goal.correct(goal.events[1] as GoalMovement, 299_999)
        assertEquals(
            GoalHistoryError.WithdrawalAboveSaved(retiro.id, Money.cop(299_999)),
            goal.refusal()
        )
    }

    @Test
    fun mt39AnAporteRecordedAfterTheWithdrawalDoesNotCoverIt() {
        // In recording order the withdrawal would take what was not there yet.
        val goal = viaje()
        val aporte = goal.aporte(500_000)
        val retiro = goal.retiro(300_000)
        goal.aporte(1_000_000)
        goal.delete(aporte)

        assertEquals(
            GoalHistoryError.WithdrawalAboveSaved(retiro.id, Money.cop(0)),
            goal.refusal()
        )
    }

    // --- Target changes ---

    @Test
    fun mt17RaisingTheTargetKeepsAStampEarnedUnderTheOldOneFaded() {
        val goal = viajeAt1710000().apply { target(4_000_000) }.progress()

        assertEquals(42L, goal.percentage)
        assertEquals(listOf(earned(), kept(), empty, empty), goal.states())
    }

    @Test
    fun mt18LoweringTheTargetEarnsANewlyReachedStamp() {
        val goal = viajeAt1710000()
        val before = goal.progress()
        goal.target(2_000_000)
        val after = goal.progress()

        assertEquals(85L, after.percentage)
        assertEquals(listOf(earned(), earned(), earned(), empty), after.states())
        assertEquals(listOf(ThreeQuarters), GoalPolicy.change(before, after).earned)
    }

    @Test
    fun mt19LoweringTheTargetBelowWhatIsSavedReachesMeta() {
        val goal = viajeAt1710000()
        val before = goal.progress()
        goal.target(1_700_000)
        val after = goal.progress()

        assertEquals(100L, after.percentage)
        assertEquals(PlaceState.Ready, after.state(Meta))
        val change = GoalPolicy.change(before, after)
        assertEquals(listOf(ThreeQuarters), change.earned)
        assertTrue(change.reachedMeta)
    }

    // --- Dates ---

    @Test
    fun mt20APastDatedAporteEarnsItsStampWhenRecorded() {
        val goal = viaje().apply { aporte(750_000, date = LocalDate.of(2026, 9, 15)) }.progress()

        assertEquals(earned(today), goal.state(Quarter))
    }

    @Test
    fun mt21AFutureDateIsRefused() {
        assertTrue(GoalPolicy.acceptsDate(today, today))
        assertTrue(GoalPolicy.acceptsDate(LocalDate.of(2026, 9, 15), today))
        assertFalse(GoalPolicy.acceptsDate(LocalDate.of(2026, 10, 24), today))
    }

    @Test
    fun mt22AWithdrawalIsCheckedAgainstWhatWasSavedWhenItWasRecorded() {
        val goal = viaje().apply {
            aporte(500_000, date = LocalDate.of(2026, 10, 10))
            retiro(300_000, date = LocalDate.of(2026, 10, 1))
        }

        assertEquals(Money.cop(200_000), goal.progress().saved)
    }

    @Test
    fun aStampKeepsTheDayItWasEarned() {
        val october = LocalDate.of(2026, 10, 2)
        val goal = Recorder(3_000_000, october).apply {
            aporte(1_710_000)
            retiro(400_000, recordedOn = today)
        }.progress()

        assertEquals(kept(october), goal.state(Half))
    }

    // --- Completion ---

    private val celebrated = LocalDate.of(2026, 10, 23)

    @Test
    fun mt23MarkingAchievedEarnsMetaAndCountsTheAportes() {
        val goal = viaje().apply {
            aporte(500_000)
            aporte(500_000)
            aporte(500_000)
            aporte(500_000)
            aporte(950_000)
            aporte(110_000)
        }.progress(completedOn = celebrated)

        assertTrue(goal.completed)
        assertEquals(Money.cop(3_060_000), goal.saved)
        assertEquals(6, goal.contributions)
        assertEquals(earned(celebrated), goal.state(Meta))
        assertNull(goal.next)
    }

    @Test
    fun mt24UndoingTheCompletionWaitsAtTheTargetAgain() {
        val goal = viaje().apply { aporte(3_060_000) }

        assertEquals(PlaceState.Ready, goal.progress().state(Meta))
        assertFalse(goal.progress().completed)
    }

    @Test
    fun mt26MarkedAchievedBelowAHundredLightsAllFourStamps() {
        val goal = viajeAt1710000().progress(completedOn = celebrated)

        assertEquals(Money.cop(1_710_000), goal.saved)
        assertEquals(listOf(earned(), earned(), earned(), earned()), goal.states())
    }

    // --- Recibo's goal ---

    private fun standing(
        id: Int,
        saved: Long,
        target: Long,
        created: Long = 0,
        done: Boolean = false
    ) = goal(id, if (done) today else null, Instant.ofEpochSecond(created)) to
        Recorder(target, today).apply { if (saved > 0) aporte(saved) }.progress()

    private val a = standing(1, 1_710_000, 3_000_000)
    private val b = standing(2, 450_000, 1_200_000)

    @Test
    fun mt28WithoutAChoiceTheHighestCompletionIsShown() {
        assertEquals(a.first.id, GoalPolicy.featured(listOf(b, a), null))
    }

    @Test
    fun mt29ThePersonsChoiceWins() {
        assertEquals(b.first.id, GoalPolicy.featured(listOf(a, b), b.first.id))
    }

    @Test
    fun mt30AChosenGoalThatIsCompletedFallsBack() {
        val done = standing(2, 1_200_000, 1_200_000, done = true)

        assertEquals(a.first.id, GoalPolicy.featured(listOf(a, done), done.first.id))
        assertEquals(a.first.id, GoalPolicy.featured(listOf(a), b.first.id))
    }

    @Test
    fun mt31ATieGoesToTheGoalCreatedFirst() {
        val older = standing(3, 500_000, 1_000_000, created = 10)
        val newer = standing(4, 1_000_000, 2_000_000, created = 20)

        assertEquals(older.first.id, GoalPolicy.featured(listOf(newer, older), null))
    }

    @Test
    fun mt32CompletionIsComparedExactlyNotByTheRoundedPercentage() {
        val lower = standing(5, 574, 1_000)
        val higher = standing(6, 579, 1_000, created = 99)

        assertEquals(57L, lower.second.percentage)
        assertEquals(57L, higher.second.percentage)
        assertEquals(higher.first.id, GoalPolicy.featured(listOf(lower, higher), null))
    }

    @Test
    fun mt33NoActiveGoalShowsNone() {
        assertNull(GoalPolicy.featured(listOf(standing(7, 1, 1, done = true)), null))
        assertNull(GoalPolicy.featured(emptyList(), null))
    }

    // --- Creating and editing ---

    @Test
    fun mt34ThePaceRoundsUpAndProjectsEachStamp() {
        val pace = GoalPolicy.pace(amount(20_000_000), null, today, YearMonth.of(2028, 12))!!

        assertEquals(26, pace.months)
        assertEquals(Money.cop(769_231), pace.monthly)
        assertEquals(
            mapOf(
                Quarter to YearMonth.of(2027, 5),
                Half to YearMonth.of(2027, 11),
                ThreeQuarters to YearMonth.of(2028, 6),
                Meta to YearMonth.of(2028, 12)
            ),
            pace.projected
        )
    }

    @Test
    fun mt35EditingCountsOnlyWhatIsStillMissing() {
        val saved = Recorder(20_000_000, today).apply { aporte(2_000_000) }.progress()
        val pace = GoalPolicy.pace(amount(20_000_000), saved, today, YearMonth.of(2028, 12))!!

        assertEquals(Money.cop(692_308), pace.monthly)
        assertEquals(
            mapOf(
                Quarter to YearMonth.of(2027, 3),
                Half to YearMonth.of(2027, 10),
                ThreeQuarters to YearMonth.of(2028, 5),
                Meta to YearMonth.of(2028, 12)
            ),
            pace.projected
        )
    }

    @Test
    fun aTargetMonthThisMonthOrEarlierHasNoPace() {
        assertNull(GoalPolicy.pace(amount(1_000), null, today, YearMonth.of(2026, 10)))
        assertNull(GoalPolicy.pace(amount(1_000), null, today, YearMonth.of(2026, 9)))
    }

    @Test
    fun mt37ANameHasFortyCharactersAtMost() {
        val forty = "a".repeat(40)

        assertEquals(forty, GoalName.of(forty).valueOrFail().value)
        assertEquals(TextError.TooLong(40, 41), GoalName.of(forty + "a").errorOrFail())
        assertEquals(TextError.Required, GoalName.of("   ").errorOrFail())
    }

    @Test
    fun mt38TheTargetHasTheBoundsOfAnExpense() {
        TransactionAmount.of(Money.cop(999_999_999_999)).valueOrFail()
        TransactionAmount.of(Money.cop(1_000_000_000_000)).errorOrFail()
    }

    @Test
    fun aHistoryThatDoesNotStartWithATargetIsRefused() {
        val goal = viaje().apply { aporte(1) }
        goal.events.removeAt(0)

        assertEquals(GoalHistoryError.NoTarget, goal.refusal())
    }

    // --- Metas and the goal detail (SELLO-089) ---

    private fun Recorder.history(goal: Goal = goal()) = GoalHistory(goal, events.toList())

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun mt04TheAporteThatCrossedTwoPlacesEarnedBoth() {
        val goal = viaje()
        goal.aporte(700_000)
        val crossing = goal.aporte(900_000)

        assertEquals(
            mapOf(Quarter to crossing.sequence, Half to crossing.sequence),
            GoalPolicy.earnedBy(goal.history()).valueOrFail()
        )
    }

    @Test
    fun mt07AStampKeptAfterAWithdrawalStillBelongsToTheAporteThatEarnedIt() {
        val goal = viaje()
        val quarter = goal.aporte(750_000)
        val half = goal.aporte(960_000)
        goal.retiro(400_000)

        assertEquals(
            mapOf(Quarter to quarter.sequence, Half to half.sequence),
            GoalPolicy.earnedBy(goal.history()).valueOrFail()
        )
    }

    @Test
    fun mt13ACorrectedAporteEarnsOnlyWhatItsCorrectFigureReaches() {
        val goal = viaje()
        goal.aporte(700_000)
        val typo = goal.aporte(9_000_000)
        goal.correct(typo, 900_000)

        assertEquals(
            mapOf(Quarter to typo.sequence, Half to typo.sequence),
            GoalPolicy.earnedBy(goal.history()).valueOrFail()
        )
    }

    @Test
    fun mt18APlaceEarnedByATargetChangeBelongsToNoMovement() {
        val goal = viaje()
        val first = goal.aporte(1_710_000)
        goal.target(2_000_000)

        assertEquals(
            mapOf(Quarter to first.sequence, Half to first.sequence, ThreeQuarters to 2L),
            GoalPolicy.earnedBy(goal.history()).valueOrFail()
        )
    }

    @Test
    fun mt26StampsGivenAtCompletionBelongToNoEvent() {
        val goal = viaje()
        val quarter = goal.aporte(750_000)
        val half = goal.aporte(960_000)

        assertEquals(
            mapOf(Quarter to quarter.sequence, Half to half.sequence),
            GoalPolicy.earnedBy(goal.history(goal(completedOn = today))).valueOrFail()
        )
    }

    @Test
    fun activeGoalsGoHighestCompletionFirstAndCompletedGoalsLastNewestFirst() {
        val over = standing(1, 3_060_000, 3_000_000)
        val higher = standing(2, 579, 1_000, created = 50)
        val lower = standing(3, 574, 1_000, created = 10)
        val olderHalf = standing(4, 500_000, 1_000_000, created = 20)
        val newerHalf = standing(5, 1_000_000, 2_000_000, created = 30)
        val third = standing(6, 370_000, 1_000_000, created = 0)
        val septemberDone = goal(7, day(9, 30)) to Recorder(400_000, today).progress()
        val octoberDone = goal(8, day(10, 20)) to Recorder(400_000, today).progress()
        val shuffled =
            listOf(third, octoberDone, newerHalf, lower, septemberDone, over, olderHalf, higher)

        assertEquals(
            listOf(over, higher, lower, olderHalf, newerHalf, third, octoberDone, septemberDone),
            GoalPolicy.ordered(shuffled, { it.first }, { it.second })
        )
    }

    @Test
    fun theBandAddsActiveGoalsOnlyAndThisMonthsAportesWithoutWithdrawals() {
        val viajeGoal = goal(1)
        val viaje = viaje().apply {
            aporte(750_000, date = day(9, 15))
            aporte(960_000, date = day(10, 2))
        }.history(viajeGoal)
        val celular = Recorder(1_200_000, today).apply {
            aporte(450_000, date = day(10, 5))
            retiro(100_000, date = day(10, 6))
        }.history(goal(2))
        val done = Recorder(400_000, today).apply { aporte(400_000, date = day(10, 1)) }
            .history(goal(3, completedOn = today))
        val goals = listOf(viaje, celular, done).map {
            it to GoalPolicy.progress(it).valueOrFail()
        }

        // 1.710.000 + 350.000 saved; 960.000 + 450.000 contributed in October.
        assertEquals(
            GoalOverview(saved = Money.cop(2_060_000), contributed = Money.cop(1_410_000)),
            GoalPolicy.overview(goals, YearMonth.of(2026, 10)).valueOrFail()
        )
        assertEquals(
            GoalOverview(saved = Money.cop(2_060_000), contributed = Money.cop(750_000)),
            GoalPolicy.overview(goals, YearMonth.of(2026, 9)).valueOrFail()
        )
    }

    @Test
    fun withNoActiveGoalTheBandHoldsNothing() {
        assertEquals(
            GoalOverview(Money.cop(0), Money.cop(0)),
            GoalPolicy.overview(emptyList(), YearMonth.of(2026, 10)).valueOrFail()
        )
    }

    @Test
    fun aBandTotalThatDoesNotFitIsReportedNotClipped() {
        val full = viaje().apply { aporte(1) }
        val progress = full.progress()
        val huge = progress.copy(saved = Money.cop(Long.MAX_VALUE))
        val goals = listOf(full.history(goal(1)) to huge, full.history(goal(2)) to progress)

        assertEquals(
            GoalHistoryError.Unrepresentable,
            GoalPolicy.overview(goals, YearMonth.of(2026, 10)).errorOrFail()
        )
    }

    @Test
    fun whatIsStillMissingStopsAtZeroOnceTheTargetIsPassed() {
        val mt07 = viaje().apply {
            aporte(1_710_000)
            retiro(400_000)
        }.progress()
        val mt05 = viaje().apply {
            aporte(2_950_000)
            aporte(110_000)
        }.progress()

        assertEquals(Money.cop(1_690_000), GoalPolicy.missing(mt07))
        assertEquals(Money.cop(0), GoalPolicy.missing(mt05))
    }

    private fun months(vararg pairs: Pair<YearMonth, Long>) =
        pairs.map { (month, pesos) -> MonthContribution(month, Money.cop(pesos)) }

    @Test
    fun aportesPorMesShowsTheLastSixMonthsEndingThisMonth() {
        val goal = Recorder(3_000_000, LocalDate.of(2026, 1, 10))
        listOf(4 to 90_000L, 5 to 200_000L, 6 to 250_000L, 7 to 260_000L, 8 to 300_000L)
            .forEach { (month, pesos) -> goal.aporte(pesos, day(month, 2), day(month, 2)) }
        goal.aporte(350_000, day(9, 2), day(9, 2))
        goal.aporte(350_000, day(10, 2), day(10, 2))
        goal.retiro(400_000, day(10, 20), day(10, 20))

        assertEquals(
            months(
                YearMonth.of(2026, 5) to 200_000,
                YearMonth.of(2026, 6) to 250_000,
                YearMonth.of(2026, 7) to 260_000,
                YearMonth.of(2026, 8) to 300_000,
                YearMonth.of(2026, 9) to 350_000,
                YearMonth.of(2026, 10) to 350_000
            ),
            GoalPolicy.monthlyContributions(goal.history(), today).valueOrFail()
        )
    }

    @Test
    fun aYoungGoalStartsAtItsFirstMonthAndAMonthWithoutAportesIsZero() {
        val goal = Recorder(1_000_000, day(8, 10))
        goal.aporte(100_000, day(8, 10), day(8, 10))
        goal.aporte(100_000, day(9, 1), day(9, 1))
        goal.aporte(100_000, day(9, 20), day(9, 20))

        assertEquals(
            months(
                YearMonth.of(2026, 8) to 100_000,
                YearMonth.of(2026, 9) to 200_000,
                YearMonth.of(2026, 10) to 0
            ),
            GoalPolicy.monthlyContributions(goal.history(), today).valueOrFail()
        )
    }

    @Test
    fun mt20AnAporteDatedBeforeTheGoalWasCreatedStartsTheChartInItsMonth() {
        val goal = Recorder(3_000_000, day(10, 1))
        goal.aporte(750_000, date = day(9, 15))

        assertEquals(
            months(YearMonth.of(2026, 9) to 750_000, YearMonth.of(2026, 10) to 0),
            GoalPolicy.monthlyContributions(goal.history(), today).valueOrFail()
        )
    }

    @Test
    fun aCompletedGoalsChartEndsInTheMonthItWasCompleted() {
        val goal = Recorder(400_000, day(7, 15))
        goal.aporte(100_000, day(7, 15), day(7, 15))
        goal.aporte(100_000, day(8, 15), day(8, 15))
        goal.aporte(200_000, day(9, 15), day(9, 15))

        assertEquals(
            months(
                YearMonth.of(2026, 7) to 100_000,
                YearMonth.of(2026, 8) to 100_000,
                YearMonth.of(2026, 9) to 200_000
            ),
            GoalPolicy.monthlyContributions(goal.history(goal(completedOn = day(9, 30))), today)
                .valueOrFail()
        )
    }

    // --- Checking a movement before it is sent (SELLO-091) ---

    private fun candidate(kind: GoalMovementKind, pesos: Long) = GoalMovement(
        GoalMovementId.of("00000000-0000-4000-8000-999999999999").valueOrFail(),
        kind,
        amount(pesos),
        today,
        Long.MAX_VALUE,
        today,
        null,
        1,
        Instant.EPOCH,
        Instant.EPOCH
    )

    @Test
    fun mt08ARetiroAboveWhatIsSavedIsRefusedWithWhatIsAvailable() {
        val goal = viaje().apply { aporte(1_710_000) }
        val retiro = candidate(GoalMovementKind.Withdrawal, 1_710_001)

        assertEquals(
            GoalHistoryError.WithdrawalAboveSaved(retiro.id, Money.cop(1_710_000)),
            GoalPolicy.withMovement(goal.history(), retiro).errorOrFail()
        )
        assertEquals(
            Money.cop(0),
            GoalPolicy.withMovement(
                goal.history(),
                candidate(GoalMovementKind.Withdrawal, 1_710_000)
            ).valueOrFail().saved
        )
    }

    @Test
    fun mt15CorrectingAnAporteALaterRetiroNeedsIsRefusedNamingThatRetiro() {
        val goal = viaje()
        val aporte = goal.aporte(500_000)
        val retiro = goal.retiro(300_000)

        assertEquals(
            Money.cop(0),
            GoalPolicy.withMovement(goal.history(), aporte.copy(amount = amount(300_000)))
                .valueOrFail().saved
        )
        assertEquals(
            GoalHistoryError.WithdrawalAboveSaved(retiro.id, Money.cop(299_999)),
            GoalPolicy.withMovement(goal.history(), aporte.copy(amount = amount(299_999)))
                .errorOrFail()
        )
    }

    @Test
    fun aNewAporteIsAddedAtTheEndOfTheHistory() {
        val goal = viaje().apply { aporte(1_000_000) }

        val after = GoalPolicy.withMovement(
            goal.history(),
            candidate(GoalMovementKind.Contribution, 500_000)
        ).valueOrFail()

        assertEquals(Money.cop(1_500_000), after.saved)
        assertEquals(50L, after.percentage)
    }

    // --- Editing a goal's target (SELLO-090) ---

    @Test
    fun mt17APreviewedHigherTargetKeepsTheStampEarnedUnderTheOldOne() {
        val goal = viaje().apply {
            aporte(750_000)
            aporte(960_000)
        }

        val preview = GoalPolicy.withTarget(goal.history(), amount(4_000_000), today).valueOrFail()

        assertEquals(42L, preview.percentage)
        assertEquals(listOf(earned(), kept(), empty, empty), preview.states())
        assertEquals(listOf(earned(), earned(), empty, empty), goal.progress().states())
    }

    @Test
    fun mt18APreviewedLowerTargetReachesTheNextPlace() {
        val goal = viaje().apply { aporte(1_710_000) }

        val preview = GoalPolicy.withTarget(goal.history(), amount(2_000_000), today).valueOrFail()

        assertEquals(85L, preview.percentage)
        assertEquals(listOf(earned(), earned(), earned(), empty), preview.states())
    }

    @Test
    fun mt35ThePaceForAnEditUsesThePreviewedTarget() {
        val goal = Recorder(10_000_000, today).apply { aporte(2_000_000) }
        val preview =
            GoalPolicy.withTarget(goal.history(), amount(20_000_000), today).valueOrFail()

        val pace = GoalPolicy.pace(amount(20_000_000), preview, today, YearMonth.of(2028, 12))!!

        assertEquals(Money.cop(692_308), pace.monthly)
        assertEquals(26, pace.months)
    }

    @Test
    fun previewingTheSameTargetChangesNothing() {
        val goal = viaje().apply { aporte(1_710_000) }

        assertEquals(
            goal.progress(),
            GoalPolicy.withTarget(goal.history(), amount(3_000_000), today).valueOrFail()
        )
    }

    private companion object {
        fun amount(pesos: Long) = TransactionAmount.of(Money.cop(pesos)).valueOrFail()
    }
}
