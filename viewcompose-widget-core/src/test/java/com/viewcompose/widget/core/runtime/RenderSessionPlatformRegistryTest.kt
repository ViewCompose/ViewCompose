package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core runtime 中的 Render Session Platform Registry 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Render Session Platform Registry behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import android.view.ViewGroup
import com.viewcompose.ui.node.VNode
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSessionPlatformRegistryTest {
    @Test
    fun `missing platform fails fast`() {
        val registry = RenderSessionPlatformRegistry()

        val error = runCatching {
            registry.requirePlatform()
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("RenderSession platform is not installed"))
        assertTrue(error?.message.orEmpty().contains("installRenderSessionPlatform"))
    }

    @Test
    fun `installation publishes one complete platform snapshot`() {
        val registry = RenderSessionPlatformRegistry()
        val platform = platform()

        registry.install(platform)

        assertSame(platform, registry.requirePlatform())
    }

    @Test
    fun `second installation fails and keeps original platform`() {
        val registry = RenderSessionPlatformRegistry()
        val first = platform()
        val second = platform()
        registry.install(first)

        val error = runCatching {
            registry.install(second)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("already installed"))
        assertSame(first, registry.requirePlatform())
    }

    private fun platform(): RenderSessionPlatform {
        return RenderSessionPlatform(
            renderEngine = FakeRenderEngine(),
            coroutineContext = EmptyCoroutineContext,
            runtimeFactory = RenderSessionRuntimeFactory { _, _ -> FakeRuntime() },
        )
    }

    private class FakeRenderEngine : CoreRenderEngine {
        override fun renderInto(
            container: ViewGroup,
            previousMountedNodes: List<Any>,
            nodes: List<VNode>,
            collectDiagnostics: Boolean,
        ): CoreRenderFrame {
            return CoreRenderFrame(mountedNodes = previousMountedNodes)
        }

        override fun disposeMounted(
            container: ViewGroup,
            mountedNodes: List<Any>,
        ): List<CoreRenderCommitFailure> = emptyList()
    }

    private class FakeRuntime : RenderSessionRuntime {
        override fun requestRender() = Unit

        override fun render() = Unit

        override fun dispose() = Unit
    }
}
