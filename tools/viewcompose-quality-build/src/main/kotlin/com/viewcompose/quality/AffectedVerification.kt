package com.viewcompose.quality

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.register

private val affectedDependencyConfigurations = listOf(
    "api",
    "implementation",
    "compileOnly",
    "runtimeOnly",
)

private val moduleVerificationTasks = setOf(
    "verifyModulePackageRoots",
    "verifyAndroidModuleNamespaces",
    "verifyModuleDependencyBoundaries",
    "verifyDevelopmentToolingIsolation",
    "verifyDesignSystemIsolation",
    "verifyUiFoundationPlatformBoundary",
    "verifyRuntimePurity",
    "verifyNavigationCorePurity",
    "verifyGestureCorePurity",
    "verifyGraphicsCorePurity",
    "verifyPreviewCorePurity",
    "verifyPreviewRunnerBoundary",
    "verifyPreviewGradlePluginBoundary",
    "verifyPreviewWorkerHostBoundary",
)

private val releaseVerificationTasks = setOf(
    "verifyDslApiContracts",
    "verifyViewComposePublishingConfiguration",
    "verifyViewComposeReleaseIntent",
)

private val demoVerificationTasks = setOf(
    "verifyDemoReleaseToolingApk",
    "testPagingMacrobenchmarkSummaryTool",
    "testDeviceDiagnosticsRequestMeasurementTool",
    "verifyDemoAutomationSelectors",
    "verifyDemoLocalizationResources",
    "verifyDemoLocalizedVisibleCopy",
)

private val sampleVerificationTasks = setOf(
    "publishViewComposeToLocalRepository",
    "verifyMigrationPairedSamples",
    "verifyTutorialSamples",
)

/** Transport contract between isolated impact classification and the configured root build. */
internal data class AffectedVerificationInput(
    val gateFamilies: Set<PullRequestGateFamily>,
    val directArtifacts: Set<String>,
    val directProjects: Set<String>,
    val dependencyClosure: Set<String>,
    val reverseDependentClosure: Set<String>,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String>): AffectedVerificationInput =
            AffectedVerificationInput(
                gateFamilies = environment.csv("VIEWCOMPOSE_AFFECTED_GATE_FAMILIES")
                    .mapTo(linkedSetOf()) { encoded ->
                        PullRequestGateFamily.values().singleOrNull { family ->
                            family.encoded == encoded
                        } ?: throw GradleException(
                            "Unknown affected gate family '$encoded'.",
                        )
                    },
                directArtifacts = environment.csv("VIEWCOMPOSE_AFFECTED_DIRECT_ARTIFACTS"),
                directProjects = environment.csv("VIEWCOMPOSE_AFFECTED_DIRECT_PROJECTS"),
                dependencyClosure = environment.csv("VIEWCOMPOSE_AFFECTED_DEPENDENCY_CLOSURE"),
                reverseDependentClosure =
                    environment.csv("VIEWCOMPOSE_AFFECTED_REVERSE_DEPENDENT_CLOSURE"),
            )

        private fun Map<String, String>.csv(name: String): Set<String> =
            get(name)
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.toSortedSet()
                .orEmpty()
    }
}

/** Deterministic selective task plan validated against the configured Gradle project graph. */
internal data class AffectedVerificationPlan(
    val input: AffectedVerificationInput,
    val actualDependencies: Map<String, Set<String>>,
    val selectedArtifacts: Set<String>,
    val taskPaths: Set<String>,
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"gateFamilies\": ${input.gateFamilies.encoded().jsonArray()},")
        appendLine("  \"directArtifacts\": ${input.directArtifacts.jsonArray()},")
        appendLine("  \"directProjects\": ${input.directProjects.jsonArray()},")
        appendLine("  \"dependencyClosure\": ${input.dependencyClosure.jsonArray()},")
        appendLine(
            "  \"reverseDependentClosure\": " +
                "${input.reverseDependentClosure.jsonArray()},",
        )
        appendLine("  \"selectedArtifacts\": ${selectedArtifacts.jsonArray()},")
        appendLine("  \"taskPaths\": ${taskPaths.jsonArray()}")
        appendLine("}")
    }

    fun toMarkdown(): String = buildString {
        appendLine("## Affected Gradle verification candidate")
        appendLine()
        appendLine("- Gate families: ${input.gateFamilies.encoded().markdownValues()}")
        appendLine("- Direct artifacts: ${input.directArtifacts.markdownValues()}")
        appendLine("- Non-published projects: ${input.directProjects.markdownValues()}")
        appendLine("- Dependency closure: ${input.dependencyClosure.markdownValues()}")
        appendLine(
            "- Reverse-dependent closure: ${input.reverseDependentClosure.markdownValues()}",
        )
        appendLine("- Selected artifacts: ${selectedArtifacts.markdownValues()}")
        appendLine("- Selected Gradle tasks: ${taskPaths.size}")
        appendLine()
        taskPaths.sorted().forEach { taskPath -> appendLine("  - `$taskPath`") }
    }
}

