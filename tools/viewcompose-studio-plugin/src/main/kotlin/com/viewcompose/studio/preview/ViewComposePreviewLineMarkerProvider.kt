package com.viewcompose.studio.preview

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import java.util.function.Supplier
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class ViewComposePreviewLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val function = element.parent as? KtNamedFunction ?: return null
        if (function.nameIdentifier !== element) return null
        val selection = function.toPreviewSourceSelection() ?: return null
        return LineMarkerInfo(
            element,
            element.textRange,
            PREVIEW_GUTTER_ICON,
            { "Render ViewCompose preview" },
            GutterIconNavigationHandler { _, _ ->
                function.project.service<ViewComposePreviewSelectionService>()
                    .selectAndShow(selection)
            },
            GutterIconRenderer.Alignment.CENTER,
            Supplier { "ViewCompose Preview" },
        )
    }
}

internal fun KtNamedFunction.toPreviewSourceSelection(): PreviewSourceSelection? {
    if (!hasViewComposePreviewAnnotation()) return null
    val symbolName = name ?: return null
    val virtualFile = containingFile.virtualFile ?: return null
    val document = PsiDocumentManager.getInstance(project)
        .getDocument(containingFile)
        ?: return null
    return PreviewSourceSelection(
        filePath = virtualFile.path,
        symbolName = symbolName,
        line = document.getLineNumber(textOffset) + 1,
    )
}

internal fun PsiFile.previewSelectionAtOffset(offset: Int): PreviewSourceSelection? {
    if (textLength == 0) return null
    val boundedOffset = offset.coerceIn(0, textLength - 1)
    val element = findElementAt(boundedOffset)
        ?: boundedOffset.takeIf { value -> value > 0 }
            ?.let { value -> findElementAt(value - 1) }
        ?: return null
    return PsiTreeUtil.getParentOfType(
        element,
        KtNamedFunction::class.java,
        false,
    )?.toPreviewSourceSelection()
}

internal fun PsiFile.previewSelectionNearestToOffset(offset: Int): PreviewSourceSelection? {
    previewSelectionAtOffset(offset)?.let { selection -> return selection }
    val boundedOffset = offset.coerceIn(0, textLength.coerceAtLeast(1) - 1)
    return PsiTreeUtil.findChildrenOfType(this, KtNamedFunction::class.java)
        .asSequence()
        .mapNotNull { function ->
            function.toPreviewSourceSelection()?.let { selection -> function to selection }
        }
        .minByOrNull { (function, _) ->
            val range = function.textRange
            when {
                boundedOffset < range.startOffset -> range.startOffset - boundedOffset
                boundedOffset > range.endOffset -> boundedOffset - range.endOffset
                else -> 0
            }
        }
        ?.second
}

internal fun KtNamedFunction.hasViewComposePreviewAnnotation(): Boolean {
    return annotationEntries.any { annotation ->
        annotation.isViewComposePreviewAnnotation(
            depth = 0,
            visited = hashSetOf(),
        )
    }
}

private fun KtAnnotationEntry.isViewComposePreviewAnnotation(
    depth: Int,
    visited: MutableSet<String>,
): Boolean {
    if (depth > MAX_META_ANNOTATION_DEPTH) return false
    if (matchesKnownPreviewImport()) return true

    val resolved = calleeExpression
        ?.constructorReferenceExpression
        ?.references
        ?.firstNotNullOfOrNull { reference -> reference.resolve() }
        ?: return false
    val kotlinClass = when (resolved) {
        is KtClass -> resolved
        else -> PsiTreeUtil.getParentOfType(resolved, KtClass::class.java, false)
    }
    if (kotlinClass != null) {
        val qualifiedName = kotlinClass.fqName?.asString()
        if (qualifiedName in PREVIEW_ANNOTATION_NAMES) return true
        val identity = qualifiedName
            ?: "${kotlinClass.containingFile.virtualFile?.path}:${kotlinClass.textOffset}"
        if (!visited.add(identity)) return false
        return kotlinClass.annotationEntries.any { metaAnnotation ->
            metaAnnotation.isViewComposePreviewAnnotation(
                depth = depth + 1,
                visited = visited,
            )
        }
    }

    val psiClass = resolved as? PsiClass
        ?: PsiTreeUtil.getParentOfType(resolved, PsiClass::class.java, false)
        ?: return false
    return psiClass.isViewComposePreviewAnnotation(
        depth = depth,
        visited = visited,
    )
}

private fun PsiClass.isViewComposePreviewAnnotation(
    depth: Int,
    visited: MutableSet<String>,
): Boolean {
    if (depth > MAX_META_ANNOTATION_DEPTH) return false
    val identity = qualifiedName ?: "${containingFile?.virtualFile?.path}:$textOffset"
    if (identity in PREVIEW_ANNOTATION_NAMES) return true
    if (!visited.add(identity)) return false
    return modifierList?.annotations.orEmpty().any { annotation ->
        annotation.isViewComposePreviewAnnotation(
            depth = depth + 1,
            visited = visited,
        )
    }
}

private fun PsiAnnotation.isViewComposePreviewAnnotation(
    depth: Int,
    visited: MutableSet<String>,
): Boolean {
    val name = qualifiedName
    if (name in PREVIEW_ANNOTATION_NAMES) return true
    return resolveAnnotationType()
        ?.isViewComposePreviewAnnotation(
            depth = depth,
            visited = visited,
        )
        ?: false
}

private fun KtAnnotationEntry.matchesKnownPreviewImport(): Boolean {
    val shortName = shortName?.asString() ?: return false
    val typeText = typeReference?.text
    if (typeText in PREVIEW_ANNOTATION_NAMES) return true
    val containingKtFile = containingKtFile
    if (
        shortName in PREVIEW_ANNOTATION_SHORT_NAMES &&
        containingKtFile.packageFqName.asString() == PREVIEW_ANNOTATION_PACKAGE
    ) {
        return true
    }
    return containingKtFile.importDirectives.any { importDirective ->
        val importedName = importDirective.importedFqName?.asString() ?: return@any false
        val visibleName = importDirective.aliasName ?: importedName.substringAfterLast('.')
        visibleName == shortName && importedName in PREVIEW_ANNOTATION_NAMES
    }
}

private val PREVIEW_GUTTER_ICON = IconLoader.getIcon(
    "/icons/viewcomposePreview.svg",
    ViewComposePreviewLineMarkerProvider::class.java,
)

private const val PREVIEW_ANNOTATION_PACKAGE = "com.viewcompose.preview.tooling"
private const val MAX_META_ANNOTATION_DEPTH = 8
private val PREVIEW_ANNOTATION_SHORT_NAMES = setOf(
    "ViewComposePreview",
    "ViewComposePreviews",
)
private val PREVIEW_ANNOTATION_NAMES = PREVIEW_ANNOTATION_SHORT_NAMES
    .mapTo(hashSetOf()) { shortName -> "$PREVIEW_ANNOTATION_PACKAGE.$shortName" }
