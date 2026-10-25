package com.software.sello.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.mapper.toHistory
import com.software.sello.data.repository.RoomFinancialProfileStore
import com.software.sello.data.repository.RoomInvestmentReads
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.InvestmentPolicy
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Investment rows written straight into the real database and read back through the
 * real queries and mappers. The figures are approved example IN04 (Fondo,
 * docs/testing/investment-examples.md); the rate is the examples' 4.150,25.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentStorageTest {
    @get:Rule
    val files = DatabaseFiles()

    private val millis = recordedAt.toEpochMilli()
    private val fondo = uuid(1)

    private fun SelloDatabase.investment(
        nextSequence: Long = 5,
        name: String = "Fondo",
        description: String? = "Fiducuenta",
        currency: String = "COP"
    ) = insertRaw(
        "investment",
        "id" to fondo,
        "name" to name,
        "description" to description,
        "icon_key" to "trending_up",
        "currency" to currency,
        "next_sequence" to nextSequence,
        "version" to 1L,
        "created_at" to millis,
        "updated_at" to millis
    )

    private fun SelloDatabase.movement(
        number: Int,
        sequence: Long,
        kind: String,
        minor: Long,
        date: String,
        note: String? = null,
        currency: String = "COP"
    ) = insertRaw(
        "investment_movement",
        "id" to uuid(number),
        "investment_id" to fondo,
        "sequence" to sequence,
        "kind" to kind,
        "amount_minor" to minor,
        "currency" to currency,
        "effective_date" to date,
        "note" to note,
        "version" to 1L,
        "created_at" to millis,
        "updated_at" to millis
    )

    private fun SelloDatabase.rate(number: Int, hundredths: Long, date: String, sequence: Long) =
        insertRaw(
            "usd_rate",
            "id" to uuid(number),
            "rate_hundredths" to hundredths,
            "effective_date" to date,
            "sequence" to sequence,
            "created_at" to millis
        )

    private suspend fun SelloDatabase.history() =
        investmentDao().byId(fondo)!!.toHistory(investmentDao().movements(fondo))

    /** IN04 as rows; the value comes first in recording order, which replay must not use. */
    private fun SelloDatabase.in04() {
        investment()
        movement(10, 0, "contribution", 3_000_000, "2025-05-03")
        movement(11, 1, "valuation", 3_000_000, "2025-05-03")
        movement(12, 2, "contribution", 3_000_000, "2025-11-03")
        movement(13, 3, "withdrawal", 500_000, "2026-08-03", note = "Arreglo")
        movement(14, 4, "valuation", 5_242_000, "2026-10-14")
    }

    @Test
    fun in04AStoredHistoryReadsBackToTheApprovedFigures() = runBlocking {
        val database = files.open()
        database.in04()

        val figures = InvestmentPolicy.figures(database.history().valueOrFail()).valueOrFail()

        // IN04: G −258.000, −4,3 %, Pierde, desde may 2025.
        assertEquals(Money.cop(6_000_000), figures.contributed)
        assertEquals(Money.cop(500_000), figures.withdrawn)
        assertEquals(Money.cop(5_242_000), figures.value?.amount)
        assertEquals(Money.cop(-258_000), figures.gain)
        assertEquals(InvestmentReturn(GainDirection.Loss, 43), figures.`return`)
        assertEquals(YearMonth.of(2025, 5), figures.since)
        assertEquals(5, figures.movements)
    }

    @Test
    fun aRateIsStoredAsExactHundredthsAndReadsBackTheSame() = runBlocking {
        val database = files.open()
        val entry = UsdRateEntry(
            UsdRateId.of(uuid(20)).valueOrFail(),
            rate(415_025),
            day("2026-10-20"),
            0,
            recordedAt
        )

        database.investmentDao().insertRate(entry.toEntity())

        // 4.150,25 pesos per dollar is the integer 415025, never a REAL.
        assertEquals(
            listOf("415025|integer"),
            database.openHelper.readableDatabase
                .query("SELECT rate_hundredths, typeof(rate_hundredths) FROM usd_rate")
                .use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                "${cursor.getLong(0)}|${cursor.getString(1)}"
                            )
                        }
                    }
                }
        )
        assertEquals(
            listOf(entry),
            database.investmentDao().rates().map {
                it.toDomain().valueOrFail()
            }
        )
        assertEquals("4150.25", entry.rate.pesos.toPlainString())
    }

    @Test
    fun aRateOutsideItsBoundsIsDamageNotClamped() = runBlocking {
        val database = files.open()
        for (hundredths in listOf(99L, 10_000_001L)) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM usd_rate")
            database.rate(20, hundredths, "2026-10-20", 0)

            assertEquals(
                Outcome.Failure(StorageFailure.Integrity("usd_rate", uuid(20), "rate_hundredths")),
                database.investmentDao().rates().single().toDomain()
            )
        }
    }

    @Test
    fun aDamagedInvestmentOrMovementIsAnIntegrityFailureNamingItsColumn() = runBlocking {
        fun damaged(record: String, key: String, field: String) =
            Outcome.Failure(StorageFailure.Integrity(record, key, field))
        val movement = "investment_movement"
        val cases = listOf<Pair<(SelloDatabase) -> Unit, Outcome<*, *>>>(
            { db: SelloDatabase -> db.movement(10, 0, "gift", 1, "2026-10-01") } to
                damaged(movement, uuid(10), "kind"),
            { db: SelloDatabase -> db.movement(10, 0, "contribution", 0, "2026-10-01") } to
                damaged(movement, uuid(10), "amount_minor"),
            { db: SelloDatabase -> db.movement(10, 0, "dividend", 0, "2026-10-01") } to
                damaged(movement, uuid(10), "amount_minor"),
            { db: SelloDatabase ->
                db.movement(10, 0, "contribution", 1_000_000_000_000, "2026-10-01")
            } to damaged(movement, uuid(10), "amount_minor"),
            { db: SelloDatabase -> db.movement(10, 0, "valuation", -1, "2026-10-01") } to
                damaged(movement, uuid(10), "amount_minor"),
            { db: SelloDatabase ->
                db.movement(10, 0, "contribution", 1, "2026-10-01", currency = "USD")
            } to damaged(movement, uuid(10), "currency"),
            { db: SelloDatabase ->
                db.movement(10, 0, "contribution", 1, "2026-10-01", note = " Prima")
            } to damaged(movement, uuid(10), "note"),
            { db: SelloDatabase -> db.movement(10, 0, "contribution", 1, "2026-02-30") } to
                damaged(movement, uuid(10), "effective_date"),
            { db: SelloDatabase -> db.movement(10, 5, "contribution", 1, "2026-10-01") } to
                damaged(movement, uuid(10), "sequence")
        )
        val database = files.open()
        for ((write, expected) in cases) {
            database.investment()
            write(database)
            assertEquals(expected, database.history())
            database.openHelper.writableDatabase.execSQL("DELETE FROM investment_movement")
            database.openHelper.writableDatabase.execSQL("DELETE FROM investment")
        }
    }

    @Test
    fun anInvestmentWhoseTextOrCurrencyIsNotWhatTheRulesStoreIsDamage() = runBlocking {
        val database = files.open()
        val cases = listOf(
            Triple(" Fondo", "Fiducuenta", "COP") to "name",
            Triple("Fondo", " ", "COP") to "description",
            Triple("Fondo", "Fiducuenta", "EUR") to "currency",
            Triple("Fondo", "Fiducuenta", "cop") to "currency"
        )
        for ((columns, field) in cases) {
            database.investment(
                name = columns.first,
                description = columns.second,
                currency = columns.third
            )

            assertEquals(
                Outcome.Failure(StorageFailure.Integrity("investment", fondo, field)),
                database.history()
            )
            database.openHelper.writableDatabase.execSQL("DELETE FROM investment")
        }
    }

    @Test
    fun aUsdInvestmentKeepsItsAmountsInCents() = runBlocking {
        val database = files.open()
        database.investment(nextSequence = 2, currency = "USD")
        database.movement(10, 0, "contribution", 100_000, "2026-10-20", currency = "USD")
        database.movement(11, 1, "valuation", 105_050, "2026-10-20", currency = "USD")

        val figures = InvestmentPolicy.figures(database.history().valueOrFail()).valueOrFail()

        // IN27: G +USD 50,50, +5,1 %.
        assertEquals(Money(5_050, Currency.USD), figures.gain)
        assertEquals(InvestmentReturn(GainDirection.Gain, 51), figures.`return`)
    }

    @Test
    fun aHistoryTheRulesRefuseFailsTheWholeBoardNeverLeftOut() = runBlocking {
        val database = files.open()
        RoomFinancialProfileStore(database) { recordedAt }.establish(ZoneId.of("America/Bogota"))
        database.in04()
        // A retiro of more than the 6.000.000 the Fondo was worth on 3 August.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE investment_movement SET amount_minor = 6000001 WHERE id = '${uuid(13)}'"
        )

        val read = RoomInvestmentReads(database).board()

        assertEquals(
            StorageFailure.Integrity("investment_movement", uuid(13), "amount_minor"),
            read.failureOrNull()
        )
    }

    @Test
    fun twoMovementsCannotShareAPositionAndAnInvestmentWithMovementsCannotBeRemoved() {
        val database = files.open()
        database.investment()
        database.movement(10, 0, "contribution", 1, "2026-10-01")
        database.rate(20, 415_025, "2026-10-20", 0)

        assertThrows(SQLiteConstraintException::class.java) {
            database.movement(11, 0, "valuation", 1, "2026-10-01")
        }
        assertThrows(SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM investment")
        }
        assertThrows(SQLiteConstraintException::class.java) {
            database.rate(21, 400_000, "2026-10-24", 0)
        }
    }
}
