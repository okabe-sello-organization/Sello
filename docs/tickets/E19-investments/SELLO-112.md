# SELLO-112 — Cover investments in the sandbox and catalog

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-110, SELLO-004
- **Gate:** G2

### Outcome
Sandbox scenarios and catalog states exercise investments through the real paths.

### Deliverables
Scenarios for returns, missing values, USD and corrections; catalog examples for the extensions SELLO-109 to SELLO-111 make, and device tests for the parts that have none: `SplitBar`, `RecordedValuesChart` and the ink dock ([gap list](../../design/v2/gaps/inversiones.md)).

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

### Execution progress

- **2026-10-25, sandbox:** investment steps (create, record, correct, delete with Deshacer,
  delete an investment, set the rate) go through the fenced `InvestmentCommands`, and
  checks read the real board: each investment's value and date, contributed, withdrawn,
  dividends, gain and return, and the overview in pesos with its rate. Five scenarios
  reach their checks: `inversiones-pagina@1` (IN02–IN07, IN15, IN17, IN23),
  `valor-arrastrado@1` (IN08–IN16, IN26), `dolares@1` (IN27–IN34), `correcciones@1`
  (IN19–IN25) and `sin-valor@1` (IN17, IN18, IN35), fresh and resumed after a cut, and
  restored from their backups with their last check again. The sandbox's operation log
  and receipt inspector now name investment writes. Built by a delegated agent and
  integrated here.
- **Catalog:** *Investment answer, rows, chart and pad* shows Pierde with the value in
  ink, an even return, six investments with repeating shades and the bar's gap, rows
  that open nothing, the values chart's months and legend, the comma pad and the ink dock
  button; device tests cover the ink dock (`DockToneTest`), the bar's gap and the chart
  (`InvestmentPartsTest`).
