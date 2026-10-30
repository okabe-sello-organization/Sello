# Anotar: recording an expense

**Owner:** SELLO-017 · Decisions: [D03, D05](../decisions/0001-mvp-contract.md) ·
Design: [Sello v2 entry](../design/v2/entry.html) · Builds on [commands](commands.md),
the [monthly snapshot](monthly-snapshot.md) and the [app shell](app-shell.md).

Its rule: what is shown as saved is only ever what storage confirmed.

This file describes the form as built: the approved v2 entry (SELLO-070), with the short
confirmation after a save from SELLO-066.

## Getting there

- The **dock** ("Anotar un gasto") on every shell screen, and the
  [`sello://anotar` link](app-shell.md#routes-and-links). With no category that can
  take an expense they open the [category editor](category-editor.md) with the reason,
  then continue here with the new category chosen.
- **"Anotar aquí"** on a category's detail, and Resumen's empty-history action.
- The route is `ExpenseEntryRoute(categoryId, amount)`. Both are draft hints; the form
  checks them like any input and drops what is not valid.

## The draft

| Field | Rule |
| --- | --- |
| Amount | The compact Sello pad only, through `AmountDraft`: up to twelve digits; the figure steps down in size as digits are added. A thirteenth is refused, the figure shakes and **Máximo 12 dígitos** takes the label's place until the next key that changes the amount. Pasted text (the slip's paste button) is taken whole or kept exactly as it was with the reason. Never zero, never changed into another amount |
| Category | One of the categories that are not archived, as chips in at most two rows that scroll sideways, ending with **Nueva**. None is chosen at opening, so Anotar waits for one, except the one a caller asked for ("Anotar aquí", a link) if it can take an expense. **Nueva** opens the [category editor](category-editor.md); the editor hands the new category's identifier back through the entry's navigation result, and the form chooses it once the month offers it, with the draft kept |
| Date | The financial day the form was opened; it stays that day if the draft is restored later. Any earlier day can be picked; a later one cannot |
| Note | Optional, up to 60 characters as people count them (code points), one line; blank means no note. Its row ("Agregar nota", or the note with Editar) opens a composer docked above the keyboard, saying what it belongs to ("Gasto en **Comida** · **48.700**"), with the cursor at the end. Over 60 the text is kept, the counter and message turn red ("Máximo 60 caracteres. Tu texto sigue aquí.") and ✓ is off; Cancelar keeps the note as it was. An open composer and its text survive the process being killed |

The button is off until there is an amount, a category and a valid note. A missing
category or a note to correct is said in a line just above the button; a missing amount
is not, because the slip already shows "Sin monto". The button is read aloud as the
whole sentence: "Anotar gasto de 48.700 pesos en Alimentación"; while saving it says
"Guardando…".

There is no live preview of the month, as in v2 (owner decision, 2026-10-20): the
confirmation after saving and Recibo show the real figures. `ExpensePreviewPolicy`
stays in the domain, unused by this form.

## Saving once

`ExpenseEntryViewModel` follows the command protocol exactly as the category editor
does: a new operation identifier is saved before the command is sent; the form is
locked while it is out; a rejection clears the identifier and keeps the draft; an
unknown outcome, or a saved identifier found after the process was killed, is
settled by `find` with that same identifier and never by sending again.

| Rejection | What the person sees | Draft |
| --- | --- | --- |
| Category missing or archived | "Esa categoría ya no acepta gastos" | Kept; the category is deselected |
| Future date | "La fecha no puede ser posterior a hoy" | Kept |
| Data replaced meanwhile | "Tus datos cambiaron mientras tanto" | Kept; the next attempt uses the new generation |
| Storage failed | "No se guardó. Tu borrador sigue aquí." | Kept |

Every message says that nothing was recorded.

## After a confirmed save

