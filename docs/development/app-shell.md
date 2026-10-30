# App shell, month session and navigation

**Owner:** SELLO-015 · Rules: [architecture](../../ARCHITECTURE.md) §§2, 6 ·
Design: [Sello v2](../design/v2/README.md) shells and [components](../design/v2/components.html).

The frame every screen sits in, and the state that belongs to the whole app instead
of to one screen. All of it is in `:app`, package `navigation`; every route is declared
in `Routes.kt`.

## What is on screen today

The tab bar switches between Recibo and Resumen (`ShellTab`). On both, the title is
the selected month and opens the month picker, and the dock records an expense.
Recibo's settings action opens Configuración. Recibo and category detail are
described in [Recibo and category detail](recibo-and-detail.md); the forms in
[Anotar](expense-entry.md) and the [category editor](category-editor.md).

Which category is open is the frame's own saved state (`ShellState.detailCategoryId`),
next to the month and the picker, so it survives rotation and the process being killed.

## The selected month

`MonthSession` holds the one month every screen shows. A screen's view model observes
`selectedMonth` and never keeps its own.

- It follows the current financial month, or stays on an earlier month the person
  picked. A later month can never be selected.
- After **more than 30 minutes** in the background it returns to the current month.
  Exactly 30 minutes keeps the selection. Drafts and a pending entry request are
  untouched.
- `FinancialClock` says which month is current; `MonotonicClock` measures time away.
  Moving financial time, real or simulated, is not time away.
- "Background" is the activity being stopped, so a dialog or the keyboard over the app
  does not count.

## What survives rotation and the process being killed

`ShellViewModel` saves small values in its `SavedStateHandle`: the picked month, the
moment the app left the foreground, whether the picker is open and on which year, and
a pending entry request as an identifier and a number. On a new process these pass
the same validation as any outside input. The 30-minute rule uses the saved moment,
so being killed in the background neither loses the selection nor extends it. Other
screens follow architecture §6.

## Back

`backTarget` is the single order: an open sheet or side panel closes first, then a
detail screen, then Resumen returns to Recibo, and Back on Recibo leaves the app.

## Windows

`ShellScreen` uses the design system's `SelloScaffold`, which keeps content clear of
the system bars and hides the dock and tab bar while the keyboard is open. The dock is
the v2 full-width **+ Anotar** button, read as "Anotar un gasto", and the tab bar is
`SelloNavigationBar`, which marks the selected tab with a `brandSoft` pill behind its icon
(SELLO-065); in a short or wide window the rail keeps its own "+". Income history
passes its own dock, **+ Anotar ingreso** (read "Anotar un ingreso"), and its own title,
"Ingresos de octubre" (SELLO-071); every other screen's dock records an expense. On Recibo
and Resumen the top bar is the v2 `MonthBar` (SELLO-068): **Octubre** large, a small
**2026** and the chevron; the button keeps the spoken
"Cambiar de mes. Mes actual: …" label and opens the same picker. Pushed screens with a
back arrow (Configuración, income history, a category opened on a phone) keep their
titled `MonthSwitcher` bar. The month
picker is a bottom sheet, opened whole and scrollable so a large font or short window
cannot cut months off; in a window wide enough it is a side panel.

"Mes actual", at the right of the picker's title, sends the same `PickMonth` as the
current month's cell from whatever year is shown, so it follows a month that turns
while the picker is open. A screen reader hears the destination, "Ir al mes actual:
Octubre 2026". With a large font it goes under the title instead of being cut.

While an earlier month is selected, every screen that shows the month (Recibo,
Resumen, income history and a category's detail, but not Configuración) has the
`PastMonthRibbon` under its bar (SELLO-064): **Estás viendo septiembre**, with the year
when it is not the current one's ("septiembre de 2025"), and **Volver a octubre**. The
message is a polite live region. The button sends `ReturnToCurrentMonth`, which selects
the month the financial clock says is current when it is handled, so a tap just after
midnight cannot pin the month that has just ended. The ribbon is drawn from the shell's
selected and current months and keeps nothing of its own: it survives rotation and
process death with the selection and disappears when the current month is selected,
including when the month turns. The scaffold draws it on the band under either the v2
bar or a titled bar, and the band grows with it.

## What is said about a correction

`Corrections` (`presentation/corrections`) is one object for the whole process.
It deletes and undoes expenses and income, and holds the one notice shown above the
dock on Recibo, Resumen, category detail and income history. Forms do not show it;
leaving the form does.

| Notice | Shown | Action |
| --- | --- | --- |
| "Gasto borrado", "Ingreso borrado" | Until the time to undo is over | "Deshacer" |
| "No se pudo deshacer. Inténtalo de nuevo." | The same, after an undo that failed | "Deshacer", the same operation again |
| "Gasto restaurado", "Gasto actualizado", "Ingreso restaurado", "Ingreso actualizado" | Four seconds | none |
| "Categoría actualizada", "Límite de septiembre actualizado. Sus cifras ya lo usan.", "Categoría archivada", "Categoría desarchivada" | Four seconds | none |
| "Ya no se puede deshacer…", "No se pudo deshacer porque tus datos cambiaron…", for either kind | Four seconds | none |
| A confirmed save (`CorrectionNotice.Confirmed`), with a check before the words its screen writes | Four seconds | none |

- **The time to undo belongs to the [commands](commands.md#the-six-seconds).** The
  notice drops the offer when the same monotonic clock says it is over, and checks
  again when the app returns to view.
- **Drawing again changes nothing.** Rotation, a resize or another screen in between
  show the same notice with the same time left. A process that ends has no notice.
- **A deletion runs to its end** even if the screen that asked for it is gone.
- **The newest notice wins.** A statement or confirmation takes the place of whatever
  is shown, an offer to undo included; that deletion stands.
- **Read aloud** as "Gasto borrado. Puedes deshacerlo durante 6 segundos." (or
  "Ingreso borrado. …"), announced when it appears.

After a new expense or income is saved, the form closes and the shell shows the
approved [v2 save feedback](../design/v2/README.md) in this confirmation look
(SELLO-066): "Gasto anotado: 48.700 en Comida" or "Ingreso anotado: 150.000", worded
from the record read back from storage, on whichever screen the form was opened from.

## Routes and links

- Routes are `@Serializable` objects in `Routes.kt` and carry identifiers only, never
  an amount, a record or a result.
- `EntryLinks` understands `sello://anotar?categoria=<identifier>&monto=<amount>`.
  A link is outside input: anything not exactly that shape is ignored, and each value
  must pass the same rules as typed input or it is dropped, never repaired.
- The dock and a link both become `ShellState.pendingEntry`, a request to open the
  entry form. The shell has no access to a command and cannot save anything. It sets
  `entryReady` or `entryNeedsCategory`; the root navigates and reports `EntryTaken`.
  The form checks the prefilled category and amount itself.
- The manifest link is not `BROWSABLE`, so only apps on the device can open it.

## View models

Screens get view models through the one factory in `composition`, described in
[time and composition](time-and-composition.md). A `…Root` composable owns the view
model and lifecycle effects; the screen below it takes state and callbacks only.
