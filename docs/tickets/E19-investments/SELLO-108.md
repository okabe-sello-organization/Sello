# SELLO-108 — Carry investments through backup, restore and erase

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-107, SELLO-004
- **Gate:** G2

### Outcome
Backups include investments and rates; restore and erase handle them.

### Deliverables
Format additions with docs; restore and reset updates.

### Acceptance criteria
- Older files still restore; new ones round-trip exactly, including decimal rates.

### Tests
Codec and round-trip tests; G2.

### Working checkpoint
A backup with investments restores with the same figures.

### Context and starting points
`docs/data/backup-format.md`.

### Implementation plan
1. Format.
2. Restore and reset.
3. Tests.

### Concrete cases and pitfalls
- Rates as exact decimals.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-108 --retain`.

### Execution progress

- **2026-10-25:** backup format version 4 carries investments (with their movements nested,
  in recording order) and USD rates as exact integer hundredths (`rateHundredths`);
  versions 1–3 still restore with none. Validation refuses bounds, currencies, duplicate
  ids and positions, text forms, and any history `InvestmentPolicy` refuses, changing
  nothing. Restore inserts them in the replacing transaction; erase clears them; export
  reads them in the same snapshot; the restore and erase previews count investments.
  Built by a delegated agent on SELLO-107's storage and reviewed here.
