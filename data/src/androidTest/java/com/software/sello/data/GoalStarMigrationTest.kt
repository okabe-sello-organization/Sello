package com.software.sello.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.data.local.Migration7To8
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.openSelloDatabase
import com.software.sello.data.repository.RoomGoalCommands
import com.software.sello.data.repository.RoomGoalReads
import com.software.sello.data.repository.UndoWindow
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.StarGoal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrading a version 7 history to version 8, where `featured_goal`'s one chosen goal
 * becomes a row of `starred_goal` (SELLO-143). The history has two goals: Viaje
 * (…012, active) and Celular (…016, completed), each with its target, and the receipt of
 * the command that chose Viaje, as version 7 stored it.
 */
@RunWith(AndroidJUnit4::class)
class GoalStarMigrationTest {
    private val name = "goal-star-migration.db"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, SelloDatabase::class.java)

    @After
    fun removeFile() {
        instrumentation.targetContext.deleteDatabase(name)
    }

    private val viaje = uuid(12)
    private val celular = uuid(16)

    /**
     * The identity of choosing Viaje at generation 4, worked out by hand from the stored
     * format (kind `goal.feature`, format 1, the generation, then the goal), each field
     * as its UTF-8 length, a colon and its bytes, hashed with SHA-256.
     */
    private val choseViajeDigest =
        "55912325a11ddc7bc9926d5a338a85145d8abfcee77d7e0fe061325880c3e75f"

    /** Every version 7 table this history writes to, but the one version 8 replaces. */
    private val keptTables = listOf("profile", "goal", "goal_event", "operation_receipt")

    private fun SupportSQLiteDatabase.rows(sql: String) = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    (0 until cursor.columnCount).joinToString("|") {
                        cursor.getString(it) ?: "null"
                    }
                )
            }
        }
    }

    private fun SupportSQLiteDatabase.kept() =
        keptTables.associateWith { rows("SELECT * FROM $it ORDER BY 1") }

    /** The history as version 7 stored it, with [chosen] in `featured_goal`, if any. */
    private fun versionSeven(chosen: String?): Map<String, List<String>> =
        helper.createDatabase(name, 7).use { db ->
            db.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 19, 0)"
            )
            db.execSQL(
                "INSERT INTO goal VALUES ('$viaje', 'Viaje', 'flight', NULL, NULL, 1, 1, 30, 40)"
            )
            db.execSQL(
                "INSERT INTO goal_event VALUES ('${uuid(13)}', '$viaje', 0, 'target', " +
                    "3000000, 'COP', NULL, '2027-01-02', NULL, 1, 30, 40)"
            )
            db.execSQL(
                "INSERT INTO goal VALUES ('$celular', 'Celular', 'flight', NULL, " +
                    "'2027-03-01', 1, 2, 31, 41)"
            )
            db.execSQL(
                "INSERT INTO goal_event VALUES ('${uuid(17)}', '$celular', 0, 'target', " +
                    "1200000, 'COP', NULL, '2027-01-02', NULL, 1, 31, 41)"
            )
            db.execSQL(
                "INSERT INTO operation_receipt VALUES ('${uuid(9)}', 'goal.feature', " +
                    "'$choseViajeDigest', 4, 17, '$viaje', 50)"
            )
            chosen?.let { db.execSQL("INSERT INTO featured_goal VALUES (1, '$it')") }
            db.kept()
        }

    private fun upgraded(check: (SupportSQLiteDatabase) -> Unit) =
        // Room compares the result with the exported version 8 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 8, true, Migration7To8).use(check)

    @Test
    fun theChosenGoalStaysStarredAndEveryOtherRowIsKept() {
        val before = versionSeven(chosen = viaje)

        upgraded { db ->
            assertEquals(listOf(viaje), db.rows("SELECT goal_id FROM starred_goal"))
            assertEquals(before, db.kept())
            assertEquals(
                emptyList<String>(),
                db.rows("SELECT name FROM sqlite_master WHERE name LIKE '%featured_goal%'")
            )
        }
    }

    @Test
    fun aChosenGoalThatIsCompletedIsNotStarred() {
        val before = versionSeven(chosen = celular)

        upgraded { db ->
            assertEquals(emptyList<String>(), db.rows("SELECT goal_id FROM starred_goal"))
            // The goal itself and its completion are kept.
            assertEquals(before, db.kept())
        }
    }

    @Test
    fun noChoiceMeansNoStar() {
        versionSeven(chosen = null)

        upgraded { db ->
            assertEquals(emptyList<String>(), db.rows("SELECT goal_id FROM starred_goal"))
        }
    }

    @Test
    fun anUpgradedHistoryShowsItsStarFirstAndItsOldChoiceStillReplays() = runBlocking {
        versionSeven(chosen = viaje)
        val day = MutableStateFlow(FinancialDay(LocalDate.parse("2027-04-10"), ZoneId.of("UTC")))
        val clock = object : FinancialClock {
            override val today = day
        }

        val database = openSelloDatabase(instrumentation.targetContext, Dispatchers.IO, name)
        val version = database.openHelper.readableDatabase.version
        val board = RoomGoalReads(database).board().valueOrFail()
        val goals = RoomGoalCommands(
            database,
            { Instant.parse("2027-04-10T15:00:00Z") },
            clock,
            { error("A replay takes no identifier") },
            UndoWindow { 5.hours }
        ) { 5.hours }
        val replayed = goals.submit(
            StarGoal(operationIdOf(9), 4, GoalId.of(viaje).valueOrFail())
        )
        database.close()

        assertEquals(9, version)
        assertEquals(setOf(GoalId.of(viaje).valueOrFail()), board.starred)
        assertEquals(listOf(GoalId.of(viaje).valueOrFail()), board.reciboGoals)
        // The receipt version 7 stored for choosing Viaje answers the star command unchanged.
        assertEquals(
            CommandOutcome.Committed(
                CommittedGoal(
                    OperationReceipt(
                        operationIdOf(9),
                        OperationKind.StarGoal,
                        generation = 4,
                        revision = 17,
                        committedAt = Instant.ofEpochMilli(50)
                    ),
                    GoalId.of(viaje).valueOrFail(),
                    null
                ),
                replayed = true
            ),
            replayed
        )
    }
}