/** Builds and cross-checks a selective task plan without trusting classifier closure output. */
internal object AffectedVerificationPlanner {
    fun plan(
        input: AffectedVerificationInput,
        registeredArtifacts: Set<String>,
        actualDependencies: Map<String, Set<String>>,
        projectTasks: Map<String, Set<String>>,
    ): AffectedVerificationPlan {
        require(actualDependencies.keys == registeredArtifacts) {
            "Actual dependency graph must contain every registered artifact exactly."
        }
        val unknownDirectArtifacts = input.directArtifacts - registeredArtifacts
        check(unknownDirectArtifacts.isEmpty()) {
            "Affected verification contains unknown direct artifacts: " +
                unknownDirectArtifacts.sorted().joinToString()
        }
        val actualDependencyClosure =
            transitiveClosure(input.directArtifacts, actualDependencies) - input.directArtifacts
        val reverseGraph = registeredArtifacts.associateWith { linkedSetOf<String>() }
        actualDependencies.forEach { (artifact, dependencies) ->
            dependencies.forEach { dependency -> reverseGraph.getValue(dependency) += artifact }
        }
        val actualReverseClosure =
            transitiveClosure(input.directArtifacts, reverseGraph) - input.directArtifacts
        check(input.dependencyClosure == actualDependencyClosure) {
            closureMismatch("dependency", input.dependencyClosure, actualDependencyClosure)
        }
        check(input.reverseDependentClosure == actualReverseClosure) {
            closureMismatch(
                "reverse-dependent",
                input.reverseDependentClosure,
                actualReverseClosure,
            )
        }

        val selectedArtifacts =
            input.directArtifacts + actualDependencyClosure + actualReverseClosure
        val tasks = linkedSetOf<String>()
        if (PullRequestGateFamily.ModuleVerification in input.gateFamilies) {
            tasks += moduleVerificationTasks
            selectedArtifacts.sorted().forEach { artifact ->
                val available = projectTasks[":$artifact"]
                    ?: error("Registered artifact '$artifact' has no configured Gradle project.")
                tasks += ":$artifact:${available.requiredPreferredTask(
                    artifact,
                    "compile",
                    listOf("compileDebugKotlin", "compileKotlin"),
                )}"
                tasks += ":$artifact:${available.requiredPreferredTask(
                    artifact,
                    "unit test",
                    listOf("testDebugUnitTest", "test"),
                )}"
            }
        }
        if (PullRequestGateFamily.ReleaseIntent in input.gateFamilies) {
            tasks += releaseVerificationTasks
        }
        if (PullRequestGateFamily.Demo in input.gateFamilies) {
            tasks += demoVerificationTasks
        }
        if (PullRequestGateFamily.Samples in input.gateFamilies) {
            tasks += sampleVerificationTasks
        }
        input.directProjects.sorted().forEach { projectPath ->
            tasks += projectTasksForScope(projectPath, input.gateFamilies, projectTasks)
        }
        check(tasks.isNotEmpty()) {
            "Affected verification selected no Gradle tasks for " +
                input.gateFamilies.encoded().joinToString()
        }
        return AffectedVerificationPlan(
            input = input,
            actualDependencies = actualDependencies,
            selectedArtifacts = selectedArtifacts,
            taskPaths = tasks,
        )
    }

    private fun projectTasksForScope(
        projectPath: String,
        gates: Set<PullRequestGateFamily>,
        projectTasks: Map<String, Set<String>>,
    ): Set<String> {
        val available = projectTasks[projectPath]
            ?: error("Affected project '$projectPath' does not exist in the configured build.")
        val names = when {
            projectPath == ":app" && PullRequestGateFamily.Demo in gates ->
                listOf("compileDebugKotlin", "testDebugUnitTest")
            projectPath.startsWith(":samples:") && PullRequestGateFamily.Samples in gates ->
                listOf("assembleDebug", "compileDebugAndroidTestKotlin")
            projectPath.startsWith(":integration-tests:") &&
                PullRequestGateFamily.IntegrationTests in gates -> listOf("test")
            projectPath == ":viewcompose-benchmark" &&
                PullRequestGateFamily.DeviceAndBenchmark in gates ->
                listOf("compileBenchmarkKotlin")
            else -> error(
                "Affected project '$projectPath' has no task policy for " +
                    gates.encoded().joinToString(),
            )
        }
        val missing = names.filterNot(available::contains)
        check(missing.isEmpty()) {
            "Affected project '$projectPath' is missing tasks: ${missing.joinToString()}"
        }
        return names.mapTo(linkedSetOf()) { name -> "$projectPath:$name" }
    }

    private fun Set<String>.requiredPreferredTask(
        artifact: String,
        role: String,
        names: List<String>,
    ): String = names.firstOrNull(::contains)
        ?: error("Artifact '$artifact' has no supported $role task (${names.joinToString()}).")

    private fun closureMismatch(
        label: String,
        supplied: Set<String>,
        actual: Set<String>,
    ): String =
        "Classifier $label closure differs from the configured Gradle graph. " +
            "Missing=${(actual - supplied).sorted()}, extra=${(supplied - actual).sorted()}."

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

