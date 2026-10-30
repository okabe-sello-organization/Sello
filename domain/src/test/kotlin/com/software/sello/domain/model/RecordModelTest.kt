package com.software.sello.domain.model

import com.software.sello.domain.errorOrFail
import com.software.sello.domain.valueOrFail
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordModelTest {
    private val canonical = "3f2c1a9e-7b4d-4c61-9a0e-5d8f2b6c7e10"

    @Test
    fun aCanonicalIdentifierIsKeptExactly() {
        assertEquals(canonical, CategoryId.of(canonical).valueOrFail().value)
        assertEquals(canonical, ExpenseId.of(canonical).valueOrFail().value)
        assertEquals(canonical, IncomeId.of(canonical).valueOrFail().value)
        assertEquals(canonical, OperationId.of(canonical).valueOrFail().value)
    }

    @Test
    fun anyOtherSpellingOfAnIdentifierIsRejectedNotNormalized() {
        val others = listOf(
            "",
            " $canonical",
            "$canonical ",
            canonical.uppercase(),
            // One upper-case letter in each block in turn.
            "3F2c1a9e-7b4d-4c61-9a0e-5d8f2b6c7e10",
            "3f2c1a9e-7B4d-4c61-9a0e-5d8f2b6c7e10",
            "3f2c1a9e-7b4d-4C61-9a0e-5d8f2b6c7e10",
            "3f2c1a9e-7b4d-4c61-9A0e-5d8f2b6c7e10",
            "3f2c1a9e-7b4d-4c61-9a0e-5D8f2b6c7e10",
            canonical.replace("-", ""),
            "{$canonical}",
            canonical.dropLast(1),
            canonical + "0",
            canonical.replace('a', 'g'),
            "1"
        )

        for (raw in others) {
            assertEquals(raw, RecordIdError.Malformed(raw), CategoryId.of(raw).errorOrFail())
            assertEquals(raw, RecordIdError.Malformed(raw), ExpenseId.of(raw).errorOrFail())
            assertEquals(raw, RecordIdError.Malformed(raw), IncomeId.of(raw).errorOrFail())
            assertEquals(raw, RecordIdError.Malformed(raw), OperationId.of(raw).errorOrFail())
        }
    }

    @Test
    fun iconKeysAreLowerCaseWordsOfAtMostFortyCharacters() {
        val longest = "a".repeat(40)

        for (raw in listOf("a", "shopping_cart", "local_cafe2", longest)) {
            assertEquals(raw, IconKey.of(raw).valueOrFail().value)
        }
        val rejected = listOf(
            "",
            "Home",
            "2nd",
            "_x",
            "shopping cart",
            "café",
            "a-b",
            " home",
            longest + "a"
        )
        for (raw in rejected) {
            assertEquals(raw, IconKeyError.Malformed(raw), IconKey.of(raw).errorOrFail())
        }
    }

    @Test
    fun aFiniteLimitMayBeZeroOrExceedTheTransactionMaximum() {
        assertEquals(Money.cop(0), BudgetLimit.finite(Money.cop(0)).valueOrFail().amount)
        assertEquals(
            Money.cop(Long.MAX_VALUE),
            BudgetLimit.finite(Money.cop(Long.MAX_VALUE)).valueOrFail().amount
        )
    }

    @Test
    fun aLimitBeingSetMayBeZeroUpToTheTransactionMaximumAndNoMore() {
        assertEquals(Money.cop(0), BudgetLimit.entered(Money.cop(0)).valueOrFail().amount)
        assertEquals(
            Money.cop(999_999_999_999),
            BudgetLimit.entered(Money.cop(999_999_999_999)).valueOrFail().amount
        )
        assertEquals(
            BudgetLimitError.AboveMaximum,
            BudgetLimit.entered(Money.cop(1_000_000_000_000)).errorOrFail()
        )
        assertEquals(
            BudgetLimitError.AboveMaximum,
            BudgetLimit.entered(Money.cop(Long.MAX_VALUE)).errorOrFail()
        )
    }

    @Test
    fun aLimitBeingSetIsRefusedForTheSameReasonsAsAnyLimitFirst() {
        assertEquals(BudgetLimitError.Negative, BudgetLimit.entered(Money.cop(-1)).errorOrFail())
        assertEquals(
            BudgetLimitError.UnsupportedCurrency(Currency.USD),
            BudgetLimit.entered(Money(1_000_000_000_000, Currency.USD)).errorOrFail()
        )
    }

    @Test
    fun aNegativeOrForeignLimitIsRejected() {
        assertEquals(
            BudgetLimitError.Negative,
            BudgetLimit.finite(Money.cop(-1)).errorOrFail()
        )
        assertEquals(
            BudgetLimitError.UnsupportedCurrency(Currency.USD),
            BudgetLimit.finite(Money(100, Currency.USD)).errorOrFail()
        )
    }
}
