package com.viewcompose.publishing

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

internal data class CommandResult(
    val exitCode: Int,
    val output: String,
)

internal fun interface CommandExecutor {
    fun execute(arguments: List<String>): CommandResult
}

internal class GitRepository(
    private val root: File,
    private val executor: CommandExecutor = CommandExecutor { arguments ->
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { input -> input.copyTo(output) }
        CommandResult(
            exitCode = process.waitFor(),
            output = output.toString(StandardCharsets.UTF_8.name()),
        )
    },
) {
    fun resolveVerificationBase(explicit: String?): String {
        if (!explicit.isNullOrBlank()) return revision(explicit)
        val originMain = executeOrNull("rev-parse", "--verify", "origin/main")?.trim()
        if (!originMain.isNullOrEmpty()) {
            return execute("merge-base", "HEAD", originMain).trim()
        }
        return execute("rev-parse", "HEAD^").trim()
    }

    fun revision(value: String): String = execute("rev-parse", "--verify", "$value^{commit}").trim()

    fun head(): String = revision("HEAD")

    fun requireClean() {
        val status = execute("status", "--porcelain", "--untracked-files=all")
        check(status.isBlank()) {
            "Release planning requires a clean worktree. Commit or stash these paths first:\n$status"
        }
    }

    fun changedPaths(base: String, includeWorkingTree: Boolean): List<String> {
        val range = if (includeWorkingTree) base else "$base..HEAD"
        val tracked = execute("diff", "--name-only", "-z", range, "--")
            .split('\u0000')
            .filter(String::isNotBlank)
        if (!includeWorkingTree) return tracked.distinct().sorted()
        val untracked = execute("ls-files", "--others", "--exclude-standard", "-z")
            .split('\u0000')
            .filter(String::isNotBlank)
        return (tracked + untracked).distinct().sorted()
    }

    fun changeSetStatuses(base: String): Map<String, Char> {
        val tracked = execute(
            "diff",
            "--name-status",
            "--find-renames",
            base,
            "--",
            "release/changes",
        ).lineSequence().filter(String::isNotBlank).associate { line ->
            val fields = line.split('\t')
            val status = fields.first().first()
            val path = fields.last().normalizeRepositoryPath()
            path to status
        }
        val untracked = execute(
            "ls-files",
            "--others",
            "--exclude-standard",
            "release/changes/*.json",
        ).lineSequence().filter(String::isNotBlank).associateWith { 'A' }
        return tracked + untracked
    }

    fun changedPathsBetween(base: String, head: String): List<String> =
        execute("diff", "--name-only", "-z", "$base..$head", "--")
            .split('\u0000')
            .filter(String::isNotBlank)
            .distinct()
            .sorted()

    fun tagsFor(artifact: String): List<String> =
        execute("tag", "--list", "maven/$artifact/*")
            .lineSequence()
            .filter(String::isNotBlank)
            .toList()

    fun annotatedTag(tag: String): MavenReleaseTag {
        val content = execute("cat-file", "-p", "refs/tags/$tag")
        check("-----BEGIN PGP SIGNATURE-----" in content) {
            "Release tag '$tag' is not a signed annotated tag."
        }
        val verification = command("tag", "-v", tag)
        check(verification.exitCode == 0) {
            "Release tag '$tag' did not pass local trust verification. Import the ViewCompose " +
                "release public key before planning.\n${verification.output}"
        }
        val sourceRevision = releaseTagSourceRevision(
            tag = tag,
            annotation = content,
        )
        val version = tag.substringAfterLast('/')
        val artifact = tag.removePrefix("maven/").substringBeforeLast('/')
        return MavenReleaseTag(
            name = tag,
            artifact = artifact,
            version = MavenVersion.parse(version),
            sourceRevision = revision(sourceRevision),
            releaseRevision = revision(tag),
        )
    }

    private fun command(vararg arguments: String): CommandResult = executor.execute(arguments.toList())

    private fun execute(vararg arguments: String): String {
        val result = command(*arguments)
        check(result.exitCode == 0) {
            "Git command failed: git ${arguments.joinToString(" ")}\n${result.output}"
        }
        return result.output
    }

    private fun executeOrNull(vararg arguments: String): String? {
        val result = command(*arguments)
        return result.output.takeIf { result.exitCode == 0 }
    }
}

