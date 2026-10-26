# SELLO-121 — Export everything to CSV or JSON

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-119, SELLO-004
- **Gate:** G2

### Outcome
From Configuración › Tus datos a person exports their whole history as CSV or JSON, and
either saves the file or shares it with another app.

### Deliverables
- An analytical export, separate from backups: CSV is one table of every money movement
  (expenses, incomes, goal contributions and withdrawals, investment contributions,
  withdrawals, dividends and recorded values) with date, kind, name, amount, currency and
  note; JSON holds the same movements plus categories with their limits, goals,
  investments and recurring items.
- The Exportar a CSV o JSON row, a format choice, and Guardar (document picker) or
  Compartir (a share sheet with a temporary file).
- The plaintext disclosure before exporting, as for backups.

### Acceptance criteria
- Amounts are exact, written from minor units, with their currency; dates are ISO dates.
- CSV cells that a spreadsheet would run as formulas are neutralized; quoting follows
  RFC 4180; text is UTF-8.
- One consistent snapshot: the export never mixes two revisions of the history.
- Shared files live in a narrowly scoped cache directory and are deleted afterwards and on
  the next start; the share grants read access to that one file only.
- A failed read is an error, never an empty file; a failed write says the file may be
  incomplete.

### Tests
- Data host: CSV and JSON for a synthetic history with independent expected text,
  formula injection, quotes and line breaks, USD cents, every movement kind.
- App host/device: the format and destination choice, the disclosure, a save through the
  picker, a share's file cleanup.

### Working checkpoint
An export of a sandbox history opens correctly in a spreadsheet and as JSON.

### Context and starting points
ARCHITECTURE §7 (exports are not backups, formula injection, plaintext disclosure,
provider documents only, scoped cache files); [MVP data handling](../../../privacy/mvp-data-handling.md);
the backup writer and its document ports.

### Implementation plan
1. Export codecs in data.
2. Save and share adapters, with the cache cleanup.
3. The row, the choices and the disclosure.

### Concrete cases and pitfalls
- A note starting with `=`, `+`, `-`, `@`, tab or carriage return.
- Merged manifest and privacy checks must keep passing with the share provider.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-121 --retain`.

### Execution progress

- **2026-10-26:** Built as follows, proposed for review with this ticket's pull request.
  The CSV has the columns Fecha, Tipo, Nombre, Monto, Moneda, Nota, one line per
  movement by date (on one date: expenses, incomes, goal movements, investment
  movements, each in recording order); amounts are written from minor units with a
  point (`12500`, `1050.50`); it starts with a UTF-8 byte order mark so spreadsheets
  read accents, uses commas and CRLF, and a cell starting with `=`, `+`, `-`, `@`, tab
  or carriage return gets a leading `'`. The JSON (`sello-export`, version 1) keeps text
  as written and uses stable codes; it names categories, goals and investments instead
  of carrying identifiers, so it cannot be restored. Files are named
  `sello-datos-AAAA-MM-DD.csv|json` in the financial zone. A shared export lives in
  `cache/exports/`, served by Sello's FileProvider with read access granted per share;
  the next share and the next start remove it. The manifest privacy check now allows
  that one provider to grant access, with its authority and paths checked.
