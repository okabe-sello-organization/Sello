# SELLO-124 — Make the ranking part of the income slip

- What changed: "¿Dónde se va?" is now part of "¿A dónde fue lo que entró?": under Entró and its bar, every category with the ranking's rows, then Gastos and Ingresos menos gastos; with no income, the rows and their Total. No category appears twice on Resumen.
- Where: `feature/resumen/ResumenScreen.kt` (`IncomeSlip`, `Ranking`), `ResumenState`/`ResumenRenderModel` (`ResumenIncome` without top three), `ResumenPolicy`/`ResumenSnapshot` (top-three sum removed), the catalog's income example, the v2 Resumen paragraphs and HANDOFF.
- Still open: nothing for E22. `EntryCatalogInteractionTest`'s Nueva test still fails on a pixel_5-sized AVD, outside this change.
