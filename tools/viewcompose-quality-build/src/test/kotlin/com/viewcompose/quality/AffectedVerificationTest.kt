package com.viewcompose.quality

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AffectedVerificationTest {
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
    private val projectTasks = artifacts.associate { artifact ->
        val tasks = if (artifact == "viewcompose-runtime") {
            setOf("compileKotlin", "test")
        } else {
            setOf("compileDebugKotlin", "testDebugUnitTest")
        }
        ":$artifact" to tasks
    }

    @Test
    fun `candidate derives artifact tasks and verifies both graph closures`() {
        val plan = AffectedVerificationPlanner.plan(
            input = AffectedVerificationInput(
                gateFamilies = setOf(
                    PullRequestGateFamily.ModuleVerification,
                    PullRequestGateFamily.ReleaseIntent,
                ),
                directArtifacts = setOf("viewcompose-ui-foundation"),
                directProjects = emptySet(),
                dependencyClosure = setOf("viewcompose-runtime"),
                reverseDependentClosure = setOf(
                    "viewcompose-material3",
                    "viewcompose-preview",
                    "viewcompose-renderer-android",
                ),
            ),
            registeredArtifacts = artifacts,
            actualDependencies = dependencies,
            projectTasks = projectTasks,
        )

        assertEquals(artifacts, plan.selectedArtifacts)
        assertTrue(":viewcompose-runtime:compileKotlin" in plan.taskPaths)
        assertTrue(":viewcompose-runtime:test" in plan.taskPaths)
        assertTrue(":viewcompose-material3:compileDebugKotlin" in plan.taskPaths)
        assertTrue(":viewcompose-material3:testDebugUnitTest" in plan.taskPaths)
        assertTrue("verifyModuleDependencyBoundaries" in plan.taskPaths)
        assertTrue("verifyViewComposeReleaseIntent" in plan.taskPaths)
    }

    @Test
    fun `candidate rejects classifier closure drift from configured graph`() {
        val error = assertThrows(IllegalStateException::class.java) {
            AffectedVerificationPlanner.plan(
                input = AffectedVerificationInput(
                    gateFamilies = setOf(PullRequestGateFamily.ModuleVerification),
                    directArtifacts = setOf("viewcompose-ui-foundation"),
                    directProjects = emptySet(),
                    dependencyClosure = emptySet(),
                    reverseDependentClosure = setOf(
                        "viewcompose-material3",
                        "viewcompose-preview",
                        "viewcompose-renderer-android",
                    ),
                ),
                registeredArtifacts = artifacts,
                actualDependencies = dependencies,
                projectTasks = projectTasks,
            )
        }

        assertTrue(error.message.orEmpty().contains("dependency closure differs"))
        assertTrue(error.message.orEmpty().contains("viewcompose-runtime"))
    }

    @Test
    fun `candidate owns non-published project tasks by gate family`() {
        val scopedTasks = projectTasks + mapOf(
            ":app" to setOf("compileDebugKotlin", "testDebugUnitTest"),
            ":samples:tutorials" to setOf("assembleDebug", "compileDebugAndroidTestKotlin"),
            ":integration-tests:paging-presenter" to setOf("test"),
            ":viewcompose-benchmark" to setOf("compileBenchmarkKotlin"),
        )
        val plan = AffectedVerificationPlanner.plan(
            input = AffectedVerificationInput(
                gateFamilies = setOf(
                    PullRequestGateFamily.Demo,
                    PullRequestGateFamily.Samples,
                    PullRequestGateFamily.IntegrationTests,
                    PullRequestGateFamily.DeviceAndBenchmark,
                ),
                directArtifacts = emptySet(),
                directProjects = setOf(
                    ":app",
                    ":samples:tutorials",
                    ":integration-tests:paging-presenter",
                    ":viewcompose-benchmark",
                ),
                dependencyClosure = emptySet(),
                reverseDependentClosure = emptySet(),
            ),
            registeredArtifacts = artifacts,
            actualDependencies = dependencies,
            projectTasks = scopedTasks,
        )

        assertTrue(":app:testDebugUnitTest" in plan.taskPaths)
        assertTrue(":samples:tutorials:assembleDebug" in plan.taskPaths)
        assertTrue(":integration-tests:paging-presenter:test" in plan.taskPaths)
        assertTrue(":viewcompose-benchmark:compileBenchmarkKotlin" in plan.taskPaths)
        assertTrue("publishViewComposeToLocalRepository" in plan.taskPaths)
    }

    @Test
    fun `configured dependency graph reads project edges instead of a task catalog`() {
        val root = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder("repository"))
            .build()
        val runtime = ProjectBuilder.builder()
            .withName("viewcompose-runtime")
            .withParent(root)
            .build()
        val foundation = ProjectBuilder.builder()
            .withName("viewcompose-ui-foundation")
            .withParent(root)
            .build()
        runtime.configurations.create("api")
        foundation.configurations.create("api")
        foundation.dependencies.add(
            "api",
            foundation.dependencies.project(mapOf("path" to runtime.path)),
        )

        assertEquals(
            mapOf(
                "viewcompose-runtime" to emptySet(),
                "viewcompose-ui-foundation" to setOf("viewcompose-runtime"),
            ),
            root.affectedProjectDependencies(
                setOf("viewcompose-runtime", "viewcompose-ui-foundation"),
            ),
        )
    }

    @Test
    fun `environment transport rejects unknown gate families`() {
        assertThrows(GradleException::class.java) {
            AffectedVerificationInput.fromEnvironment(
                mapOf("VIEWCOMPOSE_AFFECTED_GATE_FAMILIES" to "module-verification,unknown"),
            )
        }
    }
}
