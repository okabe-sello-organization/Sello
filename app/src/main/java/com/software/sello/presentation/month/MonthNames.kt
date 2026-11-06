package com.software.sello.presentation.month

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import java.time.YearMonth

/** Month names from resources: [full] as in a title, [short] as in a grid of months. */
@Immutable
class MonthNames(
    private val full: List<String>,
    private val short: List<String>,
    private val titleFormat: String
) {
    /** "Octubre 2026". */
    fun title(month: YearMonth): String = titleFormat.format(full[month.monthValue - 1], month.year)

    /** "Octubre", as the large word of the month bar. */
    fun name(month: YearMonth): String = full[month.monthValue - 1]

    /** "octubre", as inside a sentence. */
    fun inSentence(month: YearMonth): String = full[month.monthValue - 1].lowercase()

    fun short(month: YearMonth): String = short[month.monthValue - 1]
}

@Composable
fun monthNames() = MonthNames(
    full = stringArrayResource(R.array.month_names).toList(),
    short = stringArrayResource(R.array.month_names_short).toList(),
    titleFormat = stringResource(R.string.month_title)
)

/** The same names outside composition, for a render model a ViewModel owns. */
fun monthNames(resources: Resources) = MonthNames(
    full = resources.getStringArray(R.array.month_names).toList(),
    short = resources.getStringArray(R.array.month_names_short).toList(),
    titleFormat = resources.getString(R.string.month_title)
)
