# CI performance plan

Reduce feedback time while preserving the checks required by
[ADR 0009](../decisions/0009-hosted-gate-scope.md) and the
[quality flow](quality-gates.md). This is unticketed work;
it does not create tickets or change their status.

## Baseline

The reviewed source revision is `7f94c75`. Historical hosted reports show:

- [Full candidate 39057602268](https://github.com/Okabe94/Sello/actions/runs/39057602268):
  13m47s job; 399s Gradle host and 303s Gradle device; 511 host and 528
  device tests. Host JUnit suites total about 5s, included in the host step.
- [Nightly 39271536509](https://github.com/Okabe94/Sello/actions/runs/39271536509):
  15m52s job; 515s Gradle host and 337s Gradle device. This older snapshot
  predates two device tests.
- [Host 39364095314](https://github.com/Okabe94/Sello/actions/runs/39364095314):
  6m19s job; 356s Gradle host.

These runs passed with 32–33 lint warnings. They are measurements of their
own revisions, not a controlled before/after benchmark or a promised saving.

## Options and order

1. **Cache Gradle downloads and build outputs — implemented; hosted reuse
   observed.** Restore the
   wrapper, dependencies, compiled build scripts and local task-output cache
   on hosted runners. Enable Gradle build caching, track files read directly
   by tests, and let only trusted `main` runs update shared entries. This targets
   the largest measured cost; initial hosted measurements are recorded below.
2. **Tune build parallelism and emulator provisioning — image-cache experiment
   implemented locally; hosted measurement pending.** Benchmark
   bounded worker/heap settings, build/emulator contention and clean API-30 AVD
   caching. Preserve all task selections and measure net wall time and cost.
3. **Split device work between two runners — merged in PR #69; hosted execution passed.** Candidate suite times
   suggest app alone (~135s) versus data + design-system + catalog (~124s).
   The ideal reduction is about two minutes of serial suite execution, before
   extra provisioning/build overhead. First require per-module reports and
   fail the required `quality` result on any failed, missing or cancelled group.
   Use separate emulators; app methods share database and process state.
4. **Reduce device harness repetition — pending.** Replace the 20–23s catalog
   traversal with all-example content verification and meaningful navigation
   coverage using less repeated scrolling. Replace the fixed two-second
   cancellation wait with deterministic synchronization. Retain real Room and
   the real six-second undo-expiry smoke test. Expected savings are seconds.
5. **Share one gate across multiple tickets — pending.** Prevalidate each
   ticket's prerequisites and required gate, execute the shared gate once on
   an immutable candidate, and bind each ticket's acceptance evidence to it.
   This helps multi-ticket candidates only; the baseline candidate has one.
   The CI part collector below shares candidate-wide execution after validating
   every selected ticket; optimizing the local batch command remains pending.
6. **Remove redundant tests — pending, maintenance priority.** The arithmetic
   template, duplicate catalog-list assertion, standalone floating-point
   fixture check and synthetic missing-binding check total about 0.14s.
   Review those methods individually; deleting them will barely affect CI time.

Do not drop transaction, migration, recovery, accessibility or Android integration
coverage to improve runtime. Keep the existing G1/G2 event policy, exact task lists,
evidence validation, dependency locks and API-30 device execution.

## Option 1 implementation

Owned scope: this plan, the Quality workflow, `gradle.properties`, data-module
test input declarations, hosted-workflow regression tests and the setup guide.
No changes to financial behavior, existing Gradle/JDK or module dependency pins,
gate selection or ticket status are planned. Findings outside this scope remain
follow-up work.

- [x] Add a commit-pinned `gradle/actions/setup-gradle` step before gate execution.
- [x] Restrict cache writes to `main`; pull requests and other refs read only.
- [x] Enable the local Gradle build cache alongside the existing configuration cache.
- [x] Declare the schema directory and scanned Kotlin sources as inputs to data unit
  tests; stale cached schema/source assertions must not pass.
- [x] Keep cache/setup reporting in the job summary and raw Gradle outcomes in
  retained gate logs. `FROM-CACHE`, `UP-TO-DATE` and executed are distinct.
- [x] Verify focused negative/configuration checks, cache reuse and invalidation.
- [x] Document the setup and actual focused verification. Initial hosted cache
  restore/save and timing observations are now recorded below.

### Tool choice

Use the MIT-licensed `gradle/actions/setup-gradle` **v5.0.2**, commit
`0723195856401067f7a2779048b490ace7a47d7c`, from the
[official release](https://github.com/gradle/actions/releases/tag/v5.0.2).
Its Node 24 runtime fits the existing GitHub-hosted workflow; keep the project's
Gradle 9.5.0 wrapper and JDK 25. The
[pinned action documentation](https://github.com/gradle/actions/blob/0723195856401067f7a2779048b490ace7a47d7c/docs/setup-gradle.md)
describes commit-scoped cache entries, read-only PR use, summaries and cache contents.
This version provides the required rolling Gradle User Home cache without adopting
v6's separate proprietary enhanced-cache provider or its simpler fixed-key basic
provider. Future action updates need their own compatibility/license review.

No encryption secret is introduced: project configuration-cache entries stay
within a job and are not uploaded. The action caches selected Gradle User Home
build state, not application databases, emulator state, signing keys or reports.
No Build Scan publication, dependency submission or PR comments are enabled.

### Verification and follow-up measurement

Focused verification used HEAD `7f94c75` plus the six files in the owned scope:

- `python3 -m unittest discover -s scripts/quality -p test_workflow.py`: three
  new checks first failed for absent cache setup/write policy/task caching, then
  all 11 workflow tests passed.
- A Gradle task-input probe first failed with `Missing direct test input:
  schemas/com.software.sello.data.local.SelloDatabase/1.json`; after the input
  declarations it passed for both schema and source paths. Probe setup errors
  before that assertion was reached are not the red result.
- `:data:cleanTestDebugUnitTest :data:testDebugUnitTest --info` restored the test
  task `FROM-CACHE`. Adding a synthetic schema JSON and, separately, a comment-only
  Kotlin source reran the test task for changes to `exportedSchemas` and
  `storageSources`. Both temporary fixtures were removed. The probes and logs
  are under ignored `build/reports/ci-performance/`.
- A directory-list probe exposed that tracking only JSON files missed an unexpected
  schema filename: the test task stayed `UP-TO-DATE`. Tracking the entire schema
  directory makes that change invalidate the task and reach the retention assertion.
- All 137 quality-tool tests, 32 board tests, board regeneration/check,
  architecture checks and `ktlintCheck` passed.

The final integrated verification command is
`ANDROID_SERIAL=emulator-5584 ./scripts/verify-ticket --merge-candidate HEAD --gate G2`
with the documented JDK/SDK environment, on a dedicated synthetic API-30 emulator
named `sello_cache_ci`. An initial run on the existing `emulator-5582` was stopped
after another checkout's simultaneous gate interfered with its activities; report
`20261020T041204Z-bd477b30` remains failed. The isolated run verifies the same full
gate without changing assertions or task selection.

The runner writes the tested dirty-file fingerprint,
host/device results and warnings to ignored `build/reports/quality/UNTICKETED/`;
the execution result is reported after the run. No ticket completion report or
hosted result is implied by this implementation checklist.

After an authorized merge populates the `main` cache, compare several equivalent
G1 and G2 hosted runs with the baseline. Record wall time, restore/save overhead,
cache outcomes, test counts, warnings and runner cost. A cold cache must still pass
the full gate. Do not claim a measured hosted speedup from local cache hits.

### Initial hosted results

PR #59 merged as `626a131`. Its first
[main G1 run](https://github.com/Okabe94/Sello/actions/runs/39374409507) started
without a cache, passed and saved the shared Gradle state.

The [cold candidate G2](https://github.com/Okabe94/Sello/actions/runs/39372893988)
and [warm nightly G2](https://github.com/Okabe94/Sello/actions/runs/39424800578)
have the same input fingerprint
`1cc09bf96d6a0b8dad4ffe4dd957eefaeeb389430bd273f5916b4d03dda92be9`:

- Job: 16m10s cold versus 9m12s warm, a 6m58s (43%) observed reduction.
- Host phase: 8m21s versus 48s; task-cache hits increased from 37 to 221.
- Device phase: 6m01s versus 6m10s; all 533 device tests executed freshly.
- Both passed with 522 host results, zero test failures/errors/skips and 33 lint
  warnings. Warm host-test results were restored from cache.

This is one matched pair, not a repeated benchmark. A later code-changing
[main G1 run](https://github.com/Okabe94/Sello/actions/runs/39431339845) restored
the cache, finished in 5m01s and restored 175 tasks while rebuilding affected UI
modules. Its changed inputs prevent a controlled timing comparison with the pair.

## Follow-up: retain device-build cache outputs

Owned scope: the Quality workflow, its regression checks, this plan and the setup
guide. Give host pushes and full-device runs distinct cache identities within the
existing single `quality` job. Preserve gate selection, task lists, fresh device
results, action pins and read-only PR access. No app code or ticket status changes
are included. Hosted cache publication and net runtime savings need separate
measurement after this configuration reaches `main`.

The pinned action's [key and restore documentation](https://github.com/gradle/actions/blob/0723195856401067f7a2779048b490ace7a47d7c/docs/setup-gradle.md#cache-keys)
and source confirm that workflow/matrix context is hashed into each key. The
nightly run above restored the exact key already saved by the host push and logged
`Cache hit occurred on the cache key ... not saving cache.` Its newly built
instrumented-test outputs therefore did not update that saved Gradle state.

The local implementation uses a singleton `cache-profile` matrix:

- `push` uses `host`; the workflow only accepts pushes to `main`.
- PR, scheduled and manual runs use `device`. Document-only PRs still run G1;
  the profile changes cache preference, not gate selection.
- The job ID/name stay `quality`. The name expression references matrix context
  to prevent GitHub's automatic profile suffix. Exactly one entry runs per event.
- Trusted `main` writes and read-only PR/non-main access stay unchanged. Default
  restore fallback can use the other profile or the old unprofiled entries.
- No internal action inputs, custom cache backend, dependency upgrades, secrets,
  or cached device-test results are introduced.

A PR prefers the device-profile entry even when a newer host-profile entry exists.
This can trade newer host outputs for reusable instrumented-build outputs; measure
net job time with real changes rather than assuming every PR becomes faster.
Repeated runs of the same profile/SHA still use immutable entries.

The focused regression check first fails because host/device runs share a key,
then passes with separate singleton profiles. Existing tests continue to check
event/gate selection, the one required job, cache write policy and evidence checks.
Run the integrated G2 gate on the final local inputs; its execution report and
result belong to that tested snapshot, not to a hosted cache-publication claim.

Hosted follow-up after merge:

1. Confirm the main push writes a host-profile entry, using existing cache fallback
   if necessary; retain its G1 results.
2. Run G2 on the same main SHA. Confirm it falls back to available state and saves
   a distinct device-profile key, including cacheable instrumented-build outputs.
3. Run G2 again at that SHA and verify device-build task cache hits increase while
   all device tests still execute freshly. Compare net time including restore/save.
4. Check a source-changing PR restores the device profile, reruns affected tasks
   and reports the unchanged required `quality` check. Retain warnings and failures.

### Device-cache follow-up observations

PR #62 merged as `28f1660`. Main G1 passed in 2m06s. Two manual G2
[attempts](https://github.com/Okabe94/Sello/actions/runs/39443100610) at that SHA
passed with identical input fingerprints, 522 host results, 544 freshly executed
device tests, zero failures/errors/skips and 33 lint warnings. Separate host/device
keys were saved, and the repeat restored the device key exactly.

Device-phase cache hits increased from 33 to 69, including instrumented-test Kotlin
compilation. Jobs took 8m10s and 8m51s; reported device suites took 262s and 302s.
The portion outside those suites fell from about 57s to 21s, including build,
installation and runner overhead. This pair proves cache reuse but does not
establish another overall time saving. Source-changing PR performance still needs
observation.

## Option 2: system-image provisioning experiment

Owned scope: the Quality workflow, hosted-workflow regression checks, setup guide
and this plan. Measure SDK provisioning and try caching only the vendor API-30
`google_apis/x86_64` system image. Preserve fresh AVD creation, cold boot, the full
G2 task list, fresh device results, gate selection and trusted-main cache writes.
No live AVD, snapshot, app data, permission, app dependency or ticket changes are
included. Keep emulator CPU/RAM and Gradle workers unchanged during this first
comparison; evaluate resource tuning separately after its hosted measurements.

The two full-run logs above show 86–87s from emulator-action startup to the gate:
about 52–58s installing/checking SDK packages, including 27–34s for the system
image; about 25–32s creating and booting the AVD; and remaining animation/setup
work. Image download is the first isolated target.

### Implementation and tool choice

- Restore only `$ANDROID_HOME/system-images/android-30/google_apis/x86_64` for G2.
  SDK installation/update checks remain in the existing emulator action. A cache
  miss takes the existing download path.
- Save only after successful G2/evidence validation on trusted `main`. Use OS,
  host architecture, API/target/image architecture and the installed
  `source.properties` SHA-256 in the saved key. Prefix restores find the latest
  saved vendor revision; compare matched versus installed keys to avoid rewriting
  an unchanged image. A newly published SDK revision can still be installed and
  saved under a new key.
- Force fresh AVD creation and cold boot explicitly, using the action's existing
  two-core/default-RAM settings. No AVD, VM snapshot, ADB key, app data, result file,
  Gradle output or signing material is included in the SDK cache.
- Use the official MIT-licensed `actions/cache/restore` and `actions/cache/save`
  **v5.1.0**, commit `caa296126883cff596d87d8935842f9db880ef25`, from its
  [official release](https://github.com/actions/cache/releases/tag/v5.1.0).
  Its Node 24 runtime requires runner 2.327.1 or newer; the measured hosted runner
  was 2.337.0. The v5 interface matches the pinned emulator action's caching
  guidance. No app/Gradle/JDK/emulator version or permission change is introduced.

The focused configuration checks first fail for missing SDK restore/publication
and explicit fresh-AVD guards, then pass after implementation. The pre-existing
Gradle guard now permits the separate SDK-only restore/save actions while still
rejecting another combined cache action or a second Gradle cache path.

### Verification and hosted comparison

Local probes used the vendor API-30 image revision 16 on a 16-logical-CPU host:

- A clean SDK install took 40.031s.
- The 3,442,815,438-byte image directory compressed to 1,209,207,005 bytes
  (about 1.13 GiB) with tar/zstd in 4.134s.
- Three archive restores took 2.778s, 2.749s and 2.703s. Every restored file
  matched the vendor-installed source by SHA-256.
- The normal `sdkmanager --install` update check on the restored image took
  11.642s. A warm setup still includes that check and GitHub cache transfer time.
- Alternating fresh AVD probes booted the vendor image in 19.475s/18.842s and
  the restored image in 18.777s/18.885s. Each reported API 30, two cores and no
  preinstalled Sello packages. No boot-time improvement is inferred.

The workflow regression check also executes the actual key-generation script
against synthetic revision metadata: unchanged metadata reuses the key and a
revision change produces a new key. Probe data/logs are under ignored
`build/reports/ci-performance/system-image/`. Run the integrated G2 gate on the
final local inputs with an isolated fresh AVD using the restored image. Its
execution report identifies the exact tested snapshot. Local archive timing
excludes GitHub transfer time and cannot establish hosted savings.

Local validation at HEAD `515cc22` plus the four owned files passed all 141
quality-tool tests, 32 board tests, architecture checks, actionlint v1.7.7 and the
host gate (522 results, no failures/errors/skips, 32 lint warnings). Full G2 run
`20261020T160148Z-47277918` executed 544 device tests with one failure:
`DataStoreUserPreferencesPersistenceTest.preferencesSurviveReopeningTheActualFile`
reported two active DataStores for the same file. The test cancels its first scope
without awaiting completion before reopening the file. It remains unchanged.

Focused diagnostics passed 10/10 executions on the restored image, then reproduced
the same overlap error in 8/100 executions on the original vendor image. This
confirms a pre-existing test-lifecycle race independent of caching; the first
failed gate remains retained under ignored `build/reports/quality/UNTICKETED/`.
Diagnostic logs are alongside the image probes. A final G2 rerun uses the restored
image after wiping only the dedicated synthetic AVD, with no test/assertion or
gate changes. Its result is reported separately; a passing rerun does not resolve
the intermittent test defect. Fixing scope cancellation/join belongs in a focused
test-maintenance change.

After an authorized PR/merge, compare several cold/miss and warm full runs with the
same app/test code and gate. Retain image metadata, runner/emulator versions,
archive bytes, cache restore/save time, SDK/package setup, boot time, complete job
time, test counts/freshness and warnings. Resource tuning remains a separate
experiment; do not change CPU, RAM and Gradle workers together with image caching.

## Option 3: parallel device execution

Owned scope: the Quality workflow, quality runner/CLI and regression checks,
this plan, the setup guide and quality-flow documentation. Keep the local full
G2 command and all host/device tasks. CI runs host checks, app device tests and
data/design-system/catalog device tests as separate parts; each device group
gets a fresh API-30 emulator. A final required `quality` job must reject failed,
cancelled, missing, stale or mismatched parts and missing module results before
writing full gate reports. Partial execution is not completion evidence.

Use the existing event policy and prerequisite checks. Keep trusted-main cache
writes, read-only PR caches, dependency/toolchain pins and all assertions. Record
per-module results and bind parts to the same commit, input fingerprint, event,
base and workflow run/attempt. No app/test behavior, ticket status, permissions,
financial storage or unrelated DataStore test fixes are in scope.

Each CI part runs once for the immutable candidate after all selected tickets'
prerequisites and gate requirements are validated. Collection binds that complete
execution to each selected ticket's acceptance fingerprint. Local ticket and batch
commands retain their existing behavior. This is a consequence of candidate-wide
CI execution; it does not complete the separate local batch optimization.

Execution decision: each successful trusted-main part publishes its own Gradle
cache. Only the support device group may publish the vendor SDK image, after
its device checks and completion-evidence validation. This moves cache publication
from whole-job success to part success; it does not change gate success. Caches
remain reusable inputs, and the required collector still needs every part. PRs
and non-main refs cannot publish either cache.

Use official MIT-licensed `actions/download-artifact` v8.0.2, commit
`9000827ccba6bdab643e8b6fd33ac0654aef8333`, to retrieve parts from the current
workflow run with separate artifact directories and digest errors enabled. Its
Node 24 runtime fits the existing hosted runner/actions and supports the pinned
upload-artifact v7.0.2 format. Same-run downloads need no additional token or
permission. No dependency, JDK, SDK or emulator upgrade is included.

The last matched full runs at `61b7792` passed 526 host results and 564 fresh
device tests with 33 lint warnings. Warm jobs took 9m08s/8m24s, with device phases
of 330s/335s. SDK-image reuse saved 5.9–10.9s in image setup including restore,
but total provisioning was 99.1s/80.3s versus an 80.4s seed. These observations
do not establish an overall provisioning gain; larger whole-job reductions also
included greater Gradle reuse. Raw evidence is under ignored
`build/reports/ci-performance/system-image/hosted/` and
[the hosted attempts](https://github.com/Okabe94/Sello/actions/runs/39499071614).

Verification must first prove rejection of incomplete execution, then exercise
both real device groups and collect their fresh results with the host part.
Hosted performance remains unmeasured until the new topology runs on GitHub;
compare complete workflow wall time and runner minutes, including the planning,
artifact-transfer and final validation jobs. The 1–2 minute saving is an estimate.

Focused verification first showed that the old G2 runner passed when one module
had no fresh results, and that the workflow had no independent device groups.
Both guards pass after implementation. A transport probe also exposed the pinned
download action's single-artifact flattening: G1 collection now accepts that
host-only layout, while G2 still requires all named parts. Negative coverage
includes failed/cancelled/skipped/missing jobs, omitted XML or freshness checks,
wrong revisions/attempts, changed commands, malformed metadata and incomplete
module partitions. Full local execution is reported separately so its final
input fingerprint remains exact; it does not establish hosted runtime savings.

## Follow-up: change-aware gate scope (ADR 0014)

Owned scope and evidence contract are recorded in
[ADR 0014](../decisions/0014-change-aware-quality-gates.md). G0 avoids Android work
for documentation advancing no ticket; G2 selects affected module consumers while
keeping the complete host gate. Local ticket commands stay full unless --affected
BASE is requested; --gate G2 and hosted nightly/manual runs force full devices.
Schema-v2 reports distinguish the scope; old v1 evidence remains readable.

Documentation pushes also use G0. Code pushes keep G1 because trusted main builds
publish the cache outputs read-only PRs need. We did not eliminate code-merge G1 or
introduce PR-cache promotion. Expect savings mainly on documentation/narrow module
changes and runner usage, not a measured speedup on every app PR. Existing
longitudinal measurements remain under ignored build/reports/ci-performance/.

## Follow-up: the app suite in two shards (2026-10-26)

Measured on the successful nightlies: device test time grew from 279 s (10-20) to
806 s (10-26), app alone from 154 s to 602 s, while the median app test stayed at
about 0.5 s. The app device job was the pull request's critical path at about 14
minutes against 7 for the support group, of which roughly 4 minutes are fixed:
emulator boot (32 s), Gradle building and installing the app and test APKs, and
collecting results. Host unit tests are not a factor (1,294 tests in about 11 s).

Two changes, each in its own pull request: the "every theme, size and window" sweep
tests now render each scale × window once and each other theme once (locally the
seven sweeps went from 54 s to 21 s on the same emulator), and the app suite runs as
two shards (`app-1`, `app-2`) with the runner's `numShards`/`shardIndex`, each shard a
part of its own. The shards balance by test count, not time, and each still pays the
fixed minutes; building once and installing the APKs in every shard is the next step
if those minutes matter after this measurement. The hosted `quality` check on the
sharding pull request and the first nightly after it are the measurement.
