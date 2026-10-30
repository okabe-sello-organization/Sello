# SELLO-125 — Add a fixed payment from the month's receipt

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-122, SELLO-004
- **Gate:** G2

### Outcome
On Pagos e ingresos fijos, adding one is no longer a bare + in the top bar: the month's
receipt ends in a tear-off stub, **+ Nuevo pago fijo** (or **Nuevo ingreso fijo** on
Ingresos), that opens the entry with Se repite on. With nothing repeating, the receipt
shows the month at 0 with the same stub, as concept 1 in `artifacts/conceptos-cta.png`,
made compact on the owner's review: one line, the same size on both tabs, about half the
room around its words.

### Deliverables
- The summary slip ends in a tear line and a stub whose button follows the tab shown and
  opens the expense or income entry with Se repite on.
- With nothing repeating on the tab: the same slip with the month's title, 0, "Aún no
  tienes pagos fijos" (or "ingresos fijos") and the same one-line stub.
- The stub is compact: a 48dp button, and about half the room between the tear line, its
  words and the slip's edge.
- When the month's sum does not fit, the figures stay hidden and the stub still shows.
- The top bar's + is removed; the footnote no longer tells how to add one.
- The v2 reference's list paragraph describes the stub.

### Acceptance criteria
- The stub adds to the tab shown: a payment on Pagos, an income on Ingresos.
- Nothing repeating never hides the way to add one, and shows no negative or invented
  figure; a failed read still says so, with no stub.
- The stub is one 48dp button, the same size on Pagos and Ingresos, empty or not, said
  as its label, and stays reachable at large text; both themes read.
- The list, its groups and the rows open their schedules as before.

### Tests
- App device: the stub inside the receipt, its label per tab and what it opens; the empty
  receipt per tab; the stub's size on both tabs; the stub when the sum is hidden; no stub
  on a failed read; large text.
- App device journey: adding an income that repeats from the empty Ingresos receipt.

### Working checkpoint
On the running app the screen looks like concept 1 in both states and both themes.

### Context and starting points
`feature/recurrence/SchedulesScreen.kt` (`Summary`), the schedules route in
`navigation/SelloApp.kt`; `TearLine` and `StubButton` from SELLO-123; the concepts in
`artifacts/`.

### Implementation plan
1. The screen takes an add action per tab; the summary gains the tear line and stub.
2. The empty receipt; the stub when the sum is hidden.
3. The route without the +; the tests and the design reference.

### Concrete cases and pitfalls
- An empty tab may come with or without a month plan: the title uses the current month.
- Switching tab changes the stub's label and target at once.
- No schedule is created here: the entry and its Se repite sheet do that, unchanged.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-125 --retain`.

### Execution progress

- **2026-10-26:** Built as follows, proposed for review with this ticket's pull request.
  The month's receipt ends in a tear line and a `StubButton`, **Nuevo pago fijo** or
  **Nuevo ingreso fijo** by the tab, opening the expense or income entry with Se repite
  on. With nothing repeating, the receipt shows the month's title, 0 when there is a plan,
  "Aún no tienes pagos fijos" (or "ingresos fijos") and the stub; with a sum that does not
  fit, the stub alone. After trying it on the phone the owner asked for a compact stub:
  no example line, the same size on both tabs and half the room around it, so
  `StubButton` takes a `minHeight` (48dp here) and the slip and tear line sit closer. The
  top bar's + is gone, the footnote keeps only that they record themselves, and the v2
  list paragraph and HANDOFF describe the stub.
