package com.software.sello.feature.recibo

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.HelpEntry
import com.software.sello.designsystem.component.HelpSheet

/**
 * What a **?** or an underlined word on Recibo, Pagos previstos and category detail
 * explains (SELLO-207). Which one is open is part of the screen's State; its [name] is
 * what is kept across a restart, so it never changes once shipped.
 */
enum class ReciboHelp(
    @StringRes val title: Int,
    val lines: List<Pair<Int, Int>>,
    @StringRes val example: Int
) {
    /** Recibo's summary card: how Restante, por día, Límite del mes and Gastado relate. */
    Summary(
        R.string.help_summary_title,
        listOf(
            R.string.help_summary_remaining to R.string.help_summary_remaining_body,
            R.string.help_summary_per_day to R.string.help_summary_per_day_body,
            R.string.help_summary_limit to R.string.help_summary_limit_body,
            R.string.help_summary_spent to R.string.help_summary_spent_body,
            R.string.help_summary_reserved to R.string.help_summary_reserved_body
        ),
        R.string.help_summary_example
    ),

    /** "por día" on Recibo. */
    PerDay(
        R.string.help_per_day_title,
        listOf(
            R.string.help_per_day_term to R.string.help_per_day_body,
            R.string.help_per_day_rounding to R.string.help_per_day_rounding_body,
            R.string.help_per_day_reserved to R.string.help_per_day_reserved_body
        ),
        R.string.help_per_day_example
    ),

    /** "Al día" on category detail: the category's own per-day figure. */
    DayAllowance(
        R.string.help_day_allowance_title,
        listOf(
            R.string.help_day_allowance_term to R.string.help_day_allowance_body,
            R.string.help_day_allowance_days to R.string.help_day_allowance_days_body,
            R.string.help_day_allowance_reserved to R.string.help_day_allowance_reserved_body
        ),
        R.string.help_day_allowance_example
    ),

    /** "base", on Recibo and on a category. */
    Base(
        R.string.help_base_title,
        listOf(
            R.string.help_base_term to R.string.help_base_body,
            R.string.help_base_recibo to R.string.help_base_recibo_body
        ),
        R.string.help_base_example
    ),

    /** "sobrante" or "faltante", on Recibo and on a category. */
    Carry(
        R.string.help_carry_title,
        listOf(
            R.string.help_carry_surplus to R.string.help_carry_surplus_body,
            R.string.help_carry_deficit to R.string.help_carry_deficit_body,
            R.string.help_carry_when to R.string.help_carry_when_body
        ),
        R.string.help_carry_example
    ),

    /** "Separado en …" on Pagos previstos. */
    Upcoming(
        R.string.help_upcoming_title,
        listOf(
            R.string.help_upcoming_reserved to R.string.help_upcoming_reserved_body,
            R.string.help_upcoming_date to R.string.help_upcoming_date_body,
            R.string.help_upcoming_short to R.string.help_upcoming_short_body
        ),
        R.string.help_upcoming_example
    ),

    /** Pagos fijos on category detail. */
    FixedPayments(
        R.string.help_fixed_title,
        listOf(
            R.string.help_fixed_payment to R.string.help_fixed_payment_body,
            R.string.help_fixed_coming to R.string.help_fixed_coming_body,
            R.string.help_fixed_later to R.string.help_fixed_later_body,
            R.string.help_fixed_unlimited to R.string.help_fixed_unlimited_body
        ),
        R.string.help_fixed_example
    ),

    /** "Un mes normal aquí", the category's monthly history. */
    History(
        R.string.help_history_title,
        listOf(
            R.string.help_history_average to R.string.help_history_average_body,
            R.string.help_history_columns to R.string.help_history_columns_body,
            R.string.help_history_line to R.string.help_history_line_body
        ),
        R.string.help_history_example
    );

    companion object {
        /** The topic kept as [name], or null for none or one this version does not know. */
        fun named(name: String?): ReciboHelp? = entries.firstOrNull { it.name == name }
    }
}

/**
 * [topic]'s explanation, with the way to the Guía's Recibo page as [onMore]. Drawn over the
 * screen's content, outside its scrolling column.
 */
@Composable
internal fun ReciboHelpSheet(topic: ReciboHelp, onDismiss: () -> Unit, onMore: () -> Unit) {
    // Back closes the sheet before it leaves the screen.
    BackHandler(onBack = onDismiss)
    HelpSheet(
        title = stringResource(topic.title),
        entries = topic.lines.map { (term, body) ->
            HelpEntry(stringResource(term), stringResource(body))
        },
        onDismiss = onDismiss,
        example = stringResource(topic.example),
        onMore = onMore,
        modifier = Modifier.testTag(reciboHelpTag(topic))
    )
}

fun reciboHelpTag(topic: ReciboHelp) = "help:${topic.name}"
