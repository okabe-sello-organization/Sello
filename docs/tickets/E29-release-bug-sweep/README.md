# SELLO-E29 — Release bug sweep

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** The person uses a release build in which no known bug can break their data, write a payment twice, lock a screen or show a wrong figure.
- **Exit:** Every finding of the 2026-11-03 code audit and the six bug issues open that day (#275, #278, #285, #286, #289, #319) is fixed with a red-first test, and the shrunk release build has walked every journey added since the MVP.

## Decisions

**Owner direction (2026-11-03):** given in chat on the orchestrator's audit and questions.

- Work on every audit finding, critical to low, as one epic, and fold in the six open
  bug issues; each ticket's pull request closes the issues it fixes.
- One Room schema step, 8 → 9, is approved for this epic, and only in SELLO-165: indexes
  for the catch-up "recorded since" read and the receipts lookup, and a stored search key
  so expense search ignores accents and case beyond plain letters. The search key is
  checked on a device first and left out if Android's SQLite already folds them. Both
  are derived, so the backup format does not change.
- Pagos fijos' "Ya se anotaron" counts the entries as they are stored now: an edited
  entry at its edited amount, a deleted one not at all.
- "Este y los siguientes": when the schedule part saved but the entry did not, and the
  person then changes the terms and saves again, the Solo este / Este y los siguientes
  question is asked again.
- Orchestrated run; subagents and separate review agents are approved for this epic,
  and the owner delegated every merge except a release.

**Orchestrator's calls (2026-11-03), open to the owner:**

- No released build holds schedules, so SELLO-145 fixes the writes and does not
  repair rows already written on debug installs.
- A stamp earned and lost again inside one multi-line receipt is still celebrated:
  goal rule 4 earns it and rule 5 keeps it (SELLO-160).
- After a restore or reset commits, catch-up waits for the restart instead of writing
  with the old process's zone (SELLO-146).
- Income history's header total leaves out incomes dated after today, as Resumen does
  (SELLO-151).
- When the categories can't be read, the dock and `sello://anotar` open the entry form,
  which shows its own load error and retry, instead of doing nothing (SELLO-156).
- The "lograda" Deshacer window is counted from when the big moment closes, not from
  the save (SELLO-158).
- File and line references in the tickets are from the audit commit `6ed35ac3`; later
  commits may have moved them.
