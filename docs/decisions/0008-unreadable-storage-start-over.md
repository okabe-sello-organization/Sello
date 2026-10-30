# 0008 — Starting over when storage cannot be opened

**Owner:** project owner (user) · **Updated:** 2026-10-16
**Status:** approved recovery rules; retry, start-over and restore are implemented.

Show a recovery screen when storage cannot be opened. Say that nothing was deleted
and offer **Intentar de nuevo**, **Restaurar un respaldo** and **Empezar de cero**. Build the financial graph
only after storage opens; this screen must work without it.

Use one general storage-failure message. The current failure cannot distinguish a
damaged file from one written by a newer app version. Specific diagnoses need typed
failures and a separate decision. Do not disguise unrelated errors as storage failures.

## Starting over

Require two confirmations and typed **BORRAR**. Show no record counts because the
data cannot be read. Move the unreadable database and its companion files to
`sello-unreadable.db` in the same private folder, preserving the data instead of
deleting it. Keep one copy; a later start-over replaces that copy.

The next open creates an empty history with the device financial zone. Start a new
Android task so old selections, drafts and operation IDs cannot return. Refuse
retry and moving files once storage has opened; never open a second store beside it.

Startup remains synchronous. Revisit that choice using measurements when startup
or migration performance warrants a change.

## Remaining recovery work

The kept copy is private plaintext financial data with no recovery screen yet.
SELLO-030 must make backup restore reachable from this screen. Decided with the
owner on 2026-10-22 (SELLO-030): the kept copy is never recovered by Sello; a restore
from this screen sets the unreadable file aside, as starting over does, and fills the
new store from the backup. Reset erases it (SELLO-031); SELLO-032 checks it is
never shared or backed up ([MVP data handling](../privacy/mvp-data-handling.md)).

Implemented in SELLO-030: the chosen file is read and checked whole before anything
moves, and its preview shows only the backup's date and counts, with an explicit
confirmation and no typed word, as restoring inside the app has (the word is D09's
for replacing data with nothing). Confirming sets the database aside, which also
clears any restore staged for it, opens the new store without showing it, and
restores into it under a new operation; the app then starts again in a new task and
process. A failure after the set-aside says the restore did not finish; on restart
the staged file or receipt shows what happened, and Respaldo can restore again. See the
[recovery epic](../tickets/E07-recovery-privacy/README.md).

## Verification

Host: `SetAsideTest`, `StartupTest`, `StartupFailureViewModelTest`, `StartupFailureScreenTest`.
Device: `UnreadableFileSetAsideTest`, `RestoreAfterSetAsideTest`.
