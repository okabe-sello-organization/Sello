# Sello

A new local-only Android financial application, modernizing the legacy app through
a deliberately smaller, tested MVP and the Sello visual language.

## Start here

- [Sello v2 design](docs/design/v2/README.md): the locked screens and shared
  components, the visual source of truth ([ADR 0013](docs/decisions/0013-sello-v2-design.md)).
- [Design philosophy](docs/design/philosophy.md): the purpose, working motto and
  principles for new screens, with [superseded decisions and transition](docs/decisions/0011-goal-centered-design.md).
- [Screen and interaction briefs](docs/design/screen-briefs/README.md): the product
  intent and open policy behind each v2 screen.
- [Current app experience baseline](docs/development/current-app-experience.md):
  the implemented screens, navigation, behavior, adaptive layout and component
  families to use as the starting point for UI/UX work.
- [Current navigation graph](docs/development/navigation-graph.md): the visual route
  map, screen composable hierarchy and navigation test coverage.
- [Architecture and guardrails](ARCHITECTURE.md): ownership, financial/recovery
  contracts, technologies, MVP scope and developer tooling requirements.
- [Tickets](docs/tickets/README.md): one folder per ticket with its evidence,
  retained report and handoff; statuses, definition of done and gate profiles.
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
- [Quality gates](docs/development/quality-gates.md): `./scripts/verify-ticket`, the
  evidence every ticket needs and what CI enforces.
- [Design references](docs/design/README.md): Sello v2, the v1.1 snapshot and provenance.

The app currently implements Recibo and Resumen tabs, expense and income
entry/history, category and limit management, corrections with guarded undo,
persisted appearance/order settings, adaptive navigation and unreadable-storage
startup recovery. The component catalog is populated. Backup/restore/reset and the
live-testing sandbox (debug only) are not customer-facing capabilities; see the current app
baseline and tickets for the exact boundary.

## Local host verification (G1)

Use the installed Android SDK and Android Studio JBR, or the validated replacement
matrix documented by SELLO-002:

```bash
ANDROID_HOME=/home/okabe94/Android/Sdk JAVA_HOME=/opt/android-studio/jbr \
  PATH="/opt/android-studio/jbr/bin:$PATH" \
  ./scripts/verify-ticket SELLO-0XX
```

Replace `SELLO-0XX` with the ticket being worked on. The command runs that ticket's
gate (ticket and link checks, architecture rules, ktlint, unit tests, lint and assembly for
G1) and writes a report under `build/reports/quality/`. Default ticket commands run
full coverage. For a G2 ticket, `./scripts/verify-ticket SELLO-0XX --affected origin/main --retain` selects device modules and their consumers since the PR base, while
`--gate G2` forces all device suites. Fetch the intended base first; unrelated or
unknown inputs select full coverage. See the [local commands and selection map](docs/development/quality-gates.md#quality-runner).

For documentation-only work advancing no ticket, run
`./scripts/verify-ticket --merge-candidate origin/main`. It derives G0 (ticket
structure, tool tests, architecture and links, Python/Git only); it cannot replace a
ticket's G1/G2/G3.
Hosted code pushes retain G1 to warm trusted main caches; documentation pushes use
G0, and the nightly verifies `main` whole at G3 once it moved.

Build/dependency/IDE caches, machine-local configuration and signing secrets are
not tracked. No Git remote or hosted Jira project is configured by this setup.
