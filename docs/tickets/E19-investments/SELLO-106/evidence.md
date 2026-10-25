# SELLO-106 — Approve investment rules and worked examples

- **Revision:** the gate tested commit `084a603` on `sello-106-investment-rules`, stacked on
  SELLO-105 (`911faa7`, #155) from `main` `c8d0b4c`; the only uncommitted file was the
  retained report. The status, this file and the handoff are added after.
- **Requirement mapping:** every ADR 0017 investment decision has examples with exact
  amounts → [investment examples](../../../testing/investment-examples.md): the simple
  return since the first contribution (IN01–IN07, IN25), no 0 % without contributions
  (rule 6, IN22), COP and USD with one dated rate and USD kept in USD (IN27–IN31), values
  showing their date and never stale (rule 5, IN08–IN13), dividends in the gain (IN05,
  IN14), corrections with undo (IN19–IN23); rate validation and its date defined → rule
  11, IN32; the owner approves, open points listed → the owner's 2026-10-25 answers
  marked **Owner**, approval by merging this pull request, "Open points: none"; the
  added screens with default, empty, loading, failed-read, large-text and dark frames,
  recorded as locked by delegation → `inversiones.html` sections 6 and 7, the v2 README's
  *Dólares y la tasa*, `artifacts/`.
- **Red / Green:** not applicable: rules and design, no behaviour. Every figure was worked
  by hand; the USD conversions (IN28–IN30) were checked with exact decimal arithmetic
  outside the app, not with app code. The page renders without console errors in a
  headless Chromium (36 frames), and the ticket and link checks pass.
- **Gate results:** `./scripts/verify-ticket SELLO-106 --retain` passed G1 full on
  `084a603`: 1047 host tests in 97 suites, zero failures; lint 0 errors and 34 warnings,
  as on `main`.
- **Quality run:** run 20261025T204530Z-64a008ae; SELLO-106 G1 passed; HEAD 084a603, inputs sha256 b483470d2fee, changes sha256 aab54193b34a; report docs/tickets/E19-investments/SELLO-106/quality-report.json sha256 b41eded45d2cb74c09fd1d99280914a45b7808f4ac7e5ac38258bd084ce2399b
- **Device / Artifact:** not applicable: no app change. Page screenshots in `artifacts/`.
- **Review:** executor self-review; the owner answered the rules on 2026-10-25 and
  delegated the design lock; owner approval by merging.
