package com.software.sello.data.repository

import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.SearchKey
import com.software.sello.domain.port.MovementQuery
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Incomes have no stored search key: search folds their note and source name as it reads
 * them, by the rule expense notes are stored with (SELLO-193).
 */
class IncomeMatchTest {
    private val names = mapOf("salary" to "Salario", "freelance" to "Freelance")

    private fun <T> Outcome<T, *>.value() = (this as Outcome.Success).value

    private fun income(
        note: String?,
        source: IncomeSource = IncomeSource.Salary,
        pesos: Long = 2_500_000
    ) = Income(
        id = IncomeId.of("00000000-0000-4000-8000-000000000001").value(),
        date = LocalDate.of(2026, 10, 3),
        sequence = 1,
        amount = TransactionAmount.of(Money.cop(pesos)).value(),
        source = source,
        note = Note.of(note).value(),
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun finds(income: Income, typed: String): Boolean {
        val query = MovementQuery.of(typed)!!
        return IncomeMatch.finds(income, SearchKey.of(query.words), query, names)
    }

    @Test
    fun accentsAndCapitalsAreIgnoredEitherWay() {
        assertTrue(finds(income("Rappi reembolso"), "rappí"))
        assertTrue(finds(income("RAPPI reembolso"), "rappí"))
        assertTrue(finds(income("Rappí reembolso"), "RAPPI"))
        assertTrue(finds(income("Préstamo de María"), "prestamo de maria"))
        // An accent written as its own mark folds like the letter that carries it.
        assertTrue(finds(income("Cafe\u0301 vendido"), "café"))
    }

    @Test
    fun wordsMatchAnywhereInTheNote() {
        assertTrue(finds(income("Proyecto web"), "ecto w"))
        assertFalse(finds(income("Proyecto web"), "web proyecto"))
        assertFalse(finds(income(null), "proyecto"))
    }

    @Test
    fun aFixedSourceIsFoundByTheNameTheScreenGivesIt() {
        assertTrue(finds(income(null, IncomeSource.Salary), "salar"))
        assertTrue(finds(income(null, IncomeSource.Freelance), "FREE"))
        // The stored key is not what the person sees, so it finds nothing.
        assertFalse(finds(income(null, IncomeSource.PassiveIncome), "passive"))
        // A source the screen gave no name to is found only by its note.
        assertFalse(finds(income(null, IncomeSource.Transfer), "transfer"))
    }

    @Test
    fun anOtherSourceIsFoundByItsOwnName() {
        val rent = IncomeSource.Other(SourceName.of("Arriendo apartaestudio").value())
        assertTrue(finds(income(null, rent), "APARTAESTUDIO"))
        assertFalse(finds(income(null, rent), "salario"))
    }

    @Test
    fun anAmountQueryAlsoFindsExactlyThatAmount() {
        assertTrue(finds(income(null, IncomeSource.Transfer, 18_000), "18.000"))
        assertTrue(finds(income(null, IncomeSource.Transfer, 18_000), "18000"))
        assertFalse(finds(income(null, IncomeSource.Transfer, 180_000), "18.000"))
        // A badly grouped number is only words: no note holds it here.
        assertFalse(finds(income(null, IncomeSource.Transfer, 18_000), "1.80.00"))
    }

    @Test
    fun anAmountQueryStillMatchesAsWords() {
        assertTrue(
            finds(income("Factura 18.000 de octubre", IncomeSource.Transfer, 5_000), "18.000")
        )
    }

    @Test
    fun aQueryThatFoldsToNothingFindsNothing() {
        // Accent marks alone fold away; they are in every text, so they find nothing.
        assertFalse(finds(income("Salario"), "\u0301"))
    }
}
