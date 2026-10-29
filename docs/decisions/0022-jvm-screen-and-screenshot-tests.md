# 0022 — Screen and screenshot tests on the JVM

**Owner:** project owner (user) · **Date:** 2026-10-29
**Status:** owner decision of 2026-10-29 to add Robolectric and Roborazzi; versions and
wiring chosen by the executor. The expense entry screen is the trial; other screens
move one feature at a time.

## Context

About 430 device tests render one screen from state given by hand. They need an
emulator, a test APK and a hosted device job, yet nothing in them needs a real device:
Compose lays out and dispatches touches in plain Kotlin. Visual changes had no check
at all beyond a person looking.

## Decision

- Screen tests run on the JVM with **Robolectric 4.17** (MIT licence) under
  `createComposeRule()`; screenshots are taken and compared with **Roborazzi 1.77.0**
  (library and Gradle plugin, Apache-2.0). Both are the latest stable releases on Maven
  Central on 2026-10-29.
- Compatibility, from the projects' own release notes: Robolectric 4.17 "supports SDK
  37" and adds it as a default SDK, and builds itself with AGP 9 and built-in Kotlin.
  Its getting-started page lists the `--add-opens` flags needed on JDK 17 and later;
  `app/build.gradle.kts` sets them, and the tests run on JDK 25. Roborazzi added AGP 9
  compatibility in 1.56.0; 1.77.0 fixes its verify task under the configuration cache,
  which Sello enables. Kotlin 2.4.20, Gradle 9.5.0 and Compose BOM 2026.02.01 are
  checked by this build, not claimed by either project.
- Robolectric runs **API 37**, the target SDK, on a 320x640 dp mdpi screen like the
  hosted emulator, with a plain `Application` so a screen test starts no storage or
  Koin (`app/src/test/resources/robolectric.properties`).
- **No network at test time.** The framework jar
  (`org.robolectric:android-all-instrumented:17-robolectric-15733970-i7`, Apache-2.0,
  about 240 MB) is a Gradle dependency in its own locked configuration. The test task
  passes `robolectric.offline=true` and points `robolectric.dependency.dir` at the
  jar's place in Gradle's cache, declared as a task input by file name only, so the
  build cache still relocates. CI's Gradle cache holds the jar like any other library.
- Robolectric 4.17 is built against androidx.test monitor 1.8.0; the old espresso
  3.5.1 fails under API 37. The JVM test classpath alone takes the matching release
  (core 1.7.0, ext.junit 1.3.0, espresso 3.7.0); device tests keep their versions.
- Baselines are committed in `app/src/test/screenshots/`. The host gate runs
  `:app:verifyRoborazziDebug`, so a visual change fails unless the same pull request
  re-records them, and the image diff is the visual review.
- Every screenshot is drawn by the test (`View.draw` into a bitmap handed to Roborazzi),
  not copied from the window: the window copy was intermittently blank, and a blank
  baseline passed its own check. A drawing of fewer than 32 distinct colours fails the
  test before it is recorded or compared. Baselines are at one pixel per dp (mdpi).
- Device tests keep storage on real Room, platform contracts and user journeys.

## Consequences

- The release runtime classpath does not change; only unit-test classpaths and the
  framework jar's configuration are added to `app/gradle.lockfile`.
- The expense entry screen and geometry tests (41) moved from the device to the JVM
  unchanged in their assertions. `design-system` has the same wiring and its
  component tests moved too; only the system keyboard's insets and the device's real
  animation setting stay on the device. Its baselines, one image per group of
  components, are in `design-system/src/test/screenshots/`; the groups whose components moved
  into their features (categories, goals and investments, bars and keypad) are in
  `app/src/test/screenshots/`, unchanged.
- Rendering may differ between operating systems; baselines are recorded on Linux,
  as CI runs.
