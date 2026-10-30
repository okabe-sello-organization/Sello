# SELLO-001 — Approve the MVP contract and financial examples

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** None
- **Gate:** G0

### Outcome
Remove ambiguity before implementing figures or recovery. Ratify the architecture's
MVP, two-tab exception and developer tools; do not expand scope to recurrence/goals.

### Deliverables
An approved scope/decision record and versioned worked examples for day inclusion,
variable pace, rounding, projection, 95/100% verdict boundaries, no-budget behavior,
historical limits and income allocation. Record privacy/plaintext backup policy,
supported API range and app/debug/catalog identities. Verify the existing local
Sello snapshot/provenance; do not fetch another worktree as a build prerequisite.

### Acceptance criteria
- Product owner explicitly approves the open decisions; unresolved policy cannot
  be replaced by a developer guess. Examples include day 1, last day, February,
  zero spending, unlimited categories, over-budget and insufficient-history states.
- Category rename/archive and current versus prior-month budget edits have defined
  outcomes. Forecast labels never imply saved actuals; savings is not account balance.
- Design snapshot has no build-time external-path dependency or unrelated legacy files.

### Tests
Review example arithmetic independently, validate Markdown links and the board,
and verify baseline G0 still passes. Documentation-only changes need no invented red test.

### Working checkpoint
Approved decisions are usable inputs to domain/UI tickets; generated app still
builds/launches. Missing approvals keep this ticket out of Done.

