# SELLO-106 — Approve investment rules and worked examples

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-105, SELLO-004
- **Gate:** G1

### Outcome
Reviewed rules and independent examples for investments before any code.

### Deliverables
Examples for contributions, withdrawals (never above the last value), dividends, values, the simple return, missing values, USD with the app-wide rate and corrections. Added by SELLO-105 with the owner's approval (2026-10-25): the designs the locked v2 page lacks, extending `inversiones.html` and locked by owner delegation: the currency choice and the rate asked first in Agregar inversión, how USD amounts and the rate read, the Sello pad's comma key for USD, the rate setting behind the tab's settings, the total's rate line, what the valuation footnote opens and the shades after the fourth ([gap list](../../../design/v2/gaps/inversiones.md)).

### Acceptance criteria
- Every ADR 0017 investment decision has examples with exact amounts.
- Rate validation (positive, bounded, decimal) and its date are defined.
- The owner approves; open points are listed. Owner answers of 2026-10-25: a value carries forward as the last recorded value plus aportes minus retiros after it, keeping its date; the overview converts value, movements and return to COP at the current rate and names it; a USD investment asks for a rate first; USD takes cents; a rate is COP per USD, 1 to 100.000 with at most two decimals, dated today or earlier, the latest date winning; merging the pull request approves the examples.
- The added screens have default, empty, loading, failed-read, large-text and dark frames, and the README records them as locked by delegation.

### Tests
Owner review; G1.

### Working checkpoint
Approved examples exist.

### Context and starting points
[ADR 0017](../../../decisions/0017-goals-recurrence-investments.md); Inversiones brief; ARCHITECTURE FX rules.

### Implementation plan
1. Draft.
2. Review.
3. Record approval.

### Concrete cases and pitfalls
- Never a 0 % return without contributions.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-106 --retain`.
