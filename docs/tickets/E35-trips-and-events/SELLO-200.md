# SELLO-200 — See what an event cost

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-198, SELLO-192
- **Gate:** G1

### Outcome
The person opens an event and sees its total, what each category took, and every expense
day by day; they can rename or delete it.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/events-mock.html).
- Resumen's stubs become two by two: Ingresos, Fijos, Movimientos and Eventos (with the
  number of events).
- Eventos lists every event, the one with the latest expense first, with its count, the
  dates of its first and last expense, and its total. It ends with Nuevo evento.
- An event's page shows the total across every month it touches, the count, and the first
  to last date. Then each category's sum with its share of the event (half up, whole
  percent), then the expenses day by day as in Movimientos. Tapping one opens its editor.
- An event with no expenses shows Sin gastos and a total of 0.
- ⋮ holds Cambiar nombre and Borrar evento. Deleting asks once, says the expenses stay
  recorded, and removes only the tag.
- States: loading, failed, older figures, an event deleted elsewhere (Gone), and the page
  restored after process death.
- JVM tests with hand-worked literals: 980.000 + 620.000 + 410.500 + 330.000 = 2.340.500,
  shares 42 %, 26 %, 18 % and 14 %; an event spanning two months; an empty event.
  Screenshots of each screen and state.
