# SELLO-110 — Record investment movements and values

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-109, SELLO-111, SELLO-004
- **Gate:** G2

### Outcome
Each investment's detail records contributions, withdrawals, dividends and values, with corrections.

### Deliverables
The detail with its history, the movement form with the Sello pad and date, edit, delete and undo, and Agregar / editar inversión with its currency. Reuses `MovementRow`, `RecordedValuesChart`, the entry layout Metas' movement screen assembles, `FormField`, `IconChoiceStrip`, `DangerSlip` and `ConfirmSlip` ([gap list](../../../design/v2/gaps/inversiones.md)); adds the chart's axis and legend, the description field, Vale hoy's choice and the pad's comma key. A USD investment asks for the rate first through SELLO-111's setting.

### Acceptance criteria
- The first value defaults to the first contribution and is editable.
- Dividends add to gain, not value.
- Unknown outcomes are recovered before another write.

### Tests
ViewModel, screen and journey tests; G2.

### Working checkpoint
Movements and values update the investment exactly.

### Context and starting points
v2 Inversiones page.

### Implementation plan
1. Detail.
2. Form.
3. Corrections.

### Concrete cases and pitfalls
- Recording a movement is not a fresh valuation.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-110 --retain`.
