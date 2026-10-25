# SELLO-109 — Add the Inversiones tab overview

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-107, SELLO-004
- **Gate:** G2

### Outcome
Inversiones is a main tab leading with value, a Gana/Pierde stamp and the signed simple return.

### Deliverables
The v2 overview with capital and gain, distribution, each value's date, and missing-value handling. Reuses `TitleBar`, `AnswerSlip`, `Stamp`, `PerformanceLine`, `LeaderLine`, `Footnote`, `SplitBar`, `InvestmentRow`, `EmptySlip` and the ink dock ([gap list](../../../design/v2/gaps/inversiones.md)); extends `AnswerSlip` so Pierde keeps the value in ink, fixes the spacing of the big percentage's comma, gives `SplitBar` its gap, and draws the no-value circle dashed with its caption whole.

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
