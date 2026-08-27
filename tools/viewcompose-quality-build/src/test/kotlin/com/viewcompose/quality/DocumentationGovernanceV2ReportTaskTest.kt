package com.viewcompose.quality

import groovy.json.JsonSlurper
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
    fun `renderer application packages expose host tooling and integration capability entries`() {
        val repository = temporaryFolder.newFolder("renderer-capability-repository")
        val contractRoot = repository.resolve(
            "docs/project/contracts/documentation-governance-v2",
        )
        fixtureContract(contractRoot)
        val sourceRoot = repository.resolve("viewcompose-renderer-android/src").apply { mkdirs() }
        repository.resolve(
            "viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/tree/Renderer.kt",
        ).apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.viewcompose.renderer.view.tree

                object ViewTreeRenderer
                fun interface RenderTreeTimingCollector
                private object HiddenRenderer
                """.trimIndent(),
            )
        }
        repository.resolve(
            "viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/reconcile/Reconcile.kt",
        ).apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.viewcompose.renderer.reconcile

                object ChildReconciler
                """.trimIndent(),
            )
        }
        repository.resolve(
            "viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/decoration/Decoration.kt",
        ).apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.viewcompose.renderer.decoration

                interface AndroidViewDecorationBackend
                open class ViewDecorationHostLayout
                """.trimIndent(),
            )
        }
        repository.resolve(
            "viewcompose-renderer-android/src/main/java/com/viewcompose/renderer/view/container/Internal.kt",
        ).apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.viewcompose.renderer.view.container

                class DeclarativeInternalLayout
                """.trimIndent(),
            )
        }
        val publishing = repository.resolve("gradle/viewcompose-publishing.properties").apply {
            parentFile.mkdirs()
            writeText(
                """
                release.unpublishedModules=viewcompose-renderer-android
                module.viewcompose-renderer-android.version=1.0.0
                module.viewcompose-renderer-android.sourceRevision=abc
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
            writeText("""{"schemaVersion":1,"locale":"zh-CN","required":[]}""")
        }
        val moduleCatalog = repository.resolve("docs/modules/README.md").apply {
            parentFile.mkdirs()
            writeText(
                """
                | Artifact | Family | Runtime role | Manual |
                | --- | --- | --- | --- |
                | `viewcompose-renderer-android` | Android Engine | Test | Available |
                """.trimIndent(),
            )
        }

        val result = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = contractRoot.walkTopDown().filter(File::isFile).toSet(),
            recordFiles = emptySet(),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = emptySet(),
            localeMirrorFiles = emptySet(),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
        )
        @Suppress("UNCHECKED_CAST")
        val report = JsonSlurper().parseText(result.report) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val discovery = report.getValue("discovery") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val declarations = discovery.getValue("capabilityDeclarations") as List<Map<String, Any?>>
        val kindsBySymbol = declarations.associate { declaration ->
            declaration.getValue("symbol").toString() to declaration.getValue("kind").toString()
        }

        assertEquals("host", kindsBySymbol["com.viewcompose.renderer.view.tree.ViewTreeRenderer"])
        assertEquals("tooling", kindsBySymbol["com.viewcompose.renderer.view.tree.RenderTreeTimingCollector"])
        assertEquals("host", kindsBySymbol["com.viewcompose.renderer.reconcile.ChildReconciler"])
        assertEquals(
            "integration",
            kindsBySymbol["com.viewcompose.renderer.decoration.AndroidViewDecorationBackend"],
        )
        assertEquals(
            "integration",
            kindsBySymbol["com.viewcompose.renderer.decoration.ViewDecorationHostLayout"],
        )
        assertFalse(kindsBySymbol.containsKey("com.viewcompose.renderer.view.tree.HiddenRenderer"))
        assertFalse(
            kindsBySymbol.containsKey(
                "com.viewcompose.renderer.view.container.DeclarativeInternalLayout",
            ),
        )
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
        val compiledSamplePath =
            "viewcompose-example/src/main/java/example/CurrentDocumentationSample.kt"
        repository.resolve(compiledSamplePath).apply {
            writeText(
                "// DOCS_REGION_START(current)\n" +
                    "VisibleDsl()\n" +
                    "// DOCS_REGION_END(current)\n",
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

                {/* compiled-region source="$compiledSamplePath" region="current" sample_id="sample.current" build_target=":viewcompose-example:compileKotlin" */}
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

                {/* compiled-region source="$compiledSamplePath" region="current" sample_id="sample.current" build_target=":viewcompose-example:compileKotlin" */}
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
        assertEquals(first.referenceCatalog, second.referenceCatalog)
        assertEquals(2, first.referenceEntryCount)
        assertTrue(first.referenceCatalog.contains("\"entryCount\": 2"))
        assertFalse(first.referenceCatalog.contains("HiddenDsl"))
        @Suppress("UNCHECKED_CAST")
        val reference = JsonSlurper().parseText(first.referenceCatalog) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val referenceGroups = reference.getValue("groups") as List<Map<String, Any?>>
        val referenceEntries = referenceGroups.flatMap { group ->
            @Suppress("UNCHECKED_CAST")
            val groupEntries = group.getValue("entries") as List<Map<String, Any?>>
            groupEntries
        }
        assertEquals(first.referenceEntryCount, referenceEntries.size)
        assertEquals(
            referenceEntries.size,
            referenceEntries.map { entry -> entry.getValue("symbol") }.distinct().size,
        )
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

        val committedReference = repository.resolve(
            "website/src/data/capability-reference.json",
        ).apply {
            parentFile.mkdirs()
            writeText(first.referenceCatalog)
        }
        val freshReference = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
            committedReferenceFile = committedReference,
        )
        assertEquals(first.issueCount, freshReference.issueCount)
        committedReference.appendText("stale")
        val staleReference = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
            committedReferenceFile = committedReference,
        )
        assertEquals(first.issueCount + 1, staleReference.issueCount)
        assertTrue(staleReference.report.contains("stale-generated-output"))
        assertTrue(
            staleReference.ratchetViolations.any { violation ->
                violation.contains("website/src/data/capability-reference.json")
            },
        )

        val undocumentedPublicEntry = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
            ratchetContext = DocumentationGovernanceV2RatchetContext(
                verificationBase = "base",
                changedSourceFiles = listOf(
                    DocumentationGovernanceV2SourceChange(
                        basePath = null,
                        baseSource = null,
                        currentPath = "viewcompose-example/src/main/java/example/Example.kt",
                        currentSource = repository.resolve(
                            "viewcompose-example/src/main/java/example/Example.kt",
                        ).readText(),
                    ),
                ),
            ),
        )
        assertTrue(
            undocumentedPublicEntry.ratchetViolations.any { violation ->
                violation.contains("example.Modifier.visibleModifier added public API change")
            },
        )

        val changedDefaultValue = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
            ratchetContext = DocumentationGovernanceV2RatchetContext(
                verificationBase = "base",
                changedSourceFiles = listOf(
                    DocumentationGovernanceV2SourceChange(
                        basePath = "viewcompose-example/src/main/java/example/Example.kt",
                        baseSource =
                            "package example\nfun Modifier.visibleModifier(label: String = \"old\") = this\n",
                        currentPath = "viewcompose-example/src/main/java/example/Example.kt",
                        currentSource =
                            "package example\nfun Modifier.visibleModifier(label: String = \"new\") = this\n",
                    ),
                ),
            ),
        )
        assertTrue(
            changedDefaultValue.ratchetViolations.any { violation ->
                violation.contains("example.Modifier.visibleModifier changed public API change")
            },
        )

        val currentMirrorText = localeMirror.readText()
        localeMirror.writeText(currentMirrorText.replace("VisibleDsl()", "StaleDsl()"))
        val staleMirror = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
        )
        assertTrue(
            staleMirror.report.contains(
                "fence content differs from $compiledSamplePath region current",
            ),
        )
        assertTrue(
            staleMirror.ratchetViolations.any { violation ->
                violation.contains("taxonomy-mismatch")
            },
        )
        localeMirror.writeText(currentMirrorText)

        val currentDocumentText = currentDocument.readText()
        currentDocument.writeText(
            currentDocumentText.replace(
                " build_target=\":viewcompose-example:compileKotlin\"",
                "",
            ),
        )
        val incompleteMarker = DocumentationGovernanceV2Reporter.generate(
            repository = repository,
            contractFiles = inputs,
            recordFiles = setOf(baseline),
            sourceSetDirectories = setOf(sourceRoot),
            activeDocumentationFiles = setOf(currentDocument, legacyDocument),
            localeMirrorFiles = setOf(localeMirror),
            publishingFiles = setOf(publishing, releases),
            documentationPolicyFiles = setOf(translationPolicy, moduleCatalog),
        )
        assertTrue(incompleteMarker.report.contains("compiled-region requires build_target"))
        currentDocument.writeText(currentDocumentText)

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

    @Test
    fun `new public modifier requires one exact capability impact record`() {
        val declaration = capabilityDeclaration(
            symbol = "com.viewcompose.ui.foundation.Modifier.foo",
            signatureHash = "new-signature",
        )
        val changes = DocumentationGovernanceV2PublicApiChanges.detect(
            baseDeclarations = emptyList(),
            currentDeclarations = listOf(declaration),
        )

        assertEquals("added", changes.single().change)
        val violations = DocumentationGovernanceV2PublicApiChanges.verifyImpacts(
            changes = changes,
            addedImpactPaths = emptySet(),
            records = emptyList(),
        )
        assertTrue(violations.single().contains("requires exactly one newly added matching"))
    }

    @Test
    fun `changed public signature accepts a newly added exact owned impact`() {
        val symbol = "com.viewcompose.ui.foundation.Modifier.foo"
        val changes = DocumentationGovernanceV2PublicApiChanges.detect(
            baseDeclarations = listOf(capabilityDeclaration(symbol, "old-signature")),
            currentDeclarations = listOf(capabilityDeclaration(symbol, "new-signature")),
        )
        val impactPath =
            "docs/project/records/documentation-governance-v2/impacts/impact-modifier-foo.json"
        val records = listOf(
            governanceRecord(
                path = impactPath,
                contractId = "capability-impact",
                recordId = "impact.modifier-foo",
                value = mapOf(
                    "artifact" to "viewcompose-ui-foundation",
                    "symbol_id" to symbol,
                    "change" to "changed",
                    "capability_id" to "modifier.foo",
                ),
            ),
            governanceRecord(
                path =
                    "docs/project/records/documentation-governance-v2/capabilities/modifier-foo.json",
                contractId = "capability",
                recordId = "modifier.foo",
                value = mapOf(
                    "artifact" to "viewcompose-ui-foundation",
                    "symbols" to listOf(mapOf("symbol_id" to symbol)),
                    "sample_owner" to mapOf("sample_id" to "sample.modifier-foo"),
                ),
            ),
            governanceRecord(
                path = "docs/project/records/documentation-governance-v2/samples/modifier-foo.json",
                contractId = "sample",
                recordId = "sample.modifier-foo",
                value = mapOf("capability_id" to "modifier.foo"),
            ),
        )

        assertEquals("changed", changes.single().change)
        assertTrue(
            DocumentationGovernanceV2PublicApiChanges.verifyImpacts(
                changes = changes,
                addedImpactPaths = setOf(impactPath),
                records = records,
            ).isEmpty(),
        )
    }

    @Test
    fun `adding an overload to an existing symbol is a change rather than a new capability`() {
        val symbol = "com.viewcompose.ui.foundation.Modifier.foo"
        val addedOverload = capabilityDeclaration(symbol, "new-overload")
        val completeOverloadSet = capabilityDeclaration(symbol, "existing-overload").toMutableMap().apply {
            this["overloadCount"] = 2
            this["signatureHashes"] = listOf("existing-overload", "new-overload")
        }

        val changes = DocumentationGovernanceV2PublicApiChanges.detect(
            baseDeclarations = emptyList(),
            currentDeclarations = listOf(addedOverload),
            currentInventory = listOf(completeOverloadSet),
        )

        assertEquals("changed", changes.single().change)
    }

    @Test
    fun `an unambiguous package change is classified as a move`() {
        val previous = capabilityDeclaration("example.old.Modifier.foo", "same-signature")
        val current = capabilityDeclaration("example.new.Modifier.foo", "same-signature")

        val change = DocumentationGovernanceV2PublicApiChanges.detect(
            baseDeclarations = listOf(previous),
            currentDeclarations = listOf(current),
        ).single()

        assertEquals("moved", change.change)
        assertEquals("example.old.Modifier.foo", change.previousSymbol)
        assertEquals("example.new.Modifier.foo", change.symbol)
    }

    @Test
    fun `git audit keeps capability impacts immutable and supplies changed source snapshots`() {
        val repository = temporaryFolder.newFolder("public-api-git-audit")
        val sourcePath = "viewcompose-example/src/main/java/example/ModifierDsl.kt"
        repository.resolve(sourcePath).apply {
            parentFile.mkdirs()
            writeText("package example\nfun Modifier.foo() = this\n")
        }
        val impactPath =
            "docs/project/records/documentation-governance-v2/impacts/impact-example.json"
        val executor = ratchetExecutor(diff = "A\t$sourcePath\nM\t$impactPath\n")

        val audit = DocumentationGovernanceV2GitRatchet.inspect(
            repository = repository,
            explicitBaseRevision = "base",
            executor = executor,
        )

        assertTrue(audit.violations.single().contains("immutable capability-impact"))
        assertEquals(sourcePath, audit.changedSourceFiles.single().currentPath)
        assertTrue(audit.changedSourceFiles.single().currentSource!!.contains("Modifier.foo"))
    }

    private fun capabilityDeclaration(symbol: String, signatureHash: String): Map<String, Any?> =
        mapOf(
            "artifact" to "viewcompose-ui-foundation",
            "deprecated" to false,
            "signatureHashes" to listOf(signatureHash),
            "symbol" to symbol,
        )

    private fun governanceRecord(
        path: String,
        contractId: String,
        recordId: String,
        value: Map<String, Any?>,
    ) = GovernanceRecord(
        path = path,
        contractId = contractId,
        valid = true,
        violations = emptyList(),
        value = value,
        recordId = recordId,
    )

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
