# SELLO-106 — Approve investment rules and worked examples

- **Revision:** the gate tested commit `d6f2313` on `sello-106-investment-rules`, which merges
  `main` at `0782d6d` (SELLO-105 done, #155); the only uncommitted file was the retained
  report. The status, this file and the handoff are added after.
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
  `d6f2313`: 1047 host tests in 97 suites, zero failures; lint 0 errors and 34 warnings,
  as on `main`. The first run, on `084a603` before the merge, passed too; merging `main`
  moved SELLO-105's edit of this ticket into the base, so it was run again.
- **Quality run:** run 20261025T211629Z-4c39f961; SELLO-106 G1 passed; HEAD d6f2313, inputs sha256 b483470d2fee, changes sha256 4f31a1a34037; report docs/tickets/E19-investments/SELLO-106/quality-report.json sha256 69218ce9d9cb9b79e3b4a3a5b4a32a42455e9377b4415270fa06ffa3d9a272c8
- **Device / Artifact:** not applicable: no app change. Page screenshots in `artifacts/`.
- **Review:** executor self-review; the owner answered the rules on 2026-10-25 and
  delegated the design lock; owner approval by merging.
