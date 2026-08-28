package com.viewcompose.publishing

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePlanningTest {
    @Test
    fun `ignored ownership does not schedule a published artifact release`() {
        val artifact = "viewcompose-runtime"
        val planner = singleArtifactPlanner(
            artifact = artifact,
            published = true,
            changeSetJson =
                """
                {
                  "schemaVersion": 1,
                  "summary": "Classify a compiled documentation fixture without publishing runtime.",
                  "ignored": [{
                    "artifact":"$artifact",
                    "reason":"Only a test-only compiled documentation fixture changed."
                  }]
                }
                """.trimIndent(),
        )

        assertTrue(planner.plan().releases.isEmpty())
    }

    @Test
    fun `ignored ownership cannot replace a first release declaration`() {
        val artifact = "viewcompose-new"
        val planner = singleArtifactPlanner(
            artifact = artifact,
            published = false,
            changeSetJson =
                """
                {
                  "schemaVersion": 1,
                  "summary": "Attempt to ignore every publication path before the first release.",
                  "ignored": [{
                    "artifact":"$artifact",
                    "reason":"The detected source is claimed to be release-neutral."
                  }]
                }
                """.trimIndent(),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan()
        }

        assertTrue(error.message.orEmpty().contains("no release changeset"))
    }

    @Test
    fun `first release planning accepts retired artifacts in historical changesets`() {
        val directory = Files.createTempDirectory("retired-release-history").toFile()
        val rootRevision = "1111111111111111111111111111111111111111"
        val sourceRevision = "2222222222222222222222222222222222222222"
        val headRevision = "3333333333333333333333333333333333333333"
        val activeArtifact = "viewcompose-new"
        val retiredArtifact = "viewcompose-old"
        val changeSetPath = "release/changes/historical-retired-artifact.json"
        directory.resolve(changeSetPath).apply {
            parentFile.mkdirs()
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "summary": "Classify active and subsequently retired artifact history.",
                  "changes": [
                    {"artifact":"$activeArtifact","impact":"feature"},
                    {"artifact":"$retiredArtifact","impact":"fix"}
                  ]
                }
                """.trimIndent(),
            )
        }
        val git = GitRepository(
            root = directory,
            executor = CommandExecutor { arguments ->
                when (arguments) {
                    listOf("status", "--porcelain", "--untracked-files=all") ->
                        CommandResult(0, "")
                    listOf("rev-parse", "--verify", "HEAD^{commit}") ->
                        CommandResult(0, headRevision)
                    listOf("rev-parse", "--verify", "$sourceRevision^{commit}") ->
                        CommandResult(0, sourceRevision)
                    listOf("rev-list", "--max-parents=0", "HEAD") ->
                        CommandResult(0, rootRevision)
                    listOf("tag", "--list", "maven/$activeArtifact/*") ->
                        CommandResult(0, "")
                    listOf("diff", "--name-only", "-z", "$rootRevision..$headRevision", "--") ->
                        CommandResult(0, "$changeSetPath\u0000")
                    else -> error("Unexpected Git command: ${arguments.joinToString(" ")}")
                }
            },
        )

        val plan = ViewComposeReleasePlanner(
            root = directory,
            git = git,
            artifacts = setOf(activeArtifact),
            declaredVersions = mapOf(activeArtifact to MavenVersion.parse("0.1.0-alpha01")),
            declaredSourceRevisions = mapOf(activeArtifact to sourceRevision),
            unpublishedArtifacts = setOf(activeArtifact),
            retiredArtifacts = setOf(retiredArtifact),
            dependencies = mapOf(activeArtifact to emptySet()),
        ).plan()

        assertEquals(listOf(activeArtifact), plan.releases.map(PlannedArtifactRelease::artifact))
        assertEquals(listOf("historical-retired-artifact.json"), plan.releases.single().changeSets)
        assertEquals("0.1.0-alpha01", plan.releases.single().recommendedVersion.toString())
        assertEquals(headRevision, plan.releases.single().sourceRevision)
    }

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
            "viewcompose-ui-foundation" to setOf("viewcompose-ui-contract"),
        )
        val reverse = ViewComposeReleasePlanner.buildReverseDependencies(dependencies)

        assertEquals(setOf("viewcompose-ui-contract"), reverse.getValue("viewcompose-runtime"))
        assertEquals(setOf("viewcompose-ui-foundation"), reverse.getValue("viewcompose-ui-contract"))
        assertEquals(
            mapOf(
                "viewcompose-runtime" to emptySet(),
                "viewcompose-ui-contract" to setOf("viewcompose-runtime"),
                "viewcompose-ui-foundation" to setOf("viewcompose-ui-contract"),
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
    fun `metadata preparation freezes first release source and keeps version and history`() {
        val directory = Files.createTempDirectory("first-release-metadata").toFile()
        val registeredSourceRevision = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val frozenSourceRevision = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val plan = directory.resolve("plan.json").apply {
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "sourceRevision": "$frozenSourceRevision",
                  "releases": [{
                    "artifact": "viewcompose-image-glide",
                    "firstRelease": true,
                    "currentVersion": "0.1.0-alpha01",
                    "recommendedVersion": "0.1.0-alpha01",
                    "sourceRevision": "$frozenSourceRevision"
                  }]
                }
                """.trimIndent(),
            )
        }
        val publishing = directory.resolve("publishing.properties").apply {
            writeText(
                """
                module.viewcompose-image-glide.version=0.1.0-alpha01
                module.viewcompose-image-glide.sourceRevision=$registeredSourceRevision
                """.trimIndent() + "\n",
            )
        }
        val history = directory.resolve("history.properties").apply {
            writeText(
                """
                schema.version=1
                release.count=1

                release.0.version=0.1.0-alpha01
                release.0.sourceRevision=$registeredSourceRevision
                release.0.modules=viewcompose-image-glide
                """.trimIndent() + "\n",
            )
        }
        val originalHistory = history.readText()

        ReleaseMetadataPreparer.prepare(
            plan,
            publishing,
            history,
            mapOf("viewcompose-image-glide" to MavenVersion.parse("0.1.0-alpha01")),
        )

        assertTrue(publishing.readText().contains("version=0.1.0-alpha01"))
        assertTrue(publishing.readText().contains("sourceRevision=$frozenSourceRevision"))
        assertEquals(originalHistory, history.readText())
    }

    private fun singleArtifactPlanner(
        artifact: String,
        published: Boolean,
        changeSetJson: String,
    ): ViewComposeReleasePlanner {
        val directory = Files.createTempDirectory("release-planning-classification").toFile()
        val rootRevision = "1111111111111111111111111111111111111111"
        val sourceRevision = "2222222222222222222222222222222222222222"
        val releaseRevision = "3333333333333333333333333333333333333333"
        val headRevision = "4444444444444444444444444444444444444444"
        val version = MavenVersion.parse("0.1.0-alpha01")
        val tag = "maven/$artifact/$version"
        val changeSetPath = "release/changes/classify-owned-artifact.json"
        val ownedPath = "$artifact/src/test/samples/CompiledSample.kt"
        directory.resolve(changeSetPath).apply {
            parentFile.mkdirs()
            writeText(changeSetJson)
        }
        val comparisonRevision = if (published) releaseRevision else rootRevision
        val git = GitRepository(
            root = directory,
            executor = CommandExecutor { arguments ->
                when (arguments) {
                    listOf("status", "--porcelain", "--untracked-files=all") ->
                        CommandResult(0, "")
                    listOf("rev-parse", "--verify", "HEAD^{commit}") ->
                        CommandResult(0, headRevision)
                    listOf("rev-parse", "--verify", "$sourceRevision^{commit}") ->
                        CommandResult(0, sourceRevision)
                    listOf("rev-list", "--max-parents=0", "HEAD") ->
                        CommandResult(0, rootRevision)
                    listOf("tag", "--list", "maven/$artifact/*") ->
                        CommandResult(0, if (published) tag else "")
                    listOf("cat-file", "-p", "refs/tags/$tag") ->
                        CommandResult(
                            0,
                            "sourceRevision=$sourceRevision\n-----BEGIN PGP SIGNATURE-----",
                        )
                    listOf("tag", "-v", tag) ->
                        CommandResult(0, "Good signature")
                    listOf("rev-parse", "--verify", "$tag^{commit}") ->
                        CommandResult(0, releaseRevision)
                    listOf(
                        "diff",
                        "--name-only",
                        "-z",
                        "$comparisonRevision..$headRevision",
                        "--",
                    ) -> CommandResult(0, "$ownedPath\u0000$changeSetPath\u0000")
                    else -> error("Unexpected Git command: ${arguments.joinToString(" ")}")
                }
            },
        )
        return ViewComposeReleasePlanner(
            root = directory,
            git = git,
            artifacts = setOf(artifact),
            declaredVersions = mapOf(artifact to version),
            declaredSourceRevisions = mapOf(artifact to sourceRevision),
            unpublishedArtifacts = if (published) emptySet() else setOf(artifact),
            retiredArtifacts = emptySet(),
            dependencies = mapOf(artifact to emptySet()),
        )
    }
}
