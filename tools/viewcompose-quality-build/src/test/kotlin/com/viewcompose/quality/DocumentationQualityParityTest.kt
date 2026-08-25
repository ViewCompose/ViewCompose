package com.viewcompose.quality

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentationQualityParityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `broken documentation link preserves diagnostic and selected inputs`() {
        val pagePath = "docs/README.md"
        val catalogPath = "docs/modules/README.md"
        val propertiesPath = "gradle/viewcompose-publishing.properties"
        val repository = fixtureRepository(
            label = "broken-link",
            pagePath to "[Missing](./missing.md)\n",
            catalogPath to "# Modules\n",
            propertiesPath to "schemaVersion=1\n",
        )
        assertFrozenParity(
            label = "verifyDocumentationStructure",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "Documentation structure verification failed:\n" +
                        "- $pagePath -> broken relative link: ./missing.md",
                catalogPath,
                pagePath,
                propertiesPath,
            ),
        ) { fixture ->
            DocumentationQualityVerifiers.verifyDocumentationStructure(
                repository = fixture,
                rootMarkdownFiles = emptySet(),
                activeDocumentationFiles = setOf(fixture.resolve(pagePath)),
                checkedMarkdownFiles = setOf(fixture.resolve(pagePath)),
                governanceFiles = setOf(
                    fixture.resolve(catalogPath),
                    fixture.resolve(propertiesPath),
                ),
                documentationTopLevelDirectories = emptyList(),
            )
        }
    }

    @Test
    fun `missing DSL KDoc preserves diagnostic and source input`() {
        val sourcePath =
            "viewcompose-ui-foundation/src/main/java/com/viewcompose/ui/foundation/dsl/Bad.kt"
        val repository = fixtureRepository(
            label = "dsl-kdoc",
            sourcePath to "fun UiTreeBuilder.Bad(value: Int) = Unit\n",
        )
        assertFrozenParity(
            label = "verifyDslApiContracts-kdoc",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "DSL API contract verification failed:\n" +
                        "- $sourcePath -> Bad has no adjacent KDoc",
                sourcePath,
            ),
        ) { fixture ->
            DocumentationQualityVerifiers.verifyDslApiContracts(
                repository = fixture,
                foundationDslFiles = setOf(fixture.resolve(sourcePath)),
                forbiddenContractFiles = emptySet(),
                animationFiles = emptySet(),
            )
        }
    }

    @Test
    fun `renderer detail preserves DSL contract diagnostic and source input`() {
        val sourcePath = "viewcompose-ui-contract/src/main/java/com/viewcompose/ui/Bad.kt"
        val repository = fixtureRepository(
            label = "dsl-renderer-detail",
            sourcePath to "val rippleColor = 0\n",
        )
        assertFrozenParity(
            label = "verifyDslApiContracts-renderer-detail",
            repository = repository,
            expected = failedOutcome(
                diagnostic =
                    "DSL API contract verification failed:\n" +
                        "- $sourcePath:1 -> rippleColor is a renderer detail",
                sourcePath,
            ),
        ) { fixture ->
            DocumentationQualityVerifiers.verifyDslApiContracts(
                repository = fixture,
                foundationDslFiles = emptySet(),
                forbiddenContractFiles = setOf(fixture.resolve(sourcePath)),
                animationFiles = emptySet(),
            )
        }
    }

    private fun fixtureRepository(
        label: String,
        vararg files: Pair<String, String>,
    ): File {
        val repository = temporaryFolder.newFolder(label)
        files.forEach { (path, content) ->
            repository.resolve(path).apply {
                parentFile.mkdirs()
                writeText(content)
            }
        }
        return repository
    }

    private fun failedOutcome(diagnostic: String, vararg paths: String): QualityGateOutcome =
        QualityGateOutcome(
            succeeded = false,
            diagnostics = listOf(diagnostic),
            selectedPaths = paths.toList().sorted(),
        )

    private fun assertFrozenParity(
        label: String,
        repository: File,
        expected: QualityGateOutcome,
        candidate: QualityGateImplementation,
    ) {
        val result = QualityGateParityHarness().compare(
            fixtureRepository = repository,
            legacy = QualityGateImplementation { expected },
            candidate = candidate,
        )
        assertTrue("$label: ${result.differences.joinToString("; ")}", result.isEquivalent)
        result.assertEquivalent()
    }
}
