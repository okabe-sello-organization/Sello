# SELLO-077 — Make the sandbox scenario replay tests reliable

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-004
- **Gate:** G2

### Outcome
`TemporalScenarioCorpusTest` and `ScenarioReplayTest` pass on every run, so a hosted
check fails only for a real defect.

### Deliverables
The cause of the intermittent failures found and fixed, in the test or in the sandbox
runner, with evidence that it no longer recurs.

### Acceptance criteria
- The cause is identified and stated; the fix addresses it rather than retrying.
- Each test passes 20 runs in a row on the API-30 emulator.
- No financial behaviour changes; the sandbox stays absent from release builds.

### Tests
The two tests repeated; a regression test for the cause if it lies in the runner; G2.

### Working checkpoint
Twenty consecutive passes of both tests, recorded.

### Context and starting points
Seen twice in hosted CI on 2026-10-23, each passing on a full rerun:
`ScenarioReplayTest.aRunInterruptedAfterACommitAndResumedMatchesAFreshRunExactly`
(PR #86) expected "Ya estaba guardado" and got "Guardado";
`TemporalScenarioCorpusTest.everyScenarioMeetsItsFiguresAndResumesToTheSameHistory`
(PR #91) expected the first check's figures and got "Comprobado antes". In both, a
resumed scenario reads as if it had or had not already run. A likely cause is the
runner from before the simulated restart still answering; see `ScenarioRunner`.

### Implementation plan
1. Reproduce by repeating each test; capture the runner's state at the failure.
2. Fix the cause; repeat 20 times.

### Concrete cases and pitfalls
- Do not add sleeps or retries; the rules forbid arbitrary waits.
- A full workflow rerun, not a failed-job rerun, is what currently passes CI.

### Verification recipe
Repeated focused runs, then `./scripts/verify-ticket SELLO-077 --retain`.

### Execution progress

- **Cause, 2026-10-23:** not the runner from before the restart. A new process's
  `ScenarioRunner.recover()` runs in the background and, once it held the step lock,
  replaced the whole log with the steps confirmed so far. A step taken before it ran
  was then relisted from its receipt: "Guardado" instead of "Ya estaba guardado", and
  a check as "Comprobado antes" instead of its figures, exactly the two CI failures.
  Which came first depended on thread timing. The same could happen in the debug tools
  when Paso is tapped right after a restart; no record or figure was ever affected.
- **Fix:** `recover()` lists only the steps confirmed by earlier processes (the stored
  progress the runner was built on) and puts them ahead of the steps this process has
  already logged, whose results it keeps; running it twice lists them once. No test
  waits, retries or sleeps were added.
