# SELLO-136 — One kind per bulk receipt, with no switch

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
The expense or income bulk receipt keeps the kind it was opened with; its top bar shows
what it holds instead of a Gasto/Ingreso switch.

### Acceptance criteria
- Opened from Anotar Gasto, a category or its foot link, the receipt is titled
  **Varios gastos**; from Ingreso or the income list, **Varios ingresos**. No switch.
- The kind cannot change during the receipt's life, and a restored draft keeps it.
- The switch's confirmation and its texts are gone; nothing else in the receipt changes.

### Notes
Starts from `feature/bulk/BulkEntryScreen.kt` and `BulkEntryViewModel.kt`. The design
README records the change under *Anotar varios*.
