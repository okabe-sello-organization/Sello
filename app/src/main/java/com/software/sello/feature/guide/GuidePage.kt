package com.software.sello.feature.guide

import androidx.annotation.StringRes
import com.software.sello.R
import com.software.sello.designsystem.icon.SelloIcon

/**
 * The Guía's parts, in the order the list shows them. A later part, such as pro tips or a
 * written tutorial, is a new entry here with its own pages; the pages already written stay.
 */
enum class GuideSection(@StringRes val title: Int) {
    Start(R.string.guide_section_start),
    Screens(R.string.guide_section_screens),
    Data(R.string.guide_section_data)
}

/** One point of a page: a bold [term] over its plain [body]. */
data class GuideTopic(@StringRes val term: Int, @StringRes val body: Int)

/**
 * A page of the Guía de Sello: static text, nothing read or stored. [id] is how a route
 * names it, so that a help sheet can open the Guía at this page; it never changes once
 * shipped. [caption] is the line under the title in the list, where the page has one, and
 * [lede] a sentence over its topics.
 */
enum class GuidePage(
    val id: String,
    val section: GuideSection,
    val icon: SelloIcon,
    @StringRes val title: Int,
    val topics: List<GuideTopic>,
    @StringRes val caption: Int? = null,
    @StringRes val lede: Int? = null
) {
    FirstMonth(
        id = "primer-mes",
        section = GuideSection.Start,
        icon = SelloIcon.PlayArrow,
        title = R.string.guide_first_month_title,
        caption = R.string.guide_first_month_caption,
        topics = listOf(
            GuideTopic(
                R.string.guide_first_month_categories,
                R.string.guide_first_month_categories_body
            ),
            GuideTopic(R.string.guide_first_month_limits, R.string.guide_first_month_limits_body),
            GuideTopic(R.string.guide_first_month_expense, R.string.guide_first_month_expense_body)
        )
    ),
    Gestures(
        id = "gestos",
        section = GuideSection.Start,
        icon = SelloIcon.Bolt,
        title = R.string.guide_gestures_title,
        caption = R.string.guide_gestures_caption,
        lede = R.string.guide_gestures_lede,
        topics = listOf(
            GuideTopic(R.string.guide_gestures_category, R.string.guide_gestures_category_body),
            GuideTopic(R.string.guide_gestures_expense, R.string.guide_gestures_expense_body),
            GuideTopic(R.string.guide_gestures_keypad, R.string.guide_gestures_keypad_body)
        )
    ),
    Recibo(
        id = "recibo",
        section = GuideSection.Screens,
        icon = SelloIcon.ReceiptLong,
        title = R.string.guide_recibo_title,
        topics = listOf(
            GuideTopic(R.string.guide_recibo_remaining, R.string.guide_recibo_remaining_body),
            GuideTopic(R.string.guide_recibo_per_day, R.string.guide_recibo_per_day_body),
            GuideTopic(R.string.guide_recibo_limit, R.string.guide_recibo_limit_body),
            GuideTopic(R.string.guide_recibo_carry, R.string.guide_recibo_carry_body),
            GuideTopic(R.string.guide_recibo_reserved, R.string.guide_recibo_reserved_body),
            GuideTopic(R.string.guide_recibo_stamp, R.string.guide_recibo_stamp_body),
            GuideTopic(R.string.guide_recibo_categories, R.string.guide_recibo_categories_body)
        )
    ),
    Entry(
        id = "anotar",
        section = GuideSection.Screens,
        icon = SelloIcon.EditNote,
        title = R.string.guide_entry_title,
        topics = listOf(
            GuideTopic(R.string.guide_entry_one, R.string.guide_entry_one_body),
            GuideTopic(R.string.guide_entry_details, R.string.guide_entry_details_body),
            GuideTopic(R.string.guide_entry_several, R.string.guide_entry_several_body),
            GuideTopic(R.string.guide_entry_ways, R.string.guide_entry_ways_body),
            GuideTopic(R.string.guide_entry_others, R.string.guide_entry_others_body)
        )
    ),
    Summary(
        id = "resumen",
        section = GuideSection.Screens,
        icon = SelloIcon.Insights,
        title = R.string.guide_summary_title,
        topics = listOf(
            GuideTopic(R.string.guide_summary_month, R.string.guide_summary_month_body),
            GuideTopic(R.string.guide_summary_months, R.string.guide_summary_months_body),
            GuideTopic(R.string.guide_summary_small, R.string.guide_summary_small_body),
            GuideTopic(R.string.guide_summary_weekdays, R.string.guide_summary_weekdays_body),
            GuideTopic(R.string.guide_summary_income, R.string.guide_summary_income_body),
            GuideTopic(R.string.guide_summary_year, R.string.guide_summary_year_body)
        )
    ),
    Goals(
        id = "metas",
        section = GuideSection.Screens,
        icon = SelloIcon.Flag,
        title = R.string.guide_goals_title,
        topics = listOf(
            GuideTopic(R.string.guide_goals_goal, R.string.guide_goals_goal_body),
            GuideTopic(R.string.guide_goals_moves, R.string.guide_goals_moves_body),
            GuideTopic(R.string.guide_goals_apart, R.string.guide_goals_apart_body),
            GuideTopic(R.string.guide_goals_done, R.string.guide_goals_done_body)
        )
    ),
    Investments(
        id = "inversiones",
        section = GuideSection.Screens,
        icon = SelloIcon.AccountBalance,
        title = R.string.guide_investments_title,
        topics = listOf(
            GuideTopic(R.string.guide_investments_moves, R.string.guide_investments_moves_body),
            GuideTopic(R.string.guide_investments_gain, R.string.guide_investments_gain_body),
            GuideTopic(R.string.guide_investments_rate, R.string.guide_investments_rate_body)
        )
    ),
    Backup(
        id = "respaldo",
        section = GuideSection.Data,
        icon = SelloIcon.Backup,
        title = R.string.guide_backup_title,
        caption = R.string.guide_backup_caption,
        topics = listOf(
            GuideTopic(R.string.guide_backup_phone, R.string.guide_backup_phone_body),
            GuideTopic(R.string.guide_backup_make, R.string.guide_backup_make_body),
            GuideTopic(R.string.guide_backup_file, R.string.guide_backup_file_body),
            GuideTopic(R.string.guide_backup_restore, R.string.guide_backup_restore_body)
        )
    );

    companion object {
        /** The page a route names, or null for an id this version does not know. */
        fun byId(id: String): GuidePage? = entries.firstOrNull { it.id == id }
    }
}
