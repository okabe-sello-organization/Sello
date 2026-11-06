# SELLO-E32 — The code follows its design system

- **Status:** In Progress
- **Increment:** Post-MVP
- **Goal:** Every screen uses the shared component and token that means what it shows, so the app looks and behaves alike wherever the same thing happens.
- **Exit:** The findings page of `docs/design/system/` lists no misuse, no hand-built copy and no hard-coded value; every part two features share lives in `:design-system`; the design pages describe the result; the final commit carries the tag `design-system-v1`.

## Decisions

**Owner direction (2026-11-05):**

- E31's findings page is the to-do list: fix every misuse (M01–M13), hand-built copy
  (C01–C25) and hard-coded value (H01–H76) it lists. Each pull request changes the code,
  re-records the screenshots, updates the design pages and removes the findings it fixes.
- Follow the architecture rule (ARCHITECTURE §2 and §6): a part two or more features
  share moves to `:design-system`; a part one feature uses stays in that feature.
  `:design-system` takes plain inputs; the mapping from domain models stays in `:app`.
- One `StatusSlip` with a required kind (Empty, LoadFailed, Older, ActionFailed, Unknown,
  Gone) replaces `EmptySlip`, `ErrorSlip` and `StaleSlip`; the kind decides icon, ink,
  layout and button. The owner approves the six kinds from their screenshots before it
  merges. It settles the unsure misuses M01-10 to M01-17, M12-1 to M12-4 and M13-1.
- Unsure misuses, owner answers: fine as they are — M07-2, M11-1, M11-2. Fix — M03-4
  (ActionFailed inside the dialog), M07-3 (Sin respaldo in warn), M08-1 (outlined first,
  filled Respaldar second), M08-2 (stacked buttons: the action on top, Cancelar under it),
  M09-1 to M09-4 (a new withdrawal icon, approved in its pull request), M10-1 (green),
  M10-2 (ink), M10-3 (green), M11-3 (Conservar mis cambios filled, Volver a cargar
  outlined), M12-5 (loss), M12-6 (plain ink), M12-7 (the shared saved notice, plain ink).
- M03-3: the confirmation to forget a carried balance is destructive (loss ink), and
  turning carry on or off no longer saves at once: both wait for Guardar like every other
  field of the category form.
- Hard-coded colours are fixed in this epic, as groundwork for dynamic colours.
- The bugs found by the E31 audit (#374–#387, #394) are fixed outside this epic.
- When the epic is done, its final commit is tagged so the current design can be found.
