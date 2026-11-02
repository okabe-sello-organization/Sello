package com.software.sello.feature.category

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CategoryRemovalPreviews
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.DeleteCategory
import com.software.sello.domain.port.Disposal
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RemovalPreview
import com.software.sello.domain.port.RemovalPreviewFailure
import com.software.sello.presentation.carry.CarryCorrectionText
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The category the editor holds, as deleting it needs it. */
internal data class RemovalSubject(
    val categoryId: CategoryId,
    val name: String,
    val generation: Long,
    val version: Long,
    val archived: Boolean
)

/** What the editor does for the removal: what it read, and how it ends. */
internal interface RemovalHost {
    /** The category as the editor last read it; null before it was read. */
    fun subject(): RemovalSubject?

    fun show(removal: RemovalState?)

    /** The deletion has its receipt: the editor and the detail close with [notice]. */
    fun deleted(notice: CorrectionNotice)

    /** A refusal about the whole form, said in the editor's own words. */
    fun failed(error: SaveError)

    /** The category changed underneath: read it again. False when it could not be read. */
    suspend fun reread(): Boolean

    /** Archívala: the existing archive question. */
    fun askToArchive()
}

/**
 * Deleting a category from its editor (ADR 0024, SELLO-138), one step at a time and all of
 * it in state: count what it holds, ask, then send one [DeleteCategory] carrying the very
 * preview that was shown.
 *
 * It shares the editor's [submission], so one operation is out at a time and an unknown
 * outcome is asked about with the same identifier, never replaced. Which step was open,
 * the word typed and what the operation in flight deletes are kept in [saved]: after the
 * process is killed, an operation in flight is checked first, and an open step is counted
 * again and reopened with what it holds now.
 */
