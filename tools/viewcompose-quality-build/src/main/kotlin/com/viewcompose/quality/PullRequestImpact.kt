package com.viewcompose.quality

import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties

internal const val DEFAULT_MAX_SCOPED_CHANGED_FILES = 300

internal enum class PullRequestGateFamily(val encoded: String) {
    ApiDocumentation("api-documentation"),
    Demo("demo"),
    DeviceAndBenchmark("device-and-benchmark"),
    DocumentationGovernance("documentation-governance"),
    DocumentationSite("documentation-site"),
    IntegrationTests("integration-tests"),
    ModuleVerification("module-verification"),
    Preview("preview"),
    ReleaseIntent("release-intent"),
    Samples("samples"),
}

internal enum class PullRequestQaQuickMode(val encoded: String) {
    Skip("skip"),
    Complete("complete"),
    AffectedWithShadow("affected-with-shadow"),
    Affected("affected"),
}

internal data class PullRequestPathChange(
    val status: Char,
    val paths: List<String>,
) {
    init {
        require(paths.isNotEmpty()) { "A changed-path record must contain at least one path." }
    }
}

internal data class PullRequestWorkflowSelection(
    val qaQuickMode: PullRequestQaQuickMode,
    val qaPreview: Boolean,
    val documentation: Boolean,
) {
    val qaQuick: Boolean
        get() = qaQuickMode != PullRequestQaQuickMode.Skip
}

internal data class PullRequestImpactPlan(
    val baseRevision: String,
    val headRevision: String,
    val changedFiles: Int,
    val fullFallback: Boolean,
    val fullFallbackReasons: List<String>,
    val gateFamilies: Set<PullRequestGateFamily>,
    val directArtifacts: Set<String>,
    val directProjects: Set<String>,
    val dependencyClosure: Set<String>,
    val reverseDependentClosure: Set<String>,
    val reasons: List<String>,
    val workflows: PullRequestWorkflowSelection,
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 3,")
        appendLine("  \"baseRevision\": ${baseRevision.jsonString()},")
        appendLine("  \"headRevision\": ${headRevision.jsonString()},")
        appendLine("  \"changedFiles\": $changedFiles,")
        appendLine("  \"fullFallback\": $fullFallback,")
        appendLine("  \"fullFallbackReasons\": ${fullFallbackReasons.jsonArray()},")
        appendLine(
            "  \"selectedGateFamilies\": " +
                gateFamilies.map(PullRequestGateFamily::encoded).sorted().jsonArray() + ",",
        )
        appendLine("  \"directArtifacts\": ${directArtifacts.sorted().jsonArray()},")
        appendLine("  \"directProjects\": ${directProjects.sorted().jsonArray()},")
        appendLine("  \"dependencyClosure\": ${dependencyClosure.sorted().jsonArray()},")
        appendLine(
            "  \"reverseDependentClosure\": ${reverseDependentClosure.sorted().jsonArray()},",
        )
        appendLine("  \"reasons\": ${reasons.jsonArray()},")
        appendLine("  \"workflows\": {")
        appendLine("    \"qaQuick\": ${workflows.qaQuick},")
        appendLine("    \"qaQuickMode\": ${workflows.qaQuickMode.encoded.jsonString()},")
        appendLine("    \"qaPreview\": ${workflows.qaPreview},")
        appendLine("    \"documentation\": ${workflows.documentation}")
        appendLine("  }")
        appendLine("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("## Pull-request verification plan")
        appendLine()
        appendLine("- Base: `${baseRevision.take(12)}`")
        appendLine("- Head: `${headRevision.take(12)}`")
        appendLine("- Changed files: $changedFiles")
        appendLine("- Full fallback: ${if (fullFallback) "yes" else "no"}")
        appendLine(
            "- Workflows: `qaQuick=${workflows.qaQuick}`, " +
                "`qaPreview=${workflows.qaPreview}`, `documentation=${workflows.documentation}`",
        )
        appendLine("- qaQuick mode: `${workflows.qaQuickMode.encoded}`")
        appendLine()
        appendLine("### Selected gate families")
        appendLine()
        gateFamilies.map(PullRequestGateFamily::encoded).sorted().forEach { family ->
            appendLine("- `$family`")
        }
        appendLine()
        appendLine("### Skipped gate families")
        appendLine()
        val skippedFamilies = PullRequestGateFamily.values().toSet() - gateFamilies
        if (skippedFamilies.isEmpty()) {
            appendLine("- none")
        } else {
            skippedFamilies.map(PullRequestGateFamily::encoded).sorted().forEach { family ->
                appendLine("- `$family`")
            }
        }
        appendLine()
        appendLine("### Artifact impact")
        appendLine()
        appendMarkdownValues("Direct", directArtifacts)
        appendMarkdownValues("Non-published projects", directProjects)
        appendMarkdownValues("Dependencies", dependencyClosure)
        appendMarkdownValues("Reverse dependents", reverseDependentClosure)
        appendLine()
        appendLine("### Reasons")
        appendLine()
        val visibleReasons = reasons.take(100)
        visibleReasons.forEach { reason -> appendLine("- $reason") }
        if (reasons.size > visibleReasons.size) {
            appendLine("- ... ${reasons.size - visibleReasons.size} additional path reasons are in `plan.json`.")
        }
    }

    fun toGitHubOutputs(): String = buildString {
        appendLine("qa_quick=${workflows.qaQuick}")
        appendLine("qa_quick_mode=${workflows.qaQuickMode.encoded}")
        appendLine("qa_preview=${workflows.qaPreview}")
        appendLine("documentation=${workflows.documentation}")
        appendLine("full_fallback=$fullFallback")
        appendLine("gate_families=${gateFamilies.map(PullRequestGateFamily::encoded).sorted().csv()}")
        appendLine("direct_artifacts=${directArtifacts.sorted().csv()}")
        appendLine("direct_projects=${directProjects.sorted().csv()}")
        appendLine("dependency_closure=${dependencyClosure.sorted().csv()}")
        appendLine("reverse_dependent_closure=${reverseDependentClosure.sorted().csv()}")
    }

    private fun StringBuilder.appendMarkdownValues(label: String, values: Set<String>) {
        val rendered = values.sorted().joinToString { "`$it`" }.ifEmpty { "none" }
        appendLine("- $label: $rendered")
    }
}

