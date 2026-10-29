package com.software.sello.navigation

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.testing.pesos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkDraftTest {
    private val id = "3f2c1a9e-7b4d-4c61-9a0e-5d8f2b6c7e10"
    private val category = (CategoryId.of(id) as Outcome.Success).value

    /** One category exists, so an entry has somewhere to go and nothing else is asked for. */
    private val existing = HandCategoryReads().apply { categories = listOf(category(1, "Mercado")) }

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    @Test
    fun aWellFormedLinkPrefillsCategoryAndAmount() {
        assertEquals(
            EntryRequest(category, pesos(48_700)),
            EntryLinks.parse("sello://anotar?categoria=$id&monto=48700")
        )
        assertEquals(
            EntryRequest(category, pesos(1_234)),
            EntryLinks.parse("sello://anotar?monto=1.234&categoria=$id")
        )
        assertEquals(
            EntryRequest(null, pesos(999_999_999_999)),
            EntryLinks.parse("sello://anotar?monto=999999999999")
        )
    }

    @Test
    fun aLinkWithNothingToPrefillStillOpensAnEmptyEntry() {
        assertEquals(EntryRequest(null, null), EntryLinks.parse("sello://anotar"))
        assertEquals(EntryRequest(null, null), EntryLinks.parse("sello://anotar?"))
    }

    @Test
    fun anAmountThatIsNotValidInputIsDroppedNeverRepaired() {
        val amounts = listOf(
            "1e3", "-5", "0", "1000000000000", "12.34", "1,5", "%31%30", "10%20000", "$5000",
            "", "5000%3BDROP%20TABLE%20expense", "5000;", "0x10", "١٢٣", "9".repeat(40)
        )

        for (amount in amounts) {
            assertEquals(
                amount,
                EntryRequest(category, null),
                EntryLinks.parse("sello://anotar?categoria=$id&monto=$amount")
            )
        }
    }

    @Test
    fun aCategoryThatIsNotAnIdentifierIsLeftUnselected() {
        val categories = listOf(
            "Mercado", "1", id.uppercase(), "$id%00", "../../databases/sello.db",
            "%27%20OR%201%3D1%20--", "'OR'1'='1", "", id.dropLast(1),
            "%33f2c1a9e-7b4d-4c61-9a0e-5d8f2b6c7e10"
        )

        for (value in categories) {
            assertEquals(
                value,
                EntryRequest(null, pesos(5_000)),
                EntryLinks.parse("sello://anotar?categoria=$value&monto=5000")
            )
        }
    }

    @Test
    fun aValueGivenTwiceIsAmbiguousAndNotUsed() {
        assertEquals(
            EntryRequest(category, null),
            EntryLinks.parse("sello://anotar?monto=100&monto=999999&categoria=$id")
        )
        assertEquals(
            EntryRequest(null, pesos(100)),
            EntryLinks.parse("sello://anotar?categoria=$id&categoria=$id&monto=100")
        )
    }

    @Test
    fun unknownParametersCarryNothingIntoTheRequest() {
        assertEquals(
            EntryRequest(null, pesos(100)),
            EntryLinks.parse("sello://anotar?monto=100&guardar=true&resultado=ok&nota=hola")
        )
    }

    @Test
    fun anythingThatIsNotExactlyAnEntryLinkIsNotALinkAtAll() {
        val others = listOf(
            null,
            "",
            "https://anotar?monto=100",
            "SELLO://anotar?monto=100",
            "sello://ANOTAR?monto=100",
            "sello://borrar?monto=100",
            "sello://anotar/extra?monto=100",
            "sello://anotar/?monto=100",
            "sello://evil@anotar?monto=100",
            "sello://anotar:8080?monto=100",
            "sello://anotar?monto=100#fragmento",
            "sello:anotar?monto=100",
            "sello:///anotar?monto=100",
            "financetracker://add-expense",
            // Not a valid address at all, so not a link: spaces and control characters.
            "sello://anotar?monto=1 00",
            "sello://anotar?categoria=' OR 1=1 --",
            "sello://anotar?monto=5000\n&guardar=1",
            " sello://anotar?monto=100",
            "javascript:alert(1)",
            "content://com.software.sello/databases/sello.db",
            "sello://anotar?monto=" + "1".repeat(600)
        )

        for (link in others) assertNull(link, EntryLinks.parse(link))
    }

    @Test
    fun aLinkBecomesAPendingRequestAndNothingElse() {
        val session = MonthSession(HandFinancialClock("2026-10-09"), HandMonotonicClock())
        val shell = ShellViewModel(SavedStateHandle(), session, existing)
        val before = shell.state.value

        shell.onAction(ShellAction.OpenLink("sello://anotar?categoria=$id&monto=48700"))

        // A category exists, so the entry form may open with this request; nothing else changed.
        assertEquals(
            before.copy(pendingEntry = EntryRequest(category, pesos(48_700)), entryReady = true),
            shell.state.value
        )
    }

    @Test
    fun aLaterLinkReplacesTheRequestAndAForeignLinkLeavesItAlone() {
        val session = MonthSession(HandFinancialClock("2026-10-09"), HandMonotonicClock())
        val shell = ShellViewModel(SavedStateHandle(), session, existing)
        shell.onAction(ShellAction.OpenLink("sello://anotar?monto=100"))

        shell.onAction(ShellAction.OpenLink("https://example.com/?monto=999"))
        val afterForeign = shell.state.value.pendingEntry
        shell.onAction(ShellAction.OpenLink("sello://anotar?monto=200"))
        val afterSecond = shell.state.value.pendingEntry
        shell.onAction(ShellAction.EntryTaken)

        assertEquals(EntryRequest(null, pesos(100)), afterForeign)
        assertEquals(EntryRequest(null, pesos(200)), afterSecond)
        assertNull(shell.state.value.pendingEntry)
    }
}
