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
            documentationPolicyFiles = setOf(
                repository.resolve("website/i18n/translation-policy.json"),
                repository.resolve("docs/modules/README.md"),
            ),
        )

        assertTrue(result.contractViolations.joinToString("\n"), result.contractViolations.isEmpty())
        assertTrue(result.report.contains("\"contractCount\": 5"))
        assertTrue(result.report.contains("\"contractViolationCount\": 0"))
    }

    @Test
    fun `discovery is deterministic and unbaselined debt is blocking`() {
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

                {/* compiled-region source="samples/current.kt" region="current" sample_id="sample.current" */}
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

                {/* compiled-region source="samples/current.kt" region="current" sample_id="sample.current" */}
                ```kotlin
                VisibleDsl()
                ```
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
        val translationPolicy = repository.resolve("website/i18n/translation-policy.json").apply {
            parentFile.mkdirs()
            writeText(
                """
                {
                  "schemaVersion": 1,
                  "locale": "zh-CN",
                  "required": ["guides/current.md", "guides/legacy.md"]
                }
                """.trimIndent(),
            )
        }
        val moduleCatalog = repository.resolve("docs/modules/README.md").apply {
            parentFile.mkdirs()
            writeText(
                """
                | Artifact | Family | Runtime role | Manual |
                | --- | --- | --- | --- |
                | `viewcompose-example` | UI Foundation | Test | Available |
                """.trimIndent(),
            )
        }
        val baseline = repository.resolve(
            "docs/project/records/documentation-governance-v2/exceptions/DOC-0001.json",
        ).apply {
            parentFile.mkdirs()
            writeText(
                """
                {
                  "schema_version": 2,
                  "exception_id": "DOC-0001",
                  "target": {"file": "docs/guides/legacy.md"},
                  "category": "unclassified-sample",
                  "reason": "The legacy fence still needs a compiled source owner.",
                  "owner": "documentation-governance",
                  "created_on": "2026-08-26",
                  "removal_condition": "Delete this exact entry when the legacy fence is registered.",
                  "violation_count": 1
                }
                """.trimIndent(),
            )
        }
        val inputs = contractRoot.walkTopDown().filter(File::isFile).toSet()
        val first = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
        )
        val second = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs.reversed().toSet(),
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(legacyDocument, currentDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(releases, publishing),
            documentationPolicyFiles = setOf(moduleCatalog, translationPolicy),
        )

        assertEquals(first.report, second.report)
        assertTrue(first.contractViolations.isEmpty())
        assertEquals(5, first.issueCount)
        assertTrue(first.report.contains("example.UiTreeBuilder.VisibleDsl"))
        assertTrue(first.report.contains("example.Modifier.visibleModifier"))
        assertFalse(first.report.contains("HiddenDsl"))
        assertTrue(first.report.contains("website/i18n/zh-CN"))
        assertTrue(first.report.contains("\"versionState\": \"next\""))
        assertTrue(first.report.contains("\"exactEntryCount\": 1"))
        assertTrue(first.report.contains("\"unbaselinedIssueCount\": 4"))
        assertEquals(4, first.ratchetViolations.size)
        assertTrue(first.report.contains("\"status\": \"failed\""))
        assertTrue(first.report.contains("\"contractViolations\": []"))
        assertTrue(first.report.contains("\"ratchetViolations\": ["))
        assertTrue(first.humanReport.contains("Gate: failed; violations: 4"))

        legacyDocument.appendText(
            """

            ```kotlin
            anotherLegacyCall()
            ```
            """.trimIndent(),
        )
        val broadened = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
        )
        assertTrue(broadened.report.contains("\"status\": \"broadened\""))
        assertTrue(broadened.report.contains("\"actualCount\": 2"))
        assertTrue(
            broadened.ratchetViolations.any { violation ->
                violation.contains("DOC-0001 is broadened")
            },
        )
    }

    @Test
    fun `git ratchet permits only monotonic violation count reduction`() {
        val repository = temporaryFolder.newFolder("ratchet-repository")
        val path =
            "docs/project/records/documentation-governance-v2/exceptions/DOC-0001.json"
        val current = repository.resolve(path).apply {
            parentFile.mkdirs()
            writeText(exceptionFixture(count = 2))
        }
        val previous = exceptionFixture(count = 3)
        val executor = ratchetExecutor(
            diff = "M\t$path\n",
            baseFiles = mapOf(path to previous),
        )

        val reduced = DocumentationGovernanceV2GitRatchet.inspect(
            repository = repository,
            explicitBaseRevision = "base",
            executor = executor,
        )
        assertTrue(reduced.violations.joinToString("\n"), reduced.violations.isEmpty())

        current.writeText(exceptionFixture(count = 2, category = "taxonomy-mismatch"))
        val retargeted = DocumentationGovernanceV2GitRatchet.inspect(
            repository = repository,
            explicitBaseRevision = "base",
            executor = executor,
        )
        assertTrue(
            retargeted.violations.single().contains("immutable exception identity"),
        )
    }

    @Test
    fun `git ratchet rejects a new exception and permits deletion`() {
        val repository = temporaryFolder.newFolder("ratchet-add-delete")
        val path =
            "docs/project/records/documentation-governance-v2/exceptions/DOC-0312.json"

        val added = DocumentationGovernanceV2GitRatchet.inspect(
            repository = repository,
            explicitBaseRevision = "base",
            executor = ratchetExecutor(diff = "A\t$path\n"),
        )
        assertTrue(added.violations.single().contains("cannot grow or re-add"))

        val deleted = DocumentationGovernanceV2GitRatchet.inspect(
            repository = repository,
            explicitBaseRevision = "base",
            executor = ratchetExecutor(diff = "D\t$path\n"),
        )
        assertTrue(deleted.violations.joinToString("\n"), deleted.violations.isEmpty())
    }

    private fun ratchetExecutor(
        diff: String,
        baseFiles: Map<String, String> = emptyMap(),
    ) = DocumentationGovernanceV2GitCommandExecutor { arguments ->
        val output = when (arguments.firstOrNull()) {
            "rev-parse" -> "base\n"
            "diff" -> diff
            "ls-files" -> ""
            "show" -> baseFiles.getValue(arguments.last().substringAfter(':'))
            else -> error("Unexpected git arguments: $arguments")
        }
        DocumentationGovernanceV2GitCommandResult(exitCode = 0, output = output)
    }

    private fun exceptionFixture(
        count: Int,
        category: String = "unclassified-sample",
    ): String =
        """
        {
          "schema_version": 2,
          "exception_id": "DOC-0001",
          "target": {"file": "docs/guides/legacy.md"},
          "category": "$category",
          "reason": "The legacy fence still needs a compiled source owner.",
          "owner": "documentation-governance",
          "created_on": "2026-08-26",
          "removal_condition": "Delete this exact entry when the legacy fence is registered.",
          "violation_count": $count
        }
        """.trimIndent()

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
                },
                {
                  "id": "exception",
                  "schema": "exception.schema.json",
                  "accepted": ["fixtures/accepted/exception.json"],
                  "rejected": [
                    {
                      "fixture": "fixtures/rejected/exception.json",
                      "rule": "target is required"
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
        contractRoot.resolve("exception.schema.json").writeText(
            """
            {
              "type": "object",
              "required": ["exception_id", "target", "category", "violation_count"],
              "properties": {
                "exception_id": {"type": "string"},
                "target": {"type": "object"},
                "category": {"type": "string"},
                "violation_count": {"type": "integer", "minimum": 1}
              }
            }
            """.trimIndent(),
        )
        contractRoot.resolve("fixtures/accepted/exception.json").writeText(
            """{"exception_id":"DOC-0001","target":{"file":"docs/a.md"},"category":"missing-metadata","violation_count":1}""",
        )
        contractRoot.resolve("fixtures/rejected/exception.json").writeText("{}\n")
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
