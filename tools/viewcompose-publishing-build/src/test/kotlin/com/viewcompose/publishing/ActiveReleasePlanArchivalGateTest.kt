package com.viewcompose.publishing

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveReleasePlanArchivalGateTest {
    @Test
    fun `active plan blocks direct and dependency-propagated Central uploads`() {
        val fixture = fixture(
            planBody = "- `release/changes/runtime-change.json`",
            changes = listOf("viewcompose-runtime"),
        )
        val failure = assertThrows(IllegalStateException::class.java) {
            fixture.verify(
                selectedArtifacts = setOf("viewcompose-host-android"),
                dependencies = mapOf(
                    "viewcompose-ui-contract" to setOf("viewcompose-runtime"),
                    "viewcompose-host-android" to setOf("viewcompose-ui-contract"),
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("docs/project/plans/example-plan.md"))
        assertTrue(failure.message.orEmpty().contains("viewcompose-host-android"))
    }

    @Test
    fun `unrelated selection and a plan without production changesets pass`() {
        val linked = fixture(
            planBody = "- `release/changes/runtime-change.json`",
            changes = listOf("viewcompose-runtime"),
        )
        val linkedResult = linked.verify(
            selectedArtifacts = setOf("viewcompose-image-coil"),
            dependencies = emptyMap(),
        )
        assertEquals(1, linkedResult.activePlanCount)
        assertEquals(1, linkedResult.linkedChangeSetCount)

        val unstarted = fixture(
            planBody = "- None. Implementation has not started.",
            changes = emptyList(),
        )
        val unstartedResult = unstarted.verify(
            selectedArtifacts = setOf("viewcompose-runtime"),
            dependencies = emptyMap(),
        )
        assertEquals(1, unstartedResult.activePlanCount)
        assertEquals(0, unstartedResult.linkedChangeSetCount)
    }

    @Test
    fun `every active plan requires deterministic release changeset metadata`() {
        val fixture = fixture(
            planBody = "No machine-readable declaration.",
            changes = emptyList(),
            includeHeading = false,
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            fixture.verify(
                selectedArtifacts = setOf("viewcompose-runtime"),
                dependencies = emptyMap(),
            )
        }
        assertTrue(failure.message.orEmpty().contains("## Maven release changesets"))
    }

    @Test
    fun `standalone gate requires an explicit publication selection`() {
        val fixture = fixture(
            planBody = "- None. Implementation has not started.",
            changes = emptyList(),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            fixture.verify(selectedArtifacts = emptySet(), dependencies = emptyMap())
        }
        assertTrue(failure.message.orEmpty().contains("-PviewComposePublishModules"))
    }

    @Test
    fun `a changeset cannot belong to two active plans`() {
        val fixture = fixture(
            planBody = "- `release/changes/runtime-change.json`",
            changes = listOf("viewcompose-runtime"),
        )
        fixture.plansDirectory.resolve("second-plan.md").writeText(
            """
            # Second plan

            ## Maven release changesets

            - `release/changes/runtime-change.json`
            """.trimIndent() + "\n",
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            fixture.verify(
                selectedArtifacts = setOf("viewcompose-runtime"),
                dependencies = emptyMap(),
            )
        }
        assertTrue(failure.message.orEmpty().contains("exactly one active plan"))
    }

    private fun fixture(
        planBody: String,
        changes: List<String>,
        includeHeading: Boolean = true,
    ): GateFixture {
        val root = Files.createTempDirectory("active-release-plan").toFile()
        val plans = root.resolve("docs/project/plans").apply(File::mkdirs)
        val releaseChanges = root.resolve("release/changes").apply(File::mkdirs)
        val heading = if (includeHeading) "## Maven release changesets\n\n" else ""
        plans.resolve("example-plan.md").writeText(
            "# Example plan\n\n$heading$planBody\n",
        )
        if (changes.isNotEmpty()) {
            releaseChanges.resolve("runtime-change.json").writeText(
                """
                {
                  "schemaVersion": 1,
                  "summary": "Exercise the active release plan archival gate.",
                  "changes": [
                    ${changes.joinToString(",\n") { artifact ->
                        "{ \"artifact\": \"$artifact\", \"impact\": \"fix\" }"
                    }}
                  ]
                }
                """.trimIndent() + "\n",
            )
        }
        return GateFixture(root, plans)
    }

    private data class GateFixture(
        val root: File,
        val plansDirectory: File,
    ) {
        private val knownArtifacts = setOf(
            "viewcompose-runtime",
            "viewcompose-ui-contract",
            "viewcompose-host-android",
            "viewcompose-image-coil",
        )

        fun verify(
            selectedArtifacts: Set<String>,
            dependencies: Map<String, Set<String>>,
        ): ActiveReleasePlanArchivalVerification = ActiveReleasePlanArchivalGate.verify(
            repositoryRoot = root,
            plansDirectory = plansDirectory,
            selectedArtifacts = selectedArtifacts,
            knownArtifacts = knownArtifacts,
            dependencies = dependencies,
        )
    }
}
