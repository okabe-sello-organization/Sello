# Quality gates

The pull request's required `quality` check is the only proof that checks passed
([ADR 0021](../decisions/0021-ci-is-the-only-proof.md)). Nothing is retained in the
repository: no reports, fingerprints or "Quality run" lines. While working, run
focused checks with `scripts/check`; let the pull request run the gate.

## Gates

| Gate | Runs |
| --- | --- |
| G0 | The fast checks, Python and Git only: `tickets` (`tickets.py check`), `quality-tests`, `performance-tests`, `architecture`, `links` and `licences` (the release library list). |
| G1 | G0, then ktlint, then every host test, the screenshot baselines, lint and the debug and release assemblies. |
| G2 | G1 plus the device suites the change can affect, on API-30 emulators. |
| G3 | G1, every device suite, then the release checks of [ADR 0016](../decisions/0016-release-gate-signing-split.md). |

The Gradle part of G1, kept identical to `scripts/quality/gates.py` by a test:

```bash
./gradlew ktlintCheck --continue
./gradlew :domain:test :data:testDebugUnitTest :design-system:testDebugUnitTest :app:testDebugUnitTest \
  :app:verifyRoborazziDebug :design-system:verifyRoborazziDebug lintDebug :app:assembleDebug \
  :app:assembleRelease --continue
```

A ticket's `Gate` field is advisory: what the ticket expects to need. Nothing enforces
it; CI selects the gate from the changed paths alone.

## JVM screen and screenshot tests

Which layer tests what ([ADR 0022](../decisions/0022-jvm-screen-and-screenshot-tests.md)):

| Layer | Tests | Where |
| --- | --- | --- |
| JVM | Domain rules, view models, and screens rendered from state given by hand: what each state shows, what each control reports, layout geometry, font scale | `<module>/src/test/`, Robolectric |
| JVM screenshots | What a screen looks like, per ink, light or dark and font scale; what each group of components looks like | `app/src/test/` and `design-system/src/test/`, Roborazzi; baselines in `<module>/src/test/screenshots/` |
| Device | Storage on real Room, platform contracts (system keyboard, insets, backup, launcher) and user journeys through the real app | `<module>/src/androidTest*/` |

A JVM screen test runs with `@RunWith(AndroidJUnit4::class)` and `createComposeRule()`
and shows its screen with `showSello(Look(...))` from `app/src/test/.../JvmScreen.kt`.
Robolectric gives it API 37 and the hosted emulator's 320x640 dp screen
(`app/src/test/resources/robolectric.properties`), a plain `Application` and no network:
the Android framework jar is a locked Gradle dependency read in offline mode.

The host gate runs `:app:verifyRoborazziDebug` and `:design-system:verifyRoborazziDebug`:
every capture must match its committed baseline, and a capture without one fails. A
visual change re-records the baselines in the same pull request, and the image diff in
the pull request is how the change is reviewed:

```bash
./gradlew :app:recordRoborazziDebug --no-build-cache --rerun-tasks  # write the baselines
./gradlew :app:verifyRoborazziDebug     # compare, as the host gate does
```

The same tasks exist for `:design-system`. Record without the build cache: it can
replay an earlier run's images instead of drawing them again.

A screenshot is drawn by the test itself, never copied from the window by Roborazzi:
the window copy came back blank now and then, and a blank baseline then passed its own
check. App tests call `rule.captureStill(path)` (`app/src/test/.../StillCapture.kt`),
which draws the screen and any sheet over it; design-system tests call
`drawnBitmap().captureBaseline(path)` (`PixelCapture.kt`). Both fail the test, before
anything is recorded or compared, when the drawing has fewer than 32 distinct colours.
Baselines are drawn at one pixel per dp (mdpi).

Record on Linux, as CI runs: font rasterizing may differ on another system. A plain
`:app:testDebugUnitTest` (and `scripts/check app`) runs the screenshot tests without
comparing them. A failed comparison leaves the expected, actual and diff images under
`app/build/outputs/roborazzi/` and a report under `app/build/reports/roborazzi/`; the
host job keeps both in its artifact.

## What CI runs

The Quality workflow (`.github/workflows/quality.yml`) has four jobs:

1. **Plan and fast checks.** `scripts/check --plan BASE --event EVENT` picks the gate
   and device groups and writes them to the job summary; then `scripts/check --fast`
   runs G0. Every other job needs this one, so a broken ticket, link, architecture
   rule or tool test fails in about a minute, before Gradle or an emulator starts.
2. **Host checks** (when the plan selects G1 or more): `scripts/check --host`, which
   repeats the fast checks, runs ktlint, then the Gradle host gate. G3 adds
   `--ci-release`: the licence list and a release APK signed with a throwaway key.
3. **Device tests** (one job per selected group: `app-1`, `app-2` and `support`): first
   ktlint and the test APKs, then a fresh, cold-booted API-30 emulator runs the
   group's selected suites. The app suite runs as two shards. A selected suite that
   leaves no fresh JUnit results, or skips a test, fails its job.
