# SELLO-E17 — Metas: personal goals

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** Let a person create goals, record contributions and withdrawals, earn milestone stamps and complete goals, as designed in Sello v2.
- **Exit:** Metas is a tab with goals, detail, Aportar/Retirar, creation and celebrations; Recibo shows one goal; backups carry goals; the sandbox and catalog cover them.

## Decisions

**Owner direction (2026-10-23):** Metas comes first among the new features and ships with
recurrence and Inversiones in one release ([E19](../E19-investments/README.md)). Decisions:
[ADR 0017](../../decisions/0017-goals-recurrence-investments.md), [ADR 0013](../../decisions/0013-sello-v2-design.md) and the
[Metas brief](../../design/screen-briefs/goals.md). Goals never allocate income or
change category budgets.

**Owner decision (2026-10-23):** after the SELLO-085 audit ([gap list](../../design/v2/gaps/metas.md)),
SELLO-089–094 reuse the goal components the design system and catalog already have and
add only the listed extensions; SELLO-094's catalog part shrinks to those extensions and
the missing device tests. No ticket was removed.

**Owner decision (2026-10-25):** what E17 left open after closing (the Metas dock and
goals band in the catalog, two stamps landing one after the other, a Deshacer too late
replayed in the sandbox) is finished in a new ticket, SELLO-115, which reopened the epic
until it merged. Recibo's first use shows the goal: rule 12 hides it only with no active
goal or on a past month.
