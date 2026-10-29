# SELLO-012 — Save expenses with atomic receipts and uncertain-outcome recovery

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-011, SELLO-004
- **Gate:** G2

### Outcome
A recorded expense has a truthful, recoverable outcome even under double submit or cancellation.

### Deliverables
Narrow create-expense command/workflow port and Room implementation with operation
ID, input identity, data generation, transactional validation/write/receipt, typed
Rejected/Committed/OutcomeUnknown handling and receipt lookup. Establish the reusable
protocol, not a generic universal financial repository.

### Acceptance criteria
- Identical ID/input creates one expense and returns its original receipt; changed
  input under that ID conflicts. Stale generation and missing/archived category reject.
- A failed transaction leaves both financial row and receipt absent. Post-commit
  observer/platform failure cannot become “expense not saved”.
- Cancellation propagates; callers can recover a committed result using the same
  ID without blind resubmission. Receipt timestamps use audit time, not sandbox time.
- No network, file, DataStore or notification work occurs inside the transaction.

### Tests
Real-Room concurrent duplicate submit, same-ID conflicting input, rollback/fault
injection, reopen/receipt lookup and cancellation-around-commit tests; G2.

### Working checkpoint
Tests can record/read an expense through the real workflow after creating validated
test categories; app/catalog still launch. Production UI is connected in SELLO-017.

### Context and starting points
The legacy failure was “saved money reported failed” after follow-up work. Use the
real schema from SELLO-011 and [recovery cases](../../testing/mvp-financial-examples.md#recovery-contracts-to-instantiate-in-downstream-tests--d07d09).
Architecture §5 defines the command outcomes; no form UI belongs to this ticket.

### Implementation plan
1. Define an expense-specific command with operation ID, generation, category ID,
   amount/date/note and creation intent; expose receipt lookup separately from submission.
2. In one Room transaction, reject stale generation/reference/invalid input, resolve
   identical/conflicting operation replay, validate required representable totals, insert
   expense, increment revision and insert the durable receipt with real audit time.
3. Encode canonical command identity consistently so replay compares meaningful input,
   not a mutable UI label or unstable serialized object. Document conflict reasons.
4. Distinguish rollback-established rejection from committed receipt and uncertain
   outcome; propagate cancellation. Follow-up observers never sit inside commit error handling.
5. Build contract tests against real Room with synchronized concurrent submits and
   before/after-commit cancellation points. Preserve IDs/results through DB reopen.

### Concrete cases and pitfalls
- Submit op A twice with identical amount 10,000: one expense/revision/receipt.
  Reuse A with 12,000: conflict and original row unchanged.
- A category deleted/archived after form selection must reject inside the transaction.
- A caller canceled after commit receives cancellation, but later receipt lookup
  proves save; do not “undo” money or silently create op B. A real constraint failure
  must leave both row and receipt absent.

### Verification recipe
Create `ExpenseCommandContractTest` in data/androidTest; use its instrumented class
selector from the guide, then data connected suite and G2. Record row/receipt/revision
assertions independently, including actual rollback and reopen—not only returned results.

### Execution progress
2026-10-09: started on branch `sello-012-expense-commands` after SELLO-011 merged. No
product decision or dependency was needed, and the schema did not change.

Delivered. `:domain`: `OperationId`, `OperationKind`, `OperationReceipt`,
`CreateExpense`, the `ExpenseCommands` port with typed `Committed`, `Rejected` and
`OutcomeUnknown`, and `RecordIdSource`. `:data`: `RoomExpenseCommands`, the input
digest and the strict receipt mapper. `:app`: a random identifier source and the
workflow bound in the graph; no screen calls it yet. `docs/development/commands.md`
states the protocol the next commands reuse.

Choices made during the work, for owner review:
- The order inside the transaction is generation, replay, validation, write. A
  command prepared against a replaced history is refused even if it once committed;
  its original result stays available through lookup.
- Replaying a committed operation returns its receipt even if the category has been
  archived since, because nothing new is written.
- Once the transaction starts it always finishes, and the caller is checked for
  cancellation before and after. A cancelled caller always gets the cancellation,
  never a result.
- A database error before the commit is `Rejected`, because the rollback is certain.
  Only an error from the commit itself is `OutcomeUnknown`.
- The store creates the expense's identifier; the caller supplies only the operation's.
- The input identity is a SHA-256 of the meaningful fields, so a receipt holds no
  amount or note.
- No totals are checked when saving: a month would need over nine million maximum
  records to exceed what a total can hold. Totals are checked where they are computed.
- `Committed` carries no follow-up state because nothing follows a save yet.

Left for later: the form, drafts and success stamp (SELLO-017); category and limit
commands (SELLO-013); edit, delete and undo (SELLO-019); the sandbox scenario that
replays expenses through this workflow (SELLO-024).

2026-10-09: owner approved pull request 11, including the choices above; moved to Done
for a squash merge.
