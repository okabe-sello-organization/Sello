# SELLO-003 — Bootstrap enforced modules and variant identities

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-001, SELLO-002
- **Gate:** G1

### Outcome
Create the architecture's compilation boundaries without introducing unfinished features.

### Deliverables
`:domain` Kotlin/JVM, `:data` and `:design-system` Android libraries, existing
`:app`, and developer `:catalog` application. Configure test source sets, Room schema
export location/KSP capability, catalog launcher, separate debug/catalog application
IDs and the allowed dependency graph. Move template theme ownership without duplication.

### Acceptance criteria
- App and minimal catalog independently install/launch; customer app never depends
  on catalog. Domain has no Android/UI/storage/DI dependency.
- Data import access is reserved for app composition; no reverse module edge or
  screen-to-screen implementation import is introduced.
- Existing instrumentation package assertions are variant-aware; sample tests do
  not produce a false failure after adding the debug ID suffix.

### Tests
Run G1 and compile affected test APKs. Inspect Gradle dependency graphs and package
IDs. Intentionally attempt a prohibited domain Android import/module edge to prove
the compilation boundary rejects it, then remove the negative fixture.

### Working checkpoint
Both launchers show honest minimal UI; no new product destination or fake save exists.

### Context and starting points
Today all code is in `:app`, so source packages do not enforce ownership. Inputs
are accepted identity/toolchain records from SELLO-001/002 and the
[target map](../../development/execution-guide.md#module-and-file-map). Read architecture §2 and
official build-variant guidance in the source library before altering Gradle.

### Implementation plan
1. Add the four target siblings to `settings.gradle.kts`; configure JVM domain and
   Android library/application plugins using the accepted matrix/catalog, not copy-pasted versions.
   Declare the catalog's versioned JVM plugin at root with `apply false` before
   AGP/Compose and apply it in the child; SELLO-002's probe exposed a child-only
   version request's unknown-classpath-version failure. Use pinned KSP2 without
   suppressing built-in Kotlin source-set validation; see ADR 0005.
2. Create build/test/resource/manifest skeletons for each module; set namespaces,
   SDK/bytecode/test-runner settings and allowed dependency edges explicitly.
3. Give app debug and catalog independent accepted IDs; move reusable theme ownership
   to design-system and keep MainActivity functional. Catalog gets its own real launcher.
4. Add approved Room/KSP/schema export configuration to data, but no speculative
   database tables. Compile test source sets and correct variant-specific package assertions.
5. Document module task/dependency inventory in setup instructions; verify no app→catalog,
   design-system→domain or reverse data edge. Do not add feature navigation yet.

### Concrete cases and pitfalls
- Installing debug app and catalog must not replace production or one another.
- Domain's Android imports must fail; app's unavoidable data Gradle edge is composition-only
  in source, which SELLO-004 enforces separately. Gradle alone cannot enforce that package rule.
- Empty modules still need usable documented test/build tasks; no-matching-tests
  cannot be described as executed business tests.

### Verification recipe
Run `./gradlew projects`, module `dependencies` reports and G1. Compile app/catalog
test APKs and install both launchers. Demonstrate a temporary forbidden domain import
fails compilation, remove it, rerun green, and attach the dependency/package-ID evidence.

### Execution progress
2026-10-08: started from clean `475d3f8` after the owner chose SELLO-003 and the
split "agent implements, owner reviews". G0 passed on the untouched scaffold first.

Added `:domain` (Kotlin/JVM), `:data` and `:design-system` (Android libraries) and
`:catalog` (Android application) to settings; root declares the Android-library and
KSP aliases `apply false` after the JVM plugin. Moved the template theme files from
`app/.../ui/theme` to `design-system/.../designsystem/theme` (package renamed, content
unchanged); app and catalog both consume that one `SelloTheme`. App debug gained
`.debug` and the "Sello Debug" label; catalog has its own ID, label, launcher icon,
activity and disabled backup/transfer. The package assertion moved to
`app/src/androidTestDebug` with the debug ID and label; catalog has an equivalent test.

Catalog additions, all pinned to already verified or already resolved versions: Room
2.8.4 runtime/compiler (the SELLO-002 probe candidate) in `:data` only, and
`androidx.test:runner` 1.5.2, the version `:app` already resolved transitively. No
existing pin changed. `:data` sets `room.schemaLocation` to `data/schemas`; no
database, entity or placeholder schema ships. `:domain`, `:data` and `:design-system`
have no test sources, and `:domain`/`:data` no production sources yet.

Not done here by design: source-level composition-only `:data` import rule and other
architecture checks (SELLO-004), Room migration-test configuration and the customer
app's OS-backup disablement (SELLO-011), Sello tokens and catalog examples (SELLO-006).

2026-10-08: owner approved the delivery and authorized the commit; moved to Done.
