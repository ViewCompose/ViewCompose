package com.viewcompose.quality

import groovy.json.JsonSlurper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PullRequestImpactTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val artifacts = setOf(
        "viewcompose-runtime",
        "viewcompose-ui-foundation",
        "viewcompose-renderer-android",
        "viewcompose-material3",
        "viewcompose-preview",
    )
    private val dependencies = mapOf(
        "viewcompose-runtime" to emptySet(),
        "viewcompose-ui-foundation" to setOf("viewcompose-runtime"),
        "viewcompose-renderer-android" to setOf("viewcompose-ui-foundation"),
        "viewcompose-material3" to setOf("viewcompose-ui-foundation"),
        "viewcompose-preview" to setOf("viewcompose-renderer-android"),
    )
    private val policy = PullRequestFullFallbackPolicy(
        alwaysFull = listOf(
            ".github/workflows/**",
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle/**",
            "release/**",
            "tools/viewcompose-quality-build/**",
            "website/package.json",
            "website/scripts/**",
        ),
        knownScoped = listOf(
            "docs/**",
            "website/src/**",
            "website/static/**",
            "website/i18n/**",
            "app/**",
            "samples/**",
            "integration-tests/**",
        ),
    )

    @Test
    fun `documentation-only diff selects documentation without Android gates`() {
        val plan = plan(change('M', "docs/guides/theming.md"))

        assertFalse(plan.fullFallback)
        assertEquals(
            setOf(
                PullRequestGateFamily.DocumentationGovernance,
                PullRequestGateFamily.DocumentationSite,
            ),
            plan.gateFamilies,
        )
        assertEquals(
            PullRequestWorkflowSelection(
                qaQuick = false,
                qaPreview = false,
                documentation = true,
            ),
            plan.workflows,
        )
    }

    @Test
    fun `website presentation diff stays in the documentation family`() {
        val plan = plan(change('M', "website/src/pages/index.tsx"))

        assertFalse(plan.fullFallback)
        assertTrue(PullRequestGateFamily.DocumentationSite in plan.gateFamilies)
        assertTrue(plan.workflows.documentation)
        assertFalse(plan.workflows.qaQuick)
    }

    @Test
    fun `module test diff emits direct and graph closures`() {
        val plan = plan(change('M', "viewcompose-ui-foundation/src/test/kotlin/NodeTest.kt"))

        assertFalse(plan.fullFallback)
        assertEquals(setOf("viewcompose-ui-foundation"), plan.directArtifacts)
        assertEquals(setOf("viewcompose-runtime"), plan.dependencyClosure)
        assertEquals(
            setOf(
                "viewcompose-material3",
                "viewcompose-preview",
                "viewcompose-renderer-android",
            ),
            plan.reverseDependentClosure,
        )
        assertTrue(plan.workflows.qaQuick)
        assertTrue(plan.workflows.qaPreview)
        assertFalse(plan.workflows.documentation)
    }

    @Test
    fun `module production diff selects release API and documentation gates`() {
        val sourcePlan = plan(change('M', "viewcompose-material3/src/main/kotlin/Button.kt"))
        val moduleInputPlan = plan(change('M', "viewcompose-material3/consumer-rules.pro"))

        listOf(sourcePlan, moduleInputPlan).forEach { plan ->
            assertFalse(plan.fullFallback)
            assertTrue(PullRequestGateFamily.ReleaseIntent in plan.gateFamilies)
            assertTrue(PullRequestGateFamily.ApiDocumentation in plan.gateFamilies)
            assertTrue(PullRequestGateFamily.DocumentationGovernance in plan.gateFamilies)
            assertTrue(plan.workflows.qaQuick)
            assertTrue(plan.workflows.documentation)
            assertFalse(plan.workflows.qaPreview)
        }
    }

    @Test
    fun `sample diff selects sample and documentation ownership`() {
        val plan = plan(change('M', "samples/tutorials/src/main/kotlin/Counter.kt"))

        assertFalse(plan.fullFallback)
        assertTrue(PullRequestGateFamily.Samples in plan.gateFamilies)
        assertTrue(PullRequestGateFamily.DocumentationGovernance in plan.gateFamilies)
        assertTrue(plan.workflows.qaQuick)
        assertTrue(plan.workflows.documentation)
    }

    @Test
    fun `preview production diff selects every current gate workflow`() {
        val plan = plan(change('M', "viewcompose-preview/src/main/kotlin/PreviewCatalog.kt"))

        assertFalse(plan.fullFallback)
        assertTrue(PullRequestGateFamily.Preview in plan.gateFamilies)
        assertTrue(plan.workflows.qaQuick)
        assertTrue(plan.workflows.qaPreview)
        assertTrue(plan.workflows.documentation)
    }

    @Test
    fun `release root and unknown inputs conservatively select full fallback`() {
        listOf(
            "release/changes/example.json" to "always-full:release/**",
            "build.gradle.kts" to "always-full:build.gradle.kts",
            "future-system/unknown.file" to "unknown-path",
        ).forEach { (path, expectedReason) ->
            val plan = plan(change('M', path))
            assertTrue(path, plan.fullFallback)
            assertTrue(
                path,
                plan.fullFallbackReasons.any { reason -> expectedReason in reason },
            )
            assertEquals(PullRequestGateFamily.values().toSet(), plan.gateFamilies)
            assertEquals(
                PullRequestWorkflowSelection(true, true, true),
                plan.workflows,
            )
        }
    }

    @Test
    fun `deleted known file remains scoped and rename inspects both paths`() {
        val deleted = plan(change('D', "docs/old-guide.md"))
        val scopedRename = plan(
            PullRequestPathChange(
                status = 'R',
                paths = listOf("docs/old.md", "docs/new.md"),
            ),
        )
        val unsafeRename = plan(
            PullRequestPathChange(
                status = 'R',
                paths = listOf("docs/old.md", "future/new.md"),
            ),
        )

        assertFalse(deleted.fullFallback)
        assertFalse(scopedRename.fullFallback)
        assertEquals(2, scopedRename.changedFiles)
        assertTrue(unsafeRename.fullFallback)
        assertTrue(unsafeRename.fullFallbackReasons.any { "unknown-path:future/new.md" in it })
    }

    @Test
    fun `large diff manual override non PR event and empty diff select full fallback`() {
        val large = plan(
            changes = (1..4).map { index -> change('M', "docs/page-$index.md") },
            maxChangedFiles = 3,
        )
        val manual = plan(change('M', "docs/page.md"), forceFull = true)
        val push = plan(change('M', "docs/page.md"), event = "push")
        val empty = plan()

        assertTrue(large.fullFallbackReasons.contains("large-diff:4>3"))
        assertTrue(manual.fullFallbackReasons.contains("manual-full-override"))
        assertTrue(push.fullFallbackReasons.contains("event:push"))
        assertTrue(empty.fullFallbackReasons.contains("empty-diff"))
    }

    @Test
    fun `plan JSON and workflow outputs are deterministic and machine readable`() {
        val first = plan(change('M', "docs/page.md"))
        val second = plan(change('M', "docs/page.md"))

        assertEquals(first.toJson(), second.toJson())
        val json = JsonSlurper().parseText(first.toJson()) as Map<*, *>
        assertEquals(1, (json["schemaVersion"] as Number).toInt())
        assertEquals(false, json["fullFallback"])
        assertEquals(
            "qa_quick=false\nqa_preview=false\ndocumentation=true\nfull_fallback=false\n",
            first.toGitHubOutputs(),
        )
    }

    @Test
    fun `artifact catalog and dependency contract share exact ownership`() {
        val catalog = temporaryFolder.newFile("publishing.properties").apply {
            writeText(
                "module.viewcompose-runtime.version=1.0.0\n" +
                    "module.viewcompose-ui-foundation.version=1.0.0\n",
            )
        }
        val contract = temporaryFolder.newFile("dependencies.properties").apply {
            writeText(
                "schema.version=1\n" +
                    "module.viewcompose-runtime=api=;implementation=;compileOnly=;runtimeOnly=\n" +
                    "module.viewcompose-ui-foundation=" +
                    "api=viewcompose-runtime;implementation=;compileOnly=;runtimeOnly=\n",
            )
        }

        val registered = PullRequestArtifactGraph.registeredArtifacts(catalog)
        assertEquals(
            mapOf(
                "viewcompose-runtime" to emptySet(),
                "viewcompose-ui-foundation" to setOf("viewcompose-runtime"),
            ),
            PullRequestArtifactGraph.dependencies(contract, registered),
        )
    }

    @Test
    fun `git reader preserves deletion and both rename paths`() {
        val repository = temporaryFolder.newFolder("git-repository")
        repository.git("init")
        repository.git("config", "user.name", "ViewCompose Test")
        repository.git("config", "user.email", "test@viewcompose.invalid")
        repository.resolve("deleted.txt").writeText("deleted\n")
        repository.resolve("old.txt").writeText("renamed\n")
        repository.git("add", ".")
        repository.git("commit", "-m", "base")
        val base = repository.git("rev-parse", "HEAD").trim()
        repository.resolve("deleted.txt").delete()
        repository.resolve("old.txt").renameTo(repository.resolve("new.txt"))
        repository.git("add", "-A")
        repository.git("commit", "-m", "change")
        val head = repository.git("rev-parse", "HEAD").trim()

        val changes = PullRequestGitRepository(repository).changes(base, head)
        assertTrue(changes.any { change -> change.status == 'D' && change.paths == listOf("deleted.txt") })
        assertTrue(
            changes.any { change ->
                change.status == 'R' && change.paths == listOf("old.txt", "new.txt")
            },
        )
    }

    private fun plan(
        vararg changes: PullRequestPathChange,
        event: String = "pull_request",
        forceFull: Boolean = false,
        maxChangedFiles: Int = DEFAULT_MAX_SCOPED_CHANGED_FILES,
    ): PullRequestImpactPlan = plan(
        changes = changes.toList(),
        event = event,
        forceFull = forceFull,
        maxChangedFiles = maxChangedFiles,
    )

    private fun plan(
        changes: List<PullRequestPathChange>,
        event: String = "pull_request",
        forceFull: Boolean = false,
        maxChangedFiles: Int = DEFAULT_MAX_SCOPED_CHANGED_FILES,
    ): PullRequestImpactPlan = PullRequestImpactPlanner.plan(
        baseRevision = "a".repeat(40),
        headRevision = "b".repeat(40),
        eventName = event,
        forceFull = forceFull,
        maxScopedChangedFiles = maxChangedFiles,
        changes = changes,
        policy = policy,
        artifacts = artifacts,
        dependencies = dependencies,
    )

    private fun change(status: Char, path: String): PullRequestPathChange =
        PullRequestPathChange(status, listOf(path))

    private fun File.git(vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(this).start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${arguments.joinToString(" ")} failed: $stderr" }
        return stdout
    }
}