### Context and starting points
The design contains sample amounts and legacy migration hints, not approved new
financial policies. Without this task, separate actors will choose different pace,
budget history and reset semantics. Read [ADR 0001](../../decisions/0001-mvp-contract.md),
architecture §§1/4/7/9 and the
[local design](../../design/sello-spec.html#build). No predecessor implementation exists.

### Implementation plan
1. Create `docs/decisions/0001-mvp-contract.md` using D01–D10 as individual decisions;
   record owner, chosen answer, rationale, alternatives, approval/date and unresolved items.
2. Resolve each budget/forecast/order/field/reset question explicitly. Define formulas,
   included dates, rounding and labels; distinguish fixed rules from reviewed proposals.
3. Create `docs/testing/mvp-financial-examples.md` with M/B/V/H/I/G examples, at least
   three approved forecast calculations, February/leap-year and first/last-day cases.
4. Review examples independently with the product owner. If answers are unavailable,
   deliver the proposal and blocker; never invent “approved” finance/privacy semantics.
5. Verify design provenance; update architecture only for accepted guardrail changes.
   Leave implementation, toolchain changes and downstream ticket statuses untouched.

### Concrete cases and pitfalls
- Spending 50,000 across limited/unlimited categories must reconcile to B01, not
  discard unlimited expenses. Past limits cannot be read from today's category defaults.
- Decide whether forecast comparison uses exact or rounded projection at 95%/100%;
  the allowance's inclusive today is not automatically the forecast's day convention.
- UI keypad limits and Long import/display range need separate explicit answers.
  A theme preference reset does not imply permission/device state can be restored.

### Verification recipe
Run `python3 docs/planning/mvp/board.py --check` and G0 from
[gate profiles](../../development/quality-gates.md); check decision/example links and snapshot
hash. Evidence must include approver/date and independently checked inputs/outputs.
No behavior red test or device run is required for this documentation-only task.

### Execution progress
2026-10-08: drafted [D01–D10 proposals](../../decisions/0001-mvp-contract.md)
and [versioned worked examples](../../testing/mvp-financial-examples.md).
2026-10-08: project owner approved D01 scope and D03 forecast/day-inclusion/verdict
rules, with daily allowance rounded down to COP 50 multiples. Recorded per-decision
approval and updated example/architecture contracts; no other choices were approved.
Next: owner reviews D04–D07, followed by D08/D09/D02/D10. Independent full-corpus
review remains outstanding. Ticket stays In Progress; downstream prerequisites
are not satisfied by this partial approval.
2026-10-08 continuation: refined the still-unapproved D04 skipped-month proposal to
resolve known historical defaults from effective-month evidence, independent of app
opening, and explicitly exclude leftover/overspend rollover. Updated H03–H05;
request owner approval of zero/unlimited, monthly carry-forward and historical edits.
2026-10-08 follow-up: owner approved zero/unlimited distinction, prior-month stability
and historical corrections with discoverable user information. D04 is partially
approved; carryover is explicitly still under discussion. Added option comparison
and H06 guidance case; SELLO-019/020/021 must deliver applicable correction messaging.
2026-10-08 direction: owner confirmed eventual signed surplus/deficit carryover,
possibly optional and outside MVP. Added ADR 0002 and retention/ownership guardrails;
no carryover code/schema or new tickets. MVP activation/default and detailed policy
still need approval; next clarify deferral before moving to the remaining D04/D05 inputs.
2026-10-08 plan approval: owner approved MVP budget renewal without carryover and
post-MVP deferral. Added SELLO-E09/037–043 with release/policy/quality dependencies
and separate generated roadmap views; MVP remains eight epics/36 tickets. Detailed
carryover decisions are owned by SELLO-037, not blockers for SELLO-001.
2026-10-08 skipped-month approval: owner agreed that unopened months resolve budget
configuration effective then, not today's default. Recorded D04 approval, aligned
H03/H04 and added real-Room history/materialization acceptance to SELLO-013.
Next: D05 field/input/source rules, then D06/D07 (including archive budget interactions).
2026-10-08 D05 approval: owner approved all presented name/note/uniqueness, amount
input/range-preservation and income-source rules. Recorded approval, added T01–T06
policy examples and aligned SELLO-010 validation acceptance. Next: D06 ordering,
then D07 archive/undo and the remaining privacy/support/release decisions.
2026-10-08 D06 approval: owner approved all presented metric/direction/tie and
unlimited-last ordering rules. Recorded approval, added O01–O08 fixture expectations
and aligned SELLO-023. Archive visibility/budget interactions remain proposed D07;
prepared explicit current/future-month archive consequences for the next discussion.
2026-10-08 D07 approval: owner approved archive/restore/history labels, explained
current/future-month budget effects and six-second real-time undo with process-death
offer expiry/outcome recovery. Closes D04/D06 archive decisions; added AR01–AR06/
U01–U05 and aligned SELLO-013/019/020/021. Next: D08 privacy/portable backup policy.
2026-10-08 D08 approval: owner approved all presented manual plaintext/privacy/
contents/retention choices and requested committing accumulated work. Recorded D08,
added P01–P04 and aligned SELLO-029/032; implementation is still outstanding.
Next: D09 restore/reset, then D02 identities/support and D10 release/review ownership.
2026-10-08 D09 approval: owner approved all three replacement, settings-only recovery
and counted/BORRAR reset recommendations. Recorded approval and R01–R06 expectations;
aligned SELLO-030/031 acceptance. Implementation and independent review remain pending.
Next: D02 identities/support, then D10 release/review ownership.
2026-10-08 D02 approval: owner approved isolated customer/debug/catalog identities
and SDK 30 floor subject to SELLO-002 verification, selecting private APK distribution
only. Added Iden01/Iden02 and aligned toolchain/release guidance; no signing custody,
device owner, D10 process or independent review is implied. Next: D10.
2026-10-08 D10 approval: owner approved release matrix, measured performance target
process and independent review/ownership requirements. D01–D10 policy choices are
now approved. Named technical/device/signing owners were not supplied; full-corpus
independent review remains outstanding, so SELLO-001 stays In Progress. Aligned
the performance owner reference to SELLO-035, whose ticket owns measurements/targets.
2026-10-08 ownership/walkthrough: user will handle periodic physical-device testing
and release build/signing. Recorded ownership without claiming completed evidence;
independent technical reviewer remains unassigned. Started illustrative owner review
in four batches; B01/B02/B04/B05/B06/B07 response pending. No batch or full corpus
is marked reviewed merely because the user requested a walkthrough.
2026-10-08 batch 1 feedback: owner confirmed B01/B02/B05/B06/B07 and reiterated
the future funds-reflection/carryover intent. B04 allowance scope needs clarification:
one category's deficit is not necessarily an overall deficit. Explicitly scoped
B04 and added B08's 300,000 limit/110,000 spend/190,000 remaining/19,000 daily
allowance illustration (Oct 22; ten days including today). Owner response pending;
no new financial policy or independent technical review is inferred.
2026-10-08 batch 1 confirmation: owner explicitly confirmed category/global allowance
distinction and B08. Marked the first batch owner-reviewed, aligned architecture and
SELLO-014 acceptance; full-corpus independent technical review remains outstanding.
Presented F01/F02/F03/A01/A02 as the next small calendar/rounding walkthrough;
response pending, with February/zero-history/verdict-boundary examples still to follow.
2026-10-08 batch 2 partial confirmation: owner confirmed the first-day estimate,
mid-month allowance/forecast, last-day behavior and rounding examples. Recorded
F01 forecast, F02/F03/B03/A01/A02 review without extending it to unseen boundaries.
Now presenting remaining F/A/V cases: short/leap months, successful zero versus
no-budget, first-day allowance, sub-50/50/100/negative allowance and exact verdict
boundaries. Owner response pending; SELLO-001 remains In Progress.
2026-10-08 batch 2 completion: owner confirmed remaining F/A/V edge cases, including
short/leap calendars, zero/no-budget, small/negative allowance and V05's exact-value
verdict despite display rounding. Marked this batch owner-reviewed, not executed or
independently technically reviewed. Presenting H01–H06/I01–I02/G01–G02 next;
responses pending and SELLO-001 remains In Progress.
2026-10-08 batch 3 partial confirmation: owner answered items 1–4, confirming
I01/I02 and H01–H06 income/history/renewal examples. Item 5 (G01/G02 cumulative
graph correction and total reconciliation) was omitted; re-presented for explicit
response, not marked reviewed. No implementation or independent review is claimed.
2026-10-08 figure presentation approval: owner accepted keeping net recorded flow,
budget remaining and allocation with explicit labels and Resumen/Recibo hierarchy.
Recorded Ingresos menos gastos / Restante del presupuesto / Por asignar al presupuesto,
replacing ambiguous Sin destinar; aligned architecture, I/G cases and SELLO-022
acceptance. Allocation is not income minus spending; formulas unchanged. Full graph
example review and independent technical review remain pending; no UI implementation exists.
2026-10-08 graph confirmation: owner explicitly confirmed G01/G02 correction
effects after the allocation-label clarification. Batch 3 is owner-reviewed;
independent technical review remains separate. Presenting M01–M05 money input
cases next, with response pending. No validation/UI implementation exists yet.
2026-10-08 money input response: owner confirmed M01–M04 and rejected wider backup
transactions. Revised D05/M05 to the same 12-digit per-transaction range everywhere,
recorded ADR 0003, added M06 aggregate-scope case and aligned SELLO-010/030. Original
approval history is retained but superseded; no persisted data or financial code
exists to migrate. Other batch 4 cases and independent review remain outstanding.
2026-10-08 naming/source walkthrough: presented T01–T06 category trim/case/accent
duplicates including archived identities, name/note limits, required Otro name and
manual income-source meanings. Owner response pending; policies are previously
approved D05, not newly implemented validation or independent technical review.
2026-10-08 naming/source confirmation: owner explicitly confirmed all five items,
covering T01–T06. Recorded review without claiming implementation/technical review.
Presented AR01–AR06 archive/restore/history scenarios next; response pending.
O01–O08 ordering follows separately; SELLO-001 remains In Progress.
2026-10-08 archive confirmation: owner confirmed all five AR01–AR06 behaviors.
Marked those examples owner-reviewed, not executed or independently technically
reviewed. Presented O01–O08 selected-month metric/direction/tie/unlimited and
configuration-audit-time ordering next; owner response pending.
2026-10-08 sorting response: owner confirmed count/amount/limit/alphabetical and
direction/tie/unlimited cases but changed latest activity to last expense addition,
not modification. Updated D06/architecture/SELLO-023 and retired O06's configuration
expectation. Latest-addition selected-month/global scope, creation/effective time,
deletion/empty and restored-time proposals need clarification; no implementation
or blanket example approval is claimed.
2026-10-08 recency confirmation: owner approved all three selected-month/original
addition/surviving-and-empty recommendations and clearer label. Finalized D06,
ADR 0004, architecture/SELLO-023; replaced O06/O08 and added O09–O14 boundary
fixtures from approved policy, with independent fixture review still pending.
Presenting U01–U05 undo/restart/conflict examples next; response pending.
2026-10-08 undo confirmation: owner explicitly approved all U01–U05 scenarios.
Marked those owner-reviewed without claiming storage/device/technical verification.
Presenting P01–P04 backup privacy/contents/provider/recovery/retention scenarios
next; response pending. Restore/reset and installation review remains outstanding.
2026-10-08 backup confirmation: owner approved all four P01–P04 cases. Marked
those owner-reviewed, not platform/file tested. Presented R01–R06 restore/reset
and duplicate-submit/unknown-commit contracts next; response pending. Installation
examples and independent technical review remain outstanding.
2026-10-08 recovery confirmation: owner approved all six R01–R06 and duplicate/
unknown-commit scenarios, asking to replace vague ajustes pendientes. Recorded
named failed-preference/committed-history/targeted-retry copy requirements in D09,
R03 and SELLO-030; literal Spanish proposal awaits response/UI review. Installation
review and independent technical review remain outstanding; no recovery code exists.
2026-10-08 restore-copy approval: owner accepted concrete Spanish wording with
named failed preferences and targeted retry/no-second-replacement reassurance.
Recorded D09 baseline approval and aligned SELLO-030; actual rendering/retry tests
remain future implementation. Presenting Iden01/Iden02 installation/isolation/
customer-artifact/private-APK scenarios next; response pending, not device evidence.
2026-10-08 installation confirmation: owner approved all four Iden01/Iden02 items.
Owner walkthrough is complete, with all D01–D10 choices and requested revisions
recorded. No further owner question remains in the presented batches. Independent
technical/fixture review is still outstanding; SELLO-001 stays In Progress rather
than claiming that owner approval executed feature/device gates. SELLO-002 remains
independently Ready and can verify the actual toolchain while review is arranged.
Walkthrough-completion verification: base `d8d58a9` plus accumulated uncommitted
architecture/decision/input/example, ADR 0004, foundation/workflow/recovery and
generated MVP CSV edits. 32 board tests, generated-board and diff checks passed;
G0 passed (97 tasks: 2 executed, 95 up-to-date; cached sample JVM test and existing
lint warnings), log `/tmp/sello-owner-walkthrough-complete-g0.log`. Docs-only; no
behavioral red/device test applicable. Independent technical/fixture review remains
required; no feature, device, hosted CI or completed-ticket evidence is claimed.
Restore-copy verification: base `d8d58a9` plus accumulated uncommitted architecture,
decision/input/example, ADR 0004, foundation/workflow/recovery and generated MVP
CSV edits. 32 board tests, generated-board and diff checks passed; G0 passed (97
tasks: 2 executed, 95 up-to-date; cached sample JVM test and existing lint warnings),
log `/tmp/sello-installation-walkthrough-g0.log`. Docs-only; no behavioral red/device
test applicable. Installation response and independent review remain pending.
Recovery-wording verification: base `d8d58a9` plus accumulated uncommitted
architecture/decision/input/example, ADR 0004, foundation/workflow/recovery and
generated MVP CSV edits. 32 board tests, generated-board and diff checks passed;
G0 passed (97 tasks: 2 executed, 95 up-to-date; cached sample JVM test and existing
lint warnings), log `/tmp/sello-restore-wording-g0.log`. Docs-only; no behavioral
red/device test applicable. Literal copy response/installation/technical review pending.
Backup-confirmation verification: base `d8d58a9` plus accumulated uncommitted
architecture/decision/input/example, ADR 0004, foundation/workflow and generated
MVP CSV edits. 32 board tests, generated-board and diff checks passed; G0 passed
(97 tasks: 2 executed, 95 up-to-date; cached sample JVM test and existing lint
warnings), log `/tmp/sello-backup-confirmation-g0.log`. Docs-only; no behavioral
red/device test applicable. Recovery response and independent review remain pending.
Undo-confirmation verification: base `d8d58a9` plus accumulated uncommitted
architecture/decision/input/example, ADR 0004, foundation/workflow and generated
MVP CSV edits. 32 board tests, generated-board and diff checks passed; G0 passed
(97 tasks: 2 executed, 95 up-to-date; cached sample JVM test and existing lint
warnings), log `/tmp/sello-undo-confirmation-g0.log`. Docs-only; no behavioral
red/device test applicable. Backup response and independent review remain pending.
Recency-confirmation verification: base `d8d58a9` plus uncommitted architecture,
decision/input/example, ADR 0004, foundation/workflow and generated MVP CSV edits.
Executor fixture checks passed for recency/reverse, delete fallback and time ties;
not production sorting tests or independent review. 32 board tests, generated-board
and diff checks passed; G0 passed (97 tasks: 2 executed, 95 up-to-date; cached
sample JVM test and existing lint warnings), log `/tmp/sello-recency-confirmation-g0.log`.
Docs-only; no behavioral red/device test applicable. Undo response/review pending.
Latest-addition direction verification: base `d8d58a9` plus uncommitted architecture,
decision/input/example, ADR 0004, foundation/workflow and generated MVP CSV edits.
32 board tests, generated-board and diff checks passed; G0 passed (97 tasks: 2
executed, 95 up-to-date; cached sample JVM test and existing lint warnings), log
`/tmp/sello-latest-addition-direction-g0.log`. Docs-only; no behavioral red/device
test applicable. Comparator details and independent review remain pending.
Archive-confirmation verification: base `d8d58a9` plus uncommitted decision/example,
foundation progress and generated MVP CSV edits. 32 board tests, generated-board
and diff checks passed; G0 passed (97 tasks: 2 executed, 95 up-to-date; cached
sample JVM test and existing lint warnings), log `/tmp/sello-ordering-walkthrough-g0.log`.
Docs-only; no behavioral red/device test applicable. Sorting response and independent
technical review remain pending; no implementation or completed ticket is claimed.
Naming-confirmation verification: base `d8d58a9` plus uncommitted decision/example,
foundation progress and generated MVP CSV edits. 32 board tests, generated-board
and diff checks passed; G0 passed (97 tasks: 2 executed, 95 up-to-date; cached
sample JVM test and existing lint warnings), log `/tmp/sello-archive-walkthrough-g0.log`.
Docs-only; no behavioral red/device test applicable. Archive response and independent
technical review remain pending; no implementation or completed ticket is claimed.
Naming walkthrough verification: base `d8d58a9` plus uncommitted example/progress
and generated MVP CSV changes. 32 board tests, generated-board and diff checks
passed; G0 passed (97 tasks: 4 executed, 93 up-to-date; cached sample JVM test and
existing lint warnings), log `/tmp/sello-naming-walkthrough-g0.log`. Docs-only;
no behavioral red/device test applicable; owner response and technical review pending.
Transaction-range revision verification: base `cae26a3` plus accumulated architecture,
decision/input/example, ADR 0003, foundation/core/workflow/recovery acceptance and
generated MVP CSV edits. 32 board tests, generated-board checks, diff checks and
G0 passed (97 tasks: 2 executed, 95 up-to-date; cached sample JVM test and existing
lint warnings), log `/tmp/sello-transaction-range-g0.log`. Docs-only policy revision;
no behavioral red/device test or actual boundary implementation is claimed.
Graph-confirmation verification: base `cae26a3` plus accumulated architecture,
decision/input/example, foundation/core/workflow and generated MVP CSV edits.
32 board tests, generated-board checks, diff checks and G0 passed (97 tasks: 2
executed, 95 up-to-date; cached sample JVM test and existing lint warnings), log
`/tmp/sello-graph-confirmation-g0.log`. Docs-only; no behavioral red/device test
applicable. Input walkthrough response and independent technical review remain pending.
Figure-label verification: base `cae26a3` plus accumulated architecture/decision/input,
example review, foundation/core/workflow acceptance and generated MVP CSV edits.
32 board tests, generated-board and diff checks passed; G0 passed (97 tasks: 2
executed, 95 up-to-date; cached sample JVM test and existing lint warnings), log
`/tmp/sello-figure-labels-g0.log`. Docs-only label/direction approval, unchanged
arithmetic; no behavioral red/device test applicable or independent review claimed.
Batch 3 partial-approval verification: base `cae26a3` plus accumulated architecture,
decision/input/example, foundation/core and generated MVP CSV changes. 32 board
tests, generated-board checks, diff checks and G0 passed (97 tasks: 2 executed,
95 up-to-date; cached sample JVM test and existing lint warnings), log
`/tmp/sello-history-partial-approval-g0.log`. Docs-only; no behavioral red/device
test applicable. G01/G02 response and independent technical review remain pending.
History/income continuation verification: base `cae26a3` plus accumulated ownership,
architecture/decision/input/example review, foundation/core acceptance and generated
MVP CSV changes. Independent integer checks passed for H01/H02/H04, I01/I02 and
G01/G02; these are executor arithmetic checks, not app tests or independent review.
32 board tests, generated-board and diff checks passed. G0 passed (97 tasks: 2
executed, 95 up-to-date; cached sample JVM test and existing lint warnings), log
`/tmp/sello-history-income-g0.log`. Docs-only; no behavioral red/device test applicable.
Batch 2 continuation verification: base `cae26a3` plus accumulated architecture,
decision/input/example review, foundation/core acceptance and generated CSV edits.
Independent Fraction/integer scratch checks passed for F01/F04/F05/F06, A03–A08
and V05; these are arithmetic checks, not feature tests or independent review.
32 board tests, generated-board and diff checks passed. G0 passed (97 tasks: 2
executed, 95 up-to-date; cached sample JVM test and existing lint warnings), log
`/tmp/sello-forecast-edges-g0.log`. Docs-only; no behavioral red/device test applicable.
Batch 1 confirmation verification: base `cae26a3` plus accumulated ownership/review
decision/input/example, architecture, foundation/core acceptance and generated MVP
CSV edits. Independently checked F02: 60,000/17 → 3,500 downward COP 50 allowance;
40,000 × 31/15 → 82,667 displayed forecast. F01/F03 and A01/A02 match the corpus.
32 board tests, generated-board checks, diff checks and G0 passed (97 tasks: 2
executed, 95 up-to-date; cached sample JVM test and existing lint warnings), log
`/tmp/sello-batch1-confirmation-g0.log`. Docs-only; no behavioral red or device test
applicable. New example approval and independent technical review are not claimed.
Batch 1 clarification verification: base `cae26a3` plus accumulated uncommitted
ownership/review decision/input/example, foundation and generated MVP CSV changes.
B08 independently checked: 3 × 100,000 − 110,000 = 190,000; Oct 22–31 = 10
available days; 190,000 / 10 = 19,000 (already a COP 50 multiple). 32 board tests,
generated-board checks, diff checks and G0 passed (97 tasks: 2 executed, 95 cached;
existing lint warnings); log `/tmp/sello-budget-scope-g0.log`. No device/behavioral
tests applicable to documentation-only clarification; no independent review claimed.
Ownership/walkthrough verification: base `cae26a3` plus uncommitted decision/input,
example review log, foundation progress and generated MVP CSV changes. 32 board
tests, generated-board checks and diff whitespace checks passed; G0 passed (97
tasks: 4 executed, 93 up-to-date; cached sample JVM test and 17 existing lint
warnings), log `/tmp/sello-owner-walkthrough-g0.log`. Docs-only; no behavioral red
or device run applicable. Owner response and independent review remain pending.
D10 verification: base `2c8e9ef` plus accumulated uncommitted D09/D02/D10 decision,
architecture/input/example, epic/shared release guidance and generated CSV changes.
32 board tests, generated MVP/roadmap consistency checks and `git diff --check`
passed; G0 passed (97 tasks: 2 executed, 95 up-to-date; cached sample JVM test and
17 existing lint warnings), log `/tmp/sello-d10-g0.log`. Documentation-only approval
does not require invented red testing or device execution. No independent review,
toolchain support verification or production-signing/device evidence is claimed.
D02 verification: base `2c8e9ef` plus accumulated uncommitted D09/D02 architecture,
decision/input/example, foundation/recovery/release, board README and generated CSV
changes. 32 board tests, both generated-board consistency checks and diff whitespace
checks passed. G0 passed (97 tasks: 2 executed, 95 up-to-date; cached sample JVM
test and 17 existing lint warnings); log `/tmp/sello-d02-g0.log`. No behavioral code
changed, so red testing/device execution is inapplicable to this approval update.
SDK compatibility, side-by-side installation and production signing remain unverified.
D09 verification: base `2c8e9ef` plus uncommitted architecture, decision/input,
foundation/recovery acceptance, example corpus and regenerated MVP CSV changes.
32 board tests, both generated-board consistency checks and `git diff --check`
passed. G0 passed (97 tasks: 4 executed, 93 up-to-date; one cached sample JVM test,
17 existing lint warnings); log `/tmp/sello-d09-g0.log`. Documentation-only approval
needs no behavioral red test or device run. Owner policy approval is not independent
technical review; SELLO-001 remains In Progress. No financial implementation shipped.
Executor checks: 32 board unit tests passed, including meaningful red→green scope
checks (five intended assertion failures before enforcement). Both board/export
sets passed consistency checks; 325 local
documentation file/anchor links, design snapshot SHA-256 and exact arithmetic checks
passed, including 15 COP 50 allowance cases and pre-rounding verdict precision.
G0 (`./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --continue`)
passed with cached JVM test/lint results (one sample JVM test, 17 existing lint
warnings). Latest continuation tested base: `491eb26` plus uncommitted revised
D04 decision/example documents and carryover-direction ADR, architecture/root README/
decision-input/workflow changes, foundation/financial-core/correction-messaging/
recovery-privacy edits,
new post-MVP epic, board generator/tests and generated MVP/roadmap export views.
Carryover/archive arithmetic, field/money-range/deadline boundaries and O01–O08 ordering
illustrations were executor-checked, not executed feature tests or independent review. No device run
required for this documentation task;
D01–D10 product approvals and future signed-carryover direction are recorded
above; no independent review is claimed.

2026-10-08 closure: product owner explicitly authorized independent review agent
Singer (`01a11de6-b349-7b51-9d2d-300c3f9b2b39`). Its complete version-32
technical/example review passed all 88 case IDs, C01/C02 and unnumbered
illustrations, including independently recalculated money and 17 ordering
comparisons. No blocking findings or policy changes were needed. Recorded
review-status/authority clarifications without changing formulas or fixtures;
earlier dated statements remain historical. SELLO-001 is Done. This does not
complete financial implementation, toolchain compatibility or release acceptance.
