package com.viewcompose.ui.tooling

import com.viewcompose.ui.node.RenderContainerHandle

/**
 * Q2 tooling marker that classifies a renderer container for page-level source navigation.
 *
 * Hosts mark their composition root as [UiSourceSessionRole.Host]. Renderers mark an independently
 * rendered navigation or pager destination as [UiSourceSessionRole.Page] and ordinary lazy content
 * as [UiSourceSessionRole.Content]. Source tooling can then capture page boundaries without letting
 * a deeper list row replace the enclosing page.
 *
 * This marker has no rendering semantics and must not be used for application state or layout.
 *
 * @sample com.viewcompose.ui.samples.sourceSessionContainerHandleSample
 */
interface UiSourceSessionContainerHandle : RenderContainerHandle {
    /** Tooling-only role of the independently rendered container. */
    val sourceSessionRole: UiSourceSessionRole
}

/** Defines which independently rendered containers represent a navigable DSL boundary. */
enum class UiSourceSessionRole {
    /** Host composition root, such as an Activity or Fragment content container. */
    Host,

    /** Independently rendered page, such as a horizontal or vertical pager destination. */
    Page,

    /** Ordinary lazy content that should not replace its enclosing page as a navigation target. */
    Content,
}
