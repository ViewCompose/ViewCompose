package com.viewcompose.quality

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QualityGateParityHarnessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `executes both implementations against one fixture and removes environment noise`() {
        val repository = temporaryFolder.newFolder("fixture")
        var legacyRepository: File? = null
        var candidateRepository: File? = null
        val result = QualityGateParityHarness().compare(
            fixtureRepository = repository,
            legacy = QualityGateImplementation { fixture ->
                legacyRepository = fixture
                QualityGateOutcome(
                    succeeded = false,
                    diagnostics = listOf("${fixture.absolutePath}/src/Test.kt:4\r\n  violation  "),
                    selectedPaths = listOf("src\\B.kt", "./src/A.kt"),
                )
            },
            candidate = QualityGateImplementation { fixture ->
                candidateRepository = fixture
                QualityGateOutcome(
                    succeeded = false,
                    diagnostics = listOf("<repo>/src/Test.kt:4\n  violation"),
                    selectedPaths = listOf("src/A.kt", "src/B.kt"),
                )
            },
        )

        assertSame(legacyRepository, candidateRepository)
        assertEquals(listOf("<repo>/src/Test.kt:4\n  violation"), result.legacy.diagnostics)
        assertEquals(listOf("src/A.kt", "src/B.kt"), result.legacy.selectedPaths)
        assertTrue(result.isEquivalent)
        result.assertEquivalent()
    }

    @Test
    fun `reports every semantic parity difference`() {
        val repository = temporaryFolder.newFolder("fixture")
        val result = QualityGateParityHarness().compare(
            fixtureRepository = repository,
            legacy = QualityGateImplementation {
                QualityGateOutcome(
                    succeeded = false,
                    diagnostics = listOf("legacy failure"),
                    selectedPaths = listOf("src/Legacy.kt"),
                )
            },
            candidate = QualityGateImplementation {
                QualityGateOutcome(
                    succeeded = true,
                    diagnostics = listOf("candidate failure"),
                    selectedPaths = listOf("src/Candidate.kt"),
                )
            },
        )

        assertFalse(result.isEquivalent)
        assertEquals(3, result.differences.size)
        assertTrue(result.differences[0].startsWith("success differs"))
        assertTrue(result.differences[1].startsWith("diagnostics differ"))
        assertTrue(result.differences[2].startsWith("selected paths differ"))
        assertThrows(IllegalStateException::class.java, result::assertEquivalent)
    }

    @Test
    fun `rejects a missing fixture repository before either implementation runs`() {
        var executions = 0
        val implementation = QualityGateImplementation {
            executions += 1
            QualityGateOutcome(true, emptyList(), emptyList())
        }

        assertThrows(IllegalArgumentException::class.java) {
            QualityGateParityHarness().compare(
                fixtureRepository = File(temporaryFolder.root, "missing"),
                legacy = implementation,
                candidate = implementation,
            )
        }
        assertEquals(0, executions)
    }
}