internal fun releaseTagSourceRevision(tag: String, annotation: String): String {
    val matches = Regex("(?:^|[;\\s])sourceRevision=([a-f0-9]{40})(?=$|[;\\s])")
        .findAll(annotation)
        .toList()
    check(matches.size == 1) {
        "Release tag '$tag' must declare exactly one sourceRevision=<full lowercase SHA> token."
    }
    return matches.single().groupValues[1]
}

internal data class MavenVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val qualifier: String?,
) : Comparable<MavenVersion> {
    override fun compareTo(other: MavenVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (qualifier == null && other.qualifier != null) return 1
        if (qualifier != null && other.qualifier == null) return -1
        return compareQualifier(qualifier.orEmpty(), other.qualifier.orEmpty())
    }

    fun recommend(impact: ReleaseImpact): MavenVersion {
        if (qualifier != null) {
            val suffix = Regex("^(.*?)([0-9]+)$").matchEntire(qualifier)
            val nextQualifier = if (suffix == null) {
                "${qualifier}01"
            } else {
                val prefix = suffix.groupValues[1]
                val digits = suffix.groupValues[2]
                prefix + (digits.toInt() + 1).toString().padStart(digits.length, '0')
            }
            return copy(qualifier = nextQualifier)
        }
        return when (impact) {
            ReleaseImpact.Breaking -> if (major == 0) {
                MavenVersion(major, minor + 1, 0, null)
            } else {
                MavenVersion(major + 1, 0, 0, null)
            }
            ReleaseImpact.Feature -> MavenVersion(major, minor + 1, 0, null)
            ReleaseImpact.Fix,
            ReleaseImpact.Dependency,
            -> MavenVersion(major, minor, patch + 1, null)
        }
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        qualifier?.let { append("-$it") }
    }

    companion object {
        private val pattern = Regex("([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-([0-9A-Za-z.-]+))?")

        fun parse(value: String): MavenVersion {
            val match = pattern.matchEntire(value) ?: error("Invalid Maven version '$value'.")
            return MavenVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                qualifier = match.groupValues[4].ifEmpty { null },
            )
        }

        private fun compareQualifier(left: String, right: String): Int {
            val tokenPattern = Regex("[A-Za-z]+|[0-9]+")
            val leftParts = tokenPattern.findAll(left).map { it.value }.toList()
            val rightParts = tokenPattern.findAll(right).map { it.value }.toList()
            repeat(maxOf(leftParts.size, rightParts.size)) { index ->
                val leftPart = leftParts.getOrNull(index) ?: return -1
                val rightPart = rightParts.getOrNull(index) ?: return 1
                val comparison = if (leftPart.all(Char::isDigit) && rightPart.all(Char::isDigit)) {
                    leftPart.toInt().compareTo(rightPart.toInt())
                } else {
                    leftPart.compareTo(rightPart)
                }
                if (comparison != 0) return comparison
            }
            return 0
        }
    }
}

internal data class MavenReleaseTag(
    val name: String,
    val artifact: String,
    val version: MavenVersion,
    val sourceRevision: String,
    val releaseRevision: String,
)

internal data class PlannedArtifactRelease(
    val artifact: String,
    val currentVersion: MavenVersion,
    val recommendedVersion: MavenVersion,
    val impact: ReleaseImpact,
    val reason: String,
    val baselineTag: String,
    val baselineSourceRevision: String,
    val changeSets: List<String>,
    val changedDependencies: List<String>,
)

