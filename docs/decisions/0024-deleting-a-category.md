# 0024 — Deleting a category

**Owner:** project owner (user) · **Date:** 2026-11-02
**Status:** owner decision of 2026-11-02. Supersedes "MVP has no destructive category
deletion or history cascade" in [ADR 0001 D07](0001-mvp-contract.md#d07--archive-and-undo)
and the archive-only bullet of [ARCHITECTURE §5](../../ARCHITECTURE.md#5-persistence-commands-and-recovery).

## Context

A category could only be archived: it stopped taking expenses but stayed in every month
it had a limit or spending. People need to remove a category for good, even one with
expenses and fixed payments.

## Decision

- **Borrar categoría** sits in Editar categoría's ⋮, under **Archivar categoría**; it is
  not on category detail. Active and archived categories can be deleted.
- A category with no expenses and no fixed payments is deleted after one confirm slip.
- One with expenses or fixed payments asks first what happens to them:
  - **Mover** to another active category: the expenses keep their dates and the fixed
    payments their rules; the deleted category's limits and carry go. Every month's total
    stays the same. One confirm slip.
  - **Borrar todo**: expenses, fixed payments, limits and carry go from every month.
    Counted first, then the person types **BORRAR**, as reset does.
- No undo. The confirmation is the safeguard; external backups are the way back.
- One command (`DeleteCategory`) does all of it in one transaction with an operation ID
  and a receipt. It carries the preview the person confirmed and is refused, with the
  current preview, when the category, the target or the month no longer match it. The
  preview is the figures the person is shown (counts, total, months, this month's spending
  and limits), not a fingerprint of every row: a change that leaves all of them equal
  does not stop the deletion they confirmed. Moving
  into a carrying category that the moved expenses change needs the correction
  confirmation of ADR 0020 CP20–CP22.
- Foreign keys stay `RESTRICT`: the command removes or moves each dependent row itself;
  nothing cascades.

## Consequences

- Past months change after Borrar todo; the confirmation says so.
- No schema change: rows are moved or deleted, and backups never hold a reference to a
  deleted category.
- Archive stays for anyone who only wants to stop using a category.

## Verification

Real-Room contract tests for both disposals, the refusals and replay; JVM tests for the
editor flow and its screenshots (SELLO-138).
