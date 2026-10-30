# SELLO-E23 — Anotar varios: several entries at once

- **Status:** In Progress
- **Increment:** Post-MVP
- **Goal:** The person records several expenses, incomes, goal movements or investment movements in one go, on one receipt that is saved whole or not at all.
- **Exit:** Expense, income, goal and investment entry each open a bulk receipt drawn in the approved v2 design; a receipt saves every line in one recoverable operation or none; drafts survive process death; single entry works as before.

[ARCHITECTURE §8](../../../ARCHITECTURE.md) lists Anotar varios in the *Next* increment
and requires the batch semantics before any UI; they are fixed below. The v1.1 drawings
are [Anotar varios and Varios movimientos](../../design/sello-spec.html#s-varios).

## Decisions

**Owner direction (2026-10-30):** the owner's answers, given when the orchestrated run
started.

- **Scope:** expenses, incomes, goal Aportar/Retirar and investment movements. Not Se
  repite, the USD rate, or creating categories, goals or investments.
- **Saving:** all or nothing. One operation ID per receipt. If any line is refused,
  nothing is written and the draft stays. An unknown outcome is checked with the same ID
  before any retry, as for a single entry.
- **Undo:** none for a saved receipt, as v2 has for every new entry. The origin screen
  shows a confirmation; a wrong line is deleted afterwards with the usual six-second undo.
- **Design:** the bulk screens are redrawn in v2 style from the v1.1 drawings, and the
  owner approves that mock (SELLO-126) before any bulk screen is built. Storage work runs
  meanwhile.
- **One kind per receipt:** the Gasto/Ingreso switch sets the whole receipt; expense and
  income lines are never mixed.
- **Run:** implementers on Claude Opus 5.5 at high effort for the storage tickets
  (SELLO-127 to SELLO-129), Claude Sonnet 5.5 at high effort for the others; reviewers on
  Claude Opus 5.5. Merges are delegated as in [orchestration](../../development/orchestration.md).

**Owner approval (2026-10-30):** the [Anotar varios mock](../../design/v2/varios.html)
(SELLO-126) approved as drawn, with every choice it lists: switching Gasto/Ingreso asks
first, keeps amounts and notes and clears categories or sources; a goal's total is aportes
minus retiros; an investment's total is Aportado; a USD receipt stays in dollars; no type
is preselected; ways in are the Varios pill and links at the foot of the lists, with no
dock long-press; removing a draft line has a short Deshacer; leaving asks to discard; two
rows of category or source chips and no Nueva chip; a line's title is its category,
source or type, with the note under it.

**Owner ruling (2026-10-30):** routes may carry what the person typed on the screen they
came from (amount, note, date), as hints checked like any input and free to be ignored;
Varios on Anotar hands its draft to the bulk receipt this way (SELLO-131, #287).
