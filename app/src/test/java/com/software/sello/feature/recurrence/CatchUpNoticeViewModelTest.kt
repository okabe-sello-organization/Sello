package com.software.sello.feature.recurrence

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.ScheduleRecordReads
import com.software.sello.domain.port.ScheduleRecordsState
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.storedIncome
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** When it was last dismissed, as the preference store keeps it. */
private class HandCatchUpPreferences : CatchUpNoticePreferences {
    val seen = MutableStateFlow<PreferenceRead<Instant?>>(PreferenceRead.Ready(null))
    val marked = mutableListOf<Instant>()
    var answer: PreferenceWrite = PreferenceWrite.Saved

    override fun observeCatchUpNoticeSeen(): Flow<PreferenceRead<Instant?>> = seen

    override suspend fun markCatchUpNoticeSeen(at: Instant): PreferenceWrite {
        marked += at
        if (answer == PreferenceWrite.Saved) seen.value = PreferenceRead.Ready(at)
        return answer
    }
}

/** Entries with an origin, as storage keeps them; [failure] makes the read fail. */
private class HandRecords : ScheduleRecordReads {
    val entries = MutableStateFlow<List<ScheduleRecord>>(emptyList())
    var failure: StorageFailure? = null
    val asked = mutableListOf<Instant?>()

    private fun created(record: ScheduleRecord) = when (record) {
        is ScheduleRecord.Payment -> record.expense.createdAt
        is ScheduleRecord.Earning -> record.income.createdAt
    }

    override suspend fun recordedAfter(since: Instant?) =
        Outcome.Success(entries.value.filter { since == null || created(it) > since })

    override fun observeRecordedAfter(since: Instant?): Flow<ScheduleRecordsState> {
        asked += since
        return entries.map { all ->
            failure?.let { ScheduleRecordsState.Failed(it) }
                ?: ScheduleRecordsState.Ready(
                    all.filter { since == null || created(it) > since }
                )
        }
    }
}

