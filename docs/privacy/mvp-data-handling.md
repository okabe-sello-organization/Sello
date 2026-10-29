# MVP data handling

What Sello keeps, where, what can leave the phone, and the checks that hold it to
that. It describes the built app as of SELLO-032; the
[architecture](../../ARCHITECTURE.md) §7 and decisions D08/D09 set the rules.

## The promise

Sello has no account, no server and no network permission. Everything stays in the
app's private storage on the phone. The only things that leave are a backup or an
export the person saves themselves, to a place they choose, or an export they share
with an app they choose.

## What is stored

All of it is in the app's private storage (`/data/data/com.software.sello`), readable
only by Sello. Nothing is written to shared storage; the cache holds only an export
while it is being shared.

| Where | What | Notes |
| --- | --- | --- |
| `databases/sello.db` (+ `-wal`, `-shm`) | Categories, budget limits, expenses and incomes with their notes; goals with their names, targets, movements and notes; recurring payments and incomes with their notes and pauses; investments with their names, descriptions, movements and notes, and the USD rates entered; the profile (financial zone, generation, revision); operation receipts; at most one deleted expense, income, goal movement or investment movement kept to undo it | Receipts hold an operation ID, kind, input digest, generation, revision, time and a record ID, never an amount, name or note. The undo copy is removed whenever storage opens. [Storage](../development/storage.md) |
| `files/datastore/sello-preferences.preferences_pb` | Theme, ink, category order and direction, Inversiones' currency and order, two "seen" flags for correction hints, and the last backup: date, counts, size and file name | The theme, ink and the two orders and currency travel in a backup; the rest does not. |
| `cache/exports/` | At most one export the person chose to share, CSV or JSON | Plaintext financial data while it exists. Removed by the next share and when Sello next starts. |
| `files/sello-restore/` | A backup file chosen to restore, until it is confirmed, cancelled or a day old; after a restore, the backup's preferences until they are applied | Plaintext financial data while it exists. |
| `files/sello-reset/pending.properties` | During an erase only: its operation ID and whether preferences were reset | No financial data. |
| `databases/sello-unreadable.db` (+ companions) | The database that could not be opened, kept aside by Empezar de cero or a restore at start-up | Plaintext financial data. Never shared or backed up; erased by Borrar todos los datos. ([ADR 0008](../decisions/0008-unreadable-storage-start-over.md)) |

The debug build adds a separate sandbox with synthetic data: `sandbox.db`,
`sandbox-preferences`, `sandbox-restore/`, `sandbox-reset/` and the `sandbox-session`
shared preferences. No sandbox file is shared with the normal profile, and the release
build contains none of this code (see Checks).

## What can leave the phone

- **A backup file**, only when the person taps Crear respaldo and chooses a place in
  the system dialog. It is plaintext JSON with every amount, category, goal and investment
  name, description and note, and the USD rates entered: no password, encryption or checksum ([format](../data/backup-format.md)). Respaldo
  says so above the button before the place is chosen (D08, P01–P02), and that a
  cloud provider keeps it on its own terms. Sello never deletes or claims to erase a
  saved file; Borrar todos los datos says the files stay.
- **An export**, only when the person taps Exportar, chooses CSV or JSON and then
  Guardar (a place in the system dialog) or Compartir (an app in the share sheet). It
  is plaintext with every movement's amount, name and note; JSON adds categories with
  their limits, goals, investments, the USD rates and recurring items. Exportar says
  so before anything is written, that it is not a backup, and that a shared copy is the
  other app's to keep. A shared export is served from `cache/exports/` by Sello's own
  provider, which grants the chosen app read access to that one file and nothing else.
- **Nothing else.** There is no INTERNET permission. Android's cloud backup and
  device-to-device transfer are off (`allowBackup="false"`) and every storage domain is
  excluded in `data_extraction_rules.xml` and `backup_rules.xml`, so they do not depend
  on one attribute.
- **Debug only:** Sello Herramientas can share a sandbox diagnostic through the share
  sheet, on request. It lists operation IDs, kinds and outcomes, never amounts, names
  or notes. Release builds do not contain it.

## Reading and writing files

Sello asks for no storage permission. Backups and saved exports are written to and
read from documents the system pickers return; the adapters accept only provider
(`content://`) addresses, so a file path, Sello's own private files included, is
refused before any byte moves. The one FileProvider serves only `cache/exports/`
(`res/xml/export_paths.xml`), is not exported, and grants read access per share to the
one file shared; a file name that is a path is refused. No other private file is
exposed to other apps.
Staging and pending-erase folders belong to one profile and are cleared only by that
profile's restore, reset or set-aside.

## What an erase leaves

Borrar todos los datos removes every financial row, undo copy and receipt, the staged
restore and the kept unreadable copy, compacts the database so no erased row survives
in its free pages or log, and returns every preference to first-run. What remains: the
profile (the device's zone, the next generation and revision) and one `history.reset`
receipt with no subject. Saved backup files are outside Sello and are not touched.

## Logs

The app writes nothing to the system log. Libraries in use (Room, DataStore, Compose,
Koin) do not log stored values.

## Exported parts of the app

The release manifest exports two components: the main activity (launcher and the
`sello://anotar` draft link, which can only prefill a draft) and AndroidX's
profile-install receiver, which only the system or adb can call (it requires DUMP).
Its providers, AndroidX start-up and the export FileProvider, are not exported; the
export provider grants per-share read access to one file in `cache/exports/`. The only
permission requested is AndroidX's signature-protected one for private dynamic receivers.

## Checks

| Check | What it proves |
| --- | --- |
| `:app:verifyReleaseManifestPrivacy`, `:app:verifyDebugManifestPrivacy` (after each assemble) | The merged manifests: backup off, every domain excluded, only allowed permissions, providers and exported components, each with its reason; only the export provider may grant URI access, with its own authority, and its paths file serves only `cache/exports/`. Report: `app/build/reports/privacy/manifest-*.txt` |
| `ExportJourneyTest` (device) | A share grants read access to one file, the next share removes it, clearing empties the folder, and a path is not accepted as a name |
| `:app:verifyReleaseDependencies` | No test, tooling or test-manifest library in the release runtime. Report: `release-dependencies.txt` |
| `:app:verifyReleaseHasNoDeveloperTools` | The release APK holds no developer tools, sandbox names, debug composition or previews |
| `FileBoundaryTest` (device) | File paths and other non-provider addresses are refused; private files are untouched |
| `ProfileFileBoundaryTest` (device) | One profile's set-aside or erase leaves another profile's files and outside documents alone |
| `LogPrivacyTest` (device) | Synthetic names, notes and amounts carried through backup, restore and reset never reach the log |
| `ResetGenerationContractTest` (device) | Nothing erased survives in the database bytes or private folders |

Inventories from the SELLO-032 run are kept in
evidence.
