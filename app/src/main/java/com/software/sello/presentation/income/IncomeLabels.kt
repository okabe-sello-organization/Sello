package com.software.sello.presentation.income

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.software.sello.R

/** The name each fixed income source is shown by, by its stored key. */
private val fixedSourceNames = mapOf(
    "salary" to R.string.income_salary,
    "freelance" to R.string.income_freelance,
    "passive_income" to R.string.income_passive,
    "transfer" to R.string.income_transfer
)

@Composable
fun incomeSourceLabel(key: String, otherName: String? = null): String =
    fixedSourceNames[key]?.let { stringResource(it) }
        ?: otherName
        ?: stringResource(R.string.income_other)

/**
 * What each fixed income source is called on screen, by its key, so a search finds an
 * income by the name the person sees (SELLO-193). "Otro" goes by its own stored name.
 */
fun interface IncomeSourceNames {
    fun fixed(): Map<String, String>
}

/** [IncomeSourceNames] from string resources, as [incomeSourceLabel] shows them. */
class ResourceIncomeSourceNames(private val resources: Resources) : IncomeSourceNames {
    override fun fixed(): Map<String, String> =
        fixedSourceNames.mapValues { resources.getString(it.value) }
}
