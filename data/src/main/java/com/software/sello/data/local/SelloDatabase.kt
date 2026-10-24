package com.software.sello.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.software.sello.data.local.dao.CategoryDao
import com.software.sello.data.local.dao.DeletedExpenseDao
import com.software.sello.data.local.dao.DeletedGoalEventDao
import com.software.sello.data.local.dao.DeletedIncomeDao
import com.software.sello.data.local.dao.ExpenseDao
import com.software.sello.data.local.dao.GoalDao
import com.software.sello.data.local.dao.IncomeDao
import com.software.sello.data.local.dao.LimitDao
import com.software.sello.data.local.dao.OperationReceiptDao
import com.software.sello.data.local.dao.ProfileDao
import com.software.sello.data.local.dao.ScheduleDao
import com.software.sello.data.local.entity.CategoryEntity
import com.software.sello.data.local.entity.DefaultLimitEntity
import com.software.sello.data.local.entity.DeletedExpenseEntity
import com.software.sello.data.local.entity.DeletedGoalEventEntity
import com.software.sello.data.local.entity.DeletedIncomeEntity
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.local.entity.FeaturedGoalEntity
import com.software.sello.data.local.entity.GoalEntity
import com.software.sello.data.local.entity.GoalEventEntity
import com.software.sello.data.local.entity.IncomeEntity
import com.software.sello.data.local.entity.MonthLimitEntity
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.data.local.entity.ProfileEntity
import com.software.sello.data.local.entity.ScheduleEntity
import com.software.sello.data.local.entity.SchedulePauseEntity
import kotlin.coroutines.CoroutineContext

/**
 * The financial database. Version 1 is the first schema; every later change raises
 * the version, exports its schema to `data/schemas` and ships a tested migration.
 * Version 2 added `deleted_expense`; version 3 added `deleted_income`; version 4 added
 * goals: `goal`, `goal_event`, `deleted_goal_event` and `featured_goal`; version 5
 * added `schedule`, `schedule_pause` and the origin of a recorded entry.
 */
@Database(
    entities = [
        ProfileEntity::class,
        CategoryEntity::class,
        DefaultLimitEntity::class,
        MonthLimitEntity::class,
        ExpenseEntity::class,
        IncomeEntity::class,
        OperationReceiptEntity::class,
        DeletedExpenseEntity::class,
        DeletedIncomeEntity::class,
        GoalEntity::class,
        GoalEventEntity::class,
        DeletedGoalEventEntity::class,
        FeaturedGoalEntity::class,
        ScheduleEntity::class,
        SchedulePauseEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class SelloDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun categoryDao(): CategoryDao

    abstract fun limitDao(): LimitDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun incomeDao(): IncomeDao

    abstract fun operationReceiptDao(): OperationReceiptDao

    abstract fun deletedExpenseDao(): DeletedExpenseDao

    abstract fun deletedIncomeDao(): DeletedIncomeDao

    abstract fun goalDao(): GoalDao

    abstract fun deletedGoalEventDao(): DeletedGoalEventDao

    abstract fun scheduleDao(): ScheduleDao

    companion object {
        const val FILE_NAME = "sello.db"
    }
}

/**
 * The only place the database is opened. Queries run on [queries], which must carry a
 * dispatcher. There is no destructive fallback of any kind: a file this build cannot
 * read, whether from a newer schema or damaged, makes the open fail and is left as it is.
 */
internal fun openSelloDatabase(
    context: Context,
    queries: CoroutineContext,
    fileName: String = SelloDatabase.FILE_NAME
): SelloDatabase =
    Room.databaseBuilder(context.applicationContext, SelloDatabase::class.java, fileName)
        .openHelperFactory(NonDestructiveOpenHelperFactory())
        .addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5)
        .setQueryCoroutineContext(queries)
        .build()
