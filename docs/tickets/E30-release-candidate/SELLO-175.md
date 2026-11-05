# SELLO-175 — The candidate passes its release checks on the newest Android

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-170, SELLO-171, SELLO-172, SELLO-173, SELLO-174
- **Gate:** G3

### Outcome
The shrunk release build of the final `main` passes the release check, the advisory
check and a walk on a new API 37 emulator. That leaves only the owner's phone walk.

### Acceptance criteria
- `scripts/check --release` passes on the final `main`, signed with a throwaway key
  made outside the repository as in SELLO-169.
- The advisory check runs again with the network. `docs/release/advisories.md` is
  refreshed. An advisory that needs a dependency change is reported to the owner, not
  changed.
- The shrunk build is walked on a new API 37 emulator, each against hand-worked
  figures with synthetic data:
  - the paths SELLO-169 didn't walk: CSV and JSON export with Compartir, the
    `sello://anotar` link (also while an entry form holds a typed draft), deleting a
    category, entering an income, expense search, and the theme and ink settings;
  - a short pass over backup and restore, catch-up and the dollar rate on API 37;
  - the fixes of SELLO-170 to SELLO-173.
- No crash is logged. Anything that breaks gets its own fix before this ticket closes.
- The candidate checklist from SELLO-174 records the results, leaving the owner's phone
  walk pending. The pull request refreshes HANDOFF and closes the epic.

### Notes
Audit findings R2, R3 and R6 (2026-11-04), and P3 (the link over a typed draft,
unverified). Not a release: nothing is published, signed with the owner's key or
tagged. The version stays 0.1.0 (owner, 2026-11-04). Review: lighter.
