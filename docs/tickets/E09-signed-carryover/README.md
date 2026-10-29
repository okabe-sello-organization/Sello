# SELLO-E09 — Signed budget carryover after MVP

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** Preserve surplus and overspending across enabled budget periods without confusing plans with account balances.
- **Exit:** Approved signed-carry policy, exact explainable availability, recoverable storage/portable files, accessible opt-in workflows and executed multi-month regression/release evidence; non-carry journeys remain usable.

This is an approved roadmap addition, not MVP scope. Read
[ADR 0002](../../decisions/0002-signed-carryover-direction.md),
[execution guide](../../development/execution-guide.md) and [quality flow](../../development/quality-gates.md).
SELLO-037 cannot start until MVP release acceptance (SELLO-036) is Done. Proposed
file/test names below are deliverables, not existing APIs. No code/schema is built
while executing SELLO-001. Optional scope and detailed finance semantics are owned
by SELLO-037, not guessed by later executors.

## Decisions

**Owner direction (2026-10-28):** the owner's answers for the epic, recorded when the
orchestrated run started. SELLO-037 holds the policy they approve
([ADR 0020](../../decisions/0020-signed-carryover-policy.md)).

- **Scope:** per category, opt-in, off by default. Carry starts at 0 in the month it
  is turned on; past months never count. The overall budget sums the enabled categories.
- **Formula:** next carry = (base + carry) − spending, signed, every calendar month. A
  month with no expenses counts as zero spent. No cap on surplus or deficit.
- **Turning off, archiving or making unlimited with a non-zero carry:** show the exact
  balance and confirm dropping it. Re-enabling starts at 0.
- **Verdicts:** status, remaining and daily allowance use base + carry; a deficit shows
  even when the allowance is 0. "Por asignar" uses base limits only; carry is never income.
- **Corrections:** preview each later month whose availability changes and by how much,
  confirm, then replay all at once. Base limits never change.
- **Design:** SELLO-041's design is delegated: concept screenshots in its pull request,
  reviewed by the adversarial reviewer. Merging approves.
- **Release:** stop before SELLO-043; the owner starts the release.
