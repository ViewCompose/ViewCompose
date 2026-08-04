package com.viewcompose.publishing

import groovy.json.JsonSlurper
import java.io.File

internal enum class ReleaseImpact(val rank: Int) {
    Dependency(0),
    Fix(1),
    Feature(2),
    Breaking(3),
    ;

    companion object {
        fun direct(value: String): ReleaseImpact = when (value) {
            "fix" -> Fix
            "feature" -> Feature
            "breaking" -> Breaking
            else -> error(
                "Unsupported release impact '$value'. Use breaking, feature, or fix; " +
                    "dependency is derived by the release planner.",
            )
        }
    }

    fun encoded(): String = name.lowercase()
}

internal data class DeclaredReleaseChange(
    val artifact: String,
    val impact: ReleaseImpact,
)

internal data class IgnoredReleaseChange(
    val artifact: String,
    val reason: String,
)

internal data class IgnoredSharedReleasePath(
    val path: String,
    val reason: String,
)

internal data class ReleaseChangeSet(
    val file: File,
    val summary: String,
    val changes: List<DeclaredReleaseChange>,
    val ignored: List<IgnoredReleaseChange>,
    val shared: List<IgnoredSharedReleasePath>,
)

internal object ReleaseChangeSetParser {
    private val fileNamePattern = Regex("[a-z0-9][a-z0-9-]{2,80}\\.json")
    private val allowedRootKeys = setOf("schemaVersion", "summary", "changes", "ignored", "shared")

    fun parse(file: File, knownArtifacts: Set<String>): ReleaseChangeSet {
        check(fileNamePattern.matches(file.name)) {
            "Changeset '${file.name}' must use a unique lowercase kebab-case name."
        }
        val root = JsonSlurper().parse(file) as? Map<*, *>
            ?: error("Changeset '${file.path}' must contain one JSON object.")
        val unknownRootKeys = root.keys.filterIsInstance<String>().toSet() - allowedRootKeys
        check(unknownRootKeys.isEmpty()) {
            "Changeset '${file.name}' has unknown fields: ${unknownRootKeys.sorted()}."
        }
        check((root["schemaVersion"] as? Number)?.toInt() == 1) {
            "Changeset '${file.name}' must use schemaVersion 1."
        }
        val summary = (root["summary"] as? String)?.trim().orEmpty()
        check(summary.length in 8..240) {
            "Changeset '${file.name}' summary must contain 8..240 characters."
        }
        val changes = root.objectList("changes", file).map { entry ->
            entry.requireOnly(setOf("artifact", "impact"), "changes", file)
            val artifact = entry.requiredString("artifact", file)
            check(artifact in knownArtifacts) {
                "Changeset '${file.name}' declares unknown artifact '$artifact'."
            }
            DeclaredReleaseChange(
                artifact = artifact,
                impact = ReleaseImpact.direct(entry.requiredString("impact", file)),
            )
        }
        val ignored = root.objectList("ignored", file).map { entry ->
            entry.requireOnly(setOf("artifact", "reason"), "ignored", file)
            val artifact = entry.requiredString("artifact", file)
            check(artifact in knownArtifacts) {
                "Changeset '${file.name}' ignores unknown artifact '$artifact'."
            }
            IgnoredReleaseChange(
                artifact = artifact,
                reason = entry.requiredReason(file),
            )
        }
        val shared = root.objectList("shared", file).map { entry ->
            entry.requireOnly(setOf("path", "reason"), "shared", file)
            val path = entry.requiredString("path", file).normalizeRepositoryPath()
            check(path.isNotEmpty() && !path.startsWith("../") && !path.startsWith('/')) {
                "Changeset '${file.name}' has invalid shared path '$path'."
            }
            IgnoredSharedReleasePath(path = path, reason = entry.requiredReason(file))
        }
        val duplicateArtifacts = (changes.map(DeclaredReleaseChange::artifact) +
            ignored.map(IgnoredReleaseChange::artifact))
            .groupingBy(String::toString)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        check(duplicateArtifacts.isEmpty()) {
            "Changeset '${file.name}' classifies artifacts more than once: " +
                duplicateArtifacts.sorted().joinToString()
        }
        val duplicateShared = shared.map(IgnoredSharedReleasePath::path)
            .groupingBy(String::toString)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        check(duplicateShared.isEmpty()) {
            "Changeset '${file.name}' classifies shared paths more than once: " +
                duplicateShared.sorted().joinToString()
        }
        check(changes.isNotEmpty() || ignored.isNotEmpty() || shared.isNotEmpty()) {
            "Changeset '${file.name}' must classify at least one artifact or shared path."
        }
        return ReleaseChangeSet(file, summary, changes, ignored, shared)
    }

