package com.software.sello.data

import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Opens database files the usual way, except that a statement equal to [refused] fails
 * with a plain [SQLException] before it runs: a database error that is not SQLite's own
 * [android.database.sqlite.SQLiteException], as a layer above SQLite may raise.
 */
class RefusedStatements(@Volatile var refused: String? = null) : SupportSQLiteOpenHelper.Factory {
    private val files = FrameworkSQLiteOpenHelperFactory()

    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration
    ): SupportSQLiteOpenHelper = Helper(files.create(configuration))

    private inner class Helper(private val helper: SupportSQLiteOpenHelper) :
        SupportSQLiteOpenHelper by helper {
        override val writableDatabase: SupportSQLiteDatabase
            get() = Database(helper.writableDatabase)
    }

    private inner class Database(private val database: SupportSQLiteDatabase) :
        SupportSQLiteDatabase by database {
        override fun execSQL(sql: String) {
            if (sql == refused) throw SQLException("Refused: $sql")
            database.execSQL(sql)
        }
    }
}
