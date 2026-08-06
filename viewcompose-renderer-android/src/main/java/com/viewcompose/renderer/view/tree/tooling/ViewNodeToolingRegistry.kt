package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.tooling.UiNodeToolingMetadata
import java.util.Collections
import java.util.WeakHashMap

/**
 * Weak, tooling-only association between mounted Android Views and their declarative nodes.
 *
 * The registry remains empty during ordinary application rendering because normal VNodes carry no
 * tooling metadata. Weak keys also ensure diagnostics can never extend a View lifecycle.
 */
object ViewNodeToolingRegistry {
    private val metadataByView = Collections.synchronizedMap(
        WeakHashMap<View, UiNodeToolingMetadata>(),
    )

    internal fun bind(
        view: View,
        node: VNode,
    ) {
        val metadata = UiNodeTooling.metadataOf(node)
        if (metadata == null) {
            metadataByView.remove(view)
        } else {
            metadataByView[view] = metadata
        }
    }

    /**
     * Returns the source metadata captured for [view], if tooling was active during node creation.
     *
     * The result is a snapshot and the weak association can disappear after the View is released.
     * This query does not create metadata or extend the View lifecycle.
     *
     * @param view mounted View to inspect
     * @return captured declarative source metadata, or `null` when unavailable
     */
    fun metadataOf(view: View): UiNodeToolingMetadata? = metadataByView[view]

    internal fun clear(view: View) {
        metadataByView.remove(view)
    }
}
