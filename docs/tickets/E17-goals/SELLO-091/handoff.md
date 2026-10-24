# SELLO-091 — Record contributions and withdrawals

Aportar / Retirar (`GoalMovementRoute`, `feature/goals/GoalMovement*`) records, corrects and deletes goal movements with the Sello pad, past dates and the note; `GoalPolicy.withMovement` refuses a retiro above what is saved and a correction a later retiro needs before anything is sent; deletions use the shared Deshacer in `ExpenseCorrections`. Metas' dock, the goal's dock, Retirar dinero and the movement rows open it.
Still open: stamp celebrations and Marcar como lograda (SELLO-092); catalog examples for the chip's percentage (SELLO-094). If SELLO-090 merges first, this branch needs `main` merged in (same goal files) and a rerun with `--affected origin/main --retain`.
