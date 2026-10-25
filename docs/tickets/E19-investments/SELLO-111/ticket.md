# SELLO-111 — Set the USD rate

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-107, SELLO-004
- **Gate:** G2

### Outcome
A person sets one USD-to-COP rate with its date; changing it records a new dated rate.

### Deliverables
A rate setting with validation and its history, used by the overview total, reached as SELLO-106 designs it and asked first when a USD investment is created (SELLO-110).

### Acceptance criteria
- Rates are positive, bounded decimals with the date entered.
- The overview states which rate and date it used.

### Tests
Validation and screen tests; G2.

### Working checkpoint
Changing the rate updates the total and says its date.

### Context and starting points
[ADR 0017](../../../decisions/0017-goals-recurrence-investments.md); ARCHITECTURE FX rules.

### Implementation plan
1. Setting.
2. History.
3. Tests.

### Concrete cases and pitfalls
- No network or automatic rates.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-111 --retain`.
