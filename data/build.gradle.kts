import java.io.File
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.software.sello.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // The exported schemas are the migration harness's input on the device.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    // Lint stays at zero warnings. The version-update notices depend on the network, so they
    // would turn the gate red whenever upstream publishes; dependency updates are decided by hand.
    lint {
        warningsAsErrors = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

/*
 * Room reads and writes the exported schemas through this directory. As a plain
 * "$projectDir/schemas" argument, KSP's cache key would include the checkout's absolute
 * path and every new worktree would miss the build cache. Declared as a relative input,
 * the key depends on the schemas' content only.
 */
class RoomSchemaArgs(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDirectory: File
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("room.schemaLocation=${schemaDirectory.path}")
}

ksp {
    arg(RoomSchemaArgs(File(projectDir, "schemas")))
}

// ExportedSchemaTest reads these files directly, beyond the compiled test classpath.
tasks.withType<Test>().configureEach {
    inputs.dir("schemas")
        .withPropertyName("exportedSchemas")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(fileTree("src/main") { include("**/*.kt") })
        .withPropertyName("storageSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
}

ktlint {
    version.set(libs.versions.ktlint.get())
}

dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}
