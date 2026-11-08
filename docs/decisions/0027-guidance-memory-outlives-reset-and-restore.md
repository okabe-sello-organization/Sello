# 0027 — Guidance seen outlives Restablecer and merges on restore

**Owner:** project owner (user) · **Date:** 2026-11-08
**Status:** owner decision in the E38 epic's *Decisions*; implemented in SELLO-205.
Narrows [D09](0001-mvp-contract.md#d09--restore-and-reset) for this one group of
preferences.

## Context

E38 adds quiet nudges that show once. D09 says a reset returns every portable preference
to first-run values and a restore replaces them. Applied to the guidance seen, both would
tell people again what they already saw: after Restablecer, or after restoring an older
backup onto a phone that has seen more.

## Decision

- **Restablecer keeps the guidance seen** while it can be read: each nudge seen, the last
  nudge shown and its day, the backup reminder's last day and its "No volver a mostrar",
  and whether the person was already using Sello. Every other preference is cleared
  exactly as before. Guidance that cannot be read is cleared with the rest, so a reset
  still starts it over.
- **Backups carry it** from version 11 (`preferences.guidance`), and a restore **merges**
  it into the phone's: seen if either says so, "No volver a mostrar" if either says so,
  the later day wins. A file without it (older, or made while it could not be read) whose
  history has any expense, income, goal or investment merges the six new-user nudges as
  seen, since its person was already using Sello; without such a history it keeps the
  phone's. The merge is a restore preference group: one that fails stays pending and is
  retried like the others.
- Nothing financial is involved: the merge applies to these values only, and the history,
  categories and every other preference are still replaced as D09 says.
- People already using Sello when guidance arrives (any expense, income, goal or
  investment the first time a version with it starts) have the new-user nudges marked
  seen, once.

## Consequences

- A reset never leaves guidance that cannot be read behind, so nudges can show again.
- A restore can stay pending on guidance alone; Respaldo names it ("Guía: lo que ya viste")
  and applies it like any other group. [ARCHITECTURE](../../ARCHITECTURE.md) §7 names
  this exception to D09.
