package com.viewcompose.widget.core

interface RenderSessionRuntime {
    fun requestRender()

    fun render()

    fun dispose()
}

fun interface RenderSessionRuntimeFactory {
    fun create(
        onRenderNow: () -> Unit,
        onDisposeNow: () -> Unit,
    ): RenderSessionRuntime
}
