/*
 * Privacy checks on what is actually built (SELLO-032). They read the merged manifest
 * of each variant, the backup rule files it names and the release runtime classpath,
 * not the source manifests, so a library or a later edit cannot add a permission,
 * a provider, an exported component or a system backup without failing the build.
 * Each allowed entry says why it is allowed. Reports go to build/reports/privacy.
 *
 * Everything a task needs at execution time is a local value or function, as the
 * configuration cache requires.
 */

fun registerManifestCheck(variant: String, applicationId: String) =
    tasks.register("verify${variant.replaceFirstChar(Char::uppercase)}ManifestPrivacy") {
        group = "verification"
        description = "Checks the merged $variant manifest against the local-only promise."
        val capitalized = variant.replaceFirstChar(Char::uppercase)
        val manifest = layout.buildDirectory.file(
            "intermediates/merged_manifest/$variant/process${capitalized}MainManifest/" +
                "AndroidManifest.xml"
        )
        val rules = layout.projectDirectory.dir("src/main/res/xml")
        val report = layout.buildDirectory.file("reports/privacy/manifest-$variant.txt")
        val release = variant == "release"
        inputs.file(manifest)
        inputs.dir(rules)
        outputs.file(report)
        doLast {
            val androidNs = "http://schemas.android.com/apk/res/android"
            // Every storage domain Android can back up or transfer; each must be excluded.
            val storageDomains = listOf(
                "root", "file", "database", "sharedpref", "external",
                "device_root", "device_file", "device_database", "device_sharedpref"
            )
            // Permissions a build may request, with why. Nothing else, dangerous or not.
            val allowedPermissions = mapOf(
                "$applicationId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" to
                    "AndroidX core: keeps dynamic receivers private; signature protection"
            )
            // Components another app may start, with why.
            val exportedInRelease = mapOf(
                "com.software.sello.MainActivity" to
                    "the launcher entry and the sello://anotar draft link",
                "androidx.profileinstaller.ProfileInstallReceiver" to
                    "AndroidX start-up profiles; callable only with DUMP (adb or the system)"
            )
            val exportedInDebugOnly = mapOf(
                "com.software.sello.devtools.ToolsActivity" to
                    "Sello Herramientas launcher, debug only",
                "androidx.compose.ui.tooling.PreviewActivity" to
                    "Compose previews, debugImplementation",
                "androidx.activity.ComponentActivity" to
                    "Compose UI test host, debugImplementation"
            )
            // Providers a build may declare, with why. None may be exported or grant URIs,
            // except the export provider, which grants read access to one shared file.
            val allowedProviders = mapOf(
                "androidx.startup.InitializationProvider" to
                    "AndroidX start-up initializers; not exported",
                "androidx.core.content.FileProvider" to
                    "exports the person shares (SELLO-121); not exported, only cache/exports/"
            )
            val grantingProviders = mapOf(
                "androidx.core.content.FileProvider" to "$applicationId.exports"
            )
            val exportPaths = rules.file("export_paths.xml")

            fun org.w3c.dom.Element.attr(name: String): String? =
                getAttributeNS(androidNs, name).takeIf { it.isNotEmpty() }

            fun org.w3c.dom.Element.elements(tag: String): List<org.w3c.dom.Element> {
                val nodes = getElementsByTagName(tag)
                return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            }

            fun parse(file: File): org.w3c.dom.Element =
                javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .apply { isNamespaceAware = true }
                    .newDocumentBuilder().parse(file).documentElement

            // A section must exclude every domain and include none.
            fun backupRuleProblems(file: File, sections: List<String>): List<String> {
                val root = parse(file)
                return sections.flatMap { section ->
                    val scope = if (section == root.tagName) listOf(root) else root.elements(section)
                    if (scope.isEmpty()) return@flatMap listOf("${file.name}: no <$section>")
                    scope.flatMap { element ->
                        val excluded = element.elements("exclude")
                            .filter { it.getAttribute("path") == "." }
                            .map { it.getAttribute("domain") }
                        val included = element.elements("include").map { it.getAttribute("domain") }
                        storageDomains.filterNot { it in excluded }
                            .map { "${file.name} <$section>: domain '$it' is not excluded" } +
                            included.map { "${file.name} <$section>: domain '$it' is included" }
                    }
                }
            }

            val root = parse(manifest.get().asFile)
            val application = root.elements("application").single()
            val problems = mutableListOf<String>()
            val lines = mutableListOf("Merged $variant manifest of $applicationId", "")

            if (application.attr("allowBackup") != "false") problems += "allowBackup is not false"
            if (application.attr("dataExtractionRules") != "@xml/data_extraction_rules") {
                problems += "dataExtractionRules is not @xml/data_extraction_rules"
            }
            if (application.attr("fullBackupContent") != "@xml/backup_rules") {
                problems += "fullBackupContent is not @xml/backup_rules"
            }
            if (application.attr("usesCleartextTraffic") == "true") problems += "cleartext traffic"
            if (application.attr("requestLegacyExternalStorage") == "true") {
                problems += "legacy external storage"
            }
            problems += backupRuleProblems(
                rules.file("data_extraction_rules.xml").asFile,
                listOf("cloud-backup", "device-transfer")
            )
            problems += backupRuleProblems(
                rules.file("backup_rules.xml").asFile,
                listOf("full-backup-content")
            )
            lines += "Backup: allowBackup=false; every domain excluded from cloud backup, " +
                "device transfer and Android 11 full backup."

            lines += ""
            lines += "Permissions requested:"
            for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
                for (element in root.elements(tag)) {
                    val name = element.attr("name").orEmpty()
                    val why = allowedPermissions[name]
                    if (why == null) problems += "permission not allowed: $name"
                    lines += "  $name: ${why ?: "NOT ALLOWED"}"
                }
            }

            val exported = if (release) exportedInRelease else exportedInRelease + exportedInDebugOnly
            lines += ""
            lines += "Components (an exported one needs a reason):"
            for (tag in listOf("activity", "activity-alias", "service", "receiver", "provider")) {
                for (element in application.elements(tag)) {
                    val name = element.attr("name").orEmpty()
                    val isExported = element.attr("exported") == "true"
                    if (tag == "provider") {
                        val why = allowedProviders[name]
                        if (why == null) problems += "provider not allowed: $name"
                        if (isExported) problems += "provider is exported: $name"
                        if (element.attr("grantUriPermissions") == "true") {
                            val authority = grantingProviders[name]
                            if (authority == null) {
                                problems += "provider grants URI permissions: $name"
                            } else if (element.attr("authorities") != authority) {
                                problems += "provider $name has authority " +
                                    "${element.attr("authorities")}, not $authority"
                            }
                        }
                        lines += "  provider $name exported=$isExported: ${why ?: "NOT ALLOWED"}"
                        continue
                    }
                    val why = exported[name]
                    if (isExported && why == null) {
                        problems += "$tag exported without a reason: $name"
                    }
                    lines += "  $tag $name exported=$isExported" +
                        (element.attr("permission")?.let { " permission=$it" } ?: "") +
                        (if (isExported) ": ${why ?: "NOT ALLOWED"}" else "")
                }
            }

            // The export provider may serve one folder of the cache and nothing else.
            val paths = parse(exportPaths.asFile)
            val served = (0 until paths.childNodes.length).map { paths.childNodes.item(it) }
                .filterIsInstance<org.w3c.dom.Element>()
                .map { "${it.tagName}:${it.getAttribute("path")}" }
            if (served != listOf("cache-path:exports/")) {
                problems += "export_paths.xml serves $served, not only cache-path:exports/"
            }
            lines += "Export provider paths: $served"

            lines += ""
            lines += if (problems.isEmpty()) "PASS" else "FAIL"
            lines += problems.map { "  $it" }
            val text = lines.joinToString("\n")
            report.get().asFile.apply { parentFile.mkdirs() }.writeText(text + "\n")
            check(problems.isEmpty()) { "The $variant manifest breaks the privacy rules:\n$text" }
            logger.lifecycle("Manifest privacy holds for $variant: ${report.get().asFile}")
        }
    }

