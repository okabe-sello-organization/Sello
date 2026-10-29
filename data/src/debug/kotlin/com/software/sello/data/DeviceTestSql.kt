package com.software.sello.data

import android.database.Cursor

/*
 * Debug builds only: SQL on a storage's own Room database, for the app's device tests
 * to set up and look at what no command makes, such as a first-run reset or a damaged
 * row. Going through Room's own connection matters: a second connection to the file
 * fights Room for its lock, and Room's live queries never hear what it wrote.
 */

/**
 * Runs [statements] in one Room transaction. When it ends Room checks which tables
 * changed, so every screen observing them reads them again, as after a write of its own.
 */
fun FinancialStorage.runSqlForTests(statements: List<String>) {
    database.runInTransaction {
        val connection = database.openHelper.writableDatabase
        statements.forEach(connection::execSQL)
    }
}

/** Reads [sql] on Room's own connection and hands its rows to [read]. */
fun <T> FinancialStorage.querySqlForTests(sql: String, read: (Cursor) -> T): T =
    database.openHelper.readableDatabase.query(sql).use(read)
