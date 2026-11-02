# SELLO-E27 — Varios across goals and investments

- **Status:** In Progress
- **Increment:** Post-MVP
- **Goal:** The person records movements of several goals, or of several investments, on one bulk receipt, as expense receipts already do across categories.
- **Exit:** Aportar varios and Varios movimientos let each line pick its goal or investment, save every line of every goal or investment in one operation or none, and open from the Metas and Inversiones lists with none chosen.

## Decisions

**Owner direction (2026-11-02):** given in chat on the orchestrator's brief.

- Each line of Aportar varios picks its goal, and each line of Varios movimientos its
  investment, from chips as Anotar varios picks categories. This replaces "one goal" and
  "one investment" in the [Anotar varios design](../../design/v2/README.md#anotar-varios)
  locked on 2026-10-30 ([E23](../E23-bulk-entry/README.md)).
- Saving stays all or nothing with one operation ID per receipt, now across several goals
  or investments. A goal or investment that changed meanwhile asks to review and save
  again; nothing is overwritten.
- A receipt with COP and USD investments keeps each line in its own currency; its total is
  in COP at today's rate, as elsewhere ([E19](../E19-investments/README.md)).
- The Metas and Inversiones lists offer the receipt with no goal or investment chosen,
  as expenses open without a category.
- Orchestrated run; subagents and separate review agents are approved for this epic.
