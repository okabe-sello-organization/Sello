# SELLO-E26 — Delete a category

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** The person deletes a category for good, moving its expenses to another category or deleting them with it.
- **Exit:** Borrar categoría works from Editar categoría for active and archived categories, with both choices, and says why when it did not happen.

## Decisions

**Owner direction (2026-11-02):** categories can be deleted whatever they hold
([ADR 0024](../../decisions/0024-deleting-a-category.md)), replacing D07's archive-only rule.

- With expenses, ask each time: move them to another category, or delete everything.
- No undo; strong confirmation instead: counts, and typing BORRAR before deleting
  expenses.
- Archivar and Borrar both live in Editar categoría's ⋮; an archived category shows
  Desarchivar in the form. Not on category detail.
- When it does not happen, say that nothing was deleted or moved, why, and the way forward.
- Design: [borrar-categoria.html](../../design/v2/borrar-categoria.html), approved in chat.
- Subagents and a separate review agent are approved for this epic.