/**
 * The notice after catching up on jue 22 oct 2026 (recurrence-examples rule 14,
 * docs/design/v2/fijos.html): everything recorded since the last Listo, named by note,
 * else category or source, until Listo is pressed and kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatchUpNoticeViewModelTest {
    private val preferences = HandCatchUpPreferences()
    private val records = HandRecords()
    private val categories = HandCategoryReads().apply {
        categories = listOf(category(1, "Hogar"), category(2, "Transporte", icon = "bus"))
    }
    private val clock = HandFinancialClock("2026-10-22")
    private var auditNow = Instant.parse("2026-10-22T15:00:00Z")
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model() =
        CatchUpNoticeViewModel(preferences, records, categories, clock, { auditNow }, money)

    private fun rent(createdAt: String) = ScheduleRecord.Payment(
        storedExpense(50, 1, "2026-10-05", 950_000, "Arriendo")
            .copy(createdAt = Instant.parse(createdAt))
    )

    private fun bus(number: Int, date: String, createdAt: String) = ScheduleRecord.Payment(
        storedExpense(number, 2, date, 20_000).copy(createdAt = Instant.parse(createdAt))
    )

    private fun freelance(createdAt: String) = ScheduleRecord.Earning(
        storedIncome(
            60,
            "2026-10-15",
            250_000,
            IncomeSource.Other((SourceName.of("Diseño") as Outcome.Success).value)
        ).copy(createdAt = Instant.parse(createdAt))
    )

    @Test
    fun everyRecordedEntryIsListedNamedByNoteElseCategoryElseSource() {
        records.entries.value = listOf(
            rent("2026-10-22T14:00:00Z"),
            bus(51, "2026-10-10", "2026-10-22T14:00:00Z"),
            freelance("2026-10-22T14:00:00Z")
        )
        val model = model()

        val ready = model.state.value as CatchUpNoticeState.Ready
        assertEquals(
            listOf(
                listOf("Arriendo", "Hogar", "2026-10-05", "950.000 pesos", "false"),
                listOf("", "Transporte", "2026-10-10", "20.000 pesos", "false"),
                listOf("Diseño", "", "2026-10-15", "250.000 pesos", "true")
            ),
            ready.items.map {
                listOf(
                    it.note ?: it.sourceName.orEmpty(),
                    it.categoryName.orEmpty(),
                    it.date.toString(),
                    it.amount.spoken,
                    it.income.toString()
                )
            }
        )
        assertEquals(2, ready.payments)
        assertEquals(1, ready.incomes)
        assertEquals(LocalDate.parse("2026-10-22"), ready.today)
        assertEquals(listOf<Instant?>(null), records.asked)
    }

    @Test
    fun listoKeepsWhenItWasSeenThenOnlyWhatIsRecordedLaterShows() {
        records.entries.value = listOf(rent("2026-10-22T14:00:00Z"))
        val model = model()

        model.done()

        assertEquals(listOf(Instant.parse("2026-10-22T15:00:00Z")), preferences.marked)
        assertTrue(model.closed.value)
        assertEquals(
            CatchUpNoticeState.Ready(emptyList(), LocalDate.parse("2026-10-22")),
            model.state.value
        )
        // A week later, opening again: the bus rides caught up after Listo show, rent does not.
        records.entries.value = listOf(
            rent("2026-10-22T14:00:00Z"),
            bus(52, "2026-10-24", "2026-10-29T08:00:00Z"),
            bus(53, "2026-10-28", "2026-10-29T08:00:00Z")
        )
        val reopened = model()
        assertEquals(
            listOf(LocalDate.parse("2026-10-24"), LocalDate.parse("2026-10-28")),
            (reopened.state.value as CatchUpNoticeState.Ready).items.map { it.date }
        )
        assertFalse(reopened.closed.value)
    }

    /** RE38: 2 recorded at opening, not dismissed; the day changes and 1 more: 3 listed. */
    @Test
    fun re38whatIsNotDismissedAddsUpAcrossRunsUntilListo() {
        records.entries.value = listOf(
            bus(51, "2026-10-21", "2026-10-22T14:00:00Z"),
            bus(52, "2026-10-22", "2026-10-22T14:00:00Z")
        )
        val model = model()
        assertEquals(2, (model.state.value as CatchUpNoticeState.Ready).items.size)

        clock.goTo("2026-10-23")
        records.entries.value += bus(53, "2026-10-23", "2026-10-23T00:00:01Z")
        assertEquals(3, (model.state.value as CatchUpNoticeState.Ready).items.size)

        auditNow = Instant.parse("2026-10-23T09:00:00Z")
        model.done()
        assertEquals(
            emptyList<RecordedItem>(),
            (model.state.value as CatchUpNoticeState.Ready).items
        )
    }

    @Test
    fun aListoThatCannotBeKeptLeavesTheNoticeAndTheList() {
        records.entries.value = listOf(rent("2026-10-22T14:00:00Z"))
        preferences.answer = PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        val model = model()

        model.done()

        assertFalse(model.closed.value)
        assertEquals(1, (model.state.value as CatchUpNoticeState.Ready).items.size)
    }

    @Test
    fun anEntryDeletedMeanwhileLeavesTheListAndTheOthersStay() {
        records.entries.value = listOf(
            rent("2026-10-22T14:00:00Z"),
            bus(51, "2026-10-10", "2026-10-22T14:00:00Z")
        )
        val model = model()

        records.entries.value = listOf(bus(51, "2026-10-10", "2026-10-22T14:00:00Z"))

        assertEquals(
            listOf("Transporte"),
            (model.state.value as CatchUpNoticeState.Ready).items.map { it.categoryName }
        )
        records.entries.value = emptyList()
        assertEquals(
            CatchUpNoticeState.Ready(emptyList(), LocalDate.parse("2026-10-22")),
            model.state.value
        )
    }

    @Test
    fun aFailedReadIsFailedNeverNothingRecordedAndRetryReadsAgain() {
        records.entries.value = listOf(rent("2026-10-22T14:00:00Z"))
        records.failure = StorageFailure.Unavailable("SQLException")
        val model = model()
        assertEquals(CatchUpNoticeState.Failed, model.state.value)

        records.failure = null
        model.retry()
        assertEquals(1, (model.state.value as CatchUpNoticeState.Ready).items.size)

        categories.failure = StorageFailure.Unavailable("SQLException")
        model.retry()
        assertEquals(CatchUpNoticeState.Failed, model.state.value)

        categories.failure = null
        preferences.seen.value =
            PreferenceRead.Failed(PreferenceFailure.Unavailable, null)
        assertEquals(CatchUpNoticeState.Failed, model.state.value)
    }
}
