package com.viewcompose.studio.preview

internal fun findMappedNativeViewAt(
    views: List<StudioPreviewNativeViewNode>,
    x: Int,
    y: Int,
): StudioPreviewNativeViewNode? {
    return views.asReversed().firstNotNullOfOrNull { view ->
        view.findMappedDescendantAt(x, y)
    }
}

private fun StudioPreviewNativeViewNode.findMappedDescendantAt(
    x: Int,
    y: Int,
): StudioPreviewNativeViewNode? {
    if (visibility != "VISIBLE" || !bounds.contains(x, y)) return null
    children.asReversed().forEach { child ->
        child.findMappedDescendantAt(x, y)?.let { mappedChild ->
            return mappedChild
        }
    }
    return takeIf { view -> view.sourceCallSites.isNotEmpty() }
}

private fun StudioPreviewLayoutBounds.contains(
    x: Int,
    y: Int,
): Boolean {
    return width > 0 &&
        height > 0 &&
        x >= left &&
        x < right &&
        y >= top &&
        y < bottom
}
