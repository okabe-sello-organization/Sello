# SELLO-112 — Cover investments in the sandbox and catalog

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-110, SELLO-004
- **Gate:** G2

### Outcome
Sandbox scenarios and catalog states exercise investments through the real paths.

### Deliverables
Scenarios for returns, missing values, USD and corrections; catalog examples for the extensions SELLO-109 to SELLO-111 make, and device tests for the parts that have none: `SplitBar`, `RecordedValuesChart` and the ink dock ([gap list](../../../design/v2/gaps/inversiones.md)).

### Acceptance criteria
- Synthetic data, real commands; none ships in the release build.

### Tests
Scenario and catalog tests; G2.

### Working checkpoint
Each investment scenario reaches its check.

### Context and starting points
ARCHITECTURE §9.

### Implementation plan
1. Scenarios.
2. Catalog.
3. Tests.

### Concrete cases and pitfalls
- Keep clocks distinct.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-112 --retain`.