internal class CategoryRemoval(
    saved: SavedStateHandle,
    private val submission: Submission,
    private val commands: CategoryCommands,
    private val previews: CategoryRemovalPreviews,
    private val snapshots: MonthlySnapshots,
    private val money: MoneyFormatter,
    private val currentMonth: YearMonth,
    private val scope: CoroutineScope,
    private val host: RemovalHost
) {
    /** Which step was open, kept so it can be reopened. */
    @Serializable
    private enum class Stage { Choose, Pick, Move, All }

    /**
     * The step open, the target chosen and the word typed; and, while an operation is out,
     * what it deletes ([sentName], [sentExpenses], [sentTarget]), to say once it is found.
     */
    @Serializable
    private data class Kept(
        val stage: Stage? = null,
        val target: String? = null,
        val targetName: String? = null,
        val typed: String = "",
        val sentName: String? = null,
        val sentExpenses: Int = 0,
        val sentTarget: String? = null
    )

    private val kept = SavedDraft(saved, KEY_REMOVAL, Kept.serializer())
    private var memory = kept.read() ?: Kept()

    /** The preview of deleting it all: what it holds. */
    private var counted: RemovalPreview? = null

    /** The preview of moving into the chosen category, as shown. */
    private var moving: RemovalPreview? = null

    /** What came with the list of later months a move changes, confirmed by sending it. */
    private var confirmation: CorrectionConfirmation? = null

    /** What the last preview asked for, so Reintentar asks the same again. */
    private var asked: Disposal = Disposal.DeleteAll

    /** Every category of the month as last read, to name those in a correction list. */
    private var categories: Map<CategoryId, Category> = emptyMap()
    private val corrections = CarryCorrectionText(money)
    private var state: RemovalState? = null

    /** Whether a step was open when the process was killed. */
    val restorable: Boolean get() = memory.stage != null

    fun onAction(action: RemovalAction) {
        val now = state
        if (action == RemovalAction.Start) {
            if (now == null) scope.launch { count() }
            return
        }
        // Nothing is answered while something is counted, sent or checked.
        if (now == null || now.busy) return
        val step = now.step
        when (action) {
            RemovalAction.Start -> Unit

            RemovalAction.Dismiss -> if (step !is RemovalStep.Checking) close()

            RemovalAction.Move ->
                if (step is RemovalStep.Choose) scope.launch { pick(step.counts) }

            RemovalAction.DeleteAll -> if (step is RemovalStep.Choose) {
                remember(memory.copy(stage = Stage.All, typed = ""))
                show(RemovalStep.All(step.counts, ""))
            }

            RemovalAction.ArchiveInstead -> if (step is RemovalStep.Choose && step.canArchive) {
                close()
                host.askToArchive()
            }

            is RemovalAction.Pick -> if (step is RemovalStep.Pick) {
                val target = step.targets?.firstOrNull { it.id == action.categoryId }
                if (target != null) scope.launch { move(target.id, target.name) }
            }

            is RemovalAction.Word -> if (step is RemovalStep.All) {
                remember(memory.copy(typed = action.text))
                show(step.copy(typed = action.text))
            }

            RemovalAction.Confirm -> confirm(step)

            RemovalAction.Next -> next(step)
        }
    }

    /** After the process was killed with a step open: count again and reopen it. */
    fun restore() {
        val stage = memory.stage ?: return
        val target = memory.target
        val targetName = memory.targetName
        val typed = memory.typed
        scope.launch {
            val counts = count() ?: return@launch
            when (stage) {
                Stage.Choose -> Unit

                Stage.Pick -> pick(counts)

                Stage.Move -> if (target != null && targetName != null) {
                    move(target, targetName)
                } else {
                    pick(counts)
                }

                Stage.All -> {
                    remember(memory.copy(stage = Stage.All, typed = typed))
                    show(RemovalStep.All(counts, typed))
                }
            }
        }
    }

    /** Asks how the deletion in flight ended, with its own identifier. */
    suspend fun recover() {
        // Only a deletion is checked here; the form's own changes are the form's.
        if (submission.step != STEP_DELETE) return
        val name = memory.sentName ?: host.subject()?.name.orEmpty()
        publish(RemovalState(name, RemovalStep.Checking(failed = false), busy = true))
        settle(submission.recover(commands::find), counts = null)
    }

    /** Counts what the category holds and asks what to do with it. */
    private suspend fun count(): RemovalCounts? {
        val subject = host.subject() ?: return null
        publish(RemovalState(subject.name, RemovalStep.Counting, busy = true))
        asked = Disposal.DeleteAll
        val preview = when (val read = previews.preview(subject.categoryId, Disposal.DeleteAll)) {
            is Outcome.Failure -> return null.also { refused(read.error) }
            is Outcome.Success -> read.value
        }
        counted = preview
        val counts = counts(preview)
        remember(Kept(stage = Stage.Choose))
        show(
            if (preview.isEmpty) {
                RemovalStep.Empty
            } else {
                RemovalStep.Choose(counts, canArchive = !subject.archived)
            }
        )
        return counts
    }

    /** The categories that can take the expenses: those not archived, but this one. */
    private suspend fun pick(counts: RemovalCounts) {
        val subject = host.subject() ?: return
        remember(memory.copy(stage = Stage.Pick))
        publish(
            RemovalState(subject.name, RemovalStep.Pick(counts, currentMonth, null), busy = true)
        )
        val month = when (val read = snapshots.read(currentMonth)) {
            is Outcome.Failure ->
                return show(RemovalStep.Pick(counts, currentMonth, null, failed = true))

            is Outcome.Success -> read.value
        }
        categories = month.categories.associate { it.category.id to it.category }
        val targets = month.categories
            .filter { !it.category.archived && it.category.id != subject.categoryId }
            .map { line ->
                MoveTarget(
                    id = line.category.id.value,
                    name = line.category.name.value,
                    iconKey = line.category.icon.value,
                    // As Recibo's cell: what is left of a limit, or what was spent.
                    amount = money.format(line.budget?.remaining ?: line.spent)
                )
            }
        show(RemovalStep.Pick(counts, currentMonth, targets))
    }

    /** What moving into [target] would do, from its own preview. */
    private suspend fun move(target: String, targetName: String) {
        val subject = host.subject() ?: return
        val id = (CategoryId.of(target) as? Outcome.Success)?.value ?: return
        remember(memory.copy(stage = Stage.Move, target = target, targetName = targetName))
        publish(state?.copy(busy = true) ?: RemovalState(subject.name, RemovalStep.Counting, true))
        val disposal = Disposal.MoveTo(id)
        asked = disposal
        when (val read = previews.preview(subject.categoryId, disposal)) {
            is Outcome.Failure -> refused(read.error)
            is Outcome.Success -> showMove(read.value)
        }
    }

    /** The move's exact facts; a sum that does not fit is not shown, and nothing is offered. */
    private fun showMove(preview: RemovalPreview) {
        val target = preview.target ?: return show(problem(RemovalProblem.CannotCount))
        val after = when (val sum = target.spentThisMonth + preview.spentThisMonth) {
            is Outcome.Failure -> return show(problem(RemovalProblem.CannotCount))
            is Outcome.Success -> sum.value
        }
        moving = preview
        confirmation = null
        show(
            RemovalStep.Move(
                counts(preview),
                MoveFacts(
                    target = memory.targetName.orEmpty(),
                    month = preview.month,
                    targetSpent = money.format(target.spentThisMonth),
                    targetSpentAfter = money.format(after),
                    sourceLimit = preview.limitThisMonth?.let(::limit),
                    targetLimit = target.limitThisMonth?.let(::limit)
                )
            )
        )
    }

    private fun confirm(step: RemovalStep) {
        when (step) {
            RemovalStep.Empty -> counted?.let { send(Disposal.DeleteAll, it) }

            is RemovalStep.All -> if (step.canDelete) counted?.let { send(Disposal.DeleteAll, it) }

            is RemovalStep.Move, is RemovalStep.Correction -> {
                val preview = moving ?: return
                val target = preview.target ?: return
                send(Disposal.MoveTo(target.categoryId), preview)
            }

            else -> Unit
        }
    }

    /** A failure slip's way forward. */
    private fun next(step: RemovalStep) {
        when (step) {
            is RemovalStep.Checking -> if (step.failed) scope.launch { recover() }

            is RemovalStep.Pick -> if (step.failed) scope.launch { pick(step.counts) }

            is RemovalStep.Problem -> when (val problem = step.problem) {
                is RemovalProblem.Changed -> review()

                is RemovalProblem.TargetClosed -> {
                    val counts = counted?.let(::counts) ?: return restart()
                    scope.launch { pick(counts) }
                }

                is RemovalProblem.NotSaved -> retrySend()

                RemovalProblem.CannotCount -> {
                    val target = (asked as? Disposal.MoveTo)?.target?.value
                    val name = memory.targetName
                    if (target != null && name != null) {
                        scope.launch { move(target, name) }
                    } else {
                        restart()
                    }
                }
            }

            else -> Unit
        }
    }

    /** Revisar: the step the change concerns, with what the category holds now. */
    private fun review() {
        val move = moving
        val all = counted
        when {
            asked is Disposal.MoveTo && move != null && !move.isEmpty -> showMove(move)

            asked == Disposal.DeleteAll && all != null && !all.isEmpty -> {
                remember(memory.copy(stage = Stage.All, typed = ""))
                show(RemovalStep.All(counts(all), ""))
            }

            else -> restart()
        }
    }

    /** Reintentar after storage failed: the same confirmed preview, as a new operation. */
    private fun retrySend() {
        val move = moving
        val target = move?.target
        val all = counted
        when {
            asked is Disposal.MoveTo && move != null && target != null ->
                send(Disposal.MoveTo(target.categoryId), move)

            asked == Disposal.DeleteAll && all != null -> send(Disposal.DeleteAll, all)

            // Restored after the process was killed: what it holds is counted again.
            else -> restart()
        }
    }

    private fun restart() {
        scope.launch { count() }
    }

    /** Sends the deletion once, carrying the preview the person confirmed. */
    private fun send(disposal: Disposal, preview: RemovalPreview) {
        val subject = host.subject() ?: return
        remember(
            memory.copy(
                sentName = subject.name,
                sentExpenses = preview.expenses,
                sentTarget = memory.targetName.takeIf { disposal is Disposal.MoveTo }
            )
        )
        publish(state?.copy(busy = true) ?: return)
        asked = disposal
        val confirmed = confirmation
        scope.launch {
            val settled = submission.send(commands::find, STEP_DELETE) { operation ->
                commands.submit(
                    DeleteCategory(
                        operation,
                        subject.generation,
                        subject.categoryId,
                        subject.version,
                        disposal,
                        preview,
                        confirmed.takeIf { disposal is Disposal.MoveTo }
                    )
                )
            }
            settle(settled, counts(preview))
        }
    }

    private suspend fun settle(
        settled: Settled<CommittedCategoryChange, CategoryRejection>?,
        counts: RemovalCounts?
    ) {
        when (settled) {
            // Another send or check is out; an operation still unresolved is asked again.
            null -> if (submission.pending && submission.step == STEP_DELETE) {
                show(RemovalStep.Checking(failed = true))
            } else {
                state?.let { publish(it.copy(busy = false)) }
            }

            is Settled.Committed -> done()

            is Settled.Unknown -> show(RemovalStep.Checking(failed = true))

            Settled.NotCommitted -> show(problem(RemovalProblem.NotSaved(counts)))

            is Settled.Rejected -> rejected(settled.reason, counts)
        }
    }

    private suspend fun rejected(
        reason: CommandRejection<CategoryRejection>,
        counts: RemovalCounts?
    ) {
        val specific = (reason as? Specific)?.reason
        when {
            specific is CategoryRejection.ContentsChanged -> changed(specific.current, counts)

            specific == CategoryRejection.TargetMissing ||
                specific == CategoryRejection.TargetArchived ->
                show(problem(RemovalProblem.TargetClosed(memory.targetName.orEmpty())))

            specific is CategoryRejection.CorrectionUnconfirmed -> askCorrection(specific)

            specific == CategoryRejection.CategoryMissing -> end(SaveError.CategoryGone)

            reason is CommandRejection.StaleGeneration -> end(SaveError.DataChanged)

            // The category itself changed: read it again, then what deleting it does now.
            reason is CommandRejection.VersionConflict -> {
                if (!host.reread()) return show(problem(RemovalProblem.NotSaved(counts)))
                val subject = host.subject() ?: return
                when (val read = previews.preview(subject.categoryId, asked)) {
                    is Outcome.Failure -> refused(read.error)
                    is Outcome.Success -> changed(read.value, counts)
                }
            }

            // Storage failed, or a refusal this form does not know: nothing was written.
            else -> show(problem(RemovalProblem.NotSaved(counts)))
        }
    }

    /** The preview the command answered with replaces the one shown, and is said first. */
    private fun changed(current: RemovalPreview, before: RemovalCounts?) {
        if (current.target != null) moving = current else counted = current
        confirmation = null
        show(problem(RemovalProblem.Changed(before, counts(current))))
    }

    private suspend fun askCorrection(refusal: CategoryRejection.CorrectionUnconfirmed) {
        if (categories.isEmpty()) {
            (snapshots.read(currentMonth) as? Outcome.Success)?.value?.let { month ->
                categories = month.categories.associate { it.category.id to it.category }
            }
        }
        val changed = confirmation != null
        confirmation = refusal.confirmation
        show(
            RemovalStep.Correction(
                target = memory.targetName.orEmpty(),
                list = corrections.list(refusal.correction, categories),
                changed = changed
            )
        )
    }

    /** No preview: nothing is offered, and why is said. */
    private fun refused(failure: RemovalPreviewFailure) {
        when (failure) {
            RemovalPreviewFailure.CategoryMissing -> end(SaveError.CategoryGone)

            RemovalPreviewFailure.TargetMissing, RemovalPreviewFailure.TargetArchived ->
                show(problem(RemovalProblem.TargetClosed(memory.targetName.orEmpty())))

            RemovalPreviewFailure.SameCategory,
            RemovalPreviewFailure.TotalOverflow,
            is RemovalPreviewFailure.Storage -> show(problem(RemovalProblem.CannotCount))
        }
    }

    private fun done() {
        val sent = memory
        val name = sent.sentName ?: host.subject()?.name.orEmpty()
        clear()
        host.deleted(
            CorrectionNotice.CategoryDeleted(
                name = name,
                expenses = sent.sentExpenses,
                movedTo = sent.sentTarget
            )
        )
    }

    /** The editor says it in its own words; the removal is over. */
    private fun end(error: SaveError) {
        clear()
        publish(null)
        host.failed(error)
    }

    private fun close() {
        clear()
        publish(null)
    }

    private fun clear() {
        memory = Kept()
        kept.clear()
        counted = null
        moving = null
        confirmation = null
    }

    private fun remember(next: Kept) {
        memory = next
        kept.write(next)
    }

    private fun problem(problem: RemovalProblem) = RemovalStep.Problem(problem)

    private fun show(step: RemovalStep) {
        val name = state?.name ?: host.subject()?.name.orEmpty()
        publish(RemovalState(name, step, busy = false))
    }

    private fun publish(next: RemovalState?) {
        state = next
        host.show(next)
    }

    private fun counts(preview: RemovalPreview) = RemovalCounts(
        expenses = preview.expenses,
        total = money.format(preview.total),
        firstMonth = preview.firstMonth,
        lastMonth = preview.lastMonth,
        schedules = preview.schedules
    )

    private fun limit(limit: BudgetLimit): RemovalLimit = when (limit) {
        is BudgetLimit.Finite -> RemovalLimit.Finite(money.format(limit.amount))
        BudgetLimit.Unlimited -> RemovalLimit.Unlimited
    }

    companion object {
        const val STEP_DELETE = "delete"
        private const val KEY_REMOVAL = "category.edit.removal"
    }
}
