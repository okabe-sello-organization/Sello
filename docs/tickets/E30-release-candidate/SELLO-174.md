# SELLO-174 — The candidate's records describe the app as it is

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G0

### Outcome
Anyone checking the candidate reads the true state of the app, separate from the
October MVP evidence.

### Acceptance criteria
- `README.md` describes the four tabs (Recibo, Resumen, Metas, Inversiones) and the
  backup, restore, export and erase flows the app now offers.
- `docs/testing/recovery-matrix.md` "Supported versions" lists schemas 1 to 9 and backup
  formats 1 to 7, each with the test that proves it.
- `docs/release/mvp-checklist.md` stays the October MVP record, marked as such. A new
  candidate checklist beside it lists what the candidate must pass: the release check,
  the advisory check, the emulator walk and the owner's phone walk. Each starts as
  pending, for SELLO-175 and the owner to fill in.
- `docs/release/README.md` and HANDOFF stop describing the 0.1.0 MVP as the current
  baseline wherever a reader would act on it. The full HANDOFF refresh stays with
  SELLO-175.
- `python3 scripts/quality/links.py` passes.

### Notes
Audit findings R4, R5 and B8 (2026-11-04): `README.md:41-44`, `mvp-checklist.md:20-27,
38, 40, 44-49, 100-108, 131`, `HANDOFF.md:3, 16, 121`, `recovery-matrix.md` "Supported
versions". The version stays 0.1.0 (owner, 2026-11-04). Documentation only: the automated
checks, no agent review.
