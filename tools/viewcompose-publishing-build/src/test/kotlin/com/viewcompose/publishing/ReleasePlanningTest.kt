package com.viewcompose.publishing

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePlanningTest {
    @Test
    fun `explicit unpublished marker permits only a genuine first release`() {
        val rootRevision = "1111111111111111111111111111111111111111"
        val sourceRevision = "2222222222222222222222222222222222222222"
        val declaredVersion = MavenVersion.parse("0.1.0-alpha01")
        val first = artifactReleaseBaseline(
            artifact = "viewcompose-image-glide",
            declaredVersion = declaredVersion,
            declaredSourceRevision = sourceRevision,
            unpublished = true,
            tags = emptyList(),
            repositoryRootRevision = rootRevision,
        )

        assertTrue(first.firstRelease)
        assertEquals(declaredVersion, first.currentVersion)
        assertEquals(rootRevision, first.comparisonRevision)
        assertEquals(sourceRevision, first.registeredSourceRevision)
        assertTrue(first.publishedTag == null)

        assertThrows(IllegalStateException::class.java) {
            artifactReleaseBaseline(
                artifact = "viewcompose-image-glide",
                declaredVersion = declaredVersion,
                declaredSourceRevision = sourceRevision,
                unpublished = false,
                tags = emptyList(),
                repositoryRootRevision = rootRevision,
            )
        }
    }

    @Test
    fun `unpublished marker and declared version must agree with release tags`() {
        val tag = MavenReleaseTag(
            name = "maven/viewcompose-runtime/0.1.0-alpha02",
            artifact = "viewcompose-runtime",
            version = MavenVersion.parse("0.1.0-alpha02"),
            sourceRevision = "1111111111111111111111111111111111111111",
            releaseRevision = "2222222222222222222222222222222222222222",
        )

        assertThrows(IllegalStateException::class.java) {
            artifactReleaseBaseline(
                artifact = "viewcompose-runtime",
                declaredVersion = tag.version,
                declaredSourceRevision = tag.sourceRevision,
                unpublished = true,
                tags = listOf(tag),
                repositoryRootRevision = tag.sourceRevision,
            )
        }
        assertThrows(IllegalStateException::class.java) {
            artifactReleaseBaseline(
                artifact = "viewcompose-runtime",
                declaredVersion = MavenVersion.parse("0.1.0-alpha03"),
                declaredSourceRevision = tag.sourceRevision,
                unpublished = false,
                tags = listOf(tag),
                repositoryRootRevision = tag.sourceRevision,
            )
        }

        val published = artifactReleaseBaseline(
            artifact = "viewcompose-runtime",
            declaredVersion = tag.version,
            declaredSourceRevision = tag.sourceRevision,
            unpublished = false,
            tags = listOf(tag),
            repositoryRootRevision = tag.sourceRevision,
        )
        assertEquals(tag.releaseRevision, published.comparisonRevision)
    }

    @Test
    fun `release source revision accepts the documented inline annotation`() {
        listOf(
            "Maven Central: viewcompose-runtime 0.1.0-alpha02; " +
                "sourceRevision=963e114e7364a9b1f100d2a918b5b9af9a40d462",
            "Maven Central: viewcompose-runtime 0.1.0-alpha02\n" +
                "sourceRevision=963e114e7364a9b1f100d2a918b5b9af9a40d462",
        ).forEach { annotation ->
            assertEquals(
                "963e114e7364a9b1f100d2a918b5b9af9a40d462",
                releaseTagSourceRevision(
                    tag = "maven/viewcompose-runtime/0.1.0-alpha02",
                    annotation = annotation,
                ),
            )
        }
    }

    @Test
    fun `release source revision rejects missing malformed or duplicate tokens`() {
        listOf(
            "Maven Central release without provenance",
            "sourceRevision=abc",
            "sourceRevision=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "sourceRevision=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa " +
                "sourceRevision=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        ).forEach { annotation ->
            assertThrows(IllegalStateException::class.java) {
                releaseTagSourceRevision(
                    tag = "maven/viewcompose-runtime/0.1.0-alpha03",
                    annotation = annotation,
                )
            }
        }
    }

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
        val dependencies = mapOf(
            "viewcompose-ui-contract" to setOf("viewcompose-runtime"),
            "viewcompose-widget-core" to setOf("viewcompose-ui-contract"),
        )
        val reverse = ViewComposeReleasePlanner.buildReverseDependencies(dependencies)

        assertEquals(setOf("viewcompose-ui-contract"), reverse.getValue("viewcompose-runtime"))
        assertEquals(setOf("viewcompose-widget-core"), reverse.getValue("viewcompose-ui-contract"))
        assertEquals(
            mapOf(
                "viewcompose-runtime" to emptySet(),
                "viewcompose-ui-contract" to setOf("viewcompose-runtime"),
                "viewcompose-widget-core" to setOf("viewcompose-ui-contract"),
            ),
            ViewComposeReleasePlanner.propagateReleaseDependencies(
                setOf("viewcompose-runtime"),
                dependencies,
            ),
        )
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

    @Test
    fun `metadata preparation keeps registered first release version and history`() {
        val directory = Files.createTempDirectory("first-release-metadata").toFile()
        val sourceRevision = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val plan = directory.resolve("plan.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "sourceRevision": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "releases": [{
                    "artifact": "viewcompose-image-glide",
                    "firstRelease": true,
                    "currentVersion": "0.1.0-alpha01",
                    "recommendedVersion": "0.1.0-alpha01",
                    "sourceRevision": "$sourceRevision"
                  }]
                }
                """.trimIndent(),
            )
        }
        val publishing = directory.resolve("publishing.properties").apply {
            writeText(
                """
                module.viewcompose-image-glide.version=0.1.0-alpha01
                module.viewcompose-image-glide.sourceRevision=$sourceRevision
                """.trimIndent() + "\n",
            )
        }
        val history = directory.resolve("history.properties").apply {
            writeText(
                """
                schema.version=1
                release.count=1

                release.0.version=0.1.0-alpha01
                release.0.sourceRevision=$sourceRevision
                release.0.modules=viewcompose-image-glide
                """.trimIndent() + "\n",
            )
        }
        val originalPublishing = publishing.readText()
        val originalHistory = history.readText()

        ReleaseMetadataPreparer.prepare(
            plan,
            publishing,
            history,
            mapOf("viewcompose-image-glide" to MavenVersion.parse("0.1.0-alpha01")),
        )

        assertEquals(originalPublishing, publishing.readText())
        assertEquals(originalHistory, history.readText())
    }
}