internal data class PullRequestFullFallbackPolicy(
    val alwaysFull: List<String>,
    val knownScoped: List<String>,
) {
    fun alwaysFullPattern(path: String): String? = alwaysFull.firstOrNull { pattern ->
        globMatches(pattern, path)
    }

    fun knownScopedPattern(path: String): String? = knownScoped.firstOrNull { pattern ->
        globMatches(pattern, path)
    }

    companion object {
        fun parse(file: File): PullRequestFullFallbackPolicy {
            val document = JsonSlurper().parse(file) as? Map<*, *>
                ?: error("Full-fallback policy '${file.path}' must contain a JSON object.")
            check((document["schemaVersion"] as? Number)?.toInt() == 1) {
                "Unsupported full-fallback policy schema in '${file.path}'."
            }
            check(document["unknownPathBehavior"] == "full") {
                "Full-fallback policy '${file.path}' must keep unknownPathBehavior=full."
            }
            return PullRequestFullFallbackPolicy(
                alwaysFull = document.stringList("alwaysFull"),
                knownScoped = document.stringList("knownScoped"),
            )
        }

        private fun Map<*, *>.stringList(key: String): List<String> =
            (this[key] as? List<*>)
                ?.map { value -> value as? String ?: error("'$key' must contain strings.") }
                ?: error("Full-fallback policy is missing '$key'.")
    }
}

