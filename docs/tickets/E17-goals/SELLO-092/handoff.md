# SELLO-092 — Earn stamps and complete goals

A confirmed aporte, retiro or new target that earns or relights a stamp is celebrated once: on the goal page the stamp lands with the save notice (`GoalDestination`, `MilestoneRow` landing/relight), on Metas the notice becomes the horizontal `StampTicket` (`CelebrationTicket`), and `ExpenseCorrections.lands` keeps a reopened or redrawn screen from landing it again.
**Marcar como lograda** (goal page at 100 %, Metas card, and "¿Ya la lograste?" in Editar meta) runs through `GoalCompleter`: one completion, the big moment (`LogradaMoment`) only after the receipt, then Deshacer for 10 s after the moment, 30 s with TalkBack (`ScreenReader`); afterwards the goal is final and read-only.
Still open: the Recibo goal pill (SELLO-093); sandbox scenarios and catalog examples for celebrations and completion (SELLO-094).
