# SELLO-109 — Add the Inversiones tab overview

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-107, SELLO-004
- **Gate:** G2

### Outcome
Inversiones is a main tab leading with value, a Gana/Pierde stamp and the signed simple return.

### Deliverables
The v2 overview with capital and gain, distribution, each value's date, and missing-value handling. Reuses `TitleBar`, `AnswerSlip`, `Stamp`, `PerformanceLine`, `LeaderLine`, `Footnote`, `SplitBar`, `InvestmentRow`, `EmptySlip` and the ink dock ([gap list](../../design/v2/gaps/inversiones.md)); extends `AnswerSlip` so Pierde keeps the value in ink, fixes the spacing of the big percentage's comma, gives `SplitBar` its gap, and draws the no-value circle dashed with its caption whole.

### Acceptance criteria
- The total converts USD at the current rate and says its date.
- An investment without a value is left out of the total and only its row says so.
- Loading, empty and failed reads are distinct; never zero.

### Tests
Screen and ViewModel tests across windows and themes; G2.

### Working checkpoint
The overview shows the approved figures.

### Context and starting points
v2 Inversiones page.

### Implementation plan
1. Tab.
2. Overview.
3. States.

### Concrete cases and pitfalls
- Goals are never added to the total.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-109 --retain`.

### Execution progress

- **Part 1 (2026-10-25):** the overview is drawn and tested but not yet in the tab bar:
  until SELLO-110 adds Agregar inversión and Anotar movimiento, the tab could show
  nothing a person could act on, and an unfinished tab is never shown (architecture §1).
  `InversionesViewModel` and `InvestmentsRenderModel` turn the board into the answer slip
  (value with Gana / Pierde in ink, the signed return, Aportaste, Retiraste, Dividendos y
  rentas, Ganancia, the valuation dates as a quiet line and the rate line), the bar and
  the rows, with loading, failed, stale, empty, no-values and missing-rate states.
  Design-system extensions from the gap list: `AnswerSlip(lossAmount)`, the return's
  comma without tabular figures and an even (neutral) look, `SplitBar(gap)`, shades that
  repeat after the fourth, the dashed no-value circle with its caption whole, and
  `InvestmentRow` without a click until rows open something. Storage is bound in
  composition, and in debug builds investment commands go through the sandbox's fence
  and fault injectors.
- **Part 2 (2026-10-25), with SELLO-110:** the tab is in the bar with **+**, the dock
  (**Agregar inversión** when empty, **Anotar movimiento** in ink otherwise), rows opening
  the investment and the total's rate line opening Dólar; the list's heading lost its
  tabular comma. SELLO-110's pull request closes this ticket.
