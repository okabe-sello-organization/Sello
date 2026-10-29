# Reproducible Sello setup

Versions and their limits: [ADR 0005](../decisions/0005-toolchain.md). Gates and
`scripts/check`: the [quality gates guide](quality-gates.md).

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

| Artifact | Application ID | Launcher label |
| --- | --- | --- |
| `:app` release (customer) | `com.software.sello` | Sello |
| `:app` debug | `com.software.sello.debug` | Sello Debug |

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
./gradlew :design-system:dependencies --configuration debugRuntimeClasspath | grep 'project :'
./gradlew :domain:dependencies --configuration runtimeClasspath
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/debug/app-debug.apk
```

## Build and focused inspection

The host gate runs every module's tests, ktlint, lint and the assemblies; its exact
task list and the rest of the local loop are in the
[quality gates guide](quality-gates.md#the-local-loop):

```bash
scripts/check domain app        # ktlint and unit tests of some modules
scripts/check --host            # the host gate G1, as CI runs it
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
JVM 11. Build warning counts and resolved artifacts belong in the pull request;
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
and the job's own summary for failing tests. Distinguish executed tests
from `FROM-CACHE` and `UP-TO-DATE` task results; cached host results describe prior
execution for matching Gradle inputs. Device results must still be fresh for each
device run. To investigate cache behavior, run a focused task with `--info`; use
`--no-build-cache --rerun-tasks` when fresh execution is needed.

Hosted speedup is measured after cache restore/save is observed on GitHub, not
inferred from a local cache hit.

## Android system-image caching in CI

Device jobs restore only `$ANDROID_HOME/system-images/android-30/google_apis/x86_64`
through commit-pinned `actions/cache/restore` v5.1.0. The emulator action still runs
`sdkmanager` to check/install current packages. A cache miss uses the normal SDK
download path. G1 does not use this cache.

After the support device group passes on trusted `main`,
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

Compare cache restore/upload time, SDK installation, boot time and the complete device
job before claiming a speedup. The first main device run populates this cache; subsequent
PR/full runs can restore it. A compressed system image is large, so cache transfer
time must be included in the comparison.

## Hosted device groups

What each event runs, how changed paths select device groups and how the `quality`
check judges the jobs are in the [quality gates guide](quality-gates.md#what-ci-runs).
To reproduce one hosted group on your own emulator (`scripts/emulator start`, see
[device smoke](#device-smoke)):

```bash
ANDROID_SERIAL=emulator-5580 scripts/check --device app --modules app
ANDROID_SERIAL=emulator-5580 scripts/check --device support
```

## Device smoke

Use your own emulator, made the way CI makes its own: `scripts/emulator` creates an
AVD with a unique name in its own directory under `~/.cache/sello-emulators/`, picks a
port no other emulator uses, cold-boots it and prints its serial:

```bash
export ANDROID_SERIAL="$(scripts/emulator start)"   # such as emulator-5556
scripts/check --device                               # every device suite
scripts/emulator stop "$ANDROID_SERIAL"              # shuts it down and deletes the AVD
scripts/emulator list                                # what it started and still knows
```

It copies the Quality workflow's emulator step: the pinned API-30 `google_apis` x86_64
image, avdmanager's default hardware with no `--device` profile (320x640 at mdpi, no
navigation bar, no hardware keyboard), two cores, `-no-window -gpu swiftshader_indirect
-no-snapshot -noaudio -no-boot-anim`, the screen unlocked and the three animation
scales at zero. A test of `scripts/quality/test_emulator.py` keeps it equal to the
workflow. Do not make the AVD another way: a phone profile such as `-d pixel` adds an
on-screen navigation bar even with its screen edited to 320x640, the window loses
48 dp, and journeys that pass on CI fail (#224).

Agents can start emulators at once: a lock on the script's record makes them choose
different ports. `stop` refuses a serial it did not start, and stops one it did only
while both the emulator on that port and the recorded process still run that AVD; after
a crash the port or process may be someone else's, so then it only deletes the record
and the AVD. A boot that fails ends only the process it started. It never restarts the
adb server.

To look at the app by hand on it:

```bash
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" shell am start -W \
  -n com.software.sello.debug/com.software.sello.MainActivity
```

Confirm that the debug app opens on Recibo, not just that it installs. The unsigned release APK cannot be
installed, so customer/debug coexistence on a device needs the owner's signed build.
Shut down only the emulator you created, with `scripts/emulator stop`.

## Troubleshooting and signing

| Symptom | Meaning / action |
| --- | --- |
| `JAVA_HOME is set to an invalid directory` | Launcher cannot start. Point it at an installed JDK 25; daemon settings cannot repair an invalid launcher path. |
| `SDK location not found` | Set a valid ANDROID_HOME or ignored sdk.dir; install required packages. |
| Different daemon JDK than expected | Inspect daemon criteria and buildEnvironment; don't assume changing JAVA_HOME overrides them. |
| Missing SDK license/platform or repository access | Resolve actual install/license/network failure; don't bypass checks or silently change SDK/versions. |
| KSP adds forbidden kotlin.sourceSets | Use the pinned KSP2 version. Don't disable built-in Kotlin validation. |
| Child Kotlin JVM plugin has an unknown classpath version | Declare the version catalog's JVM plugin at root with apply false before AGP/Compose, then apply it in the child. |
| No device or boot not completed | APK assembly is not launch/instrumentation evidence; start your own with `scripts/emulator start` or record the missing gate. |

Debug/test builds use a local development keystore outside source. `assembleRelease`
needs **no distribution key**: without the owner's `sello.signing.*` entries in
`~/.gradle/gradle.properties` it yields an unsigned
`app/build/outputs/apk/release/app-release-unsigned.apk`, and ordinary gates pass.
With them it yields a signed `app-release.apk`; see [release](../release/README.md).
An unsigned or debug-signed artifact is not a production distribution build. The
owner keeps the key and its passwords; do not request them or create release keys
during setup. Only G3 needs the key, and without it G3 fails rather than substituting.
