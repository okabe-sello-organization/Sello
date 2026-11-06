# First run and the category editor

**Owner:** SELLO-016 · Decisions: [D04, D05](../decisions/0001-mvp-contract.md) ·
Design: [category editor](../design/system/features/category.html#screen-editor) ·
Builds on [commands](commands.md), [categories and limits](categories-and-limits.md)
and the [app shell](app-shell.md).

## What a person sees

- A new installation opens on an empty Recibo, "Dale forma a tu mes", with one
  action, "Crear categoría". There is no backup action until restore exists (SELLO-030).
- Once a category exists, the **+** in the "Tus categorías" header ("Agregar
  categoría") creates another.
- The form (`feature/category`) asks for a name, an icon and a monthly limit.

## The form's rules

| Field | Rule |
| --- | --- |
| Name | Required; the button is off without one. `CategoryName`: up to 24 characters, no line breaks. A name already used, ignoring case and accents and including archived categories, is said on the field **while it is typed**, and the button stays off until it changes. The command also refuses a name taken meanwhile |
| Icon | One of `CategoryIcons.options`. A new form starts on the first icon no category uses |
| Limit | A new form starts with "Sin límite" off and the amount empty. Empty is never zero or no limit: creating without an amount says "Escribe el límite o activa Sin límite." `0` is a real zero budget. "Sin límite" is only ever chosen; while on, it hides the amount and suggestions and keeps the typed amount unsent until it is turned off. The amount goes through `CopAmountInput`; anything else is refused and left as typed |

The limit applies to the current financial month and is the default afterwards, as
`CreateCategory` defines. Text that fails a rule stays exactly as typed next to the
message; nothing is trimmed or corrected in the field.

The limit is **drawn** in groups (`500000` as `500.000`) through `DigitGrouping`, so
its size can be read while typing. The draft, what is validated and what is saved stay
the typed text, without the dots. An error on the name scrolls the name into view.

The limit uses the compact Sello pad, like every amount
([category editor](../design/system/features/category.html#screen-editor)).

## Saving once

`CategoryEditorViewModel` follows the [command protocol](commands.md):

1. "Crear categoría" validates, takes a new operation identifier and **saves it**
   before sending. While the command is out the form is locked.
2. Only `Committed` ends the form as created. A rejection clears the identifier.
3. `OutcomeUnknown`, or a saved identifier found after the process was killed, asks
   `find` about that identifier: a receipt means created, none means the draft is
   ready again. If the answer cannot be read the form stays locked and offers
   "Comprobar si se guardó", which asks again and never creates.

The draft, the identifier in flight and a created result are in `SavedStateHandle`.

## Changing an existing category

The same form, opened by `CategoryEditRoute(categoryId, month)` from
"Editar categoría" on a category's detail, or from a long press or screen-reader
action on its cell in Recibo. `CategoryManagementViewModel` drives it; the form for a
new category is untouched by it. It is titled "Editar categoría" and its button is
"Guardar cambios", off until something differs from what is stored.

**Which month's limit.** The form shows and changes the limit of the month it was
opened from, and always says so under the field. The dismissible first-time
announcement that past limits can be corrected is on category detail.

| Opened from | The limit typed | Command |
| --- | --- | --- |
| The current month | "Vale desde Octubre 2026 en adelante. Los meses anteriores no cambian." | `SetDefaultLimit` |
| A finished month | "Cambias solo el límite de Septiembre 2026. Los demás meses y el límite habitual no cambian." | `SetMonthLimit` of that month |

A month that has a limit cannot be left without one; a month that never had one can
stay so. "Sin límite" is still only ever chosen.

**One save, up to three commands.** Name, icon and limit are separate commands, each
with its own identifier and receipt, sent in that order, each against the version the
one before left. If one fails, those before it are saved and stay saved: the form
says "Se guardó: nombre. Lo demás no se guardó; inténtalo de nuevo.", keeps the rest
of the draft, and the next attempt sends only what still differs. Nothing is rolled
back and nothing is reported saved without its receipt.

**It is read once.** What was read, with its version, is saved beside the draft, so
a restored draft is sent against the category as it was when the form opened. After
an unknown outcome or a killed process, `find` is asked about that operation and the
category is read again; what the draft still has that storage does not remains.

| What happened | What the person sees | Then |
| --- | --- | --- |
| The category changed meanwhile | "Esta categoría cambió mientras la editabas." | "Volver a cargar" starts again from what it is now; "Conservar mis cambios" reads it again and keeps the draft, to save over the new version on purpose |
| It no longer exists | "Esta categoría ya no existe. No se cambió nada." | Nothing can be sent |
| Storage failed | "No se pudo guardar. La categoría quedó como estaba…", or which parts were saved | The draft is kept |

**Archiving.** "Archivar" asks first and says what happens: new expenses stop, this
month keeps its limit, there is no budget from next month until unarchived, and how
many of this month's expenses stay (no number if the snapshot cannot be read), and
that unsaved changes are discarded. An archived category shows "Archivada", keeps its
name and icon editable, and offers "Desarchivar", which says whether its last limit
comes back or nothing else changes. Nothing is deleted, so neither confirmation is
drawn as destructive. Deleting a category is
[ADR 0024](../decisions/0024-deleting-a-category.md) (SELLO-138).

After a confirmed change the form closes and the frame shows its
[notice](app-shell.md#what-is-said-about-a-correction).

## Leaving

Back on an untouched form leaves. With a draft it asks "¿Descartar los cambios?".
While saving it does nothing.

## What the editor returns

The route is `CategoryEditorRoute(forEntry)`. When it ends with a category, the
new identifier is left for the screen that opened it under `CREATED_CATEGORY_RESULT`
in that screen's saved state: an identifier, never a category object. The opener
reads the category itself.

`forEntry = true` is for "an expense was asked for and there is no category that
can take one": the form then explains that a category comes first. The dock and an
entry link both lead here, and after the category is created the app goes on to
[Anotar](expense-entry.md) with it chosen.

## Icons

`CategoryIcons` maps the stored key to a drawing and a spoken name. Keys are stored
and exported, so a key is never renamed or reused. A key this version does not know
still shows a neutral icon. Fourteen icons ship; the reference shows twenty-eight
and names fourteen, so the rest need choosing.

## Not here yet

- The preview of the category cell and the "your month's total limit would be" line
  from the reference.
- Suggested limits from the person's own most used ones; the three fixed ones are shown.
- The form as a side panel on wide windows; it is a full screen everywhere.
