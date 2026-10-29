# SELLO-002 — Establish and verify the supported Android toolchain

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** None
- **Gate:** G0

### Outcome
Replace accidental template compatibility with a reproducible supported matrix,
without unnecessary upgrades or changing product behavior.

### Deliverables
Validated AGP/Gradle/Kotlin/Compose/KSP/JDK/SDK matrix, pinned catalog/wrapper and
setup instructions. Explain Gradle daemon toolchain versus Java bytecode target;
audit the existing JDK-25 daemon configuration and Java-11 target rather than assuming
JAVA_HOME alone controls the daemon. Establish debug versus production signing paths.

### Acceptance criteria
- A clean checkout builds using documented installed/downloaded prerequisites;
  wrapper checksum and AGP built-in Kotlin integration are valid.
- Dependency versions are pinned, source/build artifacts contain no credentials,
  and ignored machine-local configuration stays untracked.
- Approved D02: verify SDK 30 floor and customer/debug/catalog identities against
  the selected toolchain; record actual compatibility rather than inferring it from approval.
- Release compilation succeeds without distribution-signing secrets. Production signing remains
  an explicit later release gate; no keys/passwords enter source control.

### Tests
Run G0 on a clean build/cache-independent verification where practical; compile
the instrumented APK. Exercise missing-SDK/JDK/signing setup instructions and
record actual daemon/JDK versions plus dependency/lint warnings.

### Working checkpoint
App's current greeting works on an emulator/device; if no device exists, record
assembly separately and arrange smoke evidence before concluding launch acceptance.

### Context and starting points
The scaffold currently builds, but its mixed versions, JDK daemon criteria and SDK
targets have not been declared supported. Read
[tool commands](../../development/execution-guide.md#tools-and-host-verification), the catalog,
wrapper, `gradle.properties`, daemon criteria and `app/build.gradle.kts`.

### Implementation plan
1. Record actual `./gradlew --version`, installed SDK/platform/build tools and resolved
   catalog versions; distinguish launcher JVM, daemon JDK and Java/Kotlin bytecode targets.
2. Consult the guide's official AGP/Gradle/Kotlin sources and select the smallest
   compatible stable matrix; document why existing pins stay or change, including KSP.
3. Create `docs/decisions/0005-toolchain.md` and `docs/development/setup.md` with exact
   prerequisites/install locations, untracked SDK configuration and commands for a new actor.
4. Update only necessary pins/targets/wrapper checksum. Do not independently raise
   minSdk or change product identity before SELLO-001 approval.
5. Test without relying on cached outputs; document signing setup without credentials.
   Prepare/install the scaffold on the agreed emulator/device and record launcher smoke.

### Concrete cases and pitfalls
- `JAVA_HOME` pointing at Android Studio does not override daemon criteria by itself.
- A supported matrix must include the Compose plugin/BOM and selected KSP processor
  compatibility, not just successful dependency resolution with an old cache.
- A missing device is an unmet launch check. Unsigned release assembly is not a
  production-signed private APK; do not create a distribution key just to silence a build failure.

### Verification recipe
Run `./gradlew --version`, `./gradlew clean`, G0 and
`./gradlew :app:assembleDebugAndroidTest`; inspect dependency/lint reports. Follow
[device setup](../../development/execution-guide.md#device-and-process-restoration) for launch smoke.
Record the successful matrix, warnings and any missing prerequisites.

### Execution progress
2026-10-08: started after committing the owner walkthrough as `1b0894b`.
SELLO-001 remains In Progress: its policy approvals are complete, but the named
independent technical reviewer and full-corpus review are still outstanding.
SELLO-002 has no predecessor dependency and can proceed independently.

Inspected the actual single-module scaffold, catalog, wrapper, daemon criteria,
installed SDKs and Git exclusions. No build pins or product behavior changed.
`./gradlew --version` reports Gradle 9.5.0, launcher JDK 25.0.2 and daemon criteria
for Java 25. `:app:buildEnvironment` identifies the actual daemon as JetBrains
25.0.2 at `/opt/android-studio/jbr`. Gradle's reported embedded Kotlin 2.3.20 is
not proof of the app compiler version; the catalog's Compose plugin pin remains
2.2.10 and built-in Kotlin compiler compatibility still needs resolution.
`javap -verbose` on the freshly compiled MainActivity reports class major version
55 (Java 11), confirming that daemon JDK and output bytecode are different concerns.

Installed platforms: 32, 33, 35, 36, 36.1 and 37.0; build tools: 35.0.0, 36.0.0
and 36.1.0. The merged debug manifest declares minimum API 30, target API 37 and
package `com.software.sello`; the approved debug/catalog identities remain future
SELLO-003 deliverables, not installed variants. No API-30 runtime check occurred.
`adb devices -l` returned no attached devices. Release output is explicitly
`app-release-unsigned.apk`; no signing material or machine-local properties are
tracked. Owner retains production signing responsibility.

Uncached-output verification on clean commit `1b0894b`:
`./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease :app:assembleDebugAndroidTest --no-build-cache --no-configuration-cache --rerun-tasks --continue`
passed: 128 tasks executed, one sample JVM test with zero failures/errors/skips,
17 existing lint warnings. Log: `/tmp/sello-002-clean-g0.log` (local temporary
evidence, not a committed or durable CI artifact). This bypassed task/build and
configuration caches, not downloaded dependency caches; it does not yet prove
fresh dependency resolution or clean-checkout setup. Instrumented APK assembly
passed; no instrumented test or launch smoke ran.

Next: verify the official version-specific compatibility matrix, actual app
compiler/Compose integration and wrapper checksum; settle KSP compatibility for
bootstrap; write the toolchain ADR and reproducible setup/error instructions;
obtain launcher smoke evidence before completion. Use ADR 0005 because ADR 0002
already owns signed carryover; do not overwrite or duplicate its decision number.
No independent review, release signing or SELLO-002 completion is claimed.

2026-10-08 completion: recorded [ADR 0005](../../decisions/0005-toolchain.md),
[reproducible setup](../../development/setup.md) and
[verification evidence](attachments/SELLO-002-toolchain-verification.md).
Retained AGP 9.3.3/Gradle 9.5/JDK 25/SDK 37/30/BOM 2026.02.01. Aligned Kotlin
and Compose compiler to 2.4.20 because the old JVM plugin's published fully
supported range excludes Gradle 9.5; documented the tested AGP 9.3.3 patch versus
Kotlin's published 9.3.1 bound. Added catalog JVM/KSP aliases, KSP 2.3.12 pin
and root JVM declaration before AGP. No runtime feature, production module,
Room schema/dependency, signing secret or product identity changed.
The old KSP candidate failed with built-in Kotlin source-set validation;
the selected KSP2/Room/JVM combination generated/exported/compiled successfully.
Final clean builds and API-30 greeting/context-test execution passed. SELLO-002
is Done; earlier progress entries describe superseded inspected configurations.
