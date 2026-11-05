# SELLO-E30 — Release candidate

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** The person gets a release candidate where every form recovers from a failed read, the screens match the chosen theme, restore and erase say what they really do, and the build has been walked on the newest Android.
- **Exit:** SELLO-170 to SELLO-175 are Done: every before-release finding of the 2026-11-04 audit is fixed with a red-first test, the candidate's records are current, and the shrunk release build has been walked on a new API 37 emulator, leaving only the owner's phone walk.

## Decisions

**Owner direction (2026-11-04):** given in chat on the orchestrator's audit plan.

- No new features. E30 takes only the audit's before-release items; the after-release
  ones are not part of it.
- No version bump in this epic: the candidate stays `0.1.0` (versionCode 1) until the
  owner says otherwise.
- The third-party licence notice is out of scope.
- SELLO-175 may use the network to run the advisory check again. An advisory that
  needs a dependency change is reported to the owner, not changed.
- The owner walks the candidate on their own phone. Agents never use the phone.
  SELLO-175 leaves that walk as the owner's step in the candidate checklist.
- Orchestrated run. Subagents and separate review agents are approved for this epic,
  and the owner delegated every merge except a release.

**Orchestrator's calls (2026-11-04), open to the owner:**

- SELLO-170: Anotar varios and the schedule editor keep saving on the last good figures,
  as the expense form does since SELLO-151. Storage still refuses a stale version.
- SELLO-171: before Compose draws its first frame, the window follows the phone's
  theme, because the in-app choice can't be read yet.
- SELLO-171: the status bar follows v2: white over the band in both themes, Sello's theme
  over the desk on entry screens; the ticket's first criteria assumed paper under it.
- SELLO-172: the start-up failure screen does not import FinanceTracker files, since
  that would be a new capability. It says what the file is and where to import it.
- SELLO-173: sorting by name folds Ñ to N, as search does since SELLO-165, instead of
  placing Ñ after N as a dictionary does. It needs no new stored key.
- File and line references in the tickets are from the audit commit `c87b351d`; later
  commits may have moved them.
- SELLO-175 (2026-11-05): the release gate's production step accepts only the owner's
  pinned certificate (`release.py:139-142`), so with a throwaway key `scripts/check
  --release` passes up to that step, not whole, as the ticket's first criterion assumed.
  The owner's G3 run with the real key joins the phone walk as the owner's step; the
  Exit's "only the owner's phone walk" now reads "the owner's G3 run and phone walk".