4. **`quality`**, the required check: it judges each job's result against the plan.
   A selected job must succeed and an unselected one must be skipped.

| Event | Gate |
| --- | --- |
| Pull request, documents only | G0 |
| Pull request, Python tooling or unit tests only | G1 |
| Pull request, anything else | G2 with the device groups below |
| Push to `main` | G0 for documents only, otherwise G1 (publishes the Gradle caches) |
| Nightly, 07:23 UTC | G3 on `main`, skipped when `main` has not moved since the last successful nightly |
| Manual run | G3 on the chosen ref |

How paths select device suites, in `scripts/quality/selection.py`:

| Changed path | Device suites |
| --- | --- |
| Markdown outside `src/` directories; text, images, HTML, CSS, CSV and JSON under `docs/` | none (documents) |
| Documents tests read (`HOST_INPUTS`, such as `docs/testing/sandbox-scenarios.md`) | host checks, plus the suites that read them |
| `scripts/**`, except the scripts the device jobs run | none (host checks) |
| `<module>/src/test*/` | none (host checks) |
| `domain` or `data` sources, `data/schemas/` | data, app |
| `design-system` sources | design-system, app |
| `app` sources | app |
| `<module>/src/androidTest*/` | that module's suite only |
| Build files, lockfiles, `gradle/`, workflows, `scripts/check` and the modules it imports, unknown paths | all |

Mixed changes union their suites; only groups holding a selected suite start. No path
filter: `quality` always reports. Pull requests only read the Gradle caches; runs on
`main` write them, and only its `support` group saves the Android system image.

## The local loop

```bash
scripts/check domain app                  # ktlint and unit tests of these modules
scripts/check app --tests '*Budget*'      # only matching unit tests
scripts/check design-system --lint        # with Android lint
scripts/check --fast                      # G0, seconds
scripts/check --host                      # G1 exactly as CI runs it
ANDROID_SERIAL=emulator-5580 scripts/check --device           # every device suite on your emulator
ANDROID_SERIAL=emulator-5580 scripts/check --device support   # one hosted group
scripts/check --plan origin/main          # what the PR will run, from the merge base
```

Run the device tests you wrote or changed on your own emulator ([setup](setup.md#device-smoke));
the pull request runs the rest. A device run asks only the device in `ANDROID_SERIAL`
and refuses to start without it. Output stays under `build/`; nothing is committed.

## Release gate

`scripts/check --release`, on the owner's machine with the `sello.signing.*` entries
and an API-30 emulator in `ANDROID_SERIAL`, runs G1, every device suite whole, then
the licence list, the production signature checks and installing and opening the
signed APK. CI runs the same checks with a throwaway key; only the owner's run proves
the production signing. A release ticket says in its pull request that the owner ran
it and with what result. See [release](../release/README.md).

## Reading a red check

Open the failed job: its summary lists each failing test with the first lines of the
failure, as does its log, and the `quality` table shows which job failed. A failed
job keeps its test and lint reports for 14 days as an artifact. Fix the cause on the branch and push, or, for a flaky
infrastructure failure, use **Re-run failed jobs**: `quality` judges the latest
result of each job, so the jobs that passed are not repeated.

| Message | Meaning and action |
| --- | --- |
| `REFUSED: name your own emulator with ANDROID_SERIAL` | Start your own emulator and export its serial; assembling test APKs is not a device run. |
| `REFUSED: ... is not an API-30 device, or did not answer` | Use the pinned API-30 image with the default 320x640 profile. |
| `FAILED: ktlint exited 1` | Run `./gradlew ktlintFormat` and review the diff. |
| `ExpenseEntryScreenshotTest > ... FAILED` (any `*ScreenshotTest`) | A capture no longer matches its baseline. Open the diff in the host artifact; if the change is intended, run `./gradlew :app:recordRoborazziDebug --no-build-cache --rerun-tasks` (or `:design-system:`) and commit the new images. |
| `AssertionError: ... is blank: N distinct colours` | The screen drew nothing when captured; nothing was recorded or compared. Check the test shows its content and waits for it, rather than re-recording. |
| `FAILED: ... produced no fresh device test results` | The suite did not run: read the Gradle output above it (install or instrumentation failure). |
| `FAILED: ... skipped N device tests` | A device test is ignored or assumed away. Fix it; skips are not passes. |
| `FAILED: the host gate produced no test results` | Gradle failed before any test ran; read the first error in the log. |
| `Dependency Locking` / `does not have lock state` | A dependency changed without its lock. If intended, run `./scripts/update-dependency-locks` and review the diff. |
| `Semantic Node has no parent layout with a Scroll SemanticsAction` | The emulator screen is larger than the default profile. Use the default 320x640 AVD. |
| `release-production` failed: no signed release APK | The owner's `sello.signing.*` entries are missing; see [release](../release/README.md). |
| `BROKEN: <file>:<line>: missing target` | A document moved or a heading changed. Fix the link. |
| Red push run or nightly on `main` | Nothing else merges until it is fixed: reproduce with `scripts/check --host` or a device run, fix on a branch, merge, then re-run the workflow from the Actions tab. |
