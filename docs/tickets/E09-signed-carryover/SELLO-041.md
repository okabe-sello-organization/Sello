# SELLO-041 — Deliver accessible carryover opt-in and availability explanations

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-040, SELLO-020, SELLO-022, SELLO-023, SELLO-004
- **Gate:** G2

### Outcome
Users can knowingly activate the approved policy and understand why availability
differs from the configured monthly limit, including after historical corrections.

### Deliverables
Working approved-scope enable/disable flows, base/carry/effective/remaining breakdowns
in affected Recibo/detail/Resumen surfaces, contextual guidance and settings/draft
restoration. Reusable primitives and real catalog examples follow Sello's visual language.

### Acceptance criteria
- Activation and transitions use real commands and confirmed receipts; no hidden
  default enablement or success from an unimplemented control.
- Exact accessible positive/negative values and source periods explain signed carry.
  Negative capacity is visible; zero daily allowance does not hide the deficit.
- Forecast/verdict/graphs/order/income copy uses domain policy consistently. No-carry
  mode and existing journeys remain usable; labels never claim a verified bank balance.
- Historical corrections expose the approved downstream impact before confirmation
  and refresh one consistent revision after commit. Draft/version conflicts recover.
- Loading/empty/error/busy/restored states, themes, reduced motion and large-font/adaptive
  layouts work; catalog fixtures/controls stay outside customer artifacts.

### Tests
ViewModel and connected Compose flows for activation/transitions, exact breakdown,
historical impact, duplicate submission, stale draft, read/commit errors and recreation.
TalkBack/large-font/adaptive/theme checks and actual catalog interactions; G2.

### Working checkpoint
Carryover is now a complete customer journey with recovery support, not a toggle
on unfinished mathematics/storage. Existing MVP no-carry scenarios still pass.

### Context and starting points
Use accepted SELLO-037 scope/copy, real SELLO-039 ports and SELLO-040 recovery
guarantees. Inspect existing SELLO-020/022/023 roots/states/components; the visual
reference has no authoritative carryover layout, so obtain approval for the extension.

### Implementation plan
1. Obtain design approval for the minimal Sello component/layout extension and
   map immutable states/actions/events to actual domain render values.
2. Add failing presentation/semantics tests for signed breakdown and non-carry regression.
3. Implement real opt-in/transition command flows with receipt recovery and version
   conflict handling; show start period and approved consequences explicitly.
   Integrate customer carry-enabled restore support from SELLO-040 only once affected
   screens explain the policy; prove the former rejection is replaced by a complete journey.
4. Extend affected snapshots/screens/graphs/order and persistent help, reusing domain
   values and design-system primitives rather than local formulas.
5. Add interactive catalog states, execute device/a11y/restoration tests and G2;
   request design and financial-meaning review before acceptance.

### Concrete cases and pitfalls
- Base 100,000 plus carry −20,000 gives capacity 80,000 under approved example
  assumptions; the UI must not relabel the base as 80,000 or hide the −20,000.
- Moving an old transaction between months can affect more than one period;
  a generic “only this month changes” message would be false with signed carry.
- Plotting approximate coordinates never replaces exact selected/accessible values.

### Verification recipe
Run affected root/ViewModel/Compose tests and catalog examples, then G2. Capture
approved designs, exact values, opt-in/restore/disable journeys and actual device evidence.
