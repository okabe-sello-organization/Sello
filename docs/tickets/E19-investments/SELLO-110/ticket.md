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

### Execution progress

- **2026-10-25:** the Inversiones tab joins the bar now that it can be used: **+** and the
  empty dock open **Nueva inversión**, the dock is **Anotar movimiento** in ink, rows open
  the investment and the total's rate line opens Dólar. **The investment**
  (`InvestmentDetailScreen`): "Vale" with Gana / Pierde, the signed return, "Valor anotado
  el … · desde …", the breakdown and **Anotar el valor de hoy**; recorded values as dots
  with what was put in as steps (`RecordedValuesChart` gains its months and legend); the
  movements newest first, each opening its correction. **Anotar movimiento**
  (`InvestmentMovementScreen`): the four types, the investments largest first, the sign
  and label per type, one fact, the withdrawal limit with **Anotar el valor de hoy**, the
  comma pad for dollars, a value of 0 pesos, corrections and deletion with the shared
  Deshacer. **Nueva / Editar inversión** (`InvestmentEditorScreen`): name, description,
  icon, Pesos / Dólares, the first aporte with Lo mismo que aporté / Otro valor, the rate
  asked first for a first dollar investment and sent in the same command, the amount
  kept above the pad while typing, editing and deleting with the confirm slip. The
  movement form and the editor were built by delegated agents and integrated here.
