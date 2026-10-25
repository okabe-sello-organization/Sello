# SELLO-107 — Store investments, movements, values and the USD rate

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-106, SELLO-004
- **Gate:** G2

### Outcome
Investments and their history live in Room, exactly, in COP or USD, with a dated app-wide rate.

### Deliverables
Domain models and policies, schema migration, commands with receipts and undo, reads with typed failures. Per the [gap list](../../../design/v2/gaps/inversiones.md): USD amounts beside the COP-only `TransactionAmount`, a decimal rate type and an undo kind for investment movements.

### Acceptance criteria
- Decimal rates, never floating point; amounts exact in their currency.
- Withdrawals above the last value are refused.
- Corrections recompute totals as the examples.

### Tests
Real-Room and domain example tests; migration tests; G2.

### Working checkpoint
An investment history reads back with the approved figures.

### Context and starting points
Approved examples (SELLO-106); ARCHITECTURE §4–5, §8 FX.

### Implementation plan
1. Domain.
2. Schema.
3. Commands and reads.

### Concrete cases and pitfalls
- USD returns stay in USD; only the total converts.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-107 --retain`.
