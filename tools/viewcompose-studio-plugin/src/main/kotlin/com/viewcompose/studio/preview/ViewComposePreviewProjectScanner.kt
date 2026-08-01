package com.viewcompose.studio.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class ViewComposePreviewProjectScanner(
    private val project: Project,
) {
    fun scan(): List<PreviewSourceSelection> {
        return ReadAction.nonBlocking<List<PreviewSourceSelection>> {
            val scope = GlobalSearchScope.projectScope(project)
            val psiManager = PsiManager.getInstance(project)
            val fileIndex = ProjectFileIndex.getInstance(project)
            FilenameIndex.getAllFilesByExt(project, "kt", scope)
                .asSequence()
                .filter { file ->
                    isSupportedPreviewSourcePath(file.path) &&
                        !fileIndex.isInTestSourceContent(file)
                }
                .mapNotNull(psiManager::findFile)
                .filterIsInstance<KtFile>()
                .flatMap { file ->
                    PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java)
                        .asSequence()
                }
                .mapNotNull(KtNamedFunction::toPreviewSourceSelection)
                .distinct()
                .sortedWith(
                    compareBy(
                        PreviewSourceSelection::filePath,
                        PreviewSourceSelection::line,
                        PreviewSourceSelection::symbolName,
                    ),
                )
                .toList()
        }.inSmartMode(project).executeSynchronously()
    }
}

internal fun isSupportedPreviewSourcePath(path: String): Boolean {
    val segments = path.replace('\\', '/').split('/')
    if (segments.any { segment -> segment in IGNORED_SOURCE_DIRECTORIES }) return false
    return segments.windowed(size = 2).none { (parent, sourceSet) ->
        parent == "src" && sourceSet.endsWith("Test", ignoreCase = true)
    }
}

private val IGNORED_SOURCE_DIRECTORIES = setOf(
    ".gradle",
    ".idea",
    "build",
    "out",
)
