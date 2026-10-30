# 0005 — Supported toolchain

**Owner:** technical executor · **Updated:** 2026-10-16
**Status:** selected toolchain; product support and app identities follow
[D02](0001-mvp-contract.md#d02--identities-and-supported-installation).

Use this combination without unrelated dependency upgrades. The
[version catalog](../../gradle/libs.versions.toml), wrapper and build files own the
actual settings; toolchain changes need compatibility review and fresh checks.

| Tool or setting | Version |
| --- | --- |
| Android Gradle plugin (AGP) | 9.3.3 |
| Gradle wrapper | 9.5.0, with distribution SHA-256 verification |
| Gradle daemon | JDK 25 |
| Kotlin, JVM plugin and Compose compiler | 2.4.20 |
| Java/Kotlin output | JVM 11 |
| Compose BOM | 2026.02.01 |
| Compile / target / minimum API | 37 / 37 / 30 |
| SDK build tools | 36.0.0 |
| KSP | 2.3.12, KSP2 |
| Room | 2.8.4 |

Kotlin's published tested AGP range reaches 9.3.1. Sello uses patch 9.3.3, checked
through project builds; do not claim that exact pairing is certified by the table.

JDK 25 runs the build; JVM 11 describes compiled output, not the build's Java runtime.
Gradle's embedded Kotlin is separate from the app compiler. Daemon criteria in
`gradle/gradle-daemon-jvm.properties` select JDK 25 independently of `JAVA_HOME`.

## Integration rules

- Keep AGP's built-in Kotlin for Android modules. Do not add
  `org.jetbrains.kotlin.android`, disable built-in Kotlin or bypass validation.
- Use catalog plugin aliases. Declare versioned plugins once at the root with
  `apply false`; declare the JVM plugin before AGP/Compose loads its classpath.
- Use KSP2 for Room processing. Schema changes still need exported schemas,
  migration tests and portable-format review under the owning ticket.
- Changing the daemon JDK version or vendor requires fresh compatibility checks.
  Another host needs JDK 25 installed or working toolchain provisioning.

Release assembly is unsigned without a signing configuration. Development signing
is not distribution signing; ownership and release acceptance follow
[D10](0001-mvp-contract.md#d10--release-acceptance-and-review).

## Setup, verification and sources

Use the [setup guide](../development/setup.md). The
[bootstrap evidence](../tickets/E01-foundation/attachments/SELLO-002-toolchain-verification.md) records the
initial compatibility checks; current feature and release evidence stays in tickets.

- [AGP compatibility](https://developer.android.com/build/releases/agp-9-3-0-release-notes) and [built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin).
- [Gradle compatibility](https://docs.gradle.org/9.5.0/userguide/compatibility.html), [daemon criteria](https://docs.gradle.org/9.5.0/userguide/gradle_daemon.html#sec:daemon_jvm_criteria) and [wrapper checksum](https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256).
- [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html) and [Compose compiler](https://developer.android.com/develop/ui/compose/compiler).
- [KSP 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12) and [Room requirements](https://developer.android.com/jetpack/androidx/releases/room).
