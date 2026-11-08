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

- **Restablecer keeps the guidance seen**: each nudge seen, the last nudge shown and its
  day, the backup reminder's last day and its "No volver a mostrar", and whether the
  person was already using Sello. Every other preference is cleared exactly as before.
- **Backups carry it** from version 11 (`preferences.guidance`), and a restore **merges**
  it into the phone's: seen if either says so, "No volver a mostrar" if either says so,
  the later day wins. Older files, or a file whose guidance could not be read when it was
  made, keep the phone's.
- Nothing financial is involved: the merge applies to these values only, and the history,
  categories and every other preference are still replaced as D09 says.
- People already using Sello when guidance arrives (any expense, income, goal or
  investment the first time a version with it starts) have the new-user nudges marked
  seen, once.

## Consequences

- A stored guidance value that cannot be read also survives a reset: no nudge shows while
  it cannot be read, and nothing else depends on it.
- The merge is applied beside the restore's preference groups, never shown as pending: a
  merge that cannot be saved costs at most a nudge shown once more.
