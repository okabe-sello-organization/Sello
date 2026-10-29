# SELLO-010 — Implement exact money, dates and command validation

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-001, SELLO-003, SELLO-004
- **Gate:** G1

### Outcome
Create one pure domain boundary for amounts and effective periods, not scattered screen helpers.

### Deliverables
Typed COP money, currency scale policy, strict locale-aware input parsing, checked
arithmetic with exact widened totals, typed failures, LocalDate/YearMonth validation
and bounded name/note rules. Future currency policy can be represented without
exposing unsupported MVP entry currencies or adding rate infrastructure.

### Acceptance criteria
- Valid COP amounts round-trip exactly; invalid fraction, zero/negative where
  forbidden, exponent notation, overflow and ambiguous paste reject original input.
- Intermediate summation order does not create false overflow; final unrepresentable
  values are explicit failures, never zero/wrap/saturation.
- Effective dates cannot exceed the financial current date for manual MVP entries;
  audit Instants and translated labels are not embedded in financial identity.
- Enforce approved D05 name/note/Other-source boundaries and M01–M05/T01–T06;
  enforce 1–999,999,999,999 COP per expense/income across UI/domain/storage/backup.
  Follow ADR 0003: reject oversized transactions, never clip them; aggregate values
  are independently checked Long amounts, not subject to the transaction maximum.
  Category uniqueness normalization is shared, deterministic and enforced by real
  command/storage boundaries when they ship, not a UI-only duplicate warning.

### Tests
Table/property-style tests around Long bounds, grouping/separators, empty drafts,
checked addition/subtraction, reordered sums, leap days, and future-date rejection.
Include field-length Unicode boundaries, original invalid-input rejection and the
maximum valid amount preserved by an unrelated-field edit at its owning workflow;
M05 oversized backup rejection and M06 totals above the individual record cap.
Capture red→green evidence and run G1.

### Working checkpoint
Policies run without Android or DI. Existing app/catalog compile; no unvalidated
money enters a newly exposed product workflow.

### Context and starting points
Integer storage alone does not protect parsing, sums or dates. Inputs are the
approved D03/D05 decisions and example corpus from SELLO-001; read architecture §4,
[M/B examples](../../testing/mvp-financial-examples.md#input-and-exact-money-boundaries--d05) and module map.
There is no legacy helper to transplant wholesale.

### Implementation plan
1. Define typed money/currency/effective-date/month values and typed validation failures
   in domain/model/policy; define a draft parse result separately from valid positive money.
2. Implement strict approved `es-CO` input grammar, currency scale/rounding and exact
   Long conversion. Validate the original lexeme; do not strip arbitrary characters.
3. Implement checked add/subtract and widened aggregate sums; final representation
   checks must be independent of iteration order. Keep rates/floating-point out of MVP.
4. Implement approved field/date bounds with injected financial date, including
   manual future-date rejection; codecs/render strings belong to other boundaries.
5. Create table-driven domain tests before production logic and document stable
   public contracts for controls/Room/backup consumers. Do not add pass-through use cases.

### Concrete cases and pitfalls
- M01/M02 must hold. A mathematical sum `Long.MAX_VALUE + 1 - Long.MAX_VALUE` is
  1 even if a narrow intermediate would overflow; an actual final overflow must fail.
- COP fractional amounts cannot round silently unless the approved boundary explicitly
  permits conversion. Zero/empty draft and zero valid budget are different concepts.
- A valid calendar date after the injected current date is still invalid manual entry;
  date parsing failure must not return raw text as if valid.

### Verification recipe
Create/run `./gradlew :domain:test --tests '*MoneyPolicyTest'` and `*DatePolicyTest`,
then G1. Record independent boundary tables and red→green output. Tests must execute
on JVM without Android, database or DI initialization.

### Execution progress
2026-10-09: started on branch `sello-010-money-dates-validation` after SELLO-006 merged.
No new product decision was needed: D05 and ADR 0003 were already approved.

Delivered in `:domain`, with no Android, DI or new dependency: `Outcome`; `Currency`
(COP scale 0 as the only MVP entry currency, USD/EUR/GBP scale 2 nameable only);
`Money` with checked `+`/`-`; `ExactTotal` for widened sums; `TransactionAmount`
(1–999.999.999.999 COP); `CopAmountInput.parse`; `EffectiveDates` (strict date and
month parsing, manual-entry and month-selection rules against an injected today);
`CategoryName` with `uniquenessKey`, `Note`, `SourceName` and `IncomeSource`.
`docs/development/money-dates-text.md` states the contracts for controls, Room and backup.

Interpretations taken where the approved text is silent, each the stricter reading,
for owner review:
- Leading zeros (`007`, `0.123`) are rejected, not dropped. A single `0` parses.
- Only whitespace at the ends is ignored; a space or non-breaking space inside a
  number is rejected, as are non-ASCII digits.
- Text is stored with composed accents (NFC), so `Café` counts 4 code points however it
  was typed. The uniqueness key also ignores compatibility forms (full-width letters),
  `ß`/`SS` and repeated inner spaces; `Año` and `Ano` therefore conflict.
- Control characters and line breaks are rejected in names, notes and source names, so
  notes are single-line.
- A name supplied with a source other than Otro is rejected, not ignored.
- Dates accept only four-digit years in `yyyy-MM-dd`; no earliest-date limit was invented.
- Month selection up to the current month is included here because architecture §6
  forbids future months and it is the same rule as future-dated entries.

Not delivered here because the owning workflow does not exist yet: the keypad's
thirteenth-digit feedback (M04, SELLO-008; `MAX_DIGITS` is provided), the maximum
amount surviving a note edit and backup rejection before mutation (M05, SELLO-015 and
SELLO-028/030), and uniqueness enforced by storage (T01/T02, SELLO-011). This ticket
supplies and tests the shared rule each of them must call. Currency conversion
rounding has no code: no MVP path converts.

2026-10-09: owner approved pull request 5, including the interpretations above; moved
to Done for a squash merge.