internal object PullRequestArtifactGraph {
    fun registeredArtifacts(catalog: File): Set<String> {
        val properties = catalog.loadProperties()
        return properties.stringPropertyNames()
            .mapNotNull { key ->
                Regex("^module\\.([^.]+)\\.version$").matchEntire(key)?.groupValues?.get(1)
            }
            .toSortedSet()
            .also { artifacts ->
                check(artifacts.isNotEmpty()) {
                    "Publishing catalog '${catalog.path}' contains no registered module versions."
                }
            }
    }

    fun dependencies(contract: File, artifacts: Set<String>): Map<String, Set<String>> {
        val properties = contract.loadProperties()
        check(properties.getProperty("schema.version") == "1") {
            "Unsupported dependency contract schema in '${contract.path}'."
        }
        val declarations = properties.stringPropertyNames()
            .filter { key -> key.startsWith("module.") }
            .associate { key ->
                val module = key.removePrefix("module.")
                module to parseDependencyDeclaration(properties.getProperty(key))
            }
        check(declarations.keys == artifacts) {
            "Dependency contract modules must exactly match publishing artifacts. " +
                "Missing=${(artifacts - declarations.keys).sorted()}, " +
                "unknown=${(declarations.keys - artifacts).sorted()}."
        }
        declarations.forEach { (module, dependencies) ->
            check(dependencies.all(artifacts::contains)) {
                "Dependency contract for '$module' contains unknown artifacts: " +
                    (dependencies - artifacts).sorted().joinToString()
            }
        }
        return declarations
    }

    private fun parseDependencyDeclaration(value: String): Set<String> =
        value.split(';')
            .flatMap { field ->
                val separator = field.indexOf('=')
                check(separator > 0) { "Invalid dependency-contract field '$field'." }
                field.substring(separator + 1)
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
            }
            .toSortedSet()
}

internal object PullRequestImpactPlanner {
    private val appendOnlyChangeset = Regex("^release/changes/[^/]+\\.json$")
    private val fullGateFamilies = PullRequestGateFamily.values().toSet()
    private val acceptedDocumentationSampleGateFamilies = setOf(
        PullRequestGateFamily.DocumentationGovernance,
        PullRequestGateFamily.DocumentationSite,
        PullRequestGateFamily.Samples,
    )
    private val previewArtifacts = setOf(
        "viewcompose-graphics",
        "viewcompose-graphics-core",
        "viewcompose-preview",
        "viewcompose-preview-core",
        "viewcompose-preview-gradle-plugin",
        "viewcompose-preview-runner",
        "viewcompose-preview-worker-host",
        "viewcompose-renderer-android",
    )

