# SELLO-002 — Toolchain verification evidence

**Date:** 2026-10-08 (America/Bogota) · **Executor:** coding agent
**Base:** `b76214d` plus the toolchain/configuration/documentation changes listed
in the canonical ticket. This is working-tree evidence, not a clean-commit claim.
Decision: [ADR 0005](../../../decisions/0005-toolchain.md).
Commands for another executor: [setup](../../../development/setup.md).

## Compatibility and actual configuration

- Consulted version-specific AGP/Gradle documentation, Kotlin's compatibility
  table, Compose integration, KSP release notes and Room requirements; source
  links and explicit vendor-range limits are retained in ADR 0005.
- `./gradlew --version`: Gradle 9.5.0, launcher JetBrains JDK 25.0.2,
  daemon criteria Java 25, embedded Kotlin 2.3.20.
- `./gradlew :app:buildEnvironment --no-configuration-cache`: actual daemon
  JetBrains JDK 25.0.2 at `/opt/android-studio/jbr`.
- Compiler dependency reports resolve app compiler **2.4.20** and Compose compiler
  plugin **2.4.20** after the coordinated catalog change; these override the
  scaffold's AGP-default 2.2.10. Neither is Gradle's embedded Kotlin.
- Temporary read-only Gradle inspection task reported compile/target/minimum
  SDK **37/37/30**, build tools **36.0.0**, source/target Java **11**.
  `javap -verbose` confirmed fresh app and JVM-probe class major version **55**.
- Wrapper SHA-256 matches the official checksum exactly:
  `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`.
  Fresh provisioning exercised wrapper download/checking, not an existing wrapper cache.

## Clean-checkout and environment checks

Initial source export of clean `b76214d`, with a new empty GRADLE_USER_HOME,
passed the scaffold's G0 plus instrumented APK assembly: **128 tasks,
127 executed/1 up-to-date**, one sample JVM test, **17 existing lint warnings**.
This initial run still used Kotlin 2.2.10; it does not prove the later pins.

Final selected pins were subsequently rebuilt in the working tree and in a
second isolated source export with another initially empty GRADLE_USER_HOME.
The export uses `git archive HEAD` plus a non-destructive overlay of the actual
modified/untracked files; it includes uncommitted build changes. Both runs use:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease \
  :app:assembleDebugAndroidTest --no-build-cache --no-configuration-cache \
  --rerun-tasks --continue
```

The installed SDK/JDK and accepted SDK licenses are intentionally reused;
task/configuration/dependency/wrapper caches are not copied into the fresh Gradle
user home. No existing checkout/caches were wiped or reset. Final run counts and
outcomes are in the ticket's Delivery evidence.

Final working-tree run: **128 executed tasks**. Final fresh source/cache run:
**128 tasks, 127 executed/1 up-to-date**, with one passing sample JVM test and
**18 lint warnings** in each. The added catalog JVM alias exposes one additional
newer-version advisory; no lint errors or blanket baselines/suppressions were added.
Frozen Kotlin 2.4.20 remains the tested decision, not a claim to be the newest patch.
Final build-input SHA-256 values match between the working tree and source export:

| Input | SHA-256 |
| --- | --- |
| `build.gradle.kts` | `6225b7193ed87212f3d78e1445eb28c75f70effdbe0330a15e8f71a31f12cde9` |
| `gradle/libs.versions.toml` | `4100102d5ba789f20ba5c7fbc56effa452c0a878681c889926cb41159511eb8c` |

Negative setup checks in a source export without local.properties:

- Invalid JAVA_HOME exited nonzero with `JAVA_HOME is set to an invalid directory`.
- Invalid ANDROID_HOME/ANDROID_SDK_ROOT exited nonzero with `SDK location not found`.
- Restoring valid environment paths requires no source/signing edits. Release
  assembly produces `app-release-unsigned.apk` without distribution secrets.

## JVM / Room / KSP integration probe

The probe lives outside the project in a separate source export; it is **not**
a production module/schema or real financial transaction test. Reproduce it with:

1. Use the selected catalog Kotlin/Compose pins. Declare the versioned JVM plugin
   at root with `apply false` before AGP/Compose, then apply `kotlin("jvm")` without
   another version in a temporary `:jvm-probe` module. Set Java and Kotlin JVM
   targets to 11; compile a plain class with an integer-returning method.
2. Apply `com.google.devtools.ksp` **2.3.12** in the temporary app; add
   `implementation("androidx.room:room-runtime:2.8.4")` and
   `ksp("androidx.room:room-compiler:2.8.4")`. Set `room.schemaLocation` to a
   directory under that temporary app's build directory.
3. Add this synthetic generation fixture to the temporary app, using Room imports:

```kotlin
package com.software.sello

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity
data class ToolchainProbeEntity(@PrimaryKey val identifier: Int, val label: String)

