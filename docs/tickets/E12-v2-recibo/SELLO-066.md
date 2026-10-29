# SELLO-066 — Replace the saved receipt with brief feedback after a save

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-017, SELLO-021, SELLO-069, SELLO-004
- **Gate:** G2

### Outcome
After a confirmed save the form closes and the caller shows a short confirmation,
e.g. **Gasto anotado: 48.700 en Comida**, with its figures already updated.

### Deliverables
Expense and income creation return to their caller after a confirmed save; the
SELLO-069 confirmation notice above the dock; removal of the full-screen "Gasto anotado /
RECIBIDO" receipt and its **Listo** step.

### Acceptance criteria
- The notice text comes from the saved record read back from storage; nothing is
  announced before the save is confirmed.
- An unknown outcome keeps the form with **Comprobar si se guardó** and shows no
  notice; a definite failure keeps the draft.
- The notice uses ink background and paper text, is announced to TalkBack, needs no
  dismissal and has no undo.
- Income saves show the equivalent notice where income entry was opened.
- Haptics still run only after commit; process death after commit does not repeat
  the save or lose the notice silently.

### Tests
ViewModel tests for the closed-after-commit state and notice text; device journey
tests for expense and income save, unknown outcome and process death; G2.

### Working checkpoint
Saving an expense from Recibo returns straight to Recibo with the notice and the
updated remainder.

### Context and starting points
D01: "Expense and income creation use brief feedback after a confirmed save." Today
saving opens a receipt screen (`ExpenseReceiptView` in `ExpenseEntryState.kt`) that
needs **Listo**. The shell already shows `SelloNotice` for corrections
(`CorrectionNoticeBar` in `navigation/SelloApp.kt`). Tests: `ExpenseEntryJourneyTest`,
`IncomeJourneyTest`, `WalkingSliceJourneyTest`.

### Implementation plan
1. Keep the storage read-back, but turn its result into a navigation result plus
   notice instead of a receipt state.
2. Host the confirmation in the shell beside the correction notice; decide which
   one wins if both are pending and test it.
3. Remove the receipt screen and update journeys.

### Concrete cases and pitfalls
- Do not show a notice for an edit; edits already close after a confirmed update.
- The read-back may fail after commit: the save is still done, so the form must not
  offer a second write.

### Verification recipe
Focused ViewModel tests, then the entry and income journeys on a device, then
`./scripts/verify-ticket SELLO-066 --retain`.
