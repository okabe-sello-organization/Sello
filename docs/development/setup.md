# Reproducible Sello setup

Versions and their limits: [ADR 0005](../decisions/0005-toolchain.md). Gates and the
quality runner: the [execution guide](quality-gates.md#quality-runner).

## Prerequisites

- Git; Python 3 standard library for board checks; JDK **25**; Android SDK
  command-line tools and platform-tools.
- SDK platform **37.0** / API 37 and build tools **36.0.0**. API 30 is the minimum
  supported app runtime, not the SDK platform used to compile it.
- For device checks: emulator plus an API-30 system image or an explicitly selected
  device. Linux emulator verification also needs working KVM/virtualization.
- First provisioning needs Google Maven, Maven Central, Gradle Plugin Portal and
  Gradle distribution access; JDK auto-provisioning may need Foojay. A first build
  is not promised to work offline. Review SDK/JDK terms rather than bypassing them.

Do not install a global Gradle or use an IDE's different wrapper/compiler version.
Use `./gradlew` from the repository root. Android Studio is optional for CLI builds.

## Environment and SDK

On the verified host:

```bash
export ANDROID_HOME=/home/okabe94/Android/Sdk
export JAVA_HOME=/opt/android-studio/jbr
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew --version
./gradlew :app:buildEnvironment --no-configuration-cache
```

On another host, replace **both** paths with actual installed SDK/JDK locations.
`JAVA_HOME` selects the wrapper launcher; `gradle/gradle-daemon-jvm.properties`
requires a JDK-25 daemon and overrides JAVA_HOME/`org.gradle.java.home` for that
choice. Confirm the actual daemon using `:app:buildEnvironment`, not assumptions
about the shell or IDE. Foojay configuration can provision missing toolchains when
network/platform support exists; an installed JDK 25 is the simplest tested path.

An SDK location can alternatively be set with ignored root `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Never commit that file, host paths in build logic, `.gradle`/build caches or signing
material. Do not copy another developer's local.properties into a clean checkout.
Resolve conflicting SDK environment values rather than leaving stale paths.

The tested command-line-tools package is 23.0.0 (installed under `latest`). Its
`android sdk` CLI uses slash-separated package names:

```bash
ANDROID_CLI="$ANDROID_HOME/cmdline-tools/latest/bin/android"
"$ANDROID_CLI" --sdk="$ANDROID_HOME" sdk install platforms/android-37.0
"$ANDROID_CLI" --sdk="$ANDROID_HOME" sdk install build-tools/36.0.0
"$ANDROID_CLI" --sdk="$ANDROID_HOME" sdk list
```

Older SDK managers use `sdkmanager --sdk_root="$ANDROID_HOME" "platforms;android-37.0" "build-tools;36.0.0"`.
The installed sdkmanager wrapper reports deprecation; use the actual installed
CLI's help, not an assumed syntax. Install platform-tools, the emulator and
`system-images/android-30/google_apis/x86_64` for device work; that tooling is not
committed to the project.

## Modules, identities and tasks

| Module | Plugin / namespace | Project dependencies | Host tests | Device tests |
| --- | --- | --- | --- | --- |
| `:domain` | Kotlin/JVM, `com.software.sello.domain` | none (Kotlin stdlib only) | `:domain:test` | — |
| `:data` | Android library + KSP, `com.software.sello.data` | `:domain` | `:data:testDebugUnitTest` | `:data:connectedDebugAndroidTest` |
| `:design-system` | Android library + Compose, `com.software.sello.designsystem` | none | `:design-system:testDebugUnitTest` | `:design-system:connectedDebugAndroidTest` |
| `:app` | Android application, `com.software.sello` | `:domain`, `:design-system`, `:data` | `:app:testDebugUnitTest` | `:app:connectedDebugAndroidTest` |
| `:catalog` | Android application, `com.software.sello.catalog` | `:design-system` | `:catalog:testDebugUnitTest` | `:catalog:connectedDebugAndroidTest` |
| `:prototype` | Android application, `com.software.sello.prototype` | `:design-system` | — | — |

| Artifact | Application ID | Launcher label |
| --- | --- | --- |
| `:app` release (customer) | `com.software.sello` | Sello |
| `:app` debug | `com.software.sello.debug` | Sello Debug |
| `:catalog` debug | `com.software.sello.catalog` | Sello Catalog |
| `:prototype` debug | `com.software.sello.prototype` | Sello Prototipo |

- `:prototype` is the owner's debugging app: v1.1 sample screens with no storage. It
  is never shipped and has no tests; the gate's ktlint and lint still cover it. See its
  [README](../../prototype/README.md).
- `:app`'s Gradle edge to `:data` exists for the composition root only. Gradle
  cannot enforce that package rule; `scripts/quality/architecture.py` does.
- `:data` applies KSP with Room and exports schemas to `data/schemas/`.
- The debug label comes from `app/src/debug/res/values/strings.xml`. The class name
  stays `com.software.sello.MainActivity`; only the application ID gains `.debug`.
- Dependencies are locked per module. After a deliberate dependency change, run
  `./scripts/update-dependency-locks` and review the lockfile diff
  ([ADR 0006](../decisions/0006-quality-tooling.md)).

Inspect the graph and built identities:

```bash
./gradlew projects
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep 'project :'
./gradlew :catalog:dependencies --configuration debugRuntimeClasspath | grep 'project :'
./gradlew :domain:dependencies --configuration runtimeClasspath
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk
```

## Build and focused inspection

The host gate runs every module's tests, ktlint, lint and the assemblies; its exact
task list is in the [board README](../tickets/README.md):

```bash
./scripts/verify-ticket SELLO-0XX             # the local requirement: host gate G1 (G3 whole for a release)
./scripts/verify-ticket SELLO-0XX --gate G2   # add every device suite locally
./scripts/verify-ticket SELLO-0XX --affected origin/main --retain  # add the affected device suites
./scripts/verify-ticket --merge-candidate origin/main  # G0 for eligible docs, otherwise G1 locally
```

Inspect actual compiler versions independently of Gradle's embedded Kotlin:

```bash
./gradlew :app:dependencies --configuration kotlinCompilerClasspath
./gradlew :app:dependencies --configuration kotlinCompilerPluginClasspathDebug
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

`:domain` uses the same Kotlin plugin pin through the root `apply false` declaration.

Expected app compiler and Compose compiler plugin: **2.4.20**. `--version` reports
embedded Kotlin **2.3.20**, which is not the app compiler. Java/Kotlin output remains
JVM 11. Build warning counts and resolved artifacts belong in ticket evidence;
do not upgrade or baseline them incidentally.

For independent-output validation in a clean source checkout:

```bash
export GRADLE_USER_HOME="$(mktemp -d)"
./gradlew clean :domain:test testDebugUnitTest lintDebug assembleDebug \
  :app:assembleRelease assembleDebugAndroidTest --no-build-cache \
  --no-configuration-cache --rerun-tasks --continue
```

An empty Gradle user home forces wrapper/dependency provisioning, not just clean
outputs; this can consume substantial disk/network. It still uses the installed
SDK/JDK and accepted SDK licenses. Do not delete the user's existing caches or
use `git clean/reset` to manufacture a clean checkout. A separate source export
via `git archive HEAD` is safe but excludes uncommitted changes; test those
explicitly before claiming the latest snapshot was verified.

## Gradle caching in CI

The Quality workflow uses commit-pinned `gradle/actions/setup-gradle` v5.0.2
to restore downloaded dependencies, wrapper distributions, compiled build scripts
and Gradle's local task-output cache. Only trusted jobs on `main` save entries;
pull requests and other refs use them read-only. Keep `actions/setup-java` free
of a second Gradle cache configuration so the two actions cannot compete.

Host checks and device groups have separate job/matrix cache identities.
`setup-gradle` includes that context in its immutable key, so a host push cannot
occupy the entry used to save a device group's builds at the same SHA. Normal
restore fallback remains enabled for cold identities and migration from the older
single-job profiles. The first runs after this topology change can rebuild more
tasks before the new main entries exist.

Gradle validates task inputs and rebuilds affected outputs. Once an identity has
saved a key for a SHA, another run at that identity/SHA cannot update it. Each
successful trusted-main part can publish its cache independently; a cache entry
does not prove the full gate passed. PRs and non-main refs remain read-only.

`org.gradle.caching=true` enables task-output reuse locally and in CI. Gradle
still selects the full gate task graph and checks each cacheable task's inputs.
Data unit tests also track the complete schema directory and Kotlin storage sources
that `ExportedSchemaTest` reads directly. Changes to those files invalidate
its test-task result even when compiled classes are unchanged, including unexpected
schema filenames that the retention test must reject.

Configuration caching remains enabled within a job. Its project state is not
uploaded: no `cache-encryption-key` secret is configured. No application data,
emulator snapshot, signing material or project output directory is added to the
cache paths. Build Scan publication, dependency submission and PR comments stay off.

Use the action's job summary for cache restore/save information and task outcomes,
and the retained quality logs/XML for gate evidence. Distinguish executed tests
from `FROM-CACHE` and `UP-TO-DATE` task results; cached host results describe prior
execution for matching Gradle inputs. Device results must still be fresh for each
G2 run. To investigate cache behavior, run a focused task with `--info`; use
`--no-build-cache --rerun-tasks` when fresh execution is needed.

The options, baseline and verification live in the
[CI performance plan](ci-performance-plan.md). Hosted speedup is measured after
cache restore/save is observed on GitHub, not inferred from a local cache hit.

## Android system-image caching in CI

G2 restores only `$ANDROID_HOME/system-images/android-30/google_apis/x86_64`
through commit-pinned `actions/cache/restore` v5.1.0. The emulator action still runs
`sdkmanager` to check/install current packages. A cache miss uses the normal SDK
download path. G1 does not use this cache.

After the support device group and evidence validation pass on trusted `main`,
that group alone uses `actions/cache/save` v5.1.0 to save a key containing the OS, host architecture,
API/target/image architecture and SHA-256 of the installed `source.properties`.
Restores use a prefix to find the latest saved image revision; saves compare the
matched key with the installed metadata key. If Google publishes a new revision,
`sdkmanager` can update it and the next successful main support group saves a new key. PRs
and non-main refs never save entries. This is a separate SDK cache, not a second
Gradle cache.

The cached files are the vendor SDK image. AVD directories, running VM snapshots,
ADB keys, app databases and test results are outside its path. Each device run
still force-creates an AVD and cold-boots with `-no-snapshot`. Emulator cores stay
at the action's existing default of two; RAM and Gradle worker settings are unchanged.

Compare cache restore/upload time, SDK installation, boot time and the complete G2
job before claiming a speedup. The first main G2 populates this cache; subsequent
PR/full runs can restore it. A compressed system image is large, so cache transfer
time must be included in the comparison.

## Parallel hosted G2

The shared planner implements [ADR 0014](../decisions/0014-change-aware-quality-gates.md).
Eligible documentation advancing no ticket uses G0 (Python/Git, board/tool tests
and architecture), without Java, Gradle or SDK setup. Code PRs use G2 with affected
modules and consumers; shared/build/tooling/unknown changes select full devices.
Main code pushes retain G1 to populate trusted caches; eligible documentation
pushes use G0. Scheduled and manual runs use full G3 with CI's throwaway signing;
the nightly skips every job when `main` has not moved since the last successful
nightly.

G1/G2 host checks run once and retain every existing task. Only required device
groups start: app, or support with the selected data/design-system/catalog suites.
Each creates a fresh, cold-booted API-30 emulator. Sibling groups continue after a
failure for diagnostics. The selection map is app → app; catalog → catalog;
data/domain → data/app; design-system → design-system/app/catalog. Mixed changes
union those sets. No manual module/task selector can override the dependency graph.

The only required result is the final `quality` job. It runs even if an upstream
job fails or is cancelled. It requires successful planning and host jobs, plus
every selected device group/module for G2/G3; G0/G1 require the device job to be skipped. The collector
checks every module's fresh XML, check commands, outcome, toolchain, commit, input
fingerprint, base, event and workflow run/attempt. Missing or inconsistent parts
fail the gate. Device skips fail too. A part cannot be retained as ticket evidence.

Parts and raw logs/results are retained as `quality-part-<attempt>-<part>` artifacts
for 90 days. The collector downloads only the current attempt, verifies artifact
digests, and writes standard full reports plus the source parts under
`quality-reports`. The download action's flat single-artifact layout is accepted
only when host is the sole selected part (G0/G1). Re-run the complete workflow for a fresh comparison; retrying
only a failed group cannot reuse successful groups from a previous attempt.
The pinned `actions/download-artifact` v8.0.2 reads the current run without new
credentials or GitHub permissions. Normal local `verify-ticket` commands execute
the host gate; the hosted run executes the device suites. Add `--affected BASE` to a
ticket command for targeted device coverage locally; `--gate G2` forces every device suite.
Reports use schema v2 with full/affected/documents scope, compared base/paths and
exact selected modules. Targeted verification is not reported as full G2; historical
schema-v1 reports remain readable.

To exercise full collection locally, choose a fresh output directory and two isolated
API-30 emulators. The workflow_dispatch event below deliberately selects full G3:
the host part also runs the licence table check and the throwaway-key release
check, which need `keytool` and the build-tools `apksigner`.
For pull_request event probes, inspect `--ci-plan BASE --ci-event pull_request`
(with QUALITY_EXECUTION_ID set) and execute only its selected groups. Keep inputs unchanged until collection finishes. For example:

```bash
export QUALITY_EXECUTION_ID=local-device-split-001
export QUALITY_PARTS_DIR=build/reports/quality-parts/local-device-split-001
./scripts/verify-ticket --merge-candidate HEAD --ci-event workflow_dispatch \
  --ci-part host --ci-output "$QUALITY_PARTS_DIR"
ANDROID_SERIAL=emulator-5584 ./scripts/verify-ticket --merge-candidate HEAD \
  --ci-event workflow_dispatch --ci-part app --ci-output "$QUALITY_PARTS_DIR"
ANDROID_SERIAL=emulator-5586 ./scripts/verify-ticket --merge-candidate HEAD \
  --ci-event workflow_dispatch --ci-part support --ci-output "$QUALITY_PARTS_DIR"
./scripts/verify-ticket --merge-candidate HEAD --ci-event workflow_dispatch \
  --ci-collect "$QUALITY_PARTS_DIR"
```

These local commands may run sequentially to avoid sharing a Gradle workspace;
GitHub supplies separate runners/workspaces. Local passing collection proves full
coverage and report integration, not hosted parallelism or a measured speedup.

## Device smoke

Use a dedicated synthetic AVD; do not wipe/reset an owner's device or reuse their
private data. Example isolated setup for an already installed API-30 image:

```bash
export ANDROID_AVD_HOME="$(mktemp -d)"
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  --name sello_api30 --package 'system-images;android-30;google_apis;x86_64' \
  --path "$ANDROID_AVD_HOME/sello_api30.avd"
"$ANDROID_HOME/emulator/emulator" -avd sello_api30 -port 5580 \
  -no-window -no-audio -no-snapshot -gpu swiftshader -no-boot-anim
```

Run the emulator in its own terminal; first confirm that port/device slot is free.
In another terminal with the same SDK/JDK environment:

```bash
export ANDROID_SERIAL=emulator-5580
timeout 90 adb -s "$ANDROID_SERIAL" wait-for-device
adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" install -r catalog/build/outputs/apk/debug/catalog-debug.apk
adb -s "$ANDROID_SERIAL" shell am start -W \
  -n com.software.sello.debug/com.software.sello.MainActivity
adb -s "$ANDROID_SERIAL" shell am start -W -n com.software.sello.catalog/.CatalogActivity
./scripts/verify-ticket SELLO-0XX --gate G2   # the device gate
```

Wait for boot completion `1` with a bounded condition check; device connectivity
alone is not readiness. Confirm API `30`, that the debug app opens on Recibo and that
the catalog opens, not just successful installs. The unsigned release APK cannot be
installed, so customer/debug coexistence on a device needs the owner's signed build.
Shut down only the emulator you created: `adb -s "$ANDROID_SERIAL" emu kill`.

## Troubleshooting and signing

| Symptom | Meaning / action |
| --- | --- |
| `JAVA_HOME is set to an invalid directory` | Launcher cannot start. Point it at an installed JDK 25; daemon settings cannot repair an invalid launcher path. |
| `SDK location not found` | Set a valid ANDROID_HOME or ignored sdk.dir; install required packages. |
| Different daemon JDK than expected | Inspect daemon criteria and buildEnvironment; don't assume changing JAVA_HOME overrides them. |
| Missing SDK license/platform or repository access | Resolve actual install/license/network failure; don't bypass checks or silently change SDK/versions. |
| KSP adds forbidden kotlin.sourceSets | Use the pinned KSP2 version. Don't disable built-in Kotlin validation. |
| Child Kotlin JVM plugin has an unknown classpath version | Declare the catalog's versioned JVM plugin at root with apply false before AGP/Compose, then apply it in the child. |
| No device or boot not completed | APK assembly is not launch/instrumentation evidence; provide an isolated emulator or record the missing gate. |

Debug/test builds use a local development keystore outside source. `assembleRelease`
needs **no distribution key**: without the owner's `sello.signing.*` entries in
`~/.gradle/gradle.properties` it yields an unsigned
`app/build/outputs/apk/release/app-release-unsigned.apk`, and ordinary gates pass.
With them it yields a signed `app-release.apk`; see [release](../release/README.md).
An unsigned or debug-signed artifact is not a production distribution build. The
owner keeps the key and its passwords; do not request them or create release keys
during setup. Only G3 needs the key, and without it G3 fails rather than substituting.
