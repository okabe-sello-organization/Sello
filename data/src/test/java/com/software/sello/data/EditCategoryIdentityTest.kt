package com.software.sello.data

import com.software.sello.data.repository.correctionIdentity
import com.software.sello.data.repository.inputDigest
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CarrySetting
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.EditCategory
import com.software.sello.domain.port.LimitEdit
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The category form's one save (SELLO-188) replays only with the very same input: every
 * part is a slot of its own, so changing, adding or leaving out any part is another input.
 */
class EditCategoryIdentityTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private val nov = YearMonth.of(2026, 11)
    private val edit = EditCategory(
        operationId = OperationId.of(id(9)).value(),
        generation = 4,
        categoryId = CategoryId.of(id(1)).value(),
        expectedVersion = 3,
        month = nov,
        name = CategoryName.of("Mercado").value(),
        icon = IconKey.of("cart").value(),
        limit = LimitEdit.FromThisMonth(BudgetLimit.finite(Money.cop(200_000)).value()),
        carry = CarrySetting.Off,
        confirmedCarryIn = Money.cop(-20_000)
    )

    @Test
    fun theSameInputIsTheSameIdentityWhateverTheOperation() {
        val again = edit.copy(operationId = OperationId.of(id(8)).value())
        assertEquals(inputDigest(edit), inputDigest(again))
    }

    @Test
    fun everyPartChangesTheIdentity() {
        val finite = BudgetLimit.finite(Money.cop(200_000)).value()
        val variants = listOf(
            edit,
            edit.copy(generation = 5),
            edit.copy(expectedVersion = 4),
            edit.copy(month = nov.plusMonths(1)),
            edit.copy(name = null),
            edit.copy(name = CategoryName.of("Mercado 2").value()),
            edit.copy(icon = null),
            edit.copy(icon = IconKey.of("coffee").value()),
            edit.copy(limit = null),
            edit.copy(limit = LimitEdit.FromThisMonth(BudgetLimit.Unlimited)),
            edit.copy(limit = LimitEdit.FromThisMonth(BudgetLimit.finite(Money.cop(0)).value())),
            edit.copy(limit = LimitEdit.OfMonth(nov.minusMonths(1), finite)),
            edit.copy(limit = LimitEdit.OfMonth(nov.minusMonths(2), finite)),
            edit.copy(carry = null),
            edit.copy(carry = CarrySetting.On),
            edit.copy(confirmedCarryIn = null),
            edit.copy(confirmedCarryIn = Money.cop(20_000)),
            edit.copy(confirmation = CorrectionConfirmation(7, "abc"))
        )
        val digests = variants.map(::inputDigest)
        assertEquals(variants.size, digests.toSet().size)
    }

    @Test
    fun theCorrectionIdentityLeavesOnlyTheConfirmationOut() {
        val confirmed = edit.copy(confirmation = CorrectionConfirmation(7, "abc"))
        assertEquals(correctionIdentity(edit), correctionIdentity(confirmed))
        assertEquals(inputDigest(edit), correctionIdentity(confirmed))
    }

    /** Printed by this code when the kind was added; a stored receipt must keep replaying. */
    @Test
    fun theIdentityStaysWhatItWasWhenTheKindWasAdded() {
        assertEquals(PINNED, inputDigest(edit))
    }

    private companion object {
        const val PINNED = "cd529fb15f1f37061573a3df00d9e61c8bd3cfc423eb39bee8cd8405331c9932"
    }
}
