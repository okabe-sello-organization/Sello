package com.software.sello.feature.goals

import androidx.annotation.StringRes
import com.software.sello.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.presentation.category.CategoryIcons

/** An icon a goal can have: its stored key, its drawing and what it is called. */
data class GoalIconOption(val key: String, val icon: SelloIcon, @StringRes val label: Int)

/**
 * The icons offered for goals: the same 24 as for a category, in the same order, so both
 * creation screens choose from one set. The ten goals had first keep their goal names
 * ("Casa", "Viaje"); the rest are called as for a category.
 */
object GoalIcons {
    private val goalLabels: Map<String, Int> = mapOf(
        "home" to R.string.goal_icon_home,
        "flight" to R.string.goal_icon_flight,
        "school" to R.string.goal_icon_school,
        "phone_iphone" to R.string.goal_icon_phone_iphone,
        "pets" to R.string.goal_icon_pets,
        "fitness_center" to R.string.goal_icon_fitness_center,
        "child_care" to R.string.goal_icon_child_care,
        "shopping_cart" to R.string.goal_icon_shopping_cart,
        "theaters" to R.string.goal_icon_theaters,
        "medical_services" to R.string.goal_icon_medical_services
    )

    val options: List<GoalIconOption> = CategoryIcons.options.map {
        GoalIconOption(it.key, it.icon, goalLabels[it.key] ?: it.label)
    }

    val keys: List<String> = options.map { it.key }
}