val verifyReleaseManifestPrivacy = registerManifestCheck("release", "com.software.sello")
val verifyDebugManifestPrivacy = registerManifestCheck("debug", "com.software.sello.debug")

/*
 * What a customer build carries: no test, tooling or test-manifest library may be on
 * the release runtime classpath. The full list is written so it can be reviewed.
 */
val verifyReleaseDependencies by tasks.registering {
    group = "verification"
    description = "Lists the release runtime libraries and refuses test or tooling ones."
    val rootComponent = configurations.named("releaseRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }
    val report = layout.buildDirectory.file("reports/privacy/release-dependencies.txt")
    inputs.property("modules", rootComponent.map { root ->
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val component = queue.removeFirst()
            component.dependencies
                .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                .map { it.selected }
                .forEach { selected ->
                    val id = selected.moduleVersion?.let { "${it.group}:${it.name}:${it.version}" }
                        ?: selected.id.displayName
                    if (seen.add(id)) queue.add(selected)
                }
        }
        seen.sorted()
    })
    outputs.file(report)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val modules = inputs.properties["modules"] as List<String>
        val forbidden = listOf(
            "junit", "espresso", "androidx.test", "ui-test", "ui-tooling:", "ui-tooling-data",
            "koin-test", "kotlinx-coroutines-test", "mockk", "leakcanary"
        )
        val found = modules.filter { module -> forbidden.any { it in module } }
        val verdict = if (found.isEmpty()) "PASS" else "FAIL: $found"
        report.get().asFile.apply { parentFile.mkdirs() }
            .writeText((modules + "" + verdict).joinToString("\n") + "\n")
        check(found.isEmpty()) { "test or tooling libraries in the release build: $found" }
        logger.lifecycle("${modules.size} release libraries, none for tests or tooling")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReleaseManifestPrivacy, verifyReleaseDependencies)
}
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(verifyDebugManifestPrivacy)
}
