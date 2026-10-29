# SELLO-037 — Approve signed carryover policy and independent examples

- **Revision:** after review rounds 1 and 2 the gate tested commit `a63958a` on `sello-037-carryover-policy`,
  five commits on `main` at `cbcf588` (#193); `main` had not moved, so no merge was needed. The only
  uncommitted file was the retained report. The status, this file and the handoff are added after.
- **Requirement mapping:** scope, default and opt-in, start month, starting carry, missing and
  skipped history and the continuous formula → [ADR 0020](../../../decisions/0020-signed-carryover-policy.md)
  CP1–CP8, examples SC01–SC07; enable/disable/re-enable, archive/unarchive, finite/unlimited/zero,
  base-limit edits, no caps, negative capacity and out-of-range → CP3, CP9–CP13, CP23–CP25,
  SC08, SC17–SC24, SC40, SC46 (a chain never reaches a month without a finite base); no deficit forgiven or clipped, settlement only by confirmation → CP9,
  CP10, CP12; correction cascades, preview scope, report provenance, forecast and verdict
  denominator, income allocation without carry as income → CP14–CP22, CP27, SC09–SC16, SC25–SC38, SC47 (catch-up notice);
  independently approved cases (three consecutive months, surplus, deficit, deficit larger than
  the next base, skipped months, corrections) → SC01–SC05, SC25–SC30 in
  [signed carryover examples](../../../testing/signed-carryover-examples.md); presentation wording →
  the table in ADR 0020; acceptance-to-ticket mapping → ADR 0020 "Mapping to the tickets"
  (SELLO-038–043). ADR 0020 stands in for the ticket's `0003-signed-carryover-policy.md`, because
  number 0003 is taken by the transaction amount range. ADR 0002's open decisions, `ARCHITECTURE.md`
  §1 and §4 and ADR 0001 D04 now point to it.
  The owner's answers of 2026-10-28 are marked **Owner**; every other rule is **Proposed** with its
  reason and rejected alternative and is approved by merging. No financial choice is left for
  SELLO-038–043.
- **Red / Green:** not applicable: rules and examples, no behaviour and no code. Every expected
  value was worked out by hand and then checked with a throwaway Python script outside the
  repository (not committed, no app code, exact integers): every numeric figure of the 47 examples matched,
  including the COP 50 allowance rounding, the shifted capacities of each correction and the
  `Long` overflow. One hand slip was caught by it (SC17's alternative figure, 130.000 not
  150.000) and fixed before the gate; the figures added in round 1 (SC07, SC37, SC47) were checked the same way.
- **Gate results:** `./scripts/verify-ticket SELLO-037 --retain` passed G1 full (no device
  suites) on `a63958a`, after the round 1 and round 2 fixes: ticket check, quality tests,
  performance tests, architecture, links and the Gradle host gate all ok; 1294 host tests in 114
  suites, zero failures, errors or skips; lint 0 errors and 38 warnings. The Gradle results were
  not fresh (`fresh_results` false): no code changed, so Gradle reused the up-to-date test results
  of the same `main`. Earlier runs on `ad2c88f` and `d899ec6` passed too; each round's fixes
  changed the ticket's change set, so it was run again. `tickets.py check` and `links.py` were
  also run on the final head.
- **Quality run:** run 20261029T001214Z-4e7891d4; SELLO-037 G1 passed; HEAD a63958a, inputs sha256 50d39405adc6, changes sha256 8b34005180a0; report docs/tickets/E09-signed-carryover/SELLO-037/quality-report.json sha256 88218aa6492c3a8443e3eb69979d9ccd7b68e1577caf52cba4e0f511afc250c8
- **Device / Artifact:** not applicable: documentation only, no app change.
- **Review:** the owner's answers of 2026-10-28 are the product approval of the rules marked
  **Owner**. Adversarial reviewer agents (Claude Opus 5.5) on pull request 194: round 1,
  CHANGES REQUIRED, findings R1–R9 (one blocking, two important, six minor), fixed with the
  orchestrator's rulings (R1 option a, R3 as a post-commit notice, CP14/CP15 labels); round 2, a
  fresh reviewer, APPROVE with R1–R9 resolved and two Minor findings, R10 and R11, fixed in the
  commit after `63f7efe`. The agent reviews are never recorded as a human or owner approval; the
  merge, under the owner's delegated orchestrated run, is the approval.
