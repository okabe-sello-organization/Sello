package com.software.sello.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.openSelloDatabase
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CatchUpNoticePreferences
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.rules.ExternalResource

/** Real database files in the test app's private storage, closed and removed afterwards. */
class DatabaseFiles : ExternalResource() {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val name = "test-${UUID.randomUUID()}.db"
    val file: File get() = context.getDatabasePath(name)

    /** This test's own restore staging folder, removed afterwards. */
    val restoreFolder: File get() = File(context.filesDir, name.removeSuffix(".db") + "-restore")

    /** This test's own reset folder, removed afterwards. */
    val resetFolder: File get() = File(context.filesDir, name.removeSuffix(".db") + "-reset")
    private val opened = mutableListOf<SelloDatabase>()

    /** Each call is a fresh handle on the same file, as after a process restart. */
    fun open(): SelloDatabase =
        openSelloDatabase(context, Dispatchers.IO, name).also { opened += it }

    override fun after() {
        opened.forEach { if (it.isOpen) it.close() }
        context.deleteDatabase(name)
        restoreFolder.deleteRecursively()
        resetFolder.deleteRecursively()
    }
}

fun <T> Outcome<T, *>.valueOrFail(): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> throw AssertionError("expected a value, got failure $error")
}

/** A whole-peso amount as a record carries it. */
fun transactionPesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

/** Real threads for storage under test; "main" is a worker too, so no looper is needed. */
val realDispatchers: DispatcherProvider = object : DispatcherProvider {
    override val main = Dispatchers.Default
    override val default = Dispatchers.Default
    override val io = Dispatchers.IO
}

/** Writes a row exactly as given, bypassing every mapper, the way damage or a bug would. */
fun SelloDatabase.insertRaw(table: String, vararg columns: Pair<String, Any?>) {
    val values = ContentValues()
    for ((column, value) in columns) {
        when (value) {
            null -> values.putNull(column)
            is String -> values.put(column, value)
            is Long -> values.put(column, value)
            is Int -> values.put(column, value.toLong())
            else -> error("unsupported raw value for $column")
        }
    }
    openHelper.writableDatabase.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
}

fun SelloDatabase.count(table: String): Long =
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        it.moveToFirst()
        it.getLong(0)
    }

/** Synthetic identifiers: readable in a failure and canonical. */
fun uuid(number: Int): String = "00000000-0000-4000-8000-%012d".format(number)

val recordedAt: Instant = Instant.parse("2026-10-09T15:04:05.678Z")

fun category(number: Int, name: String, archived: Boolean = false) = Category(
    id = CategoryId.of(uuid(number)).valueOrFail(),
    name = CategoryName.of(name).valueOrFail(),
    icon = IconKey.of("shopping_cart").valueOrFail(),
    archived = archived,
    version = 1,
    createdAt = recordedAt,
    updatedAt = recordedAt
)

fun expense(
    number: Int,
    categoryNumber: Int,
    date: String,
    sequence: Long,
    pesos: Long,
    note: String? = null
) = Expense(
    id = ExpenseId.of(uuid(number)).valueOrFail(),
    categoryId = CategoryId.of(uuid(categoryNumber)).valueOrFail(),
    date = LocalDate.parse(date),
    sequence = sequence,
    amount = TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
    note = Note.of(note).valueOrFail(),
    version = 1,
    createdAt = recordedAt,
    updatedAt = recordedAt
)

fun income(number: Int, date: String, sequence: Long, pesos: Long, source: IncomeSource) = Income(
    id = IncomeId.of(uuid(number)).valueOrFail(),
    date = LocalDate.parse(date),
    sequence = sequence,
    amount = TransactionAmount.of(Money.cop(pesos)).valueOrFail(),
    source = source,
    note = null,
    version = 1,
    createdAt = recordedAt,
    updatedAt = recordedAt
)

fun finite(pesos: Long): BudgetLimit = BudgetLimit.finite(Money.cop(pesos)).valueOrFail()

/** When the catch-up notice was last dismissed, as a test sets it; [fail] refuses writes. */
class NoticeSeen : CatchUpNoticePreferences {
    var seen: Instant? = null
    var fail = false
    val marks = mutableListOf<Instant>()

    override fun observeCatchUpNoticeSeen(): Flow<PreferenceRead<Instant?>> =
        flowOf(PreferenceRead.Ready(seen))

    override suspend fun markCatchUpNoticeSeen(at: Instant): PreferenceWrite {
        if (fail) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        marks += at
        seen = at
        return PreferenceWrite.Saved
    }
}

/** Inversiones' settings as a test sets them; [fail] refuses writes. */
class HandInvestmentPreferences(var value: InvestmentPreference = InvestmentPreference()) :
    InvestmentPreferences {
    var fail = false

    override fun observeInvestments(): Flow<PreferenceRead<InvestmentPreference>> =
        flowOf(PreferenceRead.Ready(value))

    override suspend fun setNewInvestmentCurrency(currency: Currency): PreferenceWrite {
        if (fail) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        value = value.copy(newCurrency = currency)
        return PreferenceWrite.Saved
    }

    override suspend fun setInvestmentOrder(order: InvestmentOrder): PreferenceWrite {
        if (fail) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        value = value.copy(order = order)
        return PreferenceWrite.Saved
    }
}
