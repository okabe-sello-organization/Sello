# SELLO-037 — Approve signed carryover policy and independent examples

- **Revision:** the gate tested commit `ad2c88f` on `sello-037-carryover-policy`, one commit on
  `main` at `cbcf588` (#193); `main` had not moved, so no merge was needed. The only
  uncommitted file was the retained report. The status, this file and the handoff are added after.
- **Requirement mapping:** scope, default and opt-in, start month, starting carry, missing and
  skipped history and the continuous formula → [ADR 0020](../../../decisions/0020-signed-carryover-policy.md)
  CP1–CP8, examples SC01–SC07; enable/disable/re-enable, archive/unarchive, finite/unlimited/zero,
  base-limit edits, no caps, negative capacity and out-of-range → CP3, CP9–CP13, CP23–CP25,
  SC08, SC17–SC24, SC40; no deficit forgiven or clipped, settlement only by confirmation → CP9,
  CP10, CP12; correction cascades, preview scope, report provenance, forecast and verdict
  denominator, income allocation without carry as income → CP14–CP22, CP27, SC09–SC16, SC25–SC38;
  independently approved cases (three consecutive months, surplus, deficit, deficit larger than
  the next base, skipped months, corrections) → SC01–SC05, SC25–SC30 in
  [signed carryover examples](../../../testing/signed-carryover-examples.md); presentation wording →
  the table in ADR 0020; acceptance-to-ticket mapping → ADR 0020 "Mapping to the tickets"
  (SELLO-038–043). ADR 0020 stands in for the ticket's `0003-signed-carryover-policy.md`, because
  number 0003 is taken by the transaction amount range. ADR 0002's open decisions now point to it.
  The owner's answers of 2026-10-28 are marked **Owner**; every other rule is **Proposed** with its
  reason and rejected alternative and is approved by merging. No financial choice is left for
  SELLO-038–043.
- **Red / Green:** not applicable: rules and examples, no behaviour and no code. Every expected
  value was worked out by hand and then checked with a throwaway Python script outside the
  repository (not committed, no app code, exact integers): every numeric figure of the 45 examples matched,
  including the COP 50 allowance rounding, the shifted capacities of each correction and the
  `Long` overflow. One hand slip was caught by it (SC17's alternative figure, 130.000 not
  150.000) and fixed before the gate.
- **Gate results:** `./scripts/verify-ticket SELLO-037 --retain` passed G1 full (no device
  suites) on `ad2c88f`: ticket check, quality tests, performance tests, architecture, links and
  the Gradle host gate all ok; 1294 host tests in 114 suites, zero failures, errors or skips; lint
  0 errors and 38 warnings. The Gradle results were not fresh (`fresh_results` false): no code
  changed, so Gradle reused the up-to-date test results of the same `main`.
- **Quality run:** run 20261028T235922Z-f3ecaee2; SELLO-037 G1 passed; HEAD ad2c88f, inputs sha256 55c89ba99838, changes sha256 3e0bc7458b3f; report docs/tickets/E09-signed-carryover/SELLO-037/quality-report.json sha256 ad976cd6d4db5a29c29f208ac355df073063d43d0f00d2470e7553065331f953
- **Device / Artifact:** not applicable: documentation only, no app change.
- **Review:** the owner's answers of 2026-10-28 are the product approval; adversarial reviewer
  agent (Claude Opus 5.5) review on the pull request. The agent is not a human approver; the
  merge, under the owner's delegated orchestrated run, is the approval.
