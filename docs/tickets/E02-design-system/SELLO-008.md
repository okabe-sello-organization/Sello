# SELLO-008 — Build accessible amount-first inputs and entry states

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-006, SELLO-010, SELLO-004
- **Gate:** G2

### Outcome
Provide the shared keypad/chips/fields/buttons needed for expense and income entry.

### Deliverables
Amount keypad, date/category/source chips, bounded text fields, switches and busy
buttons, plus reusable validation-display contracts. Catalog examples demonstrate
editing, invalid paste, missing selections, disabled/busy and large-font states.

### Acceptance criteria
- Controls display explicit validation; they never sanitize `1e3`, a negative
  sign or ambiguous separators into another amount. Commands still validate later.
- Hardware keyboard/Switch Access focus follows reading order. System keyboard
  entry keeps the focused field visible; hit targets meet the minimum.
- Empty amount is a draft, not a valid zero-money command; double taps are preventable
  visually but eventual database uniqueness remains required.

### Tests
Keypad/input boundary tests and Compose tests for callbacks, focus, paste rejection,
busy/disabled semantics and scale 2.0; run G2 on relevant catalog/device states.

### Working checkpoint
Inputs are locally interactive in catalog without a repository or fake saving service.

### Context and starting points
Expense and income must share controls without sharing an unsafe universal form.
Inputs are defined at [keypad](../../design/sello-spec.html#c-amountkeypad) and
the following input components. Use SELLO-010 validators and approved D05 field
limits, not invented max digits/note lengths.

### Implementation plan
1. Build keypad/backspace/chips/date/note/field/switch/button controls as stateless
   values/actions; actual drafts and business validation stay with consuming features.
2. Route keyboard/paste input through the original-input validator; retain invalid
   text and typed error rather than filtering characters into a different amount.
3. Implement required/busy/disabled/focused states and category-chip wrapping/scrolling;
   request focus/keyboard behavior only at the appropriate Root/control boundary.
4. Add catalog-local interactive drafts and reset controls; enforce hardware keyboard
   and Switch Access focus order with minimum hit boxes.
5. Add previews for absent category, max digits, note error and keyboard-visible layout;
   do not add recurrence or investment controls merely because reference shows them.

### Concrete cases and pitfalls
- Pasting `1e3` must stay invalid, not become 13; deleting all digits returns an
  incomplete draft, not a valid zero expense. D05 resolves keypad versus import range.
- Busy button cannot emit a second confirm action; this UI guard does not replace
  future database operation uniqueness. Chips cannot trap focus below the keyboard.
- At font scale 2.0 a tall hit box is required even for visually short chips.

### Verification recipe
Create/run `*AmountInputTest` and `*EntryControlsTest` for validation/callback/focus
boundaries; run design-system/catalog G2. Capture keyboard/paste and large-font
examples using the guide's device procedure, with no financial repository involved.

### Execution progress
2026-10-09: started on branch `sello-008-entry-inputs` after SELLO-007 merged. No new
product decision or dependency was needed.

Delivered in `:domain`: `AmountDraft` (`Empty`, `Digits`, `Rejected`) with keypad and
paste rules built on `CopAmountInput` and `TransactionAmount`. Delivered in
`:design-system`: `AmountKeypad`, `AmountField`, `SelloChip`, `ChipFlow`, `FieldPill`,
`FormField`, `SwitchRow`, `SegmentedSwitch`, and the shared `FieldMessage` /
`FieldMessageText` validation display; six more icons. `SelloButton` with its loading
and disabled states came with SELLO-007. New catalog example IDs:
`inputs.amount-keypad`, `inputs.chip`, `inputs.field-pill`, `inputs.form-field`,
`inputs.switch`, `inputs.expense-entry`. The catalog now resizes for the system keyboard.

Choices made during the work, for owner review:
- Keypad keys are at least 48dp tall. The reference draws 40dp and names that as an
  exception to its own 48dp rule; this ticket requires the minimum.
- The draft rules live in `:domain` so expense and income entry share them. The catalog
  cannot depend on `:domain`, so its examples use a small stand-in draft marked as a
  fixture, as with money formatting.
- Typing after a refused paste starts a new amount; backspace removes the refused text
  whole. Nothing is ever built from part of it.
- `000` at the limit is refused whole, not partly applied. A leading zero is ignored
  without the limit signal.
- Paste is a button on the amount display that hands over the clipboard text untouched;
  there is no hidden text field. Enter on a hardware keyboard submits only when the
  caller provides a submit action.
- `FormField` neither trims nor cuts at a limit; it shows a counter and the caller's
  error, so over-long text is rejected, not truncated.
- Chips and pills grow with the font size and end long labels with an ellipsis.

Left for later: the date picker and its no-future rule belong to the entry screen
(E04). `SettingRow` is settings work, and recurrence controls are post-MVP. The
"Máximo 12 dígitos" and other messages are the consuming feature's resources; the
catalog's are fixtures. Switch Access was not run on a device; its order follows the
same focus order the hardware-keyboard test checks.

2026-10-09: owner approved pull request 7, including the choices above; moved to Done
for a squash merge.
