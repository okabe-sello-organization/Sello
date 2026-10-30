# SELLO-023 — Ship persisted appearance, ordering and essential settings

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-006, SELLO-015, SELLO-020, SELLO-004
- **Gate:** G2

### Outcome
Users can personalize the MVP through a settings surface without financial responsibilities leaking into it.

### Deliverables
Typed DataStore preference adapter/neutral contracts, Sistema/Claro/Oscuro and
Cobalto/Violeta controls, category order/direction with deterministic tie-breaks,
and settings behind Recibo's gear. Data actions become visible only as their tickets ship.

### Acceptance criteria
- Theme changes apply consistently and survive restart; preference errors are visible
  without invalidating committed money. Financial zone/limits remain in Room.
- Every supported category order uses actual data; unlimited categories remain last.
  No investment currency, recurrence, notification or debug controls appear in release.
- Approved D06 defaults/metrics/directions/ties match O01–O14 in the versioned
  corpus. Más usadas counts selected-month expenses, not lifetime count or amount;
  latest activity means last added expense, not configuration or expense modification.
  Último gasto agregado uses newest original real creation Instant among selected-month
  nondeleted expenses; edits/restore preserve it, deletion falls back, and no-expense
  rows stay last within each partition even when reversed. Old configuration ordering
  is superseded. Unlimited-last and
  alphabetical/ID ties are not reversed with the selected metric direction.
- Settings uses narrow collaborators, not a universal manager/ViewModel; drafts
  and selected month are not cleared by appearance changes.

### Tests
DataStore codec/default/failure/restart tests, sorting ties and archive interactions,
device theme/order/settings back-stack tests; G2. Extend catalog preference UI states.
Execute all O01–O14 orders, cross-month selection and reversed-direction/tie cases;
financial-time travel without expense additions and with fixed selection cannot
count as a new addition. Include O09–O14 backdated/edit/delete/restore/empty/time-tie
fixtures, preserving original creation time and reversing only metric comparison.

### Working checkpoint
Daily-use workflows remain functional in all four themes; restart preserves selected
preferences without changing financial data or formulas.

### Context and starting points
Preferences should not make settings a financial transaction coordinator. Use
neutral contracts from SELLO-005, theme from SELLO-006, real categories and approved
D06/D09. Read [settings](../../design/sello-spec.html#s-config), architecture §§2/7
and the guide's DataStore source. Rate/currency/notification settings are not MVP.

Carried over from SELLO-019 (2026-10-12, project owner): the dismissible first-time
announcement that a past expense can be corrected (D04/H06) needs a remembered
"seen" preference, so it is delivered here. SELLO-019 ships the guidance that is
shown in context every time.

Carried over from SELLO-020 (2026-10-12, project owner) for the same reason: the
dismissible announcement that a previous month's limit can be corrected. SELLO-020
ships the help that stays in view; this ticket adds the announcement and remembers
that it was dismissed, for both.

### Implementation plan
1. Define typed theme/ink/order/direction preferences and stable codecs in data;
   implement DataStore observation/update with explicit failure/default behavior.
2. Wire the adapter only in composition; screen/ViewModel consume neutral preference
   ports and never serialized UI enum names or direct DataStore keys.
3. Add gear-accessible settings UI and deterministic selected-month category ordering
   from approved D06, keeping unlimited last and stable tie-breaking.
4. Apply theme immediately through the root while preserving forms/month/back state;
   expose save/read failure and retry without invalidating financial receipts.
5. Prepare settings data-action slots through actual route availability. Backup/reset
   buttons appear only when those workflows land; debug tools appear only in debug sources.

### Concrete cases and pitfalls
- Sistema/Cobalto is the default; choose Oscuro/Violeta, restart and verify both.
- Equal metric values require the approved tie-break, not nondeterministic SQL order.
- Invalid nonfinancial preference handling is distinct from corrupt financial data;
  do not default a broken currency/budget to a UI setting or write through Room.

### Verification recipe
Create/run `*PreferencesCodecTest`, `*CategoryOrderingTest` and SettingsJourneyTest;
G2. Inject DataStore failure, verify changed theme/restart/order and untouched actual
financial rows/drafts; test every approved ordering metric with explicit fixtures.
