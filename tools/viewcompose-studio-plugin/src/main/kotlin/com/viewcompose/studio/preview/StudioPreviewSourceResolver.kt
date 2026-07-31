package com.viewcompose.studio.preview

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

internal class StudioPreviewSourceResolver(
    private val project: Project,
) {
    fun resolve(callSites: List<StudioPreviewSourceCallSite>): StudioPreviewSourceLocation? {
        if (callSites.isEmpty()) return null
        return try {
            ReadAction.computeBlocking<StudioPreviewSourceLocation?, RuntimeException> {
                val scope = GlobalSearchScope.projectScope(project)
                resolveRuntimeSource(callSites) { fileName ->
                    FilenameIndex.getVirtualFilesByName(fileName, scope)
                        .map { file -> file.path }
                }
            }
        } catch (_: IndexNotReadyException) {
            null
        }
    }
}

internal fun resolveRuntimeSource(
    callSites: List<StudioPreviewSourceCallSite>,
    findCandidatePaths: (fileName: String) -> List<String>,
): StudioPreviewSourceLocation? {
    val candidates = callSites.asSequence()
        .flatMapIndexed { callSiteIndex, callSite ->
            findCandidatePaths(callSite.fileName)
                .asSequence()
                .map { candidatePath ->
                    RuntimeSourceCandidate(
                        callSite = callSite,
                        path = candidatePath,
                        score = sourceCandidateScore(
                            callSite = callSite,
                            path = candidatePath,
                            callSiteIndex = callSiteIndex,
                        ),
                    )
                }
        }
        .toList()
    val authoredCandidates = candidates.filter { candidate ->
        !candidate.path.isFrameworkSource() && !candidate.path.isGeneratedSource()
    }
    val projectCandidates = candidates.filterNot { candidate ->
        candidate.path.isFrameworkSource()
    }
    val eligibleCandidates = authoredCandidates.ifEmpty {
        projectCandidates.ifEmpty {
            candidates
        }
    }
    return eligibleCandidates
        .maxWithOrNull(
            compareBy<RuntimeSourceCandidate> { candidate -> candidate.score }
                .thenByDescending { candidate -> candidate.path },
        )
        ?.let { candidate ->
            StudioPreviewSourceLocation(
                filePath = candidate.path,
                line = candidate.callSite.lineNumber,
                column = 1,
                symbolName = candidate.callSite.methodName,
            )
        }
}

private fun sourceCandidateScore(
    callSite: StudioPreviewSourceCallSite,
    path: String,
    callSiteIndex: Int,
): Int {
    val normalizedPath = path.replace('\\', '/')
    val packagePath = callSite.className
        .substringBeforeLast('.', missingDelimiterValue = "")
        .replace('.', '/')
    var score = -callSiteIndex
    if (
        packagePath.isNotEmpty() &&
        normalizedPath.endsWith("/$packagePath/${callSite.fileName}")
    ) {
        score += 160
    }
    score += when {
        "/app/src/" in normalizedPath -> 120
        "/src/main/" in normalizedPath -> 80
        "/src/debug/" in normalizedPath -> 75
        "/src/test/" in normalizedPath -> 60
        else -> 0
    }
    if (normalizedPath.isGeneratedSource()) {
        score -= 300
    }
    if (normalizedPath.isFrameworkSource()) {
        score -= 50
    }
    return score
}

private fun String.isFrameworkSource(): Boolean {
    val normalizedPath = replace('\\', '/')
    return "/viewcompose-" in normalizedPath
}

private fun String.isGeneratedSource(): Boolean {
    val normalizedPath = replace('\\', '/')
    return "/build/" in normalizedPath ||
        "/generated/" in normalizedPath ||
        "/.gradle/" in normalizedPath
}

private data class RuntimeSourceCandidate(
    val callSite: StudioPreviewSourceCallSite,
    val path: String,
    val score: Int,
)
