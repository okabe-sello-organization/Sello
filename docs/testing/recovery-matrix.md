# Recovery matrix

Which data versions Sello reads, how every failure on the way to a backup, restore,
reset or upgrade ends, and the test that proves each line. Every test named here runs
on real files: the `:data` and `:app` connected suites, with no mocked database.
Expected rows in them are written out by hand from their fixtures or taken from the
approved examples, never computed by the code under test. SELLO-034 assembled this
matrix; the [backup format](../data/backup-format.md), [storage](../development/storage.md)
and [ADR 0008](../decisions/0008-unreadable-storage-start-over.md) define the rules.

## Supported versions

| From | To | Outcome | Proof |
| --- | --- | --- | --- |
| Database schema 1 | 4 (current) | Upgraded through 2 and 3 on open; every row and identity kept | `ExportedSchemaOnDeviceTest`; `RecoveryRetentionTest` (a full v1 history) |
| Database schema 2 | 4 | Upgraded on open; rows kept, empty `deleted_income` and goal tables added | `ExportedSchemaOnDeviceTest` |
| Database schema 3 | 4 | Upgraded on open; rows kept, empty goal tables added | `ExportedSchemaOnDeviceTest` |
| Database schema 4 | 4 | Opens and keeps its rows | `ExportedSchemaOnDeviceTest` |
| A newer schema than 4 | — | Not opened: the start-up failure screen; the file is kept whole | `UnreadableFileSetAsideTest.aFileFromANewerVersionIsStillWholeAndReadableWhereItWasKept` |
| Backup format 1 | schema 4 | Restored, every record at version 1 (versions are not in a backup); no goals | `RecoveryRetentionTest`, `RestoreCommandContractTest`, `GoalBackupTest` |
| Backup format 2 | schema 4 | Restored with its goals and Recibo's choice, replacing the goals there; same figures on a fresh installation | `GoalBackupRestoreTest`, `GoalBackupTest` |
| Erase all with goals | schema 4 | Every goal, its history, the undo copy and the choice removed | `GoalBackupRestoreTest` |
| A newer backup format | — | Refused as "Lo hizo una versión más nueva de Sello"; nothing changes | `RestoreCommandContractTest.aFileThatCannotBeRestoredChangesNothingAndIsNotKept` |

There is no schema before 1 and no migration from it. The earlier FinanceTracker
app's database is not supported and no compatibility with it is claimed.

## The whole lifecycle, one step feeding the next

