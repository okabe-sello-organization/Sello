# SELLO-114 — Design the schedule screens, the upcoming-payments list and the catch-up notice

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-096, SELLO-004
- **Gate:** G1

### Outcome
The recurrence screens v2 does not show are designed and locked with the owner, like the other v2 pages, before any of them is built.

### Deliverables
v2 pages in `docs/design/v2/` for the list of schedules and its editor (create, change, pause, resume, end, delete), the list of upcoming payments that Recibo's footnote opens, and the notice and list after catching up, each with its states; their section in the [v2 README](../../../design/v2/README.md) marked locked; new parts added to `components.html` as Exists, Extend or New.

### Acceptance criteria
- Every screen shows its default, empty, loading, failed-read, large-text and dark states with synthetic figures that follow SELLO-096's approved examples.
- The pages settle where schedules are reached from and how one is created: on its own screen, or with a **Se repite** control in the locked v2 entry.
- Editing a recorded occurrence is settled: on its own only, or also "from now on" for its schedule, as v1.1's "Solo este" / "Este y los siguientes".
- Each page reuses the existing parts the [gap list](../../../design/v2/gaps/recurrence.md) names and says what is new and why.
- The owner locks each page; rejected options are recorded, as for the other v2 pages.

### Tests
Owner review of the pages; the link check; G1.

### Working checkpoint
SELLO-100, SELLO-101 and SELLO-103 can be built from locked pages.

### Context and starting points
The [gap list](../../../design/v2/gaps/recurrence.md) (owner decision, 2026-10-24, one design pass for the three); v1.1 *Lo que se repite* in the [v1.1 snapshot](../../../design/sello-spec.html); [ADR 0017](../../../decisions/0017-goals-recurrence-investments.md); the v2 pages' `kit.js` and `sello.css`. The earlier SELLO-100 text placed the list in Configuración and an entry's detail; v1.1 placed it in Resumen and Configuración.

### Implementation plan
1. Draft the pages from v1.1 and the existing v2 parts.
2. Review them with the owner, one screen at a time.
3. Lock them and update the README and `components.html`.

### Concrete cases and pitfalls
- v1.1 dims the schedules still to come and shows a monthly total; the total must say it is a plan, not recorded money.
- No notification, badge or background behaviour (ADR 0017).
- Design only: no app code, no tickets beyond this one.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-114 --retain`.

### Execution progress

- **Owner decisions, 2026-10-25:** the owner answered the open choices and delegated the
  lock to the executor, reviewing in the pull request: schedules are reached from
  Configuración and Resumen's income slip; a schedule is made with **Se repite** in the
  locked entry; saving a change to a recorded entry asks **Solo este / Este y los
  siguientes**; the notice is a bar above Anotar that opens a list and stays until
  **Listo**; RE29's Revisar sentence is "Pasaste tu límite por 80.000 y tus pagos previstos
  suman 200.000."
- **Pages:** `docs/design/v2/fijos.html` (Se repite and its rule sheet, the list, changing,
  pausing and deleting, editing a recorded entry, upcoming payments, the notice and its
  list, where schedules are reached from), each screen with default, empty, loading,
  failed-read, large-text and dark frames; Recibo gains RE29 and the notice as states.
  The README records the section as locked by delegation, with the rejected options;
  `components.html` gains five parts (Exists, Extend or New). Mock-only icons: autorenew,
  pause, play_arrow and event_busy, bundled when SELLO-100 needs them.
