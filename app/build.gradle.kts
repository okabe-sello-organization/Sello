import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

/*
 * The owner's release key, read from their own ~/.gradle/gradle.properties and never
 * from the repository: sello.signing.storeFile, storePassword, keyAlias, keyPassword.
 * Without all four, the release build stays unsigned; nothing falls back to a debug key.
 * See docs/release/README.md.
 */
val releaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .associateWith {
        providers.gradleProperty("sello.signing.$it").orNull?.takeIf(String::isNotBlank)
    }

android {
    namespace = "com.software.sello"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.software.sello"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "com.software.sello.SelloTestRunner"
    }

    signingConfigs {
        if (releaseSigning.values.all { it != null }) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile")!!)
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // R8 shrinks and optimizes the customer build; see docs/testing/performance-profile.md.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Screen tests on the JVM (Robolectric) read the app's real resources; see ADR 0022.
    // The opens are Robolectric's documented flags for JDK 17 and later.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
            )
        }
    }
    // Lint stays at zero warnings. The version-update notices depend on the network, so they
    // would turn the gate red whenever upstream publishes; dependency updates are decided by hand.
    lint {
        warningsAsErrors = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

/*
 * Robolectric runs the Android framework from one jar. Gradle resolves and locks it like
 * any other dependency, and the unit tests read it from Gradle's cache in offline mode,
 * so a test run never downloads anything (ADR 0022).
 */
val robolectricAndroidAll: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

class RobolectricOffline(
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) val jars: FileCollection
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf(
        "-Drobolectric.offline=true",
        "-Drobolectric.dependency.dir=${jars.singleFile.parentFile}"
    )
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders += RobolectricOffline(robolectricAndroidAll)
}

// Screenshot baselines are committed beside the tests; see docs/development/quality-gates.md.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":design-system"))
    // Composition root only: features and platform adapters must not import :data.
    implementation(project(":data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.core)
    implementation(libs.koin.core.viewmodel)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit.jvm)
    testImplementation(libs.androidx.test.core.jvm)
    testImplementation(libs.androidx.espresso.core.jvm)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    robolectricAndroidAll(libs.robolectric.android.all)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/*
 * A customer build must not contain the debug tools at all, not merely hide them:
 * no devtools class, no tools activity in the manifest, no sandbox or tools resource.
 * The check reads the APK itself, so it holds whatever shrinking does or does not do.
 * `-Psello.inspectApk=<dir>` points it at another APK directory, such as the debug
 * one, to show that it finds them when they are there.
 */
val verifyReleaseHasNoDeveloperTools by tasks.registering {
    val apkDirectory = providers.gradleProperty("sello.inspectApk")
        .map { file(it) }
        .orElse(layout.buildDirectory.dir("outputs/apk/release").map { it.asFile })
    inputs.dir(apkDirectory)
    doLast {
        val markers = listOf(
            "com/software/sello/devtools",
            "com.software.sello.devtools",
            "ToolsActivity",
            "sandbox_badge",
            "tools_reset",
            "Sello Herramientas",
            "SimulatedFault",
            "tools_faults_heading",
            "Sello sandbox diagnostic",
            // The debug composition, its sandbox storage names and the debug previews.
            "DebugComposition",
            "sandbox.db",
            "sandbox-preferences",
            "ReciboPreviews",
            "ResumenPreviews",
            "IncomePreviews"
        )
        val apks = apkDirectory.get().walkTopDown().filter { it.extension == "apk" }.toList()
        check(apks.isNotEmpty()) { "no APK in ${apkDirectory.get()}" }
        val found = apks.flatMap { apk ->
            ZipFile(apk).use { zip ->
                zip.entries().toList()
                    .filter {
                        it.name.endsWith(".dex") || it.name == "AndroidManifest.xml" ||
                            it.name == "resources.arsc"
                    }
                    .flatMap { entry ->
                        val bytes = zip.getInputStream(entry).readBytes()
                        val utf8 = String(bytes, Charsets.ISO_8859_1)
                        val utf16 = String(bytes, Charsets.UTF_16LE)
                        val shifted = String(bytes.copyOfRange(1, bytes.size), Charsets.UTF_16LE)
                        markers.filter { it in utf8 || it in utf16 || it in shifted }
                            .map { "${apk.name}!${entry.name}: $it" }
                    }
            }
        }
        check(found.isEmpty()) {
            "developer tools in a customer build:\n" + found.joinToString("\n")
        }
        logger.lifecycle("No developer tools in ${apks.joinToString { it.name }}")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReleaseHasNoDeveloperTools)
}

// Manifest, backup-rule and release-library privacy checks; see the script.
apply(from = rootProject.file("gradle/privacy.gradle.kts"))

ktlint {
    version.set(libs.versions.ktlint.get())
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}