After `Committed`, the saved expense's identifier is stored in saved state and the
expense is **read back from storage** (`ExpenseReads.byId`). The form then tells
`Corrections` what storage holds (`CorrectionNotice.ExpenseRecorded`) and
closes: the screen it was opened from shows **Gasto anotado: 48.700 en Comida** above
the dock for 4 seconds, in the confirmation look, read aloud politely with the amount
in pesos, with no undo and nothing to dismiss. Nothing is said before the save is
confirmed; an unknown outcome keeps the form with "Comprobar si se guardó". If the row
cannot be read back the notice says only "Gasto anotado": the expense is saved and
nothing is offered to send again; without its category's name it says "Gasto anotado:
48.700". After a restart the restored form finds the saved identifier, says it and
closes, sending nothing. A notice that comes later takes its place, and it takes the
place of an offer to undo, whose deletion stands.

The vibration is feedback for a save that already happened. It runs once per saved
expense, before the form closes, and nothing depends on it.

## Correcting a recorded expense

The same form, opened by `ExpenseEditRoute(expenseId)` from an expense in a
[category's list](recibo-and-detail.md#correcting-and-deleting-an-expense). It is
titled "Editar gasto", its button is "Guardar cambios" and the top bar has "Borrar".

- **It starts as what is stored.** The expense is read once (`ExpenseReads.byId`).
  What was read, with its version, is kept in saved state beside the draft, so a draft
  restored after the process was killed is still compared with, and sent against, the
  expense as it was when the form opened. A restored draft is never read over.
- **"Guardar cambios" needs a change.** Until something differs from what is stored
  the button is off and the line above the keys says "Aún no cambiaste nada.". A note
  that differs only in outer spaces is the same note.
- **Saving is `EditExpense` of the version read**, through the same steps as recording:
  identifier saved first, form locked, an unknown outcome settled by `find`. It is
  never turned into recording a new expense. When it commits the form closes and the
  list says "Gasto actualizado"; there is no receipt screen, because the list shows
  the expense as it is now.
- **The category it is in stays offered even if archived since.** No other archived
  category is.
- **Other months are named before saving.** When the expense is recorded in, or the
  draft would move it to, a month that is not the current one, a line under the date
  says "Corregir este gasto cambia las cifras de septiembre." (or of two months). The
  dismissible first-time announcement that past expenses can be corrected also shows.
- **No preview.** The preview adds the draft to the month, and a recorded expense is
  already in it.
- **"Borrar" deletes at once**, through `Corrections`, and returns to the list,
  where it can be undone for six seconds. It does not ask first.

| What happened | What the person sees | Then |
| --- | --- | --- |
| The expense was changed since it was read | "Este gasto cambió mientras lo corregías…" | Nothing more can be sent or deleted from this form; leave and open it again |
| The expense no longer exists | "Este gasto ya no existe…" | The same |
| Storage failed, a future date, a category that stopped taking expenses, data replaced | The same reasons as when recording, worded "El gasto quedó como estaba" | The draft is kept |
| Deleting failed | "No se pudo borrar. El gasto sigue anotado." | The form stays open |

## Layout

`EntryLayout`, top to bottom: ✕ and the Gasto/Ingreso switch (not when correcting),
the amount slip (label, date pill, the chosen category's icon, the figure), the chips
with Nueva and the note row, then **Anotar gasto** and the compact pad. Only the chips
and note scroll; in a window too short to keep two chip rows with the amount pinned,
the amount scrolls with them. Sideways, or 600dp wide or more, the slip and choices are
at the left and the button and pad at the right. The composer's keyboard covers the
form without resizing or flipping it.

**No key moves.** The button sits 8dp above the pad, both held from the bottom, and
everything the form says (what is missing, why a save failed) is laid out above the
button. A message appearing or going changes the space above the button, never where
the button or a key is.

## Leaving

Back with an amount or a note typed asks "¿Descartar este gasto?"; when correcting,
a change asks "¿Descartar los cambios?". While saving it does nothing.

## Not here yet

- Batch or repeating entry: none in the MVP.
- The date picker is the platform's, tinted with Sello's colours, not a Sello component.
- Opening as a sheet with the dock morphing into the slip.
