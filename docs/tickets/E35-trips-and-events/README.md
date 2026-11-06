# SELLO-E35 — Trips and events

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** The person marks expenses from any category with an event, such as a trip, a wedding or Christmas, and sees what it cost in total and by category.
- **Exit:** Events are stored, backed up and exported; Anotar, the expense editor and Anotar varios can set one; Eventos and each event's page work as in the approved mock.

## Decisions

**Owner direction (2026-11-06):**

- An event is only a tag: a name and an icon. No budget and no dates. An expense
  belongs to at most one event.
- The storage change is approved: an events table, an optional event on each expense,
  a Room migration, backup format version 8 and an event column in the CSV/JSON export.
- An event never changes a category, a limit, a carry or any month's figures.

**Planner's calls (2026-11-06), open to the owner:** event names follow category-name
rules (1–24 characters, unique once folded) and use the category icon set. Deleting an
event keeps its expenses and only removes the tag, after one confirmation. Anotar varios
sets one event for the whole receipt. Expenses recorded by a fixed payment get no event
on their own. Eventos opens from a fourth Resumen stub. The
[mock](attachments/events-mock.html) shows them and needs the owner's approval before
building. SELLO-198 changes storage: adversarial review, and the owner merges it.