@Dao
interface ToolchainProbeDao {
    @Query("SELECT * FROM ToolchainProbeEntity")
    fun read(): List<ToolchainProbeEntity>
}

@Database(entities = [ToolchainProbeEntity::class], version = 1)
abstract class ToolchainProbeDatabase : RoomDatabase() {
    abstract fun records(): ToolchainProbeDao
}

fun generatedDatabase(): ToolchainProbeDatabase = ToolchainProbeDatabase_Impl()
```

4. Run `./gradlew :app:compileDebugKotlin :app:lintDebug :jvm-probe:build --no-build-cache --no-configuration-cache --rerun-tasks`.
   Assert the generated `ToolchainProbeDatabase_Impl.kt`, compiled implementation
   and exported `com.software.sello.ToolchainProbeDatabase/1.json` exist. Inspect
   the JVM class output with javap; a successful JVM build with NO-SOURCE tests
   is compilation evidence, not an executed unit suite.

**Result:** selected Kotlin 2.4.20/KSP 2.3.12/Room 2.8.4 probe passed,
**30 executed tasks**; generated implementation and schema exist, Kotlin resolves
the generated type, and JVM output targets 11. Probe lint passed with **21 warnings**,
including direct-version-catalog advice in the temporary fixture. Production uses
catalog pins; no warnings were suppressed.

**Rejected executions:** KSP `2.2.10-2.0.2` failed with forbidden
`kotlin.sourceSets` additions. A child-only versioned JVM plugin request failed
with unknown already-loaded classpath version. Correct plugin/version/root
integration passed; no validation bypass or duplicate Android Kotlin plugin was
introduced. Kotlin 2.2.10 itself compiled in the initial probe, but was replaced
because its published fully supported Gradle range excludes 9.5.

## Device execution and limitations

Product-native device access reported disabled. CLI verification used a new,
isolated synthetic AVD, **sello002_api30**, serial **emulator-5580**, API **30**,
Google APIs x86_64 image revision **16**, window **320 × 640**, density **160**.
No existing AVD/device/private data was reused or reset. The phone's system clock
was not changed. This is not the future financial sandbox.

Both initial and final selected-pin debug builds installed successfully;
`am start -W` reported `Status: ok` / `com.software.sello/.MainActivity`.
UIAutomator output explicitly contained **Hello Android!**, not merely a launcher
intent. Final `:app:connectedDebugAndroidTest` executed the existing single package
identity test with zero failures/errors/skips on this API-30 emulator.

Physical devices, target-API runtime coverage, debug/catalog coexistence, financial
journeys, Room durability, production signing, hosted CI and release acceptance
were **not** verified here. They remain their owning tickets' gates. No independent
human/agent reviewer is claimed for SELLO-002; the executor's source/evidence
cross-check is self-review, separate from SELLO-001's independent financial review.
The isolated emulator was shut down after final verification; existing AVDs and
devices were left untouched.

Local diagnostic logs use `/tmp/sello-002-*`; they are temporary, not durable CI
artifacts. This repository documentation record and the ticket retain commands,
results and limitations; raw Gradle/device reports remain generated/ignored.

Final documentation bookkeeping checks: 32 board tests passed; generated views
validated 9 epics/43 tickets; 337 local documentation file/anchor links passed;
catalog syntax/coordinated pins and `git diff --check` passed. Final G0 rerun after
documentation updates passed, 97 tasks (2 executed/95 up-to-date), with cached
JVM/lint results and unchanged 18 warnings; log
`/tmp/sello-002-final-document-g0.log`. This rerun is distinct from the uncached
source/output and executed device evidence above.