    fun plan(
        baseRevision: String,
        headRevision: String,
        eventName: String,
        forceFull: Boolean,
        maxScopedChangedFiles: Int,
        changes: List<PullRequestPathChange>,
        policy: PullRequestFullFallbackPolicy,
        artifacts: Set<String>,
        dependencies: Map<String, Set<String>>,
    ): PullRequestImpactPlan {
        require(maxScopedChangedFiles > 0) { "maxScopedChangedFiles must be positive." }
        val changedPaths = changes.flatMap(PullRequestPathChange::paths).distinct().sorted()
        val fullReasons = linkedSetOf<String>()
        val reasons = linkedSetOf<String>()
        val gates = linkedSetOf<PullRequestGateFamily>()
        val directArtifacts = linkedSetOf<String>()
        val directProjects = linkedSetOf<String>()

        if (eventName != "pull_request") {
            fullReasons += "event:$eventName"
        }
        if (forceFull) {
            fullReasons += "manual-full-override"
        }
        if (changedPaths.isEmpty()) {
            fullReasons += "empty-diff"
        }
        if (changedPaths.size > maxScopedChangedFiles) {
            fullReasons += "large-diff:${changedPaths.size}>$maxScopedChangedFiles"
        }

        changes.forEach { change ->
            change.paths.forEach pathLoop@{ path ->
                val normalized = path.normalizeRepositoryPath()
                if (change.status == 'A' && appendOnlyChangeset.matches(normalized)) {
                    gates += PullRequestGateFamily.ReleaseIntent
                    reasons += "$normalized -> append-only-changeset"
                    return@pathLoop
                }
                policy.alwaysFullPattern(normalized)?.let { pattern ->
                    fullReasons += "always-full:$pattern:$normalized"
                    return@pathLoop
                }
                val artifact = normalized.substringBefore('/').takeIf(artifacts::contains)
                if (artifact != null) {
                    directArtifacts += artifact
                    classifyArtifactPath(normalized, artifact, gates, reasons)
                    return@pathLoop
                }
                when {
                    normalized.startsWith("docs/") -> {
                        gates += PullRequestGateFamily.DocumentationGovernance
                        gates += PullRequestGateFamily.DocumentationSite
                        reasons += "$normalized -> documentation"
                    }
                    normalized.startsWith("website/src/") ||
                        normalized.startsWith("website/static/") ||
                        normalized.startsWith("website/i18n/") -> {
                        gates += PullRequestGateFamily.DocumentationGovernance
                        gates += PullRequestGateFamily.DocumentationSite
                        reasons += "$normalized -> website"
                    }
                    normalized.startsWith("app/") -> {
                        directProjects += ":app"
                        gates += PullRequestGateFamily.Demo
                        gates += PullRequestGateFamily.ModuleVerification
                        reasons += "$normalized -> demo"
                    }
                    normalized.startsWith("samples/") -> {
                        when {
                            normalized.startsWith("samples/counter/") ->
                                directProjects += ":samples:counter"
                            normalized.startsWith("samples/tutorials/") ->
                                directProjects += ":samples:tutorials"
                            else -> directProjects += setOf(
                                ":samples:counter",
                                ":samples:tutorials",
                            )
                        }
                        gates += PullRequestGateFamily.DocumentationGovernance
                        gates += PullRequestGateFamily.Samples
                        reasons += "$normalized -> sample"
                    }
                    normalized.startsWith("integration-tests/") -> {
                        directProjects += ":integration-tests:paging-presenter"
                        gates += PullRequestGateFamily.IntegrationTests
                        gates += PullRequestGateFamily.ModuleVerification
                        reasons += "$normalized -> integration-test"
                    }
                    normalized.startsWith("viewcompose-benchmark/") -> {
                        directProjects += ":viewcompose-benchmark"
                        gates += PullRequestGateFamily.DeviceAndBenchmark
                        gates += PullRequestGateFamily.ModuleVerification
                        reasons += "$normalized -> benchmark"
                    }
                    policy.knownScopedPattern(normalized) != null -> {
                        fullReasons += "unclassified-known-scope:$normalized"
                    }
                    else -> fullReasons += "unknown-path:$normalized"
                }
            }
        }

        val dependencyClosure = transitiveClosure(directArtifacts, dependencies) - directArtifacts
        val reverseDependencies = reverseDependencies(dependencies)
        val reverseDependentClosure = transitiveClosure(directArtifacts, reverseDependencies) -
            directArtifacts
        if ((directArtifacts + dependencyClosure + reverseDependentClosure).any { artifact ->
                artifact in previewArtifacts
            }
        ) {
            gates += PullRequestGateFamily.Preview
            reasons += "artifact-closure -> preview"
        }

        val fullFallback = fullReasons.isNotEmpty()
        if (fullFallback) {
            gates.clear()
            gates += fullGateFamilies
            reasons += fullReasons.map { reason -> "full fallback: $reason" }
        }
        val selectsQaQuick = fullFallback || gates.any { family ->
            family !in setOf(
                PullRequestGateFamily.ApiDocumentation,
                PullRequestGateFamily.DocumentationGovernance,
                PullRequestGateFamily.DocumentationSite,
            )
        }
        val qaQuickMode = when {
            fullFallback -> PullRequestQaQuickMode.Complete
            !selectsQaQuick -> PullRequestQaQuickMode.Skip
            gates == acceptedDocumentationSampleGateFamilies &&
                directArtifacts.isEmpty() &&
                directProjects == setOf(":samples:tutorials") -> PullRequestQaQuickMode.Affected
            else -> PullRequestQaQuickMode.AffectedWithShadow
        }
        val workflows = PullRequestWorkflowSelection(
            qaQuickMode = qaQuickMode,
            qaPreview = fullFallback || PullRequestGateFamily.Preview in gates,
            documentation = fullFallback || gates.any { family ->
                family in setOf(
                    PullRequestGateFamily.ApiDocumentation,
                    PullRequestGateFamily.DocumentationGovernance,
                    PullRequestGateFamily.DocumentationSite,
                )
            },
        )
        return PullRequestImpactPlan(
            baseRevision = baseRevision,
            headRevision = headRevision,
            changedFiles = changedPaths.size,
            fullFallback = fullFallback,
            fullFallbackReasons = fullReasons.sorted(),
            gateFamilies = gates,
            directArtifacts = directArtifacts,
            directProjects = directProjects,
            dependencyClosure = dependencyClosure,
            reverseDependentClosure = reverseDependentClosure,
            reasons = reasons.sorted(),
            workflows = workflows,
        )
    }

