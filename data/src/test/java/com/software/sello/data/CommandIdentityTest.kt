package com.software.sello.data

import com.software.sello.data.repository.inputDigest
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * A receipt answers a replay only while the command's identity stays what it was when the
 * receipt was stored. Each digest below was printed by the code on `main` at 792b247, before
 * signed carryover added its confirmations, for the same command; none is worked out here.
 */
class CommandIdentityTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private val operation = OperationId.of(id(9)).value()
    private val category = CategoryId.of(id(1)).value()
    private val expense = ExpenseId.of(id(2)).value()
    private val amount = TransactionAmount.of(Money.cop(20_000)).value()
    private val ene = LocalDate.parse("2027-01-10")

    private val delete = DeleteExpense(operation, 4, expense, 3)
    private val month =
        SetMonthLimit(operation, 4, category, 3, YearMonth.of(2027, 2), BudgetLimit.Unlimited)

    @Test
    fun aCommandWithoutAConfirmationKeepsTheIdentityItHadBeforeCarry() {
        assertEquals(
            listOf(
                "f9637361ad0b0c1215ac58986b48c9bab890d99d43908a047449b35a0c270ae1",
                "9437c194e451096f69d1f702a6d1bd8ceed2f79fbb0f505155fff13318872369",
                "3dae58fabae43c6a46818a1732819dbb626d323050b54778b654e2df01130dd3",
                "fe94215f81dd79f944788ae54ce76ecddb886a573996fb64be496f8b957681d1",
                "127e6df3624d2f645b1549f3d090c756b8b640b392eecdcbd11244c0bfa62d0c",
                "e0fabb58e62790ae475f1653ebf6c1d737e61787e18cba45e5269a688ce096a5",
                "25ea5c615ac7e38f3199ba963306f09e1442b5c7585cac09ce97edf3ad60415a"
            ),
            listOf(
                inputDigest(
                    CreateExpense(operation, 4, category, amount, ene, Note.of(null).value())
                ),
                inputDigest(
                    EditExpense(
                        operation,
                        4,
                        expense,
                        3,
                        category,
                        amount,
                        ene,
                        Note.of("Pan").value()
                    )
                ),
                inputDigest(delete),
                inputDigest(RestoreExpense(operation, 4, OperationId.of(id(8)).value())),
                inputDigest(ArchiveCategory(operation, 4, category, 3)),
                inputDigest(
                    SetDefaultLimit(
                        operation,
                        4,
                        category,
                        3,
                        BudgetLimit.finite(Money.cop(100_000)).value()
                    )
                ),
                inputDigest(month)
            )
        )
    }

    @Test
    fun aConfirmationIsPartOfTheIdentityOfTheCommandThatCarriesIt() {
        val confirmed =
            CorrectionConfirmation(
                17,
                "3dae58fabae43c6a46818a1732819dbb626d323050b54778b654e2df01130dd3"
            )

        assertNotEquals(inputDigest(delete), inputDigest(delete.copy(confirmation = confirmed)))
        assertNotEquals(
            inputDigest(delete.copy(confirmation = confirmed)),
            inputDigest(delete.copy(confirmation = confirmed.copy(revision = 18)))
        )
        assertNotEquals(
            inputDigest(month),
            inputDigest(month.copy(confirmedCarryIn = Money.cop(0)))
        )
    }
}
