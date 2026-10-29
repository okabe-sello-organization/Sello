# SELLO-007 — Deliver paper, exact money and feedback components

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-006, SELLO-010, SELLO-004
- **Gate:** G2

### Outcome
Screens can present financial answers, loading, emptiness and failures consistently.

### Deliverables
Slip/pinked shape, TearLine, LeaderLine, TotalLine, KeyValue, MoneyText, Stamp,
ConfirmSlip, EmptySlip, ErrorSlip and SlipSkeleton; resource-based shared `es-CO`
formatting in app presentation and simple exact render inputs in design-system.
Add grouped previews/catalog fixtures for every applicable state.

### Acceptance criteria
- Amounts retain exact values and currency-aware semantics; abbreviations never
  replace receipt/list values. True minus and tabular digits follow the reference.
- Large Long-boundary amounts wrap without clipping at font scale 2.0; stamps
  announce ordinary words and reduced motion exposes the final answer immediately.
- Error/retry/busy/confirmation interactions have accessible roles and 48dp targets.

### Tests
Money formatting boundaries, semantic-node/currency assertions, reduced-motion
and callback UI tests in four themes; visual review of pinked edges/tear lines.

### Working checkpoint
Catalog demonstrates real working components; the existing app still renders.
No component depends on domain/entity types or performs a financial write.

### Context and starting points
These primitives make financial UI and errors consistent before entry screens exist.
Read [paper/money/stamps](../../design/sello-spec.html#c-slip), architecture §6,
the Money policy/tests from SELLO-010 and tokens from SELLO-006.
Shared application formatting and design-system rendering have different owners.

### Implementation plan
1. Create paper/tear/leader/total/key-value and stamp APIs using theme tokens and
   modifiers; implement pinked clipping/ripple, not hand-drawn copies in screens.
2. Add exact `es-CO` formatter in app/presentation and stateless formatted-money
   inputs in design-system. Preserve currency/sign/accessibility separately from geometry.
3. Implement empty/skeleton/error/retry/confirm/busy feedback with ordinary semantic
   roles and required callbacks; decorative stamps cannot trigger writes.
4. Register synthetic catalog examples and previews for empty/large/negative amounts,
   long labels, retry/confirmation actions, reduced motion and four color schemes.
5. Validate large-font wrapping, divider spacing, hit areas and reader order; app
   still launches through shared components without a repository dependency in catalog.

### Concrete cases and pitfalls
- Display COP 1,234 as full grouped value; a value near Long maximum must never be
  converted to Float for its label. Negative uses true minus and announces currency.
- Confirm cancellation invokes only cancellation; ErrorSlip retry invokes exactly
  its callback. One visual success stamp cannot imply a fake committed receipt.
- Reduced motion exposes exact final text immediately; no financial value exists
  only midway through a count animation. Clip pinked shapes, not text/content bounds.

### Verification recipe
Create/run `*MoneyFormatterTest`, `*MoneySemanticsTest` and feedback interaction UI
tests, then G2 for design-system/catalog. Review reference shape/spacing screenshots
at scales 1.0/1.3/2.0; list every implemented example ID in delivery evidence.

### Execution progress
2026-10-09: started on branch `sello-007-paper-money-feedback` after SELLO-010 merged.
No new product decision or dependency was needed.

Delivered in `:design-system` (`component`): `Slip` with `PinkedBottomShape`,
`TearLine`, `LeaderLine`, `TotalLine`, `KeyValue`, `MoneyText`, `CountingMoneyText`,
`Stamp`, `ConfirmSlip`, `EmptySlip`, `ErrorSlip`, `SlipSkeleton` and a minimal
`SelloButton`, which the feedback slips need before SELLO-008 extends it. `SelloTheme`
now takes `reducedMotion`, defaulting to the system setting. Delivered in `:app`
(`presentation.money`): `MoneyFormatter` with resource-backed `ResourceMoneyLabels`.
The design system draws a pre-formatted `MoneyTextValue` and knows nothing of `Money`.
The catalog gained a Motion control and these example IDs: `paper.slip`,
`paper.tear-line`, `paper.leader-line`, `paper.total-line`, `paper.key-value`,
`stamps.stamp`, `money.money-text`, `money.counting`, `inputs.button`,
`feedback.confirm-slip`, `feedback.empty-slip`, `feedback.error-slip`,
`feedback.slip-skeleton`.

Choices made during the work, for owner review:
- Tabular figures now apply to the display, headline and title roles and to money and
  receipt values, not to body and label text. In Schibsted Grotesk the feature also
  widens points, commas and colons, which visibly broke sentences; the reference's own
  stylesheet applies it to figures only. This revises a SELLO-006 token.
- `MoneyText` takes a formatted value, where the reference sketch took a `Long`, so
  formatting stays in the application as this ticket requires. Counting takes the
  application's formatter as a parameter.
- A figure too wide for its place wraps after a thousands point. Hero first steps down
  to Total, as the reference says.
- Spoken amounts use `peso`/`pesos`, `menos` and `más`. Non-COP currencies format with
  two decimals and their code but have no MVP use.
- The empty and error slips use an outlined button, keeping one filled button per screen.
- The confirmation has a `busy` state so its action cannot be requested twice.

2026-10-09, owner review of the running catalog: the owner chose to show pesos with no
currency symbol and other currencies as code, space, figure (`USD 2.340,00`), recorded
as [ADR 0007](../../decisions/0007-no-peso-symbol.md). This supersedes the
reference's raised peso sign. The tests were changed first and six failed before the
formatter and `MoneyText` were updated; the gate was run again on the result.

2026-10-09: owner approved pull request 6, including the choices above; moved to Done
for a squash merge.

Found and fixed during the work: the device test caught `BigInteger.longValueExact`,
which does not exist on API 30. Some escape sequences in Kotlin sources, including two
SELLO-010 test files, had been saved as the literal invisible characters; they are
escapes again, with no behaviour change.

Left for later: the customer app does not call the formatter or components yet (first
slice, E04). `SlipSkeleton` leaves the 150 ms show delay to its caller. The reduced
motion setting is read when the theme is composed, not observed while running. The
snackbar with undo and `SlipHeading` are not in this ticket's deliverables.
