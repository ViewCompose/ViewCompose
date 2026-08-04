package com.viewcompose.publishing

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePlanningTest {
    @Test
    fun `prerelease recommendation increments its numeric channel`() {
        val current = MavenVersion.parse("0.1.0-alpha01")

        assertEquals("0.1.0-alpha02", current.recommend(ReleaseImpact.Fix).toString())
        assertEquals("0.1.0-alpha02", current.recommend(ReleaseImpact.Breaking).toString())
        assertTrue(MavenVersion.parse("0.1.0-alpha10") > MavenVersion.parse("0.1.0-alpha9"))
    }

    @Test
    fun `stable recommendation follows zero-major semantic versioning`() {
        assertEquals(
            "0.3.0",
            MavenVersion.parse("0.2.7").recommend(ReleaseImpact.Breaking).toString(),
        )
        assertEquals(
            "1.5.0",
            MavenVersion.parse("1.4.2").recommend(ReleaseImpact.Feature).toString(),
        )
        assertEquals(
            "1.4.3",
            MavenVersion.parse("1.4.2").recommend(ReleaseImpact.Dependency).toString(),
        )
    }

    @Test
    fun `reverse dependencies preserve transitive release propagation`() {
        val reverse = ViewComposeReleasePlanner.buildReverseDependencies(
            mapOf(
                "viewcompose-ui-contract" to setOf("viewcompose-runtime"),
                "viewcompose-widget-core" to setOf("viewcompose-ui-contract"),
            ),
        )

        assertEquals(setOf("viewcompose-ui-contract"), reverse.getValue("viewcompose-runtime"))
        assertEquals(setOf("viewcompose-widget-core"), reverse.getValue("viewcompose-ui-contract"))
    }

    @Test
    fun `metadata preparation requires the exact planned artifact set`() {
        val directory = Files.createTempDirectory("release-metadata").toFile()
        val plan = directory.resolve("plan.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "sourceRevision": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "releases": [{
                    "artifact": "viewcompose-runtime",
                    "currentVersion": "0.1.0-alpha01"
                  }]
                }
                """.trimIndent(),
            )
        }
        val publishing = directory.resolve("publishing.properties").apply {
            writeText(
                """
                module.viewcompose-runtime.version=0.1.0-alpha01
                module.viewcompose-runtime.sourceRevision=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
                """.trimIndent(),
            )
        }
        val history = directory.resolve("history.properties").apply {
            writeText(
                """
                schema.version=1
                release.count=1

                release.0.version=0.1.0-alpha01
                release.0.sourceRevision=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
                release.0.modules=viewcompose-runtime
                """.trimIndent(),
            )
        }

        assertThrows(IllegalStateException::class.java) {
            ReleaseMetadataPreparer.prepare(
                plan,
                publishing,
                history,
                emptyMap(),
            )
        }

        ReleaseMetadataPreparer.prepare(
            plan,
            publishing,
            history,
            mapOf("viewcompose-runtime" to MavenVersion.parse("0.1.0-alpha02")),
        )
        assertTrue(publishing.readText().contains("version=0.1.0-alpha02"))
        assertTrue(history.readText().contains("release.count=2"))
        assertTrue(history.readText().contains("release.1.modules=viewcompose-runtime"))
    }
}
