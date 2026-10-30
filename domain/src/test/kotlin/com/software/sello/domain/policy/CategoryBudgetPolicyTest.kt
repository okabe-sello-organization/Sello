package com.software.sello.domain.policy

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.policy.MonthBudgetState.Limited
import com.software.sello.domain.policy.MonthBudgetState.Paused
import com.software.sello.domain.policy.MonthBudgetState.Unconfigured
import com.software.sello.domain.valueOrFail
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryBudgetPolicyTest {
    private val category = CategoryId.of("00000000-0000-4000-8000-000000000001").valueOrFail()
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)

    private fun pesos(amount: Long): BudgetLimit =
        BudgetLimit.finite(Money.cop(amount)).valueOrFail()

    private fun default(month: YearMonth, amount: Long) =
        DefaultLimit(category, month, AutomaticBudget.Limit(pesos(amount)))

    private fun paused(month: YearMonth) = DefaultLimit(category, month, AutomaticBudget.Paused)

    private fun derived(amount: Long) = Limited(pesos(amount), explicit = false)

    private fun state(
        month: YearMonth,
        defaults: List<DefaultLimit>,
        override: MonthLimit? = null
    ) = CategoryBudgetPolicy.stateFor(month, defaults, override)

    @Test
    fun aLaterDefaultDoesNotRewriteAnEarlierMonth() {
        // H01: September 100.000, October default changed to 120.000.
        val history = listOf(default(september, 100_000), default(october, 120_000))

        assertEquals(derived(100_000), state(september, history))
        assertEquals(derived(120_000), state(october, history))
    }

    @Test
    fun anUnopenedMonthUsesTheDefaultThatWasInForceThen() {
        // H03: default 100.000 from October, nothing in November, 120.000 from December.
        val history = listOf(default(december, 120_000), default(october, 100_000))

        assertEquals(derived(100_000), state(november, history))
        assertEquals(derived(120_000), state(december, history))
        assertEquals(derived(120_000), state(YearMonth.of(2027, 6), history))
    }

    @Test
    fun aMonthBeforeAnyConfigurationIsUnconfiguredNotBackfilled() {
        val history = listOf(default(october, 100_000))

        assertEquals(Unconfigured, state(september, history))
        assertEquals(Unconfigured, state(october, emptyList()))
    }

    @Test
    fun aLimitSetForTheMonthWinsAndChangesNoOtherMonth() {
        // H02: September corrected to 90.000; October stays 120.000.
        val history = listOf(default(september, 100_000), default(october, 120_000))
        val correction = MonthLimit(category, september, pesos(90_000))

        assertEquals(Limited(pesos(90_000), explicit = true), state(september, history, correction))
        assertEquals(derived(120_000), state(october, history))
    }

    @Test
    fun zeroUnlimitedAndUnconfiguredAreThreeDifferentAnswers() {
        val zero = listOf(default(october, 0))
        val unlimited =
            listOf(DefaultLimit(category, october, AutomaticBudget.Limit(BudgetLimit.Unlimited)))

        assertEquals(derived(0), state(october, zero))
        assertEquals(Limited(BudgetLimit.Unlimited, explicit = false), state(october, unlimited))
        assertEquals(Unconfigured, state(september, zero))
    }

    @Test
    fun archivingKeepsTheMonthAndPausesFromTheNext() {
        // AR01, AR02: archived in October with 100.000.
        assertEquals(november, CategoryBudgetPolicy.pausedFrom(october))
        assertEquals(YearMonth.of(2027, 1), CategoryBudgetPolicy.pausedFrom(december))
        val history = listOf(default(october, 100_000), paused(november))

        assertEquals(derived(100_000), state(october, history))
        assertEquals(Paused, state(november, history))
        assertEquals(Paused, state(december, history))
    }

    @Test
    fun unarchivingInTheSameMonthOnlyRemovesThePauseThatHadNotStarted() {
        // AR03.
        val history = listOf(default(october, 100_000), paused(november))

        val change = CategoryBudgetPolicy.unarchive(october, history)

        assertEquals(UnarchiveChange(remove = listOf(november), put = null), change)
    }

    @Test
    fun unarchivingLaterRestoresTheLastDefaultFromThatMonthOnly() {
        // AR04: archived in October, unarchived in December.
        val history =
            listOf(default(september, 80_000), default(october, 100_000), paused(november))

        val change = CategoryBudgetPolicy.unarchive(december, history)

        assertEquals(UnarchiveChange(emptyList(), default(december, 100_000)), change)
        val after = history + change.put!!
        assertEquals(Paused, state(november, after))
        assertEquals(derived(100_000), state(december, after))
    }

    @Test
    fun unarchivingInTheFirstPausedMonthReplacesThePauseItself() {
        val history = listOf(default(october, 100_000), paused(november))

        val change = CategoryBudgetPolicy.unarchive(november, history)

        assertEquals(UnarchiveChange(emptyList(), default(november, 100_000)), change)
    }

    @Test
    fun aLimitSetForAPausedMonthStillWins() {
        // AR05: December has its own 90.000 while the default is paused.
        val history = listOf(default(october, 100_000), paused(november))
        val december90 = MonthLimit(category, december, pesos(90_000))

        assertEquals(Limited(pesos(90_000), explicit = true), state(december, history, december90))
    }

    @Test
    fun unarchivingACategoryThatWasNeverPausedChangesNothing() {
        val history = listOf(default(october, 100_000))

        assertEquals(
            UnarchiveChange(emptyList(), null),
            CategoryBudgetPolicy.unarchive(december, history)
        )
    }

    private var nextCategory = 10

    private fun inMonth(state: MonthBudgetState, archived: Boolean = false) = CategoryInMonth(
        Category(
            id = CategoryId.of("00000000-0000-4000-8000-%012d".format(nextCategory++))
                .valueOrFail(),
            name = CategoryName.of("Categoría $nextCategory").valueOrFail(),
            icon = IconKey.of("home").valueOrFail(),
            archived = archived,
            version = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        ),
        state
    )

    private fun limits(vararg amounts: Long) = amounts.map { inMonth(derived(it)) }

    private val starting = listOf(200_000L, 300_000L, 500_000L).map(Money::cop)

    private fun suggested(categories: List<CategoryInMonth>) =
        CategoryBudgetPolicy.suggestedLimits(categories, starting).map { it.minorUnits }

    @Test
    fun withNoLimitsYetTheStartingSuggestionsAreOffered() {
        assertEquals(listOf(200_000L, 300_000L, 500_000L), suggested(emptyList()))
    }

    @Test
    fun theThreeMostCommonLimitsAreSuggestedSmallestFirst() {
        val categories = limits(300_000, 300_000, 300_000, 900_000, 900_000) +
            limits(150_000, 150_000, 50_000)

        assertEquals(listOf(150_000L, 300_000L, 900_000L), suggested(categories))
    }

    @Test
    fun equallyCommonLimitsFavourTheSmaller() {
        val categories = limits(400_000, 100_000, 300_000, 200_000)

        assertEquals(listOf(100_000L, 200_000L, 300_000L), suggested(categories))
    }

    @Test
    fun archivedZeroUnlimitedAndUnsetCategoriesSuggestNothing() {
        val categories = listOf(
            inMonth(derived(80_000), archived = true),
            inMonth(derived(80_000), archived = true),
            inMonth(derived(0)),
            inMonth(Limited(BudgetLimit.Unlimited, explicit = false)),
            inMonth(Paused),
            inMonth(Unconfigured)
        )

        assertEquals(listOf(200_000L, 300_000L, 500_000L), suggested(categories))
    }

    @Test
    fun aLimitAboveTheMaximumOneCanSetIsNotSuggestedHoweverCommon() {
        val categories = limits(1_000_000_000_000, 1_000_000_000_000, 999_999_999_999)

        assertEquals(listOf(200_000L, 300_000L, 999_999_999_999L), suggested(categories))
    }

    @Test
    fun fewerThanThreeOwnLimitsAreToppedUpFromTheStartingOnes() {
        assertEquals(listOf(200_000L, 250_000L, 300_000L), suggested(limits(250_000)))
        assertEquals(
            listOf(200_000L, 300_000L, 500_000L),
            suggested(limits(300_000, 300_000))
        )
    }
}
