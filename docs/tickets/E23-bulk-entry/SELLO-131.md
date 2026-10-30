# SELLO-131 — Anotar varios: several expenses at once

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-127, SELLO-130
- **Gate:** G2

### Outcome
From Anotar or a category, the person types several expenses on one receipt and saves
them together.

### Acceptance criteria
- Opens from the entry points of the approved design. From a category, every new line
  starts in that category. From Anotar with something typed, that expense is line one.
- The loop works: type an amount, optionally pick a category and add a note, then
  **Otro renglón**. A new line copies the category above it. Lines with no amount are
  ignored. A line with an amount and no category blocks saving and says why.
- The button counts valid lines: **Anotar gasto**, **Anotar 3 gastos**. One date for the
  receipt, never in the future.
- Saving follows SELLO-127: **Guardando…** locks the sheet; a refusal keeps the draft and
  marks the refused lines; an unknown outcome offers only **Comprobar si se guardó** with
  the same operation; a commit closes the sheet and the origin screen shows the
  confirmation once, with its figures already updated. No undo.
- A past date that needs the carry confirmation shows the existing correction preview
  once for the whole receipt.
- The draft (lines, highlighted line, date, operation ID) survives rotation and process
  death, and never submits again on restore.
- ViewModel JVM tests cover the loop, the limits, every save outcome and the restored
  draft. One device journey adds three expenses on one receipt.
- A sandbox scenario lets the owner reach an unknown outcome on a receipt by hand.

### Notes
Navigation gets one bulk route that reads its data itself (routes carry identifiers only).