    private fun classifyArtifactPath(
        path: String,
        artifact: String,
        gates: MutableSet<PullRequestGateFamily>,
        reasons: MutableSet<String>,
    ) {
        gates += PullRequestGateFamily.ModuleVerification
        if (
            path.startsWith("$artifact/src/test/") ||
            path.startsWith("$artifact/src/androidTest/") ||
            path.startsWith("$artifact/src/testFixtures/")
        ) {
            reasons += "$path -> module-test:$artifact"
        } else {
            gates += PullRequestGateFamily.ApiDocumentation
            gates += PullRequestGateFamily.DocumentationGovernance
            gates += PullRequestGateFamily.ReleaseIntent
            reasons += "$path -> published-production:$artifact"
        }
    }

    private fun reverseDependencies(
        dependencies: Map<String, Set<String>>,
    ): Map<String, Set<String>> {
        val reverse = dependencies.keys.associateWith { linkedSetOf<String>() }
        dependencies.forEach { (module, moduleDependencies) ->
            moduleDependencies.forEach { dependency -> reverse.getValue(dependency) += module }
        }
        return reverse
    }

    private fun transitiveClosure(
        roots: Set<String>,
        graph: Map<String, Set<String>>,
    ): Set<String> {
        val result = linkedSetOf<String>()
        val pending = ArrayDeque(roots.sorted())
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!result.add(current)) continue
            graph[current].orEmpty().sorted().forEach(pending::addLast)
        }
        return result
    }
}

internal class PullRequestGitRepository(private val root: File) {
    fun revision(value: String): String = execute("rev-parse", "--verify", "$value^{commit}").trim()

    fun changes(baseRevision: String, headRevision: String): List<PullRequestPathChange> {
        val output = executeBytes(
            "diff",
            "--name-status",
            "--find-renames",
            "-z",
            "$baseRevision..$headRevision",
            "--",
        )
        val fields = output.toString(StandardCharsets.UTF_8)
            .split('\u0000')
            .filter(String::isNotEmpty)
        val changes = mutableListOf<PullRequestPathChange>()
        var index = 0
        while (index < fields.size) {
            val statusField = fields[index++]
            val status = statusField.firstOrNull()
                ?: error("Git emitted an empty change status.")
            val pathCount = if (status == 'R' || status == 'C') 2 else 1
            check(index + pathCount <= fields.size) {
                "Git emitted an incomplete '$statusField' change record."
            }
            changes += PullRequestPathChange(
                status = status,
                paths = fields.subList(index, index + pathCount).map(String::normalizeRepositoryPath),
            )
            index += pathCount
        }
        return changes
    }

    private fun execute(vararg arguments: String): String =
        executeBytes(*arguments).toString(StandardCharsets.UTF_8)