`RecoveryRetentionTest`: a version 1 database holding an archived category, budget
history, past-month limits, ordered expenses with notes, a salary and an "other"
income, the Lima zone and a receipt is opened by this build (upgraded, not replaced),
saved as a backup carrying the person's appearance and order, restored into a second
installation made in Bogotá (same history and zone, generation 2, preferences
applied), erased there (nothing financial left, the device's zone, generation 3) and
restored again from the same file (generation 4). The first installation is only
read throughout.

`TemporalScenarioCorpusTest.everyScenarioRestoredFromItsBackupKeepsItsRowsAndLastFigures`
(nightly):
each of the nine sandbox scenarios is played to the end, backed up, and restored into
a new installation at the same simulated day. The restored rows equal the originals
for all nine; for the seven whose last check follows their last saved change, that
check runs again on the restored rows and every figure matches (24 figures).
`seis-meses` has no check and `bordes` saves changes after its last one; both are
compared row for row.

## Restore

| Failure point | Outcome | Proof |
| --- | --- | --- |
| Not a backup, cut short, damaged, too large, invalid amount, newer format | Rejected by kind; financial rows, generation, preferences and staging unchanged | `RestoreCommandContractTest.aFileThatCannotBeRestoredChangesNothingAndIsNotKept`; `RestoreViewModelTest` |
| Preview cancelled | Nothing changes; the staged file is forgotten | `aCanceledPreviewChangesNothingAndForgetsTheFile` |
| Process ends after staging | The staged file is offered again under its own operation | `aStagedFileSurvivesARestartWithItsOwnOperation` |
| Staged file a day old, or the generation moved | Forgotten, never reinterpreted | `anUnconfirmedFileExpiresAfterADay` |
| History changed after the preview | Refused as stale; counted again | `aChangeAfterThePreviewRefusesTheReplacementUntilPreviewedAgain` |
| Failure inside the transaction | Everything rolled back | `aFailureInsideTheReplacementRollsEverythingBack` |
| Caller cancelled around the commit | The receipt shows it committed; asking again replays it | `aCallerCancelledAroundTheCommitStillFindsTheReplacementDone` |
| Crash between the commit and the file rename | Replayed by receipt, not replaced twice | `askingAgainAfterACrashBeforeTheFileMovedReplaysTheSameReplacement` |
| Preferences fail after the commit | "Historial financiero restaurado" with only those preferences; retried without a second replacement, also after a restart | `preferencesThatFailAreNamedAndRetriedWithoutASecondReplacement` |
| Another file while preferences are pending | Refused until they are finished | `aPendingReplacementBlocksStagingAnotherFile` |
| Leaving after a restore, by Back or the top bar's arrow | The app starts again on the restored history | `RestoreJourneyTest`, including `theTopBarsBackArrowAfterARestoreStartsTheAppAgain` |

## Reset

| Failure point | Outcome | Proof |
| --- | --- | --- |
| Cancel or any word but `BORRAR` | Nothing changes | `ResetViewModelTest.anythingButTheExactWordErasesNothing`, `cancellingTheWordGoesBackToTheCountsAndForgetsWhatWasTyped` |
| History changed after the counts | Refused; counts again and both confirmations again | `ResetGenerationContractTest.aChangeAfterTheCountsRefusesTheEraseAndForgetsIt` |
| Failure inside the transaction | Everything rolled back, kept copy untouched | `aFailureInsideTheEraseRollsEverythingBack` |
| Process ends before the commit | The pending file is forgotten; nothing erased | `anEraseThatNeverCommittedIsForgottenAndChangesNothing` |
| Caller cancelled around the commit | Found by receipt; asking again never erases twice | `aCallerCancelledAroundTheCommitStillFindsTheEraseDoneAndNeverErasesTwice` |
| Preferences fail after the commit | Named; finished later, once, without a second erase | `preferencesThatFailStayPendingAndAreFinishedLaterWithoutASecondErase` |
| What survives | Profile and one `history.reset` receipt; no erased name or note in the file's bytes | `nothingLeftInTheFilesHoldsAnOldNameOrNote` |
| After the erase | A new category and expense from empty; a backup restores across it | `theNewHistoryCanBeUsedFromEmpty`; `RecoveryRetentionTest` |

## Old work across a new generation

Restore and reset each move the generation, so anything prepared against the old one
is refused, even when restored record identifiers match earlier ones.

| Attempt | Outcome | Proof |
| --- | --- | --- |
| Draft saved after a restore or reset | `StaleGeneration` | `oldDraftsAndUndoCannotCrossTheReplacement`, `oldDraftsAndUndoCannotCrossTheErase` |
| Undo of a deletion from before | `StaleGeneration`; the kept copy is gone | the same tests |
| Sandbox scenario mid-run when a reset happens | Its next step is refused | `ScenarioReplayTest.aResetBetweenStepsKeepsTheRunnerOutOfTheNewHistory` (nightly) |
| The same scenario in a new process after the reset | Finds no receipts, does not advance, writes nothing back | `ScenarioReplayTest.afterAResetANewProcessBringsBackNoneOfTheScenarioItErased` (nightly) |

## Corrupt or unavailable storage

| Situation | What happens | Proof |
| --- | --- | --- |
| The database cannot be opened (damaged, newer, unreadable) | The start-up failure screen; it is never shown as an empty ledger. Intentar de nuevo, Restaurar un respaldo or Empezar de cero | `StartupTest`; `StartupFailureScreenTest` (the screen, on the JVM) |
| Empezar de cero | The file is kept whole as `sello-unreadable.db`; the next open is empty | `UnreadableFileSetAsideTest.aDamagedFileIsKeptByteForByteAndTheNextOpenIsANewEmptyHistory` |
| Restore at start-up | Checked before anything moves; set aside, new store filled from the backup | `RestoreAfterSetAsideTest`; `StartupTest` |
| Restore at start-up cannot finish after the set-aside | "La restauración no terminó", then a restart; Respaldo can restore again | `StartupTest.aReplacementThatFailedOrMayHaveFailedIsInterruptedNotRestored` |
| A read fails while the app runs | A failure slip with retry; never zero or empty data | the owning feature tests (`ReciboViewModelTest` and others) |
| Preferences cannot be read | The last good value or the default; financial data untouched; a backup then has no preferences | [storage](../development/storage.md); `BackupSnapshotConsistencyTest` |
| The document provider fails while saving or reading | "No se pudo guardar" or "No pudimos abrir ese archivo"; nothing recorded or changed | `DocumentWriteContractTest`; `FileBoundaryTest`; `BackupFileJourneyTest` |

## Known gaps

- In the debug sandbox, a scenario's progress survives a reset of its data. Its
  steps from before then show "Sin escritura, como se esperaba" in Sello
  Herramientas, which is wrong wording for a step that was saved and then erased.
  Nothing is written back (see above); the label is a debug-tool issue, reported
  rather than fixed here.
