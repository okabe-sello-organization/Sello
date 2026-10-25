# SELLO-044 — Import legacy data through the validated restore path

- **Type:** Story
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-028, SELLO-030, SELLO-036, SELLO-004
- **Gate:** G2

### Outcome
History recorded in the previous app can be brought into Sello once, keeping
everything that follows Sello's rules and leaving out, visibly, everything that does not.

### Deliverables
A decision record for the import policy, a documented description of the legacy
export format, an isolated translator from that export to the portable backup plan
of SELLO-028, a preview that reports imported and skipped records by reason, and the
import completed through the restore workflow of SELLO-030. Synthetic fixtures only.

### Acceptance criteria
- Owner decision (2026-10-09): restore as much as possible. A legacy record that
  breaks a Sello rule is skipped, never repaired, clipped or rounded. Owner decision
  (2026-10-25, ADR 0019): two exceptions, always counted in the preview: text longer
  than Sello allows is cut to fit, and category names that clash are numbered
  `Nombre_2`, `Nombre_3`, … in the old app's order.
- Nothing is skipped silently. Before confirmation the person sees how many
  categories, expenses, incomes, goals and repeating entries will be imported, how
  many were shortened or renamed and how many are left out, grouped by reason, and can
  cancel with no change. Investments, their movements and rates are reported as not
  imported yet (SELLO-118 imports them after E19).
- A record that depends on a skipped record is skipped with it and counted under
  its own reason; no expense is moved to another category to save it.
- The translator never writes to the database. It produces a backup plan that the
  released validator accepts unchanged, and the released restore applies it with
  its normal replace warning, generation advance and receipts.
- The source is a legacy export file chosen through the document picker. The old
  database file is never read. A file that is not a recognisable legacy export, or
  exceeds the structural bounds of architecture §7, is rejected whole.
- No personal financial data enters the repository, reports or logs.

### Tests
Translator table tests over synthetic legacy exports: every rule that can skip a
record, dependent records, duplicates, bounds and an entirely unusable file.
Real-Room restore of a translated plan with independently counted results; G2.

### Working checkpoint
The released app behaves exactly as before for anyone who does not import. An
import either completes through restore or changes nothing.

### Context and starting points
**Get the data when this ticket starts.** No legacy export has been examined. Ask
the project owner for a real export from the previous app at that point and study
it locally only: its shape decides the translator. Do not commit it or copy values
from it; write synthetic fixtures that reproduce its structure and its problems.

The skip rule is a deliberate difference from Sello's own backups, where one bad
record rejects the whole file (D05, D09, architecture §7). Record it in a short ADR
before implementing, and update the guardrail text it changes. Read the delivered
contracts of SELLO-028 and SELLO-030, [storage](../../../development/storage.md) and
[money, dates and text](../../../development/money-dates-text.md) for the rules a
record must meet. Legacy code and formulas are evidence to review, not code to copy.

### Implementation plan
1. Obtain a legacy export from the owner; document its fields, units, date and zone
   handling and identifiers in `docs/data/legacy-export-format.md`.
2. List every Sello rule a legacy record can break and present the open choices
   below to the owner. Record the answers and the skip rule in an ADR.
3. Implement the translator in an isolated `data/legacy` package: bounded read,
   per-record validation with the existing domain rules, typed skip reasons, and a
   backup plan plus an import report as its only outputs.
4. Add the preview with imported and skipped counts by reason, then hand the plan
   to the released restore workflow without a second write path.
5. Build synthetic fixtures and tests first; run the ticket gate and record evidence.

### Concrete cases and pitfalls
- Owner answers (2026-10-25), recorded in ADR 0019 and the
  [legacy export format](../../../data/legacy-export-format.md): clashing names are
  numbered, over-long text is cut and shown as such, the single legacy limit is the
  default from the earliest imported month, goals come in with one contribution on the
  export day, repeating entries become schedules, an unknown income source becomes Otro
  with its name, imports may be repeated and the skip report is shown, not saved.
  Investments wait for SELLO-118.
- Amounts of zero, negative, fractional or above 999.999.999.999 COP, impossible or
  future dates and text with control characters are skipped under the approved rule.
- Restore replaces, never merges (D09). Importing into an app that already has
  history replaces that history; the preview must say so with both counts.
- A legacy total will not match the imported total when records are skipped. Say
  so; do not present the import as complete.

### Verification recipe
Create/run `./gradlew :data:testDebugUnitTest --tests '*LegacyTranslatorTest'` and a
real-Room `LegacyImportRestoreTest`, then G2. Inspect imported and skipped counts
against independently counted fixtures and confirm a cancelled or rejected import
leaves rows, generation and preferences unchanged.
