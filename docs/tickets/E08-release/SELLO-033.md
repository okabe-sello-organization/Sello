# SELLO-033 — Execute accessibility, adaptive and process-restoration journeys

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-018, SELLO-019, SELLO-020, SELLO-021, SELLO-022, SELLO-023, SELLO-009, SELLO-063, SELLO-064, SELLO-065, SELLO-066, SELLO-067, SELLO-004
- **Gate:** G2

### Outcome
Verify and fix the assembled daily-use UI across window, text-size and theme configurations.

### Deliverables
Automated/manual journey matrix and evidence for four schemes, scales 1.0/1.3/2.0,
compact/short-landscape/medium/expanded layouts, insets, reduced motion and the
accessibility semantics tests can read (spoken amounts, labels, targets). Include
actual draft/session restoration, not rotation alone.

### Acceptance criteria
- All daily-use flows work without clipping/covered fields, color-only status,
  gesture-only actions, undersized targets or inaccessible chart values.
- Tab/month/detail/panels follow approved back policy; drafts/operation IDs survive
  eligible process recreation. Force-stop/cold start is reported separately from
  OS process-death restoration and must not create a duplicate save.
- Issues discovered are fixed with regression tests; no blanket a11y waiver.
- Manual TalkBack, Switch Access and hardware keyboard walks are not part of this
  ticket; they are post-MVP in SELLO-074.

### Tests
Run affected connected Compose tests on agreed API/window configurations; capture
screenshot/video/checklist evidence of the device walks. Verify exact money
semantics in visual and accessible outputs; G2.

### Working checkpoint
Integrated daily-use MVP is usable on devices; no recovery or catalog workflow is
broken by layout/accessibility fixes.

### Context and starting points
Component-level previews cannot prove complete forms/navigation usable on actual
devices. Inputs are completed daily-use routes, approved D10 matrix and the catalog.
Read [Sello v2](../../design/v2/README.md) (large text, reduced motion, exact spoken amounts), the v1.1 [a11y](../../design/sello-spec.html#a11y) section, adaptive/navigation guidance and
[process-restoration procedure](../../development/execution-guide.md#device-and-process-restoration).

From SELLO-E12 (2026-10-16): the journeys include the v2 Recibo delivered by
SELLO-061–069, so this ticket starts after that epic.

**Owner direction (2026-10-23):** manual assistive-technology walks (TalkBack,
Switch Access, hardware keyboard) are outside the MVP and move to post-MVP
[SELLO-074](../E14-assistive-technology/SELLO-074.md).
The automated semantics checks stay here: exact spoken amounts, labels, focus order,
48dp targets and status that is not color alone.

### Implementation plan
1. Create `docs/testing/ui-acceptance-matrix.md` listing build/API/window/font/theme/
   input mode, each daily-use journey and expected accessible exact values.
2. Automate Compose semantic/focus/restoration checks and execute reduced-motion
   walks on agreed configurations.
3. Run compact portrait, <480dp short landscape, medium and expanded panes with
   keyboard/gesture insets and scales 1.0/1.3/2.0. Test both completed tab stacks.
4. Exercise drafts/operation IDs through rotation, eligible process death and cold
   start separately. Verify saved receipt cannot trigger another write on recreation.
5. Fix found clipping/focus/copy/back issues in their owning layer, add regression
   tests and rerun prior completed journeys. Keep a matrix of passes and outstanding blockers.

### Concrete cases and pitfalls
- Semantics carry the currency/full amount and stamp meaning; charts provide exact
  selected/text values. Color-only over-budget state is not accessible.
- A focused note field remains reachable with keyboard open in landscape; 2.0 scale
  cannot truncate hero amounts or put save/delete actions below an unreachable panel.
- Force-stop success proves durable reopen, not saved-task restoration; report it separately.

### Verification recipe
Run affected app/design-system/catalog connected tests and G2 across the approved
matrix. Retain annotated screenshot/video/focus notes with exact configuration and
financial scenario. No single screenshot or emulator configuration closes the whole matrix.

### Execution progress

- **Part 1 of 2, sweep and fixes** (`sello-033-ui-matrix`). The owner chose, on
  2026-10-23: the real app shell for the sweep, WCAG AA as the contrast floor, fixes in
  the same part when small. Delivered: `UiAcceptanceSweepTest` opens 13 daily-use
  screens through the app's own buttons in five windows (compact 2.0, short landscape
  1.3 and 2.0, medium, expanded; all four themes), checking 48dp touch targets, text
  not cut short, amounts spoken in pesos and main actions inside the window;
  `SelloColorsTest` gained the stamp, soft-fill and past-month pairs. Fixed, each red
  first: screen and month titles take a second line, and move under their actions when
  even that does not fit; the rail widens to its longest label; chips wrap; limit
  suggestions and Resumen's limit line are read with "pesos". Matrix:
  [UI acceptance matrix](../../testing/ui-acceptance-matrix.md).
- **Part 1 gate:** `./scripts/verify-ticket SELLO-033 --retain` passed on `4a156c5`
  (`main` at `87f3a8f`), run 20261023T065031Z-dbb34ab5 on the API-30 AVD `ci30`
  (`emulator-5582`): 674 host and 702 device tests, zero failures; lint 0 errors and
  the 33 existing warnings. Not the ticket's Quality run: the ticket stays In Progress
  until part 2.
- **Part 2 of 2, device walks** (`sello-033-walks`). On the isolated API-30 phone
  emulator `sello022_api30`: process death for a new expense, a saved expense, an
  expense edit, a new income and a new category, each draft back in a new process
  and each confirmed action one receipt, unchanged by a further kill; rotation in the
  same process; force-stop and cold start reported apart; real medium and expanded
  windows; the note composer sideways with the keyboard at 2.0; reduced motion. The
  sideways walk found **Listo** under the navigation bar on the right; the composer now
  keeps its content clear of the side system bars (red first in
  `ComponentContractsTest`). Results and screenshots are in the matrix.
