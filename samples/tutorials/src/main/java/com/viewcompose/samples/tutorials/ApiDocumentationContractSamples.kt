package com.viewcompose.samples.tutorials

private class UiTreeBuilder

private object Modifier

private class Environment

private interface RenderHost

private interface RenderSession {
    fun dispose()
}

private interface ApiDocumentationContractSamples {
    // DOCS_REGION_START(project-api-documentation-contract-comment)
    /**
     * Applies [block] to the current composition and commits its state when rendering succeeds.
     *
     * Only one prepared composition may be active at a time. If [block] fails, the previous slot and
     * observation state remains active and the exception is propagated to the caller.
     *
     * @param block computation executed in the current composition context.
     * @return the value produced by [block] after a successful commit.
     * @throws IllegalStateException if another prepared composition is still active.
     */
    fun <T> composeRoot(block: () -> T): T
    // DOCS_REGION_END(project-api-documentation-contract-comment)

    // DOCS_REGION_START(project-api-documentation-stateful-dsl-template)
    /**
     * Displays one selectable destination and reports user requests through [onSelected].
     *
     * Selection is controlled by [selected]. The component does not mutate caller state; invoke a
     * state update from [onSelected] to reflect the new selection. The callback runs on the Android
     * main thread after the click is accepted and before the next render pass.
     *
     * [modifier] is applied to the component's root node. The component resolves colors and shape
     * tokens from the current [Environment] during rendering.
     *
     * @sample com.viewcompose.samples.navigationDestination
     * @param selected whether this destination is rendered as selected
     * @param onSelected callback invoked for an accepted user selection request
     * @param modifier modifiers applied to the root node in chain order
     * @param content content rendered inside the destination
     */
    fun UiTreeBuilder.NavigationDestination(
        selected: Boolean,
        onSelected: () -> Unit,
        modifier: Modifier = Modifier,
        content: UiTreeBuilder.() -> Unit,
    )
    // DOCS_REGION_END(project-api-documentation-stateful-dsl-template)

    // DOCS_REGION_START(project-api-documentation-resource-owner-template)
    /**
     * Creates a render session that owns the native view tree until [RenderSession.dispose].
     *
     * Calls are confined to the Android main thread. Disposing the session detaches observations and
     * releases host references; subsequent render attempts throw [IllegalStateException].
     *
     * @param host Android host that owns the rendered view hierarchy
     * @return a new session with no rendered root
     * @throws IllegalStateException if [host] is already bound to an active session
     */
    fun createRenderSession(host: RenderHost): RenderSession
    // DOCS_REGION_END(project-api-documentation-resource-owner-template)
}
