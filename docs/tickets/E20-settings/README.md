# SELLO-E20 — Configuración as designed

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** Configuración matches the design snapshot's screen 16, with backup and restore first and easiest to reach, and a person can export all their data to CSV or JSON.
- **Exit:** Configuración shows Tus datos with the backup card first, then Lo que se repite, Apariencia, Recibo and Inversiones; investments have a default currency and list order; Exportar saves or shares a CSV or JSON of everything.

## Decisions

**Owner direction (2026-10-25):** the owner approved a mockup of Configuración, based on
[screen 16 of the design snapshot](../../design/sello-spec.html#s-config), with these changes:

- No Avisos slip: budget alerts are not part of this epic.
- Tus datos comes first. It opens with a backup card: the last backup's date, its age and
  size, a stamp (Guardado; Pendiente after more than 30 days; Sin respaldo when there is
  none), and the buttons Respaldar (Crear when there is no backup yet) and Restaurar,
  then a "Ver qué contiene" link to Respaldo. Below it, Exportar a CSV o JSON and Borrar
  todos los datos, with no torn or dotted line between the card and the rows.
- Lo que se repite is its own slip, right after Tus datos.
- Inversiones holds Moneda al crear (COP or USD), Orden de la lista and the Dólar rate,
  which moves there from Tus datos. Rates stay manual: no automatic rates, no EUR or GBP.
- Export holds everything, always the whole history, and offers both saving a file and
  sharing it. The CSV is one table of every money movement; the JSON also carries
  categories with their limits, goals, investments and recurring items.
- The footer reads "Sello · tus datos no salen de este teléfono".
