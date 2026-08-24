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

    fun resolveCandidates(
        sourceCandidates: List<List<StudioPreviewSourceCallSite>>,
    ): List<StudioPreviewSourceLocation> {
        if (sourceCandidates.isEmpty()) return emptyList()
        return try {
            ReadAction.computeBlocking<List<StudioPreviewSourceLocation>, RuntimeException> {
                val scope = GlobalSearchScope.projectScope(project)
                resolveRuntimeSourceCandidates(sourceCandidates) { fileName ->
                    FilenameIndex.getVirtualFilesByName(fileName, scope)
                        .map { file -> file.path }
                }
            }
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    fun resolveEach(
        sourceCandidates: List<List<StudioPreviewSourceCallSite>>,
    ): List<StudioPreviewSourceLocation?> {
        if (sourceCandidates.isEmpty()) return emptyList()
        return try {
            ReadAction.computeBlocking<List<StudioPreviewSourceLocation?>, RuntimeException> {
                val scope = GlobalSearchScope.projectScope(project)
                val pathsByFileName = mutableMapOf<String, List<String>>()
                sourceCandidates.map { callSites ->
                    resolveRuntimeSource(callSites) { fileName ->
                        pathsByFileName.getOrPut(fileName) {
                            FilenameIndex.getVirtualFilesByName(fileName, scope)
                                .map { file -> file.path }
                        }
                    }
                }
            }
        } catch (_: IndexNotReadyException) {
            List(sourceCandidates.size) { null }
        }
    }
}

internal fun resolveRuntimeSource(
    callSites: List<StudioPreviewSourceCallSite>,
    findCandidatePaths: (fileName: String) -> List<String>,
): StudioPreviewSourceLocation? {
    return resolveRuntimeSourceCandidate(callSites, findCandidatePaths)?.toLocation()
}

internal fun resolveRuntimeSourceCandidates(
    sourceCandidates: List<List<StudioPreviewSourceCallSite>>,
    findCandidatePaths: (fileName: String) -> List<String>,
): List<StudioPreviewSourceLocation> {
    val resolved = sourceCandidates.mapIndexedNotNull { emissionIndex, callSites ->
        resolveRuntimeSourceCandidate(callSites, findCandidatePaths)?.let { candidate ->
            ResolvedSourceCandidate(
                emissionIndex = emissionIndex,
                callSites = callSites,
                candidate = candidate,
            )
        }
    }
    if (resolved.isEmpty()) return emptyList()

    val groups = resolved.groupBy { source -> source.groupKey() }
    val groupKeysByFrame = resolved
        .groupBy { source -> source.candidate.callSite.frameIdentity() }
        .mapValues { (_, sources) -> sources.map(ResolvedSourceCandidate::groupKey).toSet() }
    val wrapperGroups = buildSet {
        resolved.forEach { source ->
            source.callSites
                .drop(source.candidate.callSiteIndex + 1)
                .forEach { outerCallSite ->
                    addAll(groupKeysByFrame[outerCallSite.frameIdentity()].orEmpty())
                }
        }
    }
    val contentGroups = groups.filterKeys { group -> group !in wrapperGroups }
        .ifEmpty { groups }

    return contentGroups.entries
        .sortedWith(
            compareByDescending<Map.Entry<ResolvedSourceGroupKey, List<ResolvedSourceCandidate>>> {
                entry -> entry.value.size
            }.thenBy { entry -> entry.value.minOf(ResolvedSourceCandidate::emissionIndex) },
        )
        .map { (_, sources) ->
            sources.minWith(
                compareBy<ResolvedSourceCandidate> { source -> source.candidate.callSiteIndex }
                    .thenBy(ResolvedSourceCandidate::emissionIndex),
            ).candidate.toLocation()
        }
}

private fun resolveRuntimeSourceCandidate(
    callSites: List<StudioPreviewSourceCallSite>,
    findCandidatePaths: (fileName: String) -> List<String>,
): RuntimeSourceCandidate? {
    val candidates = callSites.asSequence()
        .flatMapIndexed { callSiteIndex, callSite ->
            findCandidatePaths(callSite.fileName)
                .asSequence()
                .map { candidatePath ->
                    RuntimeSourceCandidate(
                        callSite = callSite,
                        path = candidatePath,
                        callSiteIndex = callSiteIndex,
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
    return eligibleCandidates.maxWithOrNull(
        compareBy<RuntimeSourceCandidate> { candidate -> candidate.score }
            .thenByDescending { candidate -> candidate.path },
    )
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
    val callSiteIndex: Int,
    val score: Int,
)

private data class ResolvedSourceCandidate(
    val emissionIndex: Int,
    val callSites: List<StudioPreviewSourceCallSite>,
    val candidate: RuntimeSourceCandidate,
) {
    fun groupKey(): ResolvedSourceGroupKey {
        return ResolvedSourceGroupKey(
            path = candidate.path,
            methodName = candidate.callSite.normalizedMethodName(),
        )
    }
}

private data class ResolvedSourceGroupKey(
    val path: String,
    val methodName: String,
)

private data class SourceFrameIdentity(
    val className: String,
    val methodName: String,
    val fileName: String,
)

private fun RuntimeSourceCandidate.toLocation(): StudioPreviewSourceLocation {
    return StudioPreviewSourceLocation(
        filePath = path,
        line = callSite.lineNumber,
        column = 1,
        symbolName = callSite.methodName,
    )
}

private fun StudioPreviewSourceCallSite.frameIdentity(): SourceFrameIdentity {
    return SourceFrameIdentity(
        className = className.substringBefore('$'),
        methodName = normalizedMethodName(),
        fileName = fileName,
    )
}

private fun StudioPreviewSourceCallSite.normalizedMethodName(): String {
    return methodName.substringBefore('$')
}
