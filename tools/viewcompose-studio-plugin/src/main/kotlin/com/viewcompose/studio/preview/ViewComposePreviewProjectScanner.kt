package com.viewcompose.studio.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
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
            FilenameIndex.getAllFilesByExt(project, "kt", scope)
                .asSequence()
                .filter { file ->
                    file.path
                        .replace('\\', '/')
                        .split('/')
                        .none { segment -> segment in IGNORED_SOURCE_DIRECTORIES }
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

private val IGNORED_SOURCE_DIRECTORIES = setOf(
    ".gradle",
    ".idea",
    "build",
    "out",
)
