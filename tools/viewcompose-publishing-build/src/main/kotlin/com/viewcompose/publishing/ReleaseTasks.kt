package com.viewcompose.publishing

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyViewComposeReleaseIntentTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val artifacts: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val baseRevision: Property<String>

    @TaskAction
    fun verifyIntent() {
        val root = repositoryDirectory.get().asFile
        val git = GitRepository(root)
        val base = git.resolveVerificationBase(baseRevision.orNull)
        val changedPaths = git.changedPaths(base, includeWorkingTree = true)
        val statuses = git.changeSetStatuses(base)
        val mutatedChangeSets = statuses.filter { (path, status) ->
            path.endsWith(".json") && status != 'A'
        }
        check(mutatedChangeSets.isEmpty()) {
            "Release changesets are immutable after merge. Add a new file instead of modifying, " +
                "renaming, or deleting: ${mutatedChangeSets.keys.sorted().joinToString()}."
        }
        val knownArtifacts = artifacts.get().toSet()
        val changeSets = statuses.filter { (path, status) ->
            status == 'A' && path.endsWith(".json")
        }.keys.sorted().map { path ->
            ReleaseChangeSetParser.parse(root.resolve(path), knownArtifacts)
        }
        check(changeSets.size <= 1) {
            "Each pull request owns exactly one release changeset. Combine these files: " +
                changeSets.map { changeSet -> changeSet.file.name }.joinToString()
        }
        val ownership = ReleaseOwnership.classify(changedPaths, knownArtifacts)
        val result = ReleaseIntentVerifier.verify(ownership, changeSets)
        logger.lifecycle(
            "ViewCompose release intent verified against $base: " +
                "${result.affectedArtifacts.size} release artifact(s), " +
                "${result.ignoredArtifacts.size} ignored artifact(s), and " +
                "${result.sharedPaths.size} shared path classification(s).",
        )
    }
}

abstract class PlanViewComposeReleaseTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val artifacts: ListProperty<String>

    @get:Input
    abstract val declaredVersions: MapProperty<String, String>

    @get:Input
    abstract val declaredSourceRevisions: MapProperty<String, String>

    @get:Input
    abstract val unpublishedArtifacts: ListProperty<String>

    @get:Input
    abstract val retiredArtifacts: ListProperty<String>

    @get:Input
    abstract val artifactDependencies: MapProperty<String, String>

    @get:OutputFile
    abstract val jsonOutput: RegularFileProperty

    @get:OutputFile
    abstract val markdownOutput: RegularFileProperty

    @TaskAction
    fun planRelease() {
        val root = repositoryDirectory.get().asFile
        val dependencies = artifactDependencies.get().mapValues { (_, encoded) ->
            encoded.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        }
        val plan = ViewComposeReleasePlanner(
            root = root,
            git = GitRepository(root),
            artifacts = artifacts.get().toSet(),
            declaredVersions = declaredVersions.get().mapValues { (_, version) ->
                MavenVersion.parse(version)
            },
            declaredSourceRevisions = declaredSourceRevisions.get(),
            unpublishedArtifacts = unpublishedArtifacts.get().toSet(),
            retiredArtifacts = retiredArtifacts.get().toSet(),
            dependencies = dependencies,
        ).plan()
        jsonOutput.get().asFile.writeParented(plan.toJson())
        markdownOutput.get().asFile.writeParented(plan.toMarkdown())
        logger.lifecycle(
            "ViewCompose release plan contains ${plan.releases.size} artifact(s): " +
                markdownOutput.get().asFile.absolutePath,
        )
    }
}

abstract class PrepareViewComposeReleaseTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Internal
    abstract val planFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val confirmedVersions: Property<String>

    @TaskAction
    fun prepareRelease() {
        val root = repositoryDirectory.get().asFile
        val versions = ReleaseMetadataPreparer.parseConfirmedVersions(
            confirmedVersions.orNull.orEmpty(),
        )
        ReleaseMetadataPreparer.prepare(
            planFile = planFile.get().asFile,
            publishingFile = root.resolve("gradle/viewcompose-publishing.properties"),
            historyFile = root.resolve("gradle/viewcompose-documentation-releases.properties"),
            confirmedVersions = versions,
        )
        logger.lifecycle(
            "Prepared ${versions.size} ViewCompose artifact(s). Review the metadata diff, run " +
                "release verification, and commit it as the metadata-only release commit.",
        )
    }
}

abstract class VerifyArchivedViewComposeReleasePlansTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val plansDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseChangesDirectory: DirectoryProperty

    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    abstract val availableModules: ListProperty<String>

    @get:Input
    abstract val artifactDependencies: MapProperty<String, String>

    @TaskAction
    fun verifyArchival() {
        val verification = ActiveReleasePlanArchivalGate.verify(
            repositoryRoot = repositoryDirectory.get().asFile,
            plansDirectory = plansDirectory.get().asFile,
            selectedArtifacts = modules.get().toSet(),
            knownArtifacts = availableModules.get().toSet(),
            dependencies = artifactDependencies.get().mapValues { (_, encoded) ->
                encoded.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
            },
        )
        logger.lifecycle(
            "Verified ${verification.activePlanCount} active execution plan(s) and " +
                "${verification.linkedChangeSetCount} linked Maven release changeset(s).",
        )
    }
}

private fun File.writeParented(content: String) {
    parentFile.mkdirs()
    try {
        writeText(content)
    } catch (failure: Exception) {
        throw GradleException("Cannot write release output '$absolutePath'.", failure)
    }
}
