# SELLO-118 — Import FinanceTracker investments once Inversiones exists

- **Type:** Story
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-044, SELLO-108, SELLO-111, SELLO-004
- **Gate:** G2

### Outcome
Investments, their movements and rates recorded in FinanceTracker come into Sello with
the rest of an import, instead of being reported as not imported yet.

### Deliverables
Translation of `investments`, `investmentEntries` and `exchangeRates` into the
investment records of the backup format SELLO-108 delivers, new skip reasons where
needed, and updates to ADR 0019's mapping in the legacy export format.

### Acceptance criteria
- The ADR 0019 rules hold: a record that breaks a Sello rule is left out and counted,
  dependants go with it, and the only repairs are the owner's cut-to-fit and numbered
  names. The translated file passes the released validator unchanged.
- Amounts in other currencies keep their exact minor units and currency; nothing is
  converted with a floating-point rate.
- Every investment, movement and rate is either imported or counted under a reason.
- Owner decisions (2026-10-25, ADR 0019), each counted as changed in the preview: a
  dollar rate with more than two decimals is rounded half up to the centavo from its
  exact decimal text; interest becomes a recorded value of what FinanceTracker's value
  reached; an investment with no value on its first aporte's day gets one equal to that
  aporte; an annual rate no longer adds interest. EUR and GBP investments and rates are
  left out as a currency Sello does not keep; merchant rules stay not imported.

### Tests
Translator table tests over synthetic FinanceTracker investment records; real-Room
restore of a translated file with investments; G2.

### Working checkpoint
Importing a FinanceTracker file with investments shows them in Inversiones with the
same figures as the old app, or says why each one was left out.

### Context and starting points
[ADR 0019](../../../decisions/0019-legacy-import.md),
[legacy export format](../../../data/legacy-export-format.md) and the SELLO-044
translator in `data/backup/legacy`. Learnings from SELLO-044 decide the details: the
real export holds USD and COP investments, value snapshots, cash injections, dividends,
interest and one investment with an annual rate.

### Implementation plan
1. Study investment entries in FinanceTracker's code and a real export, locally only.
2. Present the mapping and open choices to the owner; record them.
3. Extend the translator and its tests; run the gate.

### Concrete cases and pitfalls
- FinanceTracker stores investment amounts as minor units with a currency and rates as
  floating-point numbers; Sello's rates must be decimal and bounded (architecture §8).
- A value snapshot is not a movement; do not turn it into a contribution.

### Verification recipe
Focused translator tests, then `./scripts/verify-ticket SELLO-118 --retain`.
