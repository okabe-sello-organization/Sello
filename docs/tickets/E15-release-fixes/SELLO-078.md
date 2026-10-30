# SELLO-078 — Say what failed when a backup does not pass its own check

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
A backup the app refuses to hand out because it fails its own reading check is reported
as that, not as "Tus datos superan lo que cabe en un respaldo".

### Deliverables
A distinct message for a self-check failure, separate from a history that is really too
large, with a test.

### Acceptance criteria
- A history over the backup limits still reports it is too large.
- Any other self-check rejection reports that the backup could not be made correctly,
  that nothing in Sello changed and that it is a fault to report.
- Nothing about the rejected data is shown beyond what the current messages show.

### Tests
`BackupViewModelTest` or a screen test for both failures; G2.

### Working checkpoint
Both failures show their own message.

### Context and starting points
Found in SELLO-036: on Android 16 every backup failed its own check (fixed there) and
was reported as too large, which hid the cause. `BackupSession` maps
`BackupPreparationFailure.Unrestorable` to `BackupFailure.TooLarge`.

### Implementation plan
1. Tell a too-large rejection apart from other self-check rejections.
2. Add the message and tests.

### Concrete cases and pitfalls
- Keep the "an empty file may be left" warning for both.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-078 --retain`.

### Execution progress

- **Owner decision, 2026-10-23:** a short message, at most two lines: "Falla de Sello al
  armar el respaldo; tus datos no cambiaron. No uses el archivo vacío que pudo quedar."
- `BackupPreparationFailure` gains `TooLarge` (too many bytes, records or default-limit
  entries); `Unrestorable` is now every other refusal of the app's own file, shown as
  `BackupFailure.SelfCheck` with the message above. A real size refusal keeps its
  message.
