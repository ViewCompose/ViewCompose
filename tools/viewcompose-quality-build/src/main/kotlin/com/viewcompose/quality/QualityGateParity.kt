package com.viewcompose.quality

import java.io.File

internal data class QualityGateOutcome(
    val succeeded: Boolean,
    val diagnostics: List<String>,
    val selectedPaths: List<String>,
)

internal fun interface QualityGateImplementation {
    fun execute(fixtureRepository: File): QualityGateOutcome
}

internal data class QualityGateParityResult(
    val legacy: QualityGateOutcome,
    val candidate: QualityGateOutcome,
    val differences: List<String>,
) {
    val isEquivalent: Boolean
        get() = differences.isEmpty()

    fun assertEquivalent() {
        check(isEquivalent) {
            buildString {
                appendLine("Quality gate parity failed:")
                differences.forEach { difference -> appendLine("- $difference") }
            }.trimEnd()
        }
    }
}

internal class QualityGateParityHarness {
    fun compare(
        fixtureRepository: File,
        legacy: QualityGateImplementation,
        candidate: QualityGateImplementation,
    ): QualityGateParityResult {
        val repository = fixtureRepository.absoluteFile.normalize()
        require(repository.isDirectory) {
            "Parity fixture repository does not exist: $repository"
        }
        val legacyOutcome = legacy.execute(repository).normalized(repository)
        val candidateOutcome = candidate.execute(repository).normalized(repository)
        val differences = buildList {
            if (legacyOutcome.succeeded != candidateOutcome.succeeded) {
                add(
                    "success differs: legacy=${legacyOutcome.succeeded}, " +
                        "candidate=${candidateOutcome.succeeded}",
                )
            }
            if (legacyOutcome.diagnostics != candidateOutcome.diagnostics) {
                add(
                    "diagnostics differ: legacy=${legacyOutcome.diagnostics}, " +
                        "candidate=${candidateOutcome.diagnostics}",
                )
            }
            if (legacyOutcome.selectedPaths != candidateOutcome.selectedPaths) {
                add(
                    "selected paths differ: legacy=${legacyOutcome.selectedPaths}, " +
                        "candidate=${candidateOutcome.selectedPaths}",
                )
            }
        }
        return QualityGateParityResult(legacyOutcome, candidateOutcome, differences)
    }

    private fun QualityGateOutcome.normalized(repository: File): QualityGateOutcome {
        val repositoryNative = repository.absolutePath.trimEnd(File.separatorChar)
        val repositoryInvariant = repository.invariantSeparatorsPath.trimEnd('/')
        return copy(
            diagnostics = diagnostics.map { diagnostic ->
                diagnostic
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replace(repositoryNative, "<repo>")
                    .replace(repositoryInvariant, "<repo>")
                    .lineSequence()
                    .joinToString("\n") { line -> line.trimEnd() }
                    .trim()
            },
            selectedPaths = selectedPaths
                .map { path -> normalizeSelectedPath(path, repositoryInvariant) }
                .sorted(),
        )
    }

    private fun normalizeSelectedPath(path: String, repositoryInvariant: String): String {
        val normalized = path.replace('\\', '/').removePrefix("./")
        return when {
            normalized == repositoryInvariant -> "."
            normalized.startsWith("$repositoryInvariant/") ->
                normalized.removePrefix("$repositoryInvariant/")
            else -> normalized
        }
    }
}
