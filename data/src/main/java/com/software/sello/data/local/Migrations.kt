package com.software.sello.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 5 adds recurrence: schedules and their pauses, both empty, and the origin of an
 * automatically recorded expense or income, absent on every existing row and on the
 * copies kept for undo. Nothing that existed in version 4 is read, changed or removed.
 */
internal val Migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`category_id` TEXT, `source_key` TEXT, `source_name` TEXT, " +
                "`amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `note` TEXT, " +
                "`rule` TEXT NOT NULL, `rule_first` INTEGER NOT NULL, `rule_second` INTEGER, " +
                "`start_date` TEXT NOT NULL, `end_date` TEXT, `sequence` INTEGER NOT NULL, " +
                "`handled_date` TEXT, `handled_slot` INTEGER, `version` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) " +
                "ON UPDATE RESTRICT ON DELETE RESTRICT )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_category_id` ON `schedule` (`category_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_schedule_sequence` ON `schedule` (`sequence`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_pause` (`schedule_id` TEXT NOT NULL, " +
                "`from_date` TEXT NOT NULL, `until_date` TEXT, " +
                "PRIMARY KEY(`schedule_id`, `from_date`), FOREIGN KEY(`schedule_id`) " +
                "REFERENCES `schedule`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT )"
        )
        for (table in listOf("expense", "income", "deleted_expense", "deleted_income")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `origin_schedule_id` TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `origin_date` TEXT")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `origin_slot` INTEGER")
        }
        for (table in listOf("expense", "income")) {
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_${table}_origin_schedule_id_origin_date_origin_slot` " +
                    "ON `$table` (`origin_schedule_id`, `origin_date`, `origin_slot`)"
            )
        }
    }
}

/**
 * Version 4 adds goals: their history, the copy kept for undoing a deleted movement and
 * Recibo's chosen goal, all empty. Nothing that existed in version 3 is read, changed or
 * removed.
 */
internal val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`icon_key` TEXT NOT NULL, `target_month` TEXT, `completed_on` TEXT, " +
                "`next_sequence` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_event` (`id` TEXT NOT NULL, " +
                "`goal_id` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`effective_date` TEXT, `recorded_on` TEXT NOT NULL, `note` TEXT, " +
                "`version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`goal_id`) " +
                "REFERENCES `goal`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_event_goal_id_sequence` " +
                "ON `goal_event` (`goal_id`, `sequence`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_goal_event_goal_id_effective_date` " +
                "ON `goal_event` (`goal_id`, `effective_date`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_goal_event` (" +
                "`deletion_id` TEXT NOT NULL, `generation` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER NOT NULL, `event_id` TEXT NOT NULL, " +
                "`goal_id` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`effective_date` TEXT, `recorded_on` TEXT NOT NULL, `note` TEXT, " +
                "`version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`deletion_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `featured_goal` (`id` INTEGER NOT NULL, " +
                "`goal_id` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`goal_id`) " +
                "REFERENCES `goal`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_featured_goal_goal_id` " +
                "ON `featured_goal` (`goal_id`)"
        )
    }
}

/**
 * Version 3 adds the table that keeps what a deleted income was until its undo time is
 * over. Nothing that existed in version 2 is read, changed or removed.
 */
internal val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_income` (" +
                "`deletion_id` TEXT NOT NULL, `generation` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER NOT NULL, `income_id` TEXT NOT NULL, " +
                "`effective_date` TEXT NOT NULL, `sequence` INTEGER NOT NULL, " +
                "`amount_minor` INTEGER NOT NULL, `currency` TEXT NOT NULL, " +
                "`source_key` TEXT NOT NULL, `source_name` TEXT, `note` TEXT, " +
                "`version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`deletion_id`))"
        )
    }
}

/**
 * Version 2 adds the table that keeps what a deleted expense was until its undo time
 * is over. Nothing that existed in version 1 is read, changed or removed.
 */
internal val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_expense` (" +
                "`deletion_id` TEXT NOT NULL, `generation` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER NOT NULL, `expense_id` TEXT NOT NULL, " +
                "`category_id` TEXT NOT NULL, `effective_date` TEXT NOT NULL, " +
                "`sequence` INTEGER NOT NULL, `amount_minor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, `note` TEXT, `version` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`deletion_id`))"
        )
    }
}
