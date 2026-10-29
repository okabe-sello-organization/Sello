# MVP release checklist

The go/no-go record for Sello's MVP, SELLO-036. The decision is the owner's
([D10](../decisions/0001-mvp-contract.md#d10--release-acceptance-and-review)); this
checklist gathers what it rests on. Distribution is a private APK; nothing is
published, uploaded or tagged by this record.

## Decision

| | |
| --- | --- |
| Product go/no-go | The project owner. Recorded by merging the SELLO-036 pull request. |
| Independent technical review | The project owner, independent of the executor; executor self-review is not this review. |
| Device testing and signing custody | The project owner. |

## Candidate

| | |
| --- | --- |
| File | `sello-0.1.0-1.apk` (`build/release-candidate/`, not committed) |
| Application ID | `com.software.sello` |
| Version | name `0.1.0`, code 1 |
| Size | 4,458,295 bytes |
| SHA-256 | `1e28bb8c14d093b67846036fc96282bc94c3cbae06a7e60c95125a89fefa16a9` |
| Signature | APK Signature Scheme v2, one signer |
| Certificate SHA-256 | `abf6ccf5b58de43626daea387d5c18c67981e42c302f8a1c7e16ad5cf0b4d952`, confirmed by the owner on 2026-10-23 |
| R8 mapping SHA-256 | kept beside the APK; recorded in its facts file |
| Source | the SELLO-036 commit named in the ticket's delivery evidence |

Keep the mapping file with the APK: stack traces from this build can only be read with
it. The [release](README.md) page explains signing, the gate and changing the key.

## Supported devices and configurations

- Android 11 (API 30) to the target API 37, phones and larger windows.
- Tested on the API-30 emulators (gate and measurements) and a Samsung SM-S731B on
  Android 16 (API 36), 1080 × 2340, the owner's device-testing phone.
- Compact, short landscape, medium and expanded windows; text at 1.0, 1.3 and 2.0; all
  four themes ([UI acceptance matrix](../testing/ui-acceptance-matrix.md)).
- One currency (COP), one person, one device; no network, no account.

## Accepted MVP scope

Categories with monthly limits, expenses and incomes with edit and undo, Recibo, a
category's detail, Resumen, appearance and category order, the `sello://anotar` entry
link (a draft only), plaintext backup and restore, erase-all, and the start-up failure
recovery. Not in the MVP and absent from the app, with no dormant modules: Patrimonio
or investments, recurrence, batches, exchange rates, alerts, widgets, capture, signed
carryover (E09), legacy import (E10) and the manual assistive-technology pass (E14).

## Evidence

- All 48 other MVP tickets are Done with validated quality reports
  (`python3 scripts/quality/tickets.py check`): epics E01–E07, E12, E13, and in E08
  SELLO-033, SELLO-034 and SELLO-035.
- Windows, text and themes: [UI acceptance matrix](../testing/ui-acceptance-matrix.md).
- Recovery and retention: [recovery matrix](../testing/recovery-matrix.md).
- Performance: [performance profile](../testing/performance-profile.md); every approved
  target met on the shrunk release.
- Privacy: [MVP data handling](../privacy/mvp-data-handling.md).
- Libraries: [dependencies](dependencies.md) and [advisories](advisories.md).
- The final G3 run and its counts: SELLO-036's delivery evidence.

## Owner walkthrough on the signed candidate

On the Samsung SM-S731B (Android 16), fresh install of the candidate, synthetic data,
each figure checked against the value worked out beforehand. Screenshots:
evidence.

| Step | Expected and seen |
| --- | --- |
| Fresh install | First-run screen; nothing stored |
| Category with a budget | Mercado, limit 300.000: 300.000 left, 33.300 a day over 9 days |
| Expense | 48.700 with the note "Plaza": 251.300 left, 27.900 a day |
| Edit | 52.000: 248.000 left |
| Delete and undo | Deleting shows 300.000 and "Gasto borrado"; Deshacer brings back 248.000 |
| Income | Salario 4.200.000: Resumen shows 4.200.000 recorded, 4.148.000 after spending, 3.900.000 unassigned |
| Appearance and order | Light, Violeta, alphabetical: Recibo in violet, 248.000 and 27.550 a day |
| Backup | "Guardado": 1 category, 1 expense, 1 income, 1.6 kB |
| Change | Arriendo with 200.000 and a 10.000 expense: 438.000 left, 48.650 a day |
| Restore | Preview now 2/2/1 against backup 1/1/1; after replacing, 248.000 and only Mercado, in a new process |
| Restart | Force-stop and start: 248.000 |
| Erase all | Counts 1/1/1, BORRAR, "Borrado"; first-run screen after a restart, default appearance |
| Use again | Comida with 500.000 and a 25.000 expense: 475.000 left, 52.750 a day |

**Blocker found and fixed during this walkthrough.** On the first candidate every backup
failed on the phone with "Tus datos superan lo que cabe en un respaldo"
([screenshot](../tickets/E08-release/attachments/SELLO-036-00-respaldo-fallido-antes.png)). Android 16's
clock gives microseconds and the backup's own timestamp kept them, which the format
refuses; the API-30 emulators' clock gives milliseconds, so no earlier test met it. The
export now rounds its timestamp to the millisecond (red first in
`BackupSnapshotConsistencyTest`), and the whole walkthrough was repeated on the rebuilt
candidate from a fresh install.

## Developer apps

On the API-30 emulator: the catalog opened its groups and switched ink, mode and text
size (Violeta, dark, 1.3×); the debug app's tools entered the sandbox, loaded G01 and
ran its 8 steps to "4 de 4 cifras coinciden". Neither the catalog's nor the sandbox's
code, names or resources appear in the candidate APK, and the build's developer-tools
check passes on it.

## Known issues accepted as non-blocking

Approved by the owner on 2026-10-23; none affects money or recovery.

| Issue | Ticket |
| --- | --- |
| Counts of 1,000 or more have no thousands separator | [SELLO-075](../tickets/E15-release-fixes/README.md) |
| Resumen shows "Movimientos anotados hasta 2026-10-23" | [SELLO-076](../tickets/E15-release-fixes/README.md) |
| Two sandbox scenario tests fail intermittently on CI | [SELLO-077](../tickets/E15-release-fixes/README.md) |
| A backup that fails its own check is reported as too large | [SELLO-078](../tickets/E15-release-fixes/README.md) |
| The app shows no third-party licence notice | Owner decision, not ticketed ([release](README.md)) |

## Installing and recovering

- **Install:** copy the APK to the phone and open it; Android asks to allow installs
  from that source once. Updates install over it only when signed with the same key.
- **Back up regularly:** Configuración → Respaldo → Crear respaldo, saved where you
  choose. The file is plain text: anyone who opens it can read the amounts and notes.
- **Restore:** Respaldo → Restaurar → Elegir archivo. The preview shows what is on the
  phone against what the file holds; replacing removes what is there now.
- **Changing phones or reinstalling:** uninstalling deletes Sello's data; only a backup
  brings it back. Erasing all data inside Sello leaves backup files untouched.
- **If Sello cannot open its data,** it offers to restore a backup or start over,
  keeping the unreadable file aside.

## Next increment

Post-MVP epics in the [roadmap](../tickets/README.md): E09 signed budget
carryover, E10 legacy import, E14 the assistive-technology pass and E15 the fixes above.
