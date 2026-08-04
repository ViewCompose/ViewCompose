package com.viewcompose.publishing

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseIntentTest {
    private val artifacts = setOf("viewcompose-runtime", "viewcompose-widget-core")

    @Test
    fun `parser accepts direct ignored and shared classifications`() {
        val directory = Files.createTempDirectory("release-intent").toFile()
        val file = directory.resolve("runtime-fix.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "summary": "Classify runtime source and shared build input changes.",
                  "changes": [{"artifact":"viewcompose-runtime","impact":"fix"}],
                  "ignored": [{"artifact":"viewcompose-widget-core","reason":"Test-only fixture update."}],
                  "shared": [{"path":"build.gradle.kts","reason":"Verification wiring only."}]
                }
                """.trimIndent(),
            )
        }

        val result = ReleaseChangeSetParser.parse(file, artifacts)

        assertEquals(ReleaseImpact.Fix, result.changes.single().impact)
        assertEquals("viewcompose-widget-core", result.ignored.single().artifact)
        assertEquals("build.gradle.kts", result.shared.single().path)
    }

    @Test
    fun `ownership includes production sources module metadata and compiled samples only`() {
        val result = ReleaseOwnership.classify(
            listOf(
                "viewcompose-runtime/src/main/kotlin/Runtime.kt",
                "viewcompose-runtime/src/test/kotlin/RuntimeTest.kt",
                "viewcompose-widget-core/src/test/samples/Sample.kt",
                "viewcompose-widget-core/build.gradle.kts",
                "gradle/libs.versions.toml",
            ),
            artifacts,
        )

        assertEquals(
            listOf("viewcompose-runtime/src/main/kotlin/Runtime.kt"),
            result.artifactPaths.getValue("viewcompose-runtime"),
        )
        assertEquals(2, result.artifactPaths.getValue("viewcompose-widget-core").size)
        assertEquals(listOf("gradle/libs.versions.toml"), result.sharedPaths)
    }

    @Test
    fun `verifier rejects an unclassified changed artifact`() {
        val error = assertThrows(IllegalStateException::class.java) {
            ReleaseIntentVerifier.verify(
                ownership = ReleaseOwnershipResult(
                    artifactPaths = mapOf("viewcompose-runtime" to listOf("runtime.kt")),
                    sharedPaths = emptyList(),
                ),
                changeSets = emptyList(),
            )
        }

        assertTrue(error.message.orEmpty().contains("viewcompose-runtime"))
    }

    @Test
    fun `verifier rejects an ignored artifact without an owned change`() {
        val directory = Files.createTempDirectory("release-intent-stale").toFile()
        val file = directory.resolve("stale-ignore.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "summary": "Attempt to ignore an artifact that did not change.",
                  "ignored": [{
                    "artifact":"viewcompose-runtime",
                    "reason":"No matching production path exists."
                  }]
                }
                """.trimIndent(),
            )
        }
        val changeSet = ReleaseChangeSetParser.parse(file, artifacts)

        assertThrows(IllegalStateException::class.java) {
            ReleaseIntentVerifier.verify(
                ReleaseOwnershipResult(emptyMap(), emptyList()),
                listOf(changeSet),
            )
        }
    }
}
