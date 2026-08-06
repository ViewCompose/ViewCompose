package com.viewcompose.navigation

import android.view.ViewGroup

internal fun ViewGroup.requireNavHostView(): NavHostView {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child is NavHostView) {
            return child
        }
        if (child is ViewGroup) {
            child.findNavHostViewOrNull()?.let { return it }
        }
    }
    error("Expected a NavHostView descendant")
}

private fun ViewGroup.findNavHostViewOrNull(): NavHostView? {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child is NavHostView) {
            return child
        }
        if (child is ViewGroup) {
            child.findNavHostViewOrNull()?.let { return it }
        }
    }
    return null
}
