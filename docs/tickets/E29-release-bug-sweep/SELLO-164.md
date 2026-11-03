# SELLO-164 — Names with invisible characters can't pass for another

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
A category, goal, investment or source name can't use invisible or direction-changing
characters to look like another name.

### Acceptance criteria
- "Café​" (zero-width space), "Ca­fé" (soft hyphen) and a name with U+202E
  are refused or treated as "Café" by the uniqueness check when another "Café" exists,
  archived ones included.
- Names already stored or in old backups still decode and restore; the stricter rule
  applies to new input only, unless the planner shows that stored data can't hold them.
- JVM tests write these characters as `\u` escapes and check bytes.

### Notes
Audit findings: `BoundedText.kt:31` (`isControl` misses format characters) and `:40`
(`foldedKey` keeps them). If the folded key is stored in Room, changing it needs a
migration: stop and report rather than add a second schema step. Review: adversarial
(validation, backup).
