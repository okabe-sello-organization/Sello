# SELLO-182 — Parts two features share live in the design system

- **Type:** Task
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
The visual parts that two or more features share (movement form parts, repeat parts,
carry parts, restore parts) are `:design-system` components with plain inputs; the
features map their models to them. Nothing looks different.

### Acceptance criteria
- Moved: MovementPane, MovementNoteLine, MovementNoteComposer, MovementDiscardSlip,
  MovementDatePicker, MovementCloseButton, RepeatRow, RepeatChoiceSheet, FollowingChoice,
  the visual parts of CarryCorrectionContent and CarryBreakdownSection, RestoreHeading,
  RestoreBody, RestoredSlip and Counts. StaleSlip is left for SELLO-184.
- `:design-system` gains no domain, data or feature dependency.
- Every screenshot baseline is unchanged; the components page has a card for each.