    private fun Map<*, *>.objectList(name: String, file: File): List<Map<*, *>> {
        val value = this[name] ?: return emptyList()
        val list = value as? List<*>
            ?: error("Changeset '${file.name}' field '$name' must be an array.")
        return list.mapIndexed { index, item ->
            item as? Map<*, *>
                ?: error("Changeset '${file.name}' field '$name[$index]' must be an object.")
        }
    }

    private fun Map<*, *>.requiredString(name: String, file: File): String {
        val value = (this[name] as? String)?.trim().orEmpty()
        check(value.isNotEmpty()) {
            "Changeset '${file.name}' requires a non-empty '$name'."
        }
        return value
    }

    private fun Map<*, *>.requireOnly(allowed: Set<String>, field: String, file: File) {
        val unknown = keys.filterIsInstance<String>().toSet() - allowed
        check(unknown.isEmpty()) {
            "Changeset '${file.name}' has unknown $field fields: ${unknown.sorted()}."
        }
    }

    private fun Map<*, *>.requiredReason(file: File): String {
        val reason = requiredString("reason", file)
        check(reason.length in 12..320) {
            "Changeset '${file.name}' reasons must contain 12..320 characters."
        }
        return reason
    }
}

internal data class ReleaseOwnershipResult(
    val artifactPaths: Map<String, List<String>>,
    val sharedPaths: List<String>,
)

internal object ReleaseOwnership {
    private val releaseSourcePrefixes = listOf(
        "src/main/",
        "src/commonMain/",
        "src/androidMain/",
        "src/jvmMain/",
        "src/release/",
        "src/test/samples/",
    )
    private val releaseModuleFiles = setOf(
        "build.gradle.kts",
        "build.gradle",
        "consumer-rules.pro",
        "proguard-rules.pro",
        "lint.xml",
    )
    private val sharedPathsRequiringIntent = setOf(
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
        "gradle/libs.versions.toml",
    )

    fun classify(changedPaths: Collection<String>, artifacts: Set<String>): ReleaseOwnershipResult {
        val normalized = changedPaths.map(String::normalizeRepositoryPath).distinct().sorted()
        val artifactPaths = artifacts.sorted().associateWith { artifact ->
            normalized.filter { path ->
                if (!path.startsWith("$artifact/")) return@filter false
                val relative = path.removePrefix("$artifact/")
                relative in releaseModuleFiles || releaseSourcePrefixes.any(relative::startsWith)
            }
        }.filterValues(List<String>::isNotEmpty)
        return ReleaseOwnershipResult(
            artifactPaths = artifactPaths,
            sharedPaths = normalized.filter(sharedPathsRequiringIntent::contains),
        )
    }
}

internal data class ReleaseIntentVerification(
    val affectedArtifacts: Set<String>,
    val ignoredArtifacts: Set<String>,
    val sharedPaths: Set<String>,
)

internal object ReleaseIntentVerifier {
    fun verify(
        ownership: ReleaseOwnershipResult,
        changeSets: List<ReleaseChangeSet>,
    ): ReleaseIntentVerification {
        val declared = changeSets.flatMap(ReleaseChangeSet::changes)
            .map(DeclaredReleaseChange::artifact)
            .toSet()
        val ignored = changeSets.flatMap(ReleaseChangeSet::ignored)
            .map(IgnoredReleaseChange::artifact)
            .toSet()
        val staleIgnored = ignored - ownership.artifactPaths.keys
        check(staleIgnored.isEmpty()) {
            "Changesets ignore artifacts without detected publication changes: " +
                staleIgnored.sorted().joinToString()
        }
        val classifiedArtifacts = declared + ignored
        val missingArtifacts = ownership.artifactPaths.keys - classifiedArtifacts
        check(missingArtifacts.isEmpty()) {
            buildString {
                appendLine("Published artifact changes are missing release intent:")
                missingArtifacts.sorted().forEach { artifact ->
                    appendLine("- $artifact")
                    ownership.artifactPaths.getValue(artifact).forEach { path ->
                        appendLine("  - $path")
                    }
                }
                append("Add one immutable release/changes/<unique>.json file for this PR.")
            }
        }
        val ignoredShared = changeSets.flatMap(ReleaseChangeSet::shared)
            .map(IgnoredSharedReleasePath::path)
            .toSet()
        val missingShared = ownership.sharedPaths.filter { path ->
            path !in ignoredShared && changeSets.none { changeSet -> changeSet.changes.isNotEmpty() }
        }
        check(missingShared.isEmpty()) {
            "Shared build inputs require explicit release intent: ${missingShared.joinToString()}. " +
                "Declare affected artifacts, or add a shared entry with a concrete no-release reason."
        }
        val staleShared = ignoredShared - ownership.sharedPaths.toSet()
        check(staleShared.isEmpty()) {
            "Changesets classify unchanged shared paths: ${staleShared.sorted().joinToString()}."
        }
        return ReleaseIntentVerification(declared, ignored, ignoredShared)
    }
}

internal fun String.normalizeRepositoryPath(): String =
    replace('\\', '/').removePrefix("./").trim('/')