    private fun executeBytes(vararg arguments: String): ByteArray {
        val output = ByteArrayOutputStream()
        val process = ProcessBuilder(listOf("git") + arguments)
            .directory(root)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { input -> input.copyTo(output) }
        check(process.waitFor() == 0) {
            "Git command failed: git ${arguments.joinToString(" ")}\n" +
                output.toString(StandardCharsets.UTF_8)
        }
        return output.toByteArray()
    }
}

internal object PullRequestImpactCli {
    @JvmStatic
    fun main(arguments: Array<String>) {
        val options = parseOptions(arguments)
        val repository = options.requiredFile("repository").canonicalFile
        val git = PullRequestGitRepository(repository)
        val base = git.revision(options.required("base"))
        val head = git.revision(options["head"] ?: "HEAD")
        val catalog = repository.resolve("gradle/viewcompose-publishing.properties")
        val dependencyContract = repository.resolve("gradle/viewcompose-dependency-contracts.properties")
        val fallbackPolicy = repository.resolve(
            "tools/viewcompose-quality-build/phase0/fixtures/full-fallback-paths.json",
        )
        val artifacts = PullRequestArtifactGraph.registeredArtifacts(catalog)
        val plan = PullRequestImpactPlanner.plan(
            baseRevision = base,
            headRevision = head,
            eventName = options["event"] ?: "pull_request",
            forceFull = options.boolean("force-full", default = false),
            maxScopedChangedFiles = options["max-changed-files"]?.toInt()
                ?: DEFAULT_MAX_SCOPED_CHANGED_FILES,
            changes = git.changes(base, head),
            policy = PullRequestFullFallbackPolicy.parse(fallbackPolicy),
            artifacts = artifacts,
            dependencies = PullRequestArtifactGraph.dependencies(dependencyContract, artifacts),
        )
        options.requiredFile("json-output").writeParented(plan.toJson())
        options.requiredFile("summary-output").writeParented(plan.toMarkdown())
        options.requiredFile("github-output").writeParented(plan.toGitHubOutputs())
        println(plan.toMarkdown())
    }

    private fun parseOptions(arguments: Array<String>): Map<String, String> {
        check(arguments.size % 2 == 0) { "Every pull-request impact option requires a value." }
        return arguments.asList().chunked(2).associate { pair ->
            val key = pair[0]
            check(key.startsWith("--")) { "Unknown pull-request impact argument '$key'." }
            key.removePrefix("--") to pair[1]
        }
    }

    private fun Map<String, String>.required(name: String): String =
        get(name)?.takeIf(String::isNotBlank)
            ?: error("Missing required --$name option.")

    private fun Map<String, String>.requiredFile(name: String): File = File(required(name))

    private fun Map<String, String>.boolean(name: String, default: Boolean): Boolean {
        val value = get(name) ?: return default
        return value.toBooleanStrictOrNull()
            ?: error("--$name must be 'true' or 'false', but was '$value'.")
    }
}

private fun File.loadProperties(): Properties = Properties().also { properties ->
    inputStream().use(properties::load)
}

private fun File.writeParented(content: String) {
    parentFile.mkdirs()
    writeText(content)
}

private fun String.normalizeRepositoryPath(): String =
    replace('\\', '/').removePrefix("./").trimStart('/')

private fun globMatches(pattern: String, path: String): Boolean {
    val expression = buildString {
        append('^')
        var index = 0
        while (index < pattern.length) {
            when (val character = pattern[index]) {
                '*' -> {
                    if (pattern.getOrNull(index + 1) == '*') {
                        append(".*")
                        index += 2
                    } else {
                        append("[^/]*")
                        index++
                    }
                }
                '?' -> {
                    append("[^/]")
                    index++
                }
                else -> {
                    if (character in "\\.[]{}()+-^$|") append('\\')
                    append(character)
                    index++
                }
            }
        }
        append('$')
    }
    return Regex(expression).matches(path)
}

private fun String.jsonString(): String = buildString {
    append('"')
    this@jsonString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

private fun Iterable<String>.jsonArray(): String =
    joinToString(prefix = "[", postfix = "]") { value -> value.jsonString() }

private fun Iterable<String>.csv(): String = joinToString(",")
