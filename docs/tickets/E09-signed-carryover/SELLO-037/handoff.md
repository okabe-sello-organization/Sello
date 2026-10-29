# SELLO-037 — Approve signed carryover policy and independent examples

The policy is `docs/decisions/0020-signed-carryover-policy.md` (CP1–CP28, owner answers marked **Owner**, the rest **Proposed**); it stands in for the ticket's `0003-...` name, which is taken. Examples SC01–SC47 are in `docs/testing/signed-carryover-examples.md`.
ADR 0002 points to both. Per category, opt-in, off by default; next carry is (base + carry) − spending; dropping a balance needs a confirmed exact amount; corrections preview every later month.
Next: SELLO-038 implements the domain calculation and tests it with these values; its mapping is in ADR 0020.
