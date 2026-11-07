package com.software.sello.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.software.sello.domain.policy.SearchKey

/**
 * Version 9 (SELLO-165) adds what long histories need, all derived: `expense.search_key`,
 * each note folded by [SearchKey] so search ignores case and accents, filled here for
 * every expense already kept; and indexes for the catch-up notice's "recorded since"
 * read and for the latest receipt of a kind. Nothing that existed in version 8 is
 * changed or removed.
 */
internal val Migration8To9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `expense` ADD COLUMN `search_key` TEXT NOT NULL DEFAULT ''")
        fillSearchKeys(db)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_expense_created_at_origin_schedule_id` " +
                "ON `expense` (`created_at`, `origin_schedule_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_income_created_at_origin_schedule_id` " +
                "ON `income` (`created_at`, `origin_schedule_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_operation_receipt_kind_committed_at` " +
                "ON `operation_receipt` (`kind`, `committed_at`)"
        )
    }

    /** In pages by id, so a long history never sits in memory at once. */
    private fun fillSearchKeys(db: SupportSQLiteDatabase) {
        var after = ""
        while (true) {
            val page = db.query(
                "SELECT `id`, `note` FROM `expense` WHERE `note` IS NOT NULL AND `id` > ? " +
                    "ORDER BY `id` LIMIT $FILL_PAGE",
                arrayOf<Any?>(after)
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                }
            }
            for ((id, note) in page) {
                db.execSQL(
                    "UPDATE `expense` SET `search_key` = ? WHERE `id` = ?",
                    arrayOf<Any?>(SearchKey.of(note), id)
                )
            }
            if (page.size < FILL_PAGE) return
            after = page.last().first
        }
    }
}

private const val FILL_PAGE = 500

/**
 * Version 8 lets up to five goals be starred for Recibo (SELLO-143): `featured_goal`'s one
 * chosen goal moves to `starred_goal` and `featured_goal` goes. A chosen goal that is
 * completed is not carried over, since a star never sits on a completed goal; it showed
 * nowhere already. Nothing else that existed in version 7 is read, changed or removed.
 */
internal val Migration7To8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `starred_goal` (`goal_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`goal_id`), FOREIGN KEY(`goal_id`) REFERENCES `goal`(`id`) " +
                "ON UPDATE RESTRICT ON DELETE RESTRICT )"
        )
        db.execSQL(
            "INSERT INTO `starred_goal` (`goal_id`) SELECT DISTINCT f.`goal_id` " +
                "FROM `featured_goal` f JOIN `goal` g ON g.`id` = f.`goal_id` " +
                "WHERE g.`completed_on` IS NULL"
        )
        db.execSQL("DROP INDEX IF EXISTS `index_featured_goal_goal_id`")
        db.execSQL("DROP TABLE `featured_goal`")
    }
}

/**
 * Version 7 adds the carry chains of signed budget carryover (ADR 0020), empty: every
 * category of an existing history stays off (CP1). Nothing that existed in version 6 is
 * read, changed or removed.
 */
internal val Migration6To7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `carry_chain` (`category_id` TEXT NOT NULL, " +
                "`start_month` TEXT NOT NULL, `end_month` TEXT, " +
                "PRIMARY KEY(`category_id`, `start_month`), FOREIGN KEY(`category_id`) " +
                "REFERENCES `category`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT )"
        )
    }
}

/**
 * Version 6 adds investments: their movements, the copy kept for undoing a deleted
 * movement and the dated USD rates, all empty. Nothing that existed in version 5 is read,
 * changed or removed.
 */
internal val Migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `investment` (`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `description` TEXT, `icon_key` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `next_sequence` INTEGER NOT NULL, " +
                "`version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `investment_movement` (`id` TEXT NOT NULL, " +
                "`investment_id` TEXT NOT NULL, `sequence` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, `effective_date` TEXT NOT NULL, `note` TEXT, " +
                "`version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`investment_id`) REFERENCES `investment`(`id`) " +
                "ON UPDATE RESTRICT ON DELETE RESTRICT )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_investment_movement_investment_id_sequence` " +
                "ON `investment_movement` (`investment_id`, `sequence`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_investment_movement_investment_id_effective_date` " +
                "ON `investment_movement` (`investment_id`, `effective_date`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deleted_investment_movement` (" +
                "`deletion_id` TEXT NOT NULL, `generation` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER NOT NULL, `movement_id` TEXT NOT NULL, " +
                "`investment_id` TEXT NOT NULL, `sequence` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `amount_minor` INTEGER NOT NULL, " +
                "`currency` TEXT NOT NULL, `effective_date` TEXT NOT NULL, `note` TEXT, " +
                "`version` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`deletion_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `usd_rate` (`id` TEXT NOT NULL, " +
                "`rate_hundredths` INTEGER NOT NULL, `effective_date` TEXT NOT NULL, " +
                "`sequence` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_usd_rate_sequence` ON `usd_rate` (`sequence`)"
        )
    }
}

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
