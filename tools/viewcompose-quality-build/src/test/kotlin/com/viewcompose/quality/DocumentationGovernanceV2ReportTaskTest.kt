package com.viewcompose.quality

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentationGovernanceV2ReportTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `frozen governance fixtures validate in isolation`() {
        val repository = locateRepository()
        val contractRoot = repository.resolve(
            "docs/project/contracts/documentation-governance-v2",
        )
        val result = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = contractRoot.walkTopDown().filter(File::isFile).toSet(),
            recordFiles = emptySet(),
            sourceSetDirectories = emptySet(),
            activeDocumentationFiles = emptySet(),
            localeMirrorFiles = emptySet(),
            publishingFiles = setOf(
                repository.resolve("gradle/viewcompose-publishing.properties"),
                repository.resolve("gradle/viewcompose-documentation-releases.properties"),
            ),
        )

        assertTrue(result.contractViolations.joinToString("\n"), result.contractViolations.isEmpty())
        assertTrue(result.report.contains("\"contractCount\": 5"))
        assertTrue(result.report.contains("\"contractViolationCount\": 0"))
    }

    @Test
    fun `report discovery is deterministic and repository debt stays non-blocking`() {
        val repository = temporaryFolder.newFolder("report-repository")
        val contractRoot = repository.resolve(
            "docs/project/contracts/documentation-governance-v2",
        )
        fixtureContract(contractRoot)
        val sourceRoot = repository.resolve("viewcompose-example/src").apply { mkdirs() }
        repository.resolve("viewcompose-example/src/main/java/example/Example.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package example

                fun UiTreeBuilder.VisibleDsl() = Unit
                private fun UiTreeBuilder.HiddenDsl() = Unit
                fun Modifier.visibleModifier() = this
                """.trimIndent(),
            )
        }
        val currentDocument = repository.resolve("docs/guides/current.md").apply {
            parentFile.mkdirs()
            writeText(
                """
                ---
                schema_version: 2
                document_id: guide.current
                doc_type: guide
                version_lane: next
                ---

                {/* sample_id="sample.current" */}
                ```kotlin
                VisibleDsl()
                ```
                """.trimIndent(),
            )
        }
        val legacyDocument = repository.resolve("docs/guides/legacy.md").apply {
            writeText(
                """
                # Legacy

                ```java
                legacy();
                ```
                """.trimIndent(),
            )
        }
        val localeMirror = repository.resolve(
            "website/i18n/zh-CN/docusaurus-plugin-content-docs/current/guides/current.md",
        ).apply {
            parentFile.mkdirs()
            writeText(
                """
                ---
                translation_source: guides/current.md
                translation_source_hash: abc
                translation_status: current
                ---
                """.trimIndent(),
            )
        }
        val publishing = repository.resolve("gradle/viewcompose-publishing.properties").apply {
            parentFile.mkdirs()
            writeText(
                """
                release.unpublishedModules=viewcompose-example
                module.viewcompose-example.version=1.0.0
                module.viewcompose-example.sourceRevision=abc
                """.trimIndent(),
            )
        }
        val releases = repository.resolve(
            "gradle/viewcompose-documentation-releases.properties",
        ).apply {
            writeText("release.count=0\n")
        }
        val inputs = contractRoot.walkTopDown().filter(File::isFile).toSet()
        val first = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = emptySet(),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
        )
        val second = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs.reversed().toSet(),
            recordFiles = emptySet(),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(legacyDocument, currentDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(releases, publishing),
        )

        assertEquals(first.report, second.report)
        assertTrue(first.contractViolations.isEmpty())
        assertEquals(2, first.issueCount)
        assertTrue(first.report.contains("example.UiTreeBuilder.VisibleDsl"))
        assertTrue(first.report.contains("example.Modifier.visibleModifier"))
        assertFalse(first.report.contains("HiddenDsl"))
        assertTrue(first.report.contains("website/i18n/zh-CN"))
        assertTrue(first.report.contains("\"versionState\": \"next\""))
    }

    private fun fixtureContract(contractRoot: File) {
        contractRoot.resolve("fixtures/accepted").mkdirs()
        contractRoot.resolve("fixtures/rejected").mkdirs()
        contractRoot.resolve("contract-set.json").writeText(
            """
            {
              "contracts": [
                {
                  "id": "sample",
                  "schema": "sample.schema.json",
                  "accepted": ["fixtures/accepted/sample.json"],
                  "rejected": [
                    {
                      "fixture": "fixtures/rejected/sample.json",
                      "rule": "name is required"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        contractRoot.resolve("sample.schema.json").writeText(
            """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["name"],
              "properties": {
                "name": {"type": "string", "minLength": 1}
              }
            }
            """.trimIndent(),
        )
        contractRoot.resolve("fixtures/accepted/sample.json").writeText("{\"name\":\"ok\"}\n")
        contractRoot.resolve("fixtures/rejected/sample.json").writeText("{}\n")
    }

    private fun locateRepository(): File {
        var candidate = File(System.getProperty("user.dir")).canonicalFile
        while (!candidate.resolve(
                "docs/project/contracts/documentation-governance-v2/contract-set.json",
            ).isFile
        ) {
            candidate = candidate.parentFile
                ?: error("Cannot locate the ViewCompose repository from ${System.getProperty("user.dir")}")
        }
        return candidate
    }
}