/** Registers the shadow-period candidate using the consuming build's configured project graph. */
internal fun Project.registerAffectedVerificationTask(extension: ViewComposeQualityExtension) {
    val input = providers.provider {
        AffectedVerificationInput.fromEnvironment(System.getenv())
    }
    val plan = providers.provider {
        val artifacts = PullRequestArtifactGraph.registeredArtifacts(
            extension.moduleCatalogFile.get().asFile,
        )
        AffectedVerificationPlanner.plan(
            input = input.get(),
            registeredArtifacts = artifacts,
            actualDependencies = affectedProjectDependencies(artifacts),
            projectTasks = rootProject.allprojects.associate { candidate ->
                candidate.path to candidate.tasks.names
            },
        )
    }
    tasks.register("qaAffected") {
        group = "verification"
        description =
            "Run the graph-validated affected Gradle candidate before complete shadow verification."
        dependsOn(plan.map(AffectedVerificationPlan::taskPaths))
        dependsOn(
            providers.provider {
                if (PullRequestGateFamily.ReleaseIntent in input.get().gateFamilies) {
                    listOf(gradle.includedBuild("viewcompose-publishing-build").task(":test"))
                } else {
                    emptyList()
                }
            },
        )
        doLast {
            val resolved = plan.get()
            val reportDirectory = extension.reportsDirectory.get().asFile
            reportDirectory.mkdirs()
            reportDirectory.resolve("affected-verification.json").writeText(resolved.toJson())
            reportDirectory.resolve("affected-verification.md").writeText(resolved.toMarkdown())
            logger.lifecycle(
                "Affected Gradle candidate passed ${resolved.taskPaths.size} selected tasks for " +
                    "${resolved.selectedArtifacts.size} artifacts and " +
                    "${resolved.input.directProjects.size} non-published projects.",
            )
        }
    }
}

internal fun Project.affectedProjectDependencies(
    artifacts: Set<String>,
): Map<String, Set<String>> = artifacts.sorted().associateWith { artifact ->
    val artifactProject = findProject(":$artifact")
        ?: error("Registered artifact '$artifact' has no configured Gradle project.")
    affectedDependencyConfigurations.flatMap { configurationName ->
        artifactProject.configurations.findByName(configurationName)
            ?.dependencies
            ?.withType(ProjectDependency::class.java)
            ?.map { dependency -> dependency.path.substringAfterLast(':') }
            .orEmpty()
    }.filter(artifacts::contains).toSortedSet()
}

private fun Set<PullRequestGateFamily>.encoded(): List<String> =
    map(PullRequestGateFamily::encoded).sorted()

private fun Iterable<String>.jsonArray(): String =
    sorted().joinToString(prefix = "[", postfix = "]") { value ->
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

private fun Iterable<String>.markdownValues(): String =
    sorted().joinToString { value -> "`$value`" }.ifEmpty { "none" }
