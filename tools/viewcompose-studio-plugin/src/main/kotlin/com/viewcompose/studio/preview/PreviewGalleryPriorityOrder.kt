package com.viewcompose.studio.preview

/** Thread-safe ordering shared by the Swing viewport and the background gallery renderer. */
internal class PreviewGalleryPriorityOrder(
    selections: List<PreviewSourceSelection>,
) {
    private val sourceOrder = selections.distinct()
    private var visiblePriority: List<PreviewSourceSelection> = emptyList()

    @Synchronized
    fun prioritize(selections: List<PreviewSourceSelection>) {
        val known = sourceOrder.toHashSet()
        visiblePriority = selections.filter(known::contains).distinct()
    }

    @Synchronized
    fun order(selections: Collection<PreviewSourceSelection>): List<PreviewSourceSelection> {
        val candidates = selections.toHashSet()
        return buildList {
            visiblePriority.filterTo(this, candidates::contains)
            sourceOrder.filterTo(this) { selection ->
                selection in candidates && selection !in this
            }
            selections.filterTo(this) { selection -> selection !in this }
        }
    }
}
