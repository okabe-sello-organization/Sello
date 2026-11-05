# Sello

A new local-only Android financial application, modernizing the legacy app through
a deliberately smaller, tested MVP and the Sello visual language.

## Start here

- [Design system](docs/design/system/index.html): foundations, components and every
  feature as built, the visual source of truth ([ADR 0026](docs/decisions/0026-design-system-from-the-built-app.md)).
- [Design philosophy](docs/design/philosophy.md): the purpose, working motto and
  principles for new screens, with [superseded decisions and transition](docs/decisions/0011-goal-centered-design.md).
- [Architecture and guardrails](ARCHITECTURE.md): ownership, financial/recovery
  contracts, technologies, MVP scope and developer tooling requirements.
- [Tickets](docs/tickets/README.md): one folder per ticket with its handoff;
  statuses, definition of done and gate profiles.
- [Independent executor guide](docs/development/execution-guide.md): prerequisites,
  file ownership, tools, verification procedures and source library.
- [Time and composition](docs/development/time-and-composition.md): the three clocks,
  the financial zone, the Koin composition root and who cancels what.
- [Financial storage](docs/development/storage.md): the Room tables, what the database
  and the mappers each enforce, and how to change the schema.
- [Commands and receipts](docs/development/commands.md): how a change is saved once,
  replayed safely and recovered after an uncertain result.
- [Categories and monthly limits](docs/development/categories-and-limits.md): how a
  month's limit follows from history, and what archiving does.
- [Monthly snapshot](docs/development/monthly-snapshot.md): the one source of a
  month's spent, remaining, allowance, forecast and verdict.
- [App shell](docs/development/app-shell.md): the frame, the shared month, back,
  restoration and entry links.
- [First run and the category editor](docs/development/category-editor.md): the
  form's rules, saving once, and what it hands back.
- [Anotar](docs/development/expense-entry.md): recording an expense, the preview,
  saving once and the receipt.
- [Feature guide](docs/development/feature-guide.md): where a feature's files go and the
  money, write, read, screen and test rules, with Metas as the example.
- [Quality gates](docs/development/quality-gates.md): `scripts/check`, the gates and
  what CI runs.
- [Design references](docs/design/README.md): the design system, the philosophy and what owns money rules.

The app has four tabs: Recibo, Resumen, Metas and Inversiones. It records expenses and
incomes in COP (investments also in USD), with categories and monthly limits, fixed
payments and signed carryover. Configuración → Tus datos makes and restores backups
through the system file picker (restore also from the start-up failure screen), imports
a FinanceTracker backup, exports to CSV or JSON, and erases all data after typing
BORRAR. The live-testing sandbox is debug only. [HANDOFF](docs/development/HANDOFF.md)
has the full current app.

## Local checks

Use the installed Android SDK and Android Studio JBR, or the validated replacement
matrix documented by SELLO-002:

```bash
export ANDROID_HOME=/home/okabe94/Android/Sdk JAVA_HOME=/opt/android-studio/jbr
export PATH="$JAVA_HOME/bin:$PATH"
scripts/check domain app      # ktlint and unit tests of the modules you touched
scripts/check --host          # the host gate G1, exactly as CI runs it
```

The pull request's `quality` check runs the gate its changes need and is the only
proof that checks passed; see [quality gates](docs/development/quality-gates.md).

Build/dependency/IDE caches, machine-local configuration and signing secrets are
not tracked. No Git remote or hosted Jira project is configured by this setup.