internal data class ViewComposeReleasePlan(
    val sourceRevision: String,
    val releases: List<PlannedArtifactRelease>,
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"sourceRevision\": \"$sourceRevision\",")
        appendLine("  \"releases\": [")
        releases.forEachIndexed { index, release ->
            appendLine("    {")
            appendLine("      \"artifact\": \"${release.artifact}\",")
            appendLine("      \"currentVersion\": \"${release.currentVersion}\",")
            appendLine("      \"recommendedVersion\": \"${release.recommendedVersion}\",")
            appendLine("      \"impact\": \"${release.impact.encoded()}\",")
            appendLine("      \"reason\": \"${release.reason}\",")
            appendLine("      \"baselineTag\": \"${release.baselineTag}\",")
            appendLine("      \"baselineSourceRevision\": \"${release.baselineSourceRevision}\",")
            appendLine("      \"changesets\": ${release.changeSets.toJsonArray()},")
            appendLine("      \"changedDependencies\": ${release.changedDependencies.toJsonArray()}")
            append("    }")
            appendLine(if (index == releases.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("# ViewCompose release plan")
        appendLine()
        appendLine("Frozen source revision: `$sourceRevision`")
        appendLine()
        if (releases.isEmpty()) {
            appendLine("No Maven artifact requires publication.")
            return@buildString
        }
        appendLine("| Artifact | Current | Recommended | Impact | Reason |")
        appendLine("| --- | --- | --- | --- | --- |")
        releases.forEach { release ->
            appendLine(
                "| `${release.artifact}` | `${release.currentVersion}` | " +
                    "`${release.recommendedVersion}` | `${release.impact.encoded()}` | " +
                    "${release.reason} |",
            )
        }
        appendLine()
        appendLine("Confirm exact versions before running `prepareViewComposeRelease`.")
    }

    private fun List<String>.toJsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { value ->
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
}

internal class ViewComposeReleasePlanner(
    private val root: File,
    private val git: GitRepository,
    private val artifacts: Set<String>,
    private val dependencies: Map<String, Set<String>>,
) {
    fun plan(): ViewComposeReleasePlan {
        git.requireClean()
        val head = git.head()
        val baselines = artifacts.associateWith { artifact ->
            git.tagsFor(artifact)
                .map(git::annotatedTag)
                .maxByOrNull(MavenReleaseTag::version)
                ?: error(
                    "No release tag exists for '$artifact'. Fetch tags or establish its first " +
                        "maven/$artifact/<version> release boundary.",
                )
        }
        val direct = linkedMapOf<String, Pair<ReleaseImpact, MutableSet<String>>>()
        baselines.forEach { (artifact, baseline) ->
            val changeSetPaths = git.changedPathsBetween(baseline.sourceRevision, head)
                .filter { path ->
                    path.startsWith("release/changes/") && path.endsWith(".json")
                }
            val changeSets = changeSetPaths.map { path ->
                ReleaseChangeSetParser.parse(root.resolve(path), artifacts)
            }
            val declarations = changeSets.flatMap { changeSet ->
                changeSet.changes.filter { change -> change.artifact == artifact }
                    .map { change -> change to changeSet.file.name }
            }
            val ownedPaths = ReleaseOwnership.classify(
                git.changedPathsBetween(baseline.sourceRevision, head),
                artifacts,
            ).artifactPaths[artifact].orEmpty()
            check(ownedPaths.isEmpty() || declarations.isNotEmpty()) {
                buildString {
                    appendLine("Artifact '$artifact' changed after ${baseline.name} without a changeset:")
                    ownedPaths.forEach { appendLine("- $it") }
                }
            }
            if (declarations.isNotEmpty()) {
                val impact = declarations.maxBy { (change, _) -> change.impact.rank }.first.impact
                direct[artifact] = impact to declarations.mapTo(linkedSetOf()) { (_, file) -> file }
            }
        }
        val releaseReasons = propagateReleaseDependencies(direct.keys, dependencies)
        val releases = releaseReasons.keys.sorted().map { artifact ->
            val baseline = baselines.getValue(artifact)
            val directDeclaration = direct[artifact]
            val changedDependencies = releaseReasons.getValue(artifact).sorted()
            val impact = directDeclaration?.first ?: ReleaseImpact.Dependency
            PlannedArtifactRelease(
                artifact = artifact,
                currentVersion = baseline.version,
                recommendedVersion = baseline.version.recommend(impact),
                impact = impact,
                reason = if (directDeclaration != null) "direct changeset" else "dependency propagation",
                baselineTag = baseline.name,
                baselineSourceRevision = baseline.sourceRevision,
                changeSets = directDeclaration?.second?.sorted().orEmpty(),
                changedDependencies = changedDependencies,
            )
        }
        return ViewComposeReleasePlan(head, releases)
    }

    companion object {
        fun buildReverseDependencies(
            dependencies: Map<String, Set<String>>,
        ): Map<String, Set<String>> {
            val reverse = linkedMapOf<String, MutableSet<String>>()
            dependencies.forEach { (dependent, requirements) ->
                requirements.forEach { requirement ->
                    reverse.getOrPut(requirement) { linkedSetOf() }.add(dependent)
                }
            }
            return reverse.mapValues { (_, dependents) -> dependents.toSet() }
        }

        fun propagateReleaseDependencies(
            directArtifacts: Set<String>,
            dependencies: Map<String, Set<String>>,
        ): Map<String, Set<String>> {
            val reverse = buildReverseDependencies(dependencies)
            val releaseReasons = directArtifacts
                .associateWith { linkedSetOf<String>() }
                .toMutableMap()
            val queue = ArrayDeque(directArtifacts.sorted())
            while (queue.isNotEmpty()) {
                val changed = queue.removeFirst()
                reverse[changed].orEmpty().sorted().forEach { dependent ->
                    val reasons = releaseReasons.getOrPut(dependent) { linkedSetOf() }
                    if (reasons.add(changed)) queue.addLast(dependent)
                }
            }
            return releaseReasons.mapValues { (_, reasons) -> reasons.toSet() }
        }
    }
}

internal object ReleaseMetadataPreparer {
    fun parseConfirmedVersions(value: String): Map<String, MavenVersion> {
        check(value.isNotBlank()) {
            "Confirm exact versions with " +
                "-PviewComposeReleaseVersions=artifact=version,artifact=version."
        }
        val entries = value.split(',').map { entry ->
            val fields = entry.split('=', limit = 2).map(String::trim)
            check(fields.size == 2 && fields.all(String::isNotEmpty)) {
                "Invalid confirmed release version '$entry'."
            }
            fields[0] to MavenVersion.parse(fields[1])
        }
        check(entries.map { entry -> entry.first }.distinct().size == entries.size) {
            "Confirmed release versions contain duplicate artifacts."
        }
        return entries.toMap()
    }

    fun prepare(
        planFile: File,
        publishingFile: File,
        historyFile: File,
        confirmedVersions: Map<String, MavenVersion>,
    ) {
        val plan = JsonSlurper().parse(planFile) as Map<*, *>
        val sourceRevision = plan["sourceRevision"] as? String
            ?: error("Release plan is missing sourceRevision.")
        val releases = (plan["releases"] as? List<*>)?.map { item ->
            item as Map<*, *>
        }.orEmpty()
        val planned = releases.associate { release ->
            (release["artifact"] as? String
                ?: error("Release plan entry is missing artifact.")) to MavenVersion.parse(
                release["currentVersion"] as? String
                    ?: error("Release plan entry is missing currentVersion."),
            )
        }
        check(confirmedVersions.keys == planned.keys) {
            "Confirmed release modules must exactly match the plan. Planned: " +
                "${planned.keys.sorted()}; confirmed: ${confirmedVersions.keys.sorted()}."
        }
        confirmedVersions.forEach { (artifact, version) ->
            check(version > planned.getValue(artifact)) {
                "Confirmed version '$artifact:$version' must be newer than ${planned.getValue(artifact)}."
            }
        }
        var publishing = publishingFile.readText()
        confirmedVersions.toSortedMap().forEach { (artifact, version) ->
            publishing = publishing.replaceRequiredProperty(
                "module.$artifact.version",
                version.toString(),
            ).replaceRequiredProperty(
                "module.$artifact.sourceRevision",
                sourceRevision,
            )
        }
        var history = historyFile.readText()
        val count = Regex("(?m)^release\\.count=([0-9]+)$")
            .find(history)?.groupValues?.get(1)?.toInt()
            ?: error("Documentation history is missing release.count.")
        val groups = confirmedVersions.entries.groupBy(Map.Entry<String, MavenVersion>::value)
            .toSortedMap()
        history = history.replaceRequiredProperty("release.count", (count + groups.size).toString())
            .trimEnd() + "\n"
        groups.entries.forEachIndexed { offset, (version, entries) ->
            val index = count + offset
            history += buildString {
                appendLine()
                appendLine("release.$index.version=$version")
                appendLine("release.$index.sourceRevision=$sourceRevision")
                appendLine(
                    "release.$index.modules=" +
                        entries.map(Map.Entry<String, MavenVersion>::key).sorted().joinToString(","),
                )
            }
        }
        publishingFile.writeText(publishing)
        historyFile.writeText(history)
    }

    private fun String.replaceRequiredProperty(key: String, value: String): String {
        val pattern = Regex("(?m)^${Regex.escape(key)}=.*$")
        check(pattern.containsMatchIn(this)) { "Missing release property '$key'." }
        return replace(pattern, "$key=$value")
    }
}
