package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupExport
import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.data.backup.RoomRestores
import com.software.sello.data.reset.RoomResets
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.RestoreOutcome
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Goals through a real backup, restore and erase, each on real database files. The
 * history is approved example MT07 (docs/testing/goal-examples.md) plus a completed
 * goal; the working checkpoint is that a fresh installation restored from the backup
 * shows the same figures.
 */
@RunWith(AndroidJUnit4::class)
class GoalBackupRestoreTest {
    @get:Rule
    val files = DatabaseFiles()

    @get:Rule
    val fresh = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val other by lazy { TestLedger(fresh).also { it.start() } }
    private val today = LocalDate.of(2026, 10, 9)
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }
    private val investments = HandInvestmentPreferences()
    private val preferences = object : AppearancePreferences, CategoryOrderingPreferences {
        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = flowOf(
            PreferenceRead.Ready(
                AppearancePreference(ThemePreference.System, InkPreference.Cobalto)
            )
        )

        override suspend fun setTheme(theme: ThemePreference) = PreferenceWrite.Saved

        override suspend fun setInk(ink: InkPreference) = PreferenceWrite.Saved

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            flowOf(
                PreferenceRead.Ready(
                    CategoryOrderingPreference(
                        CategoryOrder.MostUsed,
                        CategoryOrderDirection.Descending
                    )
                )
            )

        override suspend fun setCategoryOrder(order: CategoryOrder) = PreferenceWrite.Saved

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            PreferenceWrite.Saved
    }
    private var nextId = 5_000

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private suspend fun TestLedger.submit(command: GoalCommand) =
        goals.submit(command).also { assertTrue("$it", it is CommandOutcome.Committed) }

    private suspend fun TestLedger.board(): GoalBoard = goalReads.board().valueOrFail()

    private suspend fun TestLedger.version(goal: GoalId) =
        board().goals.single { it.history.goal.id == goal }.history.goal.version

    private suspend fun TestLedger.goal(name: String, target: Long): GoalId {
        val created = submit(
            CreateGoal(
                operation(),
                1,
                GoalName.of(name).valueOrFail(),
                IconKey.of("flight").valueOrFail(),
                pesos(target),
                YearMonth.of(2026, 12)
            )
        ) as CommandOutcome.Committed
        return created.value.goalId
    }

    private suspend fun TestLedger.move(goal: GoalId, kind: GoalMovementKind, amount: Long) =
        submit(
            RecordGoalMovement(
                operation(),
                1,
                goal,
                version(goal),
                kind,
                pesos(amount),
                today,
                null
            )
        )

    /** MT07 on Viaje, chosen for Recibo, and a completed Celular nuevo. */
    private suspend fun history() {
        val viaje = ledger.goal("Viaje a la costa", 3_000_000)
        ledger.move(viaje, GoalMovementKind.Contribution, 1_710_000)
        ledger.move(viaje, GoalMovementKind.Withdrawal, 400_000)
        ledger.audit = ledger.audit.plusSeconds(1)
        val celular = ledger.goal("Celular nuevo", 1_200_000)
        ledger.move(celular, GoalMovementKind.Contribution, 1_200_000)
        ledger.submit(
            CompleteGoal(ledger.operation(), 1, celular, ledger.version(celular), 10.seconds)
        )
        ledger.submit(FeatureGoal(ledger.operation(), 1, viaje))
    }

    private suspend fun backup(): ByteArray = BackupExport(
        RoomBackupSnapshots(ledger.database),
        preferences,
        preferences,
        investments,
        { ledger.audit },
        dispatchers
    )
        .create().valueOrFail().bytes

    private fun restoresInto(target: TestLedger, folder: DatabaseFiles) = RoomRestores(
        target.database,
        folder.restoreFolder,
        preferences,
        preferences,
        investments,
        NoticeSeen(),
        { uuid(nextId++) },
        { target.audit },
        dispatchers
    )

    /** What a person sees of each goal: no identifiers, versions or audit times. */
    private fun GoalBoard.figures() = goals.map {
        listOf(
            it.history.goal.name.value,
            it.progress.saved,
            it.progress.percentage,
            it.progress.places.map { place -> place.state },
            it.progress.completed,
            it.history.goal.id == featured
        )
    }

    @Test
    fun aBackupWithGoalsRestoresOnAFreshInstallationWithTheSameFigures() = runBlocking {
        history()
        val before = ledger.board()
        val bytes = backup()

        val restores = restoresInto(other, fresh)
        val staged = restores.stage(bytes).valueOrFail()
        assertEquals(2, staged.replacement.goals)
        assertEquals(0, staged.current.goals)
        val outcome = restores.replace(staged)

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        assertEquals(2, (outcome as RestoreOutcome.Restored).summary.goals)
        val after = other.board()
        assertEquals(before.figures(), after.figures())
        assertEquals(Money.cop(1_310_000), after.goals.first().progress.saved)
        assertEquals(43L, after.goals.first().progress.percentage)
        // Every record starts again at version 1; positions are kept, and the next one follows.
        assertEquals(
            listOf("1|2", "1|3"),
            other.rows("goal", "version", "next_sequence").sorted()
        )
    }

    @Test
    fun aRestoreReplacesTheGoalsThereNeverMerges() = runBlocking {
        history()
        val bytes = backup()
        other.goal("Moto", 9_000_000)

        val restores = restoresInto(other, fresh)
        val staged = restores.stage(bytes).valueOrFail()
        assertEquals(1, staged.current.goals)
        restores.replace(staged)

        assertEquals(
            listOf("Viaje a la costa", "Celular nuevo"),
            other.board().goals.map { it.history.goal.name.value }
        )
    }

    @Test
    fun erasingEverythingRemovesEveryGoalItsHistoryAndTheChoice() = runBlocking {
        history()
        val resets = RoomResets(
            ledger.database,
            files.resetFolder,
            files.restoreFolder,
            emptyList(),
            PreferenceReset { PreferenceWrite.Saved },
            { ledger.zone },
            { ledger.audit },
            dispatchers
        )
        val preview = resets.preview().valueOrFail()
        assertEquals(2, preview.current.goals)

        val outcome = resets.reset(preview, ledger.operation())

        assertEquals(ResetOutcome.Erased(preferencesPending = false), outcome)
        for (table in listOf("goal", "goal_event", "deleted_goal_event", "featured_goal")) {
            assertEquals(table, 0L, ledger.long("SELECT COUNT(*) FROM $table"))
        }
    }
}
