# SELLO-118 — Import FinanceTracker investments once Inversiones exists

- What changed: a FinanceTracker import now brings investments, their movements and the dollar rate, with value, aportes and dividends matching the old app; interest, an added first value, the annual rate and a rounded rate are shown as changed, and anything breaking an investment rule is left out by reason (ADR 0019).
- Where: `data/.../backup/legacy/LegacyTranslator.kt` (investments, `history`, `usdRates`), new `LegacyReason` values in `domain/.../port/Restores.kt`, their wording in `presentation/restore/LegacyReport.kt`, [legacy export format](../../../data/legacy-export-format.md).
- Still open: merchant rules are reported as not imported (Sello has none); EUR and GBP stay out.
