package com.viewcompose.renderer.view.container

import android.view.ViewGroup

/**
 * Marks the ViewGroup that directly hosts renderer-managed children.
 * Marks the ViewGroup that should receive rendered child views.
 */
internal interface ChildHostViewGroup {
    /**
     * Target container where the patch pipeline inserts, moves, and removes Android children.
     * Target container where the patch pipeline adds, moves, and removes Android children.
     */
    val childHost: ViewGroup
}
