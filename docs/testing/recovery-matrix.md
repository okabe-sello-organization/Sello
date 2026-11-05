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
| Database schema 1 | 9 (current) | Upgraded through every version on open; every row and identity kept | `ExportedSchemaOnDeviceTest.aVersionOneDatabaseOpenedByThisBuildIsUpgradedThroughEveryVersionAndReadsTheSameData`; `RecoveryRetentionTest` (a full v1 history) |
| Schema 1 → 2 → 3 → 4 → 5 → 6 | — | Each step keeps every row and adds only its empty tables or columns | `ExportedSchemaOnDeviceTest`, one case per step |
| Schema 6 → 7 | — | Rows kept; empty carry chains, every category off | `CarryoverMigrationTest` |
| Schema 7 → 8 | — | Rows kept; Recibo's chosen goal becomes its one star, unless completed | `GoalStarMigrationTest` |
| Schema 8 → 9 | — | Rows kept; each expense gains its note's search key, indexes added | `SearchIndexMigrationTest` |
| Database schema 9 | 9 | Opens and keeps its rows | `ExportedSchemaOnDeviceTest.aDatabaseBuiltFromTheExportedCurrentSchemaOpensAndKeepsItsRows` |
| A newer schema than 9 | — | Meant to be refused with the start-up failure screen, the file kept whole. **No current proof:** the test stamps version 2, newer only when schema 1 was current ([#364](https://github.com/okabe-sello-organization/Sello/issues/364)) | `UnreadableFileSetAsideTest.aFileFromANewerVersionIsStillWholeAndReadableWhereItWasKept` (stale) |
| Backup format 1 | schema 9 | Read with no goals, schedules, investments or chains | `GoalBackupTest`, `ScheduleBackupTest`, `InvestmentBackupTest`, `CarryBackupTest` (JVM) |
| Backup format 2 | schema 9 | Read with its goals; the chosen goal is its one star | `GoalBackupTest`, `StarredGoalBackupTest` (JVM) |
| Backup format 3 | schema 9 | Read with its schedules and origins; no investments. A FinanceTracker file is translated to it | `ScheduleBackupTest`, `InvestmentBackupTest` (JVM); `LegacyImportRestoreTest` (restored on device) |
| Backup format 4 | schema 9 | Read with its investments and rates, in pesos, largest value first | `BackupRoundTripTest`, `InvestmentBackupTest` (JVM) |
| Backup format 5 | schema 9 | Read with its investment settings; every category off, no starting deficit | `BackupRoundTripTest`, `CarryBackupTest` (JVM); `CarryBackupRestoreTest.sc42AVersionFiveFileRestoresWithEveryCategoryOffOverAnEnabledOne` (device) |
| Backup format 6 | schema 9 | Read with its carry chains and financial month; the chosen goal is its one star | `BackupRoundTripTest`, `CarryBackupTest`, `StarredGoalBackupTest` (JVM); `GoalBackupRestoreTest.everyStarComesBackAndReplacesTheStarsThereNeverMerges` (device) |
| Backup format 7 (current) | schema 9 | Written and read byte for byte; restored with the same figures on a fresh installation | `BackupRoundTripTest` (JVM); `RecoveryRetentionTest`, `GoalBackupRestoreTest`, `InvestmentBackupRestoreTest`, `CarryBackupRestoreTest` (device) |
| Erase all with goals | schema 9 | Every goal, its history, the undo copy and the stars removed | `GoalBackupRestoreTest.erasingEverythingRemovesEveryGoalItsHistoryAndTheStars` |
| A newer backup format | — | Refused as "Lo hizo una versión más nueva de Sello"; nothing changes | `RestoreCommandContractTest.aFileThatCannotBeRestoredChangesNothingAndIsNotKept` (version 8) |

Every format is read into the same plan before anything is written, so one restore path
serves them all ([backup format](../data/backup-format.md#reading-a-file)).

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

`HistoryLoadTest.everyHistorySavedAsABackupAndRestoredKeepsItsRows` (JVM, real Room):
each of the five sandbox histories, with categories, limits, expenses, incomes,
schedules and their pauses, goals and investments in pesos and dollars, is loaded to
its day, backed up, and restored into a new installation at the same simulated day.
The restored rows equal the originals for all five.

## Restore

| Failure point | Outcome | Proof |
| --- | --- | --- |
| Not a backup, cut short, damaged, too large, invalid amount, newer format | Rejected by kind; financial rows, generation, preferences and staging unchanged | `RestoreCommandContractTest.aFileThatCannotBeRestoredChangesNothingAndIsNotKept`; `RestoreViewModelTest` |
| Preview cancelled | Nothing changes; the staged file is forgotten | `aCanceledPreviewChangesNothingAndForgetsTheFile` |
| Process ends after staging | The staged file is offered again under its own operation | `aStagedFileSurvivesARestartWithItsOwnOperation` |
| Staged file a day old, or the generation moved | Forgotten, never reinterpreted | `anUnconfirmedFileExpiresAfterADay` |
| Staged file a day old at the next start, Respaldo never opened | Deleted without being read; one confirmed is said not restored at the first look, unless that look finds something else or a file was chosen since. Newer, unknown, committed or beside pending preferences: kept for a look | `aPreviewADayOldIsForgottenAtStartWithoutBeingLookedAt`, `aPreviewNotADayOldIsKeptAtStart`, `aConfirmedFileADayOldIsForgottenAtStartAndSaidConfirmed`, `aConfirmedFileADayOldIsKeptAtStartWhileItsReceiptCannotBeRead`, `aCommittedFileADayOldIsNotExpiredAtStartButFinishedByTheLook`, `aMovedReplacementWithPreferencesPendingIsLeftAloneAtStart`, `aFileNeverReplacedBesideAReplacementWithPreferencesPendingIsKeptAtStart`; `RestoreViewModelTest` |
| The same, beside a manifest that cannot be read | Kept at start, whatever its age, until Respaldo settles it | `aFileBesideAnUnreadableManifestIsKeptAtStartWhateverItsAge` |
| History changed after the preview | Refused as stale; counted again | `aChangeAfterThePreviewRefusesTheReplacementUntilPreviewedAgain` |
| Failure inside the transaction | Everything rolled back | `aFailureInsideTheReplacementRollsEverythingBack` |
| Caller cancelled around the commit | The receipt shows it committed; asking again replays it | `aCallerCancelledAroundTheCommitStillFindsTheReplacementDone` |
| Crash between the commit and the file rename | Replayed by receipt, not replaced twice | `askingAgainAfterACrashBeforeTheFileMovedReplaysTheSameReplacement` |
| Preferences fail after the commit | "Historial financiero restaurado" with only those preferences; retried without a second replacement, also after a restart | `preferencesThatFailAreNamedAndRetriedWithoutASecondReplacement` |
| Another file while preferences are pending | Refused until they are finished | `aPendingReplacementBlocksStagingAnotherFile` |
| The file cannot move after the commit | Every preference it brings is named pending and none applied; the next look moves it, then applies them | `aFileThatCannotMoveAfterTheCommitIsNeverReportedComplete` |
| A restore file cannot be read | Before the commit: refused, nothing written. Its `applied` list: every group stays pending, none applied twice. Never a crash | `aStagedFileThatCannotBeReadFailsTheReplacementInsteadOfCrashing`, `anAppliedListThatCannotBeReadAppliesNothingAgainAndStaysPending` |
| `restored.json` cannot be read (I/O error) | Unknown, never nothing: nothing applied, files kept, until it reads | `aRestoredFileThatCannotBeReadIsUnknownNeverNothing` |
| `restored.json` reads but is damaged | Cleared with its `applied` list and manifest; said once that its preferences could not be applied; backups and restores go on | `aDamagedRestoredFileIsClearedSaidOnceAndBackupsGoOn`, `RestoreViewModelTest.preferencesThatCouldNotBeReadAreSaidOnceAndThenBackupsAreBack` |
| The manifest after the move cannot be read | Not needed: the receipt at the history's generation says whose `restored.json` is | `aManifestThatCannotBeReadAfterTheMoveDoesNotHoldTheRestoreUp` |
| A later restore committed with the clock set back | Still the owner: found by generation, not by time | `aLaterRestoreCommittedWhileTheClockWasBackStillAppliesItsPreferences` |
| `restored.json` damaged beside a staged file | Cleared and said once; the staged file and its manifest stay | `aDamagedRestoredFileBesideAStagedOneIsStillClearedAndSaidOnce` |
| A staged manifest cut short, naming no operation, or with a malformed `\u` escape | Its replacement committed (by operation, or by the file's digest against the receipt with nothing written since): moved and finished. Never committed, or the same bytes staged again after changes: cleared. The file damaged too beside a current restore: said once. Another file chosen beside one with the escape: refused. Never a crash | `aManifestCutShortWhoseReplacementCommittedIsFinishedNotLeftUnknown`, `aManifestNamingNoOperationBesideAReplacedFileIsFinishedByItsReceipt`, `aManifestNamingNoOperationBesideAFileNeverReplacedIsCleared`, `theSameFileStagedAgainAfterLaterChangesIsNeverTakenForTheEarlierRestore`, `aCommittedRestoresStagedFileDamagedWithItsManifestIsSaidOnceNotForgotten`, `aManifestWithAMalformedEscapeBesideAReplacedFileIsFinishedByItsReceipt`, `aManifestWithAMalformedEscapeBesideAFileNeverReplacedIsCleared`, `aFileChosenBesideAManifestWithAMalformedEscapeIsRefused` |
| A confirmation that cannot be recorded in a manifest with a malformed `\u` escape | Left unrecorded; the replacement still goes ahead and commits under its operation | `aConfirmationBesideAManifestWithAMalformedEscapeStillReplaces` |
| Confirmed, staged in this process, after a restart or from the start-up screen | The file is parsed once, on the computation dispatcher, never on the caller | `aRestoreConfirmedAfterItsStageParsesTheFileNoMore`, `aRestoreAfterARestartParsesItsFileOnceAndNotOnTheCaller`, `aFileTheStartUpCheckReadIsStagedAndRestoredWithoutParsingAgain`, `StartupTest` |
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
| Preferences fail after the commit, then a restore | Refused until the erase's preferences reset; then the backup's land on first-run values and stay at every start; a new erase is accepted | `EraseThenRestoreTest.anEraseWhosePreferencesFailedNeverUndoesTheRestoreMadeAfterIt` |
| The same, with a backup that holds no preferences | First-run values, nothing of the erased history's | `aBackupWithoutPreferencesStillLeavesNoneOfTheErasedHistorysBehind` |
| The same, with the restore's preferences pending or its move cut short | `restored.json` or the staged file is kept and applied at the next start | `aRestoresPendingFileOutlivesAnEraseThatCouldNotFinishBeforeIt`, `aRestoreCutShortAfterItsCommitIsFinishedAfterAnEraseThatCouldNotFinish` |
| The kept copy could not go, then a restore | Deleted at the next start all the same | `theCopyKeptAsideGoesEvenAfterARestoreReplacedTheErasedHistory` |
| An older restore's preferences pending, and the erase cannot empty its folder | Forgotten, never applied over the erase | `anOlderRestoresPendingPreferencesAreNeverAppliedAfterAnErase` |
| The same, with the manifest gone and `restored.json` left | Forgotten: no restore made the history now | `aRestoredFileLeftByAnErasedHistoryWithoutItsManifestIsNeverApplied` |
| A restore committed without resetting the erase's preferences first (an older build) | The erase only marks them done; the restored ones stay | `aReplacedEraseOnlyMarksItsPreferencesDoneAndNeverWipesTheRestoredOnes` |
| The erase's marker is not a file, or has a malformed escape | No erase owed: removed or read as damaged; restores and erases go on | `somethingThatIsNotAFileInTheMarkersPlaceNeverHoldsARestoreUp`, `aMarkerWithAMalformedEscapeIsReadAsDamagedNeverACrash` |
| `compact.pending` cannot be written | Compacted all the same; the erase's file stays to look at again | `anEraseIsCompactedEvenWhenWhatItOwesCannotBeWrittenDown` |
| Compaction's checkpoint kept busy, or refused by any database error | Not compacted: `compact.pending` stays and it is retried at the next start; restores and new erases go on | `aCompactionKeptBusyStaysOwedAndIsDoneLaterWithoutBlockingAnything`, `aCompactionRefusedAboveSQLiteStaysOwedAndIsDoneLater` |
| What survives | Profile and one `history.reset` receipt; no erased name or note in the file's bytes | `nothingLeftInTheFilesHoldsAnOldNameOrNote` |
| After the erase | A new category and expense from empty; a backup restores across it | `theNewHistoryCanBeUsedFromEmpty`; `RecoveryRetentionTest` |

## Old work across a new generation

Restore and reset each move the generation, so anything prepared against the old one
is refused, even when restored record identifiers match earlier ones.

| Attempt | Outcome | Proof |
| --- | --- | --- |
| Draft saved after a restore or reset | `StaleGeneration` | `oldDraftsAndUndoCannotCrossTheReplacement`, `oldDraftsAndUndoCannotCrossTheErase` |
| Undo of a deletion from before | `StaleGeneration`; the kept copy is gone | the same tests |
| Sandbox history mid-load when a reset happens | Its next step carries the old generation and is refused | `aCommandPreparedAgainstAReplacedHistoryIsRejected` in the `:data` command contract tests |
| The same history in a new process after the reset | Finds its first step's receipt gone, does not carry on, writes nothing | `ScenarioRunnerTest.aLoadIsNotCarriedOnIntoAHistoryTheAppsOwnResetErased` |

## Corrupt or unavailable storage

| Situation | What happens | Proof |
| --- | --- | --- |
| The database cannot be opened (damaged, newer, unreadable) | The start-up failure screen; it is never shown as an empty ledger. Intentar de nuevo, Restaurar un respaldo or Empezar de cero | `StartupTest`; `StartupFailureScreenTest` (the screen, on the JVM) |
| Empezar de cero | The file is kept whole as `sello-unreadable.db`; the next open is empty | `UnreadableFileSetAsideTest.aDamagedFileIsKeptByteForByteAndTheNextOpenIsANewEmptyHistory` |
| Restore at start-up | Checked before anything moves; set aside, new store filled from the backup | `RestoreAfterSetAsideTest`; `StartupTest` |
| A FinanceTracker file chosen at start-up | Not imported; named, and pointed to Respaldo after Empezar de cero | `StartupCheckTest`; `StartupFailureScreenTest` |
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
