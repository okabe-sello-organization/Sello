package com.software.sello.devtools

import com.software.sello.domain.model.Currency
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * A synthetic history the sandbox loads in one go, through the app's own commands. [id]
 * and [version] name it; change [version] whenever its steps change, because the
 * operation of each step is derived from them, the [seed] and the step's place. The same
 * history therefore always writes the same operations, and a step run again after the
 * process ended is recognised rather than recorded twice.
 *
 * It starts in a new sandbox at [start] in [zone] and ends with the financial clock on
 * [today], the day its last date step sets. A scheduled entry is never a step of its own:
 * a [ScenarioStep.Schedule] step saves the schedule, and the app's own catch-up records
 * its occurrences when a [ScenarioStep.CatchUp] step runs it after a date step, exactly
 * as Sello does on opening and when the financial day changes.
 */
data class Scenario(
    val id: String,
    val version: Int,
    val seed: Long,
    val zone: ZoneId,
    val start: LocalDate,
    val today: LocalDate,
    val title: String,
    val summary: String,
    val steps: List<ScenarioStep>
) {
    val key: String get() = "$id@$version"

    /** The operation step [index] always uses. */
    fun operation(index: Int): String =
        UUID.nameUUIDFromBytes("sello-scenario:$id:$version:$seed:$index".toByteArray())
            .toString()
}

/** How a schedule repeats, as the schedule form offers it (recurrence rule 2). */
sealed interface Every {
    /** On [day] of each month, or on the last day of a shorter month. */
    data class Month(val day: Int) : Every

    /** On [first] and [second] of each month, each clamped like [Month]; slots 1 and 2. */
    data class TwiceAMonth(val first: Int, val second: Int) : Every

    /** On the start date and every [days] days after it. */
    data class Days(val days: Int) : Every

    /** On the start date and every [weeks] weeks after it. */
    data class Weeks(val weeks: Int) : Every
}

/** What each occurrence of a schedule records (rule 1), with its [note]. */
sealed interface Scheduled {
    val pesos: Long
    val note: String

    /** A recurring payment into the category the step named [category] made. */
    data class Payment(val category: String, override val pesos: Long, override val note: String) :
        Scheduled

    /** A recurring income from the stored source key [source], such as `salary`. */
    data class Income(val source: String, override val pesos: Long, override val note: String) :
        Scheduled
}

/** An investment movement's kind, as the movement sheet offers it (rule 2). */
enum class Move {
    Aporte,
    Retiro,
    Dividendo,
    Valor
}

/** A USD rate of [hundredths] of a peso per dollar, dated [on]; by default the sandbox's day. */
data class Rate(val hundredths: Long, val on: LocalDate? = null)

/**
 * One action of a history. Amounts are whole pesos, or an investment's minor units;
 * entries are dated on the sandbox's day unless [on] dates them earlier. Every step must
 * be saved: a refusal stops the load and says why. Versions are those a screen would
 * have read at that point, so a step tried again sends exactly the same command.
 */
sealed interface ScenarioStep {
    /** Whether the step submits an operation; a date step or a catch-up does not. */
    val writes: Boolean get() = this !is AdvanceTo && this !is CatchUp

    /** Moves the sandbox's date forward to [date]. */
    data class AdvanceTo(val date: LocalDate) : ScenarioStep

    /** A category the later steps call [key], with [limit] pesos a month or no limit. */
    data class Category(val key: String, val name: String, val icon: String, val limit: Long?) :
        ScenarioStep

    data class Expense(
        val category: String,
        val pesos: Long,
        val note: String? = null,
        val on: LocalDate? = null
    ) : ScenarioStep

    /** Changes the expense step [of] recorded to [pesos], as read at [version]. */
    data class EditExpense(val of: Int, val pesos: Long, val version: Long) : ScenarioStep

    /** Deletes the expense step [of] recorded, as read at [version]. */
    data class DeleteExpense(val of: Int, val version: Long) : ScenarioStep

    /** The category's default limit from the sandbox's month on, as read at [version]; null is none. */
    data class SetDefaultLimit(val category: String, val pesos: Long?, val version: Long) :
        ScenarioStep

    /** The limit of [month] only, as read at [version]; null is none. */
    data class SetMonthLimit(
        val category: String,
        val month: YearMonth,
        val pesos: Long?,
        val version: Long
    ) : ScenarioStep

    data class Archive(val category: String, val version: Long) : ScenarioStep

    /** [source] is a stored income source key, such as `salary`. */
    data class Income(val pesos: Long, val source: String, val on: LocalDate? = null) :
        ScenarioStep

    /** A goal the later steps call [key], with a [target] of pesos and no month. */
    data class Goal(val key: String, val name: String, val icon: String, val target: Long) :
        ScenarioStep

    /** An aporte of [pesos] to [goal], read at [version]. */
    data class Aporte(
        val goal: String,
        val pesos: Long,
        val version: Long,
        val on: LocalDate? = null
    ) : ScenarioStep

    /** A retiro of [pesos] from [goal], read at [version]. */
    data class Retiro(val goal: String, val pesos: Long, val version: Long) : ScenarioStep

    /** [goal]'s new target, read at [version]. */
    data class GoalTarget(val goal: String, val pesos: Long, val version: Long) : ScenarioStep

    /** Marcar como lograda on [goal], read at [version]. */
    data class MarkLograda(val goal: String, val version: Long) : ScenarioStep

    /** [goal] chosen for Recibo, as "Mostrar en Recibo" does. */
    data class ChooseForRecibo(val goal: String) : ScenarioStep

    /**
     * A schedule the later steps call [key], from [start] (by default the sandbox's day)
     * to [end], if any (rules 1–3). Saving it records nothing: a [CatchUp] does.
     */
    data class Schedule(
        val key: String,
        val entry: Scheduled,
        val every: Every,
        val start: LocalDate? = null,
        val end: LocalDate? = null
    ) : ScenarioStep

    /** Pauses [schedule] from today, as read at [version] (rule 8). */
    data class PauseSchedule(val schedule: String, val version: Long) : ScenarioStep

    /** Resumes [schedule] from today, which counts, as read at [version] (rule 8). */
    data class ResumeSchedule(val schedule: String, val version: Long) : ScenarioStep

    /**
     * Agregar inversión: an investment the later steps call [key], in [currency], with a
     * first aporte of [contribution] minor units and a first value of [value] (Otro
     * valor), or the aporte itself when null (Lo mismo que aporté), both dated [on], by
     * default the sandbox's day. [rate] is a USD rate entered with it (rule 12).
     */
    data class Investment(
        val key: String,
        val name: String,
        val icon: String,
        val currency: Currency,
        val contribution: Long,
        val value: Long? = null,
        val rate: Rate? = null,
        val on: LocalDate? = null
    ) : ScenarioStep

    /** A movement of [minor] units of [kind] in [investment], read at [version]. */
    data class Movement(
        val investment: String,
        val kind: Move,
        val minor: Long,
        val version: Long,
        val on: LocalDate? = null
    ) : ScenarioStep

    /** A new dated USD rate (rule 11). */
    data class SetRate(val rate: Rate) : ScenarioStep

    /**
     * Runs the app's catch-up, the one Sello runs when it opens and when the financial
     * day changes, after any run already under way, and waits for it. A failed or
     * unknown batch stops the load, and the next try runs catch-up again.
     */
    data object CatchUp : ScenarioStep
}
