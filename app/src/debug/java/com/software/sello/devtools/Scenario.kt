package com.software.sello.devtools

import com.software.sello.domain.model.Occurrence
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Duration

/**
 * A synthetic history to replay in the sandbox, one step at a time, through the app's
 * own commands. [id] and [version] name it; change [version] whenever its steps
 * change, because the operation of each step is derived from them, the [seed] and the
 * step's place. The same scenario therefore always writes the same operations, and a
 * step run twice is recognised rather than recorded twice.
 *
 * It starts in a new sandbox at [start] in [zone]. A scheduled entry is never a step of
 * its own: a [ScenarioStep.Schedule] step saves the schedule, and the app's own catch-up
 * records its occurrences when a [ScenarioStep.CatchUp] step runs it after a date step,
 * exactly as Sello does on opening and when the financial day changes.
 */
data class Scenario(
    val id: String,
    val version: Int,
    val seed: Long,
    val zone: ZoneId,
    val start: LocalDate,
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

/**
 * What a step must lead to for the scenario to go on. A rejection the step expects is
 * a pass, so edge cases can be scripted; anything else stops the run and says why.
 */
sealed interface Expect {
    /** Committed, now or by an earlier try of the same operation. */
    data object Saved : Expect

    /** Committed by an earlier submission of the same operation; nothing new written. */
    data object AlreadySaved : Expect

    /** Refused by the command with this reason, such as `VersionConflict`. */
    data class Rejected(val reason: String) : Expect

    /** Refused by input validation, so no command was sent at all. */
    data object InvalidInput : Expect
}

/** A figure the month snapshots report, as Recibo and Resumen show it. */
enum class Figure(val label: String) {
    Spent("gastado"),
    Limit("límite"),
    Remaining("restante"),

    /** No category has a finite limit that month: no budget at all, not a zero one. */
    NoLimit("sin límite"),
    Income("ingresos"),
    NetFlow("ingresos menos gastos"),
    Unallocated("sin asignar"),

    /** What the month in progress sets aside for payments still due (rule 11): 0 if none. */
    Reserved("separado"),

    /** Recibo's headline: what is left after what is set aside, below 0 when short. */
    Margin("margen"),

    /** Recibo's daily figure, from the margin while something is set aside. */
    PerDay("por día"),

    /** Plan del mes (rule 13): income still expected this month, `F`; 0 when none. */
    Expected("esperado"),

    /** Plan del mes: recorded plus expected income, `I + F`. */
    Planned("planeado"),

    /** Plan del mes: planned income less the category budgets, `I + F − A`. */
    PlanUnassigned("sin asignar en el plan")
}

/** [pesos] in [month] for [figure], as given by [source]: an approved example ID or a hand derivation. */
data class Check(val month: YearMonth, val figure: Figure, val pesos: Long, val source: String)

/** How one of a goal's four stamp places stands, as its page draws it (rules 4, 5 and 8). */
enum class Stamp(val label: String) {
    Empty("vacío"),
    Earned("ganado"),

    /** Earned, no longer reached: shown faded. */
    Kept("guardado"),

    /** Meta only: the target is reached, waiting for Marcar como lograda. */
    Waiting("esperando")
}

/**
 * The goal the step named [goal] made, as Metas reads it: [saved] pesos, the rounded-down
 * [percentage], its [stamps] at 25 %, 50 %, 75 % and the Meta, whether it is [completed]
 * and whether it is Recibo's goal ([onRecibo], unchecked when null). [source] is the
 * approved example the values come from; none is computed by the code being checked.
 */
data class GoalCheck(
    val goal: String,
    val saved: Long,
    val percentage: Long,
    val stamps: List<Stamp>,
    val source: String,
    val completed: Boolean = false,
    val onRecibo: Boolean? = null
)

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

/**
 * What each occurrence of a schedule records (rule 1). [note] is unique within a
 * scenario: a history restored from a backup has no receipts, so it is found by it.
 */
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

/** An entry a schedule recorded: its occurrence's [date] and [slot] (rule 4), and its [pesos]. */
data class Recorded(val date: LocalDate, val pesos: Long, val slot: Int = 1)

/**
 * What the schedule the step named [schedule] made has recorded from [from] to [to], both
 * included: exactly [entries], by date and slot, each an expense or income dated on its
 * occurrence and carrying that occurrence as its origin. A manual entry is never one of
 * them (rule 5). [source] is the recurrence example the values come from.
 */
data class OccurrenceCheck(
    val schedule: String,
    val from: LocalDate,
    val to: LocalDate,
    val entries: List<Recorded>,
    val source: String
)

/**
 * How the schedule the step named [schedule] made stands, as Sello stores it: [handled]
 * is its checkpoint, the last occurrence catch-up dealt with (null before the first),
 * and [pausedFrom] the first day of a pause still open (null while it is active). Once it
 * is deleted it is not [present], and nothing else is read.
 */
data class ScheduleCheck(
    val schedule: String,
    val handled: Occurrence?,
    val source: String,
    val pausedFrom: LocalDate? = null,
    val present: Boolean = true
)

/** One action of a scenario. Amounts are whole pesos; entries are dated on the sandbox's day. */
sealed interface ScenarioStep {
    val expect: Expect get() = Expect.Saved

    /** Whether the step submits an operation; a date step, a catch-up or a check does not. */
    val writes: Boolean get() = this !is AdvanceTo && this !is Verify && this !is CatchUp

    /** Moves the sandbox's date forward to [date]. */
    data class AdvanceTo(val date: LocalDate) : ScenarioStep

    /** A category the later steps call [key], with [limit] pesos a month or no limit. */
    data class Category(val key: String, val name: String, val icon: String, val limit: Long?) :
        ScenarioStep

    /**
     * [pesos] may be beyond what an entry allows, to script [Expect.InvalidInput]. [on]
     * dates it earlier than the sandbox's day, a backdated entry; by default it is today.
     */
    data class Expense(
        val category: String,
        val pesos: Long,
        val note: String? = null,
        override val expect: Expect = Expect.Saved,
        val on: LocalDate? = null
    ) : ScenarioStep

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

    /**
     * Reads the real month snapshots, goal board, schedules and recorded entries and
     * compares each of [checks], [goals], [occurrences] and [schedules]. Every expected
     * value names where it comes from; none is computed by the code being checked.
     */
    data class Verify(
        val checks: List<Check>,
        val goals: List<GoalCheck> = emptyList(),
        val occurrences: List<OccurrenceCheck> = emptyList(),
        val schedules: List<ScheduleCheck> = emptyList()
    ) : ScenarioStep {
        val figures: Int get() = checks.size + goals.size + occurrences.size + schedules.size
    }

    /** [source] is a stored income source key, such as `salary`. */
    data class Income(val pesos: Long, val source: String) : ScenarioStep

    /** Submits step [of] again: the same operation and input, as a double tap would. */
    data class Repeat(val of: Int, override val expect: Expect = Expect.AlreadySaved) :
        ScenarioStep

    /** Changes the expense step [of] recorded to [pesos], as read at [version]. */
    data class EditExpense(
        val of: Int,
        val pesos: Long,
        val version: Long,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /** Deletes the expense step [of] recorded, as read at [version]. */
    data class DeleteExpense(
        val of: Int,
        val version: Long,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /** Undoes the deletion made by step [of]. */
    data class UndoDelete(val of: Int, override val expect: Expect = Expect.Saved) : ScenarioStep

    /** A goal the later steps call [key], with a [target] of pesos and no month. */
    data class Goal(val key: String, val name: String, val icon: String, val target: Long) :
        ScenarioStep

    /**
     * An aporte of [pesos] to [goal], read at [version]; [on] dates it earlier than the
     * sandbox's day.
     */
    data class Aporte(
        val goal: String,
        val pesos: Long,
        val version: Long,
        override val expect: Expect = Expect.Saved,
        val on: LocalDate? = null
    ) : ScenarioStep

    /** A retiro of [pesos] from [goal], read at [version]. */
    data class Retiro(
        val goal: String,
        val pesos: Long,
        val version: Long,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /** [goal]'s new target, read at [version]. */
    data class GoalTarget(
        val goal: String,
        val pesos: Long,
        val version: Long,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /**
     * Marcar como lograda on [goal], read at [version]. [undoFor] is how long Deshacer is
     * offered, in real elapsed time; null is the longest the app offers. Zero makes the
     * window over at once, so a later Deshacer is too late (MT25) without any waiting.
     */
    data class MarkLograda(
        val goal: String,
        val version: Long,
        override val expect: Expect = Expect.Saved,
        val undoFor: Duration? = null
    ) : ScenarioStep

    /** Deshacer the completion step [of] made, while it is on offer. */
    data class UndoLograda(val of: Int, override val expect: Expect = Expect.Saved) :
        ScenarioStep

    /** [goal] chosen for Recibo, as "Mostrar en Recibo" does. */
    data class ChooseForRecibo(val goal: String, override val expect: Expect = Expect.Saved) :
        ScenarioStep

    /**
     * A schedule the later steps call [key], from [start] (by default the sandbox's day)
     * to [end], if any (rules 1–3). Saving it records nothing: a [CatchUp] does.
     */
    data class Schedule(
        val key: String,
        val entry: Scheduled,
        val every: Every,
        val start: LocalDate? = null,
        val end: LocalDate? = null,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /**
     * New terms for [schedule] after today, as read at [version] (rule 7): [pesos], or
     * [every] from [start], which must be after today. What is not given stays as the
     * earlier steps left it.
     */
    data class ChangeSchedule(
        val schedule: String,
        val version: Long,
        val pesos: Long? = null,
        val every: Every? = null,
        val start: LocalDate? = null,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /** Pauses [schedule] from today, as read at [version] (rule 8). */
    data class PauseSchedule(
        val schedule: String,
        val version: Long,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /** Resumes [schedule] from today, which counts, as read at [version] (rule 8). */
    data class ResumeSchedule(
        val schedule: String,
        val version: Long,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /** Deletes [schedule], as read at [version]; what it recorded stays (rule 10). */
    data class DeleteSchedule(
        val schedule: String,
        val version: Long,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /** Unarchives the category, as read at [version]; its schedules stay paused (rule 9). */
    data class Unarchive(val category: String, val version: Long) : ScenarioStep

    /**
     * Deletes the entry [schedule] recorded for its occurrence on [date] in [slot], as
     * read at [version]. Its origin stays handled: it is never recorded again (rule 4).
     */
    data class DeleteOccurrence(
        val schedule: String,
        val date: LocalDate,
        val slot: Int = 1,
        val version: Long = 1,
        override val expect: Expect = Expect.Saved
    ) : ScenarioStep

    /**
     * Runs the app's catch-up, the one Sello runs when it opens and when the financial
     * day changes, after any run already under way, and waits for it. It passes once
     * everything due up to the sandbox's day is recorded; a failed or unknown batch
     * stops the scenario, and the next try runs catch-up again.
     */
    data object CatchUp : ScenarioStep
}
