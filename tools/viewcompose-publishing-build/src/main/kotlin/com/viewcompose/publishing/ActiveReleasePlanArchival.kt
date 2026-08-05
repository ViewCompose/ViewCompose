package com.viewcompose.publishing

import java.io.File

internal data class ActiveReleasePlanArchivalVerification(
    val activePlanCount: Int,
    val linkedChangeSetCount: Int,
)

internal object ActiveReleasePlanArchivalGate {
    private const val releaseChangeSetsHeading = "## Maven release changesets"
    private val changeSetEntry = Regex(
        "^- `((?:release/changes/)[a-z0-9][a-z0-9-]{2,80}\\.json)`$",
    )

    fun verify(
        repositoryRoot: File,
        plansDirectory: File,
        selectedArtifacts: Set<String>,
        knownArtifacts: Set<String>,
        dependencies: Map<String, Set<String>>,
    ): ActiveReleasePlanArchivalVerification {
        check(selectedArtifacts.isNotEmpty()) {
            "Select at least one artifact with " +
                "-PviewComposePublishModules=viewcompose-runtime,viewcompose-navigation-core"
        }
        check(selectedArtifacts.all(knownArtifacts::contains)) {
            "Unknown ViewCompose publication modules: " +
                (selectedArtifacts - knownArtifacts).sorted().joinToString()
        }
        val plans = activePlans(plansDirectory)
        val duplicateLinks = plans
            .flatMap { plan -> plan.changeSetPaths.map { path -> path to plan.path } }
            .groupBy({ (path, _) -> path }, { (_, plan) -> plan })
            .filterValues { linkedPlans -> linkedPlans.size > 1 }
        check(duplicateLinks.isEmpty()) {
            buildString {
                appendLine("Maven release changesets must belong to exactly one active plan:")
                duplicateLinks.toSortedMap().forEach { (path, linkedPlans) ->
                    appendLine("- $path -> ${linkedPlans.sorted().joinToString()}")
                }
            }.trimEnd()
        }

        val blockers = plans.mapNotNull { plan ->
            val directArtifacts = plan.changeSetPaths.flatMap { path ->
                val file = repositoryRoot.resolve(path)
                check(file.isFile) {
                    "Active plan '${plan.path}' references missing Maven release changeset '$path'."
                }
                ReleaseChangeSetParser.parse(file, knownArtifacts)
                    .changes
                    .map(DeclaredReleaseChange::artifact)
            }.toSet()
            val releaseArtifacts = ViewComposeReleasePlanner
                .propagateReleaseDependencies(directArtifacts, dependencies)
                .keys
            val blockedArtifacts = selectedArtifacts.intersect(releaseArtifacts)
            blockedArtifacts.takeIf(Set<String>::isNotEmpty)?.let { blocked ->
                PlanBlocker(
                    path = plan.path,
                    changeSetPaths = plan.changeSetPaths,
                    artifacts = blocked,
                )
            }
        }
        check(blockers.isEmpty()) {
            buildString {
                appendLine(
                    "Maven Central upload is blocked because selected artifacts are covered by " +
                        "active execution plans:",
                )
                blockers.sortedBy(PlanBlocker::path).forEach { blocker ->
                    appendLine(
                        "- ${blocker.path} -> ${blocker.artifacts.sorted().joinToString()} " +
                            "(${blocker.changeSetPaths.joinToString()})",
                    )
                }
                append(
                    "Complete and archive each listed plan, update the active and archive indexes, " +
                        "then rerun verifyArchivedViewComposeReleasePlans. Planning and local " +
                        "publication remain available before this acceptance boundary.",
                )
            }
        }
        return ActiveReleasePlanArchivalVerification(
            activePlanCount = plans.size,
            linkedChangeSetCount = plans.sumOf { plan -> plan.changeSetPaths.size },
        )
    }

    private fun activePlans(plansDirectory: File): List<ActiveReleasePlan> {
        check(plansDirectory.isDirectory) {
            "Missing active execution plan directory: ${plansDirectory.path}"
        }
        return plansDirectory.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && file.extension == "md" && file.name != "README.md"
            }
            .sortedBy(File::getName)
            .map(::parsePlan)
    }

    private fun parsePlan(file: File): ActiveReleasePlan {
        val path = "docs/project/plans/${file.name}"
        val lines = file.readLines()
        val headings = lines.withIndex()
            .filter { (_, line) -> line.trim() == releaseChangeSetsHeading }
            .map(IndexedValue<String>::index)
        check(headings.size == 1) {
            "Active plan '$path' must contain exactly one '$releaseChangeSetsHeading' section."
        }
        val start = headings.single() + 1
        val end = (start until lines.size)
            .firstOrNull { index -> lines[index].startsWith("## ") }
            ?: lines.size
        val entries = lines.subList(start, end)
            .map(String::trim)
            .filter(String::isNotEmpty)
        val noneEntries = entries.filter { line -> line.startsWith("- None.") }
        val changeSets = entries.mapNotNull { line ->
            changeSetEntry.matchEntire(line)?.groupValues?.get(1)
        }
        val invalidEntries = entries.filterNot { line ->
            line.startsWith("- None.") || changeSetEntry.matches(line)
        }
        check(invalidEntries.isEmpty()) {
            "Active plan '$path' has invalid Maven release changeset entries: " +
                invalidEntries.joinToString()
        }
        check(noneEntries.size <= 1 && !(noneEntries.isNotEmpty() && changeSets.isNotEmpty())) {
            "Active plan '$path' must declare either one '- None.' entry or release changesets, not both."
        }
        check(noneEntries.isNotEmpty() || changeSets.isNotEmpty()) {
            "Active plan '$path' must declare '- None.' or at least one release changeset."
        }
        check(changeSets.distinct().size == changeSets.size) {
            "Active plan '$path' contains duplicate Maven release changesets."
        }
        return ActiveReleasePlan(path = path, changeSetPaths = changeSets)
    }

    private data class ActiveReleasePlan(
        val path: String,
        val changeSetPaths: List<String>,
    )

    private data class PlanBlocker(
        val path: String,
        val changeSetPaths: List<String>,
        val artifacts: Set<String>,
    )
}
